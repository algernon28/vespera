package io.algernon.vespera.pipeline;

import java.nio.file.Path;

/**
 * The corpus root can no longer be listed, so the archive has gone: the disk dropped out, the share was
 * disconnected, the folder was moved (ADR-210 section 2). Thrown by {@link CorpusRootCheck} before any file
 * is marked for a failed read, and not skippable by stage 2's policy: it is not a {@link
 * ServiceScopeFailureException}, so the step stops and removes nothing.
 */
final class ArchiveGoneException extends RuntimeException {

    ArchiveGoneException(Path canonicalRoot, Throwable cause) {
        super(
                "the corpus root " + canonicalRoot + " can no longer be listed, so the archive has gone and nothing"
                        + " was removed for it",
                cause);
    }
}
