package io.algernon.controlpanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one process the control panel started (ADR-278 §3, §5, §6; ADR-285 §5): its standard error merged into its
 * standard output, read to the end by one thread into a tail of the last {@value #TAIL_LINES} lines kept in memory
 * only; stopped by killing its descendants first and then it; and said to have ended as {@link HowItEnded} decides.
 * It is built only by starting a process builder, and it is the only class that ends a process.
 */
public final class Child {

    public static final int TAIL_LINES = 200;

    public static final String STOP_CONFIRMATION =
            "Stopping loses the work done since the running step's last commit, and the next vespera run continues"
                    + " from there.";

    private final Process process;
    private final Deque<String> tail = new ArrayDeque<>();
    private final Thread reader;
    private final AtomicBoolean stoppedByItsOwnStop = new AtomicBoolean();

    private Child(Process process) {
        this.process = process;
        this.reader = Thread.ofPlatform().daemon().name("child-output-" + process.pid()).unstarted(this::drain);
    }

    /** Starts the process with standard error merged into standard output and begins reading it. */
    public static Child start(ProcessBuilder builder) throws IOException {
        builder.redirectErrorStream(true);
        Child child = new Child(builder.start());
        child.reader.start();
        return child;
    }

    public long pid() {
        return process.pid();
    }

    /** A snapshot of the last {@value #TAIL_LINES} lines, in the order written. */
    public List<String> tail() {
        synchronized (tail) {
            return List.copyOf(tail);
        }
    }

    /** Kills the descendants, then the child, and returns once all are gone; records that this was a Stop. */
    public void stop() throws InterruptedException {
        stoppedByItsOwnStop.set(true);
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor();
        for (ProcessHandle descendant : descendants) {
            try {
                descendant.onExit().get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Empty when the child has not ended, with its output read to the end, within the bound. */
    public Optional<Ending> waitForEnding(Duration bound) throws InterruptedException {
        long deadline = System.nanoTime() + bound.toNanos();
        if (!process.waitFor(bound.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS)) {
            return Optional.empty();
        }
        reader.join(Math.max(1, Duration.ofNanos(deadline - System.nanoTime()).toMillis()));
        if (reader.isAlive()) {
            return Optional.empty();
        }
        int exitCode = process.exitValue();
        Optional<String> lastLine = tail().reversed().stream().filter(line -> !line.isBlank()).findFirst();
        return Optional.of(new Ending(HowItEnded.of(stoppedByItsOwnStop.get(), exitCode), exitCode, lastLine));
    }

    private void drain() {
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                synchronized (tail) {
                    tail.addLast(line);
                    if (tail.size() > TAIL_LINES) {
                        tail.removeFirst();
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
