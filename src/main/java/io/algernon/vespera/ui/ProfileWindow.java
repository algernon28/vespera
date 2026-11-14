package io.algernon.vespera.ui;

import java.util.List;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * The one Profile window of a cockpit (ADR-388 §2): a stage of its own, with no owner and no modality, whose scene's root
 * holds the cockpit's profile form. It opens or brings to the front the one stage, keeps its size and place for the
 * cockpit's next opening of it and nowhere else (§3), asks Save, Discard or Cancel when it is closed with an edit unsaved
 * (§4), carries the badge as its title (§5) and follows the cockpit's stylesheet and {@code dark} style class (§7).
 */
final class ProfileWindow {

    private static final String TITLE = "Profile";
    private static final String QUESTION_TITLE = "The profile";
    private static final String QUESTION = "The profile has changes not saved in profile.yaml.";
    private static final String SAVE = "Save";
    private static final String DISCARD = "Discard";
    private static final String CANCEL = "Cancel";

    /** Its first size, at which the whole form is readable. */
    private static final double FIRST_WIDTH = 780;
    private static final double FIRST_HEIGHT = 760;

    private record Placed(double x, double y, double width, double height) {
    }

    private final ProfileForm form;
    private final BorderPane cockpitRoot;
    private final Dialogs dialogs;
    private final BorderPane root = new BorderPane();
    private Stage stage;
    private Dialogs overIt;
    private Placed kept;
    private String title = TITLE;

    ProfileWindow(ProfileForm form, BorderPane cockpitRoot, Dialogs dialogs) {
        this.form = form;
        this.cockpitRoot = cockpitRoot;
        this.dialogs = dialogs;
        root.getStyleClass().add("cockpit");
        follow();
        cockpitRoot.getStyleClass().addListener((ListChangeListener<String>) change -> follow());
    }

    /** Takes the cockpit's stylesheet and, as it has it or not, the {@code dark} style class. */
    private void follow() {
        root.getStylesheets().setAll(cockpitRoot.getStylesheets());
        boolean dark = cockpitRoot.getStyleClass().contains("dark");
        root.getStyleClass().remove("dark");
        if (dark) {
            root.getStyleClass().add("dark");
        }
    }

    /** Opens the window, or shows the one open again where it is minimized, brings it to the front and gives it the focus. */
    void open() {
        if (stage == null) {
            stage = new Stage();
            root.setCenter(form.content());
            stage.setScene(new Scene(root, FIRST_WIDTH, FIRST_HEIGHT));
            stage.setResizable(true);
            overIt = dialogs.over(() -> stage);
            stage.setOnCloseRequest(event -> {
                if (!mayClose(overIt)) {
                    event.consume();
                }
            });
            stage.setOnHiding(event -> kept = new Placed(stage.getX(), stage.getY(), stage.getWidth(),
                    stage.getHeight()));
        }
        follow();
        stage.setTitle(title);
        if (!stage.isShowing() && kept != null) {
            stage.setX(kept.x());
            stage.setY(kept.y());
            stage.setWidth(kept.width());
            stage.setHeight(kept.height());
        }
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    /** The window's title, which follows the section's badge while it is open. */
    void titled(String words) {
        title = words;
        if (stage != null) {
            stage.setTitle(words);
        }
    }

    boolean isOpen() {
        return stage != null && stage.isShowing();
    }

    /**
     * Whether the window may close now, asked through {@code asking} (ADR-388 §4): at once with no edit unsaved; with
     * one, after Save that writes or Discard, and not after Cancel, no answer, or a Save refused.
     */
    boolean mayClose(Dialogs asking) {
        if (!isOpen() || !form.unsaved()) {
            return true;
        }
        String answer = asking.choose(QUESTION_TITLE, QUESTION, List.of(SAVE, DISCARD, CANCEL)).orElse(CANCEL);
        if (answer.equals(SAVE)) {
            return form.saveNow().written();
        }
        if (answer.equals(DISCARD)) {
            form.discardNow();
            return true;
        }
        return false;
    }

    /** Closes the window asking nothing, as the cockpit's closing does. */
    void close() {
        if (stage != null) {
            stage.hide();
        }
    }
}
