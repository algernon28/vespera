package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The folder the sources name for the working directory (ADR-436 §2, amending ADR-340 §6): the window reads what the jar
 * reads, in Spring's order, the first row that names a folder: the system property {@code vespera.working-dir}, its
 * environment variable in Spring's four spellings, then the property {@code vespera.working-dir} of
 * {@code application-local.yaml} and of {@code application.yaml} beside the jar, resolved as Spring resolves a
 * placeholder. {@code db-dir}, the system property and its variable in Spring's four spellings, is read only through such
 * a line, as in the jar. Where no row names a folder there is none: no folder is guessed from the current directory.
 *
 * @param folder the folder, absolute and normalised, or the value as given where no path can hold it
 * @param source {@code system-property}, {@code environment} or {@code file}
 * @param name the property or variable read, or the file's name for {@code file}
 */
public record WorkingDirectoryDefault(String folder, String source, String name) {

    static final String SYSTEM_PROPERTY = "system-property";

    static final String ENVIRONMENT = "environment";

    static final String FILE = "file";

    private static final String WORKING_DIR = "vespera.working-dir";

    private static final String DB_DIR = "db-dir";

    private static final String APPLICATION_YAML = "application.yaml";

    private static final String APPLICATION_LOCAL_YAML = "application-local.yaml";

    /** What the shipped {@code application.yaml}'s line reads (ADR-436 §1). */
    private static final String SHIPPED_LINE = "${db-dir:}";

    private static final List<String> WORKING_DIR_VARIABLES = List.of("VESPERA_WORKINGDIR", "VESPERA_WORKING_DIR",
            "vespera_workingdir", "vespera_working_dir");

    private static final List<String> DB_DIR_VARIABLES = List.of("DBDIR", "DB_DIR", "dbdir", "db_dir");

    /** Where a line's value came from: a name read, or the file's own text. */
    private record Value(String text, String source, String name) {
    }

    /**
     * As the four-argument {@code of} over a folder beside the jar holding the shipped copy alone, whose line is
     * {@code ${db-dir:}}, so that a test names no file.
     */
    public static Optional<WorkingDirectoryDefault> of(Path currentDirectory, Map<String, String> systemProperties,
            Map<String, String> environment) {
        return of(currentDirectory, systemProperties, environment,
                List.of(new Line(APPLICATION_YAML, SHIPPED_LINE)));
    }

    /**
     * Resolves §2's table from the maps given, which {@code ControlPanel.main} fills from the JVM's own, and the two
     * files of {@code besideTheJar}, where they are there and set the line; empty where no row names a folder.
     */
    public static Optional<WorkingDirectoryDefault> of(Path currentDirectory, Path besideTheJar,
            Map<String, String> systemProperties, Map<String, String> environment) {
        List<Line> lines = new ArrayList<>();
        for (String file : List.of(APPLICATION_LOCAL_YAML, APPLICATION_YAML)) {
            lineOf(besideTheJar, file).ifPresent(lines::add);
        }
        return of(currentDirectory, systemProperties, environment, lines);
    }

    /** The line {@code vespera.working-dir} has in a file, as it is written. */
    private record Line(String file, String text) {
    }

    private static Optional<WorkingDirectoryDefault> of(Path currentDirectory, Map<String, String> systemProperties,
            Map<String, String> environment, List<Line> lines) {
        Path current = currentDirectory.toAbsolutePath().normalize();
        String set = set(systemProperties, WORKING_DIR);
        if (set != null) {
            return Optional.of(new WorkingDirectoryDefault(resolved(current, set), SYSTEM_PROPERTY, WORKING_DIR));
        }
        for (String variable : WORKING_DIR_VARIABLES) {
            set = set(environment, variable);
            if (set != null) {
                return Optional.of(new WorkingDirectoryDefault(resolved(current, set), ENVIRONMENT, variable));
            }
        }
        Resolution resolution = new Resolution(current, systemProperties, environment);
        for (Line line : lines) {
            Optional<Value> value = resolution.of(line.text());
            if (value.isPresent()) {
                Value read = value.get();
                String source = read.source() == null ? FILE : read.source();
                String name = read.source() == null ? line.file() : read.name();
                return Optional.of(new WorkingDirectoryDefault(resolved(current, read.text()), source, name));
            }
        }
        return Optional.empty();
    }

    /** The text of {@code vespera.working-dir} in {@code file} beside the jar, none where it is not there, unreadable or unset. */
    private static Optional<Line> lineOf(Path besideTheJar, String file) {
        try {
            Path path = besideTheJar.resolve(file);
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            JsonNode node = new YAMLMapper().readTree(Files.readString(path, StandardCharsets.UTF_8));
            String key = node.has(WORKING_DIR) ? WORKING_DIR : null;
            JsonNode found = key != null ? node.get(key) : node.path("vespera").path("working-dir");
            if (found.isMissingNode() || found.isNull() || !found.isValueNode()) {
                return Optional.empty();
            }
            return Optional.of(new Line(file, found.asString()));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** A line's placeholders resolved as Spring resolves them (ADR-436 §2). */
    private static final class Resolution {

        private final Path current;
        private final Map<String, String> systemProperties;
        private final Map<String, String> environment;

        Resolution(Path current, Map<String, String> systemProperties, Map<String, String> environment) {
            this.current = current;
            this.systemProperties = systemProperties;
            this.environment = environment;
        }

        /** The value of {@code line}, empty where it is blank or a placeholder resolves to nothing and has no default. */
        Optional<Value> of(String line) {
            String text = line.strip();
            if (text.startsWith("${") && closing(text, 0) == text.length() - 1) {
                String inner = text.substring(2, text.length() - 1);
                int colon = colonOf(inner);
                String name = colon < 0 ? inner : inner.substring(0, colon);
                if (name.equals(DB_DIR)) {
                    Optional<Value> fromDbDir = dbDir();
                    if (fromDbDir.isPresent()) {
                        return fromDbDir;
                    }
                }
            }
            String resolved = resolve(line);
            return resolved == null || resolved.isBlank() ? Optional.empty()
                    : Optional.of(new Value(resolved.strip(), null, null));
        }

        /** {@code db-dir}: the system property, then its variable in Spring's four spellings, the first set and not blank. */
        private Optional<Value> dbDir() {
            String set = set(systemProperties, DB_DIR);
            if (set != null) {
                return Optional.of(new Value(set, SYSTEM_PROPERTY, DB_DIR));
            }
            for (String variable : DB_DIR_VARIABLES) {
                set = set(environment, variable);
                if (set != null) {
                    return Optional.of(new Value(set, ENVIRONMENT, variable));
                }
            }
            return Optional.empty();
        }

        /** {@code text} with each placeholder resolved, or null where one resolves to nothing and has no default. */
        private String resolve(String text) {
            StringBuilder out = new StringBuilder();
            int at = 0;
            while (at < text.length()) {
                int open = text.indexOf("${", at);
                if (open < 0) {
                    out.append(text, at, text.length());
                    break;
                }
                int close = closing(text, open);
                if (close < 0) {
                    out.append(text, at, text.length());
                    break;
                }
                out.append(text, at, open);
                String inner = text.substring(open + 2, close);
                int colon = colonOf(inner);
                String name = colon < 0 ? inner : inner.substring(0, colon);
                String value = lookup(name.strip());
                if (value == null && colon >= 0) {
                    value = resolve(inner.substring(colon + 1));
                }
                if (value == null) {
                    return null;
                }
                out.append(value);
                at = close + 1;
            }
            return out.toString();
        }

        private String lookup(String name) {
            if (name.equals(DB_DIR)) {
                return dbDir().map(Value::text).orElse(null);
            }
            if (name.equals("user.dir")) {
                return current.toString();
            }
            String property = set(systemProperties, name);
            return property != null ? property : set(environment, name);
        }

        /** The index of the {@code }} closing the {@code ${} at {@code open}, nesting counted, or -1. */
        private static int closing(String text, int open) {
            int depth = 0;
            for (int i = open; i < text.length(); i++) {
                if (text.startsWith("${", i)) {
                    depth++;
                    i++;
                } else if (text.charAt(i) == '}' && --depth == 0) {
                    return i;
                }
            }
            return -1;
        }

        /** The first {@code :} of a placeholder's inside that is not within a nested placeholder, or -1. */
        private static int colonOf(String inner) {
            int depth = 0;
            for (int i = 0; i < inner.length(); i++) {
                if (inner.startsWith("${", i)) {
                    depth++;
                    i++;
                } else if (inner.charAt(i) == '}') {
                    depth--;
                } else if (inner.charAt(i) == ':' && depth == 0) {
                    return i;
                }
            }
            return -1;
        }
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
