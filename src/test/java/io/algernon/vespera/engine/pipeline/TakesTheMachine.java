package io.algernon.vespera.engine.pipeline;

import java.nio.file.Path;

/**
 * The child {@link HoldsTheMachine} starts, as the control panel starts its own (ADR-394 §3): it takes the machine
 * lock in the folder named by its one argument as {@code vespera run} would, its parent read from the system, and
 * prints one line, {@value #LET_THROUGH} and its pid where it was let through, or {@value #REFUSED} and the refusal's
 * line; let through, it sleeps, holding {@code machine-child.lock}, until it is killed.
 */
public final class TakesTheMachine {

    /** What the line begins with where the take was let through; the pid follows. */
    public static final String LET_THROUGH = "let through ";

    /** What the line begins with where it was refused; the refusal's line follows. */
    public static final String REFUSED = "refused: ";

    /** The arguments the take records, as a {@code vespera run} of the control panel's would. */
    static final String[] ARGUMENTS = {"run", "a-corpus-root"};

    /** Longer than any test waits; the test kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    private TakesTheMachine() {
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            new MachineLock(Path.of(args[0])).take(MachineLock.Kind.RUN, ARGUMENTS);
        } catch (RuntimeException refused) {
            System.out.println(REFUSED + refused.getMessage());
            System.out.flush();
            return;
        }
        System.out.println(LET_THROUGH + ProcessHandle.current().pid());
        System.out.flush();
        Thread.sleep(SLEEP_MILLIS);
    }
}
