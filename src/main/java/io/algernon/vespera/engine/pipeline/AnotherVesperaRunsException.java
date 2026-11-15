package io.algernon.vespera.engine.pipeline;

import java.io.Serial;

/**
 * Another Vespera holds the machine, so this start is refused before it opens a working directory or a window
 * (ADR-391 §5). Its message is the whole of what the operator is shown, in one line.
 */
public class AnotherVesperaRunsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Said in place of the holder's line when it is blank or cannot be read. */
    private static final String HOLDER_UNREADABLE = "its details could not be read";

    /**
     * @param holder the line of {@code machine.lock}, or of {@code machine-child.lock} where that one is held; blank or
     *               {@code null} when it could not be read
     * @param kind   what is refused, which says what it is told to do next
     */
    public AnotherVesperaRunsException(String holder, MachineLock.Kind kind) {
        super("another Vespera is running on this machine ("
                + (holder == null || holder.isBlank() ? HOLDER_UNREADABLE : holder.strip())
                + "); wait for it to end, or stop it or close its control panel, then " + kind.then() + ". One Vespera"
                + " runs on a machine at a time, so that none stops the sidecars another is using.");
    }
}
