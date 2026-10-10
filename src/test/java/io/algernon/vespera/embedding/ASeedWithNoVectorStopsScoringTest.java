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
import java.util.ArrayList;
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

    private static final OccurrenceId THE_FIRST_SEED = new OccurrenceId(4021);

    private static final OccurrenceId THE_SECOND_SEED = new OccurrenceId(4022);

    private static final int BOTH_SEEDS = 2;

    /** A seed whose text was cut into one chunk, which is one thing to embed. */
    private static final int ONE_CHUNK = 1;

    /** A seed whose text the chunker left out whole, so there is nothing of it to embed. */
    private static final int NO_CHUNK = 0;

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

    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Reading the seed documents' vectors stops on a seed document that has text to embed and no vector at all")
    void aSeedWithAChunkAndNoVectorIsRefused() {
        store("the-first-seed", READ_UNDER);

        claim(
                "the read stops and gives the number of the second seed document, "
                        + THE_SECOND_SEED.value() + ": it has " + ONE_CHUNK + " piece of text to embed and no"
                        + " vector was ever stored for it",
                () -> assertThatThrownBy(() -> scoring()
                                .residentSeedVectors(
                                        seeds(ONE_CHUNK, ONE_CHUNK), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("seed occurrence " + THE_SECOND_SEED.value()));
    }

    @Test
    @Story("A seed document with nothing to embed is left out and the scoring goes on")
    @DisplayName("A seed document with no piece of text to embed and no vector is left out, the caller is told which, and the other is read")
    void aSeedWithNoChunkIsLeftOutAndReported() {
        store("the-first-seed", READ_UNDER);
        List<String> told = new ArrayList<>();

        Map<OccurrenceId, List<float[]>> resident = scoring()
                .residentSeedVectors(
                        seeds(ONE_CHUNK, NO_CHUNK), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER, recording(told));

        claim(
                "only the first seed document is read: the second has nothing to embed, which is a fact about"
                        + " that document and not a vector gone missing, so the read does not stop on it",
                () -> assertThat(resident.keySet()).containsExactly(THE_FIRST_SEED));
        claim(
                "the caller is told the total of " + BOTH_SEEDS + " once, that the first was read, that the"
                        + " second, number " + THE_SECOND_SEED.value() + ", was left out, and that it too was"
                        + " gone through, so the count of seed documents ends at its total",
                () -> assertThat(told)
                        .containsExactly(
                                "to-read " + BOTH_SEEDS, "read", "left-out " + THE_SECOND_SEED.value(), "read"));
    }

    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Where every seed document has a vector under the pull the scoring names, every one is read")
    void everySeedWithAVectorIsResident() {
        store("the-first-seed", READ_UNDER);
        store("the-second-seed", READ_UNDER);

        Map<OccurrenceId, List<float[]>> resident = scoring()
                .residentSeedVectors(seeds(ONE_CHUNK, ONE_CHUNK), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);

        claim(
                "both of the " + BOTH_SEEDS + " seed documents are read, in the order they were handed over",
                () -> assertThat(resident.keySet()).containsExactly(THE_FIRST_SEED, THE_SECOND_SEED));
    }

    /** The two seeds, in order, each with its content hash and the number of chunks given for it. */
    private static Map<OccurrenceId, SeedChunks> seeds(int chunksOfTheFirst, int chunksOfTheSecond) {
        Map<OccurrenceId, SeedChunks> seeds = new LinkedHashMap<>();
        seeds.put(THE_FIRST_SEED, new SeedChunks("the-first-seed", chunksOfTheFirst));
        seeds.put(THE_SECOND_SEED, new SeedChunks("the-second-seed", chunksOfTheSecond));
        return seeds;
    }

    private static ScoringProgress recording(List<String> told) {
        return new ScoringProgress() {
            @Override
            public void toReadSeedVectors(long seeds) {
                told.add("to-read " + seeds);
            }

            @Override
            public void seedVectorsRead() {
                told.add("read");
            }

            @Override
            public void seedLeftOutWithNoChunk(OccurrenceId seed) {
                told.add("left-out " + seed.value());
            }
        };
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
