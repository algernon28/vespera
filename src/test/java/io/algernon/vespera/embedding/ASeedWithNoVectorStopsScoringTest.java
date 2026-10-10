package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
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
 * {@code RelevanceScoring.residentSeedVectors} refuses a seed with no vector under the embedder identity it
 * is handed, as {@code scoreAndRecord} refuses a survivor (ADR-231, #496). It is handed the usable seeds
 * that have a chunk: an unusable seed, and a usable one with nothing to embed, are never among them, so
 * neither is refused here.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("496")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
class ASeedWithNoVectorStopsScoringTest {

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String DTYPE = "F16";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";

    /** Every vector here has two components; what they are does not matter to this test. */
    private static final int DIMENSION = 2;

    private static final String READ_UNDER =
            EmbedderIdentity.withoutInstruction(MODEL, "the-digest-the-run-names", DTYPE, DIMENSION).value();

    private static final String A_LATER_PULLS_IDENTITY =
            EmbedderIdentity.withoutInstruction(MODEL, "the-digest-of-a-later-pull", DTYPE, DIMENSION).value();

    private static final OccurrenceId THE_SEED_EMBEDDED_BEFORE_THE_PULL = new OccurrenceId(4021);

    private static final OccurrenceId THE_SEED_EMBEDDED_AFTER_THE_PULL = new OccurrenceId(4022);

    private static final int BOTH_SEEDS = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Reading the seed documents' vectors stops on a seed document whose only vectors are of a later pull, and names it")
    void aSeedWithVectorsOnlyUnderAnotherPullIsRefused() {
        store("seed-before-the-pull", READ_UNDER);
        store("seed-after-the-pull", A_LATER_PULLS_IDENTITY);

        claim(
                "the read stops, naming seed occurrence " + THE_SEED_EMBEDDED_AFTER_THE_PULL.value() + ": it"
                        + " has a vector, but under the digest of a later pull, and leaving it out would"
                        + " score every document against one seed document where the seed folder holds "
                        + BOTH_SEEDS,
                () -> assertThatThrownBy(() -> scoring()
                                .residentSeedVectors(bothSeeds(), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("seed occurrence " + THE_SEED_EMBEDDED_AFTER_THE_PULL.value()));
    }

    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Where every seed document has a vector under the pull the scoring names, every one is read")
    void everySeedWithAVectorIsResident() {
        store("seed-before-the-pull", READ_UNDER);
        store("seed-after-the-pull", READ_UNDER);

        Map<OccurrenceId, List<float[]>> resident =
                scoring().residentSeedVectors(bothSeeds(), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);

        claim(
                "both of the " + BOTH_SEEDS + " seed documents are read, in the order they were handed over",
                () -> assertThat(resident.keySet())
                        .containsExactly(THE_SEED_EMBEDDED_BEFORE_THE_PULL, THE_SEED_EMBEDDED_AFTER_THE_PULL));
    }

    private static Map<OccurrenceId, String> bothSeeds() {
        Map<OccurrenceId, String> seeds = new LinkedHashMap<>();
        seeds.put(THE_SEED_EMBEDDED_BEFORE_THE_PULL, "seed-before-the-pull");
        seeds.put(THE_SEED_EMBEDDED_AFTER_THE_PULL, "seed-after-the-pull");
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
