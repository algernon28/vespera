package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * An OCR preset named {@code auto} by configuration is refused (ADR-330 §9, ADR-090): it is the engine the converter
 * picks where nothing records which, the drift ADR-090 pinned the client's options to end.
 *
 * <p>A class of its own, because the configuration is a property of the context. The refusal is the record's line
 * word for word, and it comes where the run the value would enter is minted: the stage before it has its run, and the
 * stage itself has none, so nothing is recorded under a value no run can be made under.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Extraction")
@Feature("Which engine produced a conversion")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
@Disabled("ADR-330: awaits the refusal of a preset no run can be made under")
class AnOcrPresetTheConverterWouldResolveIsRefusedInvocationTest {

    /** The key named by configuration. */
    private static final String KEY = "vespera.docling.ocr-preset";

    /** The engine the converter would pick, which ADR-090 refuses to leave it. */
    private static final String REFUSED_VALUE = "auto";

    /** The line the record gives for it, word for word. */
    private static final String THE_LINE = "vespera.docling.ocr-preset is \"auto\": one preset's name, of letters, digits, '-', '_' and '.', and"
            + " never auto, which the converter resolves where nothing records it";

    /** The stage whose run the value would enter, which is not minted. */
    private static final String REFUSING_STAGE = "extraction";

    /** The stage before it, whose run is minted, so the invocation reached the refusing stage. */
    private static final String STAGE_BEFORE = "byte-level-reduction";

    /** What a stack frame looks like in the output, which a refusal in one line does not print. */
    private static final String A_STACK_FRAME = "\tat ";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(KEY, () -> REFUSED_VALUE);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("How documents are converted is a preset")
    @DisplayName("An OCR preset the converter would resolve for itself stops the invocation in one line, before any conversion is recorded")
    void anOcrPresetOfAutoIsRefusedBeforeTheExtractionRun(CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the embedding-model gate is open")
                .build());
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("exactly one line says the record's words for " + KEY + " at " + REFUSED_VALUE
                        + ", and no stack trace stands in its place",
                () -> {
                    assertThat(said.lines().filter(line -> line.contains(THE_LINE)).toList()).hasSize(1);
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("the stage before has its run and the refusing stage has none, so the value stopped the invocation where"
                        + " its run would have been made, and nothing was recorded under it",
                () -> assertThat(List.of(runsOf(STAGE_BEFORE), runsOf(REFUSING_STAGE))).containsExactly(1, 0));
    }

    private int runsOf(String stage) {
        Integer runs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE stage = ?", Integer.class, stage);
        return runs == null ? 0 : runs;
    }
}
