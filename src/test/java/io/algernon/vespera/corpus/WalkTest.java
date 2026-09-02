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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
 * <p>The walk's own logic — counting, resuming, skipping a subtree already recorded — is tested
 * against a corpus built in memory instead, in {@link WalkAlgorithmTest}. The shape of the tree is
 * all those questions need, and a fixture costing no disk can be made large enough to break the
 * arithmetic (ADR-065). What stays here is what only a real filesystem is evidence for.
 *
 * <p>A path too long for the filesystem cannot be created directly — the refusal leaves nothing
 * behind to walk — so the fixture is built the way a real archive acquires one: nest a tree while
 * every path is still legal, then lengthen an ancestor, and every entry below it becomes
 * unreachable without ever having been written at an illegal path. That is not a trick, it is the
 * case itself: archives copied between systems arrive over-long for exactly this reason.
 *
 * <p>Whether the fixture can be built is asked at runtime rather than assumed from the operating
 * system name. Where the answer is no, the test aborts and says which step refused.
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

    /**
     * A path component long enough to lengthen a tree usefully, and short enough for NTFS, whose
     * limit is 255 characters for one component.
     */
    private static final int LONG_COMPONENT = 200;

    /** The longest name NTFS gives one path component. */
    private static final int NTFS_COMPONENT_LIMIT = 255;

    /** The length beyond which the JDK refuses to touch a path at all, whatever the volume allows. */
    private static final int PATH_LIMIT = 32_000;

    /**
     * How far past the limit the fixture puts its deepest directory.
     *
     * <p>Not decoration. The refusal lands on the directory the walk tries to open, not on the file
     * inside it: entries below an openable directory are reached through its handle and never
     * measured. A fixture that clears the limit by a character or two therefore depends on how long
     * the temporary directory's own name happens to be, and passes or fails by luck. This is chosen
     * so the deepest directory is over the limit on any root the fixture is given.
     */
    private static final int MARGIN = 50;

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
        final List<Instant> created = new ArrayList<>();
        final List<String> anomalies = new ArrayList<>();
        final List<WalkAnomalyKind> anomalyKinds = new ArrayList<>();

        @Override
        public void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified, Instant creationTime) {
            occurrences.add(path.value());
            sizes.add(sizeInBytes);
            modified.add(lastModified);
            created.add(creationTime);
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
    @DisplayName("Each file recorded carries its size, its last-modified time, and its creation time")
    @Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
    void recordsSizeLastModifiedAndCreationTimeForEachOccurrence(@TempDir Path root) throws IOException {
        Path file = root.resolve("sized.bin");
        Files.write(file, new byte[SIZED_FILE_BYTES]);
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Instant lastModifiedOnDisk = attrs.lastModifiedTime().toInstant();
        Instant creationTimeOnDisk = attrs.creationTime().toInstant();

        Recorder recorder = walk(root);

        claim(
                "one file was written, so the walk recorded exactly one occurrence",
                () -> assertThat(recorder.occurrences).hasSize(1));
        claim(
                "the size recorded is the " + SIZED_FILE_BYTES + " bytes the test wrote to sized.bin",
                () -> assertThat(recorder.sizes.get(0)).isEqualTo(SIZED_FILE_BYTES));
        claim(
                "the last-modified time is the one the filesystem holds for sized.bin, not the clock at walk time",
                () -> assertThat(recorder.modified.get(0)).isEqualTo(lastModifiedOnDisk));
        claim(
                "the creation time is the one the filesystem holds for sized.bin (ADR-069), read from the same"
                        + " attributes call as the last-modified time, at no extra filesystem cost",
                () -> assertThat(recorder.created.get(0)).isEqualTo(creationTimeOnDisk));
    }

    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("An empty folder is reported as empty rather than as a failure")
    void reportsTheEmptyCorpusAsEmptyRatherThanFailing(@TempDir Path root) throws IOException {
        Walk.Outcome outcome = Walk.walk(root, new Recorder());

        claim("no files under the root, so no occurrences", () -> assertThat(outcome.progress().occurrences()).isZero());
        claim("an empty corpus is not an anomaly", () -> assertThat(outcome.progress().anomalies()).isZero());
        claim(
                "one directory entered: the root itself, which the walk always enters",
                () -> assertThat(outcome.progress().directoriesEntered()).isEqualTo(1));
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
        Path unstorable;
        try {
            unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
            Files.writeString(unstorable, "content");
        } catch (InvalidPathException | IOException e) {
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
                () -> assertThat(outcome.progress().occurrences()).isEqualTo(FILES_WRITTEN));
        claim(
                "every entry could be recorded, so nothing anomalous",
                () -> assertThat(outcome.progress().anomalies()).isZero());
        claim(
                DIRECTORIES_WRITTEN + " directories entered: the root, one, and one/two",
                () -> assertThat(outcome.progress().directoriesEntered()).isEqualTo(DIRECTORIES_WRITTEN));
        claim(
                "every entry met is an occurrence, an anomaly, or a directory descended into"
                        + " — the root is subtracted because nothing contains it",
                () -> assertThat(outcome.progress().entriesSeen())
                        .isEqualTo(outcome.progress().occurrences() + outcome.progress().anomalies() + outcome.progress().directoriesEntered() - 1));
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

    /**
     * The third anomaly kind against a real filesystem: an entry the walk meets and cannot touch,
     * because its path is longer than anything the JDK will open.
     *
     * <p>What is worth pinning is that the walk reports it. An entry that is silently skipped is the
     * one outcome census cannot survive — the ledger would hold a corpus smaller than the archive
     * and say nothing about the difference — so the entry has to become an anomaly, and the
     * accounting identity has to keep holding with that anomaly in it (ADR-056).
     *
     * <p>What the anomaly's detail says is deliberately not pinned. The walk passes the JDK's own
     * message through (see {@code visitFileFailed}), and that wording belongs to the JDK: asserting
     * on it would make this test fail the day a JDK release rephrases it, for no fault of vespera's.
     * The kind, the entry named, and the arithmetic are what this project owns and what is claimed
     * here.
     */
    @Test
    @Story("Nothing goes unaccounted for")
    @DisplayName("An entry whose path is too long to open is reported, not passed over in silence")
    @Issue("6")
    @Link(name = "ADR-053", url = Adr.WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS, type = "adr")
    void reportsAnEntryWhosePathIsTooLongToOpen(@TempDir Path root) throws IOException {
        Path ancestor = Files.createDirectory(root.resolve("t"));
        Path deepest = nestWhileTheFilesystemAllowsIt(ancestor);
        Files.writeString(deepest.resolve("leaf.txt"), "x");
        Path lengthened = lengthen(ancestor, deepest);

        try {
            Recorder recorder = new Recorder();
            Walk.Outcome outcome = Walk.walk(root, recorder);

            claim(
                    "the entry the walk could not open was reported as unprocessable, which is the kind"
                            + " ADR-053 gives a path too long to touch",
                    () -> assertThat(recorder.anomalyKinds).contains(WalkAnomalyKind.UNPROCESSABLE));
            claim(
                    "and the anomaly names the entry, so what could not be opened is identifiable rather"
                            + " than merely counted",
                    () -> assertThat(recorder.anomalies).anySatisfy(anomaly -> assertThat(anomaly)
                            .contains(lengthened.getFileName().toString())));
            claim(
                    "and every entry the walk met is still accounted for, this one as an anomaly —"
                            + " the identity has to hold with the unreachable entry in it, not despite it",
                    () -> assertThat(outcome.progress().accountsForEveryEntry()).isTrue());
        } finally {
            // Renamed back so the temporary directory can be deleted: what the walk could not open,
            // the cleanup cannot delete either. Reported rather than raised, because a failure thrown
            // from here would replace whatever the test actually found with a story about tidying up.
            try {
                Files.move(lengthened, ancestor);
            } catch (IOException e) {
                System.err.println("the over-long fixture could not be shortened again, so " + ancestor.getParent()
                        + " stays behind: " + e.getMessage());
            }
        }
    }

    /**
     * Nests directories under {@code ancestor} until one more would pass the limit, so that
     * lengthening an ancestor afterwards is what carries the tree over it.
     *
     * <p>Aborts rather than fails where the filesystem stops early: a volume with a shorter limit
     * than this cannot hold the fixture, and that is a fact about the environment rather than about
     * the walk.
     */
    private static Path nestWhileTheFilesystemAllowsIt(Path ancestor) {
        String component = "x".repeat(LONG_COMPONENT);
        Path deepest = ancestor;
        try {
            while (deepest.toString().length() + LONG_COMPONENT < PATH_LIMIT) {
                deepest = Files.createDirectory(deepest.resolve(component));
            }
        } catch (IOException e) {
            abort("this filesystem stopped nesting at " + deepest.toString().length()
                    + " characters, short of the " + PATH_LIMIT + " this fixture needs: " + e.getMessage());
        }
        return deepest;
    }

    /**
     * Renames {@code ancestor} to the length that carries {@code deepest} past the limit, without any
     * entry ever having been created at an illegal path.
     *
     * <p>The new name is measured rather than fixed, because how deep the nesting reached depends on
     * how long the temporary root's own name was.
     */
    private static Path lengthen(Path ancestor, Path deepest) {
        int growthNeeded = PATH_LIMIT + MARGIN - deepest.toString().length();
        int newLength = ancestor.getFileName().toString().length() + growthNeeded;
        if (newLength > NTFS_COMPONENT_LIMIT) {
            abort("carrying this tree past " + PATH_LIMIT + " characters needs a name of " + newLength
                    + ", and no NTFS name may be longer than " + NTFS_COMPONENT_LIMIT);
        }
        Path lengthened = ancestor.resolveSibling("t".repeat(newLength));
        try {
            Files.move(ancestor, lengthened);
        } catch (IOException e) {
            abort("this filesystem would not lengthen the ancestor of an already-deep tree: " + e.getMessage());
        }
        return lengthened;
    }
}
