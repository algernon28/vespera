package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The topics analysis report's denominator, every seed passage, is counted as the breadth floor counts it: the chunks of
 * one seed of each content, the first by path in ADR-399 §4's order, a later seed of the same content adding nothing
 * (ADR-401 §1, ADR-416 §2 and §8).
 *
 * <p>The fixture is two paths of one content, {@value #A} and {@value #A_AGAIN}, each of {@value #THREE} chunks, and a
 * third of its own content, {@value #B}, of {@value #TWO}. Counted once a content, the seed passages are {@value #THREE}
 * and {@value #TWO}, five; counted once a path, they would be eight. The later path is put in the map first, so that the
 * count does not rest on the order the paths are handed in.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("834")
@Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
class TheSeedPassagesAreCountedOnceAContentTest {

    private static final String A = "a.md";

    /** A path sorting after {@value #A}, of its content. */
    private static final String A_AGAIN = "c.md";

    private static final String B = "b.md";

    /** The chunks of {@value #A}, and of {@value #A_AGAIN}, which shares its content. */
    private static final int THREE = 3;

    /** The chunks of {@value #B}. */
    private static final int TWO = 2;

    /** The seed passages counted once a content: {@value #A}'s three and {@value #B}'s two. */
    private static final long FIVE = THREE + TWO;

    @Test
    @Story("What feeds each topic is shown before the arrangement is approved")
    @DisplayName("A second seed document of one content adds no passage to the seed passages a topic's share is of")
    void aSecondPathOfOneContentAddsNothingToTheSeedPassages() {
        Map<String, SeedChunks> withTheCopy = new LinkedHashMap<>();
        withTheCopy.put(A_AGAIN, new SeedChunks("the content of a", THREE));
        withTheCopy.put(B, new SeedChunks("the content of b", TWO));
        withTheCopy.put(A, new SeedChunks("the content of a", THREE));
        Map<String, SeedChunks> withoutIt = new LinkedHashMap<>(withTheCopy);
        withoutIt.remove(A_AGAIN);

        claim("three seeds, two of them of one content, hold " + FIVE + " seed passages: the three chunks of the content"
                        + " the two share, counted once, and the two of the third, not the eight their chunks add up to",
                () -> assertThat(Topics.seedPassagesOf(withTheCopy)).isEqualTo(FIVE));
        claim("and the second seed of the shared content adds nothing: without it the count is the same",
                () -> assertThat(Topics.seedPassagesOf(withoutIt)).isEqualTo(Topics.seedPassagesOf(withTheCopy)));
    }
}
