package io.algernon.controlpanel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

/**
 * A control panel's state the tests start in a process of its own, with the shutdown hook {@code ControlPanel}
 * registers as the window opens (ADR-307 §1, ADR-342 §2), and a {@code run} child of {@link SleepingJar} started, with
 * no window and no server: what ADR-307 holds of the JVM's end needs neither.
 *
 * <p>Its arguments are a folder of the test's own, the corpus root and the working directory. It prints
 * {@link #STARTED} once its child runs, then waits until its process ends.
 */
public final class AChildStartedForTests {

    /** The line printed once the child runs. */
    public static final String STARTED = "the child is started";

    private AChildStartedForTests() {
    }

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]);
        String userName = System.getProperty("user.name");
        Path launchValues = LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), userName);
        new LaunchValues(args[1], args[2], "", "", "", "", "", "").store(launchValues, userName, line -> {
            throw new IllegalStateException("the launch values were not stored: " + line);
        });
        ControlPanelState state = new ControlPanelState(SleepingJar.runtime(), launchValues,
                Files.createDirectories(folder.resolve("root-locks")), userName);
        ControlPanel.endChildWithThisJvm(state);
        Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
        if (refusal.isPresent()) {
            throw new IllegalStateException(refusal.get());
        }
        System.out.println(STARTED);
        System.out.flush();
        Thread.currentThread().join();
    }
}
