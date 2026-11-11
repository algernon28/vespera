package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
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
    private static final String NEIGHBOURS = "vespera.clustering.neighbours";
    private static final String OCR_PRESET = "vespera.docling.ocr-preset";
    private static final String TIMEOUTS = "vespera.docling.timeouts-in-a-row";
    private static final String PERMUTATIONS = "vespera.redundancy.minhash.permutations";
    private static final String BANDS = "vespera.redundancy.minhash.bands";
    private static final String ROWS = "vespera.redundancy.minhash.rows-per-band";
    private static final String SEED = "vespera.redundancy.minhash.seed";
    private static final String JACCARD = "vespera.redundancy.near-duplicate-jaccard";
    private static final String RARE_HITS = "vespera.redundancy.rare-shingle-hits";
    private static final String TOP_SIMILARITIES = "vespera.relevance.top-similarities";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    /**
     * The presets read from {@code application.yaml} itself, its keys alone being in the environment, so that what a
     * test of what ships holds is the file and not {@link PresetsFixture}'s copy of it, and no value of the machine
     * running the test stands over one.
     */
    private static Presets theShippedFile() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("the shipped file", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        return new Presets(environment);
    }

    private static Presets naming(String key, String value) {
        return new Presets(PresetsFixture.shippedEnvironment().withProperty(key, value));
    }

    private static Presets naming(Map<String, String> values) {
        MockEnvironment environment = PresetsFixture.shippedEnvironment();
        values.forEach(environment::withProperty);
        return new Presets(environment);
    }

    /**
     * One value of §9's table no run can be made under for each check that refuses it: the near-duplicate cut at 0,
     * bands times rows that are not the permutations, a score of no similarities, an OCR preset of {@code auto}, a seed
     * past what a long holds, timeouts past 4, and more hits than the shipped sample of 32. Each row names its values over
     * the shipped sixteen, so the line it gives is that value's alone.
     */
    static Stream<Arguments> oneValueNoRunCanBeMadeUnder() {
        return Stream.of(
                Arguments.of(Map.of(JACCARD, "0"), JACCARD + " is 0: a number above 0 and at most 1"),
                Arguments.of(Map.of(PERMUTATIONS, "128", BANDS, "8", ROWS, "8"),
                        "vespera.redundancy.minhash is 128 permutations in 8 bands of 8 rows: the bands times their rows"
                                + " must be the permutations"),
                Arguments.of(Map.of(TOP_SIMILARITIES, "0"), TOP_SIMILARITIES + " is 0: a whole number from 1 to 2147483647"),
                Arguments.of(Map.of(OCR_PRESET, "auto"),
                        OCR_PRESET + " is \"auto\": one preset's name, of letters, digits, '-', '_' and '.', and never"
                                + " auto, which the converter resolves where nothing records it"),
                Arguments.of(Map.of(SEED, "9223372036854775808"),
                        SEED + " is 9223372036854775808: a whole number from -9223372036854775808 to 9223372036854775807"),
                Arguments.of(Map.of(TIMEOUTS, "5"),
                        TIMEOUTS + " is 5: a whole number from 2 to 4, so that one slow document can still earn its"
                                + " verdict and the count stays below the five failures in a row that stop the step"),
                Arguments.of(Map.of(RARE_HITS, "33"),
                        RARE_HITS + " is 33: a whole number from 1 to vespera.redundancy.rare-shingle-sample, 32"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneValueNoRunCanBeMadeUnder")
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Each value no run can be made under is refused with the one line that names its key and its value")
    void eachValueIsRefusedWithItsLine(Map<String, String> values, String line) {
        claim("configuration naming " + values + " is refused, in the record's words",
                () -> assertThat(naming(values).refusal(NOTHING_NAMED)).contains(line));
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

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("What ships is refused by no line")
    void whatShipsIsAllowed() throws IOException {
        Presets presets = theShippedFile();

        claim("the sixteen values application.yaml holds are ones a run can be made under",
                () -> assertThat(presets.refusal(NOTHING_NAMED)).isEmpty());
    }

    /**
     * The shipped values read as what every run before the presets was minted under, the texts in bold of the record's
     * table: the three identities here, each byte for byte, since they are what {@code StageRuns} compares with to leave a
     * member out. {@code RunIdentityGoldenTest} holds that nothing is then written.
     */
    @Test
    @Story("A machine may name another value of a preset")
    @DisplayName("What ships reads as the numbers and identities every earlier run was minted under")
    void whatShipsReadsAsWhatEveryEarlierRunWasMintedUnder() throws IOException {
        Presets presets = theShippedFile();

        claim("the shingle window's identity",
                () -> assertThat(presets.shingleWindow().identity()).isEqualTo("word:5"));
        claim("the signature's, its seed in decimal",
                () -> assertThat(presets.minHash().identity())
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
