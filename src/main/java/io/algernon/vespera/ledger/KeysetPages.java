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
 * <p>The one pager of {@code ledger}, shared by every read of {@code ledger} that is handed out a page at a time by keyset, and public
 * since the review of 2026-10-10 so that a capability module's read of a page at a time by a key its rows
 * carry is one constructor call and not a loop of its own (ADR-040: every module may name {@code ledger}).
 * A read that can be as long as a walk and is not handed out by keyset is the one statement {@code ORDER BY o.path}
 * of {@link Verdicts#eachExtractionFailure}, an exception recorded in ADR-218.
 * Each page is one statement on the {@link JdbcTemplate} it is given, of at most {@link #ROWS_IN_A_PAGE}
 * rows, asked for only when the page in hand has been handed out, and starting after the last row of the
 * one before. A page that comes back short ends the read, so the pager writes each page's bound, {@code LIMIT}
 * and that number, after the statement it is given, which names none (ADR-245, amending ADR-244). Nothing is
 * held between pages, so there is nothing to close, and every {@link #iterator()} and {@link #eachPage} starts
 * again at the first page.
 *
 * <p>A key that is not a column the caller hands on, such as a rowid, is read into a carrier row the row
 * mapper makes, and {@link #map} hands out what the caller wants of each. A statement that binds its key
 * before another argument is given each page's arguments whole, by the second constructor.
 */
public final class KeysetPages<T> implements Iterable<T> {

    /** The most rows one statement of a read brings back. */
    public static final int ROWS_IN_A_PAGE = 1_000;

    /** How every page's statement ends: its bound, the page, written by the pager. */
    private static final String A_PAGES_BOUND = " LIMIT " + ROWS_IN_A_PAGE;

    private final JdbcTemplate jdbcTemplate;
    private final String sql;
    private final List<Object> argumentsOfTheFirstPage;
    private final Function<T, List<Object>> argumentsAfter;
    private final RowMapper<T> rowMapper;

    /**
     * A read whose statement binds its key after every other argument.
     *
     * @param sql a page's statement, ordered by its key and with no bound, which the pager writes after it
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
        this(
                jdbcTemplate,
                sql,
                joined(fixedArguments, keyBeforeTheFirstRow),
                joinedAfter(List.copyOf(fixedArguments), keyAfter),
                rowMapper);
    }

    /**
     * A read whose statement binds its key where its text puts it, before another argument as well as after
     * one: the caller gives every argument of a page, in the order the text binds them.
     *
     * @param sql a page's statement, ordered by its key and with no bound, which the pager writes after it
     * @param argumentsOfTheFirstPage every argument of the first page's statement
     * @param argumentsAfter every argument of the page that follows the page ending in a given row
     */
    public KeysetPages(
            JdbcTemplate jdbcTemplate,
            String sql,
            List<Object> argumentsOfTheFirstPage,
            Function<T, List<Object>> argumentsAfter,
            RowMapper<T> rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql + A_PAGES_BOUND;
        this.argumentsOfTheFirstPage = List.copyOf(argumentsOfTheFirstPage);
        this.argumentsAfter = argumentsAfter;
        this.rowMapper = rowMapper;
    }

    private static List<Object> joined(List<Object> first, List<Object> then) {
        List<Object> arguments = new ArrayList<>(first);
        arguments.addAll(then);
        return arguments;
    }

    private static <T> Function<T, List<Object>> joinedAfter(
            List<Object> fixedArguments, Function<T, List<Object>> keyAfter) {
        return last -> joined(fixedArguments, keyAfter.apply(last));
    }

    /**
     * Hands each page to {@code page} once its statement has finished, an empty page never: the form for a
     * caller that goes through a page of rows at a time and asks the database about the page as a whole.
     */
    public void eachPage(Consumer<List<T>> page) {
        List<Object> arguments = argumentsOfTheFirstPage;
        while (true) {
            List<T> fetched = fetch(arguments);
            if (!fetched.isEmpty()) {
                page.accept(fetched);
            }
            if (fetched.size() < ROWS_IN_A_PAGE) {
                return;
            }
            arguments = argumentsAfter.apply(fetched.getLast());
        }
    }

    private List<T> fetch(List<Object> arguments) {
        return jdbcTemplate.query(sql, rowMapper, arguments.toArray());
    }

    /**
     * The same read handing out {@code each} of a row in the row's place, a page asked for when the page in
     * hand has been handed out, as by {@link #iterator()}: for a read whose rows carry a key the caller does
     * not hand on.
     */
    public <R> Iterable<R> map(Function<? super T, ? extends R> each) {
        return () -> {
            Iterator<T> rows = iterator();
            return new Iterator<>() {

                @Override
                public boolean hasNext() {
                    return rows.hasNext();
                }

                @Override
                public R next() {
                    return each.apply(rows.next());
                }
            };
        };
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {

            private final Deque<T> page = new ArrayDeque<>();
            private List<Object> arguments = argumentsOfTheFirstPage;
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
                List<T> fetched = fetch(arguments);
                page.addAll(fetched);
                if (fetched.size() < ROWS_IN_A_PAGE) {
                    exhausted = true;
                }
                if (!fetched.isEmpty()) {
                    arguments = argumentsAfter.apply(fetched.get(fetched.size() - 1));
                }
            }
        };
    }
}
