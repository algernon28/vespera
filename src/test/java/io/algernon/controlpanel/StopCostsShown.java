package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What the control panel's Stop confirmation shows a stop to cost, read as a browser reads it (ADR-302 §7), for a
 * contract test outside this package, which cannot start {@link ScreenServer}: the {@code data-step} of each row of
 * {@code stop-costs}, in the page's order, while a {@code run} child of the control panel's runs.
 */
public final class StopCostsShown {

    private StopCostsShown() {
    }

    /**
     * Starts the control panel's server under {@code folder}, presses Run with a child that sleeps, opens Stop's
     * confirmation and returns the steps its rows name, in order; the server and the child are closed before it
     * returns.
     */
    public static List<String> stepsOfTheStopPage(Path folder) throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser.Page confirmation = screens.browser().press("launch", "run", Map.of())
                    .press("stop", "stop", Map.of()).page();
            return ScreenServer.byId(confirmation, "stop-costs").select("[data-step]").eachAttr("data-step");
        }
    }
}
