package io.algernon.vespera.pipeline;

import java.io.Serial;
import java.nio.file.Path;

/**
 * Another invocation already holds the working directory, so this one is refused before it opens the
 * database file (ADR-177 §1, #364).
 *
 * <p>Its message is the whole of what the operator is shown, in one line: the directory, who holds it
 * as the holder wrote it into {@code vespera.lock}, and what to do. It names no record id, because the
 * operator reading it has no copy of the records.
 */
public class WorkingDirectoryInUseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Said in place of the holder's line when the lock file holds none that can be read. */
    private static final String HOLDER_UNREADABLE = "its details could not be read";

    private final Path workingDirectory;

    /**
     * @param workingDirectory the directory another invocation holds
     * @param holder the line the holder wrote into the lock file; blank or {@code null} when it could
     *     not be read, because the file is unreadable or the holder is between truncating it and
     *     writing it
     */
    public WorkingDirectoryInUseException(Path workingDirectory, String holder) {
        super("another Vespera invocation is already using the working directory " + workingDirectory + " ("
                + (holder == null || holder.isBlank() ? HOLDER_UNREADABLE : holder.strip())
                + "); wait for it to finish, or stop it, then run the same command again. Two invocations on one"
                + " working directory would write to the same database file at once.");
        this.workingDirectory = workingDirectory;
    }

    /** The directory that was refused. */
    public Path workingDirectory() {
        return workingDirectory;
    }
}
