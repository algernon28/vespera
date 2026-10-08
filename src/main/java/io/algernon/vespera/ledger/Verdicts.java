package io.algernon.vespera.ledger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the ledger records about verdicts, and the survivors they leave: the {@code verdict} table (ADR-209
 * section 1).
 */
public class Verdicts {

    /** Occurrences per statement in {@link #discardVerdictsAgainst}: SQLite caps one statement's variables. */
    private static final int DISCARD_BATCH = 500;

    /**
     * One page of {@link #survivors}: the run's walk's survivors after the last id read.
     */
    private static final String SURVIVORS_PAGE_SQL = "SELECT id FROM file_occurrence"
            + " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)"
            + " AND NOT EXISTS (SELECT 1 FROM verdict"
            + " WHERE verdict.occurrence_id = file_occurrence.id"
            + " AND verdict.kind IN (%s)"
            + " AND verdict.run_id IN (%s))"
            + " AND id > ? ORDER BY id LIMIT " + KeysetPages.ROWS_IN_A_PAGE;

    /**
     * One page of {@link #survivorsBySize}: the walk's survivors after the last row read. {@code size_bytes
     * >= ?} bounds the range by the index column after the walk, and the row-value comparison then cuts
     * it at the exact row, the pair {@code (size_bytes, id)} compared as one.
     */
    private static final String SURVIVORS_BY_SIZE_PAGE_SQL = "SELECT id, size_bytes FROM file_occurrence"
            + " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)"
            + " AND NOT EXISTS (SELECT 1 FROM verdict"
            + " WHERE verdict.occurrence_id = file_occurrence.id"
            + " AND verdict.kind IN (%s)"
            + " AND verdict.run_id IN (%s))"
            + " AND size_bytes >= ? AND (size_bytes, id) > (?, ?)"
            + " ORDER BY size_bytes, id LIMIT " + KeysetPages.ROWS_IN_A_PAGE;

    private final JdbcTemplate jdbcTemplate;

    public Verdicts(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    /**
     * Deletes the verdicts of {@code kind} recorded under {@code runId} against exactly {@code
     * occurrences}, leaving every other verdict of that kind where it is (ADR-181 section 1: the
     * verdicts that resolved a stage-2 fault, which a resumed step deletes before it reads the
     * faulted occurrence again). {@code verdict} has no unique key, so a verdict left behind would be
     * written a second time.
     */
    public void discardVerdictsAgainst(RunId runId, Collection<OccurrenceId> occurrences, VerdictKind kind) {
        List<OccurrenceId> remaining = List.copyOf(occurrences);
        // SQLite caps the variables of one statement, so a long list goes in batches.
        for (int from = 0; from < remaining.size(); from += DISCARD_BATCH) {
            List<OccurrenceId> batch = remaining.subList(from, Math.min(from + DISCARD_BATCH, remaining.size()));
            String occurrencePlaceholders = batch.stream().map(id -> "?").collect(Collectors.joining(", "));
            Object[] arguments = new Object[2 + batch.size()];
            arguments[0] = runId.value();
            arguments[1] = kind.name();
            for (int i = 0; i < batch.size(); i++) {
                arguments[2 + i] = batch.get(i).value();
            }
            jdbcTemplate.update(
                    "DELETE FROM verdict WHERE run_id = ? AND kind = ? AND occurrence_id IN ("
                            + occurrencePlaceholders + ")",
                    arguments);
        }
    }

    /**
     * Appends one verdict against an occurrence, under the run that judged it. Verdicts accumulate:
     * retuning a stage mints a new run of that stage (ADR-117, ADR-154) rather than an update in
     * place, and none is deleted except by the step that wrote it, redoing its own unfinished work
     * under the same run (ADR-116, CONTEXT.md, "Verdict").
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
     * Every occurrence carrying {@code extraction-failed} under {@code runId}, with its path and the
     * verdict's reason, in path order (ADR-175): what stage 2 lists for review once it ends. Read under
     * the run, so after a resume it covers what earlier invocations of that run recorded too (ADR-181
     * section 5).
     */
    public List<RemovedOccurrence> extractionFailures(RunId runId) {
        return jdbcTemplate.query(
                "SELECT v.occurrence_id, o.path, v.reason FROM verdict v"
                        + " JOIN file_occurrence o ON o.id = v.occurrence_id"
                        + " WHERE v.run_id = ? AND v.kind = ? ORDER BY o.path",
                (resultSet, rowNumber) -> new RemovedOccurrence(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        resultSet.getString("path"),
                        resultSet.getString("reason")),
                runId.value(),
                VerdictKind.EXTRACTION_FAILED.name());
    }

    /**
     * How many occurrences {@link #survivors} would hand out for {@code runId} — the denominator a
     * stage's progress line needs before it starts (ADR-093).
     *
     * <p>The same anti-join as the read, over the same {@link #runsInScope} set, and deliberately
     * not a count the caller keeps as it reads: a stage reports progress against the set it was
     * given, and that set is a query.
     */
    public long survivorCount(RunId runId) {
        List<String> runsInScope = runsInScope(runId);
        String placeholders = runsInScope.stream().map(id -> "?").collect(Collectors.joining(", "));
        Object[] arguments = new Object[runsInScope.size() + 1];
        arguments[0] = runId.value();
        for (int i = 0; i < runsInScope.size(); i++) {
            arguments[i + 1] = runsInScope.get(i);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_occurrence"
                        + " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)"
                        + " AND NOT EXISTS (SELECT 1 FROM verdict"
                        + " WHERE verdict.occurrence_id = file_occurrence.id"
                        + " AND verdict.kind IN (" + blockingKinds() + ")"
                        + " AND verdict.run_id IN (" + placeholders + "))",
                Long.class,
                arguments);
        return count == null ? 0 : count;
    }

    /**
     * The occurrences of {@code runId}'s walk that carry no blocking verdict under {@code runId} or
     * any run upstream of it, transitively — the survivor set, in id order, a page of 1,000 at a time
     * (ADR-060, ADR-156, ADR-209 section 2).
     *
     * <p>Not a view and not a {@code List}. The survivor set is the whole corpus minus what has been
     * ruled out, so at stage 1 it is every occurrence there is; handing back pages is what keeps a
     * stage from holding a million ids in memory, and keeps the SQL inside {@code ledger} where the
     * shape of {@code verdict} is nobody else's business. Each capability module then queries its own
     * tables for the ids it was handed.
     *
     * <p>The run names the walk, so this is scoped to that walk's occurrences. A blocking verdict counts
     * only when it was written under {@code runId} itself or under a run reached from {@code runId} by
     * following {@code run_upstream}, however many steps back ({@link #runsInScope}). A verdict under
     * any other run of the same walk — a sibling, a later run, or a run this run does not read — stays
     * recorded and removes nothing (ADR-156 section 2).
     *
     * <p>An {@code Iterable} that needs no closing; each of its iterators starts again at the first page.
     */
    public Iterable<OccurrenceId> survivors(RunId runId) {
        List<String> runsInScope = runsInScope(runId);
        String placeholders = runsInScope.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> fixedArguments = new ArrayList<>();
        fixedArguments.add(runId.value());
        fixedArguments.addAll(runsInScope);
        return new KeysetPages<>(
                jdbcTemplate,
                SURVIVORS_PAGE_SQL.formatted(blockingKinds(), placeholders),
                fixedArguments,
                List.of(Long.MIN_VALUE),
                (OccurrenceId last) -> List.of(last.value()),
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("id")));
    }

    /**
     * The survivors of {@code runId}, the same set {@link #survivors} reads, in ascending recorded size and
     * the lower id first within a size, so every survivor of one size arrives together (ADR-200).
     *
     * <p>What stage 1's content identity needs, which is each size whole and never the whole set: a caller
     * holds one size at a time and lets it go. Paged for the reason {@link #survivors} is (ADR-060), by
     * keyset over {@code file_occurrence_by_walk_and_size}, so no page sorts and none goes back over the
     * walk.
     *
     * <p>A verdict the caller writes for a survivor already handed out removes nothing still to come,
     * because a later page asks only for rows after the last one read.
     */
    public Iterable<SizedOccurrence> survivorsBySize(RunId runId) {
        List<String> runsInScope = runsInScope(runId);
        String placeholders = runsInScope.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> fixedArguments = new ArrayList<>();
        fixedArguments.add(runId.value());
        fixedArguments.addAll(runsInScope);
        return new KeysetPages<>(
                jdbcTemplate,
                SURVIVORS_BY_SIZE_PAGE_SQL.formatted(blockingKinds(), placeholders),
                fixedArguments,
                List.of(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE),
                (SizedOccurrence last) ->
                        List.of(last.sizeBytes(), last.sizeBytes(), last.occurrenceId().value()),
                (resultSet, rowNumber) ->
                        new SizedOccurrence(new OccurrenceId(resultSet.getLong("id")), resultSet.getLong("size_bytes")));
    }

    /**
     * {@code runId} itself, plus every run reached from it by following {@code run_upstream}
     * transitively — the set of runs whose blocking verdicts a run's survivors answer to (ADR-156
     * section 1). Resolved here, in Java, rather than as a recursive CTE inside the paging query: the
     * page stays a plain anti-join with a bound {@code IN} list, and this walk is a handful of rows per
     * run (one upstream per stage, seven stages), never a scan per occurrence. It reads {@code
     * run_upstream} with the statement {@link Runs#upstreamRuns} uses, so this record needs nothing but
     * a template.
     */
    private List<String> runsInScope(RunId runId) {
        List<String> runsInScope = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> toVisit = new ArrayDeque<>();
        toVisit.add(runId.value());
        while (!toVisit.isEmpty()) {
            String current = toVisit.poll();
            if (visited.add(current)) {
                runsInScope.add(current);
                toVisit.addAll(jdbcTemplate.query(
                        "SELECT upstream_run_id FROM run_upstream WHERE run_id = ? ORDER BY upstream_run_id",
                        (resultSet, rowNumber) -> resultSet.getString("upstream_run_id"),
                        current));
            }
        }
        return runsInScope;
    }

    /**
     * The blocking kinds as SQL literals rather than as placeholders.
     *
     * <p>Inlining a value into SQL is the thing not to do, with one exception, and this is it: these
     * are enum constant names this class compiled against, so there is nothing here to inject.
     */
    private static String blockingKinds() {
        return Arrays.stream(VerdictKind.values())
                .filter(VerdictKind::blocking)
                .map(kind -> "'" + kind.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
