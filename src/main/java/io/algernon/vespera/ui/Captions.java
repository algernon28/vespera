package io.algernon.vespera.ui;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;

/**
 * The window's explanations, each a caption on mouse over and never text in the window (ADR-430 §8). A caption is a
 * {@link Tooltip} installed on a node; a control that can be disabled stands on a wrapper that carries it, as a disabled
 * control shows none. How wide a caption is drawn is the stylesheet's.
 */
final class Captions {

    private Captions() {
    }

    /** Installs {@code words} as the caption of {@code node} and answers the tooltip, whose text a later reading changes. */
    static Tooltip on(Node node, String words) {
        Tooltip caption = new Tooltip(words);
        caption.setWrapText(true);
        Tooltip.install(node, caption);
        return caption;
    }
}
