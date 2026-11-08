package io.algernon.vespera.publish.confluence;

import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Plugin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The Confluence rendering as a plugin (ADR-333 §1): drawn by {@link ConfluencePages} under {@code confluence/} at the
 * tree's root by every drawing, and delivered by {@link Publisher}, whose signature does not change and whose outcome
 * is handed back as it came.
 */
@Component
public final class ConfluencePlugin implements Plugin {

    private final Publisher publisher;

    public ConfluencePlugin(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String name() {
        return Publisher.RENDERING_FOLDER;
    }

    @Override
    public String listName() {
        return Publisher.PAGE_LIST_FILE_NAME;
    }

    @Override
    public Drawing drawing(DrawingContext context) throws IOException {
        return new ConfluencePages(
                context.tree(), context.generationRun(), context.moves(), context.written(), context.language());
    }

    @Override
    public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
        Publisher.Outcome outcome = publisher.publish(rendering, generationRun, workingDirectory, out);
        return new Delivery(outcome.published(), outcome.closingLine());
    }
}
