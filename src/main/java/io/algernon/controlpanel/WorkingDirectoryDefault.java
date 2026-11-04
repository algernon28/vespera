package io.algernon.controlpanel;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The work folder left empty (ADR-340 §6): the folder the jar would use on this machine, the first set and not blank of
 * the system property {@code vespera.working-dir}, its environment variable in Spring's four spellings, the system
 * property {@code db-dir} and its variable in Spring's four spellings, and else {@code .vespera} under the current
 * directory.
 *
 * @param folder the folder, absolute and normalised, or the value as given where no path can hold it
 * @param source {@code system-property}, {@code environment} or {@code default}
 * @param name the property or variable read, empty for {@code default}
 */
public record WorkingDirectoryDefault(String folder, String source, String name) {

    static final String SYSTEM_PROPERTY = "system-property";

    static final String ENVIRONMENT = "environment";

    static final String DEFAULT = "default";

    private static final List<String> WORKING_DIR_VARIABLES = List.of("VESPERA_WORKINGDIR", "VESPERA_WORKING_DIR",
            "vespera_workingdir", "vespera_working_dir");

    private static final List<String> DB_DIR_VARIABLES = List.of("DBDIR", "DB_DIR", "dbdir", "db_dir");

    /** Resolves ADR-340 §6's table from the maps given, which {@code ControlPanel.main} fills from the JVM's own. */
    public static WorkingDirectoryDefault of(Path currentDirectory, Map<String, String> systemProperties,
            Map<String, String> environment) {
        Path current = currentDirectory.toAbsolutePath().normalize();
        String set = set(systemProperties, "vespera.working-dir");
        if (set != null) {
            return new WorkingDirectoryDefault(resolved(current, set), SYSTEM_PROPERTY, "vespera.working-dir");
        }
        for (String variable : WORKING_DIR_VARIABLES) {
            set = set(environment, variable);
            if (set != null) {
                return new WorkingDirectoryDefault(resolved(current, set), ENVIRONMENT, variable);
            }
        }
        set = set(systemProperties, "db-dir");
        if (set != null) {
            return new WorkingDirectoryDefault(resolved(current, set), SYSTEM_PROPERTY, "db-dir");
        }
        for (String variable : DB_DIR_VARIABLES) {
            set = set(environment, variable);
            if (set != null) {
                return new WorkingDirectoryDefault(resolved(current, set), ENVIRONMENT, variable);
            }
        }
        return new WorkingDirectoryDefault(current.resolve(".vespera").toString(), DEFAULT, "");
    }

    private static String set(Map<String, String> from, String key) {
        String value = from.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String resolved(Path current, String value) {
        try {
            return current.resolve(Path.of(value)).normalize().toString();
        } catch (InvalidPathException cannotBeHeld) {
            return value;
        }
    }
}
