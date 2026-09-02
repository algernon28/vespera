package io.algernon.vespera.ledger;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.support.SqlitePagingQueryProvider;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * The single record of file occurrence identity, verdicts and run identity (see CONTEXT.md's
 * "Ledger").
 *
 * <p>Deep by construction: the tables are behind this class and nothing else queries them (ADR-041).
 * The survivors query is the case that makes the rule worth the trouble — {@link #survivors} hands
 * out an item reader rather than SQL, so no other module gets to join against {@code verdict} and
 * quietly depend on its shape (ADR-060).
 */
@Component
@DependsOnDatabaseInitialization
public class Ledger {

    /**
     * How many occurrence ids the survivors reader fetches per round trip. Sized here rather than by
     * the caller: a step's chunk size is what governs commit frequency, which ticket #14 measured as
     * the setting that actually matters, and it is a separate number from this one.
     */
    private static final int SURVIVORS_PAGE_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public Ledger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The unfinished walk over {@code root}, if there is one.
     *
     * <p>Its presence is what decides between continuing a walk and minting a new one (ADR-055).
     * The root is compared byte-exact, as every path in the ledger is (ADR-051), so the caller owes
     * it the canonical spelling — which is what a walk canonicalises its root to produce.
     */
    public Optional<ResumableWalk> unfinishedWalk(Path root) {
        return jdbcTemplate
                .query(
                        "SELECT id, checkpoint_ordinals, checkpoint_path, entries_seen, directories_entered"
                                + " FROM walk WHERE root = ? AND finished = 0 ORDER BY id DESC LIMIT 1",
                        (resultSet, rowNumber) -> new ResumableWalk(
                                new WalkId(resultSet.getLong("id")),
                                resultSet.getString("checkpoint_ordinals"),
                                resultSet.getString("checkpoint_path"),
                                new WalkCounts(
                                        resultSet.getLong("entries_seen"),
                                        resultSet.getLong("directories_entered"))),
                        root.toString())
                .stream()
                .findFirst();
    }

    /** Mints the identity a walk of {@code root} is recorded under: unfinished, and at no checkpoint. */
    public WalkId startWalk(Path root) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO walk (root) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, root.toString());
                    return statement;
                },
                keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert into walk generated no key for root " + root);
        }
        return new WalkId(key.longValue());
    }

    /**
     * Records how far a walk has got: its checkpoint, and its cumulative counts as of that point
     * (ADR-055, ADR-056).
     *
     * <p>The counts move with the checkpoint deliberately. They are what the reconciliation checks,
     * so a checkpoint recorded without them would leave a resumed walk unable to say what its
     * predecessor saw.
     */
    public void recordProgress(WalkId walkId, String checkpointOrdinals, String checkpointPath, WalkCounts counts) {
        jdbcTemplate.update(
                "UPDATE walk SET checkpoint_ordinals = ?, checkpoint_path = ?, entries_seen = ?,"
                        + " directories_entered = ? WHERE id = ?",
                checkpointOrdinals,
                checkpointPath,
                counts.entriesSeen(),
                counts.directoriesEntered(),
                walkId.value());
    }

    /**
     * Marks a walk finished, with the counts it ended on.
     *
     * <p>Only a finished walk is eligible as run input, so this is the one write that turns a partial
     * observation into a corpus anything may be judged against.
     */
    public void finishWalk(WalkId walkId, WalkCounts counts) {
        jdbcTemplate.update(
                "UPDATE walk SET finished = 1, entries_seen = ?, directories_entered = ? WHERE id = ?",
                counts.entriesSeen(),
                counts.directoriesEntered(),
                walkId.value());
    }

    /** What the walk row says it met, for the reconciliation to check against the rows written. */
    public WalkCounts countsFor(WalkId walkId) {
        WalkCounts counts = jdbcTemplate.queryForObject(
                "SELECT entries_seen, directories_entered FROM walk WHERE id = ?",
                (resultSet, rowNumber) ->
                        new WalkCounts(resultSet.getLong("entries_seen"), resultSet.getLong("directories_entered")),
                walkId.value());
        if (counts == null) {
            throw new IllegalArgumentException("no walk is recorded under id " + walkId.value());
        }
        return counts;
    }

    /** Whether {@code walkId} has finished, and is therefore eligible as run input. */
    public boolean walkFinished(WalkId walkId) {
        Boolean finished = jdbcTemplate.queryForObject(
                "SELECT finished FROM walk WHERE id = ?", Boolean.class, walkId.value());
        return Boolean.TRUE.equals(finished);
    }

    /** Records one file occurrence against {@code walkId}. */
    public void fileOccurrence(
            WalkId walkId, OccurrencePath path, long sizeInBytes, Instant lastModified, Instant creationTime) {
        jdbcTemplate.update(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (?, ?, ?, ?, ?)",
                walkId.value(),
                path.value(),
                sizeInBytes,
                lastModified.toString(),
                creationTime.toString());
    }

    /** The file occurrences recorded against {@code walkId}. */
    public List<RecordedOccurrence> occurrencesForWalk(WalkId walkId) {
        return jdbcTemplate.query(
                "SELECT path, size_bytes, last_modified, creation_time FROM file_occurrence WHERE walk_id = ?",
                (resultSet, rowNumber) -> new RecordedOccurrence(
                        new OccurrencePath(resultSet.getString("path")),
                        resultSet.getLong("size_bytes"),
                        Instant.parse(resultSet.getString("last_modified")),
                        Instant.parse(resultSet.getString("creation_time"))),
                walkId.value());
    }

    /**
     * How many file occurrences stand against {@code walkId}, counted rather than remembered.
     *
     * <p>One half of the excludes-nothing reconciliation (ADR-056), and the reason that is a check
     * rather than an assertion: a counter the walk kept could be wrong in exactly the way the
     * reconciliation exists to catch.
     */
    public long occurrenceCount(WalkId walkId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_occurrence WHERE walk_id = ?", Long.class, walkId.value());
        return count == null ? 0 : count;
    }

    /** The id of an occurrence within a walk, for a stage holding a path and needing the key. */
    public Optional<OccurrenceId> occurrenceId(WalkId walkId, OccurrencePath path) {
        return jdbcTemplate
                .query(
                        "SELECT id FROM file_occurrence WHERE walk_id = ? AND path = ?",
                        (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("id")),
                        walkId.value(),
                        path.value())
                .stream()
                .findFirst();
    }

    /**
     * Mints the identity a stage's run is recorded under, and records what it was derived from
     * (ADR-048).
     *
     * <p>Nothing in the census slice calls this: stage 0 writes no verdicts, so it mints no run.
     */
    public RunId startRun(
            String stage,
            String implementationVersion,
            String configConsumed,
            WalkId walkId,
            List<RunId> upstreamRunIds) {
        RunId runId = RunId.of(implementationVersion, configConsumed, walkId, upstreamRunIds);
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                runId.value(),
                stage,
                implementationVersion,
                configConsumed,
                walkId.value());
        for (RunId upstream : upstreamRunIds) {
            jdbcTemplate.update(
                    "INSERT INTO run_upstream (run_id, upstream_run_id) VALUES (?, ?)",
                    runId.value(),
                    upstream.value());
        }
        return runId;
    }

    /** The runs {@code runId} read, as a set rather than an order (ADR-048). */
    public List<RunId> upstreamRuns(RunId runId) {
        return jdbcTemplate.query(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ? ORDER BY upstream_run_id",
                (resultSet, rowNumber) -> new RunId(resultSet.getString("upstream_run_id")),
                runId.value());
    }

    /**
     * Appends one verdict against an occurrence, under the run that judged it. Verdicts are only ever
     * appended: retuning a stage is a delete of that stage's rows and a re-run, never an update in
     * place (CONTEXT.md, "Verdict").
     */
    public void verdict(OccurrenceId occurrenceId, RunId runId, VerdictKind kind, String reason) {
        jdbcTemplate.update(
                "INSERT INTO verdict (occurrence_id, run_id, kind, reason) VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                kind.name(),
                reason);
    }

    /**
     * The occurrences of {@code runId}'s walk that carry no blocking verdict — the survivor set, as a
     * reader a step consumes chunk by chunk (ADR-060).
     *
     * <p>A reader, not a view and not a {@code List}. The survivor set is the whole corpus minus what
     * has been ruled out, so at stage 1 it is every occurrence there is; handing back a reader is
     * what keeps a stage from holding a million ids in memory, and keeps the SQL inside
     * {@code ledger} where the shape of {@code verdict} is nobody else's business. Each capability
     * module then queries its own tables for the chunk it was handed.
     *
     * <p>The run names the walk, so this reader is scoped to that walk's occurrences. Verdicts are
     * not filtered by run: a blocking verdict from any run removes an occurrence, which is what makes
     * survival cumulative across the cascade rather than one stage's opinion.
     *
     * <p>The reader is a stream — open it before reading, close it after, which is what a Spring
     * Batch step does for a reader it was handed.
     */
    public ItemStreamReader<OccurrenceId> survivors(RunId runId) {
        SqlitePagingQueryProvider queryProvider = new SqlitePagingQueryProvider();
        queryProvider.setSelectClause("id");
        queryProvider.setFromClause("file_occurrence");
        queryProvider.setWhereClause("walk_id = (SELECT walk_id FROM run WHERE id = :runId)"
                + " AND NOT EXISTS (SELECT 1 FROM verdict"
                + " WHERE verdict.occurrence_id = file_occurrence.id"
                + " AND verdict.kind IN (" + blockingKinds() + "))");
        queryProvider.setSortKeys(Map.of("id", Order.ASCENDING));

        JdbcPagingItemReader<OccurrenceId> reader = new JdbcPagingItemReader<>(dataSource(), queryProvider);
        reader.setName("survivors");
        reader.setParameterValues(Map.of("runId", runId.value()));
        reader.setPageSize(SURVIVORS_PAGE_SIZE);
        reader.setRowMapper((resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("id")));
        try {
            reader.afterPropertiesSet();
        } catch (Exception e) {
            // Only ever a mistake in the query above: the reader validates its own configuration
            // here, and nothing about it depends on the caller.
            throw new IllegalStateException("the survivors reader is misconfigured", e);
        }
        return reader;
    }

    /**
     * The blocking kinds as SQL literals rather than as placeholders.
     *
     * <p>Inlining a value into SQL is the thing not to do, with one exception, and this is it: these
     * are enum constant names this class compiled against, so there is nothing here to inject. The
     * alternative is a placeholder list whose length depends on the vocabulary, which the paging
     * query provider builds its own SQL around and cannot be handed.
     */
    private static String blockingKinds() {
        return Arrays.stream(VerdictKind.values())
                .filter(VerdictKind::blocking)
                .map(kind -> "'" + kind.name() + "'")
                .collect(Collectors.joining(", "));
    }

    private DataSource dataSource() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("the ledger's JdbcTemplate carries no DataSource");
        }
        return dataSource;
    }
}
