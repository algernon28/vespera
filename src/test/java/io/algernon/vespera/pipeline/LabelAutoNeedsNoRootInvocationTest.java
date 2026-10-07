package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * {@code vespera label --auto} needs no corpus root, and {@code --root} is no longer an option of
 * {@code label}, with {@code --auto} or without (ADR-208).
 *
 * <p>Written before ADR-208's change to {@code VesperaCommand.Label}, when the command refused with no
 * root and accepted {@code --root} on both forms, so every test here failed. Until that change landed
 * the file was kept at {@code docs/adr/0208/tests/} followed by this path; it moved here with the
 * change, unedited, and every test passes since (#451).
 *
 * <p>What is claimed of the refused option is what is this project's: the exit code, that nothing was
 * labelled or asked, and that standard error names the option. The sentence around the name is
 * picocli's and is not held.
 *
 * <p>No corpus root is configured in this context: {@code application-test.yaml} binds
 * {@code vespera.corpus-root} empty, and the first test claims it. The class has its own working
 * directory and so its own context and its own in-memory database; each test scores a corpus and a seed
 * set of its own, which rewrites the profile and the label file the tests share, and counts only the
 * answers recorded for its own seed set; and the labeller's record of what it was asked is this class's
 * and is emptied before each test.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, LabelAutoNeedsNoRootInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Local labelling")
@Issue("451")
@Link(name = "ADR-208", url = Adr.LABEL_AUTO_NEEDS_NO_CORPUS_ROOT, type = "adr")
@ExtendWith(OutputCaptureExtension.class)
class LabelAutoNeedsNoRootInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String LABEL_FILE = "relevance-labels.yaml";
    private static final String ONE_DOCUMENT_IN_THE_SAMPLE = "the fixture corpus holds one document";

    /** The words every scripted conversion carries, so finding them means a document's opening was put. */
    private static final String WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH = "stubbed but real content";

    /** The option ADR-208 removed from {@code label}. */
    private static final String THE_REMOVED_OPTION = "--root";

    /** How the refusal ADR-208 removed opened. */
    private static final String HOW_THE_REMOVED_REFUSAL_OPENS = "vespera label --auto named no root";

    /** The opening each question put to the scripted labeller, empty where none could be read. */
    static final List<Optional<String>> OPENINGS_PUT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "scripted-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    OPENINGS_PUT.add(question.opening());
                    return Optional.of(true);
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private VesperaCommand command;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void forgetQuestions() {
        OPENINGS_PUT.clear();
    }

    @Test
    @Story("Labelling opens nothing under the archive")
    @DisplayName("With no folder named for the archive and none configured, the model labels")
    void runsWithNoRootNamedAndNoneConfigured(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        claim(
                "no corpus root is bound in this context, the precondition the rest of this test rests"
                        + " on: with one bound, the command was never refused in the first place",
                () -> assertThat(environment.getProperty(VesperaCommand.Run.ROOT_PROPERTY)).isBlank());
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto");

        claim(
                "the invocation reports success, as any labelling that recorded its answers does",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.OK));
        claim(
                "the model was asked about the one document in the sample (" + ONE_DOCUMENT_IN_THE_SAMPLE
                        + "), and the question carried its opening",
                () -> assertThat(OPENINGS_PUT)
                        .singleElement()
                        .satisfies(opening -> assertThat(opening)
                                .hasValueSatisfying(text ->
                                        assertThat(text).contains(WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH))));
        claim(
                "and nothing on standard error asks for a folder",
                () -> assertThat(output.getErr()).doesNotContain(HOW_THE_REMOVED_REFUSAL_OPENS));
    }

    @Test
    @Story("A command accepts only what it uses")
    @DisplayName("Labelling by the model, given a folder as the archive's, is refused as a usage error before anything is asked")
    void refusesTheRemovedOptionWithAuto(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);
        byte[] before = Files.readAllBytes(workingDirectory.resolve(LABEL_FILE));

        cli.run("label", "--auto", THE_REMOVED_OPTION, root.toString());

        claim(
                "the invocation reports a usage error, which says the command line is what to change",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
        claim(
                "standard error names the option that was not accepted",
                () -> assertThat(output.getErr()).contains(THE_REMOVED_OPTION));
        claim("the model was asked nothing", () -> assertThat(OPENINGS_PUT).isEmpty());
        claim(
                "no answer is recorded for this test's seed folder, and the label file is byte for byte"
                        + " as the scoring left it",
                () -> {
                    assertThat(answersRecorded(seeds)).isZero();
                    assertThat(Files.readAllBytes(workingDirectory.resolve(LABEL_FILE))).isEqualTo(before);
                });
    }

    @Test
    @Story("A command accepts only what it uses")
    @DisplayName("Recording a person's answers, given a folder as the archive's, is refused as a usage error and records nothing")
    void refusesTheRemovedOptionWithoutAuto(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));

        cli.run("label", THE_REMOVED_OPTION, root.toString());

        claim(
                "the invocation reports a usage error: the option was ignored in silence here before, and"
                        + " is now refused as it is everywhere else",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
        claim(
                "standard error names the option that was not accepted",
                () -> assertThat(output.getErr()).contains(THE_REMOVED_OPTION));
        claim(
                "the answer typed into the file (" + ONE_DOCUMENT_IN_THE_SAMPLE + ") is not recorded: a"
                        + " command line that is refused does nothing",
                () -> assertThat(answersRecorded(seeds)).isZero());
        claim("and no model was asked", () -> assertThat(OPENINGS_PUT).isEmpty());
    }

    @Test
    @Story("A command accepts only what it uses")
    @DisplayName("The help for recording labels no longer lists an option for the archive's folder")
    void theLabelCommandNoLongerListsTheOption() {
        CommandLine label = command.commandLine().getSubcommands().get("label");

        claim(
                "the command declares no such option",
                () -> assertThat(label.getCommandSpec().findOption(THE_REMOVED_OPTION)).isNull());
        claim(
                "and the usage text an operator is shown does not mention it",
                () -> assertThat(label.getUsageMessage()).doesNotContain(THE_REMOVED_OPTION));
    }

    /** One document, one seed, every gate up to the label file open; the root is named on the command line. */
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
        cli.run("run", root.toString());
    }

    private long answersRecorded(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ?",
                Long.class,
                io.algernon.vespera.corpus.Walk.canonicalRoot(seeds).toString());
    }
}
