package io.algernon.vespera;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * The measurement behind ADR-180: how long one stage-2 chunk takes to write its shingles into a
 * large database, under the rollback journal ({@code DELETE}/{@code FULL}, what shipped before) and
 * under write-ahead logging ({@code WAL}/{@code NORMAL}), with the page cache and the checkpoint
 * interval as the other two variables -- the first measurement showed neither journal mode can be
 * judged without them.
 *
 * <p><b>Not a test, and never run by the build.</b> The name does not end in {@code Test}, so
 * surefire skips it. It is a {@code main} kept beside the tests so the measurement can be repeated
 * against the schema the application ships, after the change lands, or on another disk.
 *
 * <p><b>Never point it at a database a run is using.</b> It writes. Either give it a copy taken
 * while no invocation has the working directory open, or let it build a synthetic one.
 *
 * <p>One repeat is one transaction holding {@code 16} documents' shingles -- 16 is {@code
 * ExtractionJobConfiguration.CHUNK_SIZE} -- each document {@code rows-per-document} rows, inserted
 * in batches of 5,000 as {@code Shingler} sends them, every row under one run id with a random hash,
 * so {@code shingle_by_hash} takes scattered page writes the way a real chunk's does. Each setting
 * starts from its own byte-identical copy of the same base database. What is reported per setting:
 * the median transaction and the median commit, and the total over all repeats <i>including a
 * closing {@code wal_checkpoint(TRUNCATE)}</i>, because under WAL a commit that skipped its
 * checkpoint only deferred the write, and a median alone would hide the commit that pays for it.
 *
 * <pre>
 * ./mvnw -q -o test-compile
 * java -cp "target/test-classes;target/classes;&lt;sqlite-jdbc jar&gt;" io.algernon.vespera.JournalModeBenchmark \
 *     &lt;scratch-dir&gt; [base-database-to-copy | -] [occurrences] [rows-per-document] [repeats] [settings]
 * </pre>
 *
 * <p>With {@code -} (the default) it builds a synthetic base in the scratch directory from the
 * shipped {@code schema.sql}: {@code occurrences} file occurrences with {@code rows-per-document}
 * random shingle rows each, inserted with both indexes in place, so the index pages are as full as
 * an index grown one insert at a time leaves them. (Building the index afterwards packs every leaf
 * full, and then nearly every measured insert splits a page, which no ledger grown by stage 2 does.)
 * A base already in the scratch directory is reused.
 */
public final class JournalModeBenchmark {

    /** Documents per chunk, as stage 2 commits them. */
    private static final int DOCUMENTS_PER_CHUNK = 16;

    /** Rows per {@code executeBatch}, as {@code Shingler} sends them. */
    private static final int INSERT_BATCH = 5_000;

    /** The run every synthetic shingle row is written under: 64 hex characters, as a real run id is. */
    private static final String RUN_ID = "2e82fc36ac5e3ec1128dc4a60bf31865b8f9bed031eaeff597aa43c1eb7d0075";

    /** The shingle granularity identity today's default writes. */
    private static final String PARAMETER_IDENTITY = "word:5";

    /**
     * One setting per comma, its fields slash-separated: journal mode, synchronous level, {@code
     * cache_size}, {@code wal_autocheckpoint} in pages, and optionally {@code nohash} to drop {@code
     * shingle_by_hash} from the copy first, or {@code runonly} to put an index on {@code run_id} alone in its
     * place and then time building {@code shingle_by_hash} once, over the whole table, afterwards. {@code -2000} is SQLite's default cache, 2,000 KiB, and
     * {@code 1000} its default checkpoint interval.
     */
    private static final String DEFAULT_SETTINGS = String.join(",",
            "DELETE/FULL/-2000/1000",
            "WAL/NORMAL/-2000/1000",
            "WAL/NORMAL/-2000/250000",
            "DELETE/FULL/-262144/1000",
            "WAL/NORMAL/-262144/250000",
            "DELETE/FULL/-2000/1000/nohash",
            "WAL/NORMAL/-2000/1000/nohash",
            "DELETE/FULL/-2000/1000/runonly");

    private JournalModeBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path scratch = Path.of(args[0]);
        String source = args.length > 1 ? args[1] : "-";
        int occurrences = args.length > 2 ? Integer.parseInt(args[2]) : 4_000;
        int rowsPerDocument = args.length > 3 ? Integer.parseInt(args[3]) : 2_500;
        int repeats = args.length > 4 ? Integer.parseInt(args[4]) : 10;
        String settings = args.length > 5 ? args[5] : DEFAULT_SETTINGS;
        Files.createDirectories(scratch);

        Path base = scratch.resolve("base.db");
        if ("-".equals(source)) {
            if (!Files.exists(base)) {
                buildSyntheticBase(base, occurrences, rowsPerDocument);
            }
        } else {
            Files.copy(Path.of(source), base, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.printf(Locale.ROOT, "base: %s, %,d bytes, %,d shingle rows%n",
                base, Files.size(base), count(base, "SELECT count(*) FROM shingle"));

        List<String> summary = new ArrayList<>();
        for (String setting : settings.split(",")) {
            summary.add(measure(scratch, base, setting.split("/"), rowsPerDocument, repeats));
        }
        System.out.println();
        summary.forEach(System.out::println);
    }

    private static String measure(Path scratch, Path base, String[] setting, int rowsPerDocument, int repeats)
            throws IOException, SQLException {
        String journalMode = setting[0];
        String synchronous = setting[1];
        long cacheSize = Long.parseLong(setting[2]);
        long autocheckpoint = Long.parseLong(setting[3]);
        boolean runIdOnly = setting.length > 4 && "runonly".equals(setting[4]);
        boolean dropHashIndex = runIdOnly || setting.length > 4 && "nohash".equals(setting[4]);
        String label = String.join("/", setting);

        Path database = scratch.resolve("bench.db");
        for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
            Files.deleteIfExists(Path.of(database + suffix));
        }
        Files.copy(base, database);
        String url = "jdbc:sqlite:" + database.toString().replace('\\', '/')
                + "?foreign_keys=on&busy_timeout=300000&journal_mode=" + journalMode + "&synchronous=" + synchronous
                + "&cache_size=" + cacheSize;

        long[] transactionNanos = new long[repeats];
        long[] commitNanos = new long[repeats];
        long walHighWater = 0;
        long totalNanos;
        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_autocheckpoint = " + autocheckpoint);
                if (dropHashIndex) {
                    statement.executeUpdate("DROP INDEX shingle_by_hash");
                    if (runIdOnly) {
                        statement.executeUpdate("CREATE INDEX shingle_by_run ON shingle (run_id)");
                    }
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                // A copied real database has no run under this id, and foreign_keys=on needs one.
                long walkId = walkId(connection);
                statement.executeUpdate("INSERT OR IGNORE INTO run (id, stage, implementation_version, config_consumed,"
                        + " walk_id) VALUES ('" + RUN_ID + "', 'extraction', 'bench', 'bench', " + walkId + ")");
            }
            System.out.printf(Locale.ROOT, "%n%s: journal_mode=%s synchronous=%s cache_size=%s wal_autocheckpoint=%s%n",
                    label, pragma(connection, "journal_mode"), pragma(connection, "synchronous"),
                    pragma(connection, "cache_size"), pragma(connection, "wal_autocheckpoint"));
            long nextOccurrence = maxOccurrenceId(connection) + 1;
            long walkId = walkId(connection);
            SplittableRandom random = new SplittableRandom(42);
            long allStart = System.nanoTime();
            connection.setAutoCommit(false);
            for (int repeat = 0; repeat < repeats; repeat++) {
                long start = System.nanoTime();
                nextOccurrence = writeChunk(connection, nextOccurrence, walkId, rowsPerDocument, random);
                long beforeCommit = System.nanoTime();
                connection.commit();
                long end = System.nanoTime();
                transactionNanos[repeat] = end - start;
                commitNanos[repeat] = end - beforeCommit;
                Path wal = Path.of(database + "-wal");
                long walBytes = Files.exists(wal) ? Files.size(wal) : 0;
                walHighWater = Math.max(walHighWater, walBytes);
                System.out.printf(Locale.ROOT, "  repeat %2d: transaction %8.1f ms, commit %8.1f ms, -wal %,d bytes%n",
                        repeat + 1, transactionNanos[repeat] / 1e6, commitNanos[repeat] / 1e6, walBytes);
            }
            connection.setAutoCommit(true);
            long checkpointStart = System.nanoTime();
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            long allEnd = System.nanoTime();
            totalNanos = allEnd - allStart;
            System.out.printf(Locale.ROOT, "  closing checkpoint %.1f ms%n", (allEnd - checkpointStart) / 1e6);
            if (runIdOnly) {
                // What building the hash index once, after the stage, costs instead.
                long buildStart = System.nanoTime();
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE INDEX shingle_by_hash"
                            + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)");
                }
                System.out.printf(Locale.ROOT, "  building shingle_by_hash once over the whole table: %.1f ms%n",
                        (System.nanoTime() - buildStart) / 1e6);
            }
        }
        String line = String.format(Locale.ROOT,
                "%-32s median transaction %8.1f ms, median commit %8.1f ms, total incl. closing checkpoint %9.1f ms"
                        + " (%7.1f ms a chunk), largest -wal %,d bytes",
                label, median(transactionNanos) / 1e6, median(commitNanos) / 1e6, totalNanos / 1e6,
                totalNanos / 1e6 / repeats, walHighWater);
        System.out.println(line);
        return line;
    }

    private static long writeChunk(Connection connection, long firstOccurrence, long walkId, int rowsPerDocument,
            SplittableRandom random) throws SQLException {
        long nextOccurrence = firstOccurrence;
        try (PreparedStatement occurrence = connection.prepareStatement(
                        "INSERT INTO file_occurrence (id, walk_id, path, size_bytes, last_modified, creation_time)"
                                + " VALUES (?, ?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                PreparedStatement shingle = connection.prepareStatement(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)")) {
            for (int document = 0; document < DOCUMENTS_PER_CHUNK; document++) {
                long occurrenceId = nextOccurrence++;
                occurrence.setLong(1, occurrenceId);
                occurrence.setLong(2, walkId);
                occurrence.setString(3, "bench/" + occurrenceId);
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

    /**
     * The shipped schema, then {@code occurrences} occurrences carrying {@code rowsPerDocument} random
     * shingle rows each, inserted with both indexes in place. The journal is off and the cache large
     * while building, which changes how fast the base is built and not what it looks like on disk.
     */
    private static void buildSyntheticBase(Path base, int occurrences, int rowsPerDocument)
            throws IOException, SQLException {
        String url = "jdbc:sqlite:" + base.toString().replace('\\', '/')
                + "?journal_mode=OFF&synchronous=OFF&cache_size=-2000000";
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String ddl : schemaStatements()) {
                statement.executeUpdate(ddl);
            }
            statement.executeUpdate("INSERT INTO walk (id, root) VALUES (1, 'bench')");
            statement.executeUpdate("INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                    + " VALUES ('" + RUN_ID + "', 'extraction', 'bench', 'bench', 1)");
            connection.setAutoCommit(false);
            SplittableRandom random = new SplittableRandom(7);
            long next = 1;
            while (next <= occurrences) {
                next = writeChunk(connection, next, 1, rowsPerDocument, random);
                connection.commit();
                if ((next - 1) % 400 == 0) {
                    System.out.printf(Locale.ROOT, "  base: %,d of %,d occurrences%n", next - 1, occurrences);
                }
            }
            connection.setAutoCommit(true);
            statement.executeUpdate("PRAGMA journal_mode = DELETE");
        }
    }

    /** {@code schema.sql} as the application ships it, comments removed and split into statements. */
    private static List<String> schemaStatements() throws IOException {
        try (InputStream in = JournalModeBenchmark.class.getResourceAsStream("/schema.sql")) {
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

    private static long walkId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT min(id) FROM walk")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String pragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            return result.getString(1);
        }
    }

    private static double median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }
}
