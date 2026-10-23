package io.algernon.vespera.pipeline;

/** The compose files cannot be written out to the temporary folder (ADR-323 §8). */
final class ComposeFilesUnwritableException extends SidecarStartRefusedException {

    ComposeFilesUnwritableException(Throwable reason) {
        super("the sidecars are not answering and Vespera cannot start them: the compose files it starts them from"
                + " cannot be written out (" + SidecarLockUnavailableException.reasonOf(reason) + "). Make room in "
                + System.getProperty("java.io.tmpdir") + ", or make it writable, and run the same command again.");
        initCause(reason);
    }
}
