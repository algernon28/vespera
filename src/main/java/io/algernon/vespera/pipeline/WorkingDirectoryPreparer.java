package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Creates the working directory before anything tries to open a database inside it (ADR-054).
 *
 * <p>An environment listener rather than a bean, because of when it has to happen: the datasource
 * URL is built from {@code vespera.working-dir} and SQLite will not create a file in a directory
 * that does not exist, so by the time any bean could run, the connection has already failed. This is
 * the last point before that where the property is known.
 *
 * <p>It is also why {@code --db-dir} is a property and not only a command-line option: an option
 * parsed by the CLI would arrive long after the database was opened.
 */
public class WorkingDirectoryPreparer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** Where the database and the profile live, together (ADR-054). */
    public static final String PROPERTY = "vespera.working-dir";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String configured = event.getEnvironment().getProperty(PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path workingDirectory = Path.of(configured);
        try {
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("could not create the working directory at " + workingDirectory, e);
        }
    }
}
