package io.algernon.vespera.engine.deliverable;

import java.io.IOException;
import java.util.Optional;

/**
 * One cluster's membership entries as one plugin draws them, told what the HTML page written whole writes, as it
 * writes it (ADR-286 §2, ADR-333 §2): a survivor's pictures are asked for once for every rendering.
 */
public interface Entries extends AutoCloseable {

    /** Entries for nobody: what the HTML page's further pages and the tree drawn with no plugin are written with. */
    Entries NONE = new Entries() {
        @Override
        public void start(int number, Optional<ListedSurvivor> entry) {}

        @Override
        public void copyShown() {}

        @Override
        public void picture(String digest, boolean png, ListedPicture picture) {}

        @Override
        public void notShown(int notShown) {}

        @Override
        public void end() {}

        @Override
        public void close() {}
    };

    /** Opens entry {@code number}, counted from 1. */
    void start(int number, Optional<ListedSurvivor> entry) throws IOException;

    /** The entry's copy is a picture the HTML page shows inline. */
    void copyShown() throws IOException;

    /** A picture the HTML page shows. */
    void picture(String digest, boolean png, ListedPicture picture) throws IOException;

    /** The HTML page does not show {@code notShown} pictures of the entry. */
    void notShown(int notShown) throws IOException;

    /** Closes the entry. */
    void end() throws IOException;

    @Override
    void close() throws IOException;
}
