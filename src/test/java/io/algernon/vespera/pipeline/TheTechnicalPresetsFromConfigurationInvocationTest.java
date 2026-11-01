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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * The presets of the converter, of redundancy and of relevance, named by application configuration in place of the
 * shipped file (ADR-330 §8, §9): each enters the identity of the run whose output it shapes, in the place the record
 * gives it, and the OCR preset enters the extractor identity, where it stood already as a constant. One invocation, made
 * once for the class, and each test reads one stage's run of it.
 *
 * <p>Every value here differs from the shipped one and is one the record lets a run be made under. The corpus converts
 * to the one scripted text, whose shingles are boilerplate at every window, so the redundancy values decide nothing
 * here and the three documents reach scoring; their vectors are alike, so their scores are, and they fall in one band.
 *
 * <p>{@code RunIdentityGoldenTest} holds the text of every run's identity with the presets at their shipped values, and
 * passes unchanged once they are presets: that is the claim that no recorded configuration moves.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the converter's, redundancy's and relevance's presets in their runs' identities")
class TheTechnicalPresetsFromConfigurationInvocationTest {

    private static final int OCCURRENCES = 3;

    /** An OCR preset other than the shipped rapidocr. */
    private static final String CONFIGURED_OCR_PRESET = "easyocr";

    /** Timeouts in a row other than the shipped 3, within the 2 to 4 the record allows. */
    private static final int CONFIGURED_TIMEOUTS_IN_A_ROW = 2;

    /** A shingle window other than the shipped 5. */
    private static final int CONFIGURED_SHINGLE_WORDS = 6;

    /** Permutations, bands, rows and seed other than the shipped 128, 16, 8 and seed; 8 bands of 8 rows make 64. */
    private static final int CONFIGURED_PERMUTATIONS = 64;

    private static final int CONFIGURED_BANDS = 8;

    private static final int CONFIGURED_ROWS = 8;

    private static final long CONFIGURED_SEED = 1L;

    /** Cuts and counts other than the shipped 0.80, 0.95, 32 and 24. */
    private static final String CONFIGURED_JACCARD = "0.9";

    private static final String CONFIGURED_CONTAINMENT = "0.99";

    private static final int CONFIGURED_RARE_SHINGLE_SAMPLE = 16;

    private static final int CONFIGURED_RARE_SHINGLE_HITS = 12;

    /** A score that is the highest similarity alone, where the shipped value takes the mean of three. */
    private static final int CONFIGURED_TOP_SIMILARITIES = 1;

    /** Two bands, one document asked about from each, where the shipped sample is five of twelve. */
    private static final int CONFIGURED_BANDS_OF_THE_SAMPLE = 2;

    private static final int CONFIGURED_PER_BAND = 1;

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
        registry.add("vespera.docling.ocr-preset", () -> CONFIGURED_OCR_PRESET);
        registry.add("vespera.docling.timeouts-in-a-row", () -> CONFIGURED_TIMEOUTS_IN_A_ROW);
        registry.add("vespera.redundancy.shingle-words", () -> CONFIGURED_SHINGLE_WORDS);
        registry.add("vespera.redundancy.minhash.permutations", () -> CONFIGURED_PERMUTATIONS);
        registry.add("vespera.redundancy.minhash.bands", () -> CONFIGURED_BANDS);
        registry.add("vespera.redundancy.minhash.rows-per-band", () -> CONFIGURED_ROWS);
        registry.add("vespera.redundancy.minhash.seed", () -> CONFIGURED_SEED);
        registry.add("vespera.redundancy.near-duplicate-jaccard", () -> CONFIGURED_JACCARD);
        registry.add("vespera.redundancy.containment-index", () -> CONFIGURED_CONTAINMENT);
        registry.add("vespera.redundancy.rare-shingle-sample", () -> CONFIGURED_RARE_SHINGLE_SAMPLE);
        registry.add("vespera.redundancy.rare-shingle-hits", () -> CONFIGURED_RARE_SHINGLE_HITS);
        registry.add("vespera.relevance.top-similarities", () -> CONFIGURED_TOP_SIMILARITIES);
        registry.add("vespera.relevance.sample.bands", () -> CONFIGURED_BANDS_OF_THE_SAMPLE);
        registry.add("vespera.relevance.sample.per-band", () -> CONFIGURED_PER_BAND);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Made once for the class. Not in a {@code @BeforeAll} of a {@code PER_CLASS} instance: that instance, and so the
     * context, is built before the static {@code @TempDir}s are set, so {@link #properties} would find them null.
     */
    private static boolean invoked;

    @BeforeEach
    void oneInvocationOverACorpusOfThreeAndASeed() throws IOException {
        if (invoked) {
            return;
        }
        invoked = true;
        for (int document = 0; document < OCCURRENCES; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "a corpus document, the " + document + "th");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", root.toString());
    }

    @Test
    @Story("How documents are converted is a preset")
    @DisplayName("The OCR preset named by configuration is in the extractor identity, and the timeouts in a row and the shingle window end the extraction run's identity, in that order")
    void theConvertersPresetsNameTheExtractionRun() {
        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        List<String> extraction = theConfigurationsOf("extraction");
        claim("the one extraction run's extractor identity names the OCR preset configured, " + CONFIGURED_OCR_PRESET
                        + ", where the shipped one stood",
                () -> assertThat(extraction).singleElement().asString()
                        .contains(";ocr_preset=" + CONFIGURED_OCR_PRESET + ";")
                        .doesNotContain("ocr_preset=rapidocr"));
        claim("and its recorded configuration ends with " + CONFIGURED_TIMEOUTS_IN_A_ROW + " timeouts in a row"
                        + " and shingles of " + CONFIGURED_SHINGLE_WORDS + " words",
                () -> assertThat(extraction).singleElement().asString().endsWith(",\"timeoutsInARow\":" + CONFIGURED_TIMEOUTS_IN_A_ROW
                        + ",\"shingleParameters\":\"word:" + CONFIGURED_SHINGLE_WORDS + "\"}"));
    }

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("The signature's numbers and the similarity cuts named by configuration end the content-redundancy run's identity, in that order")
    void theRedundancyPresetsNameTheContentRedundancyRun() {
        claim("the one content-redundancy run's recorded configuration ends with " + CONFIGURED_PERMUTATIONS + " permutations in "
                        + CONFIGURED_BANDS + " bands of " + CONFIGURED_ROWS + " rows from seed " + CONFIGURED_SEED
                        + ", then cuts of " + CONFIGURED_JACCARD + " and " + CONFIGURED_CONTAINMENT + " over "
                        + CONFIGURED_RARE_SHINGLE_HITS + " hits in " + CONFIGURED_RARE_SHINGLE_SAMPLE + " rare shingles",
                () -> assertThat(theConfigurationsOf("content-redundancy")).singleElement().asString().endsWith(
                        ",\"minHash\":\"perms:" + CONFIGURED_PERMUTATIONS + ";bands:" + CONFIGURED_BANDS + ";rows:"
                                + CONFIGURED_ROWS + ";seed:" + CONFIGURED_SEED + "\""
                                + ",\"redundancyThresholds\":\"near-duplicate:" + CONFIGURED_JACCARD + ";containment:"
                                + CONFIGURED_CONTAINMENT + ";sample:" + CONFIGURED_RARE_SHINGLE_SAMPLE + ";hits:"
                                + CONFIGURED_RARE_SHINGLE_HITS + "\"}"));
    }

    @Test
    @Story("How relevance is scored and sampled is a preset")
    @DisplayName("The number of similarities and the sample named by configuration end the scoring run's identity, name the scale of the questions, and reach the draw")
    void theRelevancePresetsNameTheScoringRunAndReachItsSample() throws IOException {
        claim("the one scoring run's recorded configuration ends with a score of the top " + CONFIGURED_TOP_SIMILARITIES
                        + " similarity, " + CONFIGURED_BANDS_OF_THE_SAMPLE + " bands and " + CONFIGURED_PER_BAND
                        + " document from each, in that order",
                () -> assertThat(theConfigurationsOf("embedding-scoring")).singleElement().asString().endsWith(
                        ",\"topSimilarities\":" + CONFIGURED_TOP_SIMILARITIES + ",\"sampleBands\":"
                                + CONFIGURED_BANDS_OF_THE_SAMPLE + ",\"sampledPerBand\":" + CONFIGURED_PER_BAND + "}"));
        Map<String, Object> questions = theLabelFile();
        claim("the questions name a scale that ends with the number of similarities, so an answer given at the shipped"
                        + " three is not read as one given at this",
                () -> assertThat(String.valueOf(questions.get("generatedUnderEmbedder")))
                        .endsWith(";top:" + CONFIGURED_TOP_SIMILARITIES));
        claim("and the draw asks about " + CONFIGURED_PER_BAND + " document, the one band the alike scores fall in"
                        + " giving one, where the shipped twelve from each would ask about all " + OCCURRENCES,
                () -> assertThat((List<?>) questions.get("documents")).hasSize(CONFIGURED_PER_BAND));
    }

    private List<String> theConfigurationsOf(String stage) {
        return jdbcTemplate.queryForList(
                "SELECT config_consumed FROM run WHERE stage = ? ORDER BY rowid", String.class, stage);
    }

    private static Map<String, Object> theLabelFile() throws IOException {
        return new Yaml().load(Files.readString(workingDirectory.resolve("relevance-labels.yaml"), StandardCharsets.UTF_8));
    }
}
