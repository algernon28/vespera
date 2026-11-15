package io.algernon.vespera.engine.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A second process that holds the machine lock the way a Vespera does, for {@link MachineLockTest} and {@code
 * CliExitIT} to be refused by, and to kill (ADR-395 §2).
 *
 * <p>A file lock belongs to the process that took it, so the cases of two Vespera processes need a real second one.
 * This one takes the lock in the folder named by its first argument, as the kind named by its second, prints {@value
 * #HOLDING} once it holds it, and, where its third argument is {@value #WITH_ITS_CHILD}, starts {@link TakesTheMachine}
 * as its own direct child, as the control panel starts its child, and prints the line that child printed. It then
 * sleeps until it is killed.
 */
public final class HoldsTheMachine {

    /** The line printed once the machine is held, which the parent waits for. */
    public static final String HOLDING = "holding";

    /** The third argument that has this process start a child of its own that takes the machine. */
    public static final String WITH_ITS_CHILD = "with-its-child";

    /** What this process prints before the line its child printed. */
    private static final String ITS_CHILD_SAID = "its child said: ";

    /** Longer than any test waits; the parent kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    /** How long the second process gets to start a JVM, take the lock, and hear from its child. */
    private static final long START_BOUND_SECONDS = 120;

    /** The second process, and the line its child printed where it started one. */
    public record Holder(Process process, Optional<String> childSaid) {
    }

    private HoldsTheMachine() {
    }

    public static void main(String[] args) throws Exception {
        MachineLock.Kind kind = MachineLock.Kind.valueOf(args[1]);
        new MachineLock(Path.of(args[0])).take(kind, new String[] {kind.word()});
        System.out.println(HOLDING);
        System.out.flush();
        if (args.length > 2 && args[2].equals(WITH_ITS_CHILD)) {
            Process child = new ProcessBuilder(java(), "-cp", System.getProperty("java.class.path"),
                            TakesTheMachine.class.getName(), args[0])
                    .redirectErrorStream(true)
                    .start();
            String said = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            System.out.println(ITS_CHILD_SAID + said);
            System.out.flush();
        }
        Thread.sleep(SLEEP_MILLIS);
    }

    /**
     * Starts this class in a JVM of its own, on the running test's class path, and returns once it holds the machine
     * as {@code kind} and, where {@code withItsChild}, once its child has said whether it was let through.
     */
    public static Holder start(Path folder, MachineLock.Kind kind, boolean withItsChild) throws Exception {
        List<String> command = new ArrayList<>(List.of(java(), "-cp", System.getProperty("java.class.path"),
                HoldsTheMachine.class.getName(), folder.toString(), kind.name()));
        if (withItsChild) {
            command.add(WITH_ITS_CHILD);
        }
        Process holder = new ProcessBuilder(command).redirectErrorStream(true).start();
        BufferedReader printed = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        Optional<String> childSaid;
        try {
            childSaid = CompletableFuture.supplyAsync(() -> {
                        try {
                            boolean held = false;
                            String line;
                            while ((line = printed.readLine()) != null) {
                                if (line.strip().equals(HOLDING)) {
                                    held = true;
                                    if (!withItsChild) {
                                        return Optional.<String>empty();
                                    }
                                } else if (held && line.startsWith(ITS_CHILD_SAID)) {
                                    return Optional.of(line.substring(ITS_CHILD_SAID.length()));
                                }
                            }
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .get(START_BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (Exception notHeldInTime) {
            // A second process left running would keep the machine locked past the test.
            holder.destroyForcibly();
            throw notHeldInTime;
        }
        if (childSaid == null) {
            holder.destroyForcibly();
            throw new IllegalStateException("the second process ended without holding the machine in " + folder);
        }
        return new Holder(holder, childSaid);
    }

    private static String java() {
        return ProcessHandle.current().info().command().orElseThrow();
    }
}
