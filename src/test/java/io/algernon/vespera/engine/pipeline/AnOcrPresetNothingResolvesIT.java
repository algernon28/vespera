package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A placeholder nothing resolves in {@code vespera.docling.ocr-preset}, on the packaged jar (ADR-332 §2): {@code vespera
 * run} stops in the one line of ADR-330 §9 and {@code vespera label} and {@code vespera publish} are not stopped by it.
 * Launched, because a value taken by {@code @Value} stops the context in {@code SpringApplication.run}, ahead of every
 * command, and because a stack trace on the console and the code the shell sees lie outside any in-process test.
 *
 * <p>Four invocations. A run at the value that ships writes a label file, whose answers are then typed; then, naming the
 * placeholder, a run on a working directory of its own, a label recording those answers, and a publish, which finds
 * nothing drawn: what a publish does past that is not shown here. The converter is {@link LoopbackSidecar} and the
 * embedding model a loopback stand-in of Ollama's three endpoints the run calls, so it needs no Docker daemon.
 */
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-332", url = Adr.THE_LOCAL_MODEL_LABELS_ONLY_UNDER_THE_EXTRACTOR_ITS_RUN_WAS_CONVERTED_UNDER, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class AnOcrPresetNothingResolvesIT {

    /** Generous over a run of a few seconds on a handful of files; the point is only that the process ends. */
    private static final long EXIT_BOUND_SECONDS = 180;

    /** The packaged, executable jar the operator runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar").toAbsolutePath();

    /** The key this class is about. */
    private static final String THE_KEY = "vespera.docling.ocr-preset";

    /** A placeholder nothing on any machine names. */
    private static final String THE_PLACEHOLDER = "${NOTHING_NAMES_THIS}";

    /** How the refusal's line opens: the key, then the value in quotes. */
    private static final String THE_LINE_OPENS = THE_KEY + " is \"";

    /** How it ends, in the words of ADR-330 §9. */
    private static final String THE_LINE_ENDS = "\": one preset's name, of letters, digits, '-', '_' and '.', and never"
            + " auto, which the converter resolves where nothing records it";

    /** What a stack frame looks like in the output. */
    private static final String A_STACK_FRAME = "\tat ";

    /** The JDK's own opening on the lines it writes as sqlite-jdbc loads its library, in every invocation of the jar. */
    private static final String THE_JDKS_OWN_LINE = "WARNING: ";

    /** The file a started invocation writes (ADR-330 §1), which a refused one does not. */
    private static final String EFFECTIVE_VALUES = "effective-values.properties";

    /** The image the jar is told to expect and the loopback converter reports, so the run converts. */
    private static final String IMAGE = "vespera/an-ocr-preset-nothing-resolves:stub";

    /** The embedding model the profile names and the stand-in serves. */
    private static final String EMBEDDING_MODEL = "placeholder-embed:latest";

    /** How many numbers each vector the stand-in answers holds. */
    private static final int DIMENSION = 16;

    /** The corpus's documents, each converted to text of its own so that none is redundant with another. */
    private static final int CORPUS_DOCUMENTS = 6;

    /** The seed set's documents, numbered after the corpus's so that the converter tells them apart. */
    private static final int SEED_DOCUMENTS = 2;

    /** What a label file says of a document nobody has answered for, and what this test answers instead. */
    private static final String UNANSWERED = "relevant: null";

    private static final String ANSWERED = "relevant: true";

    @TempDir
    static Path temporary;

    private static LoopbackSidecar converter;
    private static HttpServer ollama;

    private static Path refusedWorkingDirectory;
    private static Launched runUnderThePlaceholder;
    private static Launched labelUnderThePlaceholder;
    private static Launched publishUnderThePlaceholder;

    /** What a launch printed, on each stream, without its blank lines, and how it ended. */
    private record Launched(boolean finished, int exitCode, List<String> out, List<String> err) {

        /** Standard error, without the lines the JDK writes of its own in every invocation. */
        List<String> errOfTheJar() {
            return err.stream().filter(line -> !line.startsWith(THE_JDKS_OWN_LINE)).toList();
        }

        Stream<String> everyLine() {
            return Stream.concat(out.stream(), err.stream());
        }
    }

    @BeforeAll
    static void launchTheFourInvocations() throws Exception {
        claim("the packaged jar exists, since this test launches it and needs verify to have built it",
                () -> assertThat(EXECUTABLE_JAR).isRegularFile());
        int converterPort = LoopbackSidecar.aFreePort();
        converter = LoopbackSidecar.startedOn(converterPort, IMAGE);
        ollama = ollamaStandIn();
        String converterUrl = "http://127.0.0.1:" + converterPort;
        String ollamaUrl = "http://127.0.0.1:" + ollama.getAddress().getPort();

        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));
        Path corpusRoot = Files.createDirectories(temporary.resolve("corpus"));
        Path seedFolder = Files.createDirectories(temporary.resolve("seeds"));
        for (int document = 1; document <= CORPUS_DOCUMENTS; document++) {
            writeDocument(corpusRoot.resolve("corpus-" + document + ".txt"), document);
        }
        for (int document = CORPUS_DOCUMENTS + 1; document <= CORPUS_DOCUMENTS + SEED_DOCUMENTS; document++) {
            writeDocument(seedFolder.resolve("seed-" + document + ".txt"), document);
        }
        Files.writeString(workingDirectory.resolve("profile.yaml"), """
                seedFolder:
                  value: "%s"
                  provenance: "the seed documents this test writes"
                boilerplateDocumentFrequencyFloor:
                  value: 0.9
                  provenance: "above every frequency of six distinct documents"
                embeddingModel:
                  value: "%s"
                  provenance: "the loopback stand-in's one model"
                """.formatted(seedFolder.toString().replace("\\", "\\\\"), EMBEDDING_MODEL));

        launch(converterUrl, ollamaUrl, List.of(), "run", "--db-dir=" + workingDirectory, corpusRoot.toString());
        Path labelFile = workingDirectory.resolve("relevance-labels.yaml");
        claim("the run at the value that ships wrote the label file, so that a label has answers to record",
                () -> assertThat(labelFile).isRegularFile());
        Files.writeString(labelFile, Files.readString(labelFile).replace(UNANSWERED, ANSWERED));

        List<String> thePlaceholder = List.of("-D" + THE_KEY + "=" + THE_PLACEHOLDER);
        refusedWorkingDirectory = Files.createDirectories(temporary.resolve("refused-working-directory"));
        runUnderThePlaceholder = launch(converterUrl, ollamaUrl, thePlaceholder,
                "run", "--db-dir=" + refusedWorkingDirectory, corpusRoot.toString());
        labelUnderThePlaceholder = launch(converterUrl, ollamaUrl, thePlaceholder,
                "label", "--db-dir=" + workingDirectory);
        publishUnderThePlaceholder = launch(converterUrl, ollamaUrl, thePlaceholder,
                "publish", "--db-dir=" + workingDirectory);
    }

    @AfterAll
    static void stopTheStandIns() {
        if (converter != null) {
            converter.close();
        }
        if (ollama != null) {
            ollama.stop(0);
        }
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A run under an OCR preset naming a placeholder nothing resolves stops in one line naming the key, with no stack trace and nothing minted")
    void aRunStopsInOneLine() throws SQLException {
        Launched launched = runUnderThePlaceholder;

        endedWith(launched, CommandLine.ExitCode.SOFTWARE, "the code a refused start returns");
        claim("standard error holds one line of the jar's, opening with the key and ending in the line's own words, which"
                        + " hold the placeholder as written between them",
                () -> {
                    assertThat(launched.errOfTheJar()).hasSize(1);
                    assertThat(launched.errOfTheJar().getFirst())
                            .startsWith(THE_LINE_OPENS)
                            .contains(THE_PLACEHOLDER)
                            .endsWith(THE_LINE_ENDS);
                });
        claim("no stack trace stands on either stream",
                () -> assertThat(launched.everyLine().filter(line -> line.startsWith(A_STACK_FRAME))).isEmpty());
        claim("no run was minted, the start having refused it", () -> assertThat(runsIn(refusedWorkingDirectory)).isZero());
        claim("and the file a started invocation writes was not written",
                () -> assertThat(refusedWorkingDirectory.resolve(EFFECTIVE_VALUES)).doesNotExist());
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A label under it records the answers typed in the file, with no line of the refusal and no stack trace")
    void aLabelIsNotStopped() {
        Launched launched = labelUnderThePlaceholder;
        String recorded = "answers are recorded; relevanceScoreFloor is not.";

        endedWith(launched, CommandLine.ExitCode.OK, "success");
        claim("the label says what it recorded",
                () -> assertThat(launched.out().stream().filter(line -> line.contains(recorded))).isNotEmpty());
        noLineOfTheRefusal(launched);
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A publish under it reaches its own refusal of nothing drawn, with no line of the refusal of the value and no stack trace")
    void aPublishIsNotStopped() {
        Launched launched = publishUnderThePlaceholder;
        String nothingDrawn = "vespera publish sent nothing: no Confluence rendering is drawn in this working directory."
                + " Run vespera run to the deliverable, and publish again.";

        endedWith(launched, CommandLine.ExitCode.SOFTWARE, "the code of its own refusal");
        claim("its one line on standard error is its own",
                () -> assertThat(launched.errOfTheJar()).containsExactly(nothingDrawn));
        noLineOfTheRefusal(launched);
    }

    private static void noLineOfTheRefusal(Launched launched) {
        claim("no line opens with the key, as the refusal does",
                () -> assertThat(launched.everyLine().filter(line -> line.startsWith(THE_KEY))).isEmpty());
        claim("and no stack trace stands on either stream",
                () -> assertThat(launched.everyLine().filter(line -> line.startsWith(A_STACK_FRAME))).isEmpty());
    }

    private static void endedWith(Launched launched, int exitCode, String meaning) {
        claim("the invocation ended within " + EXIT_BOUND_SECONDS + " seconds",
                () -> assertThat(launched.finished()).isTrue());
        claim("with exit code " + exitCode + ", " + meaning,
                () -> assertThat(launched.exitCode()).isEqualTo(exitCode));
    }

    private static int runsIn(Path workingDirectory) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + workingDirectory.resolve("vespera.db"));
                Statement statement = connection.createStatement();
                ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM run")) {
            count.next();
            return count.getInt(1);
        }
    }

    /** A document whose text names its number, which is how the loopback converter tells it apart. */
    private static void writeDocument(Path file, int document) throws IOException {
        Files.writeString(file, "document " + document + " of the placeholder test\n");
        converter.answering(document, "{\"status\":\"success\",\"errors\":[],\"processing_time\":0.1,"
                + "\"document\":{\"json_content\":{\"texts\":["
                + "{\"text\":\"Document " + document + "\",\"label\":\"title\"},"
                + "{\"text\":\"" + distinctText(document) + "\"}]}}}");
    }

    /** Words no other document of this test holds, so that no stage finds two documents the same. */
    private static String distinctText(int document) {
        StringBuilder text = new StringBuilder();
        for (int word = 0; word < 40; word++) {
            text.append("w").append(document).append("x").append(word).append(' ');
        }
        return text.toString().strip();
    }

    /**
     * Ollama's three endpoints the run calls, as {@link TheReportLineEndsTheOutputIT} has them: {@code /api/tags} naming
     * the one model, {@code /api/show} saying it is local, and {@code /api/embed} answering each input with a vector.
     */
    private static HttpServer ollamaStandIn() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/tags", exchange -> answer(exchange, "{\"models\":[{\"name\":\"" + EMBEDDING_MODEL
                + "\",\"digest\":\"placeholder-digest\",\"details\":{\"quantization_level\":\"F16\"}}]}"));
        server.createContext("/api/show", exchange -> {
            exchange.getRequestBody().readAllBytes();
            answer(exchange, "{}");
        });
        server.createContext("/api/embed", exchange -> {
            JsonNode input = json.readTree(exchange.getRequestBody().readAllBytes()).path("input");
            List<String> inputs = new ArrayList<>();
            if (input.isArray()) {
                input.forEach(each -> inputs.add(each.asString()));
            } else {
                inputs.add(input.asString());
            }
            StringBuilder body = new StringBuilder("{\"model\":\"" + EMBEDDING_MODEL + "\",\"embeddings\":[");
            for (int i = 0; i < inputs.size(); i++) {
                body.append(i == 0 ? "" : ",").append(vector(inputs.get(i)));
            }
            answer(exchange, body.append("]}").toString());
        });
        server.start();
        return server;
    }

    /** A vector drawn from the input's own characters, so that two inputs differ and each number is above 0. */
    private static String vector(String input) {
        StringBuilder vector = new StringBuilder("[");
        for (int i = 0; i < DIMENSION; i++) {
            int drawn = Math.floorMod(input.hashCode() * (31 * i + 17), 255);
            vector.append(i == 0 ? "" : ",").append(drawn / 255.0 + 0.01);
        }
        return vector.append(']').toString();
    }

    private static void answer(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Launches the packaged jar as the control panel does, with {@code CI} set, its two streams apart, and {@code
     * systemProperties} before the jar, and returns its lines that are not blank.
     */
    private static Launched launch(String converterUrl, String ollamaUrl, List<String> systemProperties, String... args)
            throws Exception {
        Path out = Files.createTempFile(temporary, "out", ".txt");
        Path err = Files.createTempFile(temporary, "err", ".txt");
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-Dspring.docker.compose.enabled=false",
                "-Dvespera.sidecars.start=false",
                "-Dvespera.sidecars.wait-at-most=1s",
                "-Dvespera.docling.base-url=" + converterUrl,
                "-Dvespera.docling.image=" + IMAGE,
                "-Dspring.ai.ollama.base-url=" + ollamaUrl));
        command.addAll(systemProperties);
        command.add("-jar");
        command.add(EXECUTABLE_JAR.toString());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(temporary.toFile())
                .redirectOutput(out.toFile())
                .redirectError(err.toFile());
        builder.environment().put("CI", "1");
        Process process = builder.start();
        boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        return new Launched(finished, finished ? process.exitValue() : -1, linesOf(out), linesOf(err));
    }

    private static List<String> linesOf(Path printed) throws IOException {
        return Files.readAllLines(printed, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
    }
}
