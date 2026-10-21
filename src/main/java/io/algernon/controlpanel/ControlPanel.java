package io.algernon.controlpanel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            ControlPanelServer server = serve(runtime, launchValuesFile,
                    corpusRootLockFolder(System.getenv(CORPUS_ROOT_LOCK_FOLDER_VARIABLE), System.getProperty("user.home")),
                    userName);
            System.out.println("open " + server.firstAddress() + " in your browser");
            System.out.flush();
            Thread.currentThread().join();
        } catch (FileNotFoundException refusal) {
            System.err.println(refusal.getMessage());
            System.exit(1);
        }
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
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("the control panel ends its child")
                .unstarted(() -> {
                    try {
                        state.endWithTheControlPanel();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }));
        return ControlPanelServer.start(ControlPanelPages.pages(state));
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
