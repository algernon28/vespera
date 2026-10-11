package io.algernon.vespera.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * A long read of the ledger, handed out a page at a time by keyset (ADR-209 section 2).
 *
 * <p>The one pager of {@code ledger}, shared by the three reads that can be as long as a walk, and public
 * since the review of 2026-10-10 so that a capability module's read of a page at a time by a key its rows
 * carry is one constructor call and not a loop of its own (ADR-040: every module may name {@code ledger}).
 * Each page is one statement on the {@link JdbcTemplate} it is given, of at most {@link #ROWS_IN_A_PAGE}
 * rows, asked for only when the page in hand has been handed out, and starting after the last row of the
 * one before. A page that comes back short ends the read. Nothing is held between pages, so there is
 * nothing to close, and every {@link #iterator()} and {@link #eachPage} starts again at the first page.
 */
public final class KeysetPages<T> implements Iterable<T> {

    /** The most rows one statement of a read brings back. */
    public static final int ROWS_IN_A_PAGE = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final String sql;
    private final List<Object> fixedArguments;
    private final List<Object> keyBeforeTheFirstRow;
    private final Function<T, List<Object>> keyAfter;
    private final RowMapper<T> rowMapper;

    /**
     * @param sql a page's statement, carrying its bound as {@code LIMIT} in the text
     * @param fixedArguments the arguments before the key, the same on every page
     * @param keyBeforeTheFirstRow the arguments after them on the first page
     * @param keyAfter the arguments after them on the page that follows the page ending in a given row
     */
    public KeysetPages(
            JdbcTemplate jdbcTemplate,
            String sql,
            List<Object> fixedArguments,
            List<Object> keyBeforeTheFirstRow,
            Function<T, List<Object>> keyAfter,
            RowMapper<T> rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
        this.fixedArguments = List.copyOf(fixedArguments);
        this.keyBeforeTheFirstRow = List.copyOf(keyBeforeTheFirstRow);
        this.keyAfter = keyAfter;
        this.rowMapper = rowMapper;
    }

    /**
     * Hands each page to {@code page} once its statement has finished, an empty page never: the form for a
     * caller that goes through a page of rows at a time and asks the database about the page as a whole.
     */
    public void eachPage(Consumer<List<T>> page) {
        List<Object> key = keyBeforeTheFirstRow;
        while (true) {
            List<T> fetched = fetch(key);
            if (!fetched.isEmpty()) {
                page.accept(fetched);
            }
            if (fetched.size() < ROWS_IN_A_PAGE) {
                return;
            }
            key = keyAfter.apply(fetched.getLast());
        }
    }

    private List<T> fetch(List<Object> key) {
        List<Object> arguments = new ArrayList<>(fixedArguments);
        arguments.addAll(key);
        return jdbcTemplate.query(sql, rowMapper, arguments.toArray());
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {

            private final Deque<T> page = new ArrayDeque<>();
            private List<Object> key = keyBeforeTheFirstRow;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                if (page.isEmpty() && !exhausted) {
                    fetchNextPage();
                }
                return !page.isEmpty();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return page.poll();
            }

            private void fetchNextPage() {
                List<T> fetched = fetch(key);
                page.addAll(fetched);
                if (fetched.size() < ROWS_IN_A_PAGE) {
                    exhausted = true;
                }
                if (!fetched.isEmpty()) {
                    key = keyAfter.apply(fetched.get(fetched.size() - 1));
                }
            }
        };
    }
}
