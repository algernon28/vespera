package io.algernon.controlpanel;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Starts the system's own command for opening an address, as ADR-266 §2 names it, without waiting for it and with its
 * output discarded, and opens nothing where no person could see it: under {@code CI}, or on a system other than Windows
 * and macOS with no display (ADR-323 §7). It holds the choice in one function and no switch to not open.
 */
final class SystemBrowserOpener implements BrowserOpener {

    @Override
    public void open(URI address) throws IOException {
        Optional<List<String>> command = command(address, System.getProperty("os.name"), System.getenv());
        if (command.isPresent()) {
            new ProcessBuilder(command.get())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
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
