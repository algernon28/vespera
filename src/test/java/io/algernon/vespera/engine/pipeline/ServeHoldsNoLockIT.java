package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import picocli.CommandLine;

/**
 * {@code vespera serve}, on the packaged jar, holds no lock and opens no database (ADR-333 §8, amending ADR-177 §1):
 * it starts while another invocation holds the working directory, and {@code vespera publish --to website} completes
 * while it serves, the page it answers then being the one that publish wrote; in a working directory holding a
 * published website and no {@code vespera.db}, serving makes none, nor anything else there or in the folder it was
 * started in, since no context, listener, datasource or log file is set up; it reads no YAML file, so a working
 * directory that only an {@code application.yaml} in the folder it was started in names does not reach it; a
 * placeholder nothing resolves in {@code vespera.docling.ocr-preset}, which stops a run (ADR-332 §2), stops nothing;
 * and a working directory that is not there is refused in one line, exit 1, and not made.
 *
 * <p>Pinned by launching the packaged jar, as {@code WorkingDirectoryInUseIT} does, because which process holds the
 * lock, what is made at start and what reaches the operator's terminal live outside any in-process test. This JVM holds
 * the working directory through the listener the jar registers, so the holder is this process. The ledger a publish
 * reads is written here, into the working directory's {@code vespera.db}, by the shipped {@code schema.sql}, before any
 * process of the jar opens it, and the website rendering it publishes is laid out here by hand: one page, its list and
 * its {@code drawing.txt}. No sidecar is started, so no Docker daemon is needed.
 */
@Epic("Architecture")
@Feature("One invocation per working directory")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class ServeHoldsNoLockIT {

    /** Generous over a start-up of a few seconds; the point is only that the process does start, or end. */
    private static final long BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    private static final String WEBSITE = DeliverableRoot.WEBSITE_FOLDER_NAME;

    private static final String VESPERA_WEBSITE_JSON = "vespera-website.json";

    private static final String FILES_JSON = "files.json";

    private static final String DATABASE_FILE = "vespera.db";

    private static final String THE_OCR_PRESET = "vespera.docling.ocr-preset";

    private static final String A_PLACEHOLDER_NOTHING_RESOLVES = "${NOTHING_NAMES_THIS}";

    private static final String FIRST_EDITION = "<p>the first edition of the page</p>";

    private static final String SECOND_EDITION = "<p>the second edition, published while it was served</p>";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Whatever an earlier test class in this JVM left held is handed back before this one takes its own. */
    @BeforeEach
    void startWithNothingHeld() {
        WorkingDirectoryLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        WorkingDirectoryLock.release();
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("vespera serve starts while another invocation holds the working directory, and a publish to the website completes while it serves")
    void serveStartsOnAHeldWorkingDirectoryAndAPublishCompletesWhileItServes(@TempDir Path workingDirectory)
            throws Exception {
        Path rendering = aGenerationRunDrawingOnePage(workingDirectory);
        writeRendering(rendering, FIRST_EDITION);
        Launched first = launch(workingDirectory, "publish", "--to", "website");
        claim("a first publish to the website completes, so there is a website to serve",
                () -> {
                    assertThat(first.exitCode()).as(String.join("\n", first.printed())).isZero();
                    assertThat(workingDirectory.resolve(WEBSITE).resolve(VESPERA_WEBSITE_JSON)).isRegularFile();
                });

        hold(workingDirectory);
        int port = aFreePort();
        Serving serving = serve(workingDirectory, List.of("-Dvespera.working-dir=" + workingDirectory,
                "-Dvespera.serve.port=" + port), port);
        try {
            claim("vespera serve starts and says where it serves, though another invocation holds the working directory",
                    () -> assertThat(serving.printed()).containsExactly(servingLine(workingDirectory, port)));
            claim("and it answers the page the first publish wrote",
                    () -> assertThat(page(port)).isEqualTo(FIRST_EDITION));

            WorkingDirectoryLock.release();
            writeRendering(rendering, SECOND_EDITION);
            Launched second = launch(workingDirectory, "publish", "--to", "website");

            claim("a publish to the website started while it serves takes the working directory and completes, exit 0,"
                            + " within " + BOUND_SECONDS + " seconds",
                    () -> {
                        assertThat(second.finished()).isTrue();
                        assertThat(second.exitCode()).as(String.join("\n", second.printed())).isZero();
                    });
            claim("vespera serve is still serving",
                    () -> assertThat(serving.process().isAlive()).isTrue());
            claim("and the page it answers is now the one the second publish wrote",
                    () -> assertThat(page(port)).isEqualTo(SECOND_EDITION));
        } finally {
            serving.stop();
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("vespera serve makes no database and nothing else, reads no YAML file, and is not stopped by an OCR preset nothing resolves")
    void servingMakesNothingAndReadsNoFileOfConfiguration(@TempDir Path workingDirectory, @TempDir Path startedIn)
            throws Exception {
        aPublishedWebsiteOfOnePage(workingDirectory, FIRST_EDITION);
        Path onlyAFileNamesIt = startedIn.resolve("a-working-directory-only-a-file-names");
        Files.writeString(startedIn.resolve("application.yaml"), "vespera:\n  working-dir: "
                + onlyAFileNamesIt.toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Set<String> workingDirectoryBefore = filesUnder(workingDirectory);
        Set<String> startedInBefore = filesUnder(startedIn);
        int port = aFreePort();

        Serving serving = serve(startedIn, List.of(
                "-Dvespera.serve.port=" + port,
                "-D" + THE_OCR_PRESET + "=" + A_PLACEHOLDER_NOTHING_RESOLVES), port, "--db-dir=" + workingDirectory);
        String answered;
        try {
            answered = page(port);
            claim("vespera serve starts on the working directory --db-dir names, and not the one an application.yaml in"
                            + " the folder it was started in names, though an OCR preset nothing resolves is set,"
                            + " printing its one line and nothing else: no banner, no line of a context starting",
                    () -> assertThat(serving.printed()).containsExactly(servingLine(workingDirectory, port)));
            claim("and it answers the published page",
                    () -> assertThat(answered).isEqualTo(FIRST_EDITION));
        } finally {
            serving.stop();
        }

        claim("the working directory holds what it held: no " + DATABASE_FILE + ", no vespera.lock, no logs folder",
                () -> {
                    assertThat(workingDirectory.resolve(DATABASE_FILE)).doesNotExist();
                    assertThat(filesUnder(workingDirectory)).isEqualTo(workingDirectoryBefore);
                });
        claim("and the folder it was started in holds what it held, the file the jar never read and nothing else",
                () -> {
                    assertThat(filesUnder(startedIn)).isEqualTo(startedInBefore);
                    assertThat(onlyAFileNamesIt).doesNotExist();
                });
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("A working directory that is not there is refused in one line, exit 1, and not made")
    void aWorkingDirectoryNotThereIsRefusedAndNotMade(@TempDir Path base) throws Exception {
        Path notThere = base.resolve("not-there");

        Launched launched = launch(notThere, "serve");

        claim("vespera serve ends within " + BOUND_SECONDS + " seconds, with exit code 1",
                () -> {
                    assertThat(launched.finished()).isTrue();
                    assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
                });
        claim("having printed the one line saying the working directory is no folder that is there, and nothing else",
                () -> assertThat(launched.printed()).containsExactly(
                        "vespera serve has nothing to serve: " + notThere + " is no folder that is there."));
        claim("and the working directory was not made",
                () -> assertThat(notThere).doesNotExist());
    }

    // ---------- helpers ----------

    /** What a launch returned: whether it ended within the bound, its code, and every line it printed. */
    private record Launched(boolean finished, int exitCode, List<String> printed) {}

    /** A {@code vespera serve} running, and the lines it printed by the time it answered. */
    private record Serving(Process process, List<String> printed) {
        void stop() throws InterruptedException {
            process.destroyForcibly();
            process.waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** {@code vespera serve}'s line once it listens (ADR-333 §8). */
    private static String servingLine(Path workingDirectory, int port) {
        return "serving " + workingDirectory.resolve(WEBSITE) + " at http://127.0.0.1:" + port
                + "/ until this process ends";
    }

    /**
     * A generation run minted in {@code workingDirectory}'s ledger, its generation step finished, and the folder of its
     * website rendering, whose drawing finished: what a publish to the website looks for (ADR-333 §6, §11).
     */
    private static Path aGenerationRunDrawingOnePage(Path workingDirectory) throws IOException {
        DriverManagerDataSource database =
                new DriverManagerDataSource("jdbc:sqlite:" + workingDirectory.resolve(DATABASE_FILE) + "?foreign_keys=on");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(database);
        Ledger ledger = new Ledger(new JdbcTemplate(database));
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId run = ledger.runs().startRun(
                StageModules.GENERATION.stage(), "test", "a website served while published", walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path rendering = Files.createDirectories(
                DeliverableRoot.UNNAMED.treeOf(workingDirectory, run.value()).resolve(WEBSITE));
        Files.writeString(rendering.resolve(Plugin.DRAWING_STATE_FILE_NAME), Plugin.DRAWN + "\n");
        return rendering;
    }

    /** The rendering's one page, {@code index.html}, holding {@code page}, and its list naming it (ADR-339 §3). */
    private static void writeRendering(Path rendering, String page) throws IOException {
        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
        Files.write(rendering.resolve("index.html"), bytes);
        Files.writeString(rendering.resolve(FILES_JSON), "{\"format\":1,\"files\":[{\"path\":\"index.html\","
                + "\"source\":\"index.html\",\"size\":" + bytes.length + ",\"sha256\":\"" + sha256(bytes)
                + "\",\"kind\":\"page\"}]}\n", StandardCharsets.UTF_8);
    }

    /** A website of one page, {@code index.html}, as a publish leaves it: the marker, the page and its own list. */
    private static void aPublishedWebsiteOfOnePage(Path workingDirectory, String page) throws IOException {
        Path website = Files.createDirectories(workingDirectory.resolve(WEBSITE));
        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
        Files.writeString(website.resolve(DeliverableRoot.MARKER_FILE_NAME), "website\n", StandardCharsets.UTF_8);
        Files.write(website.resolve("index.html"), bytes);
        Files.writeString(website.resolve(VESPERA_WEBSITE_JSON), "{\"format\":1,\"files\":[{\"path\":\"index.html\","
                + "\"sha256\":\"" + sha256(bytes) + "\"}]}\n", StandardCharsets.UTF_8);
    }

    /** Holds the working directory in this process, as a running invocation would. */
    private static void hold(Path workingDirectory) {
        new WorkingDirectoryLock().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[] {"run", "the-first-invocation"},
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString())));
    }

    /** The page {@code /} answers, asked as a browser on this machine asks it. */
    private static String page(int port) throws IOException, InterruptedException {
        HttpResponse<String> answer = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (answer.statusCode() != 200) {
            throw new IllegalStateException("/ was answered " + answer.statusCode());
        }
        return answer.body();
    }

    /** A port nothing listens at on {@code 127.0.0.1} as this is asked. */
    private static int aFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    /**
     * Starts {@code vespera serve} in {@code startedIn}, with {@code properties} before {@code -jar} and {@code
     * arguments} after {@code serve}, and waits up to the bound for {@code port} to answer or the process to end.
     * What it printed is captured outside both folders, so that nothing the test writes is mistaken for what the jar
     * made.
     */
    private static Serving serve(Path startedIn, List<String> properties, int port, String... arguments)
            throws Exception {
        Path printed = Files.createTempFile("serve-holds-no-lock", ".out");
        Process process = builder(startedIn, properties, Stream.concat(Stream.of("serve"), Stream.of(arguments)).toList())
                .redirectErrorStream(true)
                .redirectOutput(printed.toFile())
                .start();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
        while (System.nanoTime() < until && process.isAlive() && !answers(port)) {
            Thread.sleep(200);
        }
        List<String> lines = Files.readAllLines(printed, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        Files.deleteIfExists(printed);
        return new Serving(process, lines);
    }

    private static boolean answers(int port) {
        try {
            page(port);
            return true;
        } catch (IOException | RuntimeException notYet) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Launches the packaged jar on {@code workingDirectory} and waits up to the bound for it to end. */
    private static Launched launch(Path workingDirectory, String... arguments) throws Exception {
        Path printed = Files.createTempFile("serve-holds-no-lock", ".out");
        try {
            Process process = builder(
                            Path.of("").toAbsolutePath(),
                            List.of("-Dvespera.working-dir=" + workingDirectory),
                            List.of(arguments))
                    .redirectErrorStream(true)
                    .redirectOutput(printed.toFile())
                    .start();
            boolean finished = process.waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            List<String> lines = Files.readAllLines(printed, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
            return new Launched(finished, finished ? process.exitValue() : -1, lines);
        } finally {
            Files.deleteIfExists(printed);
        }
    }

    private static ProcessBuilder builder(Path startedIn, List<String> properties, List<String> arguments) {
        List<String> command = Stream.of(
                        Stream.of(ProcessHandle.current().info().command().orElseThrow(),
                                "-Dstdout.encoding=UTF-8",
                                "-Dstderr.encoding=UTF-8",
                                "-Dspring.docker.compose.enabled=false",
                                "-Dvespera.sidecars.start=false"),
                        properties.stream(),
                        Stream.of("-jar", EXECUTABLE_JAR.toAbsolutePath().toString()),
                        arguments.stream())
                .flatMap(part -> part)
                .toList();
        ProcessBuilder builder = new ProcessBuilder(command).directory(startedIn.toFile());
        // The jar opens no browser on the machine running the tests (ADR-266 section 2).
        builder.environment().put("CI", "true");
        builder.environment().remove("VESPERA_WORKING_DIR");
        builder.environment().remove("VESPERA_SERVE_PORT");
        return builder;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }

    /** Every file and folder beneath {@code folder}, by its path relative to it with {@code /} between its names. */
    private static Set<String> filesUnder(Path folder) throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.filter(path -> !path.equals(folder))
                    .forEach(path -> found.add(folder.relativize(path).toString().replace('\\', '/')));
        }
        return found;
    }
}
