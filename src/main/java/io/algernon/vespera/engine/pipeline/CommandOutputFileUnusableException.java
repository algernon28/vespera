package io.algernon.vespera.engine.pipeline;

/** A command's output file cannot be written or read (ADR-326, ADR-323 §8's ninth line). */
final class CommandOutputFileUnusableException extends SidecarStartRefusedException {

    private final String unusable;

    CommandOutputFileUnusableException(CommandOutputFileException failed) {
        super("the sidecars are not answering and Vespera cannot start them: the temporary file the output of "
                + String.join(" ", failed.command()) + " goes to cannot be written or read ("
                + SidecarLockUnavailableException.reasonOf(failed.getCause()) + "). Make room in "
                + failed.folder().toAbsolutePath() + ", or make it writable, and run the same command again.");
        initCause(failed.getCause());
        this.unusable = "the temporary file the output of " + String.join(" ", failed.command())
                + " goes to cannot be written or read (" + SidecarLockUnavailableException.reasonOf(failed.getCause())
                + ")";
    }

    /** What the refusal says of the file, for the lines of the stops (ADR-390 §6). */
    String unusable() {
        return unusable;
    }
}
