package io.algernon.vespera.ui;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * What the window asks of the system beyond JavaFX's own scene: a folder, a file and a document handed to the
 * system (ADR-342 §6, ADR-344 §6); HostServices is used for that hand-off alone. A test answers it without a native dialog; that {@code DirectoryChooser} and {@code FileChooser} open is
 * not held by any test.
 */
interface Dialogs {

    /** The folder the operator chose, empty where they chose none. */
    Optional<Path> folder(String title, String initial);

    /** The file the operator chose, empty where they chose none. */
    Optional<Path> file(String title, String initial);

    /**
     * Asks {@code sentence} in a field that does not show what is typed (ADR-359 §3): what is typed, empty where the
     * operator cancelled. Nothing is checked, kept or compared with it (ADR-359 §6).
     */
    Optional<String> masked(String title, String sentence);

    /**
     * Opens a warning, {@code sentence} over one field holding {@code initial} and the answers Publish and Cancel
     * (ADR-383 §3): what is typed, empty where the warning is cancelled.
     */
    Optional<String> warnAndAsk(String title, String sentence, String initial);

    /** Hands {@code address}, a document that is no report of the window, to the operating system to open (ADR-344 §6). */
    void handToTheSystem(URI address);

    /** Asks {@code sentence} with two answers; true where the operator gives {@code yes}, the one that goes on. */
    boolean confirm(String sentence, String yes, String no);

    /** Dialogs that open nothing and choose nothing: what a window drawn without a stage holds. */
    Dialogs NONE = new Dialogs() {
        @Override
        public Optional<String> masked(String title, String sentence) {
            return Optional.empty();
        }

        @Override
        public Optional<String> warnAndAsk(String title, String sentence, String initial) {
            return Optional.empty();
        }

        @Override
        public boolean confirm(String sentence, String yes, String no) {
            return false;
        }

        @Override
        public Optional<Path> folder(String title, String initial) {
            return Optional.empty();
        }

        @Override
        public Optional<Path> file(String title, String initial) {
            return Optional.empty();
        }

        @Override
        public void handToTheSystem(URI address) {
            // nothing to open it with
        }
    };

    /** Gives a dialog the window's stylesheet and, where the window is dark, the dark tokens: a dialog is a scene of its own. */
    private static void themed(javafx.scene.control.DialogPane pane, Window over) {
        if (over.getScene() == null || over.getScene().getRoot() == null) {
            return;
        }
        javafx.scene.Parent root = over.getScene().getRoot();
        pane.getStylesheets().addAll(root.getStylesheets());
        if (root.getStyleClass().contains("dark")) {
            pane.getStyleClass().add("dark-dialog");
        }
    }

    /** The system's dialogs over {@code owner}, a document handed over by {@code showDocument} with its address. */
    static Dialogs of(Supplier<Window> owner, Consumer<String> showDocument) {
        return new Dialogs() {
            @Override
            public Optional<String> masked(String title, String sentence) {
                ButtonType goOn = new ButtonType("Publish", ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Dialog<String> ask = new Dialog<>();
                ask.setTitle(title);
                PasswordField field = new PasswordField();
                field.setId("masked-field");
                Label words = new Label(sentence);
                words.setWrapText(true);
                ask.getDialogPane().setContent(new VBox(8, words, field));
                ask.getDialogPane().getButtonTypes().setAll(goOn, keep);
                ask.setResultConverter(pressed -> pressed == goOn ? field.getText() : null);
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
                    themed(ask.getDialogPane(), over);
                }
                return ask.showAndWait();
            }

            @Override
            public Optional<String> warnAndAsk(String title, String sentence, String initial) {
                ButtonType goOn = new ButtonType("Publish", ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert ask = new Alert(Alert.AlertType.WARNING, "", goOn, keep);
                ask.setTitle(title);
                ask.setHeaderText(null);
                TextField field = new TextField(initial == null ? "" : initial);
                field.setId("warning-field");
                Label words = new Label(sentence);
                words.setWrapText(true);
                ask.getDialogPane().setContent(new VBox(8, words, field));
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
                    themed(ask.getDialogPane(), over);
                }
                return ask.showAndWait().filter(answer -> answer == goOn).map(answer -> field.getText());
            }

            @Override
            public boolean confirm(String sentence, String yes, String no) {
                ButtonType goOn = new ButtonType(yes, ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType(no, ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert ask = new Alert(Alert.AlertType.CONFIRMATION, sentence, goOn, keep);
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
                    themed(ask.getDialogPane(), over);
                }
                return ask.showAndWait().filter(answer -> answer == goOn).isPresent();
            }

            @Override
            public Optional<Path> folder(String title, String initial) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle(title);
                startAt(initial).ifPresent(chooser::setInitialDirectory);
                return Optional.ofNullable(chooser.showDialog(owner.get())).map(File::toPath);
            }

            @Override
            public Optional<Path> file(String title, String initial) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle(title);
                startAt(initial).ifPresent(chooser::setInitialDirectory);
                return Optional.ofNullable(chooser.showOpenDialog(owner.get())).map(File::toPath);
            }

            @Override
            public void handToTheSystem(URI address) {
                showDocument.accept(address.toString());
            }
        };
    }

    private static Optional<File> startAt(String initial) {
        try {
            File folder = initial == null || initial.isBlank() ? null : Path.of(initial.strip()).toFile();
            return folder != null && folder.isDirectory() ? Optional.of(folder) : Optional.empty();
        } catch (RuntimeException cannotBeHeld) {
            return Optional.empty();
        }
    }
}
