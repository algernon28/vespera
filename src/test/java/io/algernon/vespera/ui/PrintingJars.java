package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Jars whose main class prints a line and then a report line, and either ends with exit code 1 ({@link #refusing()},
 * an invocation refused before it wrote a report) or sleeps until it is killed ({@link #printing()}, one that Stop
 * ends); and one whose main class prints a publish's refusal naming the control panel's Profile window and ends with
 * exit code 1 ({@link #refusedPublish()}). Each is written once for the test JVM, as {@link SleepingJar} is and for
 * the same reason.
 */
final class PrintingJars {

    private static Path refusing;
    private static Path printing;
    private static Path refusedPublish;

    private PrintingJars() {
    }

    static ChildRuntime refusing() throws IOException {
        return new ChildRuntime(theJava(), refusingJar());
    }

    static ChildRuntime printing() throws IOException {
        return new ChildRuntime(theJava(), printingJar());
    }

    static ChildRuntime refusedPublish() throws IOException {
        return new ChildRuntime(theJava(), refusedPublishJar());
    }

    private static Path theJava() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    }

    private static synchronized Path refusingJar() throws IOException {
        if (refusing == null) {
            refusing = written("refusing.jar", ARefusedChildForTests.class);
        }
        return refusing;
    }

    private static synchronized Path printingJar() throws IOException {
        if (printing == null) {
            printing = written("printing.jar", APrintingChildForTests.class);
        }
        return printing;
    }

    private static synchronized Path refusedPublishJar() throws IOException {
        if (refusedPublish == null) {
            refusedPublish = written("refused-publish.jar", ARefusedPublishChildForTests.class);
        }
        return refusedPublish;
    }

    private static Path written(String name, Class<?> main) throws IOException {
        Path folder = Files.createTempDirectory("vespera-printing-jar");
        folder.toFile().deleteOnExit();
        Path jar = SleepingJar.write(folder.resolve(name), main);
        jar.toFile().deleteOnExit();
        return jar;
    }
}
