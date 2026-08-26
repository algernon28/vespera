package io.algernon.vespera.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.ledger.OccurrencePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The walk against a real filesystem. These tests build the awkward cases rather than describing
 * them, because the awkward cases are the point: an entry whose name cannot be stored, and a link
 * that must not be followed.
 *
 * <p>What is deliberately not asserted here is behaviour under an unreadable directory. Provoking
 * one portably needs ACL manipulation, and how census is tested at all is still open — see issue
 * #15. The walk uses {@code walkFileTree} precisely so that such an entry becomes one anomaly
 * rather than ending the traversal, which is what issue #6 will have to verify.
 */
class WalkTest {

    /** Collects what the walk emits, so assertions read against a transcript. */
    private static final class Recorder implements Walk.Observer {
        final List<String> occurrences = new ArrayList<>();
        final List<Long> sizes = new ArrayList<>();
        final List<Instant> modified = new ArrayList<>();
        final List<String> anomalies = new ArrayList<>();

        @Override
        public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {
            occurrences.add(path.value());
            sizes.add(sizeInBytes);
            modified.add(lastModified);
        }

        @Override
        public void anomaly(String pathRendering, String reason) {
            anomalies.add(pathRendering + " :: " + reason);
        }
    }

    private static Recorder walk(Path root) throws IOException {
        Recorder recorder = new Recorder();
        Walk.walk(root, recorder);
        return recorder;
    }

    @Test
    void recordsOneOccurrencePerFileBeneathTheRoot(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/c.txt"), "deep");
        Files.writeString(root.resolve("d.txt"), "shallow");

        Recorder recorder = walk(root);

        assertEquals(List.of("a/b/c.txt", "d.txt"), new ArrayList<>(new TreeSet<>(recorder.occurrences)));
        assertTrue(recorder.anomalies.isEmpty(), recorder.anomalies.toString());
    }

    @Test
    void recordsSizeAndLastModifiedForEachOccurrence(@TempDir Path root) throws IOException {
        Path file = root.resolve("sized.bin");
        Files.write(file, new byte[1234]);
        Instant onDisk = Files.getLastModifiedTime(file).toInstant();

        Recorder recorder = walk(root);

        assertEquals(1, recorder.occurrences.size());
        assertEquals(1234L, recorder.sizes.get(0));
        assertEquals(onDisk, recorder.modified.get(0));
    }

    @Test
    void reportsTheEmptyCorpusAsEmptyRatherThanFailing(@TempDir Path root) throws IOException {
        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        assertEquals(0, outcome.occurrences());
        assertEquals(0, outcome.anomalies());
        assertEquals(1, outcome.directoriesEntered(), "the root itself is entered");
        assertTrue(outcome.finished());
    }

    /**
     * An unpaired surrogate is a valid NTFS filename with no UTF-8 encoding. It must become an
     * anomaly rather than an occurrence, or the ledger holds a path that cannot reopen its file.
     */
    @Test
    void refusesAnEntryWhoseNameCannotBeStored(@TempDir Path root) throws IOException {
        Path unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
        try {
            Files.writeString(unstorable, "content");
        } catch (IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }
        Files.writeString(root.resolve("ordinary.txt"), "content");

        Recorder recorder = walk(root);

        assertEquals(List.of("ordinary.txt"), recorder.occurrences);
        assertEquals(1, recorder.anomalies.size(), recorder.anomalies.toString());
        assertTrue(recorder.anomalies.get(0).contains("UTF-8"), recorder.anomalies.get(0));
    }

    /**
     * Soft links are skipped and recorded, never traversed (ADR-051). The link here points at a
     * directory holding a file, so following it would produce an occurrence that must not appear.
     */
    @Test
    void skipsSoftLinksAndRecordsThemInstead(@TempDir Path root) throws IOException, InterruptedException {
        Path outside = Files.createDirectories(root.resolve("real"));
        Files.writeString(outside.resolve("hidden.txt"), "must not be walked through the link");
        Path link = root.resolve("link");

        boolean created = false;
        try {
            Files.createSymbolicLink(link, outside);
            created = true;
        } catch (IOException | UnsupportedOperationException e) {
            // Creating a symlink on Windows needs a privilege; a junction does not.
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), outside.toString())
                        .redirectErrorStream(true)
                        .start();
                created = p.waitFor() == 0;
            }
        }
        assumeTrue(created, "could not create a soft link in this environment");

        Recorder recorder = walk(root);

        assertEquals(List.of("real/hidden.txt"), recorder.occurrences, "the link must not be traversed");
        assertEquals(1, recorder.anomalies.size(), recorder.anomalies.toString());
        assertTrue(recorder.anomalies.get(0).toLowerCase().contains("link"), recorder.anomalies.get(0));
        assertFalse(recorder.occurrences.contains("link/hidden.txt"));
    }

    /**
     * The arithmetic behind "excludes nothing": every entry the walk met is accounted for, as an
     * occurrence, an anomaly, or a directory it descended into. Issue #6 decides what is done with
     * this identity; the walk has to make it hold either way.
     */
    @Test
    void accountsForEveryEntryItMet(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one/two"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.writeString(root.resolve("one/two/b.txt"), "b");
        Files.writeString(root.resolve("c.txt"), "c");

        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        assertEquals(3, outcome.occurrences());
        assertEquals(0, outcome.anomalies());
        assertEquals(3, outcome.directoriesEntered(), "root, one, one/two");
        assertEquals(
                outcome.entriesSeen(),
                outcome.occurrences() + outcome.anomalies() + outcome.directoriesEntered() - 1,
                "every entry is an occurrence, an anomaly, or a directory descended into");
    }

    @Test
    void refusesARootThatIsNotADirectory(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("not-a-directory.txt"), "x");
        assertTrue(
                org.junit.jupiter.api.Assertions.assertThrows(
                                IllegalArgumentException.class, () -> Walk.walk(file, new Recorder()))
                        .getMessage()
                        .contains("directory"));
    }
}
