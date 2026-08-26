package io.algernon.vespera.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        assertThat(new TreeSet<>(recorder.occurrences)).containsExactly("a/b/c.txt", "d.txt");
        assertThat(recorder.anomalies).isEmpty();
    }

    @Test
    void recordsSizeAndLastModifiedForEachOccurrence(@TempDir Path root) throws IOException {
        Path file = root.resolve("sized.bin");
        Files.write(file, new byte[1234]);
        Instant onDisk = Files.getLastModifiedTime(file).toInstant();

        Recorder recorder = walk(root);

        assertThat(recorder.occurrences).hasSize(1);
        assertThat(recorder.sizes.get(0)).isEqualTo(1234L);
        assertThat(recorder.modified.get(0)).isEqualTo(onDisk);
    }

    @Test
    void reportsTheEmptyCorpusAsEmptyRatherThanFailing(@TempDir Path root) throws IOException {
        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        assertThat(outcome.occurrences()).isZero();
        assertThat(outcome.anomalies()).isZero();
        assertThat(outcome.directoriesEntered()).as("the root itself is entered").isEqualTo(1);
        assertThat(outcome.finished()).isTrue();
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

        assertThat(recorder.occurrences).containsExactly("ordinary.txt");
        assertThat(recorder.anomalies).hasSize(1);
        assertThat(recorder.anomalies.get(0)).contains("UTF-8");
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

        assertThat(recorder.occurrences).as("the link must not be traversed").containsExactly("real/hidden.txt");
        assertThat(recorder.anomalies).hasSize(1);
        assertThat(recorder.anomalies.get(0).toLowerCase()).contains("link");
        assertThat(recorder.occurrences).doesNotContain("link/hidden.txt");
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

        assertThat(outcome.occurrences()).isEqualTo(3);
        assertThat(outcome.anomalies()).isZero();
        assertThat(outcome.directoriesEntered()).as("root, one, one/two").isEqualTo(3);
        assertThat(outcome.entriesSeen())
                .as("every entry is an occurrence, an anomaly, or a directory descended into")
                .isEqualTo(outcome.occurrences() + outcome.anomalies() + outcome.directoriesEntered() - 1);
    }

    @Test
    void refusesARootThatIsNotADirectory(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("not-a-directory.txt"), "x");
        assertThatThrownBy(() -> Walk.walk(file, new Recorder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory");
    }
}
