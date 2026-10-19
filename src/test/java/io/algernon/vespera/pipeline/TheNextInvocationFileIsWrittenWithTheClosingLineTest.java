package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The file the control panel reads, reached through real invocations (ADR-294 §1): {@code vespera run} and {@code
 * vespera label} ending with exit code 0 write it with what their closing line asks for, and an invocation refused
 * writes none.
 *
 * <p>{@link NextActionNamesTheNextInvocationTest} holds what each branch gives; this holds that the commands write
 * it where they print the line. The deletion as an invocation starts is the working-directory lock's, which a test
 * context does not take, so each test here deletes the file first itself.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class TheNextInvocationFileIsWrittenWithTheClosingLineTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "next-invocation.properties";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

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

    @BeforeEach
    void startFromNothingAnsweredAndNoFile() throws IOException {
        profileStore.save(ProfileFixture.profile().build());
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A run with nothing set ends with exit code 0 and writes a run wanting the two run values")
    void aRunWritesTheFileWithItsClosingLine(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);

        cli.run("run", root.toString());

        claim("the run ended with exit code 0",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("and wrote the file: a run wanting seedFolder and boilerplateDocumentFrequencyFloor, in that order,"
                        + " with no answers wanted",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "run",
                        "wants", "seedFolder boilerplateDocumentFrequencyFloor",
                        "answer", "false")));
    }

    @Test
    @Story("A refused or failed invocation leaves no file")
    @DisplayName("A run refused for want of a root writes no file")
    void aRefusedRunWritesNoFile() {
        cli.run("run");

        claim("the run was refused, having no root to walk",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and wrote no file",
                () -> assertThat(workingDirectory.resolve(FILE_NAME)).doesNotExist());
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("vespera label recording answers ends with exit code 0 and writes a run wanting the relevance threshold")
    void labelWritesTheFileWithItsClosingLine(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        theRunValuesSet(seeds);
        cli.run("run", root.toString());
        answerEveryQuestion();
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));

        cli.run("label");

        claim("the answers were recorded, with exit code 0",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("and the file is a run wanting relevanceScoreFloor alone, with no answers wanted",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "run", "wants", "relevanceScoreFloor", "answer", "false")));
    }

    @Test
    @Story("A refused or failed invocation leaves no file")
    @DisplayName("vespera label refused for want of a label file writes no file")
    void aRefusedLabelWritesNoFile() throws IOException {
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));

        cli.run("label");

        claim("vespera label was refused, there being no label file to read",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and wrote no file",
                () -> assertThat(workingDirectory.resolve(FILE_NAME)).doesNotExist());
    }

    private static void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The three run values set, so the run writes the questions. */
    private void theRunValuesSet(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the scoring run is minted")
                .build());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private static void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }

    /** The file's properties as {@code java.util.Properties} reads them in UTF-8, comments left out. */
    private static Map<String, String> propertiesIn(Path workingDirectory) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> read = new TreeMap<>();
        properties.stringPropertyNames().forEach(name -> read.put(name, properties.getProperty(name)));
        return read;
    }
}
