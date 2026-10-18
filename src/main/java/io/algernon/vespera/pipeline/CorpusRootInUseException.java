package io.algernon.vespera.pipeline;

import java.io.Serial;
import java.nio.file.Path;

/**
 * Another invocation already walks the corpus root, so this one is refused before it writes a progress page
 * or starts a walk (ADR-278 §6).
 *
 * <p>Its message is the whole of what the operator is shown, in one line: the root as this invocation names
 * it, who holds it as the holder wrote it into the lock file, and what to do.
 */
public class CorpusRootInUseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Said in place of the holder's line when the lock file holds none that can be read. */
    private static final String HOLDER_UNREADABLE = "its details could not be read";

    /**
     * @param corpusRoot the root as this invocation names it
     * @param holder the line the holder wrote; blank or {@code null} when it could not be read
     */
    public CorpusRootInUseException(Path corpusRoot, String holder) {
        super("another Vespera invocation is already walking the corpus root " + corpusRoot + " ("
                + (holder == null || holder.isBlank() ? HOLDER_UNREADABLE : holder.strip())
                + "); wait for it to finish, or stop it, then run the same command again. Two invocations on one"
                + " corpus root would convert and judge one corpus twice at once, into two ledgers.");
    }
}
