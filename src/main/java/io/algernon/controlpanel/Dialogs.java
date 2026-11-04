package io.algernon.controlpanel;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * What the window asks of the system beyond JavaFX's own scene: a folder, a file and a report opened (ADR-339 §6,
 * ADR-341 §6). A test answers it without a native dialog; that {@code DirectoryChooser} and {@code FileChooser} open is
 * not held by any test.
 */
interface Dialogs {

    /** The folder the operator chose, empty where they chose none. */
    Optional<Path> folder(String title, String initial);

    /** The file the operator chose, empty where they chose none. */
    Optional<Path> file(String title, String initial);

    /** Opens the report in the system's default browser. */
    void open(Path report);

    /** Dialogs that open nothing and choose nothing: what a window drawn without a stage holds. */
    Dialogs NONE = new Dialogs() {
        @Override
        public Optional<Path> folder(String title, String initial) {
            return Optional.empty();
        }

        @Override
        public Optional<Path> file(String title, String initial) {
            return Optional.empty();
        }

        @Override
        public void open(Path report) {
            // nothing to open it with
        }
    };

    /** The system's dialogs over {@code owner}, a report opened by {@code showDocument} with its file's URI. */
    static Dialogs of(Supplier<Window> owner, Consumer<String> showDocument) {
        return new Dialogs() {
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
            public void open(Path report) {
                showDocument.accept(report.toUri().toString());
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
