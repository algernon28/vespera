package io.algernon.vespera.engine.pipeline;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Docker engine's code as the control panel calls it, in its own process (ADR-390 §4): the one class of the jar
 * the control panel names. It names no type of Spring, of JavaFX or of the control panel, and its public constructors
 * and methods take and give only primitives, types of {@code java.base} and itself.
 *
 * <p>It is built over {@link ContainerStarter}, with {@link HostSystem#current()}, the runner of real processes on
 * {@code java.io.tmpdir} and the health that asks over HTTP, as {@link ContainerStarter#forTheApplication} builds them;
 * its lines are ADR-323 §8's, ADR-390 §6's and ADR-410 §3's. Its log lines go where this process sends them,
 * its console.
 */
public final class DockerEngine {

    /** The line of a shut-down that stopped both the containers and the engine (ADR-390 §6). */
    public static final String SHUT_DOWN_DONE = ContainerStarter.SHUT_DOWN_DONE;

    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    private final String wslDistribution;
    private final Duration waitAtMost;
    private final Path lockFolder;

    private final Path profileFolder;

    private final Path composeFolder;

    private DockerEngine(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder,
            Path composeFolder) {
        this.wslDistribution = wslDistribution == null ? "" : wslDistribution;
        this.waitAtMost = waitAtMost;
        this.lockFolder = lockFolder;
        this.profileFolder = profileFolder;
        this.composeFolder = composeFolder;
    }

    /**
     * @param wslDistribution the WSL distribution Docker runs in on Windows, blank for WSL's default; read on Windows
     *     alone
     * @param waitAtMost the bound of every start and stop command and of the wait for the engine
     * @param lockFolder the folder {@code containers.lock} lies in
     * @param profileFolder the Windows profile folder {@code .wslconfig} lies in, null where none is known (ADR-421)
     */
    public static DockerEngine of(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder) {
        return of(wslDistribution, waitAtMost, lockFolder, profileFolder, null);
    }

    /**
     * As the four-argument {@code of}, with {@code composeFolder} the folder the compose files are read from, null for
     * the folder beside the jar (ADR-397 §6, ADR-430 §12).
     */
    public static DockerEngine of(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder,
            Path composeFolder) {
        return new DockerEngine(wslDistribution, waitAtMost, lockFolder, profileFolder, composeFolder);
    }

    /**
     * On Windows, with at least one container on this machine, writes {@code .wslconfig} where there is none and reads
     * one that is there (ADR-421, ADR-430 §10). Runs no command and takes no lock.
     *
     * @return the line to show; empty where the file sets the setting, the system is not Windows, or neither container
     *     is on this machine
     */
    public Optional<String> keepTheWslDistributionUp(URI doclingBaseUrl, URI ollamaBaseUrl) {
        return keepTheWslDistributionUp(doclingBaseUrl, ollamaBaseUrl, line -> {
        });
    }

    /** As the two-address form, telling {@code told} the line it logs, on the thread that logs it (ADR-437 §3, §7). */
    public Optional<String> keepTheWslDistributionUp(URI doclingBaseUrl, URI ollamaBaseUrl, Consumer<String> told) {
        return starter(doclingBaseUrl, ollamaBaseUrl).telling(told).keepTheWslDistributionUp();
    }

    /**
     * Makes the engine answer where it does not, doing nothing only where neither container is on this machine; once it
     * answers, asks the docker client for Compose and buildx (ADR-410 §2). No container is asked and no {@code up} is
     * run. The control panel's window no longer calls it (ADR-430 §12).
     *
     * @return the refusal's line; empty where the engine answers and both are installed, or where
     *     nothing was to be done
     */
    public Optional<String> startTheEngine(URI doclingBaseUrl, URI ollamaBaseUrl) {
        try {
            starter(doclingBaseUrl, ollamaBaseUrl).ensureTheEngineRuns();
            return Optional.empty();
        } catch (ContainerStartRefusedException refused) {
            return Optional.of(refused.getMessage());
        }
    }

    /**
     * Whether a container's base URL names this machine, its host resolved (ADR-430 §10).
     *
     * @return true for this machine, false for another, empty where the host cannot be resolved
     */
    public static Optional<Boolean> isOnThisMachine(URI baseUrl) {
        return switch (ThisMachine.system().where(baseUrl)) {
            case THIS_MACHINE -> Optional.of(true);
            case ANOTHER_MACHINE -> Optional.of(false);
            case NOT_RESOLVED -> Optional.empty();
        };
    }

    /**
     * Asks the Docker daemon and starts nothing (ADR-430 §3).
     *
     * @return whether it answers; empty where neither container is on this machine and nothing was asked
     */
    public Optional<Boolean> engineAnswers(URI doclingBaseUrl, URI ollamaBaseUrl) {
        return starter(doclingBaseUrl, ollamaBaseUrl).engineAnswersStartingNothing();
    }

    /**
     * What Start Docker does (ADR-430 §5): makes the containers on this machine answer, starting the engine where it
     * does not and running {@code up} with {@code --no-recreate}. Nothing is done, and no lock is taken, where neither
     * container is on this machine.
     *
     * @return the refusal's line; empty where started, already answering or nothing was to be done
     */
    public Optional<String> startTheContainers(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage) {
        return startTheContainers(doclingBaseUrl, ollamaBaseUrl, doclingImage, line -> {
        });
    }

    /**
     * As the three-argument form, telling {@code told} each line the start logs, the engine's start and what it printed
     * among them, on the thread that logs it (ADR-437 §3, §7).
     */
    public Optional<String> startTheContainers(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage,
            Consumer<String> told) {
        ContainerStartSettings settings = new ContainerStartSettings(
                doclingBaseUrl, ollamaBaseUrl, doclingImage == null ? "" : doclingImage, waitAtMost, wslDistribution,
                profileFolder);
        ContainerStarter starter = new ContainerStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpContainerHealth(settings),
                HostSystem.current(),
                () -> composeFolder == null
                        ? BesideTheJar.ofThisApplication().composeFiles()
                        : ComposeFiles.in(composeFolder),
                lockFolder,
                CHECK_EVERY);
        try {
            // .wslconfig is still seen to, but its line was logged by keepTheWslDistributionUp, which the window calls first
            starter.telling(told).ensureRunning(false);
            return Optional.empty();
        } catch (ContainerStartRefusedException refused) {
            return Optional.of(refused.getMessage());
        }
    }

    /**
     * Stops the containers alone, where one answers on this machine.
     *
     * @return the refusal's line; empty where stopped or nothing was to be done
     */
    public Optional<String> stopTheContainers(URI doclingBaseUrl, URI ollamaBaseUrl) {
        return starter(doclingBaseUrl, ollamaBaseUrl).stopTheContainers();
    }

    /** Stops the containers and then the engine; the line says what came of it, {@link #SHUT_DOWN_DONE} where both stopped. */
    public String shutDown() {
        return shutDown(line -> {
        });
    }

    /** As {@link #shutDown()}, telling {@code told} each line of the two stops and what each printed (ADR-437 §3, §7). */
    public String shutDown(Consumer<String> told) {
        return starter(URI.create(ContainerStartSettings.DEFAULT_DOCLING), URI.create(ContainerStartSettings.DEFAULT_OLLAMA))
                .telling(told).shutDown().line();
    }

    private ContainerStarter starter(URI doclingBaseUrl, URI ollamaBaseUrl) {
        ContainerStartSettings settings =
                new ContainerStartSettings(doclingBaseUrl, ollamaBaseUrl, "", waitAtMost, wslDistribution, profileFolder);
        return new ContainerStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpContainerHealth(settings),
                HostSystem.current(),
                () -> {
                    throw new UnsupportedOperationException("the control panel writes no compose file out");
                },
                lockFolder,
                CHECK_EVERY);
    }
}
