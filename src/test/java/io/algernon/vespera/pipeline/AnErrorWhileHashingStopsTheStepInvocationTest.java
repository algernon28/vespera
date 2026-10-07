package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import io.algernon.vespera.extraction.SidecarVersionReport;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An {@code Error} thrown where a file is hashed is caught by nothing and stops the step it was thrown
 * in (ADR-207 section 3). Since the hash is read through a fixed buffer no file's size can raise
 * {@code OutOfMemoryError} there, so one that still arrives says the JVM is out of memory for a reason
 * that is not this file: marking the file failed and going on would blame it wrongly, and would do the
 * same to every file after it.
 *
 * <p>The error is raised by the extraction double just before it hashes one named file, which is the
 * seam ADR-155's tests already use to move a seed away. The double is this class's own, so no fixture
 * another test relies on changes. It is a real {@code OutOfMemoryError}, because that is the type the
 * decision is about; it is raised by hand and no memory is used up.
 *
 * <p>Stage 1 hashes through {@code corpus}, which has no such seam, so its case is not held here: it is
 * read from the code and from a measurement the record gives.
 */
@CascadeSliceTest
@Import(AnErrorWhileHashingStopsTheStepInvocationTest.ErrorRaisingExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Extraction")
@Feature("Hashing content that arrived unhashed")
@Issue("449")
@Link(name = "ADR-207", url = Adr.A_FILE_IS_HASHED_THROUGH_A_FIXED_BUFFER, type = "adr")
class AnErrorWhileHashingStopsTheStepInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The file, in the archive or among the seeds, whose hashing raises the error once a test arms it. */
    private static final String RAISES_AN_ERROR_WHEN_HASHED = "raises-an-error-when-hashed.txt";

    /** What the raised error says, so the operator's closing line can be recognised by it. */
    private static final String THE_ERRORS_MESSAGE = "raised by this test where the file is hashed";

    /** The two ordinary documents every archive here holds beside the one that raises the error. */
    private static final List<String> ORDINARY_DOCUMENTS = List.of("an-ordinary-document.txt", "another-ordinary-document.txt");

    private static final String ORDINARY_SEED = "an-ordinary-seed.txt";

    /**
     * Whether hashing {@link #RAISES_AN_ERROR_WHEN_HASHED} raises the error. Static because the double is
     * one bean for the whole class; reset before and after each test, since the order tests run in is not
     * fixed.
     */
    private static final AtomicBoolean ARMED = new AtomicBoolean();

    @TestConfiguration
    static class ErrorRaisingExtractionBeans {

        /** Real text and a title, so nothing else here is removed or left unnamed. */
        private static final String WITH_TEXT = "{\"document\":{\"json_content\":{\"texts\":["
                + "{\"text\":\"A Stubbed Document\",\"label\":\"title\"},"
                + "{\"text\":\"stubbed but real content\"}]}}}";

        @Bean
        DoclingExtractor doclingExtractor(JdbcTemplate jdbcTemplate) {
            return new PathScriptedExtractor()
                    .cachingInto(jdbcTemplate)
                    .beforeHashing(RAISES_AN_ERROR_WHEN_HASHED, file -> {
                        if (ARMED.get()) {
                            throw new OutOfMemoryError(THE_ERRORS_MESSAGE);
                        }
                    })
                    .otherwiseAnswering(new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, WITH_TEXT));
        }

        /** Never reached over HTTP; the version map is what the extractor identity is composed from. */
        @Bean
        DoclingClient doclingClient(@Value("${vespera.docling.image}") String configuredImage) {
            return new DoclingClient("unused") {
                @Override
                public void checkHealth() {}

                @Override
                public Map<String, String> version() {
                    return SidecarVersionReport.runningImage(configuredImage);
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
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void disarm() {
        ARMED.set(false);
    }

    @Test
    @Story("An error that is not about the file stops the step")
    @DisplayName("An out-of-memory error raised while a document is hashed stops conversion, and no document is removed for it")
    void anErrorWhileADocumentIsHashedStopsConversion(
            CapturedOutput output, @TempDir Path root, @TempDir Path seeds) throws IOException {
        anArchiveAndItsSeeds(root, seeds);
        Files.writeString(
                root.resolve(RAISES_AN_ERROR_WHEN_HASHED),
                "a document of a size no other file here has, so only the converting stage hashes it, in "
                        + root.getFileName());
        ARMED.set(true);

        Throwable escaped = catchThrowable(() -> cli.run("run", root.toString()));

        claim(
                "the error does not leave the command as an error: the step it was raised in is ended"
                        + " and reported, and the command returns",
                () -> assertThat(escaped).as("what the command threw").isNull());
        claim("the invocation reports failure", () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the stage before conversion is recorded as finished and conversion is not: the error"
                        + " stopped the step it was raised in and nothing before it",
                () -> assertThat(finishedSteps(root))
                        .contains("byte-level-reduction")
                        .doesNotContain("extraction"));
        claim(
                "no document is removed: nothing is recorded against the document that was being hashed"
                        + " or against any other, because an error of this kind says nothing about a file",
                () -> assertThat(verdictsAgainstFilesUnder(root)).isEmpty());
        claim(
                "the line the operator reads says the stage failed and is not recorded as finished, and"
                        + " carries what the error said",
                () -> assertThat(output.getAll())
                        .contains("Stage 2 (extraction) failed and is not recorded as finished")
                        .contains(THE_ERRORS_MESSAGE));
    }

    @Test
    @Story("An error that is not about the file stops the step")
    @DisplayName("An out-of-memory error raised while a seed is hashed stops seed extraction, and the seed is not recorded as one that would not open")
    @Link(name = "ADR-155", url = Adr.A_SEED_FILE_THAT_WILL_NOT_OPEN_IS_RECORDED_UNDER_A_REASON_OF_ITS_OWN, type = "adr")
    void anErrorWhileASeedIsHashedStopsSeedExtraction(
            CapturedOutput output, @TempDir Path root, @TempDir Path seeds) throws IOException {
        anArchiveAndItsSeeds(root, seeds);
        Files.writeString(
                seeds.resolve(RAISES_AN_ERROR_WHEN_HASHED),
                "a seed with text of its own, whose hashing raises the error, in " + seeds.getFileName());
        ARMED.set(true);

        Throwable escaped = catchThrowable(() -> cli.run("run", root.toString()));

        claim(
                "the error does not leave the command as an error",
                () -> assertThat(escaped).as("what the command threw").isNull());
        claim("the invocation reports failure", () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "conversion of the archive, which came first, is recorded as finished: the error stopped"
                        + " seed extraction and nothing before it",
                () -> assertThat(finishedSteps(root)).contains("extraction"));
        claim(
                "nothing is recorded about the seeds: no run over them and so no seed recorded as one"
                        + " whose file would not open. Only a file the file system would not hand over is"
                        + " recorded that way; an error that is not about the file is not",
                () -> assertThat(runsOver(seeds)).isEmpty());
        claim(
                "the line the operator reads says seed extraction failed before it had read the whole"
                        + " seed folder, and carries what the error said",
                () -> assertThat(output.getAll())
                        .contains("Stage 5a (seed extraction) failed before it had read the whole seed folder")
                        .contains(THE_ERRORS_MESSAGE));
    }

    /** The steps recorded as finished under any run over the walk of {@code folder}. */
    private List<String> finishedSteps(Path folder) {
        return jdbcTemplate.queryForList(
                "SELECT f.step FROM finished_step f JOIN run r ON r.id = f.run_id JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(folder).toString());
    }

    /** Every verdict against a file under {@code folder}, as path and kind. */
    private List<String> verdictsAgainstFilesUnder(Path folder) {
        return jdbcTemplate.queryForList(
                "SELECT fo.path || ' -> ' || v.kind FROM verdict v JOIN file_occurrence fo ON fo.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(folder).toString());
    }

    /** The stage of every run over the walk of {@code folder}. */
    private List<String> runsOver(Path folder) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(folder).toString());
    }

    /** Two ordinary documents and one ordinary seed, each with bytes of its own, and stage 4's gate open. */
    private void anArchiveAndItsSeeds(Path root, Path seeds) throws IOException {
        String stamp = root.getFileName().toString();
        Files.writeString(root.resolve(ORDINARY_DOCUMENTS.get(0)), "one ordinary document in " + stamp);
        Files.writeString(
                root.resolve(ORDINARY_DOCUMENTS.get(1)), "another ordinary document, longer than the first, in " + stamp);
        Files.writeString(seeds.resolve(ORDINARY_SEED), "an ordinary seed with text of its own, for " + stamp);
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }
}
