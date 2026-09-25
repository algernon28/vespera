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

    /**
     * The finished walk of {@code root}, if census has completed one.
     *
     * <p>What a later stage resolves its run input from: unlike {@link #unfinishedWalk}, this looks
     * for a walk that has already reached {@code finished = 1}, since a stage may only read what
     * census actually recorded in full.
     */
    public Optional<WalkId> finishedWalkFor(Path root) {
        return jdbcTemplate
                .query(
                        "SELECT id FROM walk WHERE root = ? AND finished = 1 ORDER BY id DESC LIMIT 1",
                        (resultSet, rowNumber) -> new WalkId(resultSet.getLong("id")),
                        root.toString())
                .stream()
                .findFirst();
    }

    /**
     * The finished walk of {@code root} recorded before {@code walkId}, if there is one (ADR-115).
     *
     * <p>The immediately preceding one, never the best match among all of them. Searching earlier
     * walks would reintroduce the ambiguity ADR-099 refuses, and an approval given while the archive
     * was different should stay expired rather than be revived by the archive changing back.
     */
    public Optional<WalkId> finishedWalkBefore(Path root, WalkId walkId) {
        return jdbcTemplate
                .query(
                        "SELECT id FROM walk WHERE root = ? AND finished = 1 AND id < ? ORDER BY id DESC LIMIT 1",
                        (resultSet, rowNumber) -> new WalkId(resultSet.getLong("id")),
                        root.toString(),
                        walkId.value())
                .stream()
                .findFirst();
    }

    /**
     * Removes a walk and everything recorded beneath it (ADR-115).
     *
     * <p>Only ever called on a traversal that observed what the previous one already recorded, so
     * what is deleted is a duplicate rather than a loss. Deleted rather than left standing because a
     * spare walk row is a second corpus nobody has, and everything downstream points at a walk.
     */
    public void discardWalk(WalkId walkId) {
        jdbcTemplate.update("DELETE FROM file_occurrence WHERE walk_id = ?", walkId.value());
        jdbcTemplate.update("DELETE FROM walk_anomaly WHERE walk_id = ?", walkId.value());
        jdbcTemplate.update("DELETE FROM walk WHERE id = ?", walkId.value());
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

    /**
     * The facts recorded for {@code occurrenceId} — path, size, creation time — for a stage holding
     * the key and needing what census observed about the file.
     */
    public Optional<OccurrenceFacts> factsFor(OccurrenceId occurrenceId) {
        return jdbcTemplate
                .query(
                        "SELECT path, size_bytes, creation_time FROM file_occurrence WHERE id = ?",
                        (resultSet, rowNumber) -> new OccurrenceFacts(
                                new OccurrencePath(resultSet.getString("path")),
                                resultSet.getLong("size_bytes"),
                                Instant.parse(resultSet.getString("creation_time"))),
                        occurrenceId.value())
                .stream()
                .findFirst();
    }

    /**
     * The walk a run was recorded against, for a caller holding a run id and needing the occurrences
     * it was about.
     *
     * <p>Two callers, wanting different things from it. The relevance report resolves each label's path
     * into this walk so the answers can be joined to scores keyed by occurrence (ADR-097). Label
     * ingestion wants only the existence of the row: it records answers against paths and never
     * resolves one, so an empty result there means the file names a run this database does not hold.
     */
    public Optional<WalkId> walkOf(RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT walk_id FROM run WHERE id = ?",
                        (resultSet, rowNumber) -> new WalkId(resultSet.getLong("walk_id")),
                        runId.value())
                .stream()
                .findFirst();
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
     * (ADR-048) — or carries on under the row already standing for that identity (ADR-115).
     *
     * <p><b>Mint or continue, never mint twice.</b> A run id is a total function of the four things
     * hashed into it, so a re-derived id names a row that agrees with the caller in all four. There is
     * nothing to update and nothing to reconcile: the row is already what this call would have
     * written. {@code stage} is the one column not among them -- it is recorded, not hashed -- so two
     * stages agreeing on all four would collide here rather than being told apart, which is precisely
     * the conflict the next paragraph refuses to swallow. Before ADR-115 the second insert could not happen, because a fresh walk id
     * on every invocation made every derived id fresh too; with a repeated observation discarded, it
     * happens the moment a corpus is re-walked unchanged.
     *
     * <p>Not {@code INSERT OR IGNORE}: that would swallow a genuine conflict as readily as this one,
     * and the conflict it must never swallow is two different runs colliding on one id.
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
        if (runExists(runId)) {
            return runId;
        }
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

    /** Whether a row already stands under this identity. */
    private boolean runExists(RunId runId) {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE id = ?", Integer.class, runId.value());
        return rows != null && rows > 0;
    }

    /**
     * Records that {@code step}'s work under {@code runId} is entirely recorded (ADR-116, re-keying
     * {@link #finishWalk}'s shape from a run to the step that actually did the work).
     *
     * <p>Called by the step itself, after its last row and never before: what separates a step whose
     * completion is recorded from one whose is not is the difference between work a later invocation
     * may skip and work it must do again. Several runs are shared by more than one step, which is
     * exactly why this is keyed by step rather than by run alone — a flag on the run would let the
     * first step to finish answer for every step behind it.
     *
     * <p>{@code OR IGNORE} rather than a bare insert: a step-completion listener runs at every step
     * boundary, including one a reader already recognised as finished and skipped, so this method has
     * to tolerate being told the same true thing twice. That is unlike {@link #startRun}, where a
     * second insert under one id could be hiding two different runs colliding — here, one row already
     * says everything a second one would, since {@code (run_id, step)} carries no other column.
     */
    public void finishStep(RunId runId, String step) {
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO finished_step (run_id, step) VALUES (?, ?)", runId.value(), step);
    }

    /**
     * Whether {@code step}'s work under {@code runId} is entirely recorded.
     *
     * <p>False for a step that has never run under this run, failed partway through, or has not been
     * written yet — the same honest answer for all three, which is what makes the discard-and-redo
     * half of ADR-115/ADR-116 safe: a step meeting {@code false} here owes its own rows a clean start.
     */
    public boolean stepFinished(RunId runId, String step) {
        Integer finished = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                Integer.class,
                runId.value(),
                step);
        return finished != null && finished > 0;
    }

    /**
     * Deletes every verdict of {@code kinds} recorded under {@code runId} — the discard half of
     * ADR-115/ADR-116, for the one table every stage shares. Safe because a run is one stage's
     * identity (CONTEXT.md, "Run"): a kind named here is only ever written by the one step of that
     * run's stage that writes it, so discarding by kind under this run id discards that step's own
     * rows and nothing another step under the same run wrote.
     */
    public void discardVerdicts(RunId runId, VerdictKind... kinds) {
        if (kinds.length == 0) {
            return;
        }
        String placeholders = Arrays.stream(kinds).map(kind -> "?").collect(Collectors.joining(", "));
        Object[] arguments = new Object[kinds.length + 1];
        arguments[0] = runId.value();
        for (int i = 0; i < kinds.length; i++) {
            arguments[i + 1] = kinds[i].name();
        }
        jdbcTemplate.update("DELETE FROM verdict WHERE run_id = ? AND kind IN (" + placeholders + ")", arguments);
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
     * How many occurrences {@link #survivors} would hand out for {@code runId} — the denominator a
     * stage's progress line needs before it starts (ADR-093).
     *
     * <p>The same anti-join as the reader, and deliberately not a count the caller keeps as it reads:
     * a stage reports progress against the set it was given, and that set is a query.
     */
    public long survivorCount(RunId runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_occurrence"
                        + " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM verdict"
                        + " WHERE verdict.occurrence_id = file_occurrence.id"
                        + " AND verdict.kind IN (" + blockingKinds() + "))",
                Long.class,
                runId.value());
        return count == null ? 0 : count;
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
     * Every occurrence of {@code walkId}, as a reader a step consumes chunk by chunk — the seed
     * walk's own pass (ADR-083).
     *
     * <p>Deliberately not {@link #survivors}, and the difference is the decision rather than a
     * convenience. Survivorship is the absence of a blocking verdict, and no verdict is ever written
     * against a seed occurrence: every kind in the closed vocabulary exists to remove a document from
     * the survivor set, and a seed was never a candidate for it. Filtering seeds through the survivors query would
     * quietly make a seed folder subject to the corpus's own removals — a seed that happens to be a
     * byte-identical copy of another file would vanish from the seed set for a reason that has nothing
     * to do with seeds.
     *
     * <p>A reader rather than a {@code List} for the same reason {@link #survivors} is one: the SQL
     * stays inside {@code ledger} (ADR-060), and a seed folder large enough to matter is not held in
     * memory to be counted.
     */
    public ItemStreamReader<OccurrenceId> occurrencesOf(WalkId walkId) {
        SqlitePagingQueryProvider queryProvider = new SqlitePagingQueryProvider();
        queryProvider.setSelectClause("id");
        queryProvider.setFromClause("file_occurrence");
        queryProvider.setWhereClause("walk_id = :walkId");
        queryProvider.setSortKeys(Map.of("id", Order.ASCENDING));

        JdbcPagingItemReader<OccurrenceId> reader = new JdbcPagingItemReader<>(dataSource(), queryProvider);
        reader.setName("occurrencesOf" + walkId.value());
        reader.setParameterValues(Map.of("walkId", walkId.value()));
        reader.setPageSize(SURVIVORS_PAGE_SIZE);
        reader.setRowMapper((resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("id")));
        try {
            reader.afterPropertiesSet();
        } catch (Exception e) {
            // Only ever a mistake in the query above: the reader validates its own configuration
            // here, and nothing about it depends on the caller.
            throw new IllegalStateException("the occurrences reader is misconfigured", e);
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
