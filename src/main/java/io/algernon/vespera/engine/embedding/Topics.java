package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of the topics that stand, their passages and the seeds feeding none (ADR-409 section 2),
 * behind this class and nothing else querying {@code topic}, {@code topic_passage} or {@code seed_feeding_no_topic}
 * (ADR-041).
 *
 * <p><b>Written once, by the scoring step, under the scoring run</b>, once the breadth floor is applied and before
 * the first survivor is scored (ADR-409 section 5), and read by the arrangement step, which finds none again: a
 * topic is built from its rows, its passages in visiting order. A topic is written as its key, the seed path and the
 * chunk index of its first passage (ADR-409 section 1).
 *
 * <p>Nothing here sorts: a run's topics are read in key order by the table's primary key, and a topic's passages in
 * visiting order by the index on the run and the topic (ADR-409 section 12).
 */
@Component
public class Topics {

    private final JdbcTemplate jdbcTemplate;

    public Topics(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records the topics that stand under {@code runId}, each with its passages, and the seeds feeding none.
     */
    public void record(RunId runId, StandingTopics standing) {
        for (Topic topic : standing.topics()) {
            SeedPassage first = topic.key().firstPassage();
            jdbcTemplate.update(
                    "INSERT INTO topic (run_id, seed_path, chunk_index, passage_count) VALUES (?, ?, ?, ?)",
                    runId.value(),
                    first.seedPath(),
                    first.chunkIndex(),
                    topic.passages().size());
            for (SeedPassage passage : topic.passages()) {
                jdbcTemplate.update(
                        "INSERT INTO topic_passage (run_id, seed_path, chunk_index, topic_seed_path,"
                                + " topic_chunk_index) VALUES (?, ?, ?, ?, ?)",
                        runId.value(),
                        passage.seedPath(),
                        passage.chunkIndex(),
                        first.seedPath(),
                        first.chunkIndex());
            }
        }
        for (String seedPath : standing.seedsFeedingNoTopic()) {
            jdbcTemplate.update(
                    "INSERT INTO seed_feeding_no_topic (run_id, seed_path) VALUES (?, ?)", runId.value(), seedPath);
        }
    }

    /**
     * The topics {@code runId} kept, in key order, each with its passages in visiting order (ADR-409 section 5): what
     * the arrangement step builds its {@link Topic}s from.
     */
    public List<Topic> standingUnder(RunId runId) {
        List<TopicKey> keys = jdbcTemplate.query(
                "SELECT seed_path, chunk_index FROM topic WHERE run_id = ? ORDER BY seed_path, chunk_index",
                (resultSet, rowNumber) -> new TopicKey(
                        new SeedPassage(resultSet.getString("seed_path"), resultSet.getInt("chunk_index"))),
                runId.value());
        List<Topic> topics = new ArrayList<>();
        for (TopicKey key : keys) {
            List<SeedPassage> passages = jdbcTemplate.query(
                    "SELECT seed_path, chunk_index FROM topic_passage WHERE run_id = ? AND topic_seed_path = ?"
                            + " AND topic_chunk_index = ? ORDER BY seed_path, chunk_index",
                    (resultSet, rowNumber) ->
                            new SeedPassage(resultSet.getString("seed_path"), resultSet.getInt("chunk_index")),
                    runId.value(),
                    key.firstPassage().seedPath(),
                    key.firstPassage().chunkIndex());
            topics.add(new Topic(key, passages));
        }
        return List.copyOf(topics);
    }

    /**
     * A topic the profile lists (ADR-435 section 4): its key, its name as written and its place in the list, counted
     * from 1.
     */
    public record ListedName(TopicKey key, String name, int place) {}

    /**
     * Records the topics the profile lists under {@code runId} (ADR-435 section 4): one {@code topic} row each, with no
     * passage, and one {@code listed_topic} row each. Called where {@link #record} is, before the first survivor is
     * scored, and with no {@code topic_passage} or {@code seed_feeding_no_topic} row.
     */
    public void recordListed(RunId runId, List<ListedName> listed) {
        for (ListedName topic : listed) {
            SeedPassage key = topic.key().firstPassage();
            jdbcTemplate.update(
                    "INSERT INTO topic (run_id, seed_path, chunk_index, passage_count) VALUES (?, ?, ?, 0)",
                    runId.value(),
                    key.seedPath(),
                    key.chunkIndex());
            jdbcTemplate.update(
                    "INSERT INTO listed_topic (run_id, topic_seed_path, topic_chunk_index, name, place)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    runId.value(),
                    key.seedPath(),
                    key.chunkIndex(),
                    topic.name(),
                    topic.place());
        }
    }

    /**
     * The topics the profile listed under {@code runId}, in the list's order, by the index of the run and the place,
     * sorting nothing; empty where the run's topics are inferred (ADR-435 section 4).
     */
    public List<ListedName> listedUnder(RunId runId) {
        return jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index, name, place FROM listed_topic WHERE run_id = ?"
                        + " ORDER BY place",
                (resultSet, rowNumber) -> new ListedName(
                        new TopicKey(new SeedPassage(
                                resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index"))),
                        resultSet.getString("name"),
                        resultSet.getInt("place")),
                runId.value());
    }

    /** One seed that feeds a topic and how many of the topic's passages are of it (ADR-416 section 2). */
    public record SeedFeed(String seedPath, int passages) {}

    /**
     * The topics {@code runId} kept, each with its passage count, in key order, by the primary key and sorting nothing
     * (ADR-416 section 8).
     */
    public Map<TopicKey, Integer> passageCountsUnder(RunId runId) {
        Map<TopicKey, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT seed_path, chunk_index, passage_count FROM topic WHERE run_id = ?"
                        + " ORDER BY seed_path, chunk_index",
                resultSet -> {
                    counts.put(
                            new TopicKey(new SeedPassage(
                                    resultSet.getString("seed_path"), resultSet.getInt("chunk_index"))),
                            resultSet.getInt("passage_count"));
                },
                runId.value());
        return counts;
    }

    /**
     * The seeds feeding {@code topic} under {@code runId}, each with how many of the topic's passages it gives, in the
     * order of ADR-399 section 4's paths: grouped on the seed by the index on the run and the topic, which sorts
     * nothing (ADR-416 section 8).
     */
    public List<SeedFeed> seedsOf(RunId runId, TopicKey topic) {
        List<SeedFeed> seeds = new ArrayList<>(jdbcTemplate.query(
                "SELECT seed_path, COUNT(*) AS passages FROM topic_passage WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ? GROUP BY seed_path",
                (resultSet, rowNumber) ->
                        new SeedFeed(resultSet.getString("seed_path"), resultSet.getInt("passages")),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex()));
        seeds.sort((left, right) -> SeedPassage.comparePaths(left.seedPath(), right.seedPath()));
        return seeds;
    }

    /** The seeds feeding no topic under {@code runId}, in the order of ADR-399 section 4's paths (ADR-401 section 4). */
    public List<String> seedsFeedingNoTopic(RunId runId) {
        List<String> paths = new ArrayList<>(jdbcTemplate.query(
                "SELECT seed_path FROM seed_feeding_no_topic WHERE run_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("seed_path"),
                runId.value()));
        paths.sort(SeedPassage::comparePaths);
        return paths;
    }

    /**
     * Every seed passage, counted as the breadth floor counts it (ADR-401 section 1): the chunks of one seed of each
     * content, the first by path in ADR-399 section 4's order.
     */
    public static long seedPassagesOf(Map<String, SeedChunks> bySeedPath) {
        long passages = 0;
        Set<String> contents = new HashSet<>();
        for (String path : bySeedPath.keySet().stream().sorted(SeedPassage::comparePaths).toList()) {
            SeedChunks seed = bySeedPath.get(path);
            if (contents.add(seed.contentHash())) {
                passages += seed.chunkCount();
            }
        }
        return passages;
    }

    /**
     * Deletes every row recorded under {@code runId}, the discard half of ADR-115 and ADR-116, with the step's other
     * rows (ADR-409 section 5).
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM listed_topic WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM topic_passage WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM topic WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM seed_feeding_no_topic WHERE run_id = ?", runId.value());
    }
}
