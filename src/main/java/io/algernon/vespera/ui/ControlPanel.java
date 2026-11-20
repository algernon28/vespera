package io.algernon.vespera.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Application;

/**
 * The control panel's one entry (ADR-342 §2): {@link #open()} finds the runtime the child starts with, registers the
 * shutdown hook that ends the child with the JVM (ADR-307 §1), reads the four sources the window's fields start from
 * (ADR-344 §3), and launches {@link ControlPanelWindow}. It keeps no {@code main}: the jar's entry point calls it with no
 * argument, and does as the answer says.
 */
public final class ControlPanel {

    /** The variable of the environment that names the folder the corpus-root locks lie in (ADR-302 §2; ADR-279). */
    static final String CORPUS_ROOT_LOCK_FOLDER_VARIABLE = "VESPERA_CORPUS_ROOT_LOCK_FOLDER";

    /** How an opening came out, and the one line to say where it came to nothing. */
    public record Opening(Result result, String line) {

        /**
         * {@code CLOSED}: the window opened and was closed; {@code NO_JAR}: the class path names no jar;
         * {@code NO_WINDOW}: the toolkit could not start; {@code REFUSED}: the folder beside the jar cannot be written,
         * and no window opens (ADR-397 §3).
         */
        public enum Result { CLOSED, NO_JAR, NO_WINDOW, REFUSED }
    }

    private ControlPanel() {
    }

    /**
     * Opens the window and answers once it has closed. Where the class path names no jar the answer is
     * {@link Opening.Result#NO_JAR} with the refusal's line and no window is tried; where the toolkit throws as it starts,
     * on a machine with no display or from a jar built for another platform, {@link Opening.Result#NO_WINDOW} with
     * what it threw (ADR-342 §2). {@link ClassPathLine#install()} runs in step 3 of ADR-342 §2, immediately before
     * {@code Application.launch}, inside the attempt whose throw answers {@link Opening.Result#NO_WINDOW}, and drops JavaFX's class path line from its
     * logger before the toolkit starts (ADR-360 §2).
     */
    public static Opening open(Path besideTheJar, List<String> besideTheJarLines) {
        ChildRuntime runtime;
        try {
            runtime = ChildRuntime.find(System.getProperty("java.class.path"),
                    Path.of(System.getProperty("java.home")), System.getProperty("os.name"));
        } catch (FileNotFoundException refusal) {
            return new Opening(Opening.Result.NO_JAR, refusal.getMessage());
        }
        String userName = System.getProperty("user.name");
        StartingValues.Sources sources = readSources(besideTheJar);
        Path lockFolder = corpusRootLockFolder(System.getenv(CORPUS_ROOT_LOCK_FOLDER_VARIABLE),
                System.getProperty("user.home"));
        String home = System.getProperty("user.home");
        Path profileFolder = home == null || home.isBlank() ? null : Path.of(home.strip());
        ControlPanelState state = new ControlPanelState(runtime,
                LaunchValues.fileIn(Path.of(System.getProperty("java.io.tmpdir")), userName), lockFolder, userName,
                DockerControl.inProcess(dockerSettings(sources, lockFolder, profileFolder)));
        state.useSources(sources);
        state.useBesideTheJarLines(besideTheJarLines);
        Map<String, String> properties = new HashMap<>();
        System.getProperties().stringPropertyNames().forEach(name -> properties.put(name, System.getProperty(name)));
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(Path.of(""), properties, System.getenv()));
        endChildWithThisJvm(state);
        ControlPanelWindow.launchedWith(state);
        try {
            ClassPathLine.install();
            Application.launch(ControlPanelWindow.class);
        } catch (RuntimeException | LinkageError cannotStart) {
            String message = cannotStart.getMessage();
            return new Opening(Opening.Result.NO_WINDOW,
                    cannotStart.getClass().getName() + (message == null ? "" : ": " + message));
        }
        return new Opening(Opening.Result.CLOSED, "");
    }

    /**
     * What the Docker engine's code is given (ADR-390 §4): the WSL distribution and {@code vespera.sidecars.wait-at-most}
     * from the sources the window's fields start from, and the corpus-root lock folder, where {@code sidecars.lock} lies
     * too.
     */
    static DockerControl.Settings dockerSettings(
            StartingValues.Sources sources, Path lockFolder, Path profileFolder) {
        String distribution = StartingValues.of("vespera.sidecars.wsl-distribution", sources).value();
        String wait = StartingValues.of("vespera.sidecars.wait-at-most", sources).value();
        java.time.Duration waitAtMost = waitAtMostOf(wait);
        return new DockerControl.Settings(distribution, waitAtMost, lockFolder, profileFolder, null, sources);
    }

    /**
     * The wait as the jar reads it through Spring's {@code DurationStyle}, which the window may not use: a number with
     * {@code ns}, {@code us}, {@code ms}, {@code s}, {@code m}, {@code h} or {@code d} (a bare number being
     * milliseconds), or ISO-8601 as {@link java.time.Duration#parse} reads it. Anything else, or less than a second,
     * is 180 seconds or one second respectively.
     */
    static java.time.Duration waitAtMostOf(String wait) {
        java.time.Duration fallback = java.time.Duration.ofSeconds(180);
        String text = wait == null ? "" : wait.strip();
        java.time.Duration read = fallback;
        try {
            java.util.regex.Matcher simple = java.util.regex.Pattern.compile("([+-]?\\d+)([a-zA-Z]{0,2})").matcher(text);
            if (text.matches("[+-]?[pP].*")) {
                read = java.time.Duration.parse(text);
            } else if (simple.matches()) {
                long number = Long.parseLong(simple.group(1));
                read = switch (simple.group(2).toLowerCase(java.util.Locale.ROOT)) {
                    case "ns" -> java.time.Duration.ofNanos(number);
                    case "us" -> java.time.Duration.of(number, java.time.temporal.ChronoUnit.MICROS);
                    case "", "ms" -> java.time.Duration.ofMillis(number);
                    case "s" -> java.time.Duration.ofSeconds(number);
                    case "m" -> java.time.Duration.ofMinutes(number);
                    case "h" -> java.time.Duration.ofHours(number);
                    case "d" -> java.time.Duration.ofDays(number);
                    default -> fallback;
                };
            }
        } catch (java.time.format.DateTimeParseException | ArithmeticException | NumberFormatException unreadable) {
            read = fallback;
        }
        return read.compareTo(java.time.Duration.ofSeconds(1)) < 0 ? java.time.Duration.ofSeconds(1) : read;
    }

    /**
     * Registers a shutdown hook that ends {@code state}'s child with this JVM (ADR-307 §1): it runs as the JVM ends in
     * an orderly way, on {@code Ctrl+C}, on its terminal or console closed, and on a request of the system to end it;
     * a forced end of the process runs no hook.
     */
    static void endChildWithThisJvm(ControlPanelState state) {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("the control panel ends its child")
                .unstarted(() -> {
                    try {
                        state.endWithTheControlPanel();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }));
    }

    /**
     * The folder the corpus-root locks lie in (ADR-302 §2; ADR-279): the one {@code VESPERA_CORPUS_ROOT_LOCK_FOLDER}
     * names where it is not blank, and {@code .vespera-root-locks} in the user's home otherwise. A folder named for the
     * jar by a system property, a configuration file or an argument is not seen.
     */
    static Path corpusRootLockFolder(String named, String userHome) {
        return named == null || named.isBlank() ? Path.of(userHome).resolve(".vespera-root-locks") : Path.of(named);
    }

    /**
     * What the environment, {@code .env} in the current directory and {@code application.yaml} beside the jar say (ADR-397 §10), read
     * once for the window's fields (ADR-344 §3). A source that cannot be read says nothing; {@code .env} is never written.
     */
    private static StartingValues.Sources readSources(Path besideTheJar) {
        Map<String, String> dotEnv = Map.of();
        try {
            Path file = Path.of("").toAbsolutePath().resolve(".env");
            if (Files.isRegularFile(file)) {
                dotEnv = StartingValues.readDotEnv(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException unreadable) {
            dotEnv = Map.of();
        }
        Map<String, String> yaml = Map.of();
        try {
            Path file = besideTheJar.resolve("application.yaml");
            if (Files.isRegularFile(file)) {
                yaml = StartingValues.readApplicationYaml(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException unreadable) {
            yaml = Map.of();
        }
        return new StartingValues.Sources(Map.of(), Map.copyOf(System.getenv()), dotEnv, yaml);
    }
}
