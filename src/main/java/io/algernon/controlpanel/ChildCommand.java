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
 * absolute paths and {@code CI=1}, then the options of the command line the operator set in the window (ADR-344 §4). A
 * publish's child inherits the five values it reads from the operator's environment, as every child inherits it; the
 * control panel adds none of them.
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
                    "Passed by Let the local model answer; Record my answers passes none."),
            new Argument("label", "<file>", "label-file",
                    "Passed by the label file option, absolute, to Record my answers alone: label --auto refuses one"
                            + " (ADR-197 §6)."),
            new Argument("label", "db-dir", "vespera.working-dir", "As for run."),
            new Argument("publish", "db-dir", "vespera.working-dir", "As for run; Publish stands in the action card."));

    /**
     * One option of the command line the window passes (ADR-344 §4): the jar's property, the commands it is passed
     * to, among {@code run}, {@code label} and {@code label --auto}, and why it is among the five.
     */
    public record Option(String property, List<String> commands, String reason) {
    }

    /** The five presets the window offers, in the order they are passed (ADR-344 §4). */
    public static final List<Option> OPTIONS = List.of(
            new Option("vespera.progress-interval", List.of("run", "label", "label --auto"),
                    "How often the progress page and its data file are rewritten; no run's identity holds it."),
            new Option("vespera.sidecars.start", List.of("run", "label --auto"),
                    "Whether the jar starts the sidecars; no run's identity holds it."),
            new Option("vespera.sidecars.wait-at-most", List.of("run", "label --auto"),
                    "How long the jar waits for the sidecars to answer; no run's identity holds it."),
            new Option("vespera.models.pull", List.of("run"),
                    "Whether a model Ollama does not serve is pulled; no run's identity holds it."),
            new Option("vespera.models.pull-stalled-at-most", List.of("run"),
                    "How long a pull may stall before it is given up; no run's identity holds it."));

    /**
     * What is not offered, each by its property and why (ADR-344 §4): the window passes none of them, and the control
     * panel's test holds that no option of {@link #OPTIONS} is among them.
     */
    public static final Map<String, String> NOT_OFFERED = Map.of(
            "vespera.open-browser", "The child runs with CI=1 and opens no browser whatever it says (ADR-278 §1).",
            "vespera.relevance.sample.bands",
            "It enters the scoring run's identity where it is not its default, and ADR-330 refuses a value of that kind"
                    + " passed from a file named for a machine.",
            "vespera.relevance.sample.per-band", "As vespera.relevance.sample.bands.",
            "vespera.corpus-root-lock-folder",
            "The window reads the corpus-root lock in the folder VESPERA_CORPUS_ROOT_LOCK_FOLDER names (ADR-279), and a"
                    + " folder passed to the child alone would hide its lock from the window.");

    private final Path java;
    private final Path jar;
    private final LaunchValues values;
    private final List<String> arguments;
    private final Map<String, String> passed;
    private final String commandName;
    private final List<String> command;
    private final Path directory;
    private final Map<String, String> environment;

    private ChildCommand(Path java, Path jar, LaunchValues values, Path workingDirectory, List<String> arguments,
            String commandName, Map<String, String> passed) {
        this.java = java;
        this.jar = jar;
        this.values = values;
        this.arguments = List.copyOf(arguments);
        this.commandName = commandName;
        this.passed = Map.copyOf(passed);
        this.environment = childEnvironment(values);
        this.directory = workingDirectory.toAbsolutePath();
        List<String> line = new ArrayList<>(List.of(
                java.toString(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar",
                jar.toAbsolutePath().toString()));
        line.addAll(arguments);
        line.add("--db-dir=" + directory);
        for (Option option : OPTIONS) {
            if (option.commands().contains(commandName) && this.passed.containsKey(option.property())) {
                line.add("--" + option.property() + "=" + this.passed.get(option.property()));
            }
        }
        this.command = List.copyOf(line);
    }

    /** @throws IllegalArgumentException with {@link LaunchValues#refusalToRun()}'s line where there is one (ADR-292 §7) */
    public static ChildCommand run(Path java, Path jar, LaunchValues values) {
        values.refusalToRun().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()),
                List.of("run", Path.of(values.corpusRoot()).toAbsolutePath().toString()), "run", Map.of());
    }

    /** @throws IllegalArgumentException with {@link LaunchValues#refusalToLabel()}'s line where there is one (ADR-292 §7) */
    public static ChildCommand label(Path java, Path jar, boolean auto, LaunchValues values) {
        values.refusalToLabel().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()),
                auto ? List.of("label", "--auto") : List.of("label"), auto ? "label --auto" : "label", Map.of());
    }

    /** @throws IllegalArgumentException with {@link LaunchValues#refusalToLabel()}'s line where there is one: a publish, as a label, needs the working directory alone */
    public static ChildCommand publish(Path java, Path jar, LaunchValues values) {
        values.refusalToLabel().ifPresent(line -> {
            throw new IllegalArgumentException(line);
        });
        return new ChildCommand(java, jar, values, Path.of(values.workingDirectory()), List.of("publish"), "publish",
                Map.of());
    }

    /**
     * The same command with each of {@code presets} that {@link #OPTIONS} lists for it added as
     * {@code --<property>=<value>} after the command's own arguments, in the table's order; a property not in the table,
     * or not listed for this command, is not passed (ADR-344 §4).
     */
    public ChildCommand withOptions(Map<String, String> presets) {
        return new ChildCommand(java, jar, values, directory, arguments, commandName, presets);
    }

    /** {@code label}'s {@code <file>}, absolute, before {@code --db-dir}; {@code label --auto}, {@code run} and {@code publish} are unchanged. */
    public ChildCommand withLabelFile(Path file) {
        if (!commandName.equals("label")) {
            return this;
        }
        return new ChildCommand(java, jar, values, directory, List.of("label", file.toAbsolutePath().toString()),
                commandName, passed);
    }

    /** {@code vespera} and the arguments after {@code -jar <jar>}, one holding a space in double quotes (ADR-344 §4). */
    public String shown() {
        List<String> after = command.subList(command.indexOf("-jar") + 2, command.size());
        StringBuilder line = new StringBuilder("vespera");
        for (String argument : after) {
            line.append(' ').append(argument.contains(" ") ? "\"" + argument + "\"" : argument);
        }
        return line.toString();
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
