package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.ledger.RunId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

/**
 * A tree's ledger: the ledger's rows of the walk and of the runs a tree was drawn from, and the rows its topics,
 * clusters and writing were drawn from, in one SQLite file at the tree's top (ADR-429).
 *
 * <p>It is written once {@link Deliverable#writeTo} has returned the tree, so the marker is already there, to a part
 * file beside its place and moved over any file of its name by {@link ReplacingMove}, whole at every drawing. It is in
 * SQLite's rollback journal and closed when written, so nothing lies beside it.
 *
 * <p>The rows are read through the application's own connection, one statement per table, row by row through a
 * callback and written as they come, a thousand to a transaction: no class holds a table's rows. Every statement it
 * runs against the working store selects, and none attaches, vacuums into or takes the working store's file, so it
 * holds whatever engine the working store is. It names the twenty-four tables of ADR-429 section 3, as the one
 * exception, beside ADR-198's, to ADR-209 section 3: {@code deliverable} owns no table and no stage's version names
 * it, so writing the file moves no run.
 */
public final class TreeLedger {

    /** What the file is called, directly at the tree's top. */
    public static final String FILE_NAME = "vespera-run.db";

    /** How many rows are written in one transaction. */
    private static final int ROWS_PER_TRANSACTION = 1000;

    /** The one table of a walk, chosen by the walk's id. */
    private static final String THE_WALK = "walk";

    /** The one table of the runs, chosen by the runs' ids. */
    private static final String THE_RUNS = "run";

    /** The tables whose rows are the walk's, chosen by the walk's id. */
    private static final List<String> BY_THE_WALK =
            List.of("walk_folder", "walk_folder_not_walked", "walk_folder_not_entered", "file_occurrence");

    /** The tables whose rows lie under the runs the tree was drawn from, chosen by the run's id. */
    private static final List<String> BY_THE_RUNS = List.of(
            "run_upstream",
            "finished_step",
            "verdict",
            "topic",
            "topic_passage",
            "seed_feeding_no_topic",
            "winning_topic",
            "see_also",
            "cluster_member",
            "arranged_cluster",
            "topic_name",
            "subtopic_name",
            "cluster_synthesis_doc",
            "cluster_call_exemplar",
            "standing_cluster_fault",
            "topic_overview",
            "topic_overview_source",
            "topic_overview_fault");

    /** The table of which the generation run's own rows are left out. */
    private static final String WITHOUT_THE_GENERATION_RUNS_OWN_ROWS = "finished_step";

    /** The runs the tree was drawn from: the generation run and every run reached through its upstream runs. */
    private static final String THE_RUNS_DRAWN_FROM = "WITH RECURSIVE drawn_from(id) AS (SELECT ? UNION"
            + " SELECT run_upstream.upstream_run_id FROM run_upstream"
            + " JOIN drawn_from ON run_upstream.run_id = drawn_from.id) SELECT id FROM drawn_from";

    /** The walk the generation run is over. */
    private static final String THE_WALK_OF_A_RUN = "SELECT walk_id FROM run WHERE id = ?";

    private static final String START_OF_A_TABLES_STATEMENT = "CREATE TABLE IF NOT EXISTS ";

    private static final String END_OF_A_TABLES_STATEMENT = "\n);";

    private final JdbcTemplate workingStore;

    /** Built from the working store's {@link JdbcTemplate}, the only thing it reads. */
    public TreeLedger(JdbcTemplate workingStore) {
        this.workingStore = workingStore;
    }

    /**
     * Writes the tree's ledger at the top of {@code tree}, over the one there, and returns its path.
     *
     * @throws UncheckedIOException when the file cannot be written or moved into place, which fails the drawing
     */
    public Path writeInto(Path tree, RunId generationRun) {
        Path place = tree.resolve(FILE_NAME);
        Path part = tree.resolve(FILE_NAME + Deliverable.PART_SUFFIX);
        try {
            Files.deleteIfExists(part);
            String schema = StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
            long walk = workingStore.queryForObject(THE_WALK_OF_A_RUN, Long.class, generationRun.value());
            List<String> runs = workingStore.queryForList(THE_RUNS_DRAWN_FROM, String.class, generationRun.value());
            String inTheRuns = "(" + String.join(", ", Collections.nCopies(runs.size(), "?")) + ")";
            try (Connection file = DriverManager.getConnection("jdbc:sqlite:" + part.toUri())) {
                file.setAutoCommit(true);
                try (Statement pragma = file.createStatement()) {
                    pragma.execute("PRAGMA journal_mode = DELETE");
                    pragma.execute("PRAGMA foreign_keys = OFF");
                }
                file.setAutoCommit(false);
                writeTable(file, schema, THE_WALK, "SELECT * FROM " + THE_WALK + " WHERE id = ?", new Object[] {walk});
                writeTable(file, schema, THE_RUNS, "SELECT * FROM " + THE_RUNS + " WHERE id IN " + inTheRuns, runs.toArray());
                for (String table : BY_THE_WALK) {
                    writeTable(file, schema, table, "SELECT * FROM " + table + " WHERE walk_id = ?", new Object[] {walk});
                }
                for (String table : BY_THE_RUNS) {
                    if (table.equals(WITHOUT_THE_GENERATION_RUNS_OWN_ROWS)) {
                        // The generation run's own finished step is recorded after the tree is drawn (ADR-429 section 3).
                        List<Object> arguments = new ArrayList<>(runs);
                        arguments.add(generationRun.value());
                        writeTable(file, schema, table,
                                "SELECT * FROM " + table + " WHERE run_id IN " + inTheRuns + " AND run_id <> ?",
                                arguments.toArray());
                    } else {
                        writeTable(file, schema, table, "SELECT * FROM " + table + " WHERE run_id IN " + inTheRuns, runs.toArray());
                    }
                }
            }
            ReplacingMove.ATOMIC.intoPlace(part, place);
            return place;
        } catch (IOException | SQLException | RuntimeException failure) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // the failure to write the file is what is said
            }
            if (failure instanceof UncheckedIOException unchecked) {
                throw unchecked;
            }
            throw new UncheckedIOException(
                    "The tree's ledger could not be written to " + place + ": " + failure.getMessage(),
                    failure instanceof IOException io ? io : new IOException(failure));
        }
    }

    /** Creates {@code table} in {@code file} as the schema does, then writes each row {@code select} reads into it. */
    private void writeTable(Connection file, String schema, String table, String select, Object[] arguments)
            throws SQLException {
        try (Statement create = file.createStatement()) {
            create.execute(creationOf(schema, table));
        }
        file.commit();
        TableWriter writer = new TableWriter(file, table);
        try {
            workingStore.query(
                    select,
                    statement -> {
                        for (int i = 0; i < arguments.length; i++) {
                            statement.setObject(i + 1, arguments[i]);
                        }
                    },
                    row -> {
                        try {
                            writer.write(row);
                        } catch (SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    });
            writer.finish();
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof SQLException sql) {
                throw sql;
            }
            throw e;
        } finally {
            writer.close();
        }
    }

    /** The statement the schema creates {@code table} by, so the table has the columns the working store's has. */
    private static String creationOf(String schema, String table) {
        int start = schema.indexOf(START_OF_A_TABLES_STATEMENT + table + " (");
        if (start < 0) {
            throw new IllegalStateException("schema.sql creates no table " + table);
        }
        int end = schema.indexOf(END_OF_A_TABLES_STATEMENT, start);
        return schema.substring(start, end + END_OF_A_TABLES_STATEMENT.length() - 1);
    }

    /** The rows of one table, written as they are read, a thousand to a transaction. */
    private static final class TableWriter {

        private final Connection file;

        private final String table;

        private PreparedStatement insert;

        private int columns;

        private int pending;

        TableWriter(Connection file, String table) {
            this.file = file;
            this.table = table;
        }

        void write(java.sql.ResultSet row) throws SQLException {
            if (insert == null) {
                ResultSetMetaData shape = row.getMetaData();
                columns = shape.getColumnCount();
                List<String> names = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    names.add("\"" + shape.getColumnName(i) + "\"");
                }
                insert = file.prepareStatement("INSERT INTO " + table + " (" + String.join(", ", names) + ") VALUES ("
                        + String.join(", ", Collections.nCopies(columns, "?")) + ")");
            }
            for (int i = 1; i <= columns; i++) {
                insert.setObject(i, row.getObject(i));
            }
            insert.executeUpdate();
            if (++pending == ROWS_PER_TRANSACTION) {
                file.commit();
                pending = 0;
            }
        }

        void finish() throws SQLException {
            file.commit();
            pending = 0;
        }

        void close() throws SQLException {
            if (insert != null) {
                insert.close();
            }
        }
    }
}
