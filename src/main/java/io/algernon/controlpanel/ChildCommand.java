package io.algernon.controlpanel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line, environment and current directory of the child the control panel starts (ADR-278 §1, ADR-285 §5):
 * only the jar's {@code run} and {@code label} commands, after both encoding flags, with absolute paths and
 * {@code CI=1}.
 */
public final class ChildCommand {

    private final List<String> command;
    private final Path directory;
    private final Map<String, String> environment;

    private ChildCommand(Path java, Path jar, LaunchValues values, Path workingDirectory, List<String> arguments) {
        this.environment = childEnvironment(values);
        this.directory = workingDirectory.toAbsolutePath();
        List<String> line = new ArrayList<>(List.of(
                java.toString(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar",
                jar.toAbsolutePath().toString()));
        line.addAll(arguments);
        line.add("--db-dir=" + directory);
        this.command = List.copyOf(line);
    }

    /** @throws IllegalArgumentException with {@link LaunchValues#refusalToRun()}'s line where there is one (ADR-292 §7) */
    public static ChildCommand run(Path java, Path jar, LaunchValues values) {
        values.refusalToRun().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()),
                List.of("run", Path.of(values.corpusRoot()).toAbsolutePath().toString()));
    }

    /** @throws IllegalArgumentException with {@link LaunchValues#refusalToLabel()}'s line where there is one (ADR-292 §7) */
    public static ChildCommand label(Path java, Path jar, boolean auto, LaunchValues values) {
        values.refusalToLabel().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()),
                auto ? List.of("label", "--auto") : List.of("label"));
    }

    public List<String> command() {
        return command;
    }

    /** What is added to the child's environment: {@code CI=1} and the launch values' five (ADR-292 §4). */
    public Map<String, String> environment() {
        return environment;
    }

    private static Map<String, String> childEnvironment(LaunchValues values) {
        Map<String, String> added = new LinkedHashMap<>();
        added.put("CI", "1");
        added.putAll(values.toChildEnvironment());
        return Collections.unmodifiableMap(added);
    }

    public Path directory() {
        return directory;
    }

    public ProcessBuilder processBuilder() {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment());
        builder.directory(directory.toFile());
        return builder;
    }
}
