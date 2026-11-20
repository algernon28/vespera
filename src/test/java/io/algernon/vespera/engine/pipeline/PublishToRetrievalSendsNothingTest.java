package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.publish.retrieval.RetrievalPlugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import picocli.CommandLine;

/**
 * {@code vespera publish --to retrieval} sends nothing (ADR-434 §8): it finds the rendering as every destination's is
 * found, writes nothing and opens no connection, and ends delivered, exit 0, with the one line {@code vespera publish
 * sends nothing to retrieval: its records are at <file>, a line each with its vector, for a store of yours to import.}
 *
 * <p>One invocation is one {@code publish} command line parsed by picocli over {@code VesperaCommand.Publish}, built
 * with the jar's own retrieval plugin; the rendering is laid out by hand in a generation run's tree.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class PublishToRetrievalSendsNothingTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The retrieval destination sends nothing")
    @DisplayName("A publish to retrieval ends delivered, exit 0, in one line naming the file, and writes nothing beneath the working directory")
    void aPublishToRetrievalSendsNothing() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId run = ledger.runs().startRun(
                StageModules.GENERATION.stage(), "test", "drawn retrieval " + System.nanoTime(), walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path folder = Files.createDirectories(workingDirectory.resolve("deliverable").resolve(run.value()).resolve("retrieval"));
        Path file = Files.writeString(folder.resolve("records.jsonl"), "{}\n");
        Files.writeString(folder.resolve(Plugin.DRAWING_STATE_FILE_NAME), Plugin.DRAWN + "\n");
        List<String> before = everyFile();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream was = System.out;
        int exit;
        try (PrintStream captured = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            VesperaCommand.Publish publish =
                    new VesperaCommand.Publish(ledger, List.of(new Confluence(), new RetrievalPlugin()), workingDirectory, "");
            exit = new CommandLine(publish).execute("--to", "retrieval");
        } finally {
            System.setOut(was);
        }
        List<String> said = out.toString(StandardCharsets.UTF_8).lines().toList();

        claim("the publish ended 0, the destination having delivered",
                () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.OK));
        claim("its closing line is the whole of the record's, naming the file the records stand in",
                () -> assertThat(said.getLast()).isEqualTo("vespera publish sends nothing to retrieval: its records are at "
                        + file + ", a line each with its vector, for a store of yours to import."));
        claim("and nothing was written beneath the working directory: every file is as it stood, and none is new",
                () -> assertThat(everyFile()).isEqualTo(before));
    }

    /** The destination a bare publish names, which the command is built with and a publish to retrieval never asks. */
    private record Confluence() implements Plugin {

        @Override
        public String name() {
            return "confluence";
        }

        @Override
        public String listName() {
            return "pages.json";
        }

        @Override
        public io.algernon.vespera.engine.deliverable.Drawing drawing(DrawingContext context) {
            throw new UnsupportedOperationException("a publish draws nothing");
        }

        @Override
        public Delivery deliver(Path rendering, String generationRun, Path workingDirectory,
                java.util.function.Consumer<String> out) {
            throw new UnsupportedOperationException("a publish to retrieval asks no other destination");
        }
    }

    private List<String> everyFile() throws IOException {
        try (Stream<Path> walk = Files.walk(workingDirectory)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> workingDirectory.relativize(path) + " " + sizeOf(path))
                    .sorted()
                    .toList();
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
