package io.algernon.vespera.publish.website;

import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Plugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The website rendering as a plugin (ADR-333 §1, §3): drawn by {@link WebsiteDrawing} under {@code website/} at the
 * tree's root by every drawing, and delivered by {@link WebsiteDelivery}, which refreshes {@code <working
 * directory>/website/} by digest. It reads no credential and opens no connection.
 */
@Component
public final class WebsitePlugin implements Plugin {

    /** The destination's name on {@code --to}, and the plugin's folder at the tree's root. */
    static final String NAME = "website";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String listName() {
        return ListedFile.LIST_FILE_NAME;
    }

    @Override
    public Drawing drawing(DrawingContext context) throws IOException {
        return new WebsiteDrawing(context.tree(), context.moves(), context.written(), context.language());
    }

    @Override
    public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
        return WebsiteDelivery.deliver(rendering, workingDirectory);
    }
}
