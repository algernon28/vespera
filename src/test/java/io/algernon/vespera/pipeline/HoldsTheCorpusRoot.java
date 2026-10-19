package io.algernon.vespera.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A second process that holds a corpus root the way {@code vespera run} does, for {@link CorpusRootLockTest}
 * and {@link CorpusRootInUseIT} to be refused by, and to kill (ADR-279).
 *
 * <p>A file lock belongs to the process that took it, and within one JVM a second take fails differently from
 * the way it fails between two, so the case of two invocations needs a real second process. This one takes the
 * lock in the folder named by its first argument, on the root named by its second, for the working directory
 * named by its third, prints {@value #HOLDING} once it holds it, and sleeps until it is killed.
 */
public final class HoldsTheCorpusRoot {

    /** The line printed once the corpus root is held, which the parent waits for. */
    public static final String HOLDING = "holding";

    /** Longer than any test waits; the parent kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    /** How long the second process gets to start a JVM and take the lock. */
    private static final long START_BOUND_SECONDS = 60;

    private HoldsTheCorpusRoot() {
    }

    public static void main(String[] args) throws InterruptedException {
        new CorpusRootLock(Path.of(args[0])).take(Path.of(args[1]), Path.of(args[2]), new String[] {"run", args[1]});
        System.out.println(HOLDING);
        System.out.flush();
        Thread.sleep(SLEEP_MILLIS);
    }

    /** Starts this class in a JVM of its own, on the running test's class path, and returns once it holds the root. */
    static Process start(Path folder, Path corpusRoot, Path workingDirectory) throws Exception {
        Process holder = new ProcessBuilder(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-cp", System.getProperty("java.class.path"),
                        HoldsTheCorpusRoot.class.getName(),
                        folder.toString(),
                        corpusRoot.toString(),
                        workingDirectory.toString())
                .redirectErrorStream(true)
                .start();
        BufferedReader printed = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        String first;
        try {
            first = CompletableFuture.supplyAsync(() -> {
                        try {
                            String line;
                            while ((line = printed.readLine()) != null) {
                                if (line.strip().equals(HOLDING)) {
                                    return line.strip();
                                }
                            }
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .get(START_BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (Exception notHeldInTime) {
            // A second process left running would keep the corpus root locked past the test.
            holder.destroyForcibly();
            throw notHeldInTime;
        }
        if (first == null) {
            holder.destroyForcibly();
            throw new IllegalStateException("the second process ended without holding " + corpusRoot);
        }
        return holder;
    }
}
