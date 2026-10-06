package io.algernon.vespera;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.sqlite.SQLiteConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

/**
 * A pool of two connections over a database file of the test's own, under the shipped {@code schema.sql},
 * with no transaction open: what each module's contract test of its counted statements runs on (ADR-193
 * section 2, ADR-199 section 4).
 *
 * <p><b>Two connections on purpose.</b> A counted statement must run on the connection its progress handler
 * was set on. One handed back to a {@code JdbcTemplate} method inside the callback borrows the second
 * connection here, the handler on the first counts nothing, and a read over many rows reports no steps. The
 * test profile's pool of one, inside a test's transaction, cannot see that mistake.
 *
 * <p>Parked under {@code docs/adr/0193/tests/b/} with the tests that use it; it compiles against main and
 * names nothing part (b) adds.
 */
public final class PoolOfTwo implements AutoCloseable {

    private static final int TWO_CONNECTIONS = 2;

    private final HikariDataSource pool;
    private final JdbcTemplate jdbcTemplate;

    /** Opens the pool over a new file in {@code folder} and applies the shipped schema to it. */
    public PoolOfTwo(Path folder) throws SQLException, IOException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve("pool-of-two.db"));
        config.setMaximumPoolSize(TWO_CONNECTIONS);
        config.setMinimumIdle(TWO_CONNECTIONS);
        pool = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(pool);
        StringBuilder statements = new StringBuilder();
        String schema =
                StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
        for (String line : schema.split("\n")) {
            if (!line.strip().startsWith("--")) {
                statements.append(line).append('\n');
            }
        }
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement()) {
            for (String one : statements.toString().split(";")) {
                if (!one.isBlank()) {
                    statement.executeUpdate(one);
                }
            }
        }
    }

    /** The template every class under test is given. */
    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    /** One connection of the pool, for a fixture that writes many rows in one batch; the caller closes it. */
    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    /** How many of the pool's two connections carry a progress handler, both taken at once. */
    public int handlersLeft() throws SQLException {
        int carrying = 0;
        try (Connection first = pool.getConnection();
                Connection second = pool.getConnection()) {
            for (Connection connection : List.of(first, second)) {
                if (carriesAHandler(connection.unwrap(SQLiteConnection.class))) {
                    carrying++;
                }
            }
        }
        return carrying;
    }

    @Override
    public void close() {
        pool.close();
    }

    private static boolean carriesAHandler(SQLiteConnection connection) {
        try {
            Object database = connection.getDatabase();
            Method handler = database.getClass().getDeclaredMethod("getProgressHandler");
            handler.setAccessible(true);
            return ((Long) handler.invoke(database)) != 0L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the driver no longer says whether a progress handler is set", e);
        }
    }
}
