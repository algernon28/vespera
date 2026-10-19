package io.algernon.controlpanel;

import java.nio.file.Path;
import java.util.ArrayList;
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

    private ChildCommand(Path java, Path jar, Path workingDirectory, List<String> arguments) {
        this.directory = workingDirectory.toAbsolutePath();
        List<String> line = new ArrayList<>(List.of(
                java.toString(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar",
                jar.toAbsolutePath().toString()));
        line.addAll(arguments);
        line.add("--db-dir=" + directory);
        this.command = List.copyOf(line);
    }

    public static ChildCommand run(Path java, Path jar, Path corpusRoot, Path workingDirectory) {
        return new ChildCommand(java, jar, workingDirectory, List.of("run", corpusRoot.toAbsolutePath().toString()));
    }

    public static ChildCommand label(Path java, Path jar, boolean auto, Path workingDirectory) {
        return new ChildCommand(java, jar, workingDirectory, auto ? List.of("label", "--auto") : List.of("label"));
    }

    public List<String> command() {
        return command;
    }

    /** What is added to the child's environment. */
    public Map<String, String> environment() {
        return Map.of("CI", "1");
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
