package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
 * The last line of output that is not blank, on standard output and standard error together, is the
 * command's own closing line: nothing a library logs while the job ends or the context closes follows it
 * (ADR-281, #579). The operator is told to read that line (ADR-158), and the control panel shows it as the
 * invocation's outcome (ADR-278).
 *
 * <p>Pinned on the packaged jar, started as the control panel starts it, because the lines that followed
 * the closing line are written by the console appender of {@code logback-spring.xml}, which no in-process
 * test reads, and while {@code SpringApplication.exit} closes the context in {@code VesperaApplication.main}.
 * Four invocations: {@code run} ending on a value to set (exit 0), {@code run} whose stage 2 fails (exit 1),
 * and {@code label} recording answers (exit 0) and refused for a label file that is not there (exit 2).
 * The converter is {@link LoopbackSidecar}, and the embedding model a loopback stand-in of Ollama's three
 * endpoints the run calls, so it needs no Docker daemon.
 */
@Epic("Architecture")
@Feature("Process exit")
@Issue("579")
@Link(name = "ADR-281", url = Adr.THE_CLOSING_LINE_IS_THE_LAST_LINE, type = "adr")
@Link(name = "ADR-158", url = Adr.THE_PACKAGED_JAR_STARTS_NO_SIDECAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class TheClosingLineIsTheLastLineIT {

    /** Generous over a run of a few seconds on a handful of files; the point is only that the process ends. */
    private static final long EXIT_BOUND_SECONDS = 180;

    /** The packaged, executable jar the operator runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar").toAbsolutePath();

    /** The image the jar is told to expect and the loopback converter reports, so the run converts. */
    private static final String IMAGE = "vespera/the-closing-line-is-the-last-line:stub";

    /** The embedding model the profile names and the stand-in serves. */
    private static final String EMBEDDING_MODEL = "closing-line-embed:latest";

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

    private static Printed runEndingOnAValueToSet;
    private static Printed labelRecording;
    private static Printed labelRefused;
    private static Printed runWhoseStageTwoFails;
    private static Printed runWhoseEmbeddingFails;
    private static HttpServer brokenOllama;
    private static Path missingLabelFile;

    /** What a launch printed, standard error merged into standard output, and how it ended. */
    private record Printed(boolean finished, int exitCode, List<String> lines) {

        /** The last line that is not blank. */
        String lastLine() {
            return lines.isEmpty() ? "" : lines.getLast();
        }

        boolean anyLine(Predicate<String> line) {
            return lines.stream().anyMatch(line);
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

        runEndingOnAValueToSet = launch(converterUrl, ollamaUrl,
                "run", "--db-dir=" + workingDirectory, corpusRoot.toString());

        Path labelFile = workingDirectory.resolve("relevance-labels.yaml");
        claim("the run ending on a value to set wrote the label file, so that label has answers to record",
                () -> assertThat(labelFile).isRegularFile());
        Files.writeString(labelFile, Files.readString(labelFile).replace(UNANSWERED, ANSWERED));
        labelRecording = launch(converterUrl, ollamaUrl, "label", "--db-dir=" + workingDirectory);

        Path refusedWorkingDirectory = Files.createDirectories(temporary.resolve("refused-working-directory"));
        missingLabelFile = temporary.resolve("no-such-label-file.yaml");
        labelRefused = launch(converterUrl, ollamaUrl,
                "label", "--db-dir=" + refusedWorkingDirectory, missingLabelFile.toString());

        Path failingWorkingDirectory = Files.createDirectories(temporary.resolve("failing-working-directory"));
        String nothingListening = "http://127.0.0.1:" + LoopbackSidecar.aFreePort();
        runWhoseStageTwoFails = launch(nothingListening, ollamaUrl,
                "run", "--db-dir=" + failingWorkingDirectory, corpusRoot.toString());

        brokenOllama = ollamaWhoseEmbeddingFails();
        Path embeddingWorkingDirectory = Files.createDirectories(temporary.resolve("embedding-working-directory"));
        Files.copy(workingDirectory.resolve("profile.yaml"), embeddingWorkingDirectory.resolve("profile.yaml"));
        runWhoseEmbeddingFails = launch(converterUrl, "http://127.0.0.1:" + brokenOllama.getAddress().getPort(),
                "run", "--db-dir=" + embeddingWorkingDirectory, corpusRoot.toString());
    }

    @AfterAll
    static void stopTheStandIns() {
        if (converter != null) {
            converter.close();
        }
        if (ollama != null) {
            ollama.stop(0);
        }
        if (brokenOllama != null) {
            brokenOllama.stop(0);
        }
    }

    @Test
    @Story("The closing line is the last line")
    @DisplayName("vespera run that ends on a value to set prints its line naming that value last")
    void aRunEndingOnAValueToSetPrintsItsClosingLineLast() {
        Printed printed = runEndingOnAValueToSet;
        String closingLine = "VesperaCommand$Run - ";
        String naming = " Next: answer the questions in relevance-labels.yaml";

        endedWith(printed, CommandLine.ExitCode.OK, "success");
        claim("the run printed its closing line, naming the label file as what to answer next",
                () -> assertThat(printed.anyLine(line -> line.contains(closingLine) && line.contains(naming)))
                        .isTrue());
        claim("and that line is the last line that is not blank, standard output and standard error together",
                () -> assertThat(printed.lastLine()).contains(closingLine).contains(naming));
    }

    @Test
    @Story("The closing line is the last line")
    @DisplayName("vespera run whose stage 2 fails prints the line saying so last")
    void aRunWhoseStageTwoFailsPrintsItsClosingLineLast() {
        Printed printed = runWhoseStageTwoFails;
        String closingLine = "ExtractionHealthCheckListener - Stage 2 (extraction) failed and is not recorded as finished";
        String advice = "and run the same command again.";

        endedWith(printed, CommandLine.ExitCode.SOFTWARE, "a failure");
        claim("the run printed the line saying stage 2 failed and what to do about it",
                () -> assertThat(printed.anyLine(line -> line.contains(closingLine) && line.endsWith(advice)))
                        .isTrue());
        claim("and that line is the last line that is not blank, standard output and standard error together",
                () -> assertThat(printed.lastLine()).contains(closingLine).endsWith(advice));
    }

    @Test
    @Story("The closing line is the last line")
    @Issue("585")
    @Link(name = "ADR-289", url = Adr.A_FAILING_STEP_ENDS_ON_ITS_OWN_CLOSING_LINE, type = "adr")
    @DisplayName("vespera run whose embedding-scoring step fails prints the line saying so last")
    void aRunWhoseEmbeddingStepFailsPrintsItsClosingLineLast() {
        Printed printed = runWhoseEmbeddingFails;
        String closingLine = "embedding-scoring failed and is not recorded as finished";
        String advice = "and run the same command again.";

        endedWith(printed, CommandLine.ExitCode.SOFTWARE, "a failure");
        claim("the run printed the line saying the embedding-scoring step failed and what to do about it",
                () -> assertThat(printed.anyLine(line -> line.contains(closingLine) && line.endsWith(advice)))
                        .isTrue());
        claim("and that line is the last line that is not blank, standard output and standard error together",
                () -> assertThat(printed.lastLine()).contains(closingLine).endsWith(advice));
    }

    @Test
    @Story("The closing line is the last line")
    @DisplayName("vespera label that records answers prints its line naming the next value last")
    void aLabelThatRecordsPrintsItsClosingLineLast() {
        Printed printed = labelRecording;
        String recorded = "answers are recorded; relevanceScoreFloor is not. Next: read a number off"
                + " relevance-labelling.html";

        endedWith(printed, CommandLine.ExitCode.OK, "success");
        claim("label printed its closing line, saying the answers are recorded and naming the floor to set next",
                () -> assertThat(printed.anyLine(line -> line.contains(recorded))).isTrue());
        claim("and that line is the last line that is not blank, standard output and standard error together",
                () -> assertThat(printed.lastLine()).contains(recorded));
    }

    @Test
    @Story("The closing line is the last line")
    @DisplayName("vespera label refused for a label file that is not there prints its refusal last")
    void aLabelRefusedForAMissingFilePrintsItsRefusalLast() {
        Printed printed = labelRefused;
        String refusal = "vespera label recorded nothing: no label file to read at " + missingLabelFile;

        endedWith(printed, CommandLine.ExitCode.USAGE, "the usage code, a refusal of what was named");
        claim("label printed its refusal, naming the file that is not there",
                () -> assertThat(printed.anyLine(line -> line.startsWith(refusal))).isTrue());
        claim("and the refusal is the last line that is not blank, standard output and standard error together",
                () -> assertThat(printed.lastLine()).startsWith(refusal));
    }

    private static void endedWith(Printed printed, int exitCode, String meaning) {
        claim("the invocation ended within " + EXIT_BOUND_SECONDS + " seconds",
                () -> assertThat(printed.finished()).isTrue());
        claim("with exit code " + exitCode + ", " + meaning,
                () -> assertThat(printed.exitCode()).isEqualTo(exitCode));
    }

    /** A document whose text names its number, which is how the loopback converter tells it apart. */
    private static void writeDocument(Path file, int document) throws IOException {
        Files.writeString(file, "document " + document + " of the closing line test\n");
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
     * Ollama's three endpoints the run calls: {@code /api/tags} naming the one model, {@code /api/show}
     * saying it is local, and {@code /api/embed} answering each input with a vector drawn from its hash.
     */
    private static HttpServer ollamaStandIn() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/tags", exchange -> answer(exchange, "{\"models\":[{\"name\":\"" + EMBEDDING_MODEL
                + "\",\"digest\":\"closing-line-digest\",\"details\":{\"quantization_level\":\"F16\"}}]}"));
        server.createContext("/api/show", exchange -> {
            exchange.getRequestBody().readAllBytes();
            answer(exchange, "{}");
        });
        server.createContext("/api/embed", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
            JsonNode input = request.path("input");
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

    /** As {@link #ollamaStandIn()}, except that every embedding call fails with a server error. */
    private static HttpServer ollamaWhoseEmbeddingFails() throws IOException {
        HttpServer server = ollamaStandIn();
        server.removeContext("/api/embed");
        server.createContext("/api/embed", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        return server;
    }

    private static String vector(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder vector = new StringBuilder("[");
            for (int i = 0; i < DIMENSION; i++) {
                vector.append(i == 0 ? "" : ",").append((hash[i] & 0xff) / 255.0 + 0.01);
            }
            return vector.append(']').toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
     * Launches the packaged jar as the control panel does, with the two encoding flags, {@code CI} set and
     * standard error merged into standard output, and returns its lines that are not blank.
     */
    private static Printed launch(String converterUrl, String ollamaUrl, String... args) throws Exception {
        Path printed = Files.createTempFile(temporary, "printed", ".out");
        ProcessBuilder builder = new ProcessBuilder(Stream.concat(
                                Stream.of(
                                        ProcessHandle.current().info().command().orElseThrow(),
                                        "-Dstdout.encoding=UTF-8",
                                        "-Dstderr.encoding=UTF-8",
                                        "-Dspring.docker.compose.enabled=false",
                                        "-Dvespera.docling.base-url=" + converterUrl,
                                        "-Dvespera.docling.image=" + IMAGE,
                                        "-Dspring.ai.ollama.base-url=" + ollamaUrl,
                                        "-jar", EXECUTABLE_JAR.toString()),
                                Stream.of(args))
                        .toList())
                .directory(temporary.toFile())
                .redirectErrorStream(true)
                .redirectOutput(printed.toFile());
        builder.environment().put("CI", "1");
        Process process = builder.start();
        boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        List<String> lines = Files.readAllLines(printed, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        return new Printed(finished, finished ? process.exitValue() : -1, lines);
    }
}
