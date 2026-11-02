package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * What the control panel's Stop confirmation shows a stop to cost, read as a browser reads it (ADR-302 §7), for a
 * contract test outside this package, which cannot start {@link ScreenServer}: the {@code data-step} of each row of
 * {@code stop-costs}, in the page's order, while a {@code run} child of the control panel's runs.
 */
public final class StopCostsShown {

    /** How long a killed child's working directory is given to come free. */
    private static final Duration BOUND = Duration.ofSeconds(30);

    /** How long to wait between two looks at it. */
    private static final long POLL_MILLIS = 100;

    private StopCostsShown() {
    }

    /**
     * Starts the control panel's server under {@code folder}, presses Run with a child that sleeps, opens Stop's
     * confirmation and returns the steps its rows name, in order; the server and the child are closed before it
     * returns, and the child's working directory is free for the test's folder to be deleted.
     */
    public static List<String> stepsOfTheStopPage(Path folder) throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        List<String> steps;
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser.Page confirmation = screens.browser().press("launch", "run", Map.of())
                    .press("stop", "stop", Map.of()).page();
            steps = ScreenServer.byId(confirmation, "stop-costs").select("[data-step]").eachAttr("data-step");
        }
        untilNoProcessHolds(workingDirectory);
        return steps;
    }

    /**
     * Waits for {@code folder} to be movable: on Windows a killed child's working directory can stay in use for a moment
     * after the child has ended, as a jar it ran can ({@link ScreenServer#runtime}), and the folder could not then be
     * deleted. It stops waiting at the bound, and the deletion then says what still holds it.
     */
    private static void untilNoProcessHolds(Path folder) {
        Path moved = folder.resolveSibling(folder.getFileName() + "-moved");
        long lastLook = System.nanoTime() + BOUND.toNanos();
        while (System.nanoTime() < lastLook) {
            try {
                Files.move(folder, moved);
                Files.move(moved, folder);
                return;
            } catch (IOException stillInUse) {
                try {
                    Thread.sleep(POLL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
