package io.algernon.controlpanel;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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

    /** Hands {@code address}, a document that is no report of the window, to the operating system to open (ADR-344 §6). */
    void handToTheSystem(URI address);

    /** Asks {@code sentence} with two answers; true where the operator gives {@code yes}, the one that goes on. */
    boolean confirm(String sentence, String yes, String no);

    /** Dialogs that open nothing and choose nothing: what a window drawn without a stage holds. */
    Dialogs NONE = new Dialogs() {
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

    /** The system's dialogs over {@code owner}, a document handed over by {@code showDocument} with its address. */
    static Dialogs of(Supplier<Window> owner, Consumer<String> showDocument) {
        return new Dialogs() {
            @Override
            public boolean confirm(String sentence, String yes, String no) {
                ButtonType goOn = new ButtonType(yes, ButtonBar.ButtonData.OK_DONE);
                ButtonType keep = new ButtonType(no, ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert ask = new Alert(Alert.AlertType.CONFIRMATION, sentence, goOn, keep);
                Window over = owner.get();
                if (over != null) {
                    ask.initOwner(over);
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
