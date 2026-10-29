package io.algernon.vespera.pipeline;

import java.nio.file.Path;

/** The sidecar-start lock cannot be opened or locked (ADR-323 §8). */
final class SidecarLockUnavailableException extends SidecarStartRefusedException {

    SidecarLockUnavailableException(Path lockFile, Throwable reason) {
        super("the sidecars are not answering and Vespera cannot start them: the lock file "
                + lockFile.toAbsolutePath() + " cannot be opened (" + reasonOf(reason)
                + "). Make its folder writable, or name another with vespera.corpus-root-lock-folder, and run the"
                + " same command again.");
        initCause(reason);
    }

    /** The system's message for what refused, or the kind of refusal where it gave none. */
    static String reasonOf(Throwable reason) {
        String message = reason.getMessage();
        return message == null || message.isBlank() ? reason.getClass().getSimpleName() : message;
    }
}
