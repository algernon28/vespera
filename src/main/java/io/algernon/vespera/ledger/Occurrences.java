package io.algernon.vespera.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the ledger records about the file occurrences a walk found: the {@code file_occurrence} table
 * (ADR-209 section 1).
 */
public class Occurrences {

    private final JdbcTemplate jdbcTemplate;

    public Occurrences(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
     * Every occurrence of {@code walkId}, in id order, a page of 1,000 at a time — the seed walk's own
     * pass (ADR-083).
     *
     * <p>Deliberately not {@link Verdicts#survivors}, and the difference is the decision rather than a
     * convenience. Survivorship is the absence of a blocking verdict, and no verdict is ever written
     * against a seed occurrence: every kind in the closed vocabulary exists to remove a document from
     * the survivor set, and a seed was never a candidate for it. Filtering seeds through the survivors
     * query would quietly make a seed folder subject to the corpus's own removals — a seed that happens
     * to be a byte-identical copy of another file would vanish from the seed set for a reason that has
     * nothing to do with seeds.
     *
     * <p>An {@code Iterable} that holds one page at a time and needs no closing (ADR-209 section 2); each
     * of its iterators starts again at the first page.
     */
    public Iterable<OccurrenceId> occurrencesOf(WalkId walkId) {
        return new KeysetPages<>(
                jdbcTemplate,
                "SELECT id FROM file_occurrence WHERE walk_id = ? AND id > ? ORDER BY id LIMIT "
                        + KeysetPages.ROWS_IN_A_PAGE,
                List.of(walkId.value()),
                List.of(Long.MIN_VALUE),
                (OccurrenceId last) -> List.of(last.value()),
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("id")));
    }
}
