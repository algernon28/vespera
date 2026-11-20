package io.algernon.vespera.engine.pipeline;

import java.nio.file.Path;

/** The container-start lock cannot be opened or locked (ADR-323 §8). */
final class ContainerLockUnavailableException extends ContainerStartRefusedException {

    private final String unusable;

    ContainerLockUnavailableException(Path lockFile, Throwable reason) {
        super("the containers are not answering and Vespera cannot start them: the lock file "
                + lockFile.toAbsolutePath() + " cannot be opened (" + reasonOf(reason)
                + "). Make its folder writable, or name another with vespera.corpus-root-lock-folder, and run the"
                + " same command again.");
        initCause(reason);
        this.unusable = "the lock file " + lockFile.toAbsolutePath() + " cannot be opened (" + reasonOf(reason) + ")";
    }

    /** What the lock's refusal says of the lock, for the lines of the stops (ADR-390 §6). */
    String unusable() {
        return unusable;
    }

    /** The system's message for what refused, or the kind of refusal where it gave none. */
    static String reasonOf(Throwable reason) {
        String message = reason.getMessage();
        return message == null || message.isBlank() ? reason.getClass().getSimpleName() : message;
    }
}
