package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * A second process that holds a working directory the way an invocation does, for
 * {@link WorkingDirectoryLockTest} to be refused by (ADR-177).
 *
 * <p>A file lock belongs to the process that took it, and within one JVM a second take fails
 * differently from the way it fails between two. So the case that happened on 2026-09-28, two
 * processes, needs a real second process. This one fires the same listener {@code
 * VesperaApplication.main} registers, over the directory named by its first argument, prints
 * {@value #HOLDING} once it holds it, and sleeps until it is killed.
 */
public final class HoldsTheWorkingDirectory {

    /** The line printed once the working directory is held, which the parent waits for. */
    public static final String HOLDING = "holding";

    /** Longer than any test waits; the parent kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    private HoldsTheWorkingDirectory() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path workingDirectory = Path.of(args[0]);
        new WorkingDirectoryLock().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[] {"run", "held-by-a-second-process"},
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString())));
        System.out.println(HOLDING);
        System.out.flush();
        Thread.sleep(SLEEP_MILLIS);
    }
}
