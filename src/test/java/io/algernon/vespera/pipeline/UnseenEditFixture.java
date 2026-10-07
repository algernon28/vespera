package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * A document whose bytes change between two invocations while everything a walk records about it
 * stays the same: its path, its size, its last-modified time and its creation time (ADR-115).
 *
 * <p>The walk sees the same observation, so the archive keeps its walk, every run is continued under
 * its own id, and the approval still names the arrangement. It is the real-world case of a file
 * rewritten in place by something that puts its timestamp back, such as a restore from backup or a
 * sync tool.
 *
 * <p><b>What it shows changed with ADR-206.</b> Until then every step after stage 2 hashed the file
 * again to find its conversion, found nothing cached under the new hash, and so could send nothing of
 * the document; tests used this to reach a cluster nothing can be sent for (ADR-121). Each step now
 * reads the key stage 2 recorded, so a document rewritten in place is scored, arranged, written about
 * and listed as it was converted, and this fixture is how a test shows that. The tests that need a
 * cluster nothing can be sent for use {@link ConversionOffTheRecordFixture}.
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
