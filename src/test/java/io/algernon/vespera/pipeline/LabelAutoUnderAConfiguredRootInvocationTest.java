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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera label --auto} on a machine whose configuration names a corpus root, as an operator's
 * local profile does for {@code vespera run} (ADR-066). The command line names no root, and since
 * ADR-208 {@code label} has no option to name one with (ADR-208 section 5).
 *
 * <p>The configured root is a directory nobody created. The labelling succeeds all the same, because the
 * command finds each document through the run the label file names (ADR-206 section 4), and it asks
 * for no root on standard error. Both held before ADR-208's change, when the property satisfied a
 * requirement the command then had and was never read after that, and both hold since it, now that the
 * command does not read the property at all.
 *
 * <p>A class of its own because the property is bound when the context is built: the classes that
 * label with no root configured cannot also be the one that configures one. It has its own working
 * directory and so its own context and its own in-memory database; its labeller's record of what it was
 * asked is this class's and is emptied before each test.
 */
@CascadeSliceTest
@Import({
    SeedScriptedExtractionBeans.class,
    AutoLabelling.class,
    LabelAutoUnderAConfiguredRootInvocationTest.Beans.class
})
@Epic("Relevance")
@Feature("Local labelling")
@Issue("451")
@Link(name = "ADR-208", url = Adr.LABEL_AUTO_NEEDS_NO_CORPUS_ROOT, type = "adr")
@ExtendWith(OutputCaptureExtension.class)
class LabelAutoUnderAConfiguredRootInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String ONE_DOCUMENT_IN_THE_SAMPLE = "the fixture corpus holds one document";

    /** The words every scripted conversion carries, so finding them means a document's opening was put. */
    private static final String WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH = "stubbed but real content";

    /** A name resolved under an empty temporary directory and never created, so nothing can be read under it. */
    private static final String A_FOLDER_NOBODY_CREATED = "a-configured-root-nobody-created";

    /** How the refusal ADR-208 removed opened; with a root configured it was never reached. */
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

    /** Empty, and the parent of the configured root that is never created. */
    @TempDir
    static Path elsewhere;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.ROOT_PROPERTY, () -> configuredRoot().toString());
    }

    private static Path configuredRoot() {
        return elsewhere.resolve(A_FOLDER_NOBODY_CREATED);
    }

    @Autowired
    private VesperaCli cli;

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
    @DisplayName("With the archive's folder set in configuration and missing from disk, the model still labels, and nothing asks for a folder")
    void labelsUnderAConfiguredRootThatDoesNotExistAndSaysNothingAboutIt(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        claim(
                "configuration names a corpus root, and that folder does not exist: the precondition"
                        + " every claim below rests on",
                () -> {
                    assertThat(environment.getProperty(VesperaCommand.Run.ROOT_PROPERTY))
                            .isEqualTo(configuredRoot().toString());
                    assertThat(configuredRoot()).doesNotExist();
                });
        aScoredCorpus(root, seeds);

        cli.run("label", "--auto");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the model was asked about the one document in the sample (" + ONE_DOCUMENT_IN_THE_SAMPLE
                        + "), and the question carried its opening. Nothing exists in the configured"
                        + " folder, so the opening cannot have been read from there",
                () -> assertThat(OPENINGS_PUT)
                        .singleElement()
                        .satisfies(opening -> assertThat(opening)
                                .hasValueSatisfying(text ->
                                        assertThat(text).contains(WHAT_A_CONVERTED_DOCUMENT_OPENS_WITH))));
        claim(
                "nothing on standard error asks for a folder: the command needs none, and the"
                        + " configured one is there for the command that reads the archive",
                () -> assertThat(output.getErr()).doesNotContain(HOW_THE_REMOVED_REFUSAL_OPENS));
        claim(
                "and the configured folder still does not exist afterwards",
                () -> assertThat(configuredRoot()).doesNotExist());
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
