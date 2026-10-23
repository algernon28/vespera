package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the sidecar starter runs commands through (ADR-322): the one seam between it and the operating system, so
 * that a test runs no process.
 */
interface ProcessRunner {

    /**
     * Runs the command and waits for it at most {@code timeout}, killing it if it has not exited by then; a command
     * killed counts as exited with a code that is not zero.
     *
     * @throws IOException when the program cannot be started, as {@link ProcessBuilder#start} does for one that is
     *     not there
     */
    ProcessOutcome run(List<String> command, Duration timeout) throws IOException;

    /**
     * Runs the command to its end with no bound of Vespera's, handing every line it prints to {@code eachLine} as it
     * is printed.
     *
     * @throws IOException when the program cannot be started
     */
    ProcessOutcome runToTheEnd(List<String> command, Consumer<String> eachLine) throws IOException;
}
