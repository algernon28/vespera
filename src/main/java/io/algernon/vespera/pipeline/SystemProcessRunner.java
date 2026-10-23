package io.algernon.vespera.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** The {@link ProcessRunner} that starts real processes, with standard error merged into the output (ADR-322). */
final class SystemProcessRunner implements ProcessRunner {

    /** The exit code a command killed for running past its limit counts as. */
    static final int KILLED = -1;

    @Override
    public ProcessOutcome run(List<String> command, Duration timeout) throws IOException {
        Process process = start(command);
        StringBuffer output = new StringBuffer();
        Thread reader = Thread.ofVirtual().start(() -> {
            try {
                readLines(process, output::append);
            } catch (UncheckedIOException closed) {
                // The process was killed under the reader; what was read is what there is.
            }
        });
        try {
            boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor();
            }
            reader.join();
            return new ProcessOutcome(exited ? process.exitValue() : KILLED, output.toString());
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for " + String.join(" ", command), interrupted);
        }
    }

    @Override
    public ProcessOutcome runToTheEnd(List<String> command, Consumer<String> eachLine) throws IOException {
        Process process = start(command);
        StringBuilder output = new StringBuilder();
        try {
            readLines(process, line -> {
                output.append(line);
                eachLine.accept(line.stripTrailing());
            });
        } catch (UncheckedIOException unreadable) {
            throw unreadable.getCause();
        }
        try {
            return new ProcessOutcome(process.waitFor(), output.toString());
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for " + String.join(" ", command), interrupted);
        }
    }

    /** Nothing here is asked for input: a command that reads its input gets the end of it at once. */
    private static Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File(
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null")));
        return builder.start();
    }

    /** Hands each line of the process's output, its newline kept, to {@code into}. */
    private static void readLines(Process process, Consumer<String> into) {
        try (BufferedReader lines =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                into.accept(line + "\n");
            }
        } catch (IOException closed) {
            throw new UncheckedIOException(closed);
        }
    }
}
