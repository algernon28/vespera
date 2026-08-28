package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
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
 *
 * <p>Every assertion sits inside a {@code claim(...)}, which names it in the report. The numbers
 * the assertions use are named constants, so a claim can say where a number came from rather than
 * leaving the report to show a bare {@code 1}.
 */
@Epic("Census")
@Feature("Walk")
@Issue("15")
@Issue("2")
@Link(name = "ADR-006", url = Adr.CENSUS_MEASURES_BEFORE_JUDGING, type = "adr")
class WalkTest {

    /** The one file written by the size test, and its length. */
    private static final int SIZED_FILE_BYTES = 1234;

    /** Files written by the accounting test: one at the root, two nested. */
    private static final int FILES_WRITTEN = 3;

    /** Directories the accounting test creates and the walk therefore enters: root, one, one/two. */
    private static final int DIRECTORIES_WRITTEN = 3;

    /** Collects what the walk emits, so assertions read against a transcript. */
    private static final class Recorder implements Walk.Observer {
        final List<String> occurrences = new ArrayList<>();
        final List<Long> sizes = new ArrayList<>();
        final List<Instant> modified = new ArrayList<>();
        final List<String> anomalies = new ArrayList<>();
        final List<WalkAnomalyKind> anomalyKinds = new ArrayList<>();

        @Override
        public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified) {
            occurrences.add(path.value());
            sizes.add(sizeInBytes);
            modified.add(lastModified);
        }

        @Override
        public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
            anomalyKinds.add(kind);
            anomalies.add(pathRendering + " :: " + detail);
        }
    }

    private static Recorder walk(Path root) throws IOException {
        Recorder recorder = new Recorder();
        Walk.walk(root, recorder);
        return recorder;
    }

    @Test
    @Story("What a walk records")
    @DisplayName("One file occurrence per file beneath the root, at any depth")
    void recordsOneOccurrencePerFileBeneathTheRoot(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/c.txt"), "deep");
        Files.writeString(root.resolve("d.txt"), "shallow");

        Recorder recorder = walk(root);

        claim(
                "the two files written, a/b/c.txt and d.txt, became those two occurrences, stored root-relative",
                () -> assertThat(new TreeSet<>(recorder.occurrences)).containsExactly("a/b/c.txt", "d.txt"));
        claim(
                "no anomaly, because both names could be stored",
                () -> assertThat(recorder.anomalies).isEmpty());
    }

    @Test
    @Story("What a walk records")
    @DisplayName("Each file recorded carries its size and its last-modified time")
    void recordsSizeAndLastModifiedForEachOccurrence(@TempDir Path root) throws IOException {
        Path file = root.resolve("sized.bin");
        Files.write(file, new byte[SIZED_FILE_BYTES]);
        Instant onDisk = Files.getLastModifiedTime(file).toInstant();

        Recorder recorder = walk(root);

        claim(
                "one file was written, so the walk recorded exactly one occurrence",
                () -> assertThat(recorder.occurrences).hasSize(1));
        claim(
                "the size recorded is the " + SIZED_FILE_BYTES + " bytes the test wrote to sized.bin",
                () -> assertThat(recorder.sizes.get(0)).isEqualTo(SIZED_FILE_BYTES));
        claim(
                "the last-modified time is the one the filesystem holds for sized.bin, not the clock at walk time",
                () -> assertThat(recorder.modified.get(0)).isEqualTo(onDisk));
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("An empty folder is reported as empty rather than as a failure")
    void reportsTheEmptyCorpusAsEmptyRatherThanFailing(@TempDir Path root) throws IOException {
        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        claim("no files under the root, so no occurrences", () -> assertThat(outcome.occurrences()).isZero());
        claim("an empty corpus is not an anomaly", () -> assertThat(outcome.anomalies()).isZero());
        claim(
                "one directory entered: the root itself, which the walk always enters",
                () -> assertThat(outcome.directoriesEntered()).isEqualTo(1));
        claim(
                "the walk finished, which is what makes the emptiness a measurement rather than a failure",
                () -> assertThat(outcome.finished()).isTrue());
    }

    /**
     * An unpaired surrogate is a valid NTFS filename with no UTF-8 encoding. It must become an
     * anomaly rather than an occurrence, or the ledger holds a path that cannot reopen its file.
     */
    @Test
    @Story("Entries that become walk anomalies")
    @DisplayName("A name with no UTF-8 encoding becomes a walk anomaly, not an occurrence")
    @Link(name = "ADR-053", url = Adr.WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS, type = "adr")
    void refusesAnEntryWhoseNameCannotBeStored(@TempDir Path root) throws IOException {
        Path unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
        try {
            Files.writeString(unstorable, "content");
        } catch (IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }
        Files.writeString(root.resolve("ordinary.txt"), "content");

        Recorder recorder = walk(root);

        claim(
                "of the two files written, only the storable name, ordinary.txt, became an occurrence",
                () -> assertThat(recorder.occurrences).containsExactly("ordinary.txt"));
        claim(
                "the other file was recorded rather than dropped: one anomaly",
                () -> assertThat(recorder.anomalies).hasSize(1));
        claim(
                "that anomaly carries its reason, the name having no UTF-8 encoding",
                () -> assertThat(recorder.anomalies.get(0)).contains("UTF-8"));
        claim(
                "that anomaly is kinded as unencodable-path (ADR-053)",
                () -> assertThat(recorder.anomalyKinds).containsExactly(WalkAnomalyKind.UNENCODABLE_PATH));
    }

    /**
     * Soft links are skipped and recorded, never traversed (ADR-051). The link here points at a
     * directory holding a file, so following it would produce an occurrence that must not appear.
     */
    @Test
    @Story("Entries that become walk anomalies")
    @DisplayName("A soft link is recorded, and the walk does not follow it")
    @Issue("19")
    @Link(name = "ADR-051", url = Adr.OCCURRENCE_IDENTIFIED_BY_RELATIVE_PATH, type = "adr")
    @Link(name = "ADR-053", url = Adr.WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS, type = "adr")
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

        claim(
                "the one file behind the link was reached once, as real/hidden.txt, and not through the link",
                () -> assertThat(recorder.occurrences).containsExactly("real/hidden.txt"));
        claim("the link itself was recorded: one anomaly", () -> assertThat(recorder.anomalies).hasSize(1));
        claim(
                "that anomaly carries its reason, the entry being a link",
                () -> assertThat(recorder.anomalies.get(0).toLowerCase()).contains("link"));
        claim(
                "that anomaly is kinded as soft-link-not-followed (ADR-053)",
                () -> assertThat(recorder.anomalyKinds).containsExactly(WalkAnomalyKind.SOFT_LINK_NOT_FOLLOWED));
        claim(
                "nothing was reached through the link, which would double-count the file behind it",
                () -> assertThat(recorder.occurrences).doesNotContain("link/hidden.txt"));
    }

    /**
     * The arithmetic behind "excludes nothing": every entry the walk met is accounted for, as an
     * occurrence, an anomaly, or a directory it descended into. Issue #6 decides what is done with
     * this identity; the walk has to make it hold either way.
     */
    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("Every entry met is recorded, reported as an anomaly, or descended into")
    @Issue("6")
    @Link(name = "ADR-050", url = Adr.PIPELINE_HAS_EXCLUSIVE_ACCESS, type = "adr")
    void accountsForEveryEntryItMet(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("one/two"));
        Files.writeString(root.resolve("one/a.txt"), "a");
        Files.writeString(root.resolve("one/two/b.txt"), "b");
        Files.writeString(root.resolve("c.txt"), "c");

        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        claim(
                FILES_WRITTEN + " files were written, so " + FILES_WRITTEN + " occurrences were recorded",
                () -> assertThat(outcome.occurrences()).isEqualTo(FILES_WRITTEN));
        claim(
                "every entry could be recorded, so nothing anomalous",
                () -> assertThat(outcome.anomalies()).isZero());
        claim(
                DIRECTORIES_WRITTEN + " directories entered: the root, one, and one/two",
                () -> assertThat(outcome.directoriesEntered()).isEqualTo(DIRECTORIES_WRITTEN));
        claim(
                "every entry met is an occurrence, an anomaly, or a directory descended into"
                        + " — the root is subtracted because nothing contains it",
                () -> assertThat(outcome.entriesSeen())
                        .isEqualTo(outcome.occurrences() + outcome.anomalies() + outcome.directoriesEntered() - 1));
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("A root that is a file, not a directory, is refused up front")
    void refusesARootThatIsNotADirectory(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("not-a-directory.txt"), "x");

        claim(
                "a file given as the corpus root is refused, saying so, rather than walked as an empty corpus",
                () -> assertThatThrownBy(() -> Walk.walk(file, new Recorder()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("directory"));
    }
}
