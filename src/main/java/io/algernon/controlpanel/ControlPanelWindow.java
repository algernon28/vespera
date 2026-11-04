package io.algernon.controlpanel;

import java.net.URL;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * The control panel's window (ADR-339 §2, ADR-341 §1): the one class of the control panel that extends
 * {@link Application}, launched by {@link ControlPanel#open()}, opening at 1500 by 960. Its root is drawn by
 * {@link #drawn(ControlPanelState)} from the state it is given, which owns every rule; the window reads no environment and
 * no file of configuration of its own. The one stylesheet, a resource of this package, owns every colour, size and space
 * (ADR-339 §3).
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
        ControlPanelState state = opening;
        Dialogs dialogs = Dialogs.of(() -> stage.getScene() == null ? null : stage.getScene().getWindow(),
                uri -> getHostServices().showDocument(uri));
        Cockpit cockpit = drawn(state, dialogs);
        stage.setTitle("Vespera control panel");
        stage.setScene(new Scene(cockpit.root(), WIDTH, HEIGHT));
        stage.setOnCloseRequest(event -> {
            if (cockpit.childRuns() && !stopAndClose()) {
                event.consume();
            }
        });
        stage.setOnHidden(event -> cockpit.closed());
        stage.show();
    }

    /** ADR-341 §7: asks once, with two answers; true where the operator answers Stop and close. */
    private static boolean stopAndClose() {
        ButtonType stopAndClose = new ButtonType("Stop and close", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep the window", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
                "The invocation the control panel started is still running. Closing the window ends it.", stopAndClose,
                keep);
        return ask.showAndWait().filter(answer -> answer == stopAndClose).isPresent();
    }
}
