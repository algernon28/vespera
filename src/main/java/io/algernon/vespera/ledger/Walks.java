package io.algernon.vespera.ledger;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** What the ledger records about a walk: the {@code walk} table (ADR-209 section 1). */
public class Walks {

    private final JdbcTemplate jdbcTemplate;

    public Walks(JdbcTemplate jdbcTemplate) {
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
     * Removes a walk and the file occurrences recorded beneath it (ADR-115), {@code file_occurrence}
     * first.
     *
     * <p>Only ever called on a traversal that observed what the previous one already recorded, so
     * what is deleted is a duplicate rather than a loss. Deleted rather than left standing because a
     * spare walk row is a second corpus nobody has, and everything downstream points at a walk.
     *
     * <p>The walk's anomalies are not deleted here: they are {@code corpus}'s, and with one still
     * standing the database refuses this (ADR-209 section 3.1). The caller deletes them first.
     */
    public void discardWalk(WalkId walkId) {
        jdbcTemplate.update("DELETE FROM file_occurrence WHERE walk_id = ?", walkId.value());
        jdbcTemplate.update("DELETE FROM walk WHERE id = ?", walkId.value());
    }
}
