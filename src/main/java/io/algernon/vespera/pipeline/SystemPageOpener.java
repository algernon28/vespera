package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Starts the system's own command for opening a file with its default program, without waiting for it, and opens
 * nothing where no person could see it: under {@code CI}, or on a system other than Windows and macOS with no display
 * (ADR-266 section 2). Holds no rule.
 */
@Component
class SystemPageOpener implements PageOpener {

    @Override
    public void open(URI page) throws IOException {
        Optional<List<String>> command = command(page, System.getProperty("os.name"), System.getenv());
        if (command.isPresent()) {
            new ProcessBuilder(command.get())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }

    static Optional<List<String>> command(URI page, String osName, Map<String, String> environment) {
        if (set(environment, "CI")) {
            return Optional.empty();
        }
        String system = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (system.startsWith("windows")) {
            return Optional.of(List.of("rundll32", "url.dll,FileProtocolHandler", page.toString()));
        }
        if (system.startsWith("mac")) {
            return Optional.of(List.of("open", page.toString()));
        }
        return set(environment, "DISPLAY") || set(environment, "WAYLAND_DISPLAY")
                ? Optional.of(List.of("xdg-open", page.toString()))
                : Optional.empty();
    }

    private static boolean set(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }
}
