package io.algernon.vespera.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** The {@link ProcessRunner} that starts real processes, with standard error merged into the output (ADR-323). */
final class SystemProcessRunner implements ProcessRunner {

    /** The exit code a command killed for running past its limit counts as. */
    static final int KILLED = -1;

    /** How long a command that was killed is waited for. */
    private static final Duration KILL_GRACE = Duration.ofSeconds(1);

    @Override
    public ProcessOutcome run(List<String> command, Duration timeout) throws IOException {
        // The output goes to a file, not a pipe: a child the command started may hold it open for as long as it
        // lives, and on Windows closing a pipe another thread is blocked reading waits for that read (ADR-323 §5).
        // Nothing here reads a pipe, so nothing here can block on one.
        Path file = Files.createTempFile("vespera-command-", ".out");
        try {
            Process process = start(command, ProcessBuilder.Redirect.to(file.toFile()));
            try {
                boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
                }
                return new ProcessOutcome(exited ? process.exitValue() : KILLED, linesOf(Files.readAllBytes(file)));
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for " + String.join(" ", command), interrupted);
            }
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException held) {
                file.toFile().deleteOnExit(); // a child still holding the file open keeps Windows from deleting it
            }
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
        return start(command, ProcessBuilder.Redirect.PIPE);
    }

    private static Process start(List<String> command, ProcessBuilder.Redirect output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output);
        builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File(
                HostSystem.current() == HostSystem.WINDOWS ? "NUL" : "/dev/null")));
        return builder.start();
    }

    /** The text of the bytes, UTF-8, each line with its newline kept, as {@link #readLines} hands them. */
    private static String linesOf(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader lines =
                new BufferedReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            String line;
            while ((line = lines.readLine()) != null) {
                text.append(line).append('\n');
            }
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return text.toString();
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
