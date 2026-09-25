package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * A document whose bytes change between two invocations while everything a walk records about it
 * stays the same: its path, its size, its last-modified time and its creation time (ADR-115).
 *
 * <p>This is how a test reaches a cluster nothing can be sent for (ADR-121) with rows an arrangement
 * really records. The walk sees the same observation, so the archive keeps its walk, every run is
 * continued under its own id, and the approval still names the arrangement. Stage 6b reads the file
 * again to find its cached text by content hash, and nothing was ever cached under the new hash, since
 * no step converts or chunks a document again once its work is recorded, and the labelling page reads
 * the extraction cache only (ADR-152). So the member is still there and nothing of it can be sent. It
 * is the real-world case of a file rewritten in place by something that puts its timestamp back, such
 * as a restore from backup or a sync tool.
 *
 * <p>Deleting the file, or changing its length, would be seen by the walk. The archive would be a
 * different observation, every run downstream of it a different run, and the approval would name
 * nothing (ADR-115, ADR-154), so the test would never reach the cluster at all.
 *
 * <p>The edit is made in place, truncating and rewriting the same file rather than replacing it, so the
 * file keeps its creation time where the filesystem records one. Where it records none, the JDK reports
 * the last-modified time instead, which is put back here.
 */
final class UnseenEditFixture {

    private UnseenEditFixture() {
    }

    /**
     * Rewrites {@code file} with bytes of the same length that are not its own, and puts back its
     * last-modified time.
     *
     * @return the bytes it held before, for {@link #restored}
     */
    static byte[] editedWithoutTheWalkNoticing(Path file) throws IOException {
        byte[] original = Files.readAllBytes(file);
        byte[] edited = original.clone();
        for (int i = 0; i < edited.length; i++) {
            edited[i] = (byte) (edited[i] == 'x' ? 'y' : 'x');
        }
        rewrittenInPlace(file, edited);
        return original;
    }

    /** Puts {@code original} back into {@code file}, its last-modified time unchanged again. */
    static void restored(Path file, byte[] original) throws IOException {
        rewrittenInPlace(file, original);
    }

    private static void rewrittenInPlace(Path file, byte[] bytes) throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(file);
        Files.write(file, bytes);
        Files.setLastModifiedTime(file, lastModified);
    }
}
