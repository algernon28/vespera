package io.algernon.vespera.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The survivors of a run in ascending recorded size, the lower id first within a size, read a page at a
 * time by keyset (ADR-060, ADR-200).
 *
 * <p>Hand-written rather than a {@code JdbcPagingItemReader}, for the one reason that matters: the
 * provider Spring Batch ships spells its next-page condition as an {@code OR} of two comparisons, which
 * SQLite does not answer from {@code file_occurrence_by_walk_and_size} as one range, so every page after
 * the first would go through the walk again. A row-value comparison, bounded below by the size alone, is
 * answered as a range of that index with no sort, whatever the page.
 *
 * <p>Each page is one statement that borrows its connection and gives it back, so nothing here holds one
 * while the caller writes.
 */
final class SurvivorsBySize implements ItemStreamReader<SizedOccurrence> {

    private final JdbcTemplate jdbcTemplate;
    private final String sql;
    private final List<Object> fixedArguments;
    private final int pageSize;

    private final Deque<SizedOccurrence> page = new ArrayDeque<>();
    private long lastSize;
    private long lastId;
    private boolean exhausted;

    SurvivorsBySize(JdbcTemplate jdbcTemplate, String sql, List<Object> fixedArguments, int pageSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
        this.fixedArguments = fixedArguments;
        this.pageSize = pageSize;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        page.clear();
        lastSize = Long.MIN_VALUE;
        lastId = Long.MIN_VALUE;
        exhausted = false;
    }

    @Override
    public SizedOccurrence read() {
        if (page.isEmpty() && !exhausted) {
            fetchNextPage();
        }
        return page.poll();
    }

    @Override
    public void close() {
        page.clear();
        exhausted = true;
    }

    private void fetchNextPage() {
        List<Object> arguments = new ArrayList<>(fixedArguments);
        arguments.add(lastSize);
        arguments.add(lastSize);
        arguments.add(lastId);
        arguments.add(pageSize);
        List<SizedOccurrence> fetched = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        new SizedOccurrence(new OccurrenceId(resultSet.getLong("id")), resultSet.getLong("size_bytes")),
                arguments.toArray());
        page.addAll(fetched);
        if (fetched.size() < pageSize) {
            exhausted = true;
        }
        if (!fetched.isEmpty()) {
            SizedOccurrence last = fetched.get(fetched.size() - 1);
            lastSize = last.sizeBytes();
            lastId = last.occurrenceId().value();
        }
    }
}
