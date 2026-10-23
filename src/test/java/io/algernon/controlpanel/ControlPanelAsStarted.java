package io.algernon.controlpanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * The control panel as the operator starts it (ADR-325 §9): {@code java -jar} on a jar of the control panel's compiled
 * classes whose manifest names the jar for the screen tests as the one beside it, so that {@code ControlPanel.main}
 * runs as shipped and a Run would start {@link JarForScreenTests}, never Vespera.
 *
 * <p>It runs with {@code CI=1}, so it opens no browser (ADR-325 §7), with {@code java.io.tmpdir} a folder of the
 * test's own, so that its token and its launch values are kept there and never in the user's own folder, and with the
 * corpus-root locks read in a folder of the test's own. What it writes on each stream is read as it comes, each line of
 * standard output with the moment it was read. Closing it ends its process forcibly and waits for it to end.
 */
final class ControlPanelAsStarted implements AutoCloseable {

    /** The environment variable that names the port the control panel listens at (ADR-325 §1). */
    static final String PORT_VARIABLE = "VESPERA_CONTROL_PANEL_PORT";

    /** What ADR-288 §4's line, naming the jar it will start, begins with. */
    static final String WILL_START = "the control panel will start ";

    /** What ADR-302 §11's line, the address to open, begins with, before the port. */
    static final String OPEN = "open http://127.0.0.1:";

    /** What that line ends with, after the token. */
    static final String IN_YOUR_BROWSER = " in your browser";

    /** How long a test waits for a line, or for the process to end, before it fails rather than hangs. */
    static final Duration BOUND = Duration.ofSeconds(30);

    /** The name the manifest gives the jar beside the control panel's own. */
    private static final String JAR_BESIDE_IT = "vespera-for-screen-tests.jar";

    /** The file the token is kept in, beside the launch values (ADR-325 §2). */
    private static final String TOKEN_FILE = "token";

    /** Variables a JVM announces on standard error as it starts, which would be lines the control panel did not write. */
    private static final List<String> JVM_NOTICES = List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

    /** How long a look for a line waits before it looks again. */
    private static final long LOOK_EVERY_MILLIS = 50;

    /** The jar of the control panel's classes, once written for the test JVM. */
    private static Path jar;

    /** A line of standard output, with the moment it was read, from {@link System#nanoTime()}. */
    record Line(String text, long readAtNanos) {
    }

    private final Process process;

    private final List<Line> output = new CopyOnWriteArrayList<>();

    private final List<String> errors = new CopyOnWriteArrayList<>();

    private ControlPanelAsStarted(Process process) {
        this.process = process;
        Thread.ofVirtual().start(() -> read(process.getInputStream(),
                line -> output.add(new Line(line, System.nanoTime()))));
        Thread.ofVirtual().start(() -> read(process.getErrorStream(), errors::add));
    }

    /**
     * Starts the control panel with {@code temporaryDirectory} as its {@code java.io.tmpdir} and
     * {@link #PORT_VARIABLE} set to {@code port}, or absent from its environment where {@code port} is null.
     */
    static ControlPanelAsStarted start(Path temporaryDirectory, String port) throws IOException {
        Files.createDirectories(temporaryDirectory);
        Path corpusRootLockFolder = Files.createDirectories(temporaryDirectory.resolve("root-locks"));
        ProcessBuilder builder = new ProcessBuilder(ScreenServer.runtime().java().toString(),
                "-Djava.io.tmpdir=" + temporaryDirectory.toAbsolutePath(), "-jar", theJar().toString())
                .directory(temporaryDirectory.toFile());
        JVM_NOTICES.forEach(builder.environment()::remove);
        builder.environment().put("CI", "1");
        builder.environment().put(ControlPanel.CORPUS_ROOT_LOCK_FOLDER_VARIABLE, corpusRootLockFolder.toString());
        if (port == null) {
            builder.environment().remove(PORT_VARIABLE);
        } else {
            builder.environment().put(PORT_VARIABLE, port);
        }
        return new ControlPanelAsStarted(builder.start());
    }

    /** The first line of standard output {@code wanted} accepts, waiting up to {@code bound}; empty where none came. */
    Optional<Line> awaitLine(Predicate<String> wanted, Duration bound) throws InterruptedException {
        long giveUp = System.nanoTime() + bound.toNanos();
        while (true) {
            Optional<Line> found = output.stream().filter(line -> wanted.test(line.text())).findFirst();
            if (found.isPresent() || System.nanoTime() > giveUp) {
                return found;
            }
            Thread.sleep(LOOK_EVERY_MILLIS);
        }
    }

    /** The first line of standard error {@code wanted} accepts, waiting up to {@code bound}; empty where none came. */
    Optional<String> awaitError(Predicate<String> wanted, Duration bound) throws InterruptedException {
        long giveUp = System.nanoTime() + bound.toNanos();
        while (true) {
            Optional<String> found = errors.stream().filter(wanted).findFirst();
            if (found.isPresent() || System.nanoTime() > giveUp) {
                return found;
            }
            Thread.sleep(LOOK_EVERY_MILLIS);
        }
    }

    /** The address line ADR-302 §11 gives; the test fails where none came within {@link #BOUND}. */
    String addressLine() throws InterruptedException {
        return awaitLine(line -> line.startsWith(OPEN), BOUND).map(Line::text).orElseThrow(() -> new AssertionError(
                "the control panel printed no address to open within " + BOUND.toSeconds() + " seconds; it wrote "
                        + texts() + " and on standard error " + errors));
    }

    /** Every line of standard output read so far, in order. */
    List<String> texts() {
        return output.stream().map(Line::text).toList();
    }

    /** Every line of standard output read so far, with the moment each was read. */
    List<Line> lines() {
        return List.copyOf(output);
    }

    /** Every line of standard error read so far that is not blank, in order. */
    List<String> errorLines() {
        return errors.stream().filter(line -> !line.isBlank()).toList();
    }

    /** Whether the process ended within {@code bound}; waiting longer reads what it wrote last. */
    boolean awaitEnd(Duration bound) throws InterruptedException {
        boolean ended = process.waitFor(bound.toMillis(), TimeUnit.MILLISECONDS);
        if (ended) {
            Thread.sleep(LOOK_EVERY_MILLIS * 4);
        }
        return ended;
    }

    /** The exit code, once the process has ended. */
    int exitCode() {
        return process.exitValue();
    }

    @Override
    public void close() throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(BOUND.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** The address an address line names, the token in its query. */
    static URI addressIn(String addressLine) {
        return URI.create(addressLine.substring("open ".length(), addressLine.length() - IN_YOUR_BROWSER.length()));
    }

    /** The port an address line names. */
    static int portOf(String addressLine) {
        return addressIn(addressLine).getPort();
    }

    /** The token an address line names. */
    static String tokenOf(String addressLine) {
        String query = addressIn(addressLine).getRawQuery();
        return query == null ? "" : query.substring("token=".length());
    }

    /** The token's file a control panel started with {@code temporaryDirectory} keeps it in (ADR-325 §2). */
    static Path tokenFile(Path temporaryDirectory) {
        return LaunchValues.fileIn(temporaryDirectory, System.getProperty("user.name")).resolveSibling(TOKEN_FILE);
    }

    /** What the token's file holds, whole; empty where there is no such file. */
    static String tokenFileHolds(Path temporaryDirectory) throws IOException {
        Path file = tokenFile(temporaryDirectory);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    /** A port of {@code 127.0.0.1} free as this is called: one the system picked, then let go. */
    static int freePort() throws IOException {
        try (ServerSocket free = new ServerSocket(0, 1, loopback())) {
            return free.getLocalPort();
        }
    }

    /** Whether {@code port} of {@code 127.0.0.1} can be bound as this is called. */
    static boolean isFree(int port) {
        try (ServerSocket attempt = new ServerSocket()) {
            attempt.bind(new InetSocketAddress(loopback(), port));
            return true;
        } catch (IOException taken) {
            return false;
        }
    }

    /** {@code 127.0.0.1}, by its address and not its name. */
    static InetAddress loopback() throws IOException {
        return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    }

    private static void read(InputStream stream, Consumer<String> line) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String read;
            while ((read = reader.readLine()) != null) {
                line.accept(read);
            }
        } catch (IOException ended) {
            // the process ended, and its stream with it
        }
    }

    /**
     * The jar of the control panel's compiled classes, beside a copy of the jar for the screen tests under the name its
     * manifest gives, written once for the test JVM in a folder of its own.
     */
    private static synchronized Path theJar() throws IOException {
        if (jar == null) {
            Path folder = Files.createTempDirectory("vespera-control-panel-as-started");
            folder.toFile().deleteOnExit();
            Path beside = Files.copy(ScreenServer.runtime().jar(), folder.resolve(JAR_BESIDE_IT));
            beside.toFile().deleteOnExit();
            Path written = folder.resolve("vespera-control-panel-as-started.jar");
            writeTheJar(written);
            written.toFile().deleteOnExit();
            jar = written;
        }
        return jar;
    }

    private static void writeTheJar(Path written) throws IOException {
        Path classes;
        try {
            classes = Path.of(ControlPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IOException("the control panel's classes are at no path", e);
        }
        Path ownPackage = classes.resolve(ControlPanel.class.getPackageName().replace('.', '/'));
        if (!Files.isDirectory(ownPackage)) {
            throw new IOException("the control panel's classes are not in a folder of classes: " + classes);
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, ControlPanel.class.getName());
        manifest.getMainAttributes().put(new Attributes.Name("Vespera-Jar"), JAR_BESIDE_IT);
        try (OutputStream file = Files.newOutputStream(written);
                JarOutputStream out = new JarOutputStream(file, manifest);
                Stream<Path> found = Files.walk(ownPackage)) {
            for (Path compiled : found.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(compiled).toString().replace('\\', '/')));
                Files.copy(compiled, out);
                out.closeEntry();
            }
        }
    }
}
