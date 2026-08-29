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

    private final JdbcTemplate jdbcTemplate;

    public AnomalyLog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records one walk anomaly against {@code walkId}. */
    public void anomaly(WalkId walkId, String pathRendering, WalkAnomalyKind kind, String detail) {
        jdbcTemplate.update(
                "INSERT INTO walk_anomaly (walk_id, path_rendering, kind, detail) VALUES (?, ?, ?, ?)",
                walkId.value(),
                pathRendering,
                kind.name(),
                detail);
    }

    /** The walk anomalies recorded against {@code walkId}. */
    public List<RecordedAnomaly> anomaliesForWalk(WalkId walkId) {
        return jdbcTemplate.query(
                "SELECT path_rendering, kind, detail FROM walk_anomaly WHERE walk_id = ?",
                (resultSet, rowNumber) -> new RecordedAnomaly(
                        resultSet.getString("path_rendering"),
                        WalkAnomalyKind.valueOf(resultSet.getString("kind")),
                        resultSet.getString("detail")),
                walkId.value());
    }
}
