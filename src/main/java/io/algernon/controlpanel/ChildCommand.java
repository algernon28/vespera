package io.algernon.controlpanel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line, environment and current directory of the child the control panel starts (ADR-278 §1, ADR-285 §5):
 * only the jar's {@code run}, {@code label} and {@code publish} commands (ADR-308 §6), after both encoding flags, with
 * absolute paths and {@code CI=1}. A publish's child inherits the five values it reads from the operator's environment,
 * as every child inherits it; the control panel adds none of them.
 */
public final class ChildCommand {

    /** One option or positional argument of picocli's model of the jar's commands, and how the control panel sets it (ADR-336 §2). */
    public record Argument(String command, String name, String control, String reason) {
    }

    /** Every option and positional argument of {@code vespera} and of each of its commands, each once (ADR-336 §2). */
    public static final List<Argument> ARGUMENTS = List.of(
            new Argument("vespera", "help", "",
                    "Never passed (ADR-278 §1): it boots the context and takes the working directory's lock, and prints"
                            + " nothing the control panel reads."),
            new Argument("vespera", "version", "",
                    "Never passed (ADR-278 §1): it boots the context, takes the lock and prints no version."),
            new Argument("run", "<root>", "vespera.corpus-root", "The corpus root field, made absolute."),
            new Argument("run", "db-dir", "vespera.working-dir",
                    "The working directory field, made absolute; also the child's current directory."),
            new Argument("label", "auto", "label-auto",
                    "Passed by Have the local model answer, here and on the next page; Label passes none."),
            new Argument("label", "<file>", "",
                    "Never passed: label reads the label file the last run wrote in the working directory, which the"
                            + " Relevance page answers."),
            new Argument("label", "db-dir", "vespera.working-dir", "As for run."),
            new Argument("publish", "db-dir", "vespera.working-dir",
                    "As for run; Publish stands on the invocation page."),
            new Argument("publish", "to", "to",
                    "The checkboxes of the invocation page's Publish, one for each destination drawn (ADR-333 §6); not"
                            + " a control of the launch form."));

    /**
     * The destinations the control panel offers, which {@code publish --to} names, in the order the invocation page
     * offers them: a closed list of the control panel's own, which names no class of the jar (ADR-285 §3, ADR-333 §6).
     * A test holds it to the names of the plugins compiled into the jar.
     */
    public static final List<String> DESTINATIONS = List.of("confluence", "website");

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

    /**
     * {@code publish --to <destinations>}, joined by commas in the order given (ADR-333 §6).
     *
     * @throws IllegalArgumentException with {@link LaunchValues#refusalToLabel()}'s line where there is one: a publish,
     *     as a label, needs the working directory alone; or with a line of its own where {@code destinations} is empty
     *     or names one outside {@link #DESTINATIONS}
     */
    public static ChildCommand publish(Path java, Path jar, LaunchValues values, List<String> destinations) {
        values.refusalToLabel().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        if (destinations.isEmpty()) {
            throw new IllegalArgumentException("no destination is checked");
        }
        if (!DESTINATIONS.containsAll(destinations)) {
            throw new IllegalArgumentException("a destination outside the ones the control panel offers was named");
        }
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()),
                List.of("publish", "--to", String.join(",", destinations)));
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
