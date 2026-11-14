package io.algernon.vespera.engine.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The operating systems whose Docker engine Vespera knows how to start and stop (ADR-323 §5, ADR-389 §1). */
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

    /** The engine check, killed after thirty seconds (ADR-389 §2): {@code docker version}, inside WSL on Windows. */
    List<String> engineCheck(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "version", "--format", "{{.Server.Version}}"));
    }

    /** The one command that starts the engine (ADR-389 §2). */
    List<String> engineStart(String wslDistribution) {
        return switch (this) {
            case LINUX -> List.of("systemctl", "--no-ask-password", "start", "docker");
            case WINDOWS -> inTheEngine(wslDistribution, List.of("systemctl", "start", "docker"));
            case MAC -> List.of("colima", "start");
        };
    }

    /** The stop of the sidecars, {@code stop} and nothing harder (ADR-389 §3, step 1). */
    List<String> composeStop(String wslDistribution) {
        return inTheEngine(wslDistribution, List.of("docker", "compose", "-p", "vespera", "stop"));
    }

    /** The one command that stops the engine (ADR-389 §3, step 2). */
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
     * (ADR-389 §1); elsewhere it names each file by its path.
     */
    List<String> composeUp(String wslDistribution, List<Path> composeFiles) {
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
        return List.copyOf(command);
    }

    /** {@code command}, run inside the WSL distribution on Windows and as it is elsewhere (ADR-389 §1). */
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

    /** The program of the engine check, which a machine without it is Docker not being installed (ADR-389 §2). */
    String engineProgram() {
        return this == WINDOWS ? "wsl" : "docker";
    }
}
