package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Brings the containers on this machine up where they are not answering: starts the Docker engine where it is not
 * running, then runs the README's {@code up} with {@code --no-recreate}, and waits for every container on this machine
 * (ADR-323, ADR-430 §10). It never removes or
 * recreates a container, and stops the sidecars and the engine only when asked to ({@link #stopTheSidecars} and
 * {@link #shutDown}, ADR-390 §3); it does nothing at exit.
 *
 * <p>The commands that use a sidecar, {@code vespera run} and {@code vespera label --auto}, ask {@link
 * #ensureRunning} before they do anything else; a {@link SidecarStartRefusedException} is its refusal, one line and
 * exit 1. It is registered by {@code VesperaApplication.main} alone, as the corpus-root lock is (ADR-279 §7), so a
 * context built by Spring's test support has none.
 */
public class SidecarStarter {

    /** How a call to {@link #ensureRunning} ended when it did not refuse. */
    enum Outcome {
        /** Neither container is on this machine, so Docker on this one has nothing to make answer. */
        ON_ANOTHER_MACHINE,
        /** Every container on this machine answered, when first asked or when asked again under the lock. */
        ALREADY_ANSWERING,
        /** The engine was made to answer where it did not, {@code up} was run and every container on this machine answers. */
        STARTED,
        /** {@link #ensureTheEngineRuns}: the engine answered when asked, and nothing was started. */
        ENGINE_ANSWERING,
        /** {@link #ensureTheEngineRuns}: the engine was made to answer where it did not; no {@code up} was run. */
        ENGINE_STARTED
    }

    /** What the shut-down did: whether both the sidecars and the engine are stopped, and the one line that says it (ADR-390 §6). */
    record ShutDown(boolean done, String line) {
    }

    /** The line of a shut-down that stopped both (ADR-390 §6). */
    static final String SHUT_DOWN_DONE =
            "the sidecars and the Docker engine are stopped; Vespera starts them again when it next needs them.";

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
    private final ThisMachine thisMachine;

    SidecarStarter(
            SidecarStartSettings settings,
            ProcessRunner runner,
            SidecarHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery) {
        this(settings, runner, health, host, composeFiles, lockFolder, checkEvery, ThisMachine.system());
    }

    SidecarStarter(
            SidecarStartSettings settings,
            ProcessRunner runner,
            SidecarHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery,
            ThisMachine thisMachine) {
        this.thisMachine = thisMachine;
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
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpSidecarHealth(settings),
                HostSystem.current(),
                () -> BesideTheJar.ofThisApplication().composeFiles(),
                CorpusRootLock.folderNamedBy(environment.getProperty(CorpusRootLock.FOLDER_PROPERTY)),
                CHECK_EVERY);
    }

    /**
     * Makes every container on this machine answer where it does not, in the order of ADR-323 §3; where neither
     * container is on this machine it does nothing (ADR-430 §10).
     *
     * @throws SidecarStartRefusedException when they do not answer and cannot be made to
     */
    Outcome ensureRunning() {
        return ensureRunning(true);
    }

    /**
     * As {@link #ensureRunning()}; {@code logTheWslconfig} false where the caller has already seen to {@code .wslconfig}
     * through {@link #keepTheWslDistributionUp()} and shown its line, so that line is not logged twice for one press.
     */
    Outcome ensureRunning(boolean logTheWslconfig) {
        if (neitherIsOnThisMachine()) {
            return Outcome.ON_ANOTHER_MACHINE;
        }
        seeToTheWslconfig(logTheWslconfig);
        if (theLocalOnesAnswer()) {
            return Outcome.ALREADY_ANSWERING;
        }
        String cpuImage;
        String gpuImage;
        try (ComposeFiles files = writtenOut()) {
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
            if (theLocalOnesAnswer()) {
                return Outcome.ALREADY_ANSWERING;
            }
            makeTheEngineAnswer();
            checkComposeAndBuildx();
            bringUp(withTheGpuFile);
            waitForTheLocalOnes();
            return Outcome.STARTED;
        }
    }

    /**
     * On Windows, with at least one container on this machine, sees to {@code .wslconfig} and logs its line at WARN
     * (ADR-421 §1, §5, ADR-430 §10). Runs no command and takes no lock. What the control panel's window calls before it
     * starts the containers.
     *
     * @return the line, empty where the file sets the setting, the system is not Windows, or neither container is on
     *     this machine
     */
    Optional<String> keepTheWslDistributionUp() {
        return seeToTheWslconfig(true);
    }

    private Optional<String> seeToTheWslconfig(boolean logIt) {
        if (host != HostSystem.WINDOWS || (!doclingIsHere() && !ollamaIsHere())) {
            return Optional.empty();
        }
        Optional<String> line = WslIdleTimeout.keepTheDistributionUp(settings.profileFolder());
        if (logIt) {
            line.ifPresent(said -> log.warn("{}", said));
        }
        return line;
    }

    private boolean neitherIsOnThisMachine() {
        if (!doclingIsHere() && !ollamaIsHere()) {
            log.info("The containers are looked for on another machine ({} and {}): Vespera starts none; one that does"
                    + " not answer fails where it fails", settings.doclingBaseUrl(), settings.ollamaBaseUrl());
            return true;
        }
        return false;
    }

    private boolean doclingIsHere() {
        return thisMachine.where(settings.doclingBaseUrl()) == ThisMachine.Where.THIS_MACHINE;
    }

    private boolean ollamaIsHere() {
        return thisMachine.where(settings.ollamaBaseUrl()) == ThisMachine.Where.THIS_MACHINE;
    }

    /**
     * Asks the Docker daemon and starts nothing (ADR-430 §3): on Windows the distribution is asked only while it runs,
     * which {@code wsl --list} says without booting any; on Linux only while {@code docker.service} is active; on
     * macOS as it is. Takes no lock and reads no compose file.
     *
     * @return empty where neither container is on this machine and nothing was asked; else whether the daemon answers
     */
    Optional<Boolean> engineAnswersStartingNothing() {
        if (!doclingIsHere() && !ollamaIsHere()) {
            return Optional.empty();
        }
        return Optional.of(switch (host) {
            case WINDOWS -> windowsEngineAnswers();
            case LINUX -> exitsZero(host.engineServiceActive()) && theEngineAnswers();
            case MAC -> theEngineAnswers();
        });
    }

    private boolean windowsEngineAnswers() {
        String named = settings.wslDistribution() == null ? "" : settings.wslDistribution().strip();
        if (named.isEmpty()) {
            ProcessOutcome verbose = runCounting(host.wslVerbose(), COMMAND_LIMIT);
            if (verbose.exitCode() != 0) {
                return false;
            }
            named = theMarkedDistribution(verbose.output());
            if (named.isEmpty()) {
                return false;
            }
        }
        ProcessOutcome running = runCounting(host.wslRunning(), COMMAND_LIMIT);
        if (running.exitCode() != 0) {
            return false;
        }
        String wanted = named;
        boolean listed = readFromWsl(running.output()).lines()
                .map(String::strip)
                .anyMatch(line -> line.equalsIgnoreCase(wanted));
        return listed && theEngineAnswers();
    }

    /** The name on the line {@code wsl --list --verbose} marks with a star, empty where none is. */
    private static String theMarkedDistribution(String verbose) {
        return readFromWsl(verbose).lines()
                .map(String::strip)
                .filter(line -> line.startsWith("*"))
                .map(line -> line.substring(1).strip().split("\\s+")[0])
                .findFirst()
                .orElse("");
    }

    /**
     * What {@code wsl.exe} printed in UTF-16 as a reader of single bytes saw it, its NULs and byte order mark removed:
     * the UTF-8 decoder the runner uses turns a UTF-16LE mark, {@code FF FE}, into two U+FFFD, so those lead nothing
     * either, and so does U+FEFF itself.
     */
    private static String readFromWsl(String output) {
        return output.replace("\u0000", "").replaceFirst("^[\\uFEFF\\uFFFD]+", "").replace("﻿", "");
    }

    private boolean exitsZero(List<String> command) {
        return runCounting(command, COMMAND_LIMIT).exitCode() == 0;
    }

    private boolean theEngineAnswers() {
        try {
            return engineAnswers();
        } catch (DockerIsNotInstalledException notInstalled) {
            return false;
        }
    }

    /**
     * Makes the Docker engine answer where it does not and asks no container: ADR-323 §3's step 2, then, under the lock,
     * the engine's check, its start and the wait (ADR-390 §4), then the checks for Compose and buildx (ADR-410 §2).
     * The control panel's window no longer runs it as it opens (ADR-430 §12); it starts nothing until Start Docker is
     * pressed.
     *
     * @throws SidecarStartRefusedException when the engine does not answer and cannot be made to, or when it answers
     *     and docker compose or docker buildx is not installed (ADR-410 §3)
     */
    Outcome ensureTheEngineRuns() {
        if (neitherIsOnThisMachine()) {
            return Outcome.ON_ANOTHER_MACHINE;
        }
        try (SidecarLock lock = takeTheLock()) {
            boolean started = makeTheEngineAnswer();
            checkComposeAndBuildx();
            return started ? Outcome.ENGINE_STARTED : Outcome.ENGINE_ANSWERING;
        }
    }

    /**
     * Stops the containers alone, only where a container on this machine answers, so that no
     * engine stopped behind its socket is started and no WSL distribution is booted for nothing to stop (ADR-390 §3).
     *
     * @return the line of a refusal, empty where they were stopped or nothing was to be done
     */
    Optional<String> stopTheSidecars() {
        boolean docling = doclingIsHere();
        boolean ollama = ollamaIsHere();
        if (!(docling && health.doclingAnswers()) && !(ollama && health.ollamaAnswers())) {
            return Optional.empty();
        }
        try (SidecarLock lock = takeTheLock()) {
            ProcessOutcome stopped = runStop(host.composeStop(settings.wslDistribution()));
            if (stopped.exitCode() != 0) {
                return Optional.of("the sidecars were not stopped: " + exited(host.composeStop(settings.wslDistribution()),
                        stopped) + ".");
            }
            return Optional.empty();
        } catch (SidecarLockUnavailableException unusable) {
            return Optional.of("the sidecars were not stopped: " + unusable.unusable() + ".");
        } catch (CommandOutputFileUnusableException unusable) {
            return Optional.of("the sidecars were not stopped: " + unusable.unusable() + ".");
        }
    }

    /** The shut-down: the sidecars' stop, then, only where it exited 0, the engine's (ADR-390 §3). */
    ShutDown shutDown() {
        List<String> composeStop = host.composeStop(settings.wslDistribution());
        List<String> engineStop = host.engineStop(settings.wslDistribution());
        try (SidecarLock lock = takeTheLock()) {
            ProcessOutcome sidecars = runStop(composeStop);
            if (sidecars.exitCode() != 0) {
                return new ShutDown(false, "Docker was not shut down: " + exited(composeStop, sidecars)
                        + ". The Docker engine was not stopped.");
            }
            ProcessOutcome engine;
            try {
                engine = runStop(engineStop);
            } catch (CommandOutputFileUnusableException unusable) {
                // Step 1 exited 0: the sidecars are stopped, and it is the engine stop's output file that failed (ADR-395 §6).
                return new ShutDown(false, "The sidecars were stopped, but Docker was not shut down: "
                        + unusable.unusable() + ".");
            }
            if (engine.exitCode() != 0) {
                return new ShutDown(false, "the sidecars are stopped, but the Docker engine is not: "
                        + exited(engineStop, engine) + ". Stop Docker yourself, or leave it running.");
            }
            return new ShutDown(true, SHUT_DOWN_DONE);
        } catch (SidecarLockUnavailableException unusable) {
            return new ShutDown(false, "Docker was not shut down: " + unusable.unusable() + ".");
        } catch (CommandOutputFileUnusableException unusable) {
            return new ShutDown(false, "Docker was not shut down: " + unusable.unusable() + ".");
        }
    }

    private static String exited(List<String> command, ProcessOutcome outcome) {
        return SidecarStartRefusedException.asRun(command) + " exited " + outcome.exitCode() + ": "
                + outcome.lastLine();
    }

    /** Runs a stop, killed after the bound; a program that cannot be started counts as exit 127 with its reason. */
    private ProcessOutcome runStop(List<String> command) {
        return runCounting(command, settings.waitAtMost());
    }

    /** Whether every container on this machine answers; one on another machine is not asked (ADR-430 §10). */
    private boolean theLocalOnesAnswer() {
        boolean docling = !doclingIsHere() || health.doclingAnswers();
        boolean ollama = !ollamaIsHere() || health.ollamaAnswers();
        return docling && ollama;
    }

    // ---- the engine (ADR-323 §5)

    /** Makes the engine answer; true where it had to be started, false where it answered when asked. */
    private boolean makeTheEngineAnswer() {
        if (engineAnswers()) {
            return false;
        }
        List<String> start = host.engineStart(settings.wslDistribution());
        ProcessOutcome outcome = runCounting(start, settings.waitAtMost());
        if (outcome.exitCode() != 0) {
            throw DockerEngineDidNotStartException.noCommandStartedIt(List.of(exited(start, outcome)));
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
        return true;
    }

    private boolean engineAnswers() {
        List<String> check = host.engineCheck(settings.wslDistribution());
        try {
            return runner.run(check, COMMAND_LIMIT).exitCode() == 0;
        } catch (CommandOutputFileException unusable) {
            throw new CommandOutputFileUnusableException(unusable);
        } catch (IOException cannotStart) {
            throw new DockerIsNotInstalledException(host.engineProgram());
        }
    }

    /** Asks for Compose, then buildx, whatever the first answered; refuses naming what is missing (ADR-410 §1, §2). */
    private void checkComposeAndBuildx() {
        boolean composeMissing = !installed(host.composeCheck(settings.wslDistribution()));
        boolean buildxMissing = !installed(host.buildxCheck(settings.wslDistribution()));
        if (composeMissing || buildxMissing) {
            throw new ComposeOrBuildxIsNotInstalledException(
                    host, settings.wslDistribution(), composeMissing, buildxMissing);
        }
    }

    /** Exit 0 is installed; any other exit, a command killed at its limit included, is not (ADR-410 §1). */
    private boolean installed(List<String> check) {
        try {
            return runner.run(check, COMMAND_LIMIT).exitCode() == 0;
        } catch (CommandOutputFileException unusable) {
            throw new CommandOutputFileUnusableException(unusable);
        } catch (IOException cannotStart) {
            throw new DockerIsNotInstalledException(host.engineProgram());
        }
    }

    /**
     * Runs a command of the engine's start or stop. A program that cannot be started, {@code systemctl} on a machine
     * without systemd or {@code colima} not installed, is a command that exited 127 with its reason (ADR-390 §2).
     */
    private ProcessOutcome runCounting(List<String> command, Duration limit) {
        try {
            return runner.run(command, limit);
        } catch (CommandOutputFileException unusable) {
            // Whatever the program: Docker is not what failed, and the next command writes to the same folder.
            throw new CommandOutputFileUnusableException(unusable);
        } catch (IOException cannotStart) {
            return new ProcessOutcome(127, cannotStart.getMessage() == null ? "" : cannotStart.getMessage());
        }
    }

    /** The compose files, where they lie beside the jar (ADR-397 §6). */
    private ComposeFiles writtenOut() {
        return composeFiles.get();
    }

    // ---- up (ADR-323 §4)

    private void bringUp(boolean withTheGpuFile) {
        try (ComposeFiles files = writtenOut()) {
            List<Path> named = withTheGpuFile
                    ? List.of(files.composeFile(), files.gpuFile())
                    : List.of(files.composeFile());
            List<String> up = host.composeUp(settings.wslDistribution(), named, services());
            log.info("Starting the sidecars: {}", SidecarStartRefusedException.asRun(up));
            ProcessOutcome outcome;
            try {
                outcome = runner.runToTheEnd(up, line -> log.info("{}", line));
            } catch (IOException cannotStart) {
                throw new DockerIsNotInstalledException(host.engineProgram());
            }
            if (outcome.exitCode() != 0) {
                throw new SidecarsDidNotStartException(up, outcome);
            }
        }
    }

    // ---- the wait after up (ADR-323 §3, step 8)

    /** The service {@code up} names where one container is on this machine; none where both are (ADR-430 §10). */
    private List<String> services() {
        boolean docling = doclingIsHere();
        if (docling && ollamaIsHere()) {
            return List.of();
        }
        return List.of(docling ? "docling-serve" : "ollama");
    }

    private void waitForTheLocalOnes() {
        boolean askDocling = doclingIsHere();
        boolean askOllama = ollamaIsHere();
        String names = askDocling && askOllama ? "docling-serve and Ollama" : askDocling ? "docling-serve" : "Ollama";
        log.info("Waiting up to {} seconds for {} to answer", settings.waitAtMost().toSeconds(), names);
        long began = System.nanoTime();
        while (true) {
            boolean docling = !askDocling || health.doclingAnswers();
            boolean ollama = !askOllama || health.ollamaAnswers();
            if (docling && ollama) {
                log.info("{} answer after {} seconds", names, secondsSince(began));
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
            throw new SidecarLockUnavailableException(lockFolder.resolve(LOCK_FILE), unavailable);
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
                throw new SidecarLockUnavailableException(lockFolder.resolve(LOCK_FILE), unlockable);
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
