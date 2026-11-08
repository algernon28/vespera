package io.algernon.vespera.ui;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** The two boxes the cockpit's parts are laid out in, each given only its style class (ADR-344 §1). */
final class Layout {

    private Layout() {
    }

    /** {@code children} side by side in a {@code spaced-row}. */
    static HBox row(Node... children) {
        HBox box = new HBox(children);
        box.getStyleClass().add("spaced-row");
        return box;
    }

    /** {@code children} one above another in a {@code tight-column}. */
    static VBox tight(Node... children) {
        VBox box = new VBox(children);
        box.getStyleClass().add("tight-column");
        return box;
    }
}
