package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code corpus}'s own record of content identity: which occurrences share a SHA-256 (ADR-067),
 * and which of a shared-content group a losing member was superseded by (ADR-069). Neither is a
 * verdict on its own — {@code superseded_by} carries the pointer a {@code SUPERSEDED_BY} verdict's
 * free-text reason cannot, per the same reasoning that keeps {@code walk_anomaly} out of the ledger
 * (ADR-041).
 */
@Component
public class ContentIdentity {

    private final JdbcTemplate jdbcTemplate;

    public ContentIdentity(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records the SHA-256 computed for {@code occurrenceId} under {@code runId}. */
    public void recordHash(OccurrenceId occurrenceId, RunId runId, String sha256) {
        jdbcTemplate.update(
                "INSERT INTO content_hash (occurrence_id, run_id, sha256) VALUES (?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                sha256);
    }

    /** The SHA-256 recorded for {@code occurrenceId} under {@code runId}, if one was computed. */
    public Optional<String> hashFor(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT sha256 FROM content_hash WHERE occurrence_id = ? AND run_id = ?",
                        (resultSet, rowNumber) -> resultSet.getString("sha256"),
                        occurrenceId.value(),
                        runId.value())
                .stream()
                .findFirst();
    }

    /**
     * Records that {@code occurrenceId} was superseded by {@code representativeOccurrenceId} under
     * {@code runId}. The representative itself is never recorded here.
     */
    public void recordSupersededBy(OccurrenceId occurrenceId, RunId runId, OccurrenceId representativeOccurrenceId) {
        jdbcTemplate.update(
                "INSERT INTO superseded_by (occurrence_id, run_id, representative_occurrence_id) VALUES (?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                representativeOccurrenceId.value());
    }

    /** The representative {@code occurrenceId} was superseded by under {@code runId}, if any. */
    public Optional<OccurrenceId> representativeFor(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT representative_occurrence_id FROM superseded_by WHERE occurrence_id = ? AND run_id = ?",
                        (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("representative_occurrence_id")),
                        occurrenceId.value(),
                        runId.value())
                .stream()
                .findFirst();
    }
}
