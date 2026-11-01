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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Every bound of the table of refusals in ADR-330 §9 not already held by a class of its own: a value named by
 * configuration that no run can be made under stops {@code vespera run} at its start, in the record's one line and with
 * no stack trace, no run made.
 *
 * <p>{@code AnOcrPresetTheConverterWouldResolveIsRefusedInvocationTest}, {@code
 * MinHashBandsThatDoNotMakeItsPermutationsAreRefusedInvocationTest} and {@code
 * ARelevanceScoreOfNoSimilaritiesIsRefusedInvocationTest} hold {@code auto}, bands times rows and no similarities. Each
 * class nested here is a context of its own, because the configuration is a property of the context; what each adds is
 * its value and its line. A value that is no number stands among them: it is refused by the same line, so it reaches
 * the check at the start and is not a failure of the context to bind it.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the refusal of a preset no run can be made under")
class APresetNoRunCanBeMadeUnderIsRefusedInvocationTest {

    /** What a stack frame looks like in the output, which a refusal in one line does not print. */
    private static final String A_STACK_FRAME = "\tat ";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** The line of a count that must be a whole number from 1 to the most an int holds. */
    private static String aWholeNumber(String key, String value) {
        return key + " is " + value + ": a whole number from 1 to 2147483647";
    }

    /** The line of a cut that must be above 0 and at most 1. */
    private static String aCut(String key, String value) {
        return key + " is " + value + ": a number above 0 and at most 1";
    }

    /** The line of the timeouts in a row. */
    private static String theTimeouts(String value) {
        return "vespera.docling.timeouts-in-a-row is " + value + ": a whole number from 2 to 4, so that one slow"
                + " document can still earn its verdict and the count stays below the five failures in a row that stop"
                + " the step";
    }

    /** The line of an OCR preset. */
    private static String theOcrPreset(String value) {
        return "vespera.docling.ocr-preset is \"" + value + "\": one preset's name, of letters, digits, '-', '_' and"
                + " '.', and never auto, which the converter resolves where nothing records it";
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @BeforeAll
    static void aCorpusAndASeed() throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** What each value's own class adds to: one invocation, and the three claims of a refusal. */
    @Epic("Pipeline")
    @Feature("What a piece of work is identified by")
    abstract static class Refused {

        @Autowired
        private VesperaCli cli;

        @Autowired
        private ProfileStore profileStore;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        /** The record's line for this value, word for word. */
        abstract String theLine();

        @Test
        @Story("A value no run can be made under stops the invocation")
        @DisplayName("It stops the invocation in one line, at its start, before anything is walked or recorded")
        void stopsInOneLineAtTheStart(CapturedOutput output) {
            everyGateOpen(profileStore);
            int before = output.getAll().length();

            cli.run("run", root.toString());
            String said = output.getAll().substring(before);

            claim("the invocation ends non-zero, because it was stopped",
                    () -> assertThat(cli.getExitCode()).isNotZero());
            claim("exactly one line says the record's words for the value, and no stack trace stands in its place",
                    () -> {
                        assertThat(said.lines().filter(line -> line.contains(theLine())).toList()).hasSize(1);
                        assertThat(said).doesNotContain(A_STACK_FRAME);
                    });
            claim("no run was made and no walk begun, so the value stopped the invocation at its start and nothing was"
                            + " recorded",
                    () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run", Integer.class)).isZero());
        }
    }

    private static void everyGateOpen(ProfileStore profileStore) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
    }

    // ---- the scoring run

    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=0")
    @DisplayName("A resolution of 0")
    class AResolutionOfZero extends Refused {
        @Override
        String theLine() {
            return "vespera.clustering.resolution is 0: a number above 0";
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=NaN")
    @DisplayName("A resolution that is no finite number")
    class AResolutionThatIsNoFiniteNumber extends Refused {
        @Override
        String theLine() {
            return "vespera.clustering.resolution is NaN: a number above 0";
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=fine")
    @DisplayName("A resolution that is no number")
    class AResolutionThatIsNoNumber extends Refused {
        @Override
        String theLine() {
            return "vespera.clustering.resolution is fine: a number above 0";
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.clustering.neighbours=3000000000")
    @DisplayName("More neighbours than a whole number here can hold")
    class MoreNeighboursThanAWholeNumberHolds extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.clustering.neighbours", "3000000000");
        }
    }

    @Nested
    @TestPropertySource(properties = "vespera.clustering.neighbours=0")
    @DisplayName("No neighbours")
    class NoNeighbours extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.clustering.neighbours", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.clustering.neighbours=1.5")
    @DisplayName("Neighbours that are no whole number")
    class NeighboursThatAreNoWholeNumber extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.clustering.neighbours", "1.5");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.relevance.sample.bands=0")
    @DisplayName("A sample of no bands")
    class ASampleOfNoBands extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.relevance.sample.bands", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.relevance.sample.per-band=0")
    @DisplayName("A sample of nothing from each band")
    class ASampleOfNothingFromEachBand extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.relevance.sample.per-band", "0");
        }

    }

    // ---- the extraction run

    @Nested
    @TestPropertySource(properties = "vespera.docling.ocr-preset=")
    @DisplayName("A blank OCR preset")
    class ABlankOcrPreset extends Refused {
        @Override
        String theLine() {
            return theOcrPreset("");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.docling.ocr-preset=a;b")
    @DisplayName("An OCR preset that would misread in the extractor identity")
    class AnOcrPresetThatWouldMisreadInTheIdentity extends Refused {
        @Override
        String theLine() {
            return theOcrPreset("a;b");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.docling.timeouts-in-a-row=1")
    @DisplayName("One timeout in a row, below the bound")
    class OneTimeoutInARow extends Refused {
        @Override
        String theLine() {
            return theTimeouts("1");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.docling.timeouts-in-a-row=5")
    @DisplayName("Five timeouts in a row, above the bound")
    class FiveTimeoutsInARow extends Refused {
        @Override
        String theLine() {
            return theTimeouts("5");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.shingle-words=0")
    @DisplayName("A shingle of no words")
    class AShingleOfNoWords extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.redundancy.shingle-words", "0");
        }

    }

    // ---- the content-redundancy run

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.minhash.permutations=0")
    @DisplayName("No permutations")
    class NoPermutations extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.redundancy.minhash.permutations", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.minhash.bands=0")
    @DisplayName("No bands of a signature")
    class NoBandsOfASignature extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.redundancy.minhash.bands", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.minhash.rows-per-band=0")
    @DisplayName("No rows in a band of a signature")
    class NoRowsInABand extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.redundancy.minhash.rows-per-band", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.minhash.seed=9223372036854775808")
    @DisplayName("A seed one past what a long holds")
    class ASeedPastALong extends Refused {
        @Override
        String theLine() {
            return "vespera.redundancy.minhash.seed is 9223372036854775808: a whole number from -9223372036854775808"
                    + " to 9223372036854775807";
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.near-duplicate-jaccard=0")
    @DisplayName("A near-duplicate cut of 0, which would judge every pair")
    class ANearDuplicateCutOfZero extends Refused {
        @Override
        String theLine() {
            return aCut("vespera.redundancy.near-duplicate-jaccard", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.near-duplicate-jaccard=1.5")
    @DisplayName("A near-duplicate cut above 1, which would judge no pair")
    class ANearDuplicateCutAboveOne extends Refused {
        @Override
        String theLine() {
            return aCut("vespera.redundancy.near-duplicate-jaccard", "1.5");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.containment-index=0")
    @DisplayName("A containment cut of 0")
    class AContainmentCutOfZero extends Refused {
        @Override
        String theLine() {
            return aCut("vespera.redundancy.containment-index", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.containment-index=1.5")
    @DisplayName("A containment cut above 1")
    class AContainmentCutAboveOne extends Refused {
        @Override
        String theLine() {
            return aCut("vespera.redundancy.containment-index", "1.5");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.rare-shingle-sample=0")
    @DisplayName("A sample of no rare shingles, which is named before the hits it would also leave without a sample")
    class ASampleOfNoRareShingles extends Refused {
        @Override
        String theLine() {
            return aWholeNumber("vespera.redundancy.rare-shingle-sample", "0");
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.rare-shingle-hits=0")
    @DisplayName("No hits among the rare shingles")
    class NoHitsAmongTheRareShingles extends Refused {
        @Override
        String theLine() {
            return "vespera.redundancy.rare-shingle-hits is 0: a whole number from 1 to"
                    + " vespera.redundancy.rare-shingle-sample, 32";
        }

    }

    @Nested
    @TestPropertySource(properties = "vespera.redundancy.rare-shingle-hits=33")
    @DisplayName("More hits than the sample of rare shingles")
    class MoreHitsThanTheSample extends Refused {
        @Override
        String theLine() {
            return "vespera.redundancy.rare-shingle-hits is 33: a whole number from 1 to"
                    + " vespera.redundancy.rare-shingle-sample, 32";
        }

    }

    // ---- where nothing is refused, or the file has already been written

    /** A resolution the profile stands in place of is used by no run, so nothing is refused of it. */
    @Nested
    @TestPropertySource(properties = "vespera.clustering.resolution=0")
    @DisplayName("A resolution of 0 under a profile that names another")
    @Epic("Pipeline")
    @Feature("What a piece of work is identified by")
    class AResolutionOfZeroThatTheProfileStandsInPlaceOf {

        @Autowired
        private VesperaCli cli;

        @Autowired
        private ProfileStore profileStore;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        @Story("A value no run can be made under stops the invocation")
        @DisplayName("The invocation is not stopped: the profile's value names the scoring run")
        void stopsNothing() throws IOException {
            everyGateOpen(profileStore);
            Path profile = profileStore.file();
            Files.writeString(profile, Files.readString(profile, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^clusterResolution:\\R(  .*\\R)*", "")
                    + "clusterResolution:\n  value: \"2.5\"\n  provenance: \"set by this test\"\n", StandardCharsets.UTF_8);

            cli.run("run", root.toString());

            claim("the invocation ends well, and the one scoring run says the profile's resolution",
                    () -> {
                        assertThat(cli.getExitCode()).isZero();
                        assertThat(jdbcTemplate.queryForList(
                                        "SELECT config_consumed FROM run WHERE stage = 'embedding-scoring'", String.class))
                                .singleElement().asString().endsWith(",\"clusterResolution\":2.5}");
                    });
        }

        /** The working directory is the outer class's, and the next class's context reads the file as it is built. */
        @AfterEach
        void takeTheKeyOutOfTheProfile() throws IOException {
            Files.deleteIfExists(profileStore.file());
        }
    }

    /** An invocation refused at its start writes no file (ADR-330 §1). */
    @Nested
    @TestPropertySource(properties = "vespera.relevance.top-similarities=0")
    @DisplayName("A value refused at the start")
    @Epic("Pipeline")
    @Feature("What a piece of work is identified by")
    class AValueRefusedAtTheStartWritesNoFile {

        @Autowired
        private VesperaCli cli;

        @Autowired
        private ProfileStore profileStore;

        @Test
        @Story("A value no run can be made under stops the invocation")
        @DisplayName("The file of values the jar writes stays as the last invocation left it")
        void leavesTheFileAsItWas() throws IOException {
            everyGateOpen(profileStore);
            Path file = workingDirectory.resolve("effective-values.properties");
            Files.writeString(file, "left=by this test\n", StandardCharsets.UTF_8);

            cli.run("run", root.toString());

            claim("the invocation was stopped", () -> assertThat(cli.getExitCode()).isNotZero());
            claim("and what this test left in the file is still all it holds, an invocation refused at its start"
                            + " writing none",
                    () -> assertThat(file).hasContent("left=by this test\n"));
        }
    }
}

