package io.algernon.vespera.ui;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
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
     * Asks {@code sentence} under {@code title} in a field that does not show what is typed, with the answers {@code yes}
     * and {@code no} (ADR-444 §8): what is typed as characters the caller clears, empty where the operator answered
     * {@code no} or closed the dialog. The field is cleared once the dialog closes.
     */
    Optional<char[]> secret(String title, String sentence, String yes, String no);

    /**
     * Opens a warning, {@code sentence} over one field holding {@code initial} and the answers Publish and Cancel
     * (ADR-383 §3): what is typed, empty where the warning is cancelled.
     */
    Optional<String> warnAndAsk(String title, String sentence, String initial);

    /**
     * Shows New…'s one field, holding {@code proposed}, which the operator may edit, with Browse for its parent, OK and
     * Cancel (ADR-393 §6): the folder written in it, empty where cancelled. A test answers it without a native dialog.
     */
    default Optional<Path> newWorkingDirectory(Path proposed) {
        return Optional.empty();
    }

    /** Hands {@code address}, a document that is no report of the window, to the operating system to open (ADR-344 §6). */
    void handToTheSystem(URI address);

    /**
     * Asks {@code sentence} under {@code title}, with no header or icon, and two answers (ADR-423 §1); true where the
     * operator gives {@code yes}, the one that goes on and ends something.
     */
    boolean confirm(String title, String sentence, String yes, String no);

    /**
     * Asks {@code sentence} under {@code title} with one button for each of {@code answers}, in their order (ADR-389 §4):
     * the words of the answer pressed, empty where the dialog is closed with none.
     */
    Optional<String> choose(String title, String sentence, List<String> answers);

    /** These dialogs asked over the window {@code owner} gives; dialogs with no window of their own stay as they are. */
    default Dialogs over(Supplier<Window> owner) {
        return this;
    }

    /** Dialogs that open nothing and choose nothing: what a window drawn without a stage holds. */
    Dialogs NONE = new Dialogs() {
        @Override
        public Optional<String> masked(String title, String sentence) {
            return Optional.empty();
        }

        @Override
        public Optional<char[]> secret(String title, String sentence, String yes, String no) {
            return Optional.empty();
        }

        @Override
        public Optional<String> warnAndAsk(String title, String sentence, String initial) {
            return Optional.empty();
        }

        @Override
        public boolean confirm(String title, String sentence, String yes, String no) {
            return false;
        }

        @Override
        public Optional<String> choose(String title, String sentence, List<String> answers) {
            return Optional.empty();
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
            public Dialogs over(Supplier<Window> other) {
                return of(other, showDocument);
            }

            @Override
            public Optional<String> choose(String title, String sentence, List<String> answers) {
                List<ButtonType> buttons = new ArrayList<>();
                for (int at = 0; at < answers.size(); at++) {
                    ButtonBar.ButtonData data = at == answers.size() - 1 ? ButtonBar.ButtonData.CANCEL_CLOSE
                            : at == 0 ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.OTHER;
                    buttons.add(new ButtonType(answers.get(at), data));
                }
                Dialog<ButtonType> ask = new Dialog<>();
                ask.setDialogPane(new DialogPane() {
                    @Override
                    protected Node createButtonBar() {
                        ButtonBar bar = (ButtonBar) super.createButtonBar();
                        bar.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
                        return bar;
                    }
                });
                ask.setTitle(title);
                Label words = new Label(sentence);
                words.setWrapText(true);
                ask.getDialogPane().setContent(words);
                ask.getDialogPane().getButtonTypes().setAll(buttons);
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
                    themed(ask.getDialogPane(), over);
                }
                // JavaFX answers a close, and Escape through the cancel button, with the CANCEL_CLOSE button's type: only a
                // button really pressed is an answer (ADR-389 section 4)
                String[] pressed = new String[1];
                boolean[] escaping = new boolean[1];
                // on the scene, so that a key with no focus owner, which targets the scene, is seen too
                ask.getDialogPane().getScene().addEventFilter(KeyEvent.ANY, key -> {
                    if (key.getCode() == KeyCode.ESCAPE) {
                        escaping[0] = key.getEventType() == KeyEvent.KEY_PRESSED;
                    }
                });
                for (ButtonType type : buttons) {
                    Node button = ask.getDialogPane().lookupButton(type);
                    int place = buttons.indexOf(type);
                    if (place == 0) {
                        button.getStyleClass().add("answer-that-keeps");
                    } else if (place == 1) {
                        button.getStyleClass().add("answer-that-ends");
                    }
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        if (!escaping[0]) {
                            pressed[0] = type.getText();
                        }
                    });
                }
                ask.showAndWait();
                return Optional.ofNullable(pressed[0]);
            }

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
            public Optional<char[]> secret(String title, String sentence, String yes, String no) {
                ButtonType goOn = new ButtonType(yes, ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType(no, ButtonBar.ButtonData.CANCEL_CLOSE);
                Dialog<char[]> ask = new Dialog<>();
                ask.setTitle(title);
                PasswordField field = new PasswordField();
                field.setId("secret-field");
                Label words = new Label(sentence);
                words.setWrapText(true);
                ask.getDialogPane().setContent(new VBox(8, words, field));
                ask.getDialogPane().getButtonTypes().setAll(goOn, keep);
                ask.setResultConverter(pressed -> pressed == goOn ? field.getText().toCharArray() : null);
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
                    themed(ask.getDialogPane(), over);
                }
                try {
                    return ask.showAndWait();
                } finally {
                    field.clear();
                }
            }

            @Override
            public Optional<Path> newWorkingDirectory(Path proposed) {
                ButtonType goOn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Dialog<Path> ask = new Dialog<>();
                ask.setTitle("New working directory");
                TextField field = new TextField(proposed.toString());
                field.setId("working-directory-new-field");
                javafx.scene.control.Button browse = new javafx.scene.control.Button("Browse");
                browse.setId("working-directory-new-browse");
                browse.setOnAction(event -> {
                    Path named = Path.of(field.getText().strip());
                    Path parent = named.getParent();
                    folder("Choose the folder the working directory is made in", parent == null ? "" : parent.toString())
                            .ifPresent(chosen -> field.setText(chosen.resolve(named.getFileName()).toString()));
                });
                Label words = new Label("The folder of the new working directory. Its name is the corpus root's folder"
                        + " name and the day; edit it as you like.");
                words.setWrapText(true);
                javafx.scene.layout.HBox line = new javafx.scene.layout.HBox(8, field, browse);
                javafx.scene.layout.HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
                ask.getDialogPane().setContent(new VBox(8, words, line));
                ask.getDialogPane().getButtonTypes().setAll(goOn, keep);
                ask.setResultConverter(pressed -> {
                    if (pressed != goOn || field.getText().isBlank()) {
                        return null;
                    }
                    try {
                        return Path.of(field.getText().strip()).toAbsolutePath().normalize();
                    } catch (RuntimeException cannotBeHeld) {
                        return null;
                    }
                });
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
            public boolean confirm(String title, String sentence, String yes, String no) {
                ButtonType goOn = new ButtonType(yes, ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType(no, ButtonBar.ButtonData.CANCEL_CLOSE);
                Dialog<ButtonType> ask = new Dialog<>();
                ask.setTitle(title);
                Label words = new Label(sentence);
                words.setWrapText(true);
                ask.getDialogPane().setContent(words);
                ask.getDialogPane().getButtonTypes().setAll(goOn, keep);
                ask.getDialogPane().lookupButton(goOn).getStyleClass().add("answer-that-ends");
                ask.getDialogPane().lookupButton(keep).getStyleClass().add("answer-that-keeps");
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
