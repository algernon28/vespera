package io.algernon.vespera.ledger;

import java.io.Serial;
import java.nio.file.Path;
import java.sql.SQLException;
import org.springframework.dao.CannotAcquireLockException;

/**
 * SQLite reported the database file locked, and another process holds it (ADR-177 §2, #364).
 *
 * <p>The message names the file and says why, which the SQL statement and {@code [SQLITE_BUSY]} that
 * Spring's own translation leaves do not. It carries SQLite's message as well, so nothing the driver
 * said is lost.
 */
public class DatabaseFileLockedException extends CannotAcquireLockException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Path databaseFile;

    public DatabaseFileLockedException(Path databaseFile, SQLException cause) {
        super("the database file " + databaseFile + " is held by another process, which is writing to it or"
                + " keeping a transaction open on it (" + cause.getMessage() + ")", cause);
        this.databaseFile = databaseFile;
    }

    /** The database file that was locked. */
    public Path databaseFile() {
        return databaseFile;
    }
}
