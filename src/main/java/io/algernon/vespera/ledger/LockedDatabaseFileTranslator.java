package io.algernon.vespera.ledger;

import java.nio.file.Path;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.stereotype.Component;

/**
 * Names the database file when SQLite reports it locked, wherever a statement meets the lock (ADR-177
 * §2.1, #364).
 *
 * <p>Spring Boot hands the {@code JdbcTemplate} it builds, which every {@code JdbcTemplate} statement
 * uses, the application's {@link SQLExceptionTranslator} bean, when there is exactly one. This is that
 * bean. Ledger's two {@code JdbcPagingItemReader} reads ({@code survivors}, {@code occurrencesOf}) build
 * their own template inside Spring Batch, so they are not translated (ADR-177 §2.1). A failure with an
 * {@link SQLiteException} anywhere in its causes whose primary result code is {@code SQLITE_BUSY} (5) or
 * {@code SQLITE_LOCKED} (6) becomes a {@link DatabaseFileLockedException}, which covers the extended
 * codes too, {@code SQLITE_BUSY_SNAPSHOT} (517) among them. Anything else is translated as the template
 * would have without this bean, so no other failure is changed.
 */
@Component
public class LockedDatabaseFileTranslator implements SQLExceptionTranslator {

    /** The database file's name in the working directory (ADR-054). */
    private static final String DATABASE_FILE = "vespera.db";

    private static final int PRIMARY_CODE_MASK = 0xFF;
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;

    private final Path databaseFile;
    private final SQLExceptionTranslator everythingElse = new SQLExceptionSubclassTranslator();

    public LockedDatabaseFileTranslator(@Value("${vespera.working-dir}") Path workingDirectory) {
        this.databaseFile = workingDirectory.resolve(DATABASE_FILE);
    }

    @Override
    @Nullable
    public DataAccessException translate(String task, @Nullable String sql, SQLException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLiteException sqlite && isLock(sqlite)) {
                return new DatabaseFileLockedException(databaseFile, sqlite);
            }
        }
        return everythingElse.translate(task, sql, ex);
    }

    private static boolean isLock(SQLiteException sqlite) {
        int primary = sqlite.getResultCode().code & PRIMARY_CODE_MASK;
        return primary == SQLITE_BUSY || primary == SQLITE_LOCKED;
    }
}
