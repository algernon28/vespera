package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.similarity.MinHashParameters;
import io.algernon.vespera.similarity.RedundancyThresholds;
import io.algernon.vespera.similarity.ShingleParameters;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The class that resolves the presets (ADR-330 §9): every value of the table of refusals gives its line, word for word,
 * as {@code APresetNoRunCanBeMadeUnderIsRefusedInvocationTest} has it of the whole job for one or two of each, and each
 * bound's nearest value is allowed, which those cannot show since a run must then be made under it. What it resolves is
 * what configuration names, and the profile's {@code clusterResolution} over it where that can be read.
 *
 * <p>Each row names one value over the shipped sixteen, so the line it gives is that value's alone. The order among
 * several refused is that of the table, as {@code a sample of 0} over the shipped hits shows.
 */
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class PresetsTest {

    private static final String RESOLUTION = "vespera.clustering.resolution";
    private static final String NEIGHBOURS = "vespera.clustering.neighbours";
    private static final String OCR_PRESET = "vespera.docling.ocr-preset";
    private static final String TIMEOUTS = "vespera.docling.timeouts-in-a-row";
    private static final String SHINGLE_WORDS = "vespera.redundancy.shingle-words";
    private static final String PERMUTATIONS = "vespera.redundancy.minhash.permutations";
    private static final String BANDS = "vespera.redundancy.minhash.bands";
    private static final String ROWS = "vespera.redundancy.minhash.rows-per-band";
    private static final String SEED = "vespera.redundancy.minhash.seed";
    private static final String JACCARD = "vespera.redundancy.near-duplicate-jaccard";
    private static final String CONTAINMENT = "vespera.redundancy.containment-index";
    private static final String RARE_SAMPLE = "vespera.redundancy.rare-shingle-sample";
    private static final String RARE_HITS = "vespera.redundancy.rare-shingle-hits";
    private static final String TOP_SIMILARITIES = "vespera.relevance.top-similarities";
    private static final String SAMPLE_BANDS = "vespera.relevance.sample.bands";
    private static final String SAMPLED_PER_BAND = "vespera.relevance.sample.per-band";

    /** The keys held to a whole number from 1 to the most an int holds. */
    private static final List<String> WHOLE_NUMBERS = List.of(NEIGHBOURS, SHINGLE_WORDS, RARE_SAMPLE, TOP_SIMILARITIES,
            SAMPLE_BANDS, SAMPLED_PER_BAND, PERMUTATIONS, BANDS, ROWS);

    /** The most an int holds, the last whole number allowed. */
    private static final String MOST = "2147483647";

    /** One past it, which each type would have to cut down to hold. */
    private static final String PAST_THE_MOST = "2147483648";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    private static final String WHY = "set by this test";

    private static Presets naming(String key, String value) {
        return new Presets(PresetsFixture.shippedEnvironment().withProperty(key, value));
    }

    private static Presets naming(Map<String, String> values) {
        MockEnvironment environment = PresetsFixture.shippedEnvironment();
        values.forEach(environment::withProperty);
        return new Presets(environment);
    }

    private static Profile resolutionInTheProfile(String value) {
        return ProfileFixture.profile().clusterResolution(value, WHY).build();
    }

    private static String aWholeNumber(String key, String value) {
        return key + " is " + value + ": a whole number from 1 to 2147483647";
    }

    private static String aCut(String key, String value) {
        return key + " is " + value + ": a number above 0 and at most 1";
    }

    private static String theOcrPreset(String value) {
        return OCR_PRESET + " is \"" + value + "\": one preset's name, of letters, digits, '-', '_' and '.', and never"
                + " auto, which the converter resolves where nothing records it";
    }

    private static String theTimeouts(String value) {
        return TIMEOUTS + " is " + value + ": a whole number from 2 to 4, so that one slow document can still earn its"
                + " verdict and the count stays below the five failures in a row that stop the step";
    }

    private static String theSeed(String value) {
        return SEED + " is " + value + ": a whole number from -9223372036854775808 to 9223372036854775807";
    }

    private static String theHits(String value, String sample) {
        return RARE_HITS + " is " + value + ": a whole number from 1 to vespera.redundancy.rare-shingle-sample, " + sample;
    }

    /** Every row of the table, a key and a value, with the line that value gives. */
    static Stream<Arguments> oneValueNoRunCanBeMadeUnder() {
        List<Arguments> rows = new ArrayList<>();
        for (String value : List.of("0", "-1", "-0.5", "NaN", "Infinity", "-Infinity", "fine")) {
            rows.add(Arguments.of(RESOLUTION, value, RESOLUTION + " is " + value + ": a number above 0"));
        }
        for (String key : WHOLE_NUMBERS) {
            for (String value : List.of("0", "-1", "1.5", PAST_THE_MOST, "fine")) {
                rows.add(Arguments.of(key, value, aWholeNumber(key, value)));
            }
        }
        for (String value : List.of("", "   ", "auto", "AUTO", "Auto", "a;b", "a=b", "a b")) {
            rows.add(Arguments.of(OCR_PRESET, value, theOcrPreset(value)));
        }
        for (String value : List.of("1", "5", "0", "2.5", "fine")) {
            rows.add(Arguments.of(TIMEOUTS, value, theTimeouts(value)));
        }
        for (String value : List.of("9223372036854775808", "-9223372036854775809", "1.5", "fine")) {
            rows.add(Arguments.of(SEED, value, theSeed(value)));
        }
        for (String key : List.of(JACCARD, CONTAINMENT)) {
            for (String value : List.of("0", "-0.1", "1.5", "1.0000000000000002", "NaN", "Infinity", "fine")) {
                rows.add(Arguments.of(key, value, aCut(key, value)));
            }
        }
        for (String value : List.of("0", "-1", "33", "1.5", "fine")) {
            rows.add(Arguments.of(RARE_HITS, value, theHits(value, "32")));
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @MethodSource("oneValueNoRunCanBeMadeUnder")
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Each value no run can be made under is refused with the one line that names its key and its value")
    void eachValueIsRefusedWithItsLine(String key, String value, String line) {
        claim("configuration naming that value is refused, in the record's words",
                () -> assertThat(naming(key, value).refusal(NOTHING_NAMED)).contains(line));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A value naming a placeholder nothing resolves is refused in one line, and not by a stack trace")
    void aValueNamingAPlaceholderNothingResolvesIsRefusedInOneLine() {
        String placeholder = "${NOTHING_NAMES_THIS}";

        claim("configuration naming it is refused with the key's line, which says the placeholder",
                () -> assertThat(naming(NEIGHBOURS, placeholder).refusal(NOTHING_NAMED)).hasValueSatisfying(line ->
                        assertThat(line)
                                .startsWith(NEIGHBOURS + " is ")
                                .contains(placeholder)
                                .endsWith(": a whole number from 1 to 2147483647")
                                .doesNotContain("\n")));
    }

    @ParameterizedTest(name = "{0} permutations in {1} bands of {2} rows")
    @CsvSource({"128,16,7", "128,8,8", "64,16,8", "131073,65537,65537"})
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Bands times their rows other than the permutations are refused, however large the product")
    void bandsTimesRowsOtherThanThePermutationsAreRefused(String permutations, String bands, String rows) {
        Presets presets = naming(Map.of(PERMUTATIONS, permutations, BANDS, bands, ROWS, rows));

        claim("the line names the three together: " + bands + " times " + rows + " is not " + permutations + ", in whole"
                        + " numbers and not as an int cuts them down, in which the last row's product would come out equal",
                () -> assertThat(presets.refusal(NOTHING_NAMED)).contains(
                        "vespera.redundancy.minhash is " + permutations + " permutations in " + bands + " bands of "
                                + rows + " rows: the bands times their rows must be the permutations"));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("The hits are held to the sample configuration names, and the line says that sample")
    void theHitsAreHeldToTheSampleNamed() {
        claim("sixteen hits are too many of a sample of twelve",
                () -> assertThat(naming(Map.of(RARE_SAMPLE, "12", RARE_HITS, "16")).refusal(NOTHING_NAMED))
                        .contains(theHits("16", "12")));
        claim("and a sample of no shingles is refused for itself, ahead of the hits it leaves too many",
                () -> assertThat(naming(RARE_SAMPLE, "0").refusal(NOTHING_NAMED))
                        .contains(aWholeNumber(RARE_SAMPLE, "0")));
    }

    /** The values nearest each bound, each of which a run can be made under. */
    static Stream<Arguments> theNearestValuesAllowed() {
        List<Arguments> rows = new ArrayList<>();
        rows.add(Arguments.of(Map.of(RESOLUTION, String.valueOf(Double.MIN_VALUE))));
        rows.add(Arguments.of(Map.of(RESOLUTION, String.valueOf(Double.MAX_VALUE))));
        for (String key : List.of(NEIGHBOURS, SHINGLE_WORDS, TOP_SIMILARITIES, SAMPLE_BANDS, SAMPLED_PER_BAND)) {
            rows.add(Arguments.of(Map.of(key, "1")));
            rows.add(Arguments.of(Map.of(key, MOST)));
        }
        // A sample of 1 leaves the shipped 24 hits too many, so the hits come down with it.
        rows.add(Arguments.of(Map.of(RARE_SAMPLE, "1", RARE_HITS, "1")));
        rows.add(Arguments.of(Map.of(RARE_SAMPLE, MOST)));
        rows.add(Arguments.of(Map.of(RARE_HITS, "1")));
        rows.add(Arguments.of(Map.of(RARE_HITS, "32")));
        rows.add(Arguments.of(Map.of(TIMEOUTS, "2")));
        rows.add(Arguments.of(Map.of(TIMEOUTS, "4")));
        rows.add(Arguments.of(Map.of(PERMUTATIONS, "1", BANDS, "1", ROWS, "1")));
        rows.add(Arguments.of(Map.of(PERMUTATIONS, MOST, BANDS, MOST, ROWS, "1")));
        rows.add(Arguments.of(Map.of(PERMUTATIONS, MOST, BANDS, "1", ROWS, MOST)));
        rows.add(Arguments.of(Map.of(SEED, "9223372036854775807")));
        rows.add(Arguments.of(Map.of(SEED, "-9223372036854775808")));
        rows.add(Arguments.of(Map.of(SEED, "0")));
        for (String key : List.of(JACCARD, CONTAINMENT)) {
            rows.add(Arguments.of(Map.of(key, String.valueOf(Double.MIN_VALUE))));
            rows.add(Arguments.of(Map.of(key, "1")));
            rows.add(Arguments.of(Map.of(key, "1.0")));
        }
        for (String name : List.of("rapidocr", "easyocr", "a-b_c.D9", "autoocr", "x")) {
            rows.add(Arguments.of(Map.of(OCR_PRESET, name)));
        }
        return rows.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("theNearestValuesAllowed")
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("The value nearest each bound is one a run can be made under")
    void theNearestValueToEachBoundIsAllowed(Map<String, String> values) {
        claim("configuration naming " + values + " is refused by no line",
                () -> assertThat(naming(values).refusal(NOTHING_NAMED)).isEmpty());
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("What ships is refused by no line")
    void whatShipsIsAllowed() {
        claim("the sixteen values the application ships are ones a run can be made under",
                () -> assertThat(PresetsFixture.shipped().refusal(NOTHING_NAMED)).isEmpty());
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A configured resolution the profile's own stands in place of names no run, and stops nothing")
    void aConfiguredResolutionTheProfileStandsInPlaceOfIsNotRefused() {
        Presets presets = naming(RESOLUTION, "0");

        claim("with the profile stating 2.5, a configured 0 names no run",
                () -> assertThat(presets.refusal(resolutionInTheProfile("2.5"))).isEmpty());
        claim("but the profile stands in for the resolution alone: another value is refused beneath it",
                () -> assertThat(naming(Map.of(RESOLUTION, "0", NEIGHBOURS, "0")).refusal(resolutionInTheProfile("2.5")))
                        .contains(aWholeNumber(NEIGHBOURS, "0")));
    }

    @ParameterizedTest(name = "the profile holding \"{0}\"")
    @ValueSource(strings = {"", "   ", "fine", "0,5", "0", "-1", "NaN", "Infinity"})
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Where the profile's resolution cannot be acted on, the configured one is what would name the run, so it is refused")
    void aProfileValueThatCannotBeActedOnLeavesTheConfiguredOneRefused(String inTheProfile) {
        claim("nothing stands in place of a configured 0, so it is refused",
                () -> assertThat(naming(RESOLUTION, "0").refusal(resolutionInTheProfile(inTheProfile)))
                        .contains(RESOLUTION + " is 0: a number above 0"));
    }

    @Test
    @Story("How finely documents are grouped is a preset the profile may override")
    @DisplayName("A number above 0 in the profile is the resolution, over what configuration names")
    void theProfilesNumberIsTheResolution() {
        Presets presets = naming(RESOLUTION, "2.0");

        claim("the profile's 2.5 over a configured 2.0",
                () -> assertThat(presets.clusterResolution(resolutionInTheProfile("2.5"))).isEqualTo(2.5));
        claim("and its 1.0 over it too, the value that writes no member of the run",
                () -> assertThat(presets.clusterResolution(resolutionInTheProfile("1.0"))).isEqualTo(1.0));
    }

    @ParameterizedTest(name = "the profile holding \"{0}\"")
    @ValueSource(strings = {"", "   ", "fine", "0,5", "0", "-1", "NaN", "Infinity"})
    @Story("How finely documents are grouped is a preset the profile may override")
    @DisplayName("A resolution in the profile nothing can be acted on from leaves the preset, as configuration resolves it")
    void aProfileValueThatCannotBeActedOnLeavesThePreset(String inTheProfile) {
        claim("what applies is the configured 2.0 and not the shipped 1.0",
                () -> assertThat(naming(RESOLUTION, "2.0").clusterResolution(resolutionInTheProfile(inTheProfile)))
                        .isEqualTo(2.0));
    }

    @Test
    @Story("How finely documents are grouped is a preset the profile may override")
    @DisplayName("An unset resolution in the profile leaves the preset, as configuration resolves it")
    void anUnsetResolutionLeavesThePreset() {
        claim("what applies is the configured 2.0",
                () -> assertThat(naming(RESOLUTION, "2.0").clusterResolution(NOTHING_NAMED)).isEqualTo(2.0));
    }

    @Test
    @Story("A machine may name another value of a preset")
    @DisplayName("Each of the sixteen is read as configuration names it, and each to its own place")
    void everyPresetIsReadAsConfigurationNamesIt() {
        Presets presets = naming(Map.ofEntries(
                Map.entry(RESOLUTION, "2.5"),
                Map.entry(NEIGHBOURS, "7"),
                Map.entry(OCR_PRESET, "easyocr"),
                Map.entry(TIMEOUTS, "2"),
                Map.entry(SHINGLE_WORDS, "6"),
                Map.entry(PERMUTATIONS, "64"),
                Map.entry(BANDS, "8"),
                Map.entry(ROWS, "8"),
                Map.entry(SEED, "-1"),
                Map.entry(JACCARD, "0.9"),
                Map.entry(CONTAINMENT, "0.99"),
                Map.entry(RARE_SAMPLE, "16"),
                Map.entry(RARE_HITS, "12"),
                Map.entry(TOP_SIMILARITIES, "1"),
                Map.entry(SAMPLE_BANDS, "2"),
                Map.entry(SAMPLED_PER_BAND, "4")));

        claim("the two numbers of grouping",
                () -> assertThat(List.of(presets.clusterNeighbours(), presets.clusterResolution(NOTHING_NAMED)))
                        .containsExactly(7, 2.5));
        claim("the converter's two",
                () -> assertThat(List.of(presets.ocrPreset(), presets.timeoutsInARow())).containsExactly("easyocr", 2));
        claim("the shingle window",
                () -> assertThat(presets.shingleParameters()).isEqualTo(new ShingleParameters(6)));
        claim("the signature's four, the seed a negative long as a long holds it",
                () -> assertThat(presets.minHashParameters()).isEqualTo(new MinHashParameters(64, 8, 8, -1L)));
        claim("the two cuts and the containment path's two counts",
                () -> assertThat(presets.redundancyThresholds()).isEqualTo(new RedundancyThresholds(0.9, 0.99, 16, 12)));
        claim("and the number of similarities, the bands and the number from each, each to its own",
                () -> assertThat(List.of(presets.topSimilarities(), presets.sampleBands(), presets.sampledPerBand()))
                        .containsExactly(1, 2, 4));
    }

    /**
     * The shipped values read as what every run before the presets was minted under, the texts in bold of the record's
     * table: the three identities here, each byte for byte, since they are what {@code StageRuns} compares with to leave a
     * member out. {@code RunIdentityGoldenTest} holds that nothing is then written.
     */
    @Test
    @Story("A machine may name another value of a preset")
    @DisplayName("What ships reads as the numbers and identities every earlier run was minted under")
    void whatShipsReadsAsWhatEveryEarlierRunWasMintedUnder() {
        Presets presets = PresetsFixture.shipped();

        claim("the shingle window's identity",
                () -> assertThat(presets.shingleParameters().identity()).isEqualTo("word:5"));
        claim("the signature's, its seed in decimal",
                () -> assertThat(presets.minHashParameters().identity())
                        .isEqualTo("perms:128;bands:16;rows:8;seed:6840335614489015382"));
        claim("the cuts', the shipped 0.80 written as Java writes it",
                () -> assertThat(presets.redundancyThresholds().identity())
                        .isEqualTo("near-duplicate:0.8;containment:0.95;sample:32;hits:24"));
        claim("the OCR preset", () -> assertThat(presets.ocrPreset()).isEqualTo("rapidocr"));
        claim("the two numbers of grouping, the resolution with nothing in the profile",
                () -> assertThat(List.of(presets.clusterNeighbours(), presets.clusterResolution(NOTHING_NAMED)))
                        .containsExactly(15, 1.0));
        claim("and the timeouts, the similarities, the bands and the number from each",
                () -> assertThat(List.of(presets.timeoutsInARow(), presets.topSimilarities(), presets.sampleBands(),
                                presets.sampledPerBand()))
                        .containsExactly(3, 3, 5, 12));
    }
}
