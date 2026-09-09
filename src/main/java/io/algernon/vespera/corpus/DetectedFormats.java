package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code corpus}'s own record of what stage 1 found each file to be (ADR-094), kept under the run
 * that found it rather than as a column on the occurrence (ADR-095).
 *
 * <p>The run is what makes this correct: a detected format is derived, not observed. Add one
 * signature to the rule and the same bytes yield a different answer, so keying by run makes a
 * changed rule a fresh row set rather than history rewritten in place (ADR-077). Census is
 * stat-only and would have to open every file to produce this, which is the second reason it is not
 * a walk fact.
 *
 * <p>The format is the bytes' answer and is never absent; the subtype is the filename's, and is
 * absent wherever ADR-094's narrowing does not apply or finds nothing. They are stored in two
 * columns because they are two strengths of claim, and read back through two accessors for the same
 * reason.
 */
@Component
public class DetectedFormats {

    private final JdbcTemplate jdbcTemplate;

    public DetectedFormats(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records what {@code occurrenceId} was found to be under {@code runId}. */
    public void record(OccurrenceId occurrenceId, RunId runId, DetectedFormat format, Optional<DetectedSubtype> subtype) {
        jdbcTemplate.update(
                "INSERT INTO detected_format (occurrence_id, run_id, format, subtype) VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                format.name(),
                subtype.map(Enum::name).orElse(null));
    }

    /** What {@code occurrenceId} was found to be under {@code runId}, if stage 1 examined it. */
    public Optional<DetectedFormat> formatFor(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT format FROM detected_format WHERE occurrence_id = ? AND run_id = ?",
                        (resultSet, rowNumber) -> DetectedFormat.valueOf(resultSet.getString("format")),
                        occurrenceId.value(),
                        runId.value())
                .stream()
                .findFirst();
    }

    /** Which member of its class {@code occurrenceId} was named as, where anything named one. */
    public Optional<DetectedSubtype> subtypeFor(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT subtype FROM detected_format WHERE occurrence_id = ? AND run_id = ?",
                        (resultSet, rowNumber) -> Optional.ofNullable(resultSet.getString("subtype")),
                        occurrenceId.value(),
                        runId.value())
                .stream()
                .findFirst()
                .flatMap(subtype -> subtype)
                .map(DetectedSubtype::valueOf);
    }
}
