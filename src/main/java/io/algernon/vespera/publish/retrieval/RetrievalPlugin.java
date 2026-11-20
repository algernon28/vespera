package io.algernon.vespera.publish.retrieval;

import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.deliverable.RetrievalRecords;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The retrieval records as a plugin (ADR-434 §8): drawn into {@code retrieval/records.jsonl} by {@link RecordsFile},
 * one line a record, where the profile's {@code outputs} names {@code retrieval}. It draws from no tree, takes member
 * passages, and its delivery sends nothing.
 */
@Component
public final class RetrievalPlugin implements Plugin {

    /** The destination's name on {@code --to}, and the plugin's folder at the tree's root. */
    static final String NAME = "retrieval";

    /** The file of the plugin's folder, the rendering a publish finds. */
    static final String LIST_NAME = "records.jsonl";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String listName() {
        return LIST_NAME;
    }

    @Override
    public boolean drawsFromTheTree() {
        return false;
    }

    @Override
    public Drawing drawing(DrawingContext context) {
        throw new UnsupportedOperationException("the retrieval plugin draws from no tree");
    }

    @Override
    public Optional<RetrievalRecords> records(DrawingContext context) throws IOException {
        Path folder = context.tree().resolve(NAME);
        return Optional.of(new RecordsFile(folder.resolve(LIST_NAME), context.moves()));
    }

    @Override
    public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
        return new Delivery(
                true,
                "vespera publish sends nothing to " + NAME + ": its records are at " + rendering.resolve(LIST_NAME)
                        + ", a line each with its vector, for a store of yours to import.");
    }
}
