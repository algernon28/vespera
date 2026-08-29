package io.algernon.vespera.ledger;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * The single record of file occurrence identity, verdicts and run identity (see CONTEXT.md's
 * "Ledger"). What exists so far: a walk's identity and the file occurrences recorded against it.
 */
@Component
public class Ledger {

    private final JdbcTemplate jdbcTemplate;

    public Ledger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Mints the identity a walk of {@code corpusRoot} is recorded under. */
    public WalkId startWalk(Path corpusRoot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO walk (corpus_root) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                    statement.setString(1, corpusRoot.toString());
                    return statement;
                },
                keyHolder);
        return new WalkId(keyHolder.getKey().longValue());
    }

    /** Records one file occurrence against {@code walkId}. */
    public void fileOccurrence(WalkId walkId, OccurrencePath path, long sizeInBytes, Instant lastModified) {
        jdbcTemplate.update(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified) VALUES (?, ?, ?, ?)",
                walkId.value(),
                path.value(),
                sizeInBytes,
                lastModified.toString());
    }

    /** The file occurrences recorded against {@code walkId}. */
    public List<RecordedOccurrence> occurrencesForWalk(WalkId walkId) {
        return jdbcTemplate.query(
                "SELECT path, size_bytes, last_modified FROM file_occurrence WHERE walk_id = ?",
                (resultSet, rowNumber) -> new RecordedOccurrence(
                        new OccurrencePath(resultSet.getString("path")),
                        resultSet.getLong("size_bytes"),
                        Instant.parse(resultSet.getString("last_modified"))),
                walkId.value());
    }
}
