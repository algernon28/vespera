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
import picocli.CommandLine;

/**
 * An operator who keeps the working directory somewhere other than the default names it with
 * {@code --db-dir=<path>} on every invocation, including the one that records their answers (#310).
 *
 * <p>ADR-054 makes the working directory overridable per invocation on the command line, and the
 * README tells the operator to move it with {@code --db-dir=<path>}. {@code vespera label} is
 * invocation 3, and it refused the option outright, so an operator who had moved the working
 * directory with {@code --db-dir} could reach it from there only by also setting
 * {@code vespera.working-dir} in configuration.
 *
 * <p>The option is read twice (the property the datasource is built from, and the option picocli
 * parses), so an in-process test cannot see the property half: the context here is built with
 * {@code vespera.working-dir} already set, which is what {@code --db-dir=} resolves to in a real
 * launch. What it can see is the half that was missing — whether {@code label} accepts the option,
 * and whether it refuses one that disagrees with the directory it opened, as {@code run} does.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Labelling")
@Issue("310")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class LabelInAMovedWorkingDirectoryTest {

    /** The file the scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    /** The moved working directory: not the default, and named on every invocation. */
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
    @Story("The database directory can be named on every command")
    @DisplayName("A database directory that disagrees with the one actually opened stops the label command")
    void labelRefusesADatabaseDirectoryThatWasNotTheOneOpened(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path elsewhere) throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();

        cli.run("label", "--db-dir=" + elsewhere);

        claim(
                "the label command refuses rather than recording answers into one database while the"
                        + " operator believes they named another -- the same refusal the run command makes",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("and no answer was recorded", () -> assertThat(labelCount(seeds)).isZero());
    }

    /** Runs the pipeline in the moved directory far enough that a label file has been written there. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString(), "--db-dir=" + workingDirectory);
        claim(
                "the run in the moved directory reports success, so a label file is there to answer",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }

    /** Answers recorded about this test's own seed folder, since the database outlives each method. */
    private long labelCount(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ?",
                Long.class,
                Walk.canonicalRoot(seeds).toString());
    }
}
