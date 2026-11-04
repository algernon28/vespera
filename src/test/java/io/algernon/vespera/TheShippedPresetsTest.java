package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * The presets the application ships, read as Spring reads the file (ADR-330 §8, §9): each at the value every run was
 * minted under while it was a constant of the code, held exactly, because another value here re-clusters, re-judges or
 * asks about other documents in every corpus whose machine names none.
 *
 * <p>Each test holds the presets of one choice, those of redundancy one case each. The numbers are compared as numbers, so that {@code 0.80} written in
 * the file and the {@code 0.8} Java reads it as are one value, as they are one value in a run's identity.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheShippedPresetsTest {

    /** The file the application ships with. */
    private static final String SHIPPED = "application.yaml";

    /** How many neighbours clustering keeps of each document, the value ADR-087 chose. */
    private static final String SHIPPED_NEIGHBOURS = "15";

    /** How finely clustering divides a partition, the value ADR-087 chose. */
    private static final String SHIPPED_RESOLUTION = "1.0";

    /** The OCR preset every conversion names, the one ADR-090 pinned in the client. */
    private static final String SHIPPED_OCR_PRESET = "rapidocr";

    /** Timeouts in a row before a timeout is read as the converter's, ADR-071's count. */
    private static final int SHIPPED_TIMEOUTS_IN_A_ROW = 3;

    /** Words a shingle spans, the window ADR-073 provisionally chose. */
    private static final int SHIPPED_SHINGLE_WORDS = 5;

    /** ADR-081's permutations, bands and rows in each band. */
    private static final List<Integer> SHIPPED_MINHASH_SHAPE = List.of(128, 16, 8);

    /** The seed every permutation is derived from, as the code wrote it, which the file holds in decimal. */
    private static final long SHIPPED_MINHASH_SEED = 0x5EED_C0FF_EE12_3456L;

    /** ADR-081's near-duplicate cut and containment cut. */
    private static final List<Double> SHIPPED_CUTS = List.of(0.80, 0.95);

    /** ADR-081's rarest-shingle sample and the hits a container needs among it. */
    private static final List<Integer> SHIPPED_RARE_SHINGLES = List.of(32, 24);

    /** How many of the highest similarities a relevance score is the mean of, ADR-020's three. */
    private static final int SHIPPED_TOP_SIMILARITIES = 3;

    /** ADR-088's five bands and twelve documents asked about from each, sixty in all. */
    private static final List<Integer> SHIPPED_SAMPLE = List.of(5, 12);

    private static Map<Object, Object> shipped;

    @BeforeAll
    static void theShippedFile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(SHIPPED));
        yaml.afterPropertiesSet();
        shipped = yaml.getObject();
    }

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("The shipped file names the neighbours and the resolution at the values every earlier run was made under")
    void theShippedFileNamesBothAtTheirEarlierValues() {
        claim("the neighbours are " + SHIPPED_NEIGHBOURS + " and the resolution " + SHIPPED_RESOLUTION
                        + ", so a corpus naming neither is clustered as it was",
                () -> assertThat(List.of(text("vespera.clustering.neighbours"), text("vespera.clustering.resolution")))
                        .containsExactly(SHIPPED_NEIGHBOURS, SHIPPED_RESOLUTION));
    }

    @Test
    @Story("How documents are converted is a preset")
    @DisplayName("The shipped file names the OCR preset and the timeouts in a row at the values every earlier run was made under")
    void theConvertersTwoPresetsShipAtTheirEarlierValues() {
        claim("the OCR preset is " + SHIPPED_OCR_PRESET + " and a timeout is the converter's after "
                        + SHIPPED_TIMEOUTS_IN_A_ROW + " in a row, so a machine naming neither converts and judges as it did",
                () -> assertThat(List.<Object>of(text("vespera.docling.ocr-preset"),
                                whole("vespera.docling.timeouts-in-a-row")))
                        .containsExactly(SHIPPED_OCR_PRESET, (long) SHIPPED_TIMEOUTS_IN_A_ROW));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("theRedundancyPresets")
    @Story("How redundancy is measured is a preset")
    @DisplayName("The shipped file names each value redundancy is measured and judged by at the value every earlier run was made under")
    void theRedundancyPresetsShipAtTheirEarlierValues(String key, Number earlier) {
        claim("the file names " + key + " at " + earlier + ", so every shingle and signature is filed under the identity"
                        + " it was and a machine naming none judges redundancy as it did",
                () -> assertThat(earlier instanceof Double ? (Object) number(key) : (Object) whole(key))
                        .isEqualTo(earlier));
    }

    /** Each value redundancy is measured and judged by, as its key and the value every earlier run was made under. */
    static Stream<Arguments> theRedundancyPresets() {
        return Stream.of(
                Arguments.of(Named.of("the words a shingle spans", "vespera.redundancy.shingle-words"),
                        (long) SHIPPED_SHINGLE_WORDS),
                Arguments.of(Named.of("the permutations of a signature", "vespera.redundancy.minhash.permutations"),
                        (long) SHIPPED_MINHASH_SHAPE.get(0)),
                Arguments.of(Named.of("the bands of a signature", "vespera.redundancy.minhash.bands"),
                        (long) SHIPPED_MINHASH_SHAPE.get(1)),
                Arguments.of(Named.of("the rows in each band", "vespera.redundancy.minhash.rows-per-band"),
                        (long) SHIPPED_MINHASH_SHAPE.get(2)),
                Arguments.of(Named.of("the seed the permutations are derived from, as the code held it",
                        "vespera.redundancy.minhash.seed"), SHIPPED_MINHASH_SEED),
                Arguments.of(Named.of("the cut at which two documents are near-duplicates",
                        "vespera.redundancy.near-duplicate-jaccard"), SHIPPED_CUTS.get(0)),
                Arguments.of(Named.of("the cut at which one document is contained in another",
                        "vespera.redundancy.containment-index"), SHIPPED_CUTS.get(1)),
                Arguments.of(Named.of("the rarest shingles a container is sought from",
                        "vespera.redundancy.rare-shingle-sample"), (long) SHIPPED_RARE_SHINGLES.get(0)),
                Arguments.of(Named.of("the hits among them a container needs", "vespera.redundancy.rare-shingle-hits"),
                        (long) SHIPPED_RARE_SHINGLES.get(1)));
    }

    @Test
    @Story("How relevance is scored and sampled is a preset")
    @DisplayName("The shipped file names how many similarities a score is the mean of, and the sample's bands and documents from each, at the values every earlier run was made under")
    void theRelevanceScoreAndItsSampleShipAtTheirEarlierValues() {
        claim("a score is the mean of the " + SHIPPED_TOP_SIMILARITIES + " highest similarities, so every floor stays"
                        + " on the scale it was read on",
                () -> assertThat(whole("vespera.relevance.top-similarities")).isEqualTo(SHIPPED_TOP_SIMILARITIES));
        claim("the scores are divided into " + SHIPPED_SAMPLE.get(0) + " bands and " + SHIPPED_SAMPLE.get(1)
                        + " documents are asked about from each, so a run asks about the documents it did",
                () -> assertThat(List.of(whole("vespera.relevance.sample.bands"),
                                whole("vespera.relevance.sample.per-band")))
                        .containsExactly((long) SHIPPED_SAMPLE.get(0), (long) SHIPPED_SAMPLE.get(1)));
    }

    /** The key's value as the file holds it; a key the file does not hold fails here, naming it. */
    private static String text(String key) {
        assertThat(shipped).as("the shipped file names " + key).containsKey(key);
        return String.valueOf(shipped.get(key));
    }

    /** The key's value as a whole number; one the file does not hold, or holds as no whole number, fails here. */
    private static long whole(String key) {
        return Long.parseLong(text(key));
    }

    /** The key's value as a number; one the file does not hold, or holds as no number, fails here. */
    private static double number(String key) {
        return Double.parseDouble(text(key));
    }
}
