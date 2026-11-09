package io.algernon.vespera.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

        /** {@code CLOSED}: the window opened and was closed; {@code NO_JAR}: the class path names no jar; {@code NO_WINDOW}: the toolkit could not start. */
        public enum Result { CLOSED, NO_JAR, NO_WINDOW }
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
    public static Opening open() {
        ChildRuntime runtime;
        try {
            runtime = ChildRuntime.find(System.getProperty("java.class.path"),
                    Path.of(System.getProperty("java.home")), System.getProperty("os.name"));
        } catch (FileNotFoundException refusal) {
            return new Opening(Opening.Result.NO_JAR, refusal.getMessage());
        }
        String userName = System.getProperty("user.name");
        ControlPanelState state = new ControlPanelState(runtime,
                LaunchValues.fileIn(Path.of(System.getProperty("java.io.tmpdir")), userName),
                corpusRootLockFolder(System.getenv(CORPUS_ROOT_LOCK_FOLDER_VARIABLE), System.getProperty("user.home")),
                userName, Reports.OPENING_NATIVELY);
        state.useSources(readSources());
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
     * What the environment, {@code .env} in the current directory and the jar's own {@code application.yaml} say, read
     * once for the window's fields (ADR-344 §3). A source that cannot be read says nothing; {@code .env} is never written.
     */
    private static StartingValues.Sources readSources() {
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
        try (InputStream in = ControlPanel.class.getResourceAsStream("/application.yaml")) {
            if (in != null) {
                yaml = StartingValues.readApplicationYaml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException unreadable) {
            yaml = Map.of();
        }
        return new StartingValues.Sources(Map.of(), Map.copyOf(System.getenv()), dotEnv, yaml);
    }
}
