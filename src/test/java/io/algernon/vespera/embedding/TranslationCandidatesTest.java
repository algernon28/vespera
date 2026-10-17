package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The likenesses across languages (ADR-271 §3): two candidates of different detected languages, each the
 * other's nearest by the cosine of their mean vectors among the candidates of its own language, the vectors
 * read in blocks as {@link NearestNeighbourGraph} reads them.
 *
 * <p>Candidates are named by their index in the order the source hands them over, which is occurrence-id
 * order, and a likeness names the lower index first.
 */
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class TranslationCandidatesTest {

    /** Larger than every fixture below but the last, so a block boundary falls only where a test puts one. */
    private static final int ONE_BLOCK = 64;

    /** How far a cosine computed here may lie from the one the pass computes, in floats or in doubles. */
    private static final double ROUNDING = 1e-6;

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Each is paired with its nearest in the other language where the nearest is nearest back")
    void eachIsALikenessOfItsNearestInTheOtherLanguageWhereThatIsMutual() {
        InMemory vectors = new InMemory(
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}),
                new TranslationCandidates.Vector("CHINESE", new float[] {0.9f, 0.1f}),
                new TranslationCandidates.Vector("ENGLISH", new float[] {0f, 1f}),
                new TranslationCandidates.Vector("CHINESE", new float[] {0.1f, 0.9f}));

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
                "each carries the cosine of its two mean vectors, 0.9 over the square root of 0.82 for both",
                () -> assertThat(likenesses)
                        .allSatisfy(likeness -> assertThat(likeness.similarity())
                                .isCloseTo(cosine(new float[] {1f, 0f}, new float[] {0.9f, 0.1f}), within(ROUNDING))));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Two texts detected in one language are never paired, however alike")
    void twoOfOneLanguageAreNeverALikeness() {
        InMemory vectors = new InMemory(
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}),
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}));

        claim(
                "two identical vectors in one language are no likeness: copies in one language are for the"
                        + " lexical redundancy check, not for this one",
                () -> assertThat(TranslationCandidates.mutualNearest(vectors, ONE_BLOCK)).isEmpty());
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A text whose language was not detected is never paired")
    void oneWithNoLanguageIsNeverALikeness() {
        InMemory vectors = new InMemory(
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}),
                new TranslationCandidates.Vector(null, new float[] {1f, 0f}));

        claim(
                "a text with no detected language cannot be said to be in another language, so it is no"
                        + " likeness of anything",
                () -> assertThat(TranslationCandidates.mutualNearest(vectors, ONE_BLOCK)).isEmpty());
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("A text whose nearest in the other language is nearer to another is not paired")
    void aNearestThatIsNotMutualIsNoLikeness() {
        InMemory vectors = new InMemory(
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}),
                new TranslationCandidates.Vector("ENGLISH", new float[] {0.8f, 0.6f}),
                new TranslationCandidates.Vector("CHINESE", new float[] {1f, 0f}));

        List<TranslationCandidates.Likeness> likenesses = TranslationCandidates.mutualNearest(vectors, ONE_BLOCK);

        claim(
                "the Chinese text is nearest the first English one and that one nearest it, a likeness; the"
                        + " second English text's nearest Chinese one is nearer the first, so it has none",
                () -> assertThat(likenesses)
                        .extracting(TranslationCandidates.Likeness::first, TranslationCandidates.Likeness::second)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple(0, 2)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Each pair of languages is asked on its own")
    void everyPairOfLanguagesIsAskedApart() {
        InMemory vectors = new InMemory(
                new TranslationCandidates.Vector("ENGLISH", new float[] {1f, 0f}),
                new TranslationCandidates.Vector("CHINESE", new float[] {1f, 0f}),
                new TranslationCandidates.Vector("ITALIAN", new float[] {1f, 0f}));

        claim(
                "one text in each of three languages, all alike, gives a likeness for each of the three pairs"
                        + " of languages",
                () -> assertThat(TranslationCandidates.mutualNearest(vectors, ONE_BLOCK))
                        .extracting(TranslationCandidates.Likeness::first, TranslationCandidates.Likeness::second)
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple(0, 1),
                                org.assertj.core.groups.Tuple.tuple(0, 2),
                                org.assertj.core.groups.Tuple.tuple(1, 2)));
    }

    @Test
    @Story("Two copies in different languages are found by their vectors")
    @DisplayName("Reading the vectors one at a time, seven at a time or all at once finds the same pairs")
    void theBlockSizeChangesNothing() {
        int candidates = 60;
        List<TranslationCandidates.Vector> scattered = new ArrayList<>();
        Random random = new Random(271);
        List<String> languages = List.of("ENGLISH", "CHINESE", "ITALIAN");
        for (int i = 0; i < candidates; i++) {
            float[] mean = new float[8];
            for (int component = 0; component < mean.length; component++) {
                mean[component] = random.nextFloat() - 0.5f;
            }
            scattered.add(new TranslationCandidates.Vector(languages.get(i % languages.size()), mean));
        }
        InMemory vectors = new InMemory(scattered.toArray(TranslationCandidates.Vector[]::new));

        List<TranslationCandidates.Likeness> inOneBlock = TranslationCandidates.mutualNearest(vectors, ONE_BLOCK + 1);

        claim(
                "the " + candidates + " vectors in three languages give some likeness, so the comparison below"
                        + " compares something",
                () -> assertThat(inOneBlock).isNotEmpty());
        claim(
                "read one vector at a time, seven at a time or all at once, the likenesses are the same, so"
                        + " what is found is a property of the vectors and not of how they were read",
                () -> assertThat(List.of(
                                TranslationCandidates.mutualNearest(vectors, 1),
                                TranslationCandidates.mutualNearest(vectors, 7)))
                        .allSatisfy(found -> assertThat(found)
                                .extracting(TranslationCandidates.Likeness::first, TranslationCandidates.Likeness::second)
                                .containsExactlyElementsOf(inOneBlock.stream()
                                        .map(likeness -> org.assertj.core.groups.Tuple.tuple(
                                                likeness.first(), likeness.second()))
                                        .toList())));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double aa = 0;
        double bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            aa += (double) a[i] * a[i];
            bb += (double) b[i] * b[i];
        }
        return dot / Math.sqrt(aa * bb);
    }

    /** Candidates held in a list, handed over in blocks as the stored ones are. */
    private record InMemory(List<TranslationCandidates.Vector> all) implements TranslationCandidates.Vectors {

        InMemory(TranslationCandidates.Vector... vectors) {
            this(List.of(vectors));
        }

        @Override
        public int count() {
            return all.size();
        }

        @Override
        public List<TranslationCandidates.Vector> block(int from, int size) {
            return all.subList(from, Math.min(from + size, all.size()));
        }
    }
}
