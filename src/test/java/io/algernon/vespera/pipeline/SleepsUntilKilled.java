package io.algernon.vespera.pipeline;

/** A program that prints one line and then sleeps until it is killed, for {@link SystemProcessRunnerTest}. */
final class SleepsUntilKilled {

    /** The line it prints before it sleeps. */
    static final String STARTED = "started and sleeping";

    private SleepsUntilKilled() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(STARTED);
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
