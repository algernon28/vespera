package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.WalkId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code corpus}'s own record of walk anomalies (ADR-041): not a verdict, so not the ledger's
 * concern — a verdict needs an occurrence to attach to, and an anomaly is exactly the case where
 * none exists.
 */
@Component
public class AnomalyLog {

    /** The most rows one statement of a comparison brings back. */
    private static final int ROWS_IN_A_PAGE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public AnomalyLog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records one walk anomaly against {@code walkId}. */
    void anomaly(WalkId walkId, String pathRendering, WalkAnomalyKind kind, String detail) {
        jdbcTemplate.update(
                "INSERT INTO walk_anomaly (walk_id, path_rendering, kind, detail) VALUES (?, ?, ?, ?)",
                walkId.value(),
                pathRendering,
                kind.name(),
                detail);
    }

    /**
     * How many walk anomalies stand against {@code walkId}.
     *
     * <p>The other half of the excludes-nothing reconciliation (ADR-056), which lives in
     * {@code corpus} rather than {@code ledger} for the same reason this table does: an anomaly is
     * not a verdict.
     */
    long anomalyCount(WalkId walkId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk_anomaly WHERE walk_id = ?", Long.class, walkId.value());
        return count == null ? 0 : count;
    }

    /**
     * Deletes the walk anomalies recorded against {@code walkId} and no other walk's (ADR-209 section
     * 3.1). An anomaly refers to its walk, so with foreign keys on the walk's row cannot be discarded
     * while one stands: the caller deletes these first.
     */
    void discardForWalk(WalkId walkId) {
        jdbcTemplate.update("DELETE FROM walk_anomaly WHERE walk_id = ?", walkId.value());
    }

    /**
     * Whether the two walks noted the same walk anomalies, in the order they were noted (ADR-220 section 3).
     *
     * <p>Asked a page of up to {@value #ROWS_IN_A_PAGE} of each walk at a time, in id order, by
     * {@code walk_anomaly_by_walk_id}, and compared on the path rendering, the kind and the detail; the ids
     * are not compared. Two pages are held, and the comparison stops at the first page that differs.
     */
    boolean sameAnomalies(WalkId earlier, WalkId later) {
        long afterEarlier = Long.MIN_VALUE;
        long afterLater = Long.MIN_VALUE;
        while (true) {
            List<PagedAnomaly> pageOfEarlier = pageOf(earlier, afterEarlier);
            List<PagedAnomaly> pageOfLater = pageOf(later, afterLater);
            if (pageOfEarlier.size() != pageOfLater.size()) {
                return false;
            }
            for (int row = 0; row < pageOfEarlier.size(); row++) {
                if (!pageOfEarlier.get(row).anomaly().equals(pageOfLater.get(row).anomaly())) {
                    return false;
                }
            }
            if (pageOfEarlier.size() < ROWS_IN_A_PAGE) {
                return true;
            }
            afterEarlier = pageOfEarlier.get(pageOfEarlier.size() - 1).id();
            afterLater = pageOfLater.get(pageOfLater.size() - 1).id();
        }
    }

    private List<PagedAnomaly> pageOf(WalkId walkId, long afterId) {
        return jdbcTemplate.query(
                "SELECT id, path_rendering, kind, detail FROM walk_anomaly WHERE walk_id = ? AND id > ?"
                        + " ORDER BY id LIMIT " + ROWS_IN_A_PAGE,
                (resultSet, rowNumber) -> new PagedAnomaly(
                        resultSet.getLong("id"),
                        new RecordedAnomaly(
                                resultSet.getString("path_rendering"),
                                WalkAnomalyKind.valueOf(resultSet.getString("kind")),
                                resultSet.getString("detail"))),
                walkId.value(),
                afterId);
    }

    /** One row of a page of a walk's anomalies: its number, which the comparison pages by, and what was noted. */
    private record PagedAnomaly(long id, RecordedAnomaly anomaly) {}
}
