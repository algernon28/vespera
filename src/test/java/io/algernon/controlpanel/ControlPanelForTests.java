package io.algernon.controlpanel;

import java.nio.file.Path;

/**
 * A control panel the tests start as a process of its own, serving as {@code ControlPanel.main} serves, through
 * {@link ControlPanel#serve} and so with its shutdown hook (ADR-307 §1), but on the jar for the screen tests and on
 * launch values of the test's own, as {@link ScreenServer} sets them, so that Run starts a child that sleeps.
 *
 * <p>Its arguments are a folder of the test's own, the corpus root and the working directory. It prints
 * {@link #OPEN} and the address to open, then serves until its process ends.
 */
public final class ControlPanelForTests {

    /** What the line naming the address begins with, as {@code ControlPanel.main} writes it (ADR-302 §11). */
    public static final String OPEN = "open ";

    private ControlPanelForTests() {
    }

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]);
        String userName = System.getProperty("user.name");
        ControlPanelServer server = ControlPanel.serve(ScreenServer.runtime(),
                ScreenServer.storedIn(folder, ScreenServer.values(Path.of(args[1]), Path.of(args[2])), userName),
                folder.resolve("root-locks"), userName);
        System.out.println(OPEN + server.firstAddress());
        System.out.flush();
        Thread.currentThread().join();
    }
}
