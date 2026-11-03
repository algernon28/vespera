package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A plugin inside the jar: it draws one rendering beside the HTML tree at every drawing and delivers it to one
 * destination (ADR-333 §1, §2). Each is a Spring bean; {@code deliverable} names none, and {@code pipeline} hands
 * {@link Deliverable#writeTo} the beans.
 *
 * <p>A plugin writes only beneath its own folder at the tree's root, and adds each folder it writes to {@link
 * DrawingContext#written()} (ADR-312 §4). {@code Deliverable} writes the state of its drawing, {@code drawing.txt},
 * in that folder (ADR-333 §11); no plugin does.
 */
public interface Plugin {

    /** The file of a plugin's folder in which {@code Deliverable} writes the state of that plugin's drawing (ADR-333 §11). */
    String DRAWING_STATE_FILE_NAME = "drawing.txt";

    /** What that file says of a drawing that went to its end; anything else is a drawing that did not (ADR-333 §11). */
    String DRAWN = "drawn";

    /** The destination's name on {@code --to}, and the plugin's folder at the tree's root: lowercase letters a to z alone. */
    String name();

    /** The file of its rendering, in that folder, a publish finds the rendering by: pages.json, files.json. */
    String listName();

    /** Opens this plugin's drawing of the tree being drawn. */
    Drawing drawing(DrawingContext context) throws IOException;

    /** Delivers the rendering in {@code rendering} of {@code generationRun} to this plugin's destination. */
    Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out);

    /** How a delivery ended: whether it went to its end, and the one closing line saying so. */
    record Delivery(boolean delivered, String closingLine) {}

    /** What a drawing is opened on: the tree, its generation run, how to move a file into place, and the language. */
    record DrawingContext(
            Path tree, String generationRun, ReplacingMove moves, Set<String> written, DeliverableLanguage language) {}
}
