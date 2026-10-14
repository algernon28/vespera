package io.algernon.vespera.ledger;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * A list of keys cut into the pages a statement asks (ADR-257).
 *
 * <p>The one place a read that asks a list of keys with {@code IN} cuts the list, as {@link KeysetPages} is
 * the one place a read by keyset is paged: the page is at most {@link KeysetPages#ROWS_IN_A_PAGE} keys, the
 * order is the order given, and an empty list is no page. What the statement is, which placeholders it
 * writes with {@link Placeholders} and what it binds beside the keys stay the caller's, so that a statement
 * the caller writes is the one {@code EveryStatementThatSortsIsRecordedTest} plans and the plan tests hold.
 * It lives in {@code ledger}, the one module every capability may name (ADR-040), and holds no rule.
 */
public final class ListedPages {

    private ListedPages() {}

    /**
     * Hands each page of {@code keys}, of at most {@link KeysetPages#ROWS_IN_A_PAGE}, to {@code page}, in the
     * order the keys iterate; none for an empty collection. The keys are copied first, so a caller that changes
     * its collection while it goes through the pages does not change what it is handed.
     */
    public static <T> void each(Collection<T> keys, Consumer<List<T>> page) {
        each(keys, KeysetPages.ROWS_IN_A_PAGE, page);
    }

    /**
     * As {@link #each(Collection, Consumer)}, in pages of at most {@code size}: for the one statement whose
     * page the ledger has fixed at another size (ADR-257).
     *
     * @throws IllegalArgumentException if {@code size} is less than one, which would never end
     */
    public static <T> void each(Collection<T> keys, int size, Consumer<List<T>> page) {
        if (size < 1) {
            throw new IllegalArgumentException("a page holds at least one key, not " + size);
        }
        List<T> all = List.copyOf(keys);
        for (int from = 0; from < all.size(); from += size) {
            page.accept(all.subList(from, Math.min(all.size(), from + size)));
        }
    }
}
