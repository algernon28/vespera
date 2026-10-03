package io.algernon.vespera.pipeline;

import org.springframework.boot.SpringBootExceptionReporter;
import picocli.CommandLine;

/**
 * Says, in one line, that another invocation already holds the working directory, in place of Spring's
 * account of a start-up that failed (ADR-177 §1, #364).
 *
 * <p>The same shape as {@link MisshapenProfileRefusal}, for the same reason: {@link
 * WorkingDirectoryLock} refuses while the environment is prepared, so there is no banner, no bean and
 * no context refresh to report it. It prints {@link WorkingDirectoryInUseException}'s message to
 * standard error and claims nothing else.
 *
 * <p>It does not end the process. The exit code is {@code VesperaApplication.main}'s to deliver
 * (ADR-141), and it asks {@link #refuses} whether the failure is this one.
 */
public class WorkingDirectoryInUseRefusal implements SpringBootExceptionReporter {

    /** What the invocation exits with: the code a refused profile already returns (#321). */
    public static final int EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    @Override
    public boolean reportException(Throwable failure) {
        WorkingDirectoryInUseException inUse = inUseIn(failure);
        if (inUse == null) {
            return false;
        }
        System.err.println(inUse.getMessage());
        return true;
    }

    /** Whether a failed start was a working directory in use, and so has already been said. */
    public static boolean refuses(Throwable failure) {
        return inUseIn(failure) != null;
    }

    /** The refusal anywhere in the chain: unwrapped from the listener, or beneath another failure. */
    private static WorkingDirectoryInUseException inUseIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof WorkingDirectoryInUseException inUse) {
                return inUse;
            }
        }
        return null;
    }
}
