package io.algernon.vespera.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * A long read of the ledger, handed out a page at a time by keyset (ADR-209 section 2).
 *
 * <p>The one pager of {@code ledger}, shared by the three reads that can be as long as a walk. Each
 * page is one statement on the application's {@link JdbcTemplate}, of at most {@link #ROWS_IN_A_PAGE}
 * rows, asked for only when the page in hand has been handed out, and starting after the last row of the
 * one before. A page that comes back short ends the read. Nothing is held between pages, so there is
 * nothing to close, and every {@link #iterator()} starts again at the first page.
 */
final class KeysetPages<T> implements Iterable<T> {

    /** The most rows one statement of a read brings back. */
    static final int ROWS_IN_A_PAGE = 1_000;

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
    KeysetPages(
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
                List<Object> arguments = new ArrayList<>(fixedArguments);
                arguments.addAll(key);
                List<T> fetched = jdbcTemplate.query(sql, rowMapper, arguments.toArray());
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
