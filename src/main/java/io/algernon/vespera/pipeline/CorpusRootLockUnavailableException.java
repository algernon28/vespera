package io.algernon.vespera.pipeline;

import java.io.Serial;
import java.nio.file.Path;

/**
 * The corpus-root lock could not be taken because its folder or file cannot be created or opened, so the
 * invocation is refused rather than walking unlocked (ADR-279 §4).
 */
public class CorpusRootLockUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CorpusRootLockUnavailableException(Path folder, Exception cause) {
        super("the corpus-root lock could not be taken in the folder " + folder + " ("
                + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                + "); make that folder one this user can write, or name another in "
                + CorpusRootLock.FOLDER_PROPERTY + ", then run the same command again. vespera run walks no"
                + " corpus root it has not locked.", cause);
    }
}
