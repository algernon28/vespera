package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.StatementSteps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Applies {@code schema.sql} as Spring Boot does, after saying which index it is about to build over rows
 * that are already there (ADR-187 section 3, #402).
 *
 * <p><b>Why this runs before the script.</b> It is the application's script initializer: Boot's own is
 * conditional on there being none, so this one replaces it, and it announces from {@link
 * #afterPropertiesSet()}, before it hands over to the script. Every {@code JdbcTemplate} and every bean
 * marked {@code @DependsOnDatabaseInitialization} depends on the initializer (Boot's
 * initialization-dependency detection, which {@code JdbcTemplateAutoConfiguration} imports whether or not
 * Boot's own initializer is there), so none of them reads the database before this bean has finished.
 *
 * <p><b>What it announces is read from {@code schema.sql}.</b> Each {@code CREATE INDEX IF NOT EXISTS}
 * statement in the file, in file order, whose index the database lacks and whose table exists and holds a
 * row, gets a line before and a line after that statement, which it runs itself, and, between them, the
 * progress lines of ADR-193 while SQLite goes through the rows. The script then runs as it always did, and
 * finds those indexes in place. There is no second list of indexes.
 */
@Component
class StartUpIndexAnnouncement extends ApplicationDataSourceScriptDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(StartUpIndexAnnouncement.class);

    private static final String SCHEMA = "schema.sql";

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /**
     * An index statement: the whole of it up to its semicolon, with the index, its table and the
     * parenthesised list of its columns captured, so that the columns can be counted (ADR-193 section 3).
     */
    private static final Pattern INDEX_STATEMENT = Pattern.compile(
            "^[ \\t]*(CREATE[ \\t]+INDEX[ \\t]+IF[ \\t]+NOT[ \\t]+EXISTS\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(([^)]*)\\)[^;]*);",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /**
     * The steps SQLite takes for each row of a whole-table index build beyond one for each column the index
     * holds: 8 plus c, measured against the bundled SQLite and pinned by {@code StatementStepsPerRowTest}
     * (ADR-193 section 3).
     */
    static final int INDEX_BUILD_STEPS_BEYOND_COLUMNS = 8;

    private final DataSource dataSource;

    /**
     * The {@code spring.sql.init} properties are bound here from the environment: the bean Boot would
     * have registered them through is part of the initializer this one replaces, so it is not there to inject.
     */
    StartUpIndexAnnouncement(DataSource dataSource, Environment environment) {
        super(dataSource, Binder.get(environment).bindOrCreate("spring.sql.init", SqlInitializationProperties.class));
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        announceIndexesBuiltOverExistingRows();
        super.afterPropertiesSet();
    }

    private void announceIndexesBuiltOverExistingRows() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Matcher statements = INDEX_STATEMENT.matcher(schema());
        while (statements.find()) {
            String statement = statements.group(1);
            String index = statements.group(2);
            String table = statements.group(3);
            if (exists(jdbcTemplate, "index", index) || !exists(jdbcTemplate, "table", table)) {
                continue;
            }
            Long greatestRow = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM " + table, Long.class);
            if (greatestRow == null) {
                continue;
            }
            log.info(
                    "Start-up is building index {} on {}, over up to {} rows, which this database does not have"
                            + " yet; on a large database this takes minutes, and stopping before it ends undoes it",
                    index,
                    table,
                    greatestRow);
            int columns = statements.group(4).split(",").length;
            StatementProgress progress = StatementProgress.ofBuild(
                    "Start-up (building index " + index + ")",
                    greatestRow,
                    INDEX_BUILD_STEPS_BEYOND_COLUMNS + columns);
            long started = System.nanoTime();
            StatementSteps.counted(jdbcTemplate, progress::stepsTaken, connection -> {
                try (Statement create = connection.createStatement()) {
                    create.execute(statement);
                }
                return null;
            });
            log.info(
                    "Start-up built index {} in {} s",
                    index,
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / NANOS_PER_SECOND));
        }
    }

    private static boolean exists(JdbcTemplate jdbcTemplate, String type, String name) {
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?", Long.class, type, name);
        return found != null && found > 0;
    }

    private static String schema() {
        try {
            return StreamUtils.copyToString(new ClassPathResource(SCHEMA).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + SCHEMA + " to find the indexes it builds", e);
        }
    }
}
