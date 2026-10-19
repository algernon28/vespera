package io.algernon.controlpanel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * The control panel's entry point, which the operator starts with {@code java -jar} (ADR-288 §1): it finds the jar
 * beside its own and the java running it, and says what it will start.
 */
public final class ControlPanel {

    private ControlPanel() {
    }

    public static void main(String[] arguments) throws IOException, URISyntaxException {
        Path location = Path.of(ControlPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try {
            ChildRuntime runtime = ChildRuntime.find(location, jarNameIn(location),
                    Path.of(System.getProperty("java.home")), System.getProperty("os.name"));
            System.out.println("the control panel will start " + runtime.jar().toAbsolutePath() + " with "
                    + runtime.java());
        } catch (FileNotFoundException refusal) {
            System.err.println(refusal.getMessage());
            System.exit(1);
        }
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
