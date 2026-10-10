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
 * A file whose only text is in page headers and page footers, taken from the walk through to relevance
 * scoring (ADR-232, #499), on the corpus side and on the seed side.
 *
 * <p><b>What it was.</b> Stage 2's tier 1 read the text of every item, so such a corpus file survived; the
 * chunker leaves page headers and footers out, so it had no chunk and no vector; and {@code
 * RelevanceScoring.scoreAndRecord} threw on it, in every invocation, so no document of the corpus was ever
 * scored. On the seed side ADR-231 section 2a left such a seed out with a warning. Since ADR-232 tier 1 and
 * the seed's usability bar count the text the chunker cuts chunks from: the corpus file earns {@code
 * degenerate-output} and the seed is an unusable seed, each under the one reason {@link #THE_REASON}.
 *
 * <p><b>Every file is written with its folder's name in it</b>, as in {@link
 * APullWhileTheEmbeddingStepRunsInvocationTest} and for its reason: conversions and vectors are keyed by
 * content, and the tests of every class sharing this context share one database.
 */
@CascadeSliceTest
@ExtendWith(OutputCaptureExtension.class)
@Import(SeedScriptedExtractionBeans.class)
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("499")
@Link(name = "ADR-232", url = Adr.A_FILE_WHOSE_ONLY_TEXT_IS_IN_PAGE_HEADERS_AND_FOOTERS_IS_DEGENERATE_OUTPUT_AND_SUCH_A_SEED_IS_UNUSABLE, type = "adr")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
class AFileOfPageHeadersAndFootersOnlyInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The corpus documents that have a body. */
    private static final int DOCUMENTS_WITH_A_BODY = 2;

    /** The seed documents that have a body. */
    private static final int SEEDS_WITH_A_BODY = 2;

    /** The one file, of the corpus or of the seed folder, whose only text is a page header and footer. */
    private static final long THE_ONE_FILE = 1;

    /** A step recorded as finished is recorded in one row. */
    private static final long RECORDED_ONCE = 1;

    /** The reason the verdict and the unusable seed are recorded under, word for word (ADR-232 section 1). */
    private static final String THE_REASON = "zero alphanumeric content outside page headers and footers";

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
    @Story("A document whose only text is in page headers and footers is removed and the rest are scored")
    @DisplayName("A corpus document with text only in a page header and a page footer is removed at text extraction, with a reason that says so, and every other document is scored")
    void aCorpusFileOfPageHeadersAndFootersOnlyIsDegenerateOutputAndTheRestAreScored(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        documentsWithABody(seeds, "seed", SEEDS_WITH_A_BODY);
        Files.writeString(
                root.resolve(SeedScriptedExtractionBeans.HEADERS_AND_FOOTERS_ONLY),
                "a corpus file that converts to a page header and a page footer alone, of " + root.getFileName());
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: one document with nothing in it to score is that document's"
                        + " matter and stops nothing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "nothing says a document had no stored vectors to score, which is what the scoring stopped"
                        + " on, in every invocation, while such a document was let through",
                () -> assertThat(output.getAll()).doesNotContain("has no stored chunk vectors"));
        claim(
                "the document carries " + THE_ONE_FILE + " removal, recorded by text extraction as output with"
                        + " no usable content, and its reason says where the only text was",
                () -> assertThat(verdictsOf(root, SeedScriptedExtractionBeans.HEADERS_AND_FOOTERS_ONLY))
                        .containsExactly("extraction DEGENERATE_OUTPUT " + THE_REASON));
        claim(
                "the " + DOCUMENTS_WITH_A_BODY + " documents with a body are scored and the removed one is"
                        + " not, and scoring is recorded as finished",
                () -> assertThat(scoringRunIdsFor(root)).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isEqualTo(DOCUMENTS_WITH_A_BODY);
                    assertThat(finishedRows(run, StepNames.RELEVANCE_SCORING)).isEqualTo(RECORDED_ONCE);
                }));
    }

    @Test
    @Story("A seed document whose only text is in page headers and footers is recorded as unusable")
    @DisplayName("A seed document with text only in a page header is recorded as unusable, with a reason that says so, and the scoring goes on against the others")
    void aSeedOfPageHeadersOnlyIsAnUnusableSeedAndScoringGoesOn(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        documentsWithABody(seeds, "seed", SEEDS_WITH_A_BODY);
        Files.writeString(
                seeds.resolve(SeedScriptedExtractionBeans.HEADER_ONLY_SEED),
                "a seed that converts to a page header alone, of " + seeds.getFileName());
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a seed document with nothing in it to score against narrows"
                        + " what the others are measured by and stops nothing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                THE_ONE_FILE + " seed document is recorded as unusable, and its reason says where its only"
                        + " text was",
                () -> assertThat(unusableSeedReasonsOf(seeds)).containsExactly(THE_REASON));
        claim(
                "the " + SEEDS_WITH_A_BODY + " seed documents with a body have vectors and the unusable one"
                        + " has none",
                () -> assertThat(documentsWithVectors(seeds)).isEqualTo(SEEDS_WITH_A_BODY));
        claim(
                "all " + DOCUMENTS_WITH_A_BODY + " corpus documents are scored, and scoring is recorded as"
                        + " finished",
                () -> assertThat(scoringRunIdsFor(root)).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isEqualTo(DOCUMENTS_WITH_A_BODY);
                    assertThat(finishedRows(run, StepNames.RELEVANCE_SCORING)).isEqualTo(RECORDED_ONCE);
                }));
        claim(
                "and scoring has no seed document to warn about: the one with nothing to embed was recorded"
                        + " where the seed documents are read, and is not among those scoring is handed",
                () -> assertThat(output.getAll()).doesNotContain("produced text and no chunk"));
    }

    /**
     * The expectations are those of {@code SeedExtractionInvocationTest.mintsNoRunWhenNoSeedIsUsable}, for a
     * folder whose one seed converts with no text at all: success, the same gated line, no measurement run,
     * and no unusable-seed row, because such a row carries the run that found it and there is none. So the
     * reason is on no row here; it is in the line seed extraction writes as it finishes each seed.
     */
    @Test
    @Story("A seed folder holding only such seed documents ends the work at the seeds, as a folder with no text does")
    @DisplayName("With a seed document of a page header alone as the only one, the invocation succeeds, scores nothing and says no seed document produced any text outside its page headers and footers, as for a folder of seed documents with no text")
    void aSeedFolderOfPageHeadersOnlyIsGatedAsOneWithNoText(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        documentsWithABody(root, "corpus", DOCUMENTS_WITH_A_BODY);
        Files.writeString(
                seeds.resolve(SeedScriptedExtractionBeans.HEADER_ONLY_SEED),
                "the only seed, which converts to a page header alone, of " + seeds.getFileName());
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a seed folder with nothing usable in it is a value to fix,"
                        + " not an error",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it says the one line that is true of a folder of seed documents with no text and of this"
                        + " folder, and what to do",
                () -> assertThat(output.getAll())
                        .contains("No seed document produced any text outside its page headers and footers")
                        .contains("Fix the seed folder and run again"));
        claim(
                "the seed document was found unusable for where its only text was",
                () -> assertThat(output.getAll()).contains("unusable: " + THE_REASON));
        claim(
                "nothing was measured or scored against the seed folder: no run was made for either, and so"
                        + " no unusable seed document is recorded, a record of one belonging to such a run",
                () -> {
                    assertThat(runsOf("seed-measurement", root)).isEmpty();
                    assertThat(scoringRunIdsFor(root)).isEmpty();
                    assertThat(unusableSeedReasonsOf(seeds)).isEmpty();
                });
    }

    private List<String> runsOf(String stage, Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id WHERE run.stage = ? AND w.root = ?",
                String.class,
                stage,
                walkRoot(root));
    }

    /** {@code count} files beneath {@code folder}, each naming its folder so that no other test wrote its bytes. */
    private void documentsWithABody(Path folder, String kind, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            Files.writeString(
                    folder.resolve(kind + "-" + i + ".txt"), "a " + kind + " document " + i + " of " + folder.getFileName());
        }
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and no threshold. */
    private void profile(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Every verdict against the file named {@code name} beneath {@code root}: its run's stage, its kind and its reason. */
    private List<String> verdictsOf(Path root, String name) {
        return jdbcTemplate.queryForList(
                "SELECT run.stage || ' ' || v.kind || ' ' || v.reason FROM verdict v"
                        + " JOIN run ON run.id = v.run_id JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path = ?",
                String.class,
                walkRoot(root),
                name);
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

    private long finishedRows(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows == null ? 0 : rows;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    private List<String> scoringRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                String.class,
                walkRoot(root));
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }
}
