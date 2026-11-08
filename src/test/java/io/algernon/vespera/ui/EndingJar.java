package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A jar whose main class ends at once with exit code 0, started with the java running the tests, so that the control
 * panel's state starts a child that ends as an invocation that ended does (ADR-278 §5) and is never Vespera. Written once
 * for the test JVM, in a folder of its own, as {@link SleepingJar} is and for the same reason.
 */
final class EndingJar {

    private static Path jar;

    private EndingJar() {
    }

    /** The java running the tests, and the jar that ends at once. */
    static ChildRuntime runtime() throws IOException {
        return new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"), theJar());
    }

    private static synchronized Path theJar() throws IOException {
        if (jar == null) {
            Path folder = Files.createTempDirectory("vespera-ending-jar");
            folder.toFile().deleteOnExit();
            jar = SleepingJar.write(folder.resolve("ending.jar"), AnEndingChildForTests.class);
            jar.toFile().deleteOnExit();
        }
        return jar;
    }
}
