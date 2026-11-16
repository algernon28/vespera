package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The order the topics are found in (ADR-399, ADR-396 §9): a seed's path relative to the seed folder, as the walk
 * stores it (ADR-051: {@code /} between its names, no case folded, nothing normalised), compared whole by code point,
 * which is the order of its UTF-8 bytes; then the chunk's index, as a number.
 *
 * <p>Each pair below is one that another comparison puts the other way round, so each claim fails for one way of
 * getting the order wrong: folding case, comparing a path's names one at a time, comparing UTF-16 code units as {@link
 * String#compareTo} does, or comparing a chunk's index as text.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("826")
@Link(name = "ADR-399", url = Adr.TOPICS_ARE_COMMUNITIES_OF_THE_SEED_PASSAGES, type = "adr")
class SeedPassageTest {

    @Test
    @Story("The same seeds give the same topics")
    @DisplayName("Passages are in the order of their seed's path by code point, case kept, then of their chunk's number")
    void ordersBySeedPathByCodePointThenByChunkIndex() {
        claim("an upper-case Z comes before a lower-case a, so no case is folded",
                () -> assertThat(new SeedPassage("Zeta.md", 0)).isLessThan(new SeedPassage("alpha.md", 0)));
        claim("a hyphen comes before a slash, so the path is compared whole and not one folder's name at a time",
                () -> assertThat(new SeedPassage("alpha-two.md", 0)).isLessThan(new SeedPassage("alpha/notes.md", 0)));
        claim("U+FF5E comes before U+1F4C4, which UTF-16 writes with a lower first code unit, so code points are"
                        + " compared and not code units",
                () -> assertThat(new SeedPassage("\uFF5E.md", 0)).isLessThan(new SeedPassage("\uD83D\uDCC4.md", 0)));
        claim("within one seed, chunk 2 comes before chunk 10, so the index is compared as a number",
                () -> assertThat(new SeedPassage("a.md", 2)).isLessThan(new SeedPassage("a.md", 10)));
        claim("and every chunk of a seed comes before every chunk of a seed whose path sorts after it",
                () -> assertThat(new SeedPassage("a.md", 10)).isLessThan(new SeedPassage("b.md", 0)));
    }
}
