package io.algernon.vespera.ui;

/**
 * The main class of {@link PrintingJars#printing()}: it prints a line and then a report line, as the jar prints one
 * after its last line, and sleeps until its process is killed.
 */
public final class APrintingChildForTests {

    /** The last line of its output that is neither blank nor a report line. */
    public static final String ITS_LAST_LINE = "Step extraction is running.";

    private APrintingChildForTests() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(ITS_LAST_LINE);
        System.out.println("Report: /nowhere/report.html");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
