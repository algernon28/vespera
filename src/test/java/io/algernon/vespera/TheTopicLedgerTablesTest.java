package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;

/**
 * The tables ADR-409 keeps the topics in, and what a row of each is (§1 to §4): read off the shipped {@code schema.sql}
 * applied to a database in memory, by SQLite's own account of each table's columns, their types, which may be null
 * and the place of each in the primary key, in the order the record lists them; and by the line above each table that
 * names its owner (ADR-209 §3).
 *
 * <p>A topic is written as two columns, the seed path and the chunk index of its first passage, and never as an
 * occurrence id; a subtopic as its number within its topic, or -1 where the topic has none. Every table is new, so that
 * a database an earlier build wrote opens with its old tables kept and unread, and no schema version moves (§11).
 *
 * <p>What a row of each column means, and which step writes it, are held by the invocation tests that read them.
 */
@Epic("Architecture")
@Feature("What the database keeps")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class TheTopicLedgerTablesTest {

    /** The line that says whose a table is, which stands directly above the table's {@code CREATE TABLE}. */
    private static final Pattern OWNED_TABLE =
            Pattern.compile("^-- owner: ([a-z]+)\\R^CREATE TABLE IF NOT EXISTS ([a-z_]+) \\(", Pattern.MULTILINE);

    /** The five tables keyed by a winning seed, which a database an earlier build wrote holds and the schema no longer creates. */
    private static final List<String> THE_FIVE_KEYED_BY_A_WINNING_SEED =
            List.of("document_cluster", "cluster", "synthesis_doc", "call_exemplar", "cluster_fault");

    /**
     * The five tables as an earlier build created them, and their eight indexes, word for word as its {@code schema.sql}
     * had them, without the comments.
     */
    private static final String THE_FIVE_AS_AN_EARLIER_BUILD_CREATED_THEM = """
            CREATE TABLE IF NOT EXISTS schema_version (module TEXT PRIMARY KEY, version INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS document_cluster (
                occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                run_id TEXT NOT NULL REFERENCES run (id),
                winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                cluster_ordinal INTEGER NOT NULL,
                PRIMARY KEY (occurrence_id, run_id)
            );
            CREATE INDEX IF NOT EXISTS document_cluster_by_run_id ON document_cluster (run_id);
            CREATE INDEX IF NOT EXISTS document_cluster_by_winning_seed_occurrence_id ON document_cluster (winning_seed_occurrence_id);
            CREATE INDEX IF NOT EXISTS document_cluster_by_winning_seed_run_and_cluster ON document_cluster (winning_seed_occurrence_id, run_id, cluster_ordinal);
            CREATE TABLE IF NOT EXISTS cluster (
                run_id TEXT NOT NULL REFERENCES run (id),
                winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                cluster_ordinal INTEGER NOT NULL,
                label TEXT NOT NULL,
                document_count INTEGER NOT NULL,
                partition_order INTEGER NOT NULL,
                cluster_order INTEGER NOT NULL,
                PRIMARY KEY (run_id, winning_seed_occurrence_id, cluster_ordinal)
            );
            CREATE INDEX IF NOT EXISTS cluster_by_winning_seed_occurrence_id ON cluster (winning_seed_occurrence_id);
            CREATE TABLE IF NOT EXISTS synthesis_doc (
                run_id TEXT NOT NULL REFERENCES run (id),
                winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                cluster_ordinal INTEGER NOT NULL,
                title TEXT NOT NULL,
                prose TEXT NOT NULL,
                PRIMARY KEY (run_id, winning_seed_occurrence_id, cluster_ordinal)
            );
            CREATE INDEX IF NOT EXISTS synthesis_doc_by_winning_seed_occurrence_id ON synthesis_doc (winning_seed_occurrence_id);
            CREATE TABLE IF NOT EXISTS call_exemplar (
                run_id TEXT NOT NULL REFERENCES run (id),
                winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                cluster_ordinal INTEGER NOT NULL,
                citation_ordinal INTEGER NOT NULL,
                occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                PRIMARY KEY (run_id, winning_seed_occurrence_id, cluster_ordinal, citation_ordinal),
                UNIQUE (run_id, winning_seed_occurrence_id, cluster_ordinal, occurrence_id)
            );
            CREATE INDEX IF NOT EXISTS call_exemplar_by_winning_seed_occurrence_id ON call_exemplar (winning_seed_occurrence_id);
            CREATE INDEX IF NOT EXISTS call_exemplar_by_occurrence_id ON call_exemplar (occurrence_id);
            CREATE TABLE IF NOT EXISTS cluster_fault (
                run_id TEXT NOT NULL REFERENCES run (id),
                winning_seed_occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
                cluster_ordinal INTEGER NOT NULL,
                kind TEXT NOT NULL,
                detail TEXT NOT NULL,
                PRIMARY KEY (run_id, winning_seed_occurrence_id, cluster_ordinal)
            );
            CREATE INDEX IF NOT EXISTS cluster_fault_by_winning_seed_occurrence_id ON cluster_fault (winning_seed_occurrence_id);
            INSERT INTO schema_version (module, version) VALUES ('embedding', 7), ('synthesis', 4);
            INSERT INTO document_cluster VALUES (1, 'an earlier scoring run', 2, 0);
            INSERT INTO cluster VALUES ('an earlier arrangement', 2, 0, 'a label', 1, 1, 0);
            INSERT INTO synthesis_doc VALUES ('an earlier generation', 2, 0, 'a title', 'some prose');
            INSERT INTO call_exemplar VALUES ('an earlier generation', 2, 0, 1, 1);
            INSERT INTO cluster_fault VALUES ('an earlier generation', 2, 0, 'SCHEMA_VIOLATION', 'a detail');
            """;

    /** The five columns a cluster is keyed by under a run, each the key's place given. */
    private static final List<String> A_CLUSTERS_KEY = List.of(
            "run_id TEXT NOT NULL key 1",
            "topic_seed_path TEXT NOT NULL key 2",
            "topic_chunk_index INTEGER NOT NULL key 3",
            "subtopic_ordinal INTEGER NOT NULL key 4",
            "cluster_ordinal INTEGER NOT NULL key 5");

    /** The three columns a topic is keyed by under a run. */
    private static final List<String> A_TOPICS_KEY = List.of(
            "run_id TEXT NOT NULL key 1", "topic_seed_path TEXT NOT NULL key 2", "topic_chunk_index INTEGER NOT NULL key 3");

    private static Connection database;

    private static Map<String, String> owners;

    @BeforeAll
    static void theShippedSchemaInMemory() throws Exception {
        database = DriverManager.getConnection("jdbc:sqlite::memory:");
        ScriptUtils.executeSqlScript(database, new ClassPathResource("schema.sql"));
        owners = new TreeMap<>();
        Matcher owned = OWNED_TABLE.matcher(schema());
        while (owned.find()) {
            owners.put(owned.group(2), owned.group(1));
        }
    }

    @AfterAll
    static void closeIt() throws SQLException {
        database.close();
    }

    @Test
    @Story("The topics and what is arranged under them are on record")
    @DisplayName("Stage 5 keeps the topics that stand, their passages, the seeds feeding none, and each document's topic and the topics it fits")
    void stageFivesTablesKeepTheTopicsTheirPassagesAndEachDocumentsPlace() throws SQLException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("topic", List.of(
                "run_id TEXT NOT NULL key 1",
                "seed_path TEXT NOT NULL key 2",
                "chunk_index INTEGER NOT NULL key 3",
                "passage_count INTEGER NOT NULL"));
        expected.put("topic_passage", List.of(
                "run_id TEXT NOT NULL key 1",
                "seed_path TEXT NOT NULL key 2",
                "chunk_index INTEGER NOT NULL key 3",
                "topic_seed_path TEXT NOT NULL",
                "topic_chunk_index INTEGER NOT NULL"));
        expected.put("seed_feeding_no_topic", List.of("run_id TEXT NOT NULL key 1", "seed_path TEXT NOT NULL key 2"));
        expected.put("winning_topic", List.of(
                "occurrence_id INTEGER NOT NULL key 1",
                "run_id TEXT NOT NULL key 2",
                "topic_seed_path TEXT NOT NULL",
                "topic_chunk_index INTEGER NOT NULL"));
        expected.put("see_also", List.of(
                "occurrence_id INTEGER NOT NULL key 1",
                "run_id TEXT NOT NULL key 2",
                "topic_seed_path TEXT NOT NULL key 3",
                "topic_chunk_index INTEGER NOT NULL key 4"));

        claim("each of stage 5's five tables has these columns, in this order: a topic is its first passage's seed path"
                        + " and chunk index, a passage its seed's path and chunk index and never an occurrence alone, and"
                        + " a document's winning topic is one row under the key its score is kept under, where a topic"
                        + " it fits is one row of each",
                () -> assertThat(columnsOf(expected.keySet())).isEqualTo(expected));
        claim("and every one of them is the embedding module's",
                () -> assertThat(ownersOf(expected.keySet())).containsOnly("embedding"));
    }

    @Test
    @Story("The topics and what is arranged under them are on record")
    @DisplayName("A cluster is keyed by the run, the topic, the subtopic and its number, where it was keyed by the seed a document matched")
    void theClusterTablesAreKeyedByTheTopicTheSubtopicAndTheOrdinal() throws SQLException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("cluster_member", List.of(
                "occurrence_id INTEGER NOT NULL key 1",
                "run_id TEXT NOT NULL key 2",
                "topic_seed_path TEXT NOT NULL",
                "topic_chunk_index INTEGER NOT NULL",
                "subtopic_ordinal INTEGER NOT NULL",
                "cluster_ordinal INTEGER NOT NULL"));
        expected.put("arranged_cluster", with(A_CLUSTERS_KEY,
                "label TEXT NOT NULL",
                "document_count INTEGER NOT NULL",
                "topic_order INTEGER NOT NULL",
                "subtopic_order INTEGER NOT NULL",
                "cluster_order INTEGER NOT NULL"));
        expected.put("cluster_synthesis_doc", with(A_CLUSTERS_KEY, "title TEXT NOT NULL", "prose TEXT NOT NULL"));
        expected.put("cluster_call_exemplar", with(A_CLUSTERS_KEY,
                "citation_ordinal INTEGER NOT NULL key 6", "occurrence_id INTEGER NOT NULL"));
        expected.put("standing_cluster_fault", with(A_CLUSTERS_KEY, "kind TEXT NOT NULL", "detail TEXT NOT NULL"));

        claim("each of the five tables a cluster is kept in has these columns, in this order: the topic's key, the"
                        + " subtopic and the cluster's number within it, and no column naming a seed",
                () -> assertThat(columnsOf(expected.keySet())).isEqualTo(expected));
        claim("one document is listed once among what one call for a cluster sent, by a uniqueness over the cluster's"
                        + " five key columns and the document, so that no citation of it can be given twice",
                () -> assertThat(uniqueColumnsOf("cluster_call_exemplar")).containsExactly(List.of(
                        "run_id", "topic_seed_path", "topic_chunk_index", "subtopic_ordinal", "cluster_ordinal",
                        "occurrence_id")));
        claim("which documents share a cluster is the embedding module's, and the rest the synthesis module's, as the"
                        + " tables they take the place of were",
                () -> assertThat(ownersOf(expected.keySet())).containsExactly(
                        "embedding", "synthesis", "synthesis", "synthesis", "synthesis"));
    }

    @Test
    @Story("The topics and what is arranged under them are on record")
    @DisplayName("The names of the topics and subtopics, and the topics' overviews, are keyed by the topic")
    void theNamesAndTheOverviewsAreKeyedByTheTopic() throws SQLException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("topic_name", with(A_TOPICS_KEY, "name TEXT NOT NULL", "origin TEXT NOT NULL", "turn_down TEXT"));
        expected.put("subtopic_name", with(A_TOPICS_KEY,
                "subtopic_ordinal INTEGER NOT NULL key 4", "name TEXT NOT NULL", "origin TEXT NOT NULL", "turn_down TEXT"));
        expected.put("topic_overview", with(A_TOPICS_KEY, "writing TEXT NOT NULL", "asked INTEGER NOT NULL"));
        expected.put("topic_overview_source", with(A_TOPICS_KEY,
                "number INTEGER NOT NULL key 4", "subtopic_ordinal INTEGER NOT NULL", "cluster_ordinal INTEGER NOT NULL"));
        expected.put("topic_overview_fault", with(A_TOPICS_KEY,
                "kind TEXT NOT NULL", "detail TEXT NOT NULL", "asked INTEGER NOT NULL"));

        claim("each of the five tables has these columns, in this order: a name with where it came from and, where it"
                        + " fell back, why; an overview with the asking it was believed at, each number it cited by the"
                        + " cluster at that number, and a turned-down overview's fault",
                () -> assertThat(columnsOf(expected.keySet())).isEqualTo(expected));
        claim("and every one of them is the synthesis module's",
                () -> assertThat(ownersOf(expected.keySet())).containsOnly("synthesis"));
    }

    @Test
    @Story("The topics and what is arranged under them are on record")
    @DisplayName("Each read of one topic's rows seeks them by an index led by the run and the topic")
    void theReadsOfOneTopicSeekByAnIndexLedByTheRunAndTheTopic() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("topic_passage_by_run_and_topic",
                "topic_passage (run_id, topic_seed_path, topic_chunk_index, seed_path, chunk_index)");
        expected.put("winning_topic_by_run_topic_and_occurrence",
                "winning_topic (run_id, topic_seed_path, topic_chunk_index, occurrence_id)");
        expected.put("see_also_by_run_topic_and_occurrence",
                "see_also (run_id, topic_seed_path, topic_chunk_index, occurrence_id)");
        expected.put("cluster_member_by_run_topic_subtopic_and_cluster",
                "cluster_member (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal)");
        expected.put("cluster_call_exemplar_by_occurrence_id", "cluster_call_exemplar (occurrence_id)");

        Map<String, String> found = new LinkedHashMap<>();
        for (String index : expected.keySet()) {
            found.put(index, indexed(index));
        }
        claim("the five indexes are on these tables, over these columns in this order: a topic's passages in visiting"
                        + " order, its members and its links in occurrence order, its clusters' members in subtopic and"
                        + " cluster order, each under one run; and what one call sent, by the document it names",
                () -> assertThat(found).isEqualTo(expected));
    }

    @Test
    @Story("The topics and what is arranged under them are on record")
    @DisplayName("A subtopic below none, a fallback name with no reason and a written name with one are refused")
    void aSubtopicBelowNoneAndAFallbackWithNoTurnDownAreRefused() throws SQLException {
        Map<String, String> aRowAtASubtopic = new LinkedHashMap<>();
        aRowAtASubtopic.put("cluster_member",
                "INSERT INTO cluster_member VALUES (1, 'a run at subtopic %1$d', 'seed.txt', 0, %1$d, 0)");
        aRowAtASubtopic.put("arranged_cluster",
                "INSERT INTO arranged_cluster VALUES ('a run at subtopic %1$d', 'seed.txt', 0, %1$d, 0, 'A label', 1, 1, 0, 1)");
        aRowAtASubtopic.put("cluster_synthesis_doc",
                "INSERT INTO cluster_synthesis_doc VALUES ('a run at subtopic %1$d', 'seed.txt', 0, %1$d, 0, 'A title', 'Prose')");
        aRowAtASubtopic.put("cluster_call_exemplar",
                "INSERT INTO cluster_call_exemplar VALUES ('a run at subtopic %1$d', 'seed.txt', 0, %1$d, 0, 1, 1)");
        aRowAtASubtopic.put("standing_cluster_fault",
                "INSERT INTO standing_cluster_fault VALUES ('a run at subtopic %1$d', 'seed.txt', 0, %1$d, 0, 'SCHEMA_VIOLATION', 'A detail')");
        aRowAtASubtopic.put("topic_overview_source",
                "INSERT INTO topic_overview_source VALUES ('a run at subtopic %1$d', 'seed.txt', 0, 1, %1$d, 0)");

        Map<String, List<String>> atNoneAndBelow = new LinkedHashMap<>();
        for (Map.Entry<String, String> table : aRowAtASubtopic.entrySet()) {
            atNoneAndBelow.put(table.getKey(), List.of(
                    "-1 " + keptOrRefused(table.getValue().formatted(-1)),
                    "-2 " + keptOrRefused(table.getValue().formatted(-2))));
        }
        List<String> aSubtopicsName = List.of(
                "0 " + keptOrRefused("INSERT INTO subtopic_name VALUES ('a run', 'seed.txt', 0, 0, 'A name', 'WRITTEN', NULL)"),
                "-1 " + keptOrRefused("INSERT INTO subtopic_name VALUES ('a run', 'seed.txt', 0, -1, 'A name', 'WRITTEN', NULL)"));
        Map<String, List<String>> namesByOriginAndTurnDown = new LinkedHashMap<>();
        namesByOriginAndTurnDown.put("topic_name", List.of(
                "fallback, a turn-down " + keptOrRefused(
                        "INSERT INTO topic_name VALUES ('a run', 'first.txt', 0, 'A name', 'FALLBACK', 'EMPTY')"),
                "fallback, none " + keptOrRefused(
                        "INSERT INTO topic_name VALUES ('a run', 'second.txt', 0, 'A name', 'FALLBACK', NULL)"),
                "written, none " + keptOrRefused(
                        "INSERT INTO topic_name VALUES ('a run', 'third.txt', 0, 'A name', 'WRITTEN', NULL)"),
                "written, a turn-down " + keptOrRefused(
                        "INSERT INTO topic_name VALUES ('a run', 'fourth.txt', 0, 'A name', 'WRITTEN', 'EMPTY')")));
        namesByOriginAndTurnDown.put("subtopic_name", List.of(
                "fallback, a turn-down " + keptOrRefused(
                        "INSERT INTO subtopic_name VALUES ('a run', 'first.txt', 0, 0, 'A name', 'FALLBACK', 'EMPTY')"),
                "fallback, none " + keptOrRefused(
                        "INSERT INTO subtopic_name VALUES ('a run', 'second.txt', 0, 0, 'A name', 'FALLBACK', NULL)"),
                "written, none " + keptOrRefused(
                        "INSERT INTO subtopic_name VALUES ('a run', 'third.txt', 0, 0, 'A name', 'WRITTEN', NULL)"),
                "written, a turn-down " + keptOrRefused(
                        "INSERT INTO subtopic_name VALUES ('a run', 'fourth.txt', 0, 0, 'A name', 'WRITTEN', 'EMPTY')")));

        claim("on each of the six tables a cluster's subtopic is kept in, the five of the clusters and the overview's"
                        + " sources, a cluster of a topic with no subtopics, -1, is kept, and a subtopic of -2 is refused by"
                        + " the table's check",
                () -> assertThat(atNoneAndBelow).containsOnlyKeys(aRowAtASubtopic.keySet())
                        .allSatisfy((table, outcomes) -> assertThat(outcomes)
                                .as("what %s does with a subtopic of -1 and of -2", table)
                                .containsExactly("-1 kept", "-2 refused")));
        claim("a subtopic's name is kept for a subtopic that exists, 0, and refused for none, -1",
                () -> assertThat(aSubtopicsName).containsExactly("0 kept", "-1 refused"));
        claim("on both tables of names, a name that fell back is kept with the reason it fell back and refused with none,"
                        + " and a name the model wrote is kept with no reason and refused with one",
                () -> assertThat(namesByOriginAndTurnDown).allSatisfy((table, outcomes) -> assertThat(outcomes)
                        .as("what %s does with each origin, with and without a turn-down", table)
                        .containsExactly(
                                "fallback, a turn-down kept",
                                "fallback, none refused",
                                "written, none kept",
                                "written, a turn-down refused")));
    }

    /**
     * {@code kept} where the table takes the row, {@code refused} where its check refuses it; any other failure, a
     * table or a column the schema lacks among them, is the statement's own and is thrown.
     */
    private static String keptOrRefused(String statement) throws SQLException {
        try {
            insert(statement);
            return "kept";
        } catch (SQLException refusal) {
            if (String.valueOf(refusal.getMessage()).contains("CHECK constraint failed")) {
                return "refused";
            }
            throw refusal;
        }
    }

    @Test
    @Story("A working directory an earlier build used opens as it is")
    @DisplayName("A database an earlier build wrote opens, keeping its five old tables and their rows, and a new one has none of them")
    void aDatabaseAnEarlierBuildWroteOpensKeepingItsOldTablesUnread() throws Exception {
        List<String> theFifteen = List.of(
                "topic", "topic_passage", "seed_feeding_no_topic", "winning_topic", "see_also", "cluster_member",
                "arranged_cluster", "cluster_synthesis_doc", "cluster_call_exemplar", "standing_cluster_fault",
                "topic_name", "subtopic_name", "topic_overview", "topic_overview_source", "topic_overview_fault");
        try (Connection earlier = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ScriptUtils.executeSqlScript(earlier, new ByteArrayResource(
                    THE_FIVE_AS_AN_EARLIER_BUILD_CREATED_THEM.getBytes(StandardCharsets.UTF_8)));

            ScriptUtils.executeSqlScript(earlier, new ClassPathResource("schema.sql"));

            Map<String, Long> rowsKept = new LinkedHashMap<>();
            for (String table : THE_FIVE_KEYED_BY_A_WINNING_SEED) {
                rowsKept.put(table, count(earlier, "SELECT COUNT(*) FROM " + table));
            }
            claim("the shipped schema applies over a database holding the five tables keyed by a winning seed, and"
                            + " each keeps its one row: nothing drops them",
                    () -> assertThat(rowsKept).containsOnly(
                            Map.entry("document_cluster", 1L), Map.entry("cluster", 1L), Map.entry("synthesis_doc", 1L),
                            Map.entry("call_exemplar", 1L), Map.entry("cluster_fault", 1L)));
            claim("the fifteen tables of the topics are created beside them",
                    () -> assertThat(tablesAmong(earlier, theFifteen)).containsExactlyInAnyOrderElementsOf(theFifteen));
            Map<String, Integer> expected = Map.of(
                    "embedding", expectedVersion("io.algernon.vespera.engine.embedding.EmbeddingSchema"),
                    "synthesis", expectedVersion("io.algernon.vespera.engine.synthesis.SynthesisSchema"));
            claim("and the versions the earlier build recorded, embedding at 7 and synthesis at 4, are the ones the"
                            + " shipped modules expect, no table that exists having changed its shape, so the database"
                            + " is not refused",
                    () -> assertThat(versions(earlier)).isEqualTo(expected));
        }
        claim("a new database is given none of the five",
                () -> assertThat(tablesAmong(database, THE_FIVE_KEYED_BY_A_WINNING_SEED)).isEmpty());
    }

    private static List<String> with(List<String> key, String... rest) {
        List<String> columns = new ArrayList<>(key);
        columns.addAll(List.of(rest));
        return columns;
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

    /** The columns of each uniqueness a table's own definition declares, apart from its primary key. */
    private static List<List<String>> uniqueColumnsOf(String table) throws SQLException {
        List<String> uniques = new ArrayList<>();
        try (Statement statement = database.createStatement();
                ResultSet indexes = statement.executeQuery("PRAGMA index_list(" + table + ")")) {
            while (indexes.next()) {
                if ("u".equals(indexes.getString("origin"))) {
                    uniques.add(indexes.getString("name"));
                }
            }
        }
        List<List<String>> columns = new ArrayList<>();
        for (String unique : uniques) {
            columns.add(indexColumns(unique));
        }
        return columns;
    }

    /** {@code table (column, column)} for an index, or {@code (none)} where the schema builds no index of that name. */
    private static String indexed(String index) throws SQLException {
        String table;
        try (PreparedStatement statement =
                database.prepareStatement("SELECT tbl_name FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            statement.setString(1, index);
            try (ResultSet found = statement.executeQuery()) {
                if (!found.next()) {
                    return "(none)";
                }
                table = found.getString(1);
            }
        }
        return table + " (" + String.join(", ", indexColumns(index)) + ")";
    }

    private static List<String> indexColumns(String index) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = database.createStatement();
                ResultSet info = statement.executeQuery("PRAGMA index_info(" + index + ")")) {
            while (info.next()) {
                columns.add(info.getString("name"));
            }
        }
        return columns;
    }

    private static List<String> ownersOf(Iterable<String> tables) {
        List<String> found = new ArrayList<>();
        for (String table : tables) {
            found.add(owners.getOrDefault(table, "(no owner line)"));
        }
        return found;
    }

    private static void insert(String statement) throws SQLException {
        try (Statement insert = database.createStatement()) {
            insert.execute(statement);
        }
    }

    private static long count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static List<String> tablesAmong(Connection connection, List<String> names) throws SQLException {
        List<String> found = new ArrayList<>();
        for (String name : names) {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, name);
                try (ResultSet table = statement.executeQuery()) {
                    if (table.next()) {
                        found.add(name);
                    }
                }
            }
        }
        return found;
    }

    private static Map<String, Integer> versions(Connection connection) throws SQLException {
        Map<String, Integer> versions = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT module, version FROM schema_version WHERE module IN ('embedding', 'synthesis') ORDER BY module")) {
            while (rows.next()) {
                versions.put(rows.getString("module"), rows.getInt("version"));
            }
        }
        return versions;
    }

    /**
     * The version a module's schema class expects, read by reflection because the class and its constant are its
     * module's own and not public.
     */
    private static int expectedVersion(String schemaClass) throws ReflectiveOperationException {
        java.lang.reflect.Field version = Class.forName(schemaClass).getDeclaredField("VERSION");
        version.setAccessible(true);
        return version.getInt(null);
    }

    private static String schema() throws Exception {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }
}
