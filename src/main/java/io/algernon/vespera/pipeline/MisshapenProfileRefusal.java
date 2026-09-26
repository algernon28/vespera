package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.MisshapenProfileException;
import org.springframework.boot.SpringBootExceptionReporter;
import picocli.CommandLine;

/**
 * Says, in one line, that {@code profile.yaml} did not load, in place of Spring's account of a start-up
 * that failed (#321).
 *
 * <p>Spring Boot hands a failed start to every {@link SpringBootExceptionReporter} named in {@code
 * META-INF/spring.factories} before it logs the failure itself, and logs nothing more once one of them
 * says it has reported it. This is that reporter for one fault only: it prints {@link
 * MisshapenProfileException}'s message, which names the file, the key and the shape, to standard
 * error, where every other refusal of the command line is printed, and claims nothing else.
 *
 * <p>It does not end the process. The exit code is {@code VesperaApplication.main}'s to deliver, as
 * every other exit code is (ADR-141), and it asks {@link #refuses} whether the failure is this one.
 */
public class MisshapenProfileRefusal implements SpringBootExceptionReporter {

    /**
     * What the invocation exits with: the code {@code vespera label} returns for a label file it
     * cannot use and {@code run} for a {@code --db-dir} that disagrees, since this is the same kind of
     * refusal — a file the operator wrote that the tool will not act on. Not picocli's usage code,
     * which this command line uses for something the operator left out.
     */
    public static final int EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    @Override
    public boolean reportException(Throwable failure) {
        MisshapenProfileException misshapen = misshapenProfileIn(failure);
        if (misshapen == null) {
            return false;
        }
        System.err.println(misshapen.getMessage());
        return true;
    }

    /** Whether a failed start was a profile that did not load, and so has already been said. */
    public static boolean refuses(Throwable failure) {
        return misshapenProfileIn(failure) != null;
    }

    /**
     * The profile's fault anywhere in the chain. It arrives unwrapped from {@link ProfileShapeCheck},
     * and wrapped in a bean's creation failure should the file change between that check and a bean
     * reading it; both are the same fault to the operator.
     */
    private static MisshapenProfileException misshapenProfileIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MisshapenProfileException misshapen) {
                return misshapen;
            }
        }
        return null;
    }
}
