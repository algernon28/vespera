package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpHandler;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/**
 * The control panel's entry point, which the operator starts with {@code java -jar} (ADR-288 §1): it finds the jar
 * beside its own and the java running it, says what it will start and where the operator opens it, and serves until its
 * process ends (ADR-302 §11), ending the child it started with it where that end runs the JVM's shutdown hooks
 * (ADR-307).
 */
public final class ControlPanel {

    /** The variable of the environment that names the folder the corpus-root locks lie in (ADR-302 §2; ADR-279). */
    static final String CORPUS_ROOT_LOCK_FOLDER_VARIABLE = "VESPERA_CORPUS_ROOT_LOCK_FOLDER";

    /** The variable of the environment that names the port the control panel listens at (ADR-325 §1). */
    static final String PORT_VARIABLE = "VESPERA_CONTROL_PANEL_PORT";

    /** The port the control panel listens at where the environment names none (ADR-325 §1). */
    static final int FIXED_PORT = 24291;

    /** The line a start at a port of its own writes before it waits, whatever bound it was handed (ADR-325 §3). */
    static final String WAITING = "the control panel waits up to 5 seconds for a tab it opened before";

    private static final int MAXIMUM_PORT = 65535;

    /** How long a start at a port of its own waits for a tab to come back (ADR-325 §3). */
    private static final Duration TAB_BOUND = Duration.ofSeconds(5);

    private ControlPanel() {
    }

    public static void main(String[] arguments) throws IOException, URISyntaxException, InterruptedException {
        Path location = Path.of(ControlPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try {
            ChildRuntime runtime = ChildRuntime.find(location, jarNameIn(location),
                    Path.of(System.getProperty("java.home")), System.getProperty("os.name"));
            System.out.println("the control panel will start " + runtime.jar().toAbsolutePath() + " with "
                    + runtime.java());
            String userName = System.getProperty("user.name");
            Path launchValuesFile = LaunchValues.fileIn(Path.of(System.getProperty("java.io.tmpdir")), userName);
            ControlPanelState state = new ControlPanelState(runtime, launchValuesFile,
                    corpusRootLockFolder(System.getenv(CORPUS_ROOT_LOCK_FOLDER_VARIABLE), System.getProperty("user.home")),
                    userName);
            endChildWithThisJvm(state);
            Optional<ControlPanelServer> server = begin(state, System.getenv(PORT_VARIABLE),
                    TAB_BOUND, new SystemBrowserOpener(), line -> {
                        System.out.println(line);
                        System.out.flush();
                    }, line -> {
                        System.err.println(line);
                        System.err.flush();
                    });
            if (server.isEmpty()) {
                System.exit(1);
            }
            Thread.currentThread().join();
        } catch (FileNotFoundException refusal) {
            System.err.println(refusal.getMessage());
            System.exit(1);
        }
    }

    /**
     * ADR-325 §3's start, after the line naming the jar: reads the port the variable names and refuses one it cannot
     * (§1), binds it or, where it is taken, one the system picks, and at a port of its own waits up to {@code bound}
     * for a tab it opened to come back, opening none where one does and otherwise printing the address and asking
     * {@code opener} to open it. Every line is handed to {@code out} or {@code err}, which flush. Empty where the
     * variable names no port, the control panel having said so on {@code err}.
     */
    static Optional<ControlPanelServer> begin(ControlPanelState state, String portNamed, Duration bound,
            BrowserOpener opener, Consumer<String> out, Consumer<String> err) throws IOException {
        OptionalInt requested = requestedPort(portNamed);
        if (requested.isEmpty()) {
            err.accept(PORT_VARIABLE + " is " + portNamed
                    + ", and the control panel listens only at a port from 0 to 65535");
            return Optional.empty();
        }
        Map<String, HttpHandler> pages = ControlPanelPages.pages(state);
        ControlPanelServer server;
        try {
            server = ControlPanelServer.start(pages, requested.getAsInt());
        } catch (BindException taken) {
            err.accept("the port " + requested.getAsInt() + " is taken, so the control panel listens at a port the"
                    + " system picked, and no tab it opened before can come back to it");
            server = ControlPanelServer.start(pages, 0);
            requested = OptionalInt.of(0);
        }
        if (requested.getAsInt() != 0) {
            out.accept(WAITING);
            try {
                if (server.awaitTab(bound)) {
                    out.accept("a tab it opened before came back to " + server.firstAddress()
                            + ", so the control panel opens no other");
                    return Optional.of(server);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        URI address = server.firstAddress();
        out.accept("open " + address + " in your browser");
        try {
            opener.open(address);
        } catch (IOException | RuntimeException failed) {
            err.accept("the control panel could not open the browser: " + failed.getClass().getName());
        }
        return Optional.of(server);
    }

    /**
     * The port {@code named} asks for: 24291 where it is absent or blank, the port a decimal integer from 0 to 65535
     * names, and empty for anything else (ADR-325 §1).
     */
    static OptionalInt requestedPort(String named) {
        if (named == null || named.isBlank()) {
            return OptionalInt.of(FIXED_PORT);
        }
        if (!named.matches("[0-9]{1,5}") || Integer.parseInt(named) > MAXIMUM_PORT) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(named));
    }

    /**
     * Starts the server on the pages of a {@link ControlPanelState} of its own, having first registered a shutdown hook
     * that ends that state's child with this JVM (ADR-307 §1): it runs as the JVM ends in an orderly way, on
     * {@code Ctrl+C}, on its terminal or console closed, and on a request of the system to end it; a forced end of the
     * process runs no hook.
     */
    static ControlPanelServer serve(ChildRuntime runtime, Path launchValuesFile, Path corpusRootLockFolder,
            String userName) throws IOException {
        ControlPanelState state = new ControlPanelState(runtime, launchValuesFile, corpusRootLockFolder, userName);
        endChildWithThisJvm(state);
        return ControlPanelServer.start(ControlPanelPages.pages(state));
    }

    private static void endChildWithThisJvm(ControlPanelState state) {
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

    /** The {@code Vespera-Jar} of the manifest of the jar at the location; {@code null} where it is not a jar. */
    private static String jarNameIn(Path location) throws IOException {
        if (!Files.isRegularFile(location)) {
            return null;
        }
        try (JarFile jar = new JarFile(location.toFile())) {
            return jar.getManifest() == null ? null : jar.getManifest().getMainAttributes().getValue("Vespera-Jar");
        }
    }
}
