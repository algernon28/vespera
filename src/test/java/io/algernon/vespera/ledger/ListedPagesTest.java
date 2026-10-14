package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ListedPages}, {@code ledger}'s one cut of a list of keys into the pages a statement asks (ADR-257):
 * the pages of at most {@link KeysetPages#ROWS_IN_A_PAGE} that every read by key hands to its statement, in the
 * order the keys were given, each key once, and none for an empty list. No database: the cut is all it does,
 * the statement staying the caller's.
 */
@Epic("Ledger")
@Feature("Long reads")
@Link(name = "ADR-257", url = Adr.ONE_CUT_OF_A_LIST_OF_KEYS_INTO_PAGES, type = "adr")
class ListedPagesTest {

    private static final int A_PAGE = KeysetPages.ROWS_IN_A_PAGE;

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("A list is handed over in pages of the ledger's size, in order, each key once, the last page short")
    void aListIsCutIntoPagesOfTheLedgersSize() {
        List<Integer> keys = IntStream.rangeClosed(1, 2 * A_PAGE + 5).boxed().toList();
        List<List<Integer>> pages = new ArrayList<>();

        ListedPages.each(keys, pages::add);

        claim(
                "two whole pages and a short one of five, as many pages as the statements the caller will make",
                () -> assertThat(pages.stream().map(List::size).toList()).containsExactly(A_PAGE, A_PAGE, 5));
        claim(
                "the keys come back in the order they were given, every one once",
                () -> assertThat(pages.stream().flatMap(List::stream).toList()).isEqualTo(keys));
    }

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("A list of exactly one page is one page, and one key more is two")
    void thePageBoundaryIsExact() {
        List<List<Integer>> whole = new ArrayList<>();
        List<List<Integer>> oneMore = new ArrayList<>();

        ListedPages.each(IntStream.range(0, A_PAGE).boxed().toList(), whole::add);
        ListedPages.each(IntStream.rangeClosed(0, A_PAGE).boxed().toList(), oneMore::add);

        claim(
                "a list of a page is a page",
                () -> assertThat(whole.stream().map(List::size).toList()).containsExactly(A_PAGE));
        claim(
                "a list of a page and one is a page and a page of one",
                () -> assertThat(oneMore.stream().map(List::size).toList()).containsExactly(A_PAGE, 1));
    }

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("An empty list is handed over as no page, so no statement is asked")
    void anEmptyListIsNoPage() {
        List<List<Integer>> pages = new ArrayList<>();

        ListedPages.each(List.<Integer>of(), pages::add);
        ListedPages.each(Set.<Integer>of(), pages::add);

        claim("no page is handed over for nothing", () -> assertThat(pages).isEmpty());
    }

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("A caller that names its own size is handed pages of that size, and a size of none is refused")
    void aCallerThatNamesItsSizeIsHandedPagesOfIt() {
        List<List<Integer>> pages = new ArrayList<>();

        ListedPages.each(List.of(1, 2, 3, 4, 5), 2, pages::add);

        claim(
                "pages of two, two and one",
                () -> assertThat(pages).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5)));
        claim(
                "a page of no keys would never end, so it is refused",
                () -> assertThatThrownBy(() -> ListedPages.each(List.of(1), 0, page -> {}))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("A list of keys is cut once")
    @DisplayName("A collection that is not a list is cut in the order it iterates, and the caller's collection is not held")
    void aCollectionIsCutInItsOwnOrderAndNotHeld() {
        List<Integer> given = new ArrayList<>(List.of(1, 2, 3));
        List<List<Integer>> pages = new ArrayList<>();

        ListedPages.each(given, page -> {
            pages.add(page);
            given.clear();
        });

        claim(
                "the page handed over is the keys as they stood when the cut began, whatever the caller does to"
                        + " its collection while it goes through the pages",
                () -> assertThat(pages).containsExactly(List.of(1, 2, 3)));
    }
}
