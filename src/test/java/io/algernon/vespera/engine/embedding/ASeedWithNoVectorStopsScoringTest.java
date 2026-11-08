package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Which usable seeds {@code RelevanceScoring.residentSeedVectors} reads, leaves out and refuses (ADR-231,
 * #496). It is handed each usable seed's content hash and how many chunks it has ({@code SeedChunks}). A seed
 * with a chunk and no vector under the embedder identity is refused, as {@code scoreAndRecord} refuses a
 * survivor (section 2). A seed with no chunk has nothing to embed: it is left out, and the caller is told
 * (section 2a). The rule is here and not in {@code pipeline}, which looks the two values up and decides
 * nothing by them (ADR-222, ADR-226).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("496")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
class ASeedWithNoVectorStopsScoringTest {

    /** The number of similarities the application ships (vespera.relevance.top-similarities). */
    private static final int TOP_SIMILARITIES = 3;

    private static final String MODEL = "a-comparing-model:0.6b";
    private static final String DTYPE = "F16";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";

    /** Every vector here has two components; what they are does not matter to this test. */
    private static final int DIMENSION = 2;

    private static final String READ_UNDER =
            EmbedderIdentity.withoutInstruction(MODEL, "the-digest-the-run-names", DTYPE, DIMENSION).value();

    private static final String A_LATER_PULLS_IDENTITY =
            EmbedderIdentity.withoutInstruction(MODEL, "the-digest-of-a-later-pull", DTYPE, DIMENSION).value();

    private static final OccurrenceId THE_FIRST_SEED = new OccurrenceId(4021);

    private static final OccurrenceId THE_SECOND_SEED = new OccurrenceId(4022);

    private static final int BOTH_SEEDS = 2;

    /** A document whose text was cut into one chunk, which is one thing to embed: each seed and the corpus document here. */
    private static final int ONE_CHUNK = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Reading the seed documents' vectors stops on a seed document whose only vectors are of a later pull, and gives its number")
    void aSeedWithVectorsOnlyUnderAnotherPullIsRefused() {
        store("the-first-seed", READ_UNDER);
        store("the-second-seed", A_LATER_PULLS_IDENTITY);

        claim(
                "the read stops and gives the number of the second seed document, "
                        + THE_SECOND_SEED.value() + ": it has " + ONE_CHUNK + " piece of text to embed and a"
                        + " vector for it, but under the digest of a later pull, and leaving it out would"
                        + " score every document against one seed document where the seed folder holds "
                        + BOTH_SEEDS,
                () -> assertThatThrownBy(() -> scoring()
                                .residentSeedVectors(
                                        seeds(ONE_CHUNK, ONE_CHUNK), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("seed occurrence " + THE_SECOND_SEED.value()));
    }

    /** A seed document, or a corpus document, whose text was cut into two chunks. */
    private static final int TWO_CHUNKS = 2;

    /**
     * A seed is refused when it has a vector under the run's identity for some of its chunks only (ADR-231
     * sections 3 and 5): scored on the chunks that are left, it would measure every survivor against a part
     * of itself, with no line.
     */
    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Reading the seed documents' vectors stops on a seed document with two pieces of text to embed and a vector for only one of them")
    void aSeedWithAVectorForOnlySomeOfItsChunksIsRefused() {
        store("the-first-seed", READ_UNDER);
        store("the-second-seed", READ_UNDER);
        new VectorCache(jdbcTemplate)
                .put("the-second-seed", CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 1, A_LATER_PULLS_IDENTITY,
                        new float[] {0f, 1f});

        claim(
                "the read stops and gives the number of the second seed document, "
                        + THE_SECOND_SEED.value() + ": it has " + TWO_CHUNKS + " pieces of text to embed, and"
                        + " the vector of the second is under the digest of another pull, so read under the"
                        + " scoring's own it would stand for half of itself -- the refusal says it has a vector"
                        + " for only " + ONE_CHUNK + " of its " + TWO_CHUNKS + " chunks",
                () -> assertThatThrownBy(() -> scoring()
                                .residentSeedVectors(
                                        seeds(ONE_CHUNK, TWO_CHUNKS), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("seed occurrence " + THE_SECOND_SEED.value()
                                + " has stored chunk vectors for only " + ONE_CHUNK + " of its " + TWO_CHUNKS
                                + " chunks"));
    }

    /** A corpus document with a vector under the run's identity for the first of its two chunks only. */
    private static final OccurrenceId A_PARTLY_EMBEDDED_DOCUMENT = new OccurrenceId(4024);

    /** The survivor's side of the refusal above. */
    @Test
    @Story("A document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Scoring a corpus document with two pieces of text to embed and a vector for only one of them stops and names it")
    void aSurvivorWithAVectorForOnlySomeOfItsChunksIsRefused() {
        store("a-partly-embedded-document", READ_UNDER);
        Map<OccurrenceId, List<float[]>> oneResidentSeed = Map.of(THE_FIRST_SEED, List.of(new float[] {1f, 0f}));

        claim(
                "the scoring stops on document " + A_PARTLY_EMBEDDED_DOCUMENT.value() + " rather than score it"
                        + " on the one chunk it has a vector for: the refusal says it has a vector for only "
                        + ONE_CHUNK + " of its " + TWO_CHUNKS + " chunks",
                () -> assertThatThrownBy(() -> scoring()
                                .scoreAndRecord(
                                        A_PARTLY_EMBEDDED_DOCUMENT,
                                        new io.algernon.vespera.engine.ledger.RunId("a-scoring-run"),
                                        "a-partly-embedded-document",
                                        TWO_CHUNKS,
                                        CHUNKER_IDENTITY,
                                        CHUNKING_RULE_IDENTITY,
                                        READ_UNDER,
                                        oneResidentSeed, TOP_SIMILARITIES))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("occurrence " + A_PARTLY_EMBEDDED_DOCUMENT.value()
                                + " has stored chunk vectors for only " + ONE_CHUNK + " of its " + TWO_CHUNKS
                                + " chunks"));
    }

    /** A corpus document that has no vector stored under any identity. */
    private static final OccurrenceId A_CORPUS_DOCUMENT = new OccurrenceId(4023);

    /**
     * The survivor's side of the same refusal, and its words since ADR-232 section 4 (#499): it no longer
     * calls a survivor with no chunk impossible on the strength of #108, which was wrong about a file of page
     * headers and footers, and says what now keeps such a file from reaching it.
     */
    @Test
    @Issue("499")
    @Link(name = "ADR-232", url = Adr.A_FILE_WHOSE_ONLY_TEXT_IS_IN_PAGE_HEADERS_AND_FOOTERS_IS_DEGENERATE_OUTPUT_AND_SUCH_A_SEED_IS_UNUSABLE, type = "adr")
    @Story("A document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Scoring a corpus document with no stored vector stops, names the model's identity it looked under, and says why such a document should not exist")
    void aSurvivorWithNoVectorIsRefusedInWordsThatAreTrue() {
        claim(
                "the scoring stops on document " + A_CORPUS_DOCUMENT.value() + ", names the identity it looked"
                        + " for vectors under, says that a file with no text outside page headers and footers"
                        + " is removed before this, and no longer calls the case impossible by construction",
                () -> assertThatThrownBy(() -> scoring()
                                .scoreAndRecord(
                                        A_CORPUS_DOCUMENT,
                                        new io.algernon.vespera.engine.ledger.RunId("a-scoring-run"),
                                        "a-corpus-document",
                                        ONE_CHUNK,
                                        CHUNKER_IDENTITY,
                                        CHUNKING_RULE_IDENTITY,
                                        READ_UNDER,
                                        Map.of(), TOP_SIMILARITIES))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("occurrence " + A_CORPUS_DOCUMENT.value())
                        .hasMessageContaining("under embedder identity " + READ_UNDER)
                        .hasMessageContaining(
                                "stage 2 removes a file with no text outside page headers and footers")
                        .satisfies(refusal ->
                                assertThat(refusal.getMessage()).doesNotContain("impossible by construction")));
    }

    /** The two seeds, in order, each with its content hash and the number of chunks given for it. */
    private static Map<OccurrenceId, SeedChunks> seeds(int chunksOfTheFirst, int chunksOfTheSecond) {
        Map<OccurrenceId, SeedChunks> seeds = new LinkedHashMap<>();
        seeds.put(THE_FIRST_SEED, new SeedChunks("the-first-seed", chunksOfTheFirst));
        seeds.put(THE_SECOND_SEED, new SeedChunks("the-second-seed", chunksOfTheSecond));
        return seeds;
    }

    /** One chunk's vector for {@code contentHash} under {@code embedderIdentity}. */
    private void store(String contentHash, String embedderIdentity) {
        new VectorCache(jdbcTemplate)
                .put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, embedderIdentity, new float[] {1f, 0f});
    }

    private RelevanceScoring scoring() {
        return new RelevanceScoring(
                new VectorCache(jdbcTemplate), new RelevanceScorer(), new RelevanceScoreCache(jdbcTemplate));
    }
}
