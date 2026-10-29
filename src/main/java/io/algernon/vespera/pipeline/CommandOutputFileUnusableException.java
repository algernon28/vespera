package io.algernon.vespera.pipeline;

/** A command's output file cannot be written or read (ADR-326, ADR-323 §8's ninth line). */
final class CommandOutputFileUnusableException extends SidecarStartRefusedException {

    CommandOutputFileUnusableException(CommandOutputFileException failed) {
        super("the sidecars are not answering and Vespera cannot start them: the temporary file the output of "
                + String.join(" ", failed.command()) + " goes to cannot be written or read ("
                + SidecarLockUnavailableException.reasonOf(failed.getCause()) + "). Make room in "
                + failed.folder().toAbsolutePath() + ", or make it writable, and run the same command again.");
        initCause(failed.getCause());
    }
}
