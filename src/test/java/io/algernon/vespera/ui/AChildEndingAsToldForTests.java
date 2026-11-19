package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The main class of {@link PrintingJars#endingAsTold()}: a child that ends as the test tells it to, in the file
 * {@link #TOLD} of the working directory its {@code --db-dir=} names. The file's first line is the exit code; its second
 * the line the child prints, nothing where it is empty; and the lines after them, where there are any, the {@code
 * report.json} it writes in that working directory before it prints, as the jar writes its last report before its
 * closing line, {@link #ITS_PID} replaced by its own pid so that the window takes the report for its child's (ADR-362
 * §8). With no lines after the second it writes no report, as a command refused before the lock writes none. It then
 * prints a report line, as the jar prints one after its last line, and exits with the code. It is the jar's main class
 * alone, naming no other class.
 */
public final class AChildEndingAsToldForTests {

    /** The file of the working directory that tells the child how to end. */
    public static final String TOLD = "ending-as-told.txt";

    /** What the report the test hands it says of its pid, which it replaces by its own. */
    public static final String ITS_PID = "\"pid\":0,";

    private AChildEndingAsToldForTests() {
    }

    public static void main(String[] args) throws IOException {
        Path folder = null;
        for (String argument : args) {
            if (argument.startsWith("--db-dir=")) {
                folder = Path.of(argument.substring("--db-dir=".length()));
            }
        }
        if (folder == null) {
            System.out.println("no --db-dir= was passed");
            System.exit(1);
        }
        List<String> told = Files.readAllLines(folder.resolve(TOLD), StandardCharsets.UTF_8);
        int exitCode = Integer.parseInt(told.get(0).strip());
        String line = told.size() > 1 ? told.get(1) : "";
        if (told.size() > 2) {
            String report = String.join("\n", told.subList(2, told.size()))
                    .replace(ITS_PID, "\"pid\":" + ProcessHandle.current().pid() + ",");
            Files.writeString(folder.resolve("report.json"), report, StandardCharsets.UTF_8);
        }
        if (!line.isEmpty()) {
            System.out.println(line);
        }
        System.out.println("Report: " + folder.resolve("report.html"));
        System.out.flush();
        System.exit(exitCode);
    }
}
