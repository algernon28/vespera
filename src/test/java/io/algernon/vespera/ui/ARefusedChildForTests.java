package io.algernon.vespera.ui;

/**
 * The main class of {@link PrintingJars#refusing()}: it prints the line of an invocation refused before it took the
 * lock, and then a report line, as the jar prints one after its last line, and exits with code 1 having written no
 * report.
 */
public final class ARefusedChildForTests {

    /** The last line of its output that is neither blank nor a report line. */
    public static final String ITS_LAST_LINE = "Run is refused: the corpus root is not there.";

    private ARefusedChildForTests() {
    }

    public static void main(String[] args) {
        System.out.println(ITS_LAST_LINE);
        System.out.println();
        System.out.println("Report: /nowhere/report.html");
        System.out.flush();
        System.exit(1);
    }
}
