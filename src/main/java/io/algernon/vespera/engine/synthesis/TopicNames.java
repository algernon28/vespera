package io.algernon.vespera.engine.synthesis;

import io.algernon.vespera.engine.ledger.RunId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code synthesis}'s record of the names given to topics and subtopics under an arrangement run (ADR-409 sections 4
 * and 6), behind this class and nothing else querying {@code topic_name} or {@code subtopic_name} (ADR-041).
 *
 * <p><b>A name is recorded as soon as it is given</b>, with its origin and, where it fell back, its turn-down, and
 * <b>a name recorded under the run is read back and never asked for again</b>, by this invocation or any later one
 * that arrives at the run. Nothing deletes from either table (ADR-409 section 5): a name once given is the one every
 * later reading of the run shows.
 */
@Component
public class TopicNames {

    private final JdbcTemplate jdbcTemplate;

    public TopicNames(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records the name given to {@code topic} under {@code arrangement}. */
    public void recordTopic(RunId arrangement, TopicKey topic, GivenName given) {
        jdbcTemplate.update(
                "INSERT INTO topic_name (run_id, topic_seed_path, topic_chunk_index, name, origin, turn_down)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                arrangement.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                given.name(),
                given.origin().name(),
                given.turnedDown().map(NameTurnDown::name).orElse(null));
    }

    /** The name recorded for {@code topic} under {@code arrangement}, or empty where none is. */
    public Optional<GivenName> ofTopic(RunId arrangement, TopicKey topic) {
        List<GivenName> found = jdbcTemplate.query(
                "SELECT name, origin, turn_down FROM topic_name WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ?",
                (resultSet, rowNumber) -> givenFrom(
                        resultSet.getString("name"), resultSet.getString("origin"), resultSet.getString("turn_down")),
                arrangement.value(),
                topic.seedPath(),
                topic.chunkIndex());
        return found.stream().findFirst();
    }

    /** Records the name given to subtopic {@code subtopic} of {@code topic} under {@code arrangement}. */
    public void recordSubtopic(RunId arrangement, TopicKey topic, int subtopic, GivenName given) {
        jdbcTemplate.update(
                "INSERT INTO subtopic_name (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, name,"
                        + " origin, turn_down) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrangement.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                subtopic,
                given.name(),
                given.origin().name(),
                given.turnedDown().map(NameTurnDown::name).orElse(null));
    }

    /** The name recorded for subtopic {@code subtopic} of {@code topic} under {@code arrangement}, or empty where none is. */
    public Optional<GivenName> ofSubtopic(RunId arrangement, TopicKey topic, int subtopic) {
        List<GivenName> found = jdbcTemplate.query(
                "SELECT name, origin, turn_down FROM subtopic_name WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ? AND subtopic_ordinal = ?",
                (resultSet, rowNumber) -> givenFrom(
                        resultSet.getString("name"), resultSet.getString("origin"), resultSet.getString("turn_down")),
                arrangement.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                subtopic);
        return found.stream().findFirst();
    }

    private static GivenName givenFrom(String name, String origin, String turnDown) {
        return new GivenName(
                name, NameOrigin.valueOf(origin), Optional.ofNullable(turnDown).map(NameTurnDown::valueOf));
    }
}
