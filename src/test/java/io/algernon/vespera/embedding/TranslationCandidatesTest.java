package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The likenesses across languages (ADR-271 §3): two candidates of different detected languages, each the
 * other's nearest by the cosine of their mean vectors among the candidates of its own language, the vectors
 * read in blocks as {@link NearestNeighbourGraph} reads them.
 *
 * <p>Candidates are named by their index in the order the source hands them over, which is occurrence-id
 * order, and a likeness names the lower index first. A candidate's language is asked of the source alone, and its
 * mean vector is read only by the comparison, an empty one being no vector (ADR-273 §1).
 */
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class TranslationCandidatesTest {

    /** Larger than every fixture below, so a block boundary falls only where a test puts one. */
    private static final int ONE_BLOCK = 64;

    /** How far the cosine the pass computes, in floats or in doubles, may lie from one worked by hand. */
    private static final double ROUNDING = 1e-6;

    /** The cosine of (1, 0) and (0.9, 0.1), worked by hand: 0.9 over the square root of 0.82. */
    private static final double NINE_TENTHS_OVER_THE_ROOT_OF_0_82 = 0.99388373467;

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Each is paired with its nearest in the other language where the nearest is nearest back")
    void eachIsALikenessOfItsNearestInTheOtherLanguageWhereThatIsMutual() {
        InMemory vectors = new InMemory(
                new Candidate("ENGLISH", new float[] {1f, 0f}),
                new Candidate("CHINESE", new float[] {0.9f, 0.1f}),
                new Candidate("ENGLISH", new float[] {0f, 1f}),
                new Candidate("CHINESE", new float[] {0.1f, 0.9f}));

        List<TranslationCandidates.Likeness> likenesses = TranslationCandidates.mutualNearest(vectors, ONE_BLOCK);

        claim(
                "the first English text and the first Chinese one are each other's nearest across the two"
                        + " languages, and so are the second of each: two likenesses, lower index first",
                () -> assertThat(likenesses)
                        .extracting(TranslationCandidates.Likeness::first, TranslationCandidates.Likeness::second)
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple(0, 1),
                                org.assertj.core.groups.Tuple.tuple(2, 3)));
        claim(
                "each carries the cosine of its two mean vectors, 0.9 over the square root of 0.82 for both,"
                        + " about 0.99388",
                () -> assertThat(likenesses)
                        .allSatisfy(likeness -> assertThat(likeness.similarity())
                                .isCloseTo(NINE_TENTHS_OVER_THE_ROOT_OF_0_82, within(ROUNDING))));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Two texts detected in one language are never paired, however alike")
    void twoOfOneLanguageAreNeverALikeness() {
        InMemory vectors = new InMemory(
                new Candidate("ENGLISH", new float[] {1f, 0f}),
                new Candidate("ENGLISH", new float[] {1f, 0f}));

        claim(
                "two identical vectors in one language are no likeness: copies in one language are for the"
                        + " lexical redundancy check, not for this one",
                () -> assertThat(TranslationCandidates.mutualNearest(vectors, ONE_BLOCK)).isEmpty());
    }

    /** A candidate as a test writes it: the language stage 2 stored for it, or none, and its mean vector. */
    private record Candidate(String language, float[] mean) {}

    /**
     * Candidates held in a list, each language handed over alone and the means in blocks, as the stored ones are.
     */
    private record InMemory(List<Candidate> all) implements TranslationCandidates.Vectors {

        InMemory(Candidate... candidates) {
            this(List.of(candidates));
        }

        @Override
        public int count() {
            return all.size();
        }

        @Override
        public String language(int index) {
            return all.get(index).language();
        }

        @Override
        public List<float[]> block(int from, int size) {
            return all.subList(from, Math.min(from + size, all.size())).stream()
                    .map(Candidate::mean)
                    .toList();
        }
    }
}
