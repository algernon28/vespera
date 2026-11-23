package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the container starter runs commands through (ADR-323): the one seam between it and the operating system, so
 * that a test runs no process.
 */
interface ProcessRunner {

    /**
     * Runs the command and waits for it at most {@code timeout}, killing it if it has not exited by then; a command
     * killed counts as exited with a code that is not zero.
     *
     * @throws IOException when the program cannot be started, as {@link ProcessBuilder#start} does for one that is
     *     not there
     * @throws CommandOutputFileException when the temporary file its output goes to cannot be created or read
     *     (ADR-326)
     */
    ProcessOutcome run(List<String> command, Duration timeout) throws IOException, CommandOutputFileException;

    /**
     * As {@link #run}, with {@code input} written on the command's standard input, encoded as UTF-8, and the input
     * closed; a null {@code input} writes nothing and closes it (ADR-445 §10).
     *
     * @throws IOException when the program cannot be started
     * @throws CommandOutputFileException when the temporary file its output goes to cannot be created or read
     */
    ProcessOutcome runFeeding(List<String> command, Duration timeout, char[] input)
            throws IOException, CommandOutputFileException;

    /**
     * Runs the command to its end with no bound of Vespera's, handing every line it prints to {@code eachLine} as it
     * is printed.
     *
     * @throws IOException when the program cannot be started
     */
    ProcessOutcome runToTheEnd(List<String> command, Consumer<String> eachLine) throws IOException;
}
