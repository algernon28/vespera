package io.algernon.controlpanel;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Starts a window of a Chromium browser with no address bar where one is found, and the system's own command for
 * opening an address, as ADR-266 §2 names it, otherwise (ADR-331 §1; ADR-325 §7): without waiting for it and with its
 * output discarded, and opens nothing where no person could see it: under {@code CI}, or on a system other than Windows
 * and macOS with no display. It holds the choice in two functions and no switch to not open.
 */
final class SystemBrowserOpener implements BrowserOpener {

    @Override
    public void open(URI address) throws IOException {
        Optional<List<String>> command = windowCommand(address, System.getProperty("os.name"), System.getenv(),
                Files::exists);
        if (command.isPresent()) {
            new ProcessBuilder(command.get())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }

    /**
     * The command that opens {@code address} as a window with no address bar in the first browser {@code exists}
     * accepts, in ADR-331 §1's order for the system {@code osName}; {@link #command}'s where it accepts none, and empty
     * wherever {@link #command} is empty.
     */
    static Optional<List<String>> windowCommand(URI address, String osName, Map<String, String> environment,
            Predicate<Path> exists) {
        Optional<List<String>> own = command(address, osName, environment);
        if (own.isEmpty()) {
            return own;
        }
        String system = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String asAWindow = "--app=" + address;
        if (system.startsWith("windows")) {
            List<Path> candidates = new ArrayList<>();
            for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles")) {
                String named = ignoringCase(environment, variable);
                if (absoluteOnWindows(named)) {
                    addIfHeld(candidates, named, "Microsoft", "Edge", "Application", "msedge.exe");
                    addIfHeld(candidates, named, "Google", "Chrome", "Application", "chrome.exe");
                }
            }
            String localAppData = ignoringCase(environment, "LOCALAPPDATA");
            if (absoluteOnWindows(localAppData)) {
                addIfHeld(candidates, localAppData, "Google", "Chrome", "Application", "chrome.exe");
            }
            return candidates.stream().filter(exists).findFirst()
                    .map(found -> List.of(found.toString(), asAWindow)).or(() -> own);
        }
        if (system.startsWith("mac")) {
            for (String name : List.of("Microsoft Edge", "Google Chrome")) {
                if (exists.test(Path.of("/Applications", name + ".app"))) {
                    return Optional.of(List.of("open", "-na", name, "--args", asAWindow));
                }
            }
            return own;
        }
        String folders = set(environment, "PATH") ? environment.get("PATH") : "";
        for (String name : List.of("chromium", "chromium-browser", "google-chrome", "microsoft-edge")) {
            for (String folder : folders.split(":")) {
                if (folder.startsWith("/") && exists.test(Path.of(folder, name))) {
                    return Optional.of(List.of(Path.of(folder, name).toString(), asAWindow));
                }
            }
        }
        return own;
    }

    /** The value of the variable {@code name} in whatever case, as Windows holds one; empty where absent or blank (ADR-331 §1). */
    private static String ignoringCase(Map<String, String> environment, String name) {
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse("");
    }

    /** Adds the path {@code first} and {@code more} name; adds nothing where the system cannot hold the text as a path. */
    private static void addIfHeld(List<Path> candidates, String first, String... more) {
        try {
            candidates.add(Path.of(first, more));
        } catch (java.nio.file.InvalidPathException refused) {
            // a value that names no folder finds no browser there
        }
    }

    /** Whether {@code folder} is absolute as Windows writes it, told by its text: a drive, a colon and a separator. */
    private static boolean absoluteOnWindows(String folder) {
        return folder.matches("^[A-Za-z]:[\\\\/].*");
    }

    /** The command that opens {@code address} on the system {@code osName}, empty where nothing is opened. */
    static Optional<List<String>> command(URI address, String osName, Map<String, String> environment) {
        if (set(environment, "CI")) {
            return Optional.empty();
        }
        String system = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (system.startsWith("windows")) {
            return Optional.of(List.of("rundll32", "url.dll,FileProtocolHandler", address.toString()));
        }
        if (system.startsWith("mac")) {
            return Optional.of(List.of("open", address.toString()));
        }
        return set(environment, "DISPLAY") || set(environment, "WAYLAND_DISPLAY")
                ? Optional.of(List.of("xdg-open", address.toString()))
                : Optional.empty();
    }

    private static boolean set(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }
}
