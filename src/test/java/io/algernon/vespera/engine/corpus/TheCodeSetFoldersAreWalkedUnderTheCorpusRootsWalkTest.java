package io.algernon.vespera.engine.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.ResumableWalk;
import io.algernon.vespera.engine.ledger.WalkCounts;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The code set folders outside the corpus root are walked under the corpus root's one walk id (ADR-334 §5.3, §5.4):
 * an unfinished walk is continued with the folders it recorded when it started, whatever it is given now (ADR-055); a
 * checkpoint naming a folder by its ordinal, {@code @<n>:}, is stored and read back whole; a walk reading another list
 * of folders is never taken for a repetition of the one before it (ADR-115), an empty folder added included; and a walk
 * anomaly below a code set folder is spelt by that folder's real path, absolute, as its occurrences are.
 *
 * <p>Like {@code WalkRecorderTest}, a walk is stopped through the traversal seam of {@link WalkRecorder}'s
 * package-private constructor, with a commit interval of one, and stops in the corpus root, before any folder outside
 * it is read; or, to stop inside such a folder, which that seam does not reach, through the transaction template the
 * same constructor takes.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk recording")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheCodeSetFoldersAreWalkedUnderTheCorpusRootsWalkTest {

    /** Checkpoint at every completed directory, so a small fixture is stopped after a checkpoint. */
    private static final int EVERY_DIRECTORY = 1;

    /** The ordinal of the first code set folder a walk reads, the corpus root being {@code 0}. */
    private static final int THE_FIRST_FOLDER = 1;

    /** The looks at the corpus root kept by the test of repetitions: one for each list of folders read. */
    private static final int FOUR_LOOKS = 4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A walk that stopped in the corpus root is continued under its own id with the folders it recorded, not those it is given now")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void anUnfinishedWalkIsContinuedWithTheFoldersItRecorded(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        Path tools = aFolderHolding(temp.resolve("tools"), "build.py");
        Path addedSince = aFolderHolding(temp.resolve("added-since"), "Later.java");
        Ledger ledger = new Ledger(jdbcTemplate);

        WalkId stopped = recorder(stoppingAfterTheFirstDirectory()).walk(root, List.of(billing, tools));

        claim("the stopped walk is unfinished and recorded the two folders it was given, by their real paths, in order",
                () -> {
                    assertThat(ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).isEmpty();
                    assertThat(ledger.walks().foldersOf(stopped))
                            .containsExactly(Walk.canonicalRoot(billing), Walk.canonicalRoot(tools));
                });
        claim("it stopped in the corpus root, so nothing below either folder is recorded yet",
                () -> assertThat(pathsOf(stopped)).noneMatch(path -> path.startsWith(spelt(billing))
                        || path.startsWith(spelt(tools))));

        WalkId resumed = recorder().walk(root, List.of(addedSince));

        claim("the walk given another list of folders since is continued under its own id",
                () -> assertThat(resumed).isEqualTo(stopped));
        claim("and reads the folders it recorded: the corpus's files, and the two folders' files by their whole paths;"
                        + " the folder given now applies from the next walk",
                () -> assertThat(pathsOf(resumed)).containsExactlyInAnyOrder("one/a.txt", "two/b.txt",
                        spelt(billing) + "/pom.xml", spelt(tools) + "/build.py"));
        claim("the walk is finished, accounting for every entry, and still records the two folders it started with",
                () -> {
                    assertThat(ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).contains(resumed);
                    assertThat(ledger.walks().foldersOf(resumed))
                            .containsExactly(Walk.canonicalRoot(billing), Walk.canonicalRoot(tools));
                });
    }

    /**
     * An invocation stopped inside a code set folder, once a checkpoint there is committed, is continued from that
     * checkpoint by the next (ADR-055; ADR-334 §5.3, "a checkpoint names the folder it lies in by its ordinal"): under
     * the walk's own id, in that folder, the corpus root not walked again nor what the checkpoint covers. The folders are
     * walked by {@link Walk#walk} itself, not through the traversal seam, so the invocation is stopped by the transaction
     * template the recorder commits through: it throws once the walk row's checkpoint names the first folder, as a
     * machine stopping there ends the invocation, every row committed so far kept.
     */
    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A walk that stopped inside a folder outside the corpus root is continued from where it stopped there, under its own id, walking nothing again")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void aWalkStoppedInsideAFolderIsContinuedFromItsCheckpointThere(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        aFolderHolding(billing.resolve("lib"), "Lib.java");
        aFolderHolding(billing.resolve("app"), "App.java");
        Path tools = aFolderHolding(temp.resolve("tools"), "build.py");
        String inLib = spelt(billing) + "/lib/Lib.java";
        String inApp = spelt(billing) + "/app/App.java";
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkRecorder stoppingInTheFirstFolder = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate),
                stoppingOnceTheCheckpointLiesIn(THE_FIRST_FOLDER), Walk::walk, EVERY_DIRECTORY);

        Throwable stoppedBy = catchThrowable(() -> stoppingInTheFirstFolder.walk(root, List.of(billing, tools)));
        Optional<ResumableWalk> unfinished = ledger.walks().unfinishedWalk(Walk.canonicalRoot(root));

        claim("the first invocation ended inside the first folder outside the corpus root, its walk unfinished and its last"
                        + " checkpoint led by that folder's ordinal",
                () -> {
                    assertThat(stoppedBy).isInstanceOf(MachineStopped.class);
                    assertThat(unfinished).map(ResumableWalk::checkpointOrdinals)
                            .hasValueSatisfying(ordinals -> assertThat(ordinals).startsWith("@" + THE_FIRST_FOLDER + ":"));
                });
        WalkId stopped = unfinished.orElseThrow().walkId();
        Map<String, Long> recordedBeforeTheStop = rowsOf(stopped);
        claim("by then the corpus's files are recorded, and of the folder's two subfolders the file of the one its"
                        + " checkpoint covers and not the other's, and nothing of the second folder",
                () -> {
                    assertThat(recordedBeforeTheStop).containsKeys("one/a.txt", "two/b.txt");
                    assertThat(Stream.of(inLib, inApp).filter(recordedBeforeTheStop::containsKey)).hasSize(1);
                    assertThat(recordedBeforeTheStop.keySet()).noneMatch(path -> path.startsWith(spelt(tools)));
                });

        WalkId resumed = recorder().walk(root, List.of(billing, tools));

        claim("the next invocation continues the walk under its own id, and finishes it, accounting for every entry",
                () -> {
                    assertThat(resumed).isEqualTo(stopped);
                    assertThat(ledger.walks().finishedWalkFor(Walk.canonicalRoot(root))).contains(resumed);
                });
        claim("the walk holds every file of the corpus and of both folders, each once",
                () -> assertThat(pathsOf(resumed)).containsExactlyInAnyOrder("one/a.txt", "two/b.txt",
                        spelt(billing) + "/pom.xml", inLib, inApp, spelt(tools) + "/build.py"));
        claim("and every file recorded before the stop is the same row, so nothing the checkpoint covers was walked again",
                () -> assertThat(rowsOf(resumed)).containsAllEntriesOf(recordedBeforeTheStop));
    }

    @Test
    @Story("A walk resumes where it stopped")
    @DisplayName("A checkpoint in a folder outside the corpus root is stored led by the folder's ordinal and read back whole, and one in the corpus root carries no mark")
    @Link(name = "ADR-055", url = Adr.A_WALK_IS_RESUMED_UNDER_ITS_OWN_ID, type = "adr")
    void aCheckpointNamingItsFolderSurvivesARoundTrip(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        Path tools = aFolderHolding(temp.resolve("tools"), "build.py");
        Checkpoint inTheSecondFolder = new Checkpoint(List.of(2, 1), "src/main", 2);
        Checkpoint inTheCorpusRoot = new Checkpoint(List.of(3), "two");
        Ledger ledger = new Ledger(jdbcTemplate);

        claim("a checkpoint in the second folder is stored as @2: and its ordinals, one in the corpus root as its"
                        + " ordinals alone",
                () -> {
                    assertThat(inTheSecondFolder.encodedOrdinals()).isEqualTo("@2:2/1");
                    assertThat(inTheCorpusRoot.encodedOrdinals()).isEqualTo("3");
                });
        claim("each is read back from its two columns as the checkpoint it was, its folder among it",
                () -> {
                    assertThat(Checkpoint.of(inTheSecondFolder.encodedOrdinals(), inTheSecondFolder.pathRendering()))
                            .contains(inTheSecondFolder);
                    assertThat(Checkpoint.of(inTheCorpusRoot.encodedOrdinals(), inTheCorpusRoot.pathRendering()))
                            .contains(inTheCorpusRoot);
                });

        WalkId walk = ledger.walks().startWalk(Walk.canonicalRoot(root),
                List.of(Walk.canonicalRoot(billing), Walk.canonicalRoot(tools)));
        ledger.walks().recordProgress(walk, inTheSecondFolder.encodedOrdinals(), inTheSecondFolder.pathRendering(),
                new WalkCounts(9, 4));
        ResumableWalk unfinished = ledger.walks().unfinishedWalk(Walk.canonicalRoot(root)).orElseThrow();

        claim("and recorded with a walk, the ledger hands it back to the next invocation as the same checkpoint",
                () -> assertThat(Checkpoint.of(unfinished.checkpointOrdinals(), unfinished.checkpointPath()))
                        .contains(inTheSecondFolder));
    }

    @Test
    @Story("Looking twice at a folder nothing has happened to is one look")
    @DisplayName("A walk reading another list of folders than the walk before it is a new look, though it found the same files, an empty folder added included")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aWalkReadingAnotherListOfFoldersIsNeverARepetition(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        Path anEmptyFolder = Files.createDirectories(temp.resolve("empty-one"));
        Path anotherEmptyFolder = Files.createDirectories(temp.resolve("empty-two"));

        WalkId first = recorder().walk(root, List.of(billing));
        WalkId again = recorder().walk(root, List.of(billing));
        WalkId withAnEmptyFolder = recorder().walk(root, List.of(billing, anEmptyFolder));
        WalkId withAnotherEmptyFolder = recorder().walk(root, List.of(billing, anotherEmptyFolder));
        WalkId withNoFolder = recorder().walk(root, List.of());

        claim("the same folders read again, nothing in them changed, are one look, as any repetition is",
                () -> assertThat(again).isEqualTo(first));
        claim("an empty folder added to the list is a new look, though no file was added",
                () -> assertThat(withAnEmptyFolder).isNotEqualTo(first));
        claim("another empty folder in its place is a new look again, though the files, the anomalies and the folders"
                        + " entered are as before: only the list read differs",
                () -> assertThat(withAnotherEmptyFolder).isNotEqualTo(withAnEmptyFolder));
        claim("and no folder read is a new look too",
                () -> assertThat(withNoFolder).isNotEqualTo(withAnotherEmptyFolder));
        claim("so " + FOUR_LOOKS + " looks at the corpus root are kept, one for each list of folders read",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class,
                        Walk.canonicalRoot(root).toString())).isEqualTo(FOUR_LOOKS));
    }

    @Test
    @Story("Code kept beside the archive is read with it")
    @DisplayName("A name with no UTF-8 encoding below a folder of code outside the corpus root is noted by the folder's whole path")
    void anUnencodableNameBelowACodeSetFolderIsSpeltAbsolute(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        try {
            Files.writeString(billing.resolve("orphan-" + (char) 0xD800 + ".txt"), "content");
        } catch (InvalidPathException | IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }

        WalkId walk = recorder().walk(root, List.of(billing));

        claim("the one note of the walk is spelt by the folder's real path, / between its elements, then the name below",
                () -> assertThat(anomaliesOf(walk)).singleElement().asString()
                        .startsWith(spelt(billing) + "/orphan-")
                        .endsWith(".txt"));
    }

    @Test
    @Story("Code kept beside the archive is read with it")
    @DisplayName("A soft link below a folder of code outside the corpus root is noted by the folder's whole path")
    void aSoftLinkBelowACodeSetFolderIsSpeltAbsolute(@TempDir Path temp) throws IOException {
        Path root = theCorpusRoot(temp);
        Path billing = aFolderHolding(temp.resolve("billing"), "pom.xml");
        try {
            Files.createSymbolicLink(billing.resolve("linked"), billing.resolve("pom.xml"));
        } catch (UnsupportedOperationException | IOException e) {
            abort("this machine will not make a soft link: " + e.getMessage());
        }

        WalkId walk = recorder().walk(root, List.of(billing));

        claim("the note is spelt by the folder's real path, / between its elements, then the link's name",
                () -> assertThat(anomaliesOf(walk)).containsExactly(spelt(billing) + "/linked"));
    }

    private WalkRecorder recorder() {
        return new WalkRecorder(new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate),
                new JdbcTransactionManager(dataSource));
    }

    private WalkRecorder recorder(WalkRecorder.Traversal traversal) {
        return new WalkRecorder(new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate),
                new TransactionTemplate(new JdbcTransactionManager(dataSource)), traversal, EVERY_DIRECTORY);
    }

    /** A corpus root of two folders, a file in each, so that a walk stopped at its first checkpoint has more to do. */
    private static Path theCorpusRoot(Path temp) throws IOException {
        Path root = Files.createDirectories(temp.resolve("archivio"));
        Files.createDirectories(root.resolve("one"));
        Files.writeString(root.resolve("one/a.txt"), "a", StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("two"));
        Files.writeString(root.resolve("two/b.txt"), "b", StandardCharsets.UTF_8);
        return root;
    }

    /** A folder outside the corpus root holding one file. */
    private static Path aFolderHolding(Path folder, String file) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(file), "code of " + file, StandardCharsets.UTF_8);
        return folder;
    }

    /** The folder's real path with {@code /} between its elements, as ADR-334 §5.4 spells what lies below it. */
    private static String spelt(Path folder) {
        return Walk.spellingOf(Walk.canonicalRoot(folder));
    }

    /** The paths of the occurrences {@code walk} recorded. */
    private List<String> pathsOf(WalkId walk) {
        return jdbcTemplate.queryForList(
                "SELECT path FROM file_occurrence WHERE walk_id = ? ORDER BY path", String.class, walk.value());
    }

    /** The occurrences {@code walk} recorded, each path's row id by its path. */
    private Map<String, Long> rowsOf(WalkId walk) {
        return jdbcTemplate.query("SELECT id, path FROM file_occurrence WHERE walk_id = ?",
                        (row, number) -> Map.entry(row.getString("path"), row.getLong("id")), walk.value())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * A transaction template that commits as the recorder's does, then ends the invocation, as a machine stopping would,
     * once a walk's checkpoint lies in the folder of {@code ordinal}.
     */
    private TransactionTemplate stoppingOnceTheCheckpointLiesIn(int ordinal) {
        return new TransactionTemplate(new JdbcTransactionManager(dataSource)) {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                T result = super.execute(action);
                Integer there = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM walk WHERE checkpoint_ordinals LIKE ?", Integer.class, "@" + ordinal + ":%");
                if (there != null && there > 0) {
                    throw new MachineStopped();
                }
                return result;
            }
        };
    }

    /** Not an error: the way this test says "the machine stopped here", once the checkpoint was committed. */
    private static final class MachineStopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** How the walk anomalies {@code walk} recorded are spelt. */
    private List<String> anomaliesOf(WalkId walk) {
        return jdbcTemplate.queryForList(
                "SELECT path_rendering FROM walk_anomaly WHERE walk_id = ? ORDER BY id", String.class, walk.value());
    }

    /**
     * A traversal that walks for real and stops as soon as the walk offers its first checkpoint, the shape of an
     * invocation killed partway, as {@code WalkRecorderTest}'s does.
     */
    private static WalkRecorder.Traversal stoppingAfterTheFirstDirectory() {
        return (root, observer, resumeFrom) -> {
            StopAtTheFirstCheckpoint stopping = new StopAtTheFirstCheckpoint(observer);
            try {
                return Walk.walk(root, stopping, resumeFrom);
            } catch (StopAtTheFirstCheckpoint.Stop stop) {
                return new Walk.Outcome(root, stopping.lastProgress, false, "the test stopped this invocation");
            }
        };
    }

    /** Passes everything through, then stops the walk the first time it offers a checkpoint. */
    private static final class StopAtTheFirstCheckpoint implements Walk.Observer {

        private final Walk.Observer delegate;
        private Walk.Progress lastProgress = new Walk.Progress(0, 0, 0, 0);

        private StopAtTheFirstCheckpoint(Walk.Observer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void fileOccurrence(io.algernon.vespera.engine.ledger.OccurrencePath path, long sizeInBytes,
                java.time.Instant lastModified, java.time.Instant creationTime) {
            delegate.fileOccurrence(path, sizeInBytes, lastModified, creationTime);
        }

        @Override
        public void anomaly(String pathRendering, WalkAnomalyKind kind, String detail) {
            delegate.anomaly(pathRendering, kind, detail);
        }

        @Override
        public void checkpoint(Checkpoint at, Walk.Progress progress) {
            delegate.checkpoint(at, progress);
            lastProgress = progress;
            throw new Stop();
        }

        /** Not an error: the way this test says "the machine stopped here". */
        private static final class Stop extends RuntimeException {
            private static final long serialVersionUID = 1L;
        }
    }
}
