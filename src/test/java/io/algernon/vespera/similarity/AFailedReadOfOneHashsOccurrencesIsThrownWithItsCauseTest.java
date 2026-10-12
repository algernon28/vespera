package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A failure of the database while stage 4b reads the occurrences of one rare hash reaches the caller as a
 * failure of the database, carrying what SQLite said (ADR-237). That read runs on a connection of its own
 * rather than through a template method, so it turns SQLite's error into Spring's itself; SQLite gives no
 * SQL state, which leaves Spring's translator with nothing to name the error by, and a translation that
 * answers nothing must not be thrown as it is.
 *
 * <p>No disk is made to fail: the pool's connections are handed out through a stand-in that refuses to
 * prepare that one read, with the error and the code SQLite gives when another program holds the database
 * file. Every other statement goes to the database as it would.
 */
@Epic("Redundancy")
@Feature("Resolution")
@Link(name = "ADR-237", url = Adr.THE_READ_OF_ONE_HASHS_OCCURRENCES_IS_PREPARED_ONCE_A_PASS, type = "adr")
class AFailedReadOfOneHashsOccurrencesIsThrownWithItsCauseTest {

    /** The contained document's shingles, every one of which the container holds too. */
    private static final int CONTAINED_SHINGLES = 40;

    /** What the container holds besides them. */
    private static final int CONTAINER_EXTRA_SHINGLES = 200;

    /** Not a floor that strips anything: every shingle of both documents stays distinctive. */
    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    /** How the read of one hash's occurrences begins, with the stage-2 run written into its text. */
    private static final String THE_READ_OF_ONE_HASH = "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = '";

    private static final String WHAT_SQLITE_SAID = "[SQLITE_BUSY] The database file is locked (database is locked)";

    private PoolOfTwo pool;

    @BeforeEach
    void openThePool(@TempDir Path folder) throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Redundancy resolution says what the database said when a read fails")
    @DisplayName("When the database refuses the read of the documents carrying one rare word sequence, the failure thrown is the database's, carrying its own message")
    void aRefusedReadIsThrownAsTheDatabasesFailure() {
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-of-a-refused-read"));
        RunId stage2 = ledger.runs().startRun("extraction", "s2-refused", "{}", walk, List.of());
        RunId stage3 = ledger.runs().startRun("content-census", "s3-refused", "{}", walk, List.of(stage2));
        RunId stage4 = ledger.runs().startRun("content-redundancy", "s4-refused", "{}", walk, List.of(stage3));
        OccurrenceId chapter = document(jdbcTemplate, ledger, walk, stage2, "chapter.pdf", 0, CONTAINED_SHINGLES, 0);
        OccurrenceId volume = document(
                jdbcTemplate, ledger, walk, stage2, "volume.pdf", 0, CONTAINED_SHINGLES, CONTAINER_EXTRA_SHINGLES);
        new TransactionTemplate(new JdbcTransactionManager(jdbcTemplate.getDataSource()))
                .executeWithoutResult(status -> new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2));
        RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
        for (OccurrenceId occurrence : List.of(chapter, volume)) {
            signatures.write(occurrence, stage4, stage2, Set.of(), NO_BOILERPLATE_FLOOR);
        }
        JdbcTemplate refusing = new JdbcTemplate(refusingTheReadOfOneHash(jdbcTemplate.getDataSource()));

        Throwable thrown = catchThrowable(() -> new RedundancyResolution(refusing, new Ledger(refusing))
                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(refusing)));

        claim(
                "what is thrown is a failure of the database, and not a failure of the program to say one: no"
                        + " empty reference stands in its place",
                () -> assertThat(thrown).isInstanceOf(DataAccessException.class).isNotInstanceOf(NullPointerException.class));
        claim(
                "and it carries what the database said, so the operator reads why the read failed",
                () -> assertThat(thrown).rootCause().isInstanceOf(SQLiteException.class).hasMessage(WHAT_SQLITE_SAID));
    }

    /**
     * An occurrence holding the shingles {@code from} to {@code from + shared - 1} and {@code extra} more of its
     * own, with an extraction metric so that the survivor rule has a size to read.
     */
    private static OccurrenceId document(
            JdbcTemplate jdbcTemplate, Ledger ledger, WalkId walk, RunId stage2, String path, long from, int shared, int extra) {
        ledger.occurrences()
                .fileOccurrence(walk, new OccurrencePath(path), 1, Instant.parse("2026-10-12T10:00:00Z"), Instant.parse("2026-10-12T10:00:00Z"));
        OccurrenceId occurrence = ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
        LongStream hashes = LongStream.concat(
                LongStream.range(from, from + shared), LongStream.range(1_000_000L, 1_000_000L + extra));
        hashes.forEach(hash -> jdbcTemplate.update(
                "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash) VALUES (?, ?, ?, ?)",
                occurrence.value(),
                stage2.value(),
                ShingleParameters.DEFAULT.identity(),
                hash));
        jdbcTemplate.update(
                "INSERT INTO extraction_metric"
                        + " (occurrence_id, run_id, status, processing_time, character_count,"
                        + " alphanumeric_char_count, word_count, word_character_length_total,"
                        + " vowelless_word_count, single_character_word_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                occurrence.value(),
                stage2.value(),
                "success",
                0.5,
                shared + extra,
                shared + extra,
                1,
                1,
                0,
                0);
        return occurrence;
    }

    /** {@code dataSource}, whose connections refuse to prepare the read of one hash's occurrences as SQLite refuses one. */
    private static DataSource refusingTheReadOfOneHash(DataSource dataSource) {
        return new DelegatingDataSource(dataSource) {
            @Override
            public Connection getConnection() throws SQLException {
                return refusing(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return refusing(super.getConnection(username, password));
            }
        };
    }

    private static Connection refusing(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                AFailedReadOfOneHashsOccurrencesIsThrownWithItsCauseTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")
                            && arguments != null
                            && arguments[0] instanceof String sql
                            && sql.startsWith(THE_READ_OF_ONE_HASH)) {
                        throw new SQLiteException(WHAT_SQLITE_SAID, SQLiteErrorCode.SQLITE_BUSY);
                    }
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                });
    }
}
