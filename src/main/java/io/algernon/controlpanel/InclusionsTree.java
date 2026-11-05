package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;

/**
 * The folders to include, as a tree of the corpus root's folders each with a box (ADR-344 §1, ADR-334 §6): every one
 * ticked at first, an unticked folder standing unticked with every folder below it, and a folder with one below it
 * unticked half-ticked. A folder's subfolders are listed when it is first opened and never before, so a corpus root is
 * never walked to draw it, and no file's name is listed. What is written is the highest unticked folder of each branch,
 * each as occurrences' paths spell one (relative to the corpus root, {@code /} between its elements).
 */
final class InclusionsTree {

    private static final boolean NAMES_IGNORE_CASE =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** Set while the tree moves boxes itself, so that a box it moves is not read as the operator's tick. */
    private static boolean adjusting;

    /**
     * A folder of the tree, whose subfolders are read the first time it is opened. Each item is independent, because
     * JavaFX's dependent mode marks a parent unticked once every child is, and that would write the parent: a tick or
     * untick here is copied down to the folders loaded below by this class, and each folder above is half-ticked while
     * any below it is unticked, and never unticked because of what is below it.
     */
    private static final class Folder extends CheckBoxTreeItem<String> {

        private final Path path;
        private final String relative;
        private boolean loaded;

        Folder(Path path, String relative, boolean selected) {
            super(path.getFileName() == null ? path.toString() : path.getFileName().toString());
            this.path = path;
            this.relative = relative;
            setIndependent(true);
            setSelected(selected);
            selectedProperty().addListener((observed, was, now) -> ticked(now));
        }

        private void ticked(boolean now) {
            if (adjusting) {
                return;
            }
            adjusting = true;
            try {
                setIndeterminate(false);
                copyDown(this, now);
                for (TreeItem<String> up = getParent(); up instanceof Folder above; up = up.getParent()) {
                    if (!now) {
                        above.setIndeterminate(true);
                    } else {
                        boolean oneBelowUnticked = above.getChildren().stream()
                                .map(child -> (Folder) child).anyMatch(child -> !child.isSelected() || child.isIndeterminate());
                        above.setSelected(true);
                        above.setIndeterminate(oneBelowUnticked);
                    }
                }
            } finally {
                adjusting = false;
            }
        }

        private static void copyDown(Folder from, boolean now) {
            for (TreeItem<String> child : from.getChildren()) {
                Folder folder = (Folder) child;
                folder.setSelected(now);
                folder.setIndeterminate(false);
                copyDown(folder, now);
            }
        }

        @Override
        public boolean isLeaf() {
            return loaded && super.isLeaf();
        }

        /** Lists the subfolders once, each ticked as this folder is. */
        void load() {
            if (loaded) {
                return;
            }
            loaded = true;
            List<Folder> found = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path, Files::isDirectory)) {
                for (Path entry : entries) {
                    found.add(new Folder(entry, relative.isEmpty() ? entry.getFileName().toString()
                            : relative + "/" + entry.getFileName(), isSelected() && !isIndeterminate()));
                }
            } catch (IOException | RuntimeException unreadable) {
                // a folder that cannot be listed has none to show
            }
            found.sort((a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
            getChildren().setAll(found);
        }
    }

    private final TreeView<String> view = new TreeView<>();
    private Folder root;
    private List<String> unresolved = List.of();

    InclusionsTree() {
        view.setShowRoot(false);
        view.setCellFactory(CheckBoxTreeCell.forTreeView());
    }

    TreeView<String> view() {
        return view;
    }

    /**
     * Draws the tree of {@code corpusRoot} with each folder of {@code notRelevant} unticked. A folder listed that is no
     * longer there is kept for the next save as it stands.
     */
    void draw(Optional<Path> corpusRoot, List<String> notRelevant) {
        root = null;
        unresolved = List.of();
        view.setRoot(null);
        if (corpusRoot.isEmpty() || !Files.isDirectory(corpusRoot.get())) {
            return;
        }
        root = new Folder(corpusRoot.get(), "", true);
        root.load();
        root.setExpanded(true);
        List<String> missing = new ArrayList<>();
        for (String listed : notRelevant) {
            if (!untick(root, listed.split("/"), 0)) {
                missing.add(listed);
            }
        }
        unresolved = missing;
        watch(root);
        view.setRoot(root);
    }

    /** Opens each folder the first time the operator expands it. */
    private void watch(Folder folder) {
        folder.expandedProperty().addListener((observed, was, now) -> {
            if (now) {
                folder.load();
                folder.getChildren().forEach(child -> watch((Folder) child));
            }
        });
        folder.getChildren().forEach(child -> watch((Folder) child));
    }

    /** Loads down the elements of a listed folder, only along its own path, and unticks it; false where it is not there. */
    private static boolean untick(Folder from, String[] elements, int at) {
        from.load();
        for (TreeItem<String> child : from.getChildren()) {
            String name = child.getValue();
            if (NAMES_IGNORE_CASE ? name.equalsIgnoreCase(elements[at]) : name.equals(elements[at])) {
                Folder next = (Folder) child;
                if (at == elements.length - 1) {
                    next.setSelected(false);
                    return true;
                }
                return untick(next, elements, at + 1);
            }
        }
        return false;
    }

    /** The highest unticked folder of each branch, then each listed folder that is no longer there. */
    List<String> notRelevant() {
        List<String> folders = new ArrayList<>();
        if (root != null) {
            collect(root, folders);
        }
        folders.addAll(unresolved);
        return folders;
    }

    private static void collect(Folder from, List<String> into) {
        for (TreeItem<String> child : from.getChildren()) {
            Folder folder = (Folder) child;
            if (folder.isIndeterminate()) {
                collect(folder, into);
            } else if (!folder.isSelected()) {
                into.add(folder.relative);
            }
        }
    }

    /** Whether the tree holds a folder, so that the operator has something to untick. */
    boolean isDrawn() {
        return root != null;
    }

    /** Whether {@code spelling} can be held by a path the system reads (a guard for the corpus root field). */
    static Optional<Path> pathOf(String spelling) {
        try {
            return spelling.isBlank() ? Optional.empty() : Optional.of(Path.of(spelling.stripTrailing()));
        } catch (InvalidPathException cannotBeHeld) {
            return Optional.empty();
        }
    }
}
