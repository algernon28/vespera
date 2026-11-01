package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The shingle window and MinHash's numbers named by configuration do what they name (ADR-330 §9): the number that names
 * the run is the number that works. Three documents of words of their own, so each shingle is in one document, no floor
 * strips it, and each is signed. One invocation, made once for the class, which ends at the seed folder wanted.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheShingleWindowAndMinHashReachTheirRowsInvocationTest.Beans.class})
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the shingle window and the signature's numbers in the steps that use them")
class TheShingleWindowAndMinHashReachTheirRowsInvocationTest {

    private static final int DOCUMENTS = 3;

    /** The words of each document, each a word of that document alone. */
    private static final int WORDS = 40;

    /** A window other than the shipped 5. */
    private static final int WINDOW = 6;

    /** Each document's shingles: one for each place a window of {@value #WINDOW} words starts. */
    private static final int SHINGLES_OF_A_DOCUMENT = WORDS - WINDOW + 1;

    /** Permutations other than the shipped 128, in bands of the shipped 8 rows. */
    private static final int PERMUTATIONS = 64;

    /** Bands other than the shipped 16, which with {@value #ROWS} rows make {@value #PERMUTATIONS} permutations. */
    private static final int BANDS = 8;

    private static final int ROWS = 8;

    private static final long SEED = 1L;

    /** How long a minimum is stored, in bytes. */
    private static final int BYTES_OF_A_MINIMUM = 4;

    /** Above every measured document frequency here, so stage 4's gate is open, and strips nothing. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Answers each document with words of its own. */
    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        DoclingExtractor documentsOfTheirOwnWords(JdbcTemplate jdbcTemplate) {
            PathScriptedExtractor extractor = new PathScriptedExtractor().cachingInto(jdbcTemplate);
            for (int document = 0; document < DOCUMENTS; document++) {
                extractor.answering("corpus-" + document + ".txt",
                        TextsOfTheirOwn.converted(TextsOfTheirOwn.words("doc" + document + "word", WORDS)));
            }
            return extractor;
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.redundancy.shingle-words", () -> WINDOW);
        registry.add("vespera.redundancy.minhash.permutations", () -> PERMUTATIONS);
        registry.add("vespera.redundancy.minhash.bands", () -> BANDS);
        registry.add("vespera.redundancy.minhash.rows-per-band", () -> ROWS);
        registry.add("vespera.redundancy.minhash.seed", () -> SEED);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static boolean invoked;

    @BeforeEach
    void oneInvocation() throws IOException {
        if (invoked) {
            return;
        }
        invoked = true;
        for (int document = 0; document < DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "the bytes of document " + document);
        }
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
        cli.run("run", root.toString());
    }

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("Every shingle is kept under the window configured")
    void theShingleRowsAreKeyedByTheWindowConfigured() {
        claim("the one identity the shingle rows carry names " + WINDOW + " words",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT DISTINCT shingle_parameter_identity FROM shingle", String.class))
                        .containsExactly("word:" + WINDOW));
    }

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("The window configured is the one that cuts the text")
    void theWindowConfiguredCutsTheText() {
        claim("each of the " + DOCUMENTS + " documents of " + WORDS + " words gives " + SHINGLES_OF_A_DOCUMENT
                        + " shingles, one for each place a window of " + WINDOW + " starts",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle", Integer.class))
                        .isEqualTo(DOCUMENTS * SHINGLES_OF_A_DOCUMENT));
    }

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("Each signature is under the identity of what configuration names")
    void eachSignatureIsUnderTheIdentityConfigured() {
        claim("the one identity of the signatures names the window, the permutations, the bands, the rows, the seed and"
                        + " the floor",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT DISTINCT signature_identity FROM minhash_signature", String.class))
                        .containsExactly("word:" + WINDOW + ";perms:" + PERMUTATIONS + ";bands:" + BANDS + ";rows:" + ROWS
                                + ";seed:" + SEED + ";floor:" + BOILERPLATE_FLOOR));
    }

    @Test
    @Story("How redundancy is measured is a preset")
    @DisplayName("Each signature is of the numbers configured")
    void eachSignatureIsOfTheNumbersConfigured() {
        claim("each of the " + DOCUMENTS + " signatures holds " + PERMUTATIONS + " minima",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT LENGTH(signature) FROM minhash_signature", Integer.class))
                        .hasSize(DOCUMENTS).containsOnly(PERMUTATIONS * BYTES_OF_A_MINIMUM));
        claim("and each stands in " + BANDS + " bands",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM signature_band", Integer.class))
                        .isEqualTo(DOCUMENTS * BANDS));
    }
}
