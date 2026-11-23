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
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.core.env.Environment;

/**
 * Brings the containers on this machine up where they are not answering: starts the Docker engine where it is not
 * running, then runs the README's {@code up} with {@code --no-recreate}, and waits for every container on this machine
 * (ADR-323, ADR-430 §10). It never removes or
 * recreates a container, and stops the containers and the engine only when asked to ({@link #stopTheContainers} and
 * {@link #shutDown}, ADR-390 §3); it does nothing at exit.
 *
 * <p>The commands that use a container, {@code vespera run} and {@code vespera label --auto}, ask {@link
 * #ensureRunning} before they do anything else; a {@link ContainerStartRefusedException} is its refusal, one line and
 * exit 1. It is registered by {@code VesperaApplication.main} alone, as the corpus-root lock is (ADR-279 §7), so a
 * context built by Spring's test support has none.
 */
public class ContainerStarter {

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

    /** What the shut-down did: whether both the containers and the engine are stopped, and the one line that says it (ADR-390 §6). */
    record ShutDown(boolean done, String line) {
    }

    /** The line of a shut-down that stopped both (ADR-390 §6). */
    static final String SHUT_DOWN_DONE =
            "the containers and the Docker engine are stopped; Vespera starts them again when it next needs them.";

    /** The lock file, in the folder the corpus-root locks lie in (ADR-323 §6). */
    private static final String LOCK_FILE = "containers.lock";

    /** The file an earlier version of Vespera locked, which a live one refuses the action by (ADR-433 §6). */
    private static final String THE_EARLIER_LOCK_FILE = "sidecars.lock";

    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /** How long between two checks while waiting for the engine or a container. */
    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    /** A command of the engine's section is killed, and counts as exited non-zero, after this long. */
    private static final Duration COMMAND_LIMIT = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(ContainerStarter.class);

    private final ContainerStartSettings settings;
    private final ProcessRunner runner;
    private final ContainerHealth health;
    private final HostSystem host;
    private final Supplier<ComposeFiles> composeFiles;
    private final Path lockFolder;
    private final Duration checkEvery;
    private final ThisMachine thisMachine;
    /** Told each line the starter logs for a press, its words alone (ADR-437 §3); drops them where none was given. */
    private final Consumer<String> eachLine;

    ContainerStarter(
            ContainerStartSettings settings,
            ProcessRunner runner,
            ContainerHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery) {
        this(settings, runner, health, host, composeFiles, lockFolder, checkEvery, ThisMachine.system());
    }

    ContainerStarter(
            ContainerStartSettings settings,
            ProcessRunner runner,
            ContainerHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery,
            ThisMachine thisMachine) {
        this(settings, runner, health, host, composeFiles, lockFolder, checkEvery, thisMachine, line -> {
        });
    }

    private ContainerStarter(
            ContainerStartSettings settings,
            ProcessRunner runner,
            ContainerHealth health,
            HostSystem host,
            Supplier<ComposeFiles> composeFiles,
            Path lockFolder,
            Duration checkEvery,
            ThisMachine thisMachine,
            Consumer<String> eachLine) {
        this.eachLine = eachLine;
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
     * A starter of the same settings and collaborators that hands {@code eachLine} each line it logs for
     * {@link #ensureRunning}, {@link #keepTheWslDistributionUp} and {@link #shutDown}, its words alone, after logging it,
     * on the thread that logs it (ADR-437 §3, §7).
     */
    ContainerStarter telling(Consumer<String> eachLine) {
        return new ContainerStarter(settings, runner, health, host, composeFiles, lockFolder, checkEvery, thisMachine,
                eachLine);
    }

    /** Logs at INFO and tells the line's words alone (ADR-437 §3). */
    private void say(String format, Object... arguments) {
        log.info(format, arguments);
        eachLine.accept(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    /**
     * What a command of the engine's start or stop printed, logged a line at a time once it has exited (ADR-437 §2):
     * NULs and byte-order marks taken out, trailing white space taken off, blank lines left out.
     */
    private void sayWhatItPrinted(String output) {
        output.replace("\u0000", "").replace("\uFEFF", "").lines().map(String::stripTrailing)
                .filter(line -> !line.isBlank()).forEach(line -> say("{}", line));
    }

    /**
     * The starter an invocation of the application has: real processes, real HTTP questions, the compose files the
     * jar carries, and the lock in the folder the corpus-root locks lie in.
     */
    public static ContainerStarter forTheApplication(Environment environment) {
        ContainerStartSettings settings = ContainerStartSettings.from(environment);
        return new ContainerStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpContainerHealth(settings),
                HostSystem.current(),
                () -> BesideTheJar.ofThisApplication().composeFiles(),
                CorpusRootLock.folderNamedBy(environment.getProperty(CorpusRootLock.FOLDER_PROPERTY)),
                CHECK_EVERY);
    }

    /**
     * Makes every container on this machine answer where it does not, in the order of ADR-323 §3; where neither
     * container is on this machine it does nothing (ADR-430 §10).
     *
     * @throws ContainerStartRefusedException when they do not answer and cannot be made to
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
        if (withTheGpuFile && host == HostSystem.MAC) {
            throw new NoNvidiaCardException("on macOS Docker reaches no graphics card", cpuImage);
        }
        try (ContainerLock lock = takeTheLock()) {
            if (theLocalOnesAnswer()) {
                return Outcome.ALREADY_ANSWERING;
            }
            makeTheEngineAnswer();
            checkComposeAndBuildx();
            if (withTheGpuFile) {
                checkTheGraphicsCard(cpuImage);
            }
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
            line.ifPresent(said -> {
                log.warn("{}", said);
                eachLine.accept(said);
            });
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
     * @throws ContainerStartRefusedException when the engine does not answer and cannot be made to, or when it answers
     *     and docker compose or docker buildx is not installed (ADR-410 §3)
     */
    Outcome ensureTheEngineRuns() {
        if (neitherIsOnThisMachine()) {
            return Outcome.ON_ANOTHER_MACHINE;
        }
        try (ContainerLock lock = takeTheLock()) {
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
    Optional<String> stopTheContainers() {
        boolean docling = doclingIsHere();
        boolean ollama = ollamaIsHere();
        if (!(docling && health.doclingAnswers()) && !(ollama && health.ollamaAnswers())) {
            return Optional.empty();
        }
        try (ContainerLock lock = takeTheLock()) {
            ProcessOutcome stopped = runStop(host.composeStop(settings.wslDistribution()));
            if (stopped.exitCode() != 0) {
                return Optional.of("the containers were not stopped: " + exited(host.composeStop(settings.wslDistribution()),
                        stopped) + ".");
            }
            return Optional.empty();
        } catch (ContainerLockUnavailableException unusable) {
            return Optional.of("the containers were not stopped: " + unusable.unusable() + ".");
        } catch (EarlierVersionHoldsTheLockException held) {
            return Optional.of("the containers were not stopped: " + held.unusable() + ".");
        } catch (CommandOutputFileUnusableException unusable) {
            return Optional.of("the containers were not stopped: " + unusable.unusable() + ".");
        }
    }

    /** The shut-down: the containers' stop, then, only where it exited 0, the engine's (ADR-390 §3). */
    ShutDown shutDown() {
        List<String> composeStop = host.composeStop(settings.wslDistribution());
        List<String> engineStop = host.engineStop(settings.wslDistribution());
        try (ContainerLock lock = takeTheLock()) {
            ProcessOutcome containers = runStopTelling("Stopping the containers", composeStop);
            if (containers.exitCode() != 0) {
                return new ShutDown(false, "Docker was not shut down: " + exited(composeStop, containers)
                        + ". The Docker engine was not stopped.");
            }
            ProcessOutcome engine;
            try {
                engine = runStopTelling("Stopping the Docker engine", engineStop);
            } catch (CommandOutputFileUnusableException unusable) {
                // Step 1 exited 0: the containers are stopped, and it is the engine stop's output file that failed (ADR-395 §6).
                return new ShutDown(false, "The containers were stopped, but Docker was not shut down: "
                        + unusable.unusable() + ".");
            }
            if (engine.exitCode() != 0) {
                return new ShutDown(false, "the containers are stopped, but the Docker engine is not: "
                        + exited(engineStop, engine) + ". Stop Docker yourself, or leave it running.");
            }
            return new ShutDown(true, SHUT_DOWN_DONE);
        } catch (ContainerLockUnavailableException unusable) {
            return new ShutDown(false, "Docker was not shut down: " + unusable.unusable() + ".");
        } catch (EarlierVersionHoldsTheLockException held) {
            return new ShutDown(false, "Docker was not shut down: " + held.unusable() + ".");
        } catch (CommandOutputFileUnusableException unusable) {
            return new ShutDown(false, "Docker was not shut down: " + unusable.unusable() + ".");
        }
    }

    private static String exited(List<String> command, ProcessOutcome outcome) {
        return ContainerStartRefusedException.asRun(command) + " exited " + outcome.exitCode() + ": "
                + outcome.lastLine();
    }

    /** Runs a stop, killed after the bound; a program that cannot be started counts as exit 127 with its reason. */
    private ProcessOutcome runStop(List<String> command) {
        return runCounting(command, settings.waitAtMost());
    }

    /** As {@link #runStop}, logging {@code beforeIt} first and what the command printed once it has exited (ADR-437 §2). */
    private ProcessOutcome runStopTelling(String beforeIt, List<String> command) {
        say(beforeIt + ": {}", ContainerStartRefusedException.asRun(command));
        return runTelling(command, settings.waitAtMost());
    }

    /** As {@link #runCounting}, then logs each line the command printed; a program that cannot be started prints nothing. */
    private ProcessOutcome runTelling(List<String> command, Duration limit) {
        try {
            ProcessOutcome outcome = runner.run(command, limit);
            sayWhatItPrinted(outcome.output());
            return outcome;
        } catch (CommandOutputFileException unusable) {
            throw new CommandOutputFileUnusableException(unusable);
        } catch (IOException cannotStart) {
            return new ProcessOutcome(127, cannotStart.getMessage() == null ? "" : cannotStart.getMessage());
        }
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
        say("Starting the Docker engine: {}", ContainerStartRefusedException.asRun(start));
        ProcessOutcome outcome = runTelling(start, settings.waitAtMost());
        if (outcome.exitCode() != 0) {
            throw DockerEngineDidNotStartException.noCommandStartedIt(List.of(exited(start, outcome)));
        }
        say("Waiting up to {} seconds for the Docker engine to answer", settings.waitAtMost().toSeconds());
        long began = System.nanoTime();
        while (!engineAnswers()) {
            if (System.nanoTime() - began >= settings.waitAtMost().toNanos()) {
                throw DockerEngineDidNotStartException.didNotAnswer(settings.waitAtMost());
            }
            sleep();
        }
        say("The Docker engine answers after {} seconds", secondsSince(began));
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

    // ---- the graphics card (ADR-444)

    /**
     * Before {@code up}, for the GPU image: the engine lists an {@code nvidia} runtime or the start is refused (§1, §2,
     * §3); where it does, the driver and the card are warned of where they are old and the start goes on (§4).
     */
    private void checkTheGraphicsCard(String cpuImage) {
        if (!runtimeListed()) {
            throw theRuntimeIsMissing(cpuImage);
        }
        for (String warning : cardWarnings()) {
            log.warn("{}", warning);
            eachLine.accept(warning);
        }
    }

    /** Whether the engine lists an {@code nvidia} runtime: exit 0 and the name as a key of the object it printed (§1). */
    private boolean runtimeListed() {
        List<String> check = host.runtimeCheck(settings.wslDistribution());
        try {
            ProcessOutcome outcome = runner.run(check, COMMAND_LIMIT);
            return outcome.exitCode() == 0 && outcome.output().contains("\"nvidia\":");
        } catch (CommandOutputFileException unusable) {
            throw new CommandOutputFileUnusableException(unusable);
        } catch (IOException cannotStart) {
            throw new DockerIsNotInstalledException(host.engineProgram());
        }
    }

    /** Where the runtime is not listed: reads the cards, and gives the refusal of §2 or of §3. */
    private ContainerStartRefusedException theRuntimeIsMissing(String cpuImage) {
        ProcessOutcome read = runCounting(host.cardsRead(), COMMAND_LIMIT);
        String where = host.whereTheEngineIs(settings.wslDistribution());
        if (read.exitCode() != 0) {
            return new NvidiaRuntimeIsMissingException(where, cpuImage);
        }
        boolean nvidia = false;
        boolean amd = false;
        boolean intel = false;
        for (String vendor : vendorsIn(read.output())) {
            nvidia |= vendor.equals("10de");
            amd |= vendor.equals("1002");
            intel |= vendor.equals("8086");
        }
        if (nvidia) {
            return new NvidiaRuntimeIsMissingException(where, cpuImage);
        }
        String cards = amd && intel ? "this machine's graphics cards are AMD's and Intel's"
                : amd ? "this machine's graphics card is AMD's"
                : intel ? "this machine's graphics card is Intel's"
                : "no NVIDIA graphics card was found on this machine";
        return new NoNvidiaCardException(cards, cpuImage);
    }

    private static final java.util.regex.Pattern VENDOR =
            java.util.regex.Pattern.compile("(?i)VEN_([0-9a-f]{4})|^\\s*0x([0-9a-f]{4})\\s*$");

    /** The vendor ids, lower case, of the lines: Windows' {@code VEN_<id>} in a device id, Linux's {@code 0x<id>}. */
    private static List<String> vendorsIn(String output) {
        List<String> vendors = new ArrayList<>();
        output.replace("\u0000", "").replace("﻿", "").lines().forEach(line -> {
            java.util.regex.Matcher found = VENDOR.matcher(line);
            if (found.find()) {
                vendors.add((found.group(1) != null ? found.group(1) : found.group(2)).toLowerCase(java.util.Locale.ROOT));
            }
        });
        return vendors;
    }

    /** The CUDA 12.8 GA driver floor's, and the converter's compute capability floor's, lines for each card (§4). */
    private List<String> cardWarnings() {
        ProcessOutcome outcome = runCounting(host.cardQuery(settings.wslDistribution()), COMMAND_LIMIT);
        if (outcome.exitCode() != 0) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        String floor = host.driverFloor();
        outcome.output().replace("\u0000", "").replace("﻿", "").lines().map(String::strip)
                .filter(line -> !line.isEmpty()).forEach(line -> {
                    String[] card = line.split(", ", -1);
                    if (card.length != 3) {
                        return;
                    }
                    String name = card[0].strip();
                    String driver = card[1].strip();
                    String capability = card[2].strip();
                    boolean driverIsOld;
                    boolean capabilityIsOld;
                    try {
                        driverIsOld = compareNumberByNumber(driver, floor) < 0;
                        capabilityIsOld = compareNumberByNumber(capability, "7.5") < 0;
                    } catch (NumberFormatException unreadable) {
                        return;
                    }
                    if (driverIsOld) {
                        warnings.add("The NVIDIA driver of " + name + " is " + driver + ", and the GPU image's CUDA 12.8"
                                + " needs " + floor + " or later, so the containers may not start on the card: update the"
                                + " driver" + (host == HostSystem.WINDOWS ? ", on Windows," : "")
                                + " or choose the CPU image.");
                    }
                    if (capabilityIsOld) {
                        warnings.add(name + " has compute capability " + capability + ", and the GPU image's converter"
                                + " needs 7.5 or later, a Turing card (GeForce RTX 20 or GTX 16) or newer, so it may not"
                                + " convert on the card: choose the CPU image if it does not.");
                    }
                });
        return warnings;
    }

    /** Two dotted versions compared number by number; a part that is no whole number is a {@link NumberFormatException}. */
    private static int compareNumberByNumber(String version, String other) {
        String[] left = version.split("\\.", -1);
        String[] right = other.split("\\.", -1);
        for (int at = 0; at < Math.max(left.length, right.length); at++) {
            int one = at < left.length ? Integer.parseUnsignedInt(left[at]) : 0;
            int two = at < right.length ? Integer.parseUnsignedInt(right[at]) : 0;
            if (one != two) {
                return Integer.compare(one, two);
            }
        }
        return 0;
    }

    /**
     * Whether the engine lists the {@code nvidia} runtime, asked only where ADR-430 §3's check finds it answering, which
     * starts nothing and tells nothing (ADR-444 §5, §10).
     *
     * @return empty where nothing was asked: macOS, no container on this machine, or no engine answering
     */
    Optional<Boolean> nvidiaRuntimeListedStartingNothing() {
        if (host == HostSystem.MAC || !engineAnswersStartingNothing().orElse(false)) {
            return Optional.empty();
        }
        try {
            return Optional.of(runtimeListed());
        } catch (DockerIsNotInstalledException notInstalled) {
            return Optional.empty();
        }
    }

    /** The lines of §4's warnings, asked as {@link #nvidiaRuntimeListedStartingNothing()} is, and told to no one. */
    List<String> graphicsCardWarningsStartingNothing() {
        if (host == HostSystem.MAC || !engineAnswersStartingNothing().orElse(false)) {
            return List.of();
        }
        return cardWarnings();
    }

    // ---- the toolkit's install (ADR-444 §6)

    /** The version of the NVIDIA Container Toolkit the jar pins (ADR-444 §6). */
    private static final String NVIDIA_TOOLKIT_VERSION = "1.20.2-1";

    /** The line of an install that ran every command (ADR-444 §6). */
    static final String NVIDIA_TOOLKIT_INSTALLED = "The NVIDIA Container Toolkit " + NVIDIA_TOOLKIT_VERSION
            + " is installed, and Docker was restarted with its nvidia runtime.";

    private static final String THE_KEYRING = "/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg";

    /** The install's commands, root's, in order (ADR-444 §6). */
    private static final List<List<String>> NVIDIA_TOOLKIT_INSTALL = List.of(
            List.of("apt-get", "update"),
            List.of("apt-get", "install", "-y", "--no-install-recommends", "ca-certificates", "curl", "gnupg2"),
            List.of("bash", "-c", "set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey"
                    + " | gpg --batch --yes --dearmor -o " + THE_KEYRING),
            List.of("bash", "-c", "set -euo pipefail; curl -fsSL"
                    + " https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list"
                    + " | sed 's#deb https://#deb [signed-by=" + THE_KEYRING + "] https://#g'"
                    + " > /etc/apt/sources.list.d/nvidia-container-toolkit.list"),
            List.of("apt-get", "update"),
            List.of("apt-get", "install", "-y", "nvidia-container-toolkit=" + NVIDIA_TOOLKIT_VERSION,
                    "nvidia-container-toolkit-base=" + NVIDIA_TOOLKIT_VERSION,
                    "libnvidia-container-tools=" + NVIDIA_TOOLKIT_VERSION,
                    "libnvidia-container1=" + NVIDIA_TOOLKIT_VERSION),
            List.of("nvidia-ctk", "runtime", "configure", "--runtime=docker"),
            List.of("systemctl", "restart", "docker"));

    /** Each command of each install is killed after this long. */
    private static final Duration INSTALL_COMMAND_LIMIT = Duration.ofMinutes(10);

    /** The install's commands as the confirmation lists them, none on macOS (ADR-444 §6). */
    List<String> nvidiaToolkitCommands() {
        if (host == HostSystem.MAC) {
            return List.of();
        }
        return NVIDIA_TOOLKIT_INSTALL.stream().map(ContainerStarter::asListed).toList();
    }

    /** The words of a command joined by spaces, a word holding a space between double quotes. */
    private static String asListed(List<String> command) {
        return command.stream().map(word -> word.contains(" ") ? "\"" + word + "\"" : word)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /**
     * Installs the NVIDIA Container Toolkit as root: inside the WSL distribution on Windows, through {@code sudo -S}
     * fed {@code sudoPassword} on Linux (ADR-444 §6, §8). The first command that does not exit 0 ends it. Every character
     * of {@code sudoPassword} is set to {@code \0} on return.
     *
     * @return the line of what came of it, {@link #NVIDIA_TOOLKIT_INSTALLED} where every command exited 0
     */
    String installTheNvidiaToolkit(char[] sudoPassword) {
        try {
            if (host == HostSystem.MAC) {
                return "Vespera installs the NVIDIA Container Toolkit on Windows and Linux alone.";
            }
            try (ContainerLock lock = takeTheLock()) {
                for (List<String> command : NVIDIA_TOOLKIT_INSTALL) {
                    String listed = asListed(command);
                    say("Installing the NVIDIA Container Toolkit: {}", listed);
                    char[] input = standardInputOfTheInstall(sudoPassword);
                    ProcessOutcome outcome;
                    try {
                        outcome = runFeedingTelling(host.asRoot(settings.wslDistribution(), command), input);
                    } catch (CommandOutputFileUnusableException unusable) {
                        return "The NVIDIA Container Toolkit was not installed: " + unusable.unusable()
                                + ". Nothing after it was run.";
                    } finally {
                        if (input != null) {
                            java.util.Arrays.fill(input, '\0');
                        }
                    }
                    if (outcome.exitCode() != 0) {
                        return "The NVIDIA Container Toolkit was not installed: " + listed + " exited "
                                + outcome.exitCode() + ": " + outcome.lastLine() + ". Nothing after it was run.";
                    }
                }
                return NVIDIA_TOOLKIT_INSTALLED;
            } catch (ContainerLockUnavailableException unusable) {
                return "The NVIDIA Container Toolkit was not installed: " + unusable.unusable() + ". Nothing was run.";
            } catch (EarlierVersionHoldsTheLockException held) {
                return "The NVIDIA Container Toolkit was not installed: " + held.unusable() + ". Nothing was run.";
            }
        } finally {
            if (sudoPassword != null) {
                java.util.Arrays.fill(sudoPassword, '\0');
            }
        }
    }

    /** Nothing on Windows; on Linux the password and one line end, an empty line where none was typed (ADR-444 §6). */
    private char[] standardInputOfTheInstall(char[] sudoPassword) {
        if (host != HostSystem.LINUX) {
            return null;
        }
        char[] typed = sudoPassword == null ? new char[0] : sudoPassword;
        char[] input = java.util.Arrays.copyOf(typed, typed.length + 1);
        input[typed.length] = '\n';
        return input;
    }

    /** As {@link #runTelling}, writing {@code input} on the command's standard input; the password is in no line. */
    private ProcessOutcome runFeedingTelling(List<String> command, char[] input) {
        try {
            ProcessOutcome outcome = runner.runFeeding(command, INSTALL_COMMAND_LIMIT, input);
            sayWhatItPrinted(outcome.output());
            return outcome;
        } catch (CommandOutputFileException unusable) {
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
            say("Starting the containers: {}", ContainerStartRefusedException.asRun(up));
            ProcessOutcome outcome;
            try {
                outcome = runner.runToTheEnd(up, line -> say("{}", line));
            } catch (IOException cannotStart) {
                throw new DockerIsNotInstalledException(host.engineProgram());
            }
            if (outcome.exitCode() != 0) {
                throw new ContainersDidNotStartException(up, outcome);
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
        say("Waiting up to {} seconds for {} to answer", settings.waitAtMost().toSeconds(), names);
        long began = System.nanoTime();
        while (true) {
            boolean docling = !askDocling || health.doclingAnswers();
            boolean ollama = !askOllama || health.ollamaAnswers();
            if (docling && ollama) {
                say("{} answer after {} seconds", names, secondsSince(began));
                return;
            }
            if (System.nanoTime() - began >= settings.waitAtMost().toNanos()) {
                String which = !docling && !ollama ? "docling-serve and Ollama" : docling ? "Ollama" : "docling-serve";
                throw new ContainersDidNotAnswerException(which, settings.waitAtMost());
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
            throw new IllegalStateException("interrupted while waiting for the containers", interrupted);
        }
    }

    // ---- the locks (ADR-323 §6, ADR-433 §6)

    /**
     * Takes {@code containers.lock} in the lock folder, waiting for it where another invocation holds it: unlike the
     * working directory's and the corpus root's, it is waited for, because the holder is doing what this would. Then
     * tries the lock file of an earlier version, without waiting for it: held, the action is refused and
     * {@code containers.lock} is let go; not held, it is kept beside the first until the action ends (ADR-433 §6).
     */
    private ContainerLock takeTheLock() {
        ContainerLock own = takeTheLockOf(LOCK_FILE, true);
        try {
            ContainerLock earlier = takeTheLockOf(THE_EARLIER_LOCK_FILE, false);
            return new ContainerLock(earlier, own);
        } catch (RuntimeException refused) {
            own.close();
            throw refused;
        }
    }

    private ContainerLock takeTheLockOf(String fileName, boolean waitForIt) {
        Path file = lockFolder.resolve(fileName);
        FileChannel channel;
        try {
            Files.createDirectories(lockFolder);
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException unavailable) {
            throw new ContainerLockUnavailableException(file, unavailable);
        }
        boolean said = false;
        while (true) {
            try {
                FileLock lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
                if (lock != null) {
                    return new ContainerLock(channel, lock, null);
                }
            } catch (OverlappingFileLockException heldByThisProcess) {
                // Another lock of this JVM holds it, which a test can.
            } catch (IOException unlockable) {
                closeQuietly(channel);
                throw new ContainerLockUnavailableException(file, unlockable);
            }
            if (!waitForIt) {
                closeQuietly(channel);
                throw new EarlierVersionHoldsTheLockException(file);
            }
            if (!said) {
                say("Another Vespera invocation is starting the containers; waiting for it");
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

    /** The held lock, or two, the earlier version's and this one's, released and the channels closed at the end of the action. */
    private record ContainerLock(FileChannel channel, FileLock lock, ContainerLock alsoHeld) implements AutoCloseable {

        /** The earlier lock, released before the one it is kept beside. */
        ContainerLock(ContainerLock earlier, ContainerLock own) {
            this(own.channel, own.lock, earlier);
        }

        @Override
        public void close() {
            if (alsoHeld != null) {
                alsoHeld.close();
            }
            try {
                lock.release();
            } catch (IOException ignored) {
                // Closing the channel releases it all the same.
            }
            closeQuietly(channel);
        }
    }

    /**
     * An earlier version of Vespera holds its lock file, so that it may be starting or stopping the containers
     * (ADR-433 §6). A start of the containers and the engine's start are refused by its message; the stop and the
     * shut-down say {@link #unusable()} in their own lines.
     */
    static final class EarlierVersionHoldsTheLockException extends ContainerStartRefusedException {

        private final String unusable;

        EarlierVersionHoldsTheLockException(Path lockFile) {
            super("the containers are not answering and Vespera cannot start them: an earlier version of Vespera holds"
                    + " the lock file " + lockFile.toAbsolutePath() + ", and may be starting or stopping them. Let it"
                    + " finish, or end it, and run the same command again.");
            this.unusable = "an earlier version of Vespera holds the lock file " + lockFile.toAbsolutePath();
        }

        /** What the refusal says of the lock, for the lines of the stops (ADR-390 §6). */
        String unusable() {
            return unusable;
        }
    }
}
