package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Sets SQLite's directory for temporary files to the working directory, once, as the process starts (ADR-211
 * section 12): what a statement sorts on disk is then written beside the database and not in the system's
 * temporary folder.
 *
 * <p>An environment listener, registered by {@code VesperaApplication.main} after the ones that create the
 * working directory, take its lock and check its profile, for the reasons the other three are (ADR-054,
 * ADR-177): the directory has to exist and be this invocation's, and no connection pool may exist yet. SQLite's
 * directory is one variable of the process, and its documentation forbids changing it while another connection
 * is open or another thread is using SQLite, which is why the setting is made here, once, and is not a setting
 * of the datasource that every connection the pool opens would run again.
 *
 * <p>It opens a connection of its own to no database, {@code jdbc:sqlite::memory:}, runs {@code PRAGMA
 * temp_store_directory} with the working directory's absolute path between single quotes, each apostrophe in
 * it doubled, and closes the connection; the setting outlives it. Its constructor does nothing, so a test that
 * has {@code main} register it sets nothing.
 *
 * <p>The path is absolute because {@code --db-dir} need not be: SQLite keeps the string it is given and reads
 * it whenever it opens a temporary file, so a relative one would be read against whatever the current
 * directory is at that moment. It is not resolved to a real path, which would differ under a symlink and fail
 * for a directory that is not there.
 */
public class TemporaryFilesInTheWorkingDirectory implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** The SQLite driver's class, loaded by name below so that the driver is registered under this loader. */
    private static final String DRIVER = "org.sqlite.JDBC";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String configured = event.getEnvironment().getProperty(WorkingDirectoryPreparer.PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        String directory = Path.of(configured).toAbsolutePath().toString();
        try {
            // Under the packaged jar's class loader the driver is not found by the JDK's own scan of the
            // class path, and no pool has loaded it yet: loading it from here registers it for this class.
            Class.forName(DRIVER);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                    Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA temp_store_directory = '" + directory.replace("'", "''") + "'");
            }
        } catch (SQLException | ClassNotFoundException e) {
            // Not swallowed: a process that went on would write its temporary files to the system drive
            // without saying so. SQLite's refusal leaves its default in place.
            throw new IllegalStateException(
                    "could not set SQLite's directory for temporary files to the working directory at " + directory,
                    e);
        }
    }
}
