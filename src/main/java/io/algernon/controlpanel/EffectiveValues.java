package io.algernon.controlpanel;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * What the jar says it would use where the profile names nothing, read from {@code effective-values.properties} in the
 * working directory (ADR-330 §1): each value on the file's closed list with the {@code <key>.source} beside it.
 *
 * <p>Parsed with {@code java.util.Properties} alone, as {@link NextInvocation} is, because the control panel links no
 * class of the jar (ADR-285 §3). The jar moves the file into place whole and never deletes it, so it is read at any time;
 * a file that cannot be read is treated as absent. A file older than the jar beside the control panel is stale: still
 * shown, but it may speak of a jar since replaced.
 *
 * @param values what the file names, by key, in the file's own words
 * @param stale whether the file is older than the jar the control panel starts
 */
record EffectiveValues(Map<String, Value> values, boolean stale) {

    /** The file's name, in the working directory beside the database file. */
    static final String FILE_NAME = "effective-values.properties";

    /** The source a value is given when the file names none beside it, which the jar always does. */
    private static final String SOURCE_UNSAID = "unknown";

    /** One value of the file: what it is and where it came from. */
    record Value(String value, String source) {
    }

    /** What the jar has said of nothing at all: an absent file, or one that cannot be read. */
    static EffectiveValues none() {
        return new EffectiveValues(Map.of(), false);
    }

    /**
     * The file in {@code workingDirectory}, or empty where there is none or it cannot be read.
     *
     * @param jar the jar the control panel starts, whose modification time the file is compared with
     */
    static Optional<EffectiveValues> read(Optional<Path> workingDirectory, Path jar) {
        if (workingDirectory.isEmpty()) {
            return Optional.empty();
        }
        Path file = workingDirectory.get().resolve(FILE_NAME);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, Value> values = new LinkedHashMap<>();
            for (String name : properties.stringPropertyNames()) {
                if (name.endsWith(".source") || name.equals("written-at")) {
                    continue;
                }
                String source = properties.getProperty(name + ".source", SOURCE_UNSAID);
                values.put(name, new Value(properties.getProperty(name), source));
            }
            return Optional.of(new EffectiveValues(Map.copyOf(values), isOlder(file, jar)));
        } catch (IOException | IllegalArgumentException | SecurityException unreadable) {
            return Optional.empty();
        }
    }

    /** Whether {@code file} was last written before {@code jar}; false where either time cannot be read. */
    private static boolean isOlder(Path file, Path jar) {
        try {
            FileTime written = Files.getLastModifiedTime(file);
            FileTime jarWritten = Files.getLastModifiedTime(jar);
            return written.compareTo(jarWritten) < 0;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /** The value the file names for {@code key}, empty where it names none. */
    Optional<Value> named(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
