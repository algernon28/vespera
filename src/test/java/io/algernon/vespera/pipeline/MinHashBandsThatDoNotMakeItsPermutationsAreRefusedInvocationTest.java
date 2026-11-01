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
 * A signature whose bands times their rows are not its permutations is refused (ADR-330 §9, ADR-081): 16 bands of 7
 * rows cover 112 of the shipped 128 permutations, and banding over part of a signature is no banding the record
 * measured.
 *
 * <p>A class of its own, because the configuration is a property of the context. The refusal is the record's line
 * word for word, and it comes where the run the value would enter is minted: the stage before it has its run, and the
 * stage itself has none, so nothing is recorded under a value no run can be made under.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Redundancy")
@Feature("Resolution")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
@Disabled("ADR-330: awaits the refusal of a preset no run can be made under")
class MinHashBandsThatDoNotMakeItsPermutationsAreRefusedInvocationTest {

    /** The key named by configuration. */
    private static final String KEY = "vespera.redundancy.minhash.rows-per-band";

    /** Seven rows in each of the shipped 16 bands, which make 112 and not the shipped 128 permutations. */
    private static final String REFUSED_VALUE = "7";

    /** The line the record gives for it, word for word. */
    private static final String THE_LINE = "vespera.redundancy.minhash is 128 permutations in 16 bands of 7 rows: the bands times their rows must"
            + " be the permutations";

    /** The stage whose run the value would enter, which is not minted. */
    private static final String REFUSING_STAGE = "content-redundancy";

    /** The stage before it, whose run is minted, so the invocation reached the refusing stage. */
    private static final String STAGE_BEFORE = "content-census";

    /** What a stack frame looks like in the output, which a refusal in one line does not print. */
    private static final String A_STACK_FRAME = "\tat ";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

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
    @Story("How redundancy is measured is a preset")
    @DisplayName("Bands and rows that do not make the permutations stop the invocation in one line, before any redundancy is judged")
    void bandsTimesRowsOtherThanThePermutationsAreRefusedBeforeTheContentRedundancyRun(CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
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
