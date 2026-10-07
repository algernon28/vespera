package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.nio.file.InvalidPathException;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import picocli.CommandLine;

/**
 * {@code vespera label --auto} needs no corpus root, and says so once when it is given one (ADR-208,
 * sections 1, 2 and 6).
 *
 * <p><b>Parked.</b> Every test here fails until ADR-208's change to {@code VesperaCommand.Label} lands:
 * today the command refuses with no root, writes no line, has picocli convert the option to a path, and
 * describes the option as the folder the label file's documents are under. This file is kept at
 * {@code docs/adr/0208/tests/} followed by the path it takes in the repository, and moves into
 * {@code src/test} with that change.
 *
 * <p>No corpus root is configured in this context: {@code application-test.yaml} binds
 * {@code vespera.corpus-root} empty, and the first test claims it. The class has its own working
 * directory and so its own context and its own in-memory database; each test scores a corpus and a seed
 * set of its own, which rewrites the profile and the label file the tests share; and the labeller's
 * record of what it was asked is this class's and is emptied before each test.
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
    private static final String ONE_DOCUMENT_IN_THE_SAMPLE = "the fixture corpus holds one document";

    /** The words every scripted conversion carries, so finding them means a document's opening was put. */
    private static final String WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH = "stubbed but real content";

    /** ADR-208 section 2's line, word for word. */
    private static final String THE_UNUSED_ROOT_LINE = "--root is not used: vespera label --auto finds each document"
            + " through the run the label file names and opens nothing under a corpus root. The option is"
            + " still accepted, so a command line that names one keeps working.";

    /** How that line opens, for the claims that it is absent. */
    private static final String HOW_THE_UNUSED_ROOT_LINE_OPENS = "--root is not used";

    /** How the refusal ADR-208 removes opened. */
    private static final String HOW_THE_REMOVED_REFUSAL_OPENS = "vespera label --auto named no root";

    /**
     * Text no file system holds a name for: the NUL character ends a name on every one of them, so
     * {@code Path.of} refuses it on Windows and on Linux alike. A root that cannot exist, not one that
     * merely does not.
     */
    private static final String TEXT_THAT_IS_NO_PATH = "no-such" + '\0' + "root";

    /** What the option's description said before ADR-208, false since ADR-206. */
    private static final String THE_STALE_DESCRIPTION = "the label file's documents are under";

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
        theOneDocumentWasPutWithItsOpening();
        claim(
                "nothing on standard error asks for a folder",
                () -> assertThat(output.getErr()).doesNotContain(HOW_THE_REMOVED_REFUSAL_OPENS));
        claim(
                "and nothing is said about a folder that was not used, because none was given",
                () -> assertThat(output.getAll()).doesNotContain(HOW_THE_UNUSED_ROOT_LINE_OPENS));
    }

    @Test
    @Story("Labelling opens nothing under the archive")
    @DisplayName("Given a folder as the archive's, the model labels as it does without one, and one line says the folder was not used")
    void saysOnceThatAGivenRootIsNotUsed(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto", "--root", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.OK));
        theOneDocumentWasPutWithItsOpening();
        claim(
                "one line, written once, says the folder was not used, what the command works from"
                        + " instead, and that naming one is still allowed",
                () -> assertThat(output.getAll()).containsOnlyOnce(THE_UNUSED_ROOT_LINE));
    }

    @Test
    @Story("Labelling opens nothing under the archive")
    @DisplayName("Given text that could not name a folder on any disk as the archive's, the model still labels")
    void acceptsARootThatIsNoPathAtAll(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        claim(
                "the text given could not name a folder on this machine or any other, so nothing can be"
                        + " opened under it: the precondition that makes this a folder that cannot exist",
                () -> assertThatThrownBy(() -> Path.of(TEXT_THAT_IS_NO_PATH))
                        .isInstanceOf(InvalidPathException.class));
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto", "--root", TEXT_THAT_IS_NO_PATH);

        claim(
                "the invocation reports success: what was given is not read at all, not even to see"
                        + " whether it could be a folder's name",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.OK));
        theOneDocumentWasPutWithItsOpening();
        claim(
                "and the same one line says it was not used",
                () -> assertThat(output.getAll()).containsOnlyOnce(THE_UNUSED_ROOT_LINE));
    }

    @Test
    @Story("Labelling opens nothing under the archive")
    @DisplayName("The help for the option says it is not used")
    void theOptionSaysItIsNotUsed() {
        String description = String.join(
                " ",
                command.commandLine()
                        .getSubcommands()
                        .get("label")
                        .getCommandSpec()
                        .findOption("--root")
                        .description());

        claim(
                "the description opens by saying the option is not used",
                () -> assertThat(description).startsWith("Not used."));
        claim(
                "and no longer says the label file's documents are under the folder it names, which"
                        + " stopped being true when labelling stopped opening them",
                () -> assertThat(description).doesNotContain(THE_STALE_DESCRIPTION));
    }

    private void theOneDocumentWasPutWithItsOpening() {
        claim(
                "the model was asked about the one document in the sample (" + ONE_DOCUMENT_IN_THE_SAMPLE
                        + "), and the question carried its opening",
                () -> assertThat(OPENINGS_PUT)
                        .singleElement()
                        .satisfies(opening -> assertThat(opening)
                                .hasValueSatisfying(text ->
                                        assertThat(text).contains(WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH))));
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
}
