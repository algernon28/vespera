package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The likenesses the translation step is handed, found from the chunk vectors stored in the vector cache
 * (ADR-271 §3): each candidate's mean vector computed as clustering computes it (ADR-087), the likenesses found
 * among them by {@link TranslationCandidates}, and each mapped back to the occurrences it pairs.
 *
 * <p>The vector table is keyed by content and not by occurrence, so the candidates need no occurrence rows: a
 * candidate is an occurrence id, the content hash its vectors are stored under, and the language stage 2 stored.
 * Each candidate's mean vector is computed by the comparison alone (ADR-273 §1).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class TranslationOfTest {

    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";

    /** The identity this test's vectors are stored and read under: four components, as the vectors have. */
    private static final String EMBEDDER_IDENTITY =
            "model=translation-of-test:0.6b;digest=d34db33f;dtype=F16;dimension=4;instruction=none";

    /** How far a cosine computed here may lie from the one the pass computes, in floats or in doubles. */
    private static final double ROUNDING = 1e-6;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Each likeness found among stored vectors names the two occurrences it pairs, from their mean vectors")
    void eachLikenessNamesTheOccurrencesItPairs() {
        // The Chinese copy of a has two chunks, whose mean is (0.9, 0.1, 0, 0): its mean, not its first chunk, is
        // compared.
        store("en-a", new float[] {1f, 0f, 0f, 0f});
        store("zh-a", new float[] {1f, 0.2f, 0f, 0f}, new float[] {0.8f, 0f, 0f, 0f});
        store("en-b", new float[] {0f, 0f, 1f, 0f});
        store("zh-b", new float[] {0f, 0f, 0.9f, 0.1f});
        List<TranslationOf.Candidate> candidates = List.of(
                candidate(101, "en-a", "ENGLISH"),
                candidate(102, "zh-a", "CHINESE"),
                candidate(103, "en-b", "ENGLISH"),
                candidate(104, "zh-b", "CHINESE"));

        List<Translations.Likeness> likenesses = likenessesAmong(candidates);

        claim(
                "the English and Chinese copies of a are each other's nearest across the two languages, and so"
                        + " are those of b: two likenesses, naming occurrences 101 and 102, then 103 and 104",
                () -> assertThat(likenesses)
                        .extracting(likeness -> likeness.first().value(), likeness -> likeness.second().value())
                        .containsExactly(Tuple.tuple(101L, 102L), Tuple.tuple(103L, 104L)));
        claim(
                "and each carries the cosine of the two mean vectors, 0.9 over the square root of 0.82, which the"
                        + " Chinese copy of a reaches only through the mean of its two chunks",
                () -> assertThat(likenesses).allSatisfy(likeness -> assertThat(likeness.similarity())
                        .isCloseTo(0.9 / Math.sqrt(0.82), within(ROUNDING))));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A survivor with no stored vector is no candidate")
    void aSurvivorWithNoStoredVectorIsNoCandidate() {
        store("en-c", new float[] {1f, 0f, 0f, 0f});
        store("zh-c", new float[] {0f, 1f, 0f, 0f});
        List<TranslationOf.Candidate> candidates = List.of(
                candidate(201, "en-c", "ENGLISH"),
                candidate(202, "unembedded", "CHINESE"),
                candidate(203, "zh-c", "CHINESE"));

        List<Translations.Likeness> likenesses = likenessesAmong(candidates);

        claim(
                "the Chinese survivor with no vector under the identity is paired with nothing, so the English one's"
                        + " nearest Chinese candidate is the one with a vector: one likeness, 201 with 203",
                () -> assertThat(likenesses)
                        .extracting(likeness -> likeness.first().value(), likeness -> likeness.second().value())
                        .containsExactly(Tuple.tuple(201L, 203L)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("With no vector stored for any survivor, there is no likeness")
    void withNoVectorAtAllThereIsNoLikeness() {
        List<TranslationOf.Candidate> candidates = List.of(
                candidate(301, "none-en", "ENGLISH"),
                candidate(302, "none-zh", "CHINESE"));

        claim(
                "no survivor has a mean vector, so nothing is compared and nothing is found",
                () -> assertThat(likenessesAmong(candidates)).isEmpty());
    }

    /**
     * Each candidate's mean vector is computed by the comparison and by nothing else (ADR-273 §1): not to learn its
     * language, which the candidate carries, and not to learn how wide a vector is, which one read of the vector
     * cache gives. Three candidates are one block at four components, so the comparison reads each mean once.
     */
    @Test
    @Story("Each mean vector is read only to be compared")
    @DisplayName("Each survivor's mean vector is computed once, by the comparison, the one with no vector first among them")
    @Issue("548")
    @Link(name = "ADR-273", url = Adr.THE_TRANSLATION_STEP_READS_A_MEAN_VECTOR_ONLY_TO_COMPARE_IT_AND_THE_PROFILE_ONCE, type = "adr")
    void eachMeanVectorIsComputedOnceAndOnlyByTheComparison() {
        store("en-d", new float[] {1f, 0f, 0f, 0f});
        store("zh-d", new float[] {0.9f, 0.1f, 0f, 0f});
        List<TranslationOf.Candidate> candidates = List.of(
                candidate(401, "unembedded-d", "CHINESE"),
                candidate(402, "en-d", "ENGLISH"),
                candidate(403, "zh-d", "CHINESE"));
        Map<String, Integer> computed = new HashMap<>();

        likenessesAmong(candidates, counting(computed));

        claim(
                "the mean vector of each of the three survivors, the one with no vector stored among them, was"
                        + " computed exactly once, as the comparison read the one block they make: none was computed"
                        + " for its language, and none to learn how wide a vector is",
                () -> assertThat(computed)
                        .containsExactlyInAnyOrderEntriesOf(Map.of("unembedded-d", 1, "en-d", 1, "zh-d", 1)));
    }

    /**
     * How wide a vector is comes from one read of the vector cache under the step's identities (ADR-273 §1), not from
     * the candidates' mean vectors: where none is stored under them, nothing is compared and no mean is computed.
     */
    @Test
    @Story("Each mean vector is read only to be compared")
    @DisplayName("With no vector stored under the embedder, no survivor's mean vector is computed")
    @Issue("548")
    @Link(name = "ADR-273", url = Adr.THE_TRANSLATION_STEP_READS_A_MEAN_VECTOR_ONLY_TO_COMPARE_IT_AND_THE_PROFILE_ONCE, type = "adr")
    void withNoVectorStoredNoMeanVectorIsComputed() {
        List<TranslationOf.Candidate> candidates = List.of(
                candidate(501, "none-en", "ENGLISH"),
                candidate(502, "none-zh", "CHINESE"));
        Map<String, Integer> computed = new HashMap<>();

        likenessesAmong(candidates, counting(computed));

        claim(
                "no vector is stored under the identities the step reads, so no survivor's mean vector was"
                        + " computed, not even to find how wide a vector would be",
                () -> assertThat(computed).isEmpty());
    }

    private List<Translations.Likeness> likenessesAmong(List<TranslationOf.Candidate> candidates) {
        return likenessesAmong(candidates, ClusteringBeans.real(jdbcTemplate));
    }

    private List<Translations.Likeness> likenessesAmong(List<TranslationOf.Candidate> candidates, Clustering clustering) {
        return new TranslationOf(clustering, jdbcTemplate).likenessesAmong(
                candidates, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, ClusteringProgress.NONE);
    }

    /** Real clustering over this test's tables, counting into {@code computed} each content hash a mean is computed for. */
    private Clustering counting(Map<String, Integer> computed) {
        return new Clustering(
                new VectorCache(jdbcTemplate), new RelevanceScoreCache(jdbcTemplate), new DocumentClusters(jdbcTemplate)) {
            @Override
            float[] meanVectorOf(
                    String contentHash, String chunkerIdentity, String chunkingRuleIdentity, String embedderIdentity) {
                computed.merge(contentHash, 1, Integer::sum);
                return super.meanVectorOf(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
            }
        };
    }

    private static TranslationOf.Candidate candidate(long id, String contentHash, String language) {
        return new TranslationOf.Candidate(new OccurrenceId(id), contentHash, language);
    }

    /** Stores {@code chunks} as the content's chunk vectors, ordinal by ordinal, under the test's identity. */
    private void store(String contentHash, float[]... chunks) {
        VectorCache vectors = new VectorCache(jdbcTemplate);
        for (int ordinal = 0; ordinal < chunks.length; ordinal++) {
            vectors.put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, ordinal, EMBEDDER_IDENTITY, chunks[ordinal]);
        }
    }
}
