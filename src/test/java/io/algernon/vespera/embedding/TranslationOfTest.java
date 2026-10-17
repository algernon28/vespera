package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
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

    private List<Translations.Likeness> likenessesAmong(List<TranslationOf.Candidate> candidates) {
        return new TranslationOf(ClusteringBeans.real(jdbcTemplate), jdbcTemplate).likenessesAmong(
                candidates, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, ClusteringProgress.NONE);
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
