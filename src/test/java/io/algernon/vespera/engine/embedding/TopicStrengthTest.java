package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which of a topic's passages a name is asked from first, and which seed a name falls back to (ADR-407 §4, §7).
 *
 * <p>A passage's strength is the cosine of its vector to the topic's centroid, the mean of its passages' vectors each
 * taken to unit length; the strongest is sent first, two of equal strength in the order of ADR-399 §4. The fixture is
 * six passages of three seeds, each a vector of three small whole numbers: by strength they are {@code c.md}'s chunk 0
 * (0.992), {@code b.md}'s chunks 0 and 1 (0.931, the same vector), {@code a.md}'s chunk 1 (0.903), its chunk 0 (0.669)
 * and {@code b.md}'s chunk 2 (0.619). A dot product with the vectors left at their own lengths orders them otherwise,
 * {@code b.md}'s two first and {@code c.md}'s third.
 *
 * <p>The strongest contributing seed is the seed with the most passages in the topic, two with as many being taken in
 * the order of ADR-399 §4, which compares paths by code point: an upper-case letter before every lower-case one.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("829")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TopicStrengthTest {

    private static final SeedPassage A0 = new SeedPassage("a.md", 0);
    private static final SeedPassage A1 = new SeedPassage("a.md", 1);
    private static final SeedPassage B0 = new SeedPassage("b.md", 0);
    private static final SeedPassage B1 = new SeedPassage("b.md", 1);
    private static final SeedPassage B2 = new SeedPassage("b.md", 2);
    private static final SeedPassage C0 = new SeedPassage("c.md", 0);

    /** The six passages in the visiting order, as the topic lists them. */
    private static final Topic TOPIC = new Topic(new TopicKey(A0), List.of(A0, A1, B0, B1, B2, C0));

    /** Each seed's content hash and its chunks' vectors in chunk order. */
    private static final Map<String, SeedVectors> VECTORS = Map.of(
            "a.md", new SeedVectors("hash-a", List.of(new float[] {4, 0, 1}, new float[] {1, 1, 0})),
            "b.md", new SeedVectors("hash-b", List.of(
                    new float[] {1, 3, 1}, new float[] {1, 3, 1}, new float[] {0, 1, 3})),
            "c.md", new SeedVectors("hash-c", List.of(new float[] {2, 2, 1})));

    @Test
    @Story("A name is asked from the passages most like the rest of the topic")
    @DisplayName("A topic's passages go strongest first, by closeness to the topic's centre, two alike in seed path then chunk order")
    void passagesGoByTheirCosineToTheCentroidOfTheTopic() {
        List<SeedPassage> strongestFirst = TOPIC.passagesStrongestFirst(VECTORS);

        claim("the six passages go c.md's chunk 0, b.md's chunks 0 and 1, whose vectors are the same and so go in"
                        + " chunk order, a.md's chunk 1, its chunk 0, then b.md's chunk 2: by the cosine of each to"
                        + " the mean of the six taken to unit length, not by a dot product with vectors of their own"
                        + " lengths, nor in the order they were visited",
                () -> assertThat(strongestFirst).containsExactly(C0, B0, B1, A1, A0, B2));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("The strongest contributing seed is the one with the most passages in the topic")
    void theStrongestContributingSeedHasTheMostPassages() {
        claim("of a.md's two passages, b.md's three and c.md's one, b.md contributes most, though c.md holds the"
                        + " strongest passage and a.md the first",
                () -> assertThat(TOPIC.strongestContributingSeed()).isEqualTo("b.md"));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("Two seeds with as many passages are taken in seed path order, an upper-case letter first")
    void twoSeedsWithAsManyPassagesAreTakenInSeedPathOrder() {
        SeedPassage lower = new SeedPassage("a.md", 0);
        SeedPassage upper = new SeedPassage("B.md", 0);
        Topic tied = new Topic(
                new TopicKey(upper), List.of(upper, new SeedPassage("B.md", 1), lower, new SeedPassage("a.md", 1)));

        claim("B.md and a.md feed two passages each, and B.md is taken: an upper-case B sorts before a lower-case a"
                        + " by code point, where an order folding case would take a.md",
                () -> assertThat(tied.strongestContributingSeed()).isEqualTo("B.md"));
    }
}
