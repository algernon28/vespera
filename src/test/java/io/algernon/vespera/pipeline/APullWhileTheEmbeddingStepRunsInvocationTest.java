package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
 * What a pull of the embedding model does when it lands while stage 5's embedding step is running, end to
 * end (ADR-231, #496). The scoring run names the digest Ollama reported when it was minted (ADR-228), and
 * each chunk is stored under what Ollama reports when it is embedded (ADR-084), so the chunks after the pull
 * are not under the run's embedder identity. The embedding step is last to embed the seeds, so a late pull
 * leaves seeds on the far side of it, and scoring used to go on against the seeds that were left.
 *
 * <p><b>Two ways a seed loses its vector here.</b> Three tests script the runtime to report another digest
 * once it has embedded {@link #CHUNKS_BEFORE_THE_PULL} chunks ({@link
 * EmbeddingScriptedBeans#pullsAgainOnceItHasEmbedded}), which is the pull itself. The fourth moves one seed's
 * stored vectors under another digest's identity by hand and removes the row recording that scoring
 * finished, so that scoring runs again where the embedding step is recorded and makes no comparison: the way
 * into the same state that the embedding step's own check does not close.
 *
 * <p><b>A seed that never had a vector to lose is not here any more.</b> A fifth test held that a seed whose
 * only text is a page header is usable, is left out of the seeds scoring reads with a line, and is not
 * refused (ADR-231 section 2a). Since ADR-232 (#499) such a seed is an unusable seed, and {@link
 * AFileOfPageHeadersAndFootersOnlyInvocationTest} holds that. The leave-out and its line stay for a usable
 * seed with no chunk stored, a state no conversion makes any more, so the last test here makes it by hand.
 *
 * <p><b>Every file is written with its folder's name in it.</b> A vector is keyed by content and not by
 * walk, and the tests of every class sharing this context share one database, so a file with the bytes of
 * another test's would find that test's vectors already stored under the fixture's first digest and no seed
 * would be without one. Every vector not under the first digest is taken away after each test, as {@code
 * APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest} does.
 */
@CascadeSliceTest
@ExtendWith(OutputCaptureExtension.class)
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("496")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class APullWhileTheEmbeddingStepRunsInvocationTest {

    /** The digest the runtime reports once the embedding model has been pulled again. */
    private static final String A_SECOND_PULLS_DIGEST = "5ec0nd9a11000000000000000000000000000000000000000000000000000";

    private static final int CORPUS_DOCUMENTS = 2;

    private static final int SEED_DOCUMENTS = 2;

    /**
     * The corpus documents are embedded first and the seeds after them, and each of these short files is one
     * chunk: so after this many, both corpus documents and one seed, the one chunk left is the other seed's.
     */
    private static final int CHUNKS_BEFORE_THE_PULL = CORPUS_DOCUMENTS + 1;

    /** The words of the embedding step's stop that say what happened. */
    private static final String THE_STOP_SAYS = "was pulled again while its chunks were being embedded";

    /** A seed on each side of the pull. */
    private static final long ONE_SEED = 1;

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
    void theRuntimeReportsItsFirstDigest() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void onlyTheFirstDigestsVectorsAreLeft() {
        EmbeddingScriptedBeans.servesEveryModel();
        jdbcTemplate.update(
                "DELETE FROM vector WHERE embedder_identity NOT LIKE ?",
                "%;digest=" + EmbeddingScriptedBeans.DIGEST + ";dtype=" + EmbeddingScriptedBeans.DTYPE + ";%");
    }

    @Test
    @Story("The embedding model pulled again while documents are being embedded stops the work")
    @DisplayName("A pull that lands before the last seed document is embedded stops the invocation: embedding is not recorded as finished and no document is scored")
    void aPullPartWayThroughTheEmbeddingStepStopsItUnrecorded(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        EmbeddingScriptedBeans.pullsAgainOnceItHasEmbedded(CHUNKS_BEFORE_THE_PULL, A_SECOND_PULLS_DIGEST);

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim(
                "the pull landed between the " + SEED_DOCUMENTS + " seed documents: " + ONE_SEED + " has its"
                        + " vectors under the digest reported before it and " + ONE_SEED + " under the digest"
                        + " reported after, and both corpus documents were embedded before it",
                () -> {
                    assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(ONE_SEED);
                    assertThat(documentsWithVectorsUnder(seeds, A_SECOND_PULLS_DIGEST))
                            .isEqualTo(ONE_SEED);
                    assertThat(documentsWithVectorsUnder(root, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(CORPUS_DOCUMENTS);
                });
        claim(
                "the invocation fails: the model that finished the embedding is not the one the scoring was"
                        + " started for, and a seed document is missing from it",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and it says why: the embedding model was pulled again while the documents were being embedded",
                () -> assertThat(output.getAll()).contains(THE_STOP_SAYS));
        claim(
                "it was started as one scoring run, which names the digest reported before the pull",
                () -> assertThat(theRuns).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModelDigest\":\"" + EmbeddingScriptedBeans.DIGEST + "\"")));
        claim(
                "embedding is not recorded as finished under that run, so if the run is ever continued it"
                        + " embeds again and does not go on without the seed document",
                () -> assertThat(finishedRows(theRuns.getFirst(), StepNames.EMBEDDING_SCORING))
                        .isZero());
        claim(
                "and no document is scored under it: a score against " + ONE_SEED + " of " + SEED_DOCUMENTS
                        + " seed documents would look like any other score and mean something narrower",
                () -> assertThat(countUnder("relevance_score", theRuns.getFirst())).isZero());
    }

    /**
     * How many file occurrences found beneath {@code walked} have a vector stored under {@code digest}, read
     * through the key their conversion was recorded under, which is the key a vector is stored by.
     */
    private long documentsWithVectorsUnder(Path walked, String digest) {
        Long documents = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT k.occurrence_id) FROM extraction_cache_key k"
                        + " JOIN file_occurrence f ON f.id = k.occurrence_id JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? AND EXISTS (SELECT 1 FROM vector v"
                        + " WHERE v.content_hash = k.content_hash AND v.embedder_identity LIKE ?)",
                Long.class,
                walkRoot(walked),
                "%;digest=" + digest + ";%");
        return documents == null ? 0 : documents;
    }

    private long finishedRows(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows == null ? 0 : rows;
    }

    /** Two corpus documents and two seeds, each naming its folder so that no other test wrote the same bytes. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(
                    root.resolve("corpus-" + i + ".txt"), "a corpus document " + i + " of " + root.getFileName());
        }
        for (int i = 0; i < SEED_DOCUMENTS; i++) {
            Files.writeString(
                    seeds.resolve("seed-" + i + ".txt"), "a seed document " + i + " of " + seeds.getFileName());
        }
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and no threshold. */
    private void profile(Path seeds) {
        EveryGateOpen.throughStageFive(profileStore, seeds);
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

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }
}
