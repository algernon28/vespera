package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
/**
 * Ingesting a completed label file through the command an operator actually types (ADR-088, #105).
 *
 * <p>Label ingestion is its own subcommand rather than a step in the job, because it is a different
 * act: a person invokes it, having just finished labelling, and it writes rows under no run. The
 * claims here are about that act -- what it records, what it refuses, and that it mints nothing.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("111")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class LabelIngestionInvocationTest {

    /** The file the scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    /** The one corpus document this fixture writes, and so the one thing there is to answer about. */
    private static final String CORPUS_DOCUMENT = "corpus.txt";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Answers become rows, and nothing else changes")
    @DisplayName("A completed file is ingested, its answers recorded, and no run is minted for it")
    void ingestsACompletedFileAndMintsNoRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        long runsBefore = runCount();
        answerEveryQuestion();

        cli.run("label");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the answer a person gave is now a row, which is the whole point of the command",
                () -> assertThat(labelCount(seeds)).isEqualTo(1));
        claim(
                "and no run was minted for it: a label is a fact about a document rather than something"
                        + " derived under a configuration, so nothing about ingesting one is a run",
                () -> assertThat(runCount()).isEqualTo(runsBefore));
        claim(
                "nor did it write a verdict of any kind -- the floor that reads these answers into a"
                        + " removal is a later ticket, and until it lands nothing here removes anything",
                () -> assertThat(verdictCount()).isZero());
    }

    /** Runs the pipeline far enough that a sample exists and a label file has been written. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(CORPUS_DOCUMENT), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }

    /**
     * Answers recorded about this test's own seed folder. The database outlives each test method, so
     * counting every row would count another test's answers -- and scoping by seed set is also what
     * a label is keyed by, so the count asks the same question the table answers.
     */
    private long labelCount(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ?",
                Long.class,
                io.algernon.vespera.corpus.Walk.canonicalRoot(seeds).toString());
    }

    private long runCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run", Long.class);
    }

    private long verdictCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict", Long.class);
    }
}
