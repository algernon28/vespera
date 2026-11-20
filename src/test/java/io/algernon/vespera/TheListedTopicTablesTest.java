package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;

/**
 * The two tables ADR-429 §4 adds under the embedding-scoring run, read off the shipped {@code schema.sql} applied to a
 * database in memory: {@code listed_topic}, a listed topic's name as written and its place in the list, keyed by the run
 * and the topic's two columns; and {@code winning_topic_score}, a scored document's score at its winning listed topic,
 * keyed as {@code winning_topic} is. Both are {@code embedding}'s; a place is counted from 1 and held once a run.
 *
 * <p>Which step writes them and what their rows hold under a run are the invocation tests' that read them.
 */
@Epic("Architecture")
@Feature("What the database keeps")
@Issue("898")
@Link(name = "ADR-429", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class TheListedTopicTablesTest {

    /** The line that says whose a table is, which stands directly above the table's {@code CREATE TABLE}. */
    private static final Pattern OWNED_TABLE =
            Pattern.compile("^-- owner: ([a-z]+)\\R^CREATE TABLE IF NOT EXISTS ([a-z_]+) \\(", Pattern.MULTILINE);

    /** A run, written as every row here names it; SQLite checks no reference here, its foreign keys being off. */
    private static final String A_RUN = "a scoring run";

    private static Connection database;

    private static Map<String, String> owners;

    @BeforeAll
    static void theShippedSchemaInMemory() throws Exception {
        database = DriverManager.getConnection("jdbc:sqlite::memory:");
        ScriptUtils.executeSqlScript(database, new ClassPathResource("schema.sql"));
        owners = new TreeMap<>();
        Matcher owned = OWNED_TABLE.matcher(
                StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8));
        while (owned.find()) {
            owners.put(owned.group(2), owned.group(1));
        }
    }

    @AfterAll
    static void closeIt() throws SQLException {
        database.close();
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("Stage 5 keeps each listed topic's name and place, and each document's score at the topic it was placed under")
    void theTwoTablesHaveTheirColumnsAndAreTheEmbeddingModules() throws SQLException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("listed_topic", List.of(
                "run_id TEXT NOT NULL key 1",
                "topic_seed_path TEXT NOT NULL key 2",
                "topic_chunk_index INTEGER NOT NULL key 3",
                "name TEXT NOT NULL",
                "place INTEGER NOT NULL"));
        expected.put("winning_topic_score", List.of(
                "occurrence_id INTEGER NOT NULL key 1",
                "run_id TEXT NOT NULL key 2",
                "score REAL NOT NULL"));

        claim("listed_topic is keyed by the run and the topic's two columns and holds the name and the place; and"
                        + " winning_topic_score is keyed by the document and the run, as its winning topic is, and holds"
                        + " the score",
                () -> assertThat(columnsOf(expected.keySet())).isEqualTo(expected));
        claim("and both are the embedding module's",
                () -> assertThat(List.of(owners.get("listed_topic"), owners.get("winning_topic_score")))
                        .containsExactly("embedding", "embedding"));
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("A listed topic's place is counted from 1, and no two topics of one run share a place")
    void aPlaceIsCountedFromOneAndHeldOnceARun() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.execute("INSERT INTO listed_topic VALUES ('" + A_RUN + "', 'pagamenti', -1, 'Pagamenti', 1)");
        }

        claim("a place of 0 is refused",
                () -> assertThatThrownBy(() -> insert("'incassi', -1, 'Incassi', 0")).isInstanceOf(SQLException.class));
        claim("and a second topic at the first's place under the same run is refused",
                () -> assertThatThrownBy(() -> insert("'incassi', -1, 'Incassi', 1")).isInstanceOf(SQLException.class));
        claim("where the second topic at place 2 is kept",
                () -> assertThat(insertedRows("'incassi', -1, 'Incassi', 2")).isEqualTo(1));
    }

    private static void insert(String afterTheRun) throws SQLException {
        insertedRows(afterTheRun);
    }

    private static int insertedRows(String afterTheRun) throws SQLException {
        try (Statement statement = database.createStatement()) {
            return statement.executeUpdate("INSERT INTO listed_topic VALUES ('" + A_RUN + "', " + afterTheRun + ")");
        }
    }

    /** Each table's columns as SQLite reports them: the name, the type, whether it may be null, and its place in the key. */
    private static Map<String, List<String>> columnsOf(Iterable<String> tables) throws SQLException {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (String table : tables) {
            List<String> described = new ArrayList<>();
            try (Statement statement = database.createStatement();
                    ResultSet info = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (info.next()) {
                    int key = info.getInt("pk");
                    described.add(info.getString("name") + " " + info.getString("type")
                            + (info.getInt("notnull") == 1 ? " NOT NULL" : "")
                            + (key > 0 ? " key " + key : ""));
                }
            }
            columns.put(table, described);
        }
        return columns;
    }
}
