package io.algernon.vespera.engine.pipeline;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * The Docker engine's code as the control panel calls it, in its own process (ADR-390 §4): the one class of the jar
 * the control panel names. It names no type of Spring, of JavaFX or of the control panel, and its public constructors
 * and methods take and give only primitives, types of {@code java.base} and itself.
 *
 * <p>It is built over {@link SidecarStarter}, with {@link HostSystem#current()}, the runner of real processes on
 * {@code java.io.tmpdir} and the health that asks over HTTP, as {@link SidecarStarter#forTheApplication} builds them;
 * its lines are ADR-323 §8's, ADR-390 §6's and ADR-410 §3's. Its log lines go where this process sends them,
 * its console.
 */
public final class DockerEngine {

    /** The line of a shut-down that stopped both the sidecars and the engine (ADR-390 §6). */
    public static final String SHUT_DOWN_DONE = SidecarStarter.SHUT_DOWN_DONE;

    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    private final String wslDistribution;
    private final Duration waitAtMost;
    private final Path lockFolder;

    private final Path profileFolder;

    private DockerEngine(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder) {
        this.wslDistribution = wslDistribution == null ? "" : wslDistribution;
        this.waitAtMost = waitAtMost;
        this.lockFolder = lockFolder;
        this.profileFolder = profileFolder;
    }

    /**
     * @param wslDistribution the WSL distribution Docker runs in on Windows, blank for WSL's default; read on Windows
     *     alone
     * @param waitAtMost the bound of every start and stop command and of the wait for the engine
     * @param lockFolder the folder {@code sidecars.lock} lies in
     * @param profileFolder the Windows profile folder {@code .wslconfig} lies in, null where none is known (ADR-421)
     */
    public static DockerEngine of(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder) {
        return new DockerEngine(wslDistribution, waitAtMost, lockFolder, profileFolder);
    }

    /**
     * On Windows, with both base URLs on this machine, writes {@code .wslconfig} where there is none and reads one that
     * is there (ADR-421). Runs no command and takes no lock.
     *
     * @return the line to show; empty where the file sets the setting, the system is not Windows, or a base URL names
     *     another machine
     */
    public Optional<String> keepTheWslDistributionUp(URI doclingBaseUrl, URI ollamaBaseUrl) {
        return starter(doclingBaseUrl, ollamaBaseUrl).keepTheWslDistributionUp();
    }

    /**
     * Makes the engine answer where it does not, a base URL on another machine doing nothing; once it answers, asks the
     * docker client for Compose and buildx (ADR-410 §2). No sidecar is asked and no {@code up} is run.
     *
     * @return the refusal's line; empty where the engine answers and both are installed, or where
     *     nothing was to be done
     */
    public Optional<String> startTheEngine(URI doclingBaseUrl, URI ollamaBaseUrl) {
        try {
            starter(doclingBaseUrl, ollamaBaseUrl).ensureTheEngineRuns();
            return Optional.empty();
        } catch (SidecarStartRefusedException refused) {
            return Optional.of(refused.getMessage());
        }
    }

    /**
     * Stops the sidecars alone, where one answers on this machine.
     *
     * @return the refusal's line; empty where stopped or nothing was to be done
     */
    public Optional<String> stopTheSidecars(URI doclingBaseUrl, URI ollamaBaseUrl) {
        return starter(doclingBaseUrl, ollamaBaseUrl).stopTheSidecars();
    }

    /** Stops the sidecars and then the engine; the line says what came of it, {@link #SHUT_DOWN_DONE} where both stopped. */
    public String shutDown() {
        return starter(URI.create(SidecarStartSettings.DEFAULT_DOCLING), URI.create(SidecarStartSettings.DEFAULT_OLLAMA))
                .shutDown().line();
    }

    private SidecarStarter starter(URI doclingBaseUrl, URI ollamaBaseUrl) {
        SidecarStartSettings settings =
                new SidecarStartSettings(doclingBaseUrl, ollamaBaseUrl, "", waitAtMost, wslDistribution, profileFolder);
        return new SidecarStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpSidecarHealth(settings),
                HostSystem.current(),
                () -> {
                    throw new UnsupportedOperationException("the control panel writes no compose file out");
                },
                lockFolder,
                CHECK_EVERY);
    }
}
