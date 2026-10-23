package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Brings the two sidecars up where they are not answering: starts the Docker engine where it is not running, then
 * runs the README's {@code up} with {@code --no-recreate}, and waits for both sidecars (ADR-323). It never stops,
 * removes or recreates a container or the engine, and does nothing at exit.
 *
 * <p>The commands that use a sidecar, {@code vespera run} and {@code vespera label --auto}, ask {@link
 * #ensureRunning} before they do anything else; a {@link SidecarStartRefusedException} is its refusal, one line and
 * exit 1. It is registered by {@code VesperaApplication.main} alone, as the corpus-root lock is (ADR-279 §7), so a
 * context built by Spring's test support has none.
 */
public class SidecarStarter {

    /** How a call to {@link #ensureRunning} ended when it did not refuse. */
    enum Outcome {
        /** {@code vespera.sidecars.start} is false (ADR-323 §9). */
        NOT_ASKED_TO_START,
        /** A base URL names another machine, which Docker on this one cannot make answer. */
        ON_ANOTHER_MACHINE,
        /** Both sidecars answered, when first asked or when asked again under the lock. */
        ALREADY_ANSWERING,
        /** The engine was made to answer where it did not, {@code up} was run and both sidecars answer. */
        STARTED
    }

    /** The lock file, in the folder the corpus-root locks lie in (ADR-323 §6). */
    private static final String LOCK_FILE = "sidecars.lock";

    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /** How long between two checks while waiting for the engine or a sidecar. */
    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    /** A command of the engine's section is killed, and counts as exited non-zero, after this long. */
    private static final Duration COMMAND_LIMIT = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(SidecarStarter.class);

    private final SidecarStartSettings settings;
    private final ProcessRunner runner;
    private final SidecarHealth health;
    private final HostSystem host;
    private final Supplier<ComposeFiles> composeFiles;
    private final Path lockFolder;
    private final Duration checkEvery;

    SidecarStarter(
            SidecarStartSettings settings,
            ProcessRunner runner,
            SidecarHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery) {
        this.settings = settings;
        this.runner = runner;
        this.health = health;
        this.host = host;
        this.composeFiles = composeFiles;
        this.lockFolder = lockFolder;
        this.checkEvery = checkEvery;
    }

    /**
     * The starter an invocation of the application has: real processes, real HTTP questions, the compose files the
     * jar carries, and the lock in the folder the corpus-root locks lie in.
     */
    public static SidecarStarter forTheApplication(Environment environment) {
        SidecarStartSettings settings = SidecarStartSettings.from(environment);
        return new SidecarStarter(
                settings,
                new SystemProcessRunner(),
                new HttpSidecarHealth(settings),
                HostSystem.current(),
                () -> {
                    try {
                        return ComposeFiles.fromTheClasspath();
                    } catch (IOException unwritten) {
                        throw new java.io.UncheckedIOException(unwritten);
                    }
                },
                CorpusRootLock.folderNamedBy(environment.getProperty(CorpusRootLock.FOLDER_PROPERTY)),
                CHECK_EVERY);
    }

    /**
     * Makes both sidecars answer where they do not, in the order of ADR-323 §3.
     *
     * @throws SidecarStartRefusedException when they do not answer and cannot be made to
     */
    Outcome ensureRunning() {
        if (!settings.start()) {
            return Outcome.NOT_ASKED_TO_START;
        }
        if (!isOnThisMachine(settings.doclingBaseUrl()) || !isOnThisMachine(settings.ollamaBaseUrl())) {
            log.info("A sidecar is looked for on another machine ({} and {}): Vespera starts none; one that does not"
                    + " answer fails where it fails", settings.doclingBaseUrl(), settings.ollamaBaseUrl());
            return Outcome.ON_ANOTHER_MACHINE;
        }
        if (bothAnswer()) {
            return Outcome.ALREADY_ANSWERING;
        }
        String cpuImage;
        String gpuImage;
        try (ComposeFiles files = composeFiles.get()) {
            cpuImage = files.cpuImage();
            gpuImage = files.gpuImage();
        }
        boolean withTheGpuFile;
        if (settings.doclingImage().equals(cpuImage)) {
            withTheGpuFile = false;
        } else if (settings.doclingImage().equals(gpuImage)) {
            withTheGpuFile = true;
        } else {
            throw new NoComposeFileBuildsTheImageException(settings.doclingImage(), cpuImage, gpuImage);
        }
        try (SidecarLock lock = takeTheLock()) {
            if (bothAnswer()) {
                return Outcome.ALREADY_ANSWERING;
            }
            makeTheEngineAnswer();
            bringUp(withTheGpuFile);
            waitForBoth();
            return Outcome.STARTED;
        }
    }

    private boolean bothAnswer() {
        boolean docling = health.doclingAnswers();
        boolean ollama = health.ollamaAnswers();
        return docling && ollama;
    }

    /** A host that is a loopback name or address: {@code localhost}, {@code 127.0.0.0/8}, {@code ::1}. */
    private static boolean isOnThisMachine(URI baseUrl) {
        String host = baseUrl.getHost();
        if (host == null) {
            return false;
        }
        String name = host.toLowerCase(java.util.Locale.ROOT);
        return name.equals("localhost")
                || name.matches("127(\\.\\d{1,3}){3}")
                || name.equals("[::1]")
                || name.equals("::1")
                || name.equals("[0:0:0:0:0:0:0:1]");
    }

    // ---- the engine (ADR-323 §5)

    private void makeTheEngineAnswer() {
        if (engineAnswers()) {
            return;
        }
        List<String> tried = new ArrayList<>();
        boolean started = false;
        for (List<String> command : host.engineStartCommands()) {
            ProcessOutcome outcome = runOrNotInstalled(command);
            if (outcome.exitCode() == 0) {
                started = true;
                break;
            }
            tried.add(SidecarStartRefusedException.asRun(command) + " exited " + outcome.exitCode() + ": "
                    + outcome.lastLine());
        }
        if (!started) {
            throw DockerEngineDidNotStartException.noCommandStartedIt(tried);
        }
        log.info("Waiting up to {} seconds for the Docker engine to answer", settings.waitAtMost().toSeconds());
        long began = System.nanoTime();
        while (!engineAnswers()) {
            if (System.nanoTime() - began >= settings.waitAtMost().toNanos()) {
                throw DockerEngineDidNotStartException.didNotAnswer(settings.waitAtMost());
            }
            sleep();
        }
        log.info("The Docker engine answers after {} seconds", secondsSince(began));
    }

    private boolean engineAnswers() {
        return runOrNotInstalled(List.of("docker", "version", "--format", "{{.Server.Version}}")).exitCode() == 0;
    }

    /**
     * Runs a command of the engine's section. A {@code docker} that cannot be started is Docker not being installed;
     * any other program that cannot be started, {@code systemctl} on a machine without systemd, is a command that
     * exited non-zero.
     */
    private ProcessOutcome runOrNotInstalled(List<String> command) {
        try {
            return runner.run(command, COMMAND_LIMIT);
        } catch (IOException cannotStart) {
            if (command.getFirst().equals("docker")) {
                throw new DockerIsNotInstalledException();
            }
            return new ProcessOutcome(127, cannotStart.getMessage() == null ? "" : cannotStart.getMessage());
        }
    }

    // ---- up (ADR-323 §4)

    private void bringUp(boolean withTheGpuFile) {
        try (ComposeFiles files = composeFiles.get()) {
            List<String> up = new ArrayList<>(List.of("docker", "compose", "-p", "vespera", "-f"));
            up.add(files.composeFile().toString());
            if (withTheGpuFile) {
                up.add("-f");
                up.add(files.gpuFile().toString());
            }
            up.addAll(List.of("up", "-d", "--build", "--no-recreate"));
            log.info("Starting the sidecars: {}", SidecarStartRefusedException.asRun(up));
            ProcessOutcome outcome;
            try {
                outcome = runner.runToTheEnd(up, line -> log.info("{}", line));
            } catch (IOException cannotStart) {
                throw new DockerIsNotInstalledException();
            }
            if (outcome.exitCode() != 0) {
                throw new SidecarsDidNotStartException(up, outcome);
            }
        }
    }

    // ---- the wait after up (ADR-323 §3, step 8)

    private void waitForBoth() {
        log.info("Waiting up to {} seconds for docling-serve and Ollama to answer", settings.waitAtMost().toSeconds());
        long began = System.nanoTime();
        while (true) {
            boolean docling = health.doclingAnswers();
            boolean ollama = health.ollamaAnswers();
            if (docling && ollama) {
                log.info("docling-serve and Ollama answer after {} seconds", secondsSince(began));
                return;
            }
            if (System.nanoTime() - began >= settings.waitAtMost().toNanos()) {
                String which = !docling && !ollama ? "docling-serve and Ollama" : docling ? "Ollama" : "docling-serve";
                throw new SidecarsDidNotAnswerException(which, settings.waitAtMost());
            }
            sleep();
        }
    }

    private static long secondsSince(long nanoTime) {
        return Duration.ofNanos(System.nanoTime() - nanoTime).toSeconds();
    }

    private void sleep() {
        try {
            Thread.sleep(checkEvery);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the sidecars", interrupted);
        }
    }

    // ---- the lock (ADR-323 §6)

    /**
     * Takes {@code sidecars.lock} in the lock folder, waiting for it where another invocation holds it: unlike the
     * working directory's and the corpus root's, it is waited for, because the holder is doing what this would.
     */
    private SidecarLock takeTheLock() {
        FileChannel channel;
        try {
            Files.createDirectories(lockFolder);
            channel = FileChannel.open(
                    lockFolder.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException unavailable) {
            throw new IllegalStateException("the lock file " + LOCK_FILE + " cannot be opened in " + lockFolder,
                    unavailable);
        }
        boolean said = false;
        while (true) {
            try {
                FileLock lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
                if (lock != null) {
                    return new SidecarLock(channel, lock);
                }
            } catch (OverlappingFileLockException heldByThisProcess) {
                // Another invocation of this JVM holds it, which a test can; wait the same way.
            } catch (IOException unlockable) {
                closeQuietly(channel);
                throw new IllegalStateException("the lock file " + LOCK_FILE + " in " + lockFolder
                        + " cannot be locked", unlockable);
            }
            if (!said) {
                log.info("Another Vespera invocation is starting the sidecars; waiting for it");
                said = true;
            }
            sleep();
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Nothing is left to do for a file that will not close.
        }
    }

    /** The held lock, released and its channel closed at the end of the start. */
    private record SidecarLock(FileChannel channel, FileLock lock) implements AutoCloseable {

        @Override
        public void close() {
            try {
                lock.release();
            } catch (IOException ignored) {
                // Closing the channel releases it all the same.
            }
            closeQuietly(channel);
        }
    }
}
