package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.WalkId;
import java.util.Iterator;
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
     * <p>Asked a page of up to {@value KeysetPages#ROWS_IN_A_PAGE} of each walk at a time, in id order, by
     * {@code walk_anomaly_by_walk_id}, through {@link KeysetPages} (ADR-257), and compared on the path
     * rendering, the kind and the detail; the ids are not compared. A page of each is held at a time, the two
     * asked in turn, and the comparison stops at the first row that differs.
     */
    boolean sameAnomalies(WalkId earlier, WalkId later) {
        Iterator<RecordedAnomaly> ofEarlier = anomaliesOf(earlier).iterator();
        Iterator<RecordedAnomaly> ofLater = anomaliesOf(later).iterator();
        while (true) {
            boolean moreOfEarlier = ofEarlier.hasNext();
            boolean moreOfLater = ofLater.hasNext();
            if (moreOfEarlier != moreOfLater) {
                return false;
            }
            if (!moreOfEarlier) {
                return true;
            }
            if (!ofEarlier.next().equals(ofLater.next())) {
                return false;
            }
        }
    }

    /** What {@code walkId} noted, in id order, a page asked for when the one in hand has been gone through. */
    private Iterable<RecordedAnomaly> anomaliesOf(WalkId walkId) {
        return new KeysetPages<PagedAnomaly>(
                        jdbcTemplate,
                        "SELECT id, path_rendering, kind, detail FROM walk_anomaly WHERE walk_id = ? AND id > ?"
                                + " ORDER BY id",
                        List.of(walkId.value()),
                        List.of(Long.MIN_VALUE),
                        last -> List.of(last.id()),
                        (resultSet, rowNumber) -> new PagedAnomaly(
                                resultSet.getLong("id"),
                                new RecordedAnomaly(
                                        resultSet.getString("path_rendering"),
                                        WalkAnomalyKind.valueOf(resultSet.getString("kind")),
                                        resultSet.getString("detail"))))
                .map(PagedAnomaly::anomaly);
    }

    /** One row of a page of a walk's anomalies: its number, which the comparison pages by, and what was noted. */
    private record PagedAnomaly(long id, RecordedAnomaly anomaly) {}
}
