package io.algernon.vespera.engine.pipeline;

/**
 * A start of the command line through Spring whose {@code vespera.working-dir} names no folder (ADR-436 §1). Thrown
 * after the configuration files are read and before Logback is set up, so its message, the one line, is said by the
 * entry point on standard error as it stands.
 */
public final class NoWorkingDirectoryNamedException extends RuntimeException {

    /** The line a start naming no working directory is refused in, whole. */
    public static final String LINE = "Vespera is not started: no working directory is named. Name one with"
            + " --db-dir=<path>, the environment variable VESPERA_WORKING_DIR, or vespera.working-dir in"
            + " application.yaml beside the jar.";

    public NoWorkingDirectoryNamedException() {
        super(LINE);
    }
}
