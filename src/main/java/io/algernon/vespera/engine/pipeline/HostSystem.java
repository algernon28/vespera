package io.algernon.vespera.engine.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The operating systems whose Docker engine Vespera knows how to start and stop (ADR-323 §5, ADR-390 §1). */
enum HostSystem {
    WINDOWS,
    MAC,
    LINUX;

    /** The system this JVM runs on; anything that is neither Windows nor macOS is taken for Linux. */
    static HostSystem current() {
        return named(System.getProperty("os.name", ""));
    }

    /** The system an {@code os.name} names: macOS is asked first, because "Darwin" contains "win" (ADR-323 §5). */
    static HostSystem named(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return MAC;
        }
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        return LINUX;
    }

    /** The engine check, killed after thirty seconds (ADR-390 §2): {@code docker version}, inside WSL on Windows. */
    List<String> engineCheck(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "version", "--format", "{{.Server.Version}}"));
    }

    /** The check for Compose, {@code docker compose version}, inside WSL on Windows (ADR-410 §1). */
    List<String> composeCheck(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "compose", "version"));
    }

    /** The check for buildx, {@code docker buildx version}, inside WSL on Windows (ADR-410 §1). */
    List<String> buildxCheck(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "buildx", "version"));
    }

    /** The one command that starts the engine (ADR-390 §2). */
    List<String> engineStart(String wslDistribution) {
        return switch (this) {
            case LINUX -> List.of("systemctl", "--no-ask-password", "start", "docker");
            case WINDOWS -> inTheEngine(wslDistribution, List.of("systemctl", "start", "docker"));
            case MAC -> List.of("colima", "start");
        };
    }

    /** The stop of the containers, {@code stop} and nothing harder (ADR-390 §3, step 1). */
    List<String> composeStop(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "compose", "-p", "vespera", "stop"));
    }

    /** The one command that stops the engine (ADR-390 §3, step 2). */
    List<String> engineStop(String wslDistribution) {
        return switch (this) {
            case LINUX -> List.of("systemctl", "--no-ask-password", "stop", "docker.socket", "docker.service");
            case WINDOWS -> inTheEngine(wslDistribution, List.of("systemctl", "stop", "docker.socket", "docker.service"));
            case MAC -> List.of("colima", "stop");
        };
    }

    /**
     * ADR-323 §4's {@code up}, {@code composeFiles} being the files written out to one folder, {@code compose.yaml}
     * first. On Windows it runs inside the distribution, given the folder by {@code --cd} and the files by their names
     * (ADR-390 §1); elsewhere it names each file by its path.
     */
    List<String> composeUp(String wslDistribution, List<Path> composeFiles) {
        return composeUp(wslDistribution, composeFiles, List.of());
    }

    /** {@code wsl --list --running --quiet}: the distributions that run, listing which boots none (ADR-430 §3). */
    List<String> wslRunning() {
        return List.of("wsl", "--list", "--running", "--quiet");
    }

    /** {@code wsl --list --verbose}: every distribution, WSL's default marked with {@code *} (ADR-430 §3). */
    List<String> wslVerbose() {
        return List.of("wsl", "--list", "--verbose");
    }

    /** Linux's question whether the engine's service is active, which starts nothing (ADR-430 §3). */
    List<String> engineServiceActive() {
        return List.of("systemctl", "is-active", "--quiet", "docker.service");
    }

    /** {@link #composeUp(String, List)} naming {@code services} after its options, none naming every service. */
    List<String> composeUp(String wslDistribution, List<Path> composeFiles, List<String> services) {
        List<String> command = new ArrayList<>();
        if (this == WINDOWS) {
            command.addAll(wsl(wslDistribution));
            command.add("--cd");
            command.add(composeFiles.getFirst().toAbsolutePath().getParent().toString());
            command.add("--exec");
        }
        command.addAll(List.of("docker", "compose", "-p", "vespera"));
        for (Path file : composeFiles) {
            command.add("-f");
            command.add(this == WINDOWS ? file.getFileName().toString() : file.toString());
        }
        command.addAll(List.of("up", "-d", "--build", "--no-recreate"));
        command.addAll(services);
        return List.copyOf(command);
    }

    /** The runtime check, {@code docker info --format {{json .Runtimes}}}, inside WSL on Windows (ADR-444 §1). */
    List<String> runtimeCheck(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "info", "--format", "{{json .Runtimes}}"));
    }

    /**
     * The read of the display adapters, run on the system itself and not inside WSL (ADR-444 §3); none on macOS, where
     * the GPU image is refused before any command.
     */
    List<String> cardsRead() {
        return switch (this) {
            case WINDOWS -> List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_VideoController | ForEach-Object PNPDeviceID");
            case LINUX -> List.of("sh", "-c",
                    "for d in /sys/bus/pci/devices/*; do case \"$(cat \"$d/class\")\" in 0x03*) cat \"$d/vendor\";;"
                            + " esac; done");
            case MAC -> List.of();
        };
    }

    /** The query of the cards' names, drivers and compute capabilities; inside WSL on Windows, by the path off root's (ADR-444 §4). */
    List<String> cardQuery(String wslDistribution) {
        List<String> query = List.of("--query-gpu=name,driver_version,compute_cap", "--format=csv,noheader");
        List<String> command = new ArrayList<>();
        command.add(this == WINDOWS ? "/usr/lib/wsl/lib/nvidia-smi" : "nvidia-smi");
        command.addAll(query);
        return inTheEngine(wslDistribution, command);
    }

    /** The driver's version the GPU image's CUDA 12.8 needs at least: 570.65 on Windows, 570.26 elsewhere (ADR-444 §4). */
    String driverFloor() {
        return this == WINDOWS ? "570.65" : "570.26";
    }

    /** {@code command} as root: inside the WSL distribution on Windows, through {@code sudo -k -S} on Linux (ADR-444 §6). */
    List<String> asRoot(String wslDistribution, List<String> command) {
        if (this == WINDOWS) {
            return inTheEngine(wslDistribution, command);
        }
        List<String> through = new ArrayList<>(List.of("sudo", "-k", "-S"));
        through.addAll(command);
        return List.copyOf(through);
    }

    /** Where the engine is, as ADR-410 §3 and ADR-444 §2 name it. */
    String whereTheEngineIs(String wslDistribution) {
        if (this != WINDOWS) {
            return "on this machine";
        }
        String named = wslDistribution == null ? "" : wslDistribution.strip();
        return named.isEmpty() ? "in WSL's default distribution" : "in the WSL distribution " + named;
    }

    /** {@code command}, run inside the WSL distribution on Windows and as it is elsewhere (ADR-390 §1). */
    private List<String> inTheEngine(String wslDistribution, List<String> command) {
        if (this != WINDOWS) {
            return command;
        }
        List<String> inside = new ArrayList<>(wsl(wslDistribution));
        inside.add("--exec");
        inside.addAll(command);
        return List.copyOf(inside);
    }

    private static List<String> wsl(String wslDistribution) {
        List<String> prefix = new ArrayList<>(List.of("wsl"));
        if (wslDistribution != null && !wslDistribution.isBlank()) {
            prefix.add("-d");
            prefix.add(wslDistribution.strip());
        }
        prefix.add("-u");
        prefix.add("root");
        return prefix;
    }

    /** The program of the engine check, which a machine without it is Docker not being installed (ADR-390 §2). */
    String engineProgram() {
        return this == WINDOWS ? "wsl" : "docker";
    }
}
