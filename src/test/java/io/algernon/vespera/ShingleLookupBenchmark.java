package io.algernon.vespera;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The measurement behind ADR-182: what one stage-2 chunk costs to commit with {@code shingle_by_hash}
 * maintained row by row, against the same chunk with only an index on {@code run_id}, and what the
 * one-off statements that shape adds cost on their own -- dropping {@code shingle_by_hash}, building
 * {@code shingle_by_run_id} over a table that already has rows, and building {@code shingle_by_hash}
 * once, over the whole table, the way stage 4b does before it reads it. It also times stage 3's read
 * of one run's rows under each shape, because which index that read goes through changes with it.
 *
 * <p><b>Not a test, and never run by the build.</b> The name does not end in {@code Test}, so surefire
 * skips it. It is a {@code main} kept beside the tests so the measurement can be repeated on another
 * disk, or against a copy of a real database.
 *
 * <p><b>Never point it at a database a run is using.</b> It writes. Give it a copy taken while no
 * invocation has the working directory open, or let it build a synthetic one.
 *
 * <p>It sets the indexes itself rather than taking them from {@code schema.sql}, so the two shapes stay
 * comparable after {@code schema.sql} stops creating {@code shingle_by_hash}. A chunk is what {@link
 * JournalModeBenchmark} writes: 16 documents of {@code rows-per-document} random-hash shingle rows in one
 * transaction, inserted in batches of 5,000.
 *
 * <pre>
 * ./mvnw -q -o test-compile
 * java -cp "target/test-classes;target/classes;&lt;sqlite-jdbc jar&gt;" io.algernon.vespera.ShingleLookupBenchmark \
 *     &lt;scratch-dir&gt; [occurrences] [rows-per-document] [repeats] [journal modes]
 * </pre>
 *
 * <p>The synthetic base is the shipped schema with {@code shingle_by_hash} present while it is filled,
 * so its pages are as full as an index grown one insert at a time leaves them, the way stage 2 grew it
 * on every database written before ADR-182. A base already in the scratch directory is reused.
 */
public final class ShingleLookupBenchmark {

    private static final int DOCUMENTS_PER_CHUNK = 16;

    private static final int INSERT_BATCH = 5_000;

    private static final String RUN_ID = "2e82fc36ac5e3ec1128dc4a60bf31865b8f9bed031eaeff597aa43c1eb7d0075";

    private static final String PARAMETER_IDENTITY = "word:5";

    private static final String BY_HASH =
            "CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    private static final String BY_RUN_ID = "CREATE INDEX IF NOT EXISTS shingle_by_run_id ON shingle (run_id)";

    private static final String STAGE_3_READ =
            "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?";

    private ShingleLookupBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path scratch = Path.of(args[0]);
        int occurrences = args.length > 1 ? Integer.parseInt(args[1]) : 4_000;
        int rowsPerDocument = args.length > 2 ? Integer.parseInt(args[2]) : 2_500;
        int repeats = args.length > 3 ? Integer.parseInt(args[3]) : 10;
        String[] journalModes = (args.length > 4 ? args[4] : "WAL/NORMAL,DELETE/FULL").split(",");
        Files.createDirectories(scratch);

        Path base = scratch.resolve("base.db");
        if (!Files.exists(base)) {
            long start = System.nanoTime();
            buildSyntheticBase(base, occurrences, rowsPerDocument);
            System.out.printf(Locale.ROOT, "built base in %.1f s%n", (System.nanoTime() - start) / 1e9);
        }
        System.out.printf(Locale.ROOT, "base: %,d bytes, %,d shingle rows%n", Files.size(base),
                count(base, "SELECT count(*) FROM shingle"));

        List<String> summary = new ArrayList<>();
        for (String journalMode : journalModes) {
            String[] mode = journalMode.split("/");
            summary.add(measure(scratch, base, mode[0], mode[1], false, rowsPerDocument, repeats));
            summary.add(measure(scratch, base, mode[0], mode[1], true, rowsPerDocument, repeats));
        }
        System.out.println();
        summary.forEach(System.out::println);
    }

    private static String measure(Path scratch, Path base, String journalMode, String synchronous,
            boolean adr182, int rowsPerDocument, int repeats) throws IOException, SQLException {
        String label = journalMode + "/" + synchronous + (adr182 ? " run_id index, no by-hash" : " by-hash (before)");
        Path database = scratch.resolve("bench.db");
        for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
            Files.deleteIfExists(Path.of(database + suffix));
        }
        Files.copy(base, database);
        String url = "jdbc:sqlite:" + database.toString().replace('\\', '/')
                + "?foreign_keys=on&busy_timeout=300000&journal_mode=" + journalMode + "&synchronous=" + synchronous;

        StringBuilder oneOffs = new StringBuilder();
        long[] chunkNanos = new long[repeats];
        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement()) {
                if (adr182) {
                    oneOffs.append(timed(statement, "DROP INDEX IF EXISTS shingle_by_hash", "drop by-hash"));
                    oneOffs.append(timed(statement, BY_RUN_ID, "build run_id index"));
                } else {
                    statement.executeUpdate("DROP INDEX IF EXISTS shingle_by_run_id");
                    statement.executeUpdate(BY_HASH);
                }
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            System.out.printf(Locale.ROOT, "%n%s%s%n", label, oneOffs);
            long nextOccurrence = maxOccurrenceId(connection) + 1;
            SplittableRandom random = new SplittableRandom(42);
            connection.setAutoCommit(false);
            for (int repeat = 0; repeat < repeats; repeat++) {
                long start = System.nanoTime();
                nextOccurrence = JournalModeChunk.write(connection, nextOccurrence, rowsPerDocument, random);
                connection.commit();
                chunkNanos[repeat] = System.nanoTime() - start;
                System.out.printf(Locale.ROOT, "  repeat %2d: chunk %8.1f ms%n", repeat + 1, chunkNanos[repeat] / 1e6);
            }
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                oneOffs.append(timedStage3Read(connection));
                if (adr182) {
                    oneOffs.append(timed(statement, BY_HASH, "4b builds by-hash"));
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            }
        }
        for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
            Files.deleteIfExists(Path.of(database + suffix));
        }
        String line = String.format(Locale.ROOT, "%-42s median chunk %8.1f ms%s", label, median(chunkNanos) / 1e6, oneOffs);
        System.out.println(line);
        return line;
    }

    private static String timed(Statement statement, String sql, String what) throws SQLException {
        long start = System.nanoTime();
        statement.executeUpdate(sql);
        return String.format(Locale.ROOT, ", %s %.1f s", what, (System.nanoTime() - start) / 1e9);
    }

    private static String timedStage3Read(Connection connection) throws SQLException {
        long start = System.nanoTime();
        long rows = 0;
        String plan;
        try (PreparedStatement explain = connection.prepareStatement("EXPLAIN QUERY PLAN " + STAGE_3_READ)) {
            explain.setString(1, RUN_ID);
            try (ResultSet result = explain.executeQuery()) {
                result.next();
                plan = result.getString("detail");
            }
        }
        try (PreparedStatement read = connection.prepareStatement(STAGE_3_READ)) {
            read.setString(1, RUN_ID);
            try (ResultSet result = read.executeQuery()) {
                while (result.next()) {
                    rows++;
                }
            }
        }
        return String.format(Locale.ROOT, ", stage-3 read of %,d rows %.1f s [%s]", rows,
                (System.nanoTime() - start) / 1e9, plan);
    }

    private static void buildSyntheticBase(Path base, int occurrences, int rowsPerDocument)
            throws IOException, SQLException {
        String url = "jdbc:sqlite:" + base.toString().replace('\\', '/')
                + "?journal_mode=OFF&synchronous=OFF&cache_size=-2000000";
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String ddl : schemaStatements()) {
                statement.executeUpdate(ddl);
            }
            statement.executeUpdate(BY_HASH);
            statement.executeUpdate("INSERT INTO walk (id, root) VALUES (1, 'bench')");
            statement.executeUpdate("INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                    + " VALUES ('" + RUN_ID + "', 'extraction', 'bench', 'bench', 1)");
            connection.setAutoCommit(false);
            SplittableRandom random = new SplittableRandom(7);
            long next = 1;
            while (next <= occurrences) {
                next = JournalModeChunk.write(connection, next, rowsPerDocument, random);
                connection.commit();
                if ((next - 1) % 400 == 0) {
                    System.out.printf(Locale.ROOT, "  base: %,d of %,d occurrences%n", next - 1, occurrences);
                }
            }
            connection.setAutoCommit(true);
            statement.executeUpdate("PRAGMA journal_mode = DELETE");
        }
    }

    /** One stage-2 chunk, as {@link JournalModeBenchmark} writes it, under walk 1. */
    private static final class JournalModeChunk {

        static long write(Connection connection, long firstOccurrence, int rowsPerDocument, SplittableRandom random)
                throws SQLException {
            long nextOccurrence = firstOccurrence;
            try (PreparedStatement occurrence = connection.prepareStatement(
                            "INSERT INTO file_occurrence (id, walk_id, path, size_bytes, last_modified, creation_time)"
                                    + " VALUES (?, 1, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                    PreparedStatement shingle = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)")) {
                for (int document = 0; document < DOCUMENTS_PER_CHUNK; document++) {
                    long occurrenceId = nextOccurrence++;
                    occurrence.setLong(1, occurrenceId);
                    occurrence.setString(2, "bench/" + occurrenceId);
                    occurrence.executeUpdate();
                    for (int row = 0; row < rowsPerDocument; row++) {
                        shingle.setLong(1, occurrenceId);
                        shingle.setString(2, RUN_ID);
                        shingle.setString(3, PARAMETER_IDENTITY);
                        shingle.setLong(4, random.nextLong());
                        shingle.addBatch();
                        if ((row + 1) % INSERT_BATCH == 0) {
                            shingle.executeBatch();
                        }
                    }
                    shingle.executeBatch();
                }
            }
            return nextOccurrence;
        }
    }

    private static List<String> schemaStatements() throws IOException {
        try (InputStream in = ShingleLookupBenchmark.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql is not on the classpath; put target/classes on it");
            }
            StringBuilder withoutComments = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                int comment = line.indexOf("--");
                withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
            }
            List<String> statements = new ArrayList<>();
            for (String statement : withoutComments.toString().split(";")) {
                if (!statement.isBlank()) {
                    statements.add(statement.strip());
                }
            }
            return statements;
        }
    }

    private static long count(Path database, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toString().replace('\\', '/'));
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static long maxOccurrenceId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT coalesce(max(id), 0) FROM file_occurrence")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static double median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }
}
