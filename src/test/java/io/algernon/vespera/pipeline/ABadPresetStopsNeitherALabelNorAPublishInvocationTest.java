package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A preset no run can be made under stops {@code vespera run} alone (ADR-330 §9, ADR-332 §1): plain {@code vespera label}
 * and {@code vespera publish} mint no run and read no preset.
 *
 * <p>The value is what configuration names as the context is built, so a check made as the application starts stops
 * every test here. It is put back for the one run that writes the label file, since no run can score under it, and named
 * again before the invocation the claim is about. Each value is read as it is asked for, so this needs one context.
 *
 * <p><b>A label is shown going on</b>: with answers typed in the file a run wrote, under the value a run is refused for,
 * it records them.
 *
 * <p><b>A publish is shown as far as its first refusal of its own</b>, nothing being drawn here: it says that and no line
 * of the preset, which a check ahead of its reading the ledger would turn into another line. A check of the preset past
 * that refusal is not shown. No test here can draw a rendering and go on, because what follows it reads the Confluence
 * variables of the machine running the test, to send to the site they name.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-332", url = Adr.THE_LOCAL_MODEL_LABELS_ONLY_UNDER_THE_EXTRACTOR_ITS_RUN_WAS_CONVERTED_UNDER, type = "adr")
class ABadPresetStopsNeitherALabelNorAPublishInvocationTest {

    /** The preset named here: no stage before scoring reads it, and a score of no similarities is no score. */
    private static final String THE_KEY = "vespera.relevance.top-similarities";

    /** The value that ships, under which the run here writes the label file. */
    private static final String THE_SHIPPED_VALUE = "3";

    /** A value no run can be made under. */
    private static final String THE_BAD_VALUE = "0";

    /** The whole of the line that value gives. */
    private static final String THE_REFUSAL = THE_KEY + " is " + THE_BAD_VALUE + ": a whole number from 1 to 2147483647";

    /** The refusal of a publish with nothing drawn, its own and no preset's. */
    private static final String NOTHING_DRAWN = "vespera publish sent nothing: no Confluence rendering is drawn in this"
            + " working directory. Run vespera run to the deliverable, and publish again.";

    /** What the label file says of a document nobody has answered for. */
    private static final String UNANSWERED = "relevant: null";

    /** What a person typing an answer writes in its place. */
    private static final String ANSWERED = "relevant: true";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    private static volatile String topSimilarities = THE_BAD_VALUE;

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(THE_KEY, () -> topSimilarities);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private RelevanceLabels relevanceLabels;

    @BeforeEach
    void theBadValueIsNamed() {
        topSimilarities = THE_BAD_VALUE;
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Recording the answers typed in a label file goes on under a value no run can be made under")
    void aLabelIsNotStoppedByIt(CapturedOutput output) throws IOException {
        int typed = aLabelFileWithItsAnswersTyped();
        int recordedBefore = recorded();
        int beforeTheRun = output.getAll().length();

        cli.run("run", root.toString());

        claim("a run is refused under the value, so that what follows is about a value that stops one",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(output.getAll().substring(beforeTheRun).lines().filter(THE_REFUSAL::equals).toList())
                            .hasSize(1);
                });
        int beforeTheLabel = output.getAll().length();

        cli.run("label");

        claim("the label ends well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + typed + " answers typed was recorded",
                () -> {
                    assertThat(typed).isPositive();
                    assertThat(recorded()).isEqualTo(recordedBefore + typed);
                });
        claim("and no line of the value is said",
                () -> assertThat(output.getAll().substring(beforeTheLabel)).doesNotContain(THE_KEY));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Publishing goes on under a value no run can be made under, as far as finding nothing drawn to send")
    void aPublishIsNotStoppedByIt(CapturedOutput output) {
        int before = output.getAll().length();

        cli.run("publish");

        String said = output.getAll().substring(before);
        claim("the publish ends non-zero, and for its own reason: its one line says nothing is drawn",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(said.lines().filter(NOTHING_DRAWN::equals).toList()).hasSize(1);
                });
        claim("and no line of the value is said", () -> assertThat(said).doesNotContain(THE_KEY));
    }

    /** A run at the value that ships writes the file, its answers are typed, and the bad value is named again. */
    private int aLabelFileWithItsAnswersTyped() throws IOException {
        topSimilarities = THE_SHIPPED_VALUE;
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", root.toString());
        Path labelFile = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        String asked = Files.readString(labelFile, StandardCharsets.UTF_8);
        Files.writeString(labelFile, asked.replace(UNANSWERED, ANSWERED), StandardCharsets.UTF_8);
        topSimilarities = THE_BAD_VALUE;
        return (int) asked.lines().filter(line -> line.contains(UNANSWERED)).count();
    }

    private int recorded() {
        return relevanceLabels.forSeedSet(Walk.canonicalRoot(seeds).toString()).size();
    }
}
