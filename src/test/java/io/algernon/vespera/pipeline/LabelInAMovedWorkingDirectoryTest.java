package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
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
 * invocation 3, and it refused the option outright, so the only way to reach a moved working
 * directory from it was a property the README never named for that case.
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

    /** A floor of 1.0 opens stage 4's gate, the way every stage-5 invocation fixture does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The file the scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    /** The fixture corpus is one document, so one answer is every answer there is to give. */
    private static final int EVERY_ANSWER = 1;

    /** Built rather than written literally, so the fixture carries no escaped quotes. */
    private static final String QUOTE = String.valueOf('"');

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
    @DisplayName("Answers given after a run in a moved directory are recorded when the label command names it too")
    void labelReachesTheWorkingDirectoryTheRunWasGiven(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();

        cli.run("label", "--db-dir=" + workingDirectory);

        claim(
                "the label command accepts the same database directory the run was given, and reports"
                        + " success -- an operator who moved the directory names it on every command, and"
                        + " refusing it here left no documented way to reach it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the " + EVERY_ANSWER + " answer written into that directory's label file is now a row",
                () -> assertThat(labelCount(seeds)).isEqualTo(EVERY_ANSWER));
    }

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

    @Test
    @Story("The database directory can be named on every command")
    @DisplayName("A label file kept in another directory is checked against the questions the opened directory asked")
    void aLabelFileFromAnotherDirectoryIsCheckedAgainstTheOneOpened(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path elsewhere) throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();
        Path foreign = elsewhere.resolve(LABEL_FILE);
        String foreignStamp = "generatedUnderRun: " + QUOTE + "a-run-another-directory-holds" + QUOTE;
        Files.writeString(
                foreign,
                Files.readString(workingDirectory.resolve(LABEL_FILE))
                        .replaceFirst("generatedUnderRun: .*", foreignStamp));

        cli.run("label", "--db-dir=" + workingDirectory, foreign.toString());

        claim(
                "the file is refused: where it sits says nothing about which database it belongs to, so"
                        + " it is checked against the questions the opened directory's last run asked,"
                        + " and these answers are to questions that run never asked",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and no answer was recorded", () -> assertThat(labelCount(seeds)).isZero());
    }

    /** Runs the pipeline in the moved directory far enough that a label file has been written there. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
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
