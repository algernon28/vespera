package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the archive has gone (ADR-210 section 2): opens the canonical corpus root as a directory
 * listing and closes it, reading no entry. If that throws, or the root is not a directory, the archive has
 * gone; if it succeeds, a failure to read a file was the file's own.
 *
 * <p>It is the cheapest question whose answer is about the archive and not about one file, and it reads no
 * document. {@code pipeline} supplies it because the canonical root is held here, and {@code extraction}
 * and {@code corpus} reach it through callbacks and sites {@code pipeline} owns (ADR-040). It is asked on
 * every failure to read a file, not once per step: a disk drops out partway, and the first failure after
 * it is where it can be seen.
 */
final class CorpusRootCheck {

    private final Path canonicalRoot;

    CorpusRootCheck(Path canonicalRoot) {
        this.canonicalRoot = canonicalRoot;
    }

    /**
     * What the file system reported for a file that could not be read: the kind of exception, by its simple
     * name, then its message, or the simple name alone where it has no message. The kind is what tells a
     * file that is gone from one that is locked or whose permission was removed, since the message of
     * those is often only the path.
     */
    static String reported(IOException cause) {
        String kind = cause.getClass().getSimpleName();
        return cause.getMessage() != null ? kind + ": " + cause.getMessage() : kind;
    }

    /** Returns when the root can still be listed. */
    void requireListable() {
        if (!Files.isDirectory(canonicalRoot)) {
            throw new ArchiveGoneException(canonicalRoot, null);
        }
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(canonicalRoot)) {
            // Opened and closed, no entry read.
        } catch (IOException | RuntimeException cannotList) {
            throw new ArchiveGoneException(canonicalRoot, cannotList);
        }
    }
}
