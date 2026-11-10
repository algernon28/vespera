package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
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
}
