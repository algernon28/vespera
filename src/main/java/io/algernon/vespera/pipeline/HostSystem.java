package io.algernon.vespera.pipeline;

import java.util.List;
import java.util.Locale;

/** The operating systems whose Docker engine Vespera knows how to start (ADR-323 §5). */
enum HostSystem {
    WINDOWS,
    MAC,
    LINUX;

    private static final List<String> DESKTOP_START = List.of("docker", "desktop", "start", "--detach");

    /** The system this JVM runs on; anything that is neither Windows nor macOS is taken for Linux. */
    static HostSystem current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return MAC;
        }
        return LINUX;
    }

    /** The commands that start the engine, tried in this order until one exits 0 (ADR-323 §5's table). */
    List<List<String>> engineStartCommands() {
        return switch (this) {
            case WINDOWS -> List.of(
                    DESKTOP_START,
                    List.of("cmd", "/c", "start", "", programFiles() + "\\Docker\\Docker\\Docker Desktop.exe"));
            case MAC -> List.of(DESKTOP_START, List.of("open", "-a", "Docker"));
            case LINUX -> List.of(List.of("systemctl", "--no-ask-password", "start", "docker"), DESKTOP_START);
        };
    }

    private static String programFiles() {
        String configured = System.getenv("ProgramFiles");
        return configured == null || configured.isBlank() ? "C:\\Program Files" : configured;
    }
}
