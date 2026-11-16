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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A topic's strongest contributing seed, whose title stands in for a name the model's answer cannot give (ADR-403 §7):
 * the seed feeding the most of the topic's passages, two seeds feeding as many going in the visiting order of ADR-399
 * §4, which puts every passage of one seed together and compares the seeds' paths by code point.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("829")
@Link(name = "ADR-403", url = Adr.THE_GENERATION_MODEL_NAMES_EACH_TOPIC, type = "adr")
class TopicStrongestSeedTest {

    /** The most passages any seed of the first fixture feeds its topic. */
    private static final int THREE_PASSAGES = 3;

    /** U+FF5E, before {@link #PAST_THE_BASIC_PLANE} by code point and after it by UTF-16 code unit. */
    private static final String FULL_WIDTH = "～notes.md";

    /** U+1F4C4, past the basic plane. */
    private static final String PAST_THE_BASIC_PLANE = "📄notes.md";

    @Test
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("The strongest seed is the one feeding the topic the most passages, not the one its first passage is of")
    void theSeedFeedingTheMostPassagesIsTheStrongest() {
        SeedPassage first = new SeedPassage("a.md", 0);
        Topic topic = new Topic(
                new TopicKey(first),
                List.of(
                        first,
                        new SeedPassage("b.md", 0),
                        new SeedPassage("b.md", 1),
                        new SeedPassage("c.md", 0),
                        new SeedPassage("c.md", 1),
                        new SeedPassage("c.md", 2)));

        claim(
                "c.md, which feeds the topic " + THREE_PASSAGES + " passages, is its strongest seed, and not a.md, whose"
                        + " one passage is the topic's first, nor b.md, which feeds two",
                () -> assertThat(topic.strongestContributingSeed()).isEqualTo("c.md"));
    }

    @Test
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("Of two seeds feeding as many passages, the one whose path comes first by code point is the strongest")
    void aTieGoesToTheSeedWhosePathComesFirstByCodePoint() {
        SeedPassage first = new SeedPassage("a.md", 0);
        Topic topic = new Topic(
                new TopicKey(first),
                List.of(
                        first,
                        new SeedPassage(FULL_WIDTH, 0),
                        new SeedPassage(FULL_WIDTH, 1),
                        new SeedPassage(PAST_THE_BASIC_PLANE, 0),
                        new SeedPassage(PAST_THE_BASIC_PLANE, 1)));

        claim(
                "of the two seeds feeding two passages each, the full-width one is the strongest: its path comes first"
                        + " by code point, though the other's comes first by UTF-16 code unit",
                () -> assertThat(topic.strongestContributingSeed()).isEqualTo(FULL_WIDTH));
    }
}
