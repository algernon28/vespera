package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * The word floor, from the profile to the verdict, through a whole invocation (ADR-282): a file that converts to one
 * word is {@code degenerate-output} under a floor of 2, with the reason that says so, and the files with more words
 * are kept; unset, the floor removes nothing and is no value the invocation stops for; and a seed of one word is a
 * usable seed under the same floor, since a seed is held to tier 1 alone (ADR-083).
 *
 * <p>The one-word file is {@link SeedScriptedExtractionBeans#ONE_WORD}, whose conversion is "OK". <b>Every file is
 * written with its folder's name in it</b>, as in {@link AFileOfPageHeadersAndFootersOnlyInvocationTest} and for its
 * reason: conversions and vectors are keyed by content, and the tests of every class sharing this context share one
 * database.
 */
@CascadeSliceTest
@ExtendWith(OutputCaptureExtension.class)
@Import(SeedScriptedExtractionBeans.class)
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
class TheWordFloorInvocationTest {

    /** The floor the operator writes: two words or more are kept. */
    private static final String A_FLOOR_OF_TWO = "2";

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";
    private static final String WHY = "set by this test";

    /** The corpus documents, or seeds, that have a body of seven words. */
    private static final int DOCUMENTS_WITH_A_BODY = 2;

    /** The verdict the one-word file carries under a floor of two, as stage, kind and reason. */
    private static final String REMOVED_FOR_ONE_WORD =
            "extraction DEGENERATE_OUTPUT 1 word(s), below the configured word floor 2";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void theRuntimeServesEveryModelBefore() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void theRuntimeServesEveryModelAfter() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("A file with fewer words than the word floor is removed, whatever its kind")
    @DisplayName("Under a word floor of two, a file that converts to one word is removed at text extraction with a reason that says so, and the others are kept")
    void aOneWordFileIsRemovedUnderAFloorOfTwo(@TempDir Path root) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.ONE_WORD), "one word, of " + root.getFileName());
        profileStore.save(ProfileFixture.profile().degenerateOutputWordFloor(A_FLOOR_OF_TWO, WHY).build());

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a file with too few words is that file's matter and stops nothing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the one-word file carries one removal, by text extraction, as output with no recognisable text, and"
                        + " its reason states its words and the floor",
                () -> assertThat(verdictsOf(root, SeedScriptedExtractionBeans.ONE_WORD))
                        .containsExactly(REMOVED_FOR_ONE_WORD));
        claim(
                "the " + DOCUMENTS_WITH_A_BODY + " documents of seven words carry no verdict",
                () -> assertThat(verdictsOf(root, "corpus-0.txt", "corpus-1.txt")).isEmpty());
    }

    @Test
    @Story("A word floor left unset removes nothing and is no stop")
    @DisplayName("With the word floor unset, a file that converts to one word is kept, and the invocation stops where it did")
    void anUnsetFloorRemovesNothingAndIsNoStop(@TempDir Path root, CapturedOutput output) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.ONE_WORD), "one word, unset, of " + root.getFileName());
        profileStore.save(ProfileFixture.profile().build());

        cli.run("run", root.toString());

        claim(
                "observe before enforce: the one-word file carries no verdict while no floor is set",
                () -> assertThat(verdictsOf(root, SeedScriptedExtractionBeans.ONE_WORD)).isEmpty());
        claim(
                "the invocation ends for the value it always ended for after the content census, the boilerplate floor,"
                        + " and does not ask for a word floor",
                () -> assertThat(output.getAll())
                        .contains("boilerplateDocumentFrequencyFloor")
                        .doesNotContain("degenerateOutputWordFloor"));
    }

    @Test
    @Story("A seed is held to usable text alone")
    @DisplayName("Under a word floor of two, a seed document of one word is usable and is measured with the others")
    void aOneWordSeedIsUsableUnderTheFloor(@TempDir Path root, @TempDir Path seeds) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        documentsWithABody(seeds, "seed", DOCUMENTS_WITH_A_BODY - 1);
        Files.writeString(seeds.resolve(SeedScriptedExtractionBeans.ONE_WORD), "one word seed, of " + seeds.getFileName());
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), WHY)
                .degenerateOutputWordFloor(A_FLOOR_OF_TWO, WHY)
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());

        cli.run("run", root.toString());

        claim(
                "no seed document is recorded as unusable: a seed is held to having a letter or a digit outside its"
                        + " page headers and footers, and never to the word floor",
                () -> assertThat(unusableSeedReasonsOf(seeds)).isEmpty());
        claim(
                "both seed documents, the one of one word among them, have vectors to score the corpus against",
                () -> assertThat(documentsWithVectors(seeds)).isEqualTo(DOCUMENTS_WITH_A_BODY));
    }

    /** {@code count} files beneath {@code folder}, each naming its folder so that no other test wrote its bytes. */
    private void documentsWithABody(Path folder, String kind, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            Files.writeString(
                    folder.resolve(kind + "-" + i + ".txt"), "a " + kind + " document " + i + " of " + folder.getFileName());
        }
    }

    /** Every verdict against the files named beneath {@code root}: its run's stage, its kind and its reason. */
    private List<String> verdictsOf(Path root, String... names) {
        return jdbcTemplate.queryForList(
                "SELECT run.stage || ' ' || v.kind || ' ' || v.reason FROM verdict v"
                        + " JOIN run ON run.id = v.run_id JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path IN ("
                        + String.join(", ", Collections.nCopies(names.length, "?")) + ")",
                String.class,
                concat(walkRoot(root), names));
    }

    private static Object[] concat(String first, String[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    /** The reason of every unusable seed found beneath {@code seeds}, under any run. */
    private List<String> unusableSeedReasonsOf(Path seeds) {
        return jdbcTemplate.queryForList(
                "SELECT u.reason FROM unusable_seed u JOIN file_occurrence f ON f.id = u.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                String.class,
                walkRoot(seeds));
    }

    /** How many file occurrences found beneath {@code walked} have a vector stored, by the key of their conversion. */
    private long documentsWithVectors(Path walked) {
        Long documents = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT k.occurrence_id) FROM extraction_cache_key k"
                        + " JOIN file_occurrence f ON f.id = k.occurrence_id JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? AND EXISTS (SELECT 1 FROM vector v WHERE v.content_hash = k.content_hash)",
                Long.class,
                walkRoot(walked));
        return documents == null ? 0 : documents;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }
}
