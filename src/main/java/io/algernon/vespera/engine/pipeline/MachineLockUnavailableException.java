package io.algernon.vespera.engine.pipeline;

import java.io.Serial;
import java.nio.file.Path;

/**
 * The machine lock could not be taken because its folder or a file of it cannot be created, opened or locked, so the
 * start is refused rather than run unguarded (ADR-394 §2, §5).
 */
public class MachineLockUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MachineLockUnavailableException(Path folder, Exception cause, MachineLock.Kind kind) {
        this(folder.toAbsolutePath().toString(), cause, kind);
    }

    /** For a folder named by a value that is no path, quoted as written. */
    MachineLockUnavailableException(String folder, Exception cause, MachineLock.Kind kind) {
        super("the machine lock could not be taken in the folder " + folder + " ("
                + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                + "); make that folder one this user can write, or name another in " + MachineLock.FOLDER_VARIABLE
                + ", then " + kind.then() + ". Vespera does not start while it cannot tell whether another Vespera is"
                + " running.", cause);
    }
}
