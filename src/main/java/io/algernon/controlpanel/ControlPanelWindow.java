package io.algernon.controlpanel;

import java.net.URL;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The control panel's window (ADR-342 §2, ADR-344 §1): the one class of the control panel that extends
 * {@link Application}, launched by {@link ControlPanel#open()}, opening at 1500 by 960. Its root is drawn by
 * {@link #drawn(ControlPanelState)} from the state it is given, which owns every rule; the window reads no environment and
 * no file of configuration of its own. The one stylesheet, a resource of this package, owns every colour, size and space
 * (ADR-342 §3).
 */
public final class ControlPanelWindow extends Application {

    /** The window's size as it opens. */
    static final double WIDTH = 1500;

    static final double HEIGHT = 960;

    private static final String STYLESHEET = "control-panel.css";

    /** The state the window is launched with: {@code Application.launch} makes the instance itself. */
    private static volatile ControlPanelState opening;

    /** Hands the state the next launch draws. */
    static void launchedWith(ControlPanelState state) {
        opening = state;
    }

    /** The window's root drawn from {@code state}, its folder dialogs and its reports opened by nothing: for a test, and the base of {@link #start}. */
    public static Parent drawn(ControlPanelState state) {
        return drawn(state, Dialogs.NONE).root();
    }

    private static Cockpit drawn(ControlPanelState state, Dialogs dialogs) {
        Cockpit cockpit = new Cockpit(state, dialogs);
        URL stylesheet = ControlPanelWindow.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            cockpit.root().getStylesheets().add(stylesheet.toExternalForm());
        }
        return cockpit;
    }

    @Override
    public void start(Stage stage) {
        shownOn(stage, opening, Dialogs.of(() -> stage.getScene() == null ? null : stage.getScene().getWindow(),
                uri -> getHostServices().showDocument(uri)));
    }

    /** Draws {@code state} on {@code stage}, asks through {@code dialogs} and shows it: what {@link #start} does, for a test to do too. */
    static void shownOn(Stage stage, ControlPanelState state, Dialogs dialogs) {
        Cockpit cockpit = drawn(state, dialogs);
        stage.setTitle("Vespera control panel");
        stage.setScene(new Scene(cockpit.root(), WIDTH, HEIGHT));
        stage.setOnCloseRequest(event -> {
            if (cockpit.childRuns() && !dialogs.confirm(Child.STOP_CONFIRMATION, "Stop and close", "Keep the window")) {
                event.consume();
            }
        });
        stage.setOnHidden(event -> cockpit.closed());
        stage.show();
    }
}
