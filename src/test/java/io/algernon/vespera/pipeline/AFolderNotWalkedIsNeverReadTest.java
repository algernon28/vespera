package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.File;
import java.util.List;
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

/**
 * A folder the profile's {@code foldersNotWalked} lists is never read (ADR-340 §8): the census counts it as an entry of
 * the walk and does not enter it, so nothing below it is recorded, and the walk still accounts for every entry it met;
 * the list the walk was given is recorded with it, and a walk given another list is another walk.
 *
 * <p>Assembled by hand as {@code ACodeSetFolderOutsideTheCorpusRootIsWalkedWithTheCorpusTest} is. The profile is
 * written as text and the tables read by their names, so this compiles before {@code Profile} has the key; until it
 * does, the census fails on the first load of a profile naming it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-056", url = Adr.EXCLUDES_NOTHING_IS_RECONCILED, type = "adr")
class AFolderNotWalkedIsNeverReadTest {

    private static final Instant CENSUS_RAN_AT = Instant.parse("2026-11-04T09:00:00Z");

    /** A note the corpus keeps, outside the folder not walked. */
    private static final String THE_NOTE = "notes/appunti.txt";

    /** A file beside the folder not walked, in the folder above it, which is read. */
    private static final String BESIDE_IT = "old/kept.txt";

    /** The folder the profile says is not walked, by its path from the corpus root. */
    private static final String NOT_WALKED = "old/flows";

    /** Two files below the folder not walked, one in a folder of its own, neither of which may be read. */
    private static final List<String> BELOW_IT = List.of("old/flows/flow.ok", "old/flows/deeper/flow2.ok");

    /** The entries below the root a walk leaving out the folder meets: notes, its note, old, its file, and the folder. */
    private static final long ENTRIES_BELOW_THE_ROOT = 5;

    /** The folders that walk enters: the root, notes and old. */
    private static final long FOLDERS_ENTERED = 3;

    /** The files of the corpus, each read where no folder is left out: the note, the file beside, and two below. */
    private static final long FILES_OF_THE_CORPUS = 4;

    /** The walks recorded after two censuses that were given two different lists. */
    private static final long TWO_WALKS = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("The walk never enters a folder the profile lists as not read, counts it, and still accounts for every entry")
    void theWalkNeverEntersAFolderTheProfileSaysIsNotWalked(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = theCorpus(temp);
        theProfileListsNotWalked(workingDirectory, NOT_WALKED);

        census(root, workingDirectory);

        claim("the walk recorded the note and the file beside the folder, and nothing below the folder",
                () -> assertThat(jdbcTemplate.queryForList("SELECT path FROM file_occurrence ORDER BY path", String.class))
                        .containsExactly(THE_NOTE, BESIDE_IT));
        claim("the walk finished, having met five entries below the root (notes, its note, old, its file and the"
                        + " folder not read) and entered three folders (the root, notes and old): two occurrences, no"
                        + " anomaly, three folders entered less the root and one not walked account for the five",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT finished || ' ' || entries_seen || ' '"
                                + " || directories_entered FROM walk", String.class))
                        .isEqualTo("1 " + ENTRIES_BELOW_THE_ROOT + " " + FOLDERS_ENTERED));
        claim("the one folder it met and did not enter is recorded as a row of its own, by its path",
                () -> assertThat(jdbcTemplate.queryForList("SELECT path FROM walk_folder_not_entered", String.class))
                        .containsExactly(NOT_WALKED));
        claim("and the list it was given is recorded with it, the folder first and alone",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT ordinal || ' ' || folder FROM walk_folder_not_walked ORDER BY ordinal", String.class))
                        .containsExactly("1 " + NOT_WALKED));
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A folder whose name only begins with the folder listed is read: folders are compared name by name")
    void aFolderNotWalkedIsComparedElementByElement(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = theCorpus(temp);
        theProfileListsNotWalked(workingDirectory, "old/flow");

        census(root, workingDirectory);

        claim("old/flow names no folder of the corpus, so every file is read, old/flows among them",
                () -> assertThat(jdbcTemplate.queryForList("SELECT path FROM file_occurrence ORDER BY path", String.class))
                        .containsExactly(THE_NOTE, "old/flows/deeper/flow2.ok", "old/flows/flow.ok", BESIDE_IT));
        claim("and no folder was met as one not walked",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk_folder_not_entered", Long.class))
                        .isZero());
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A list naming a folder outside the corpus root cannot be read, and then every folder is read")
    void aPieceThatIsNoFolderBelowTheCorpusRootReadsNothingOfTheValue(@TempDir Path temp, @TempDir Path workingDirectory)
            throws Exception {
        Path root = theCorpus(temp);
        theProfileListsNotWalked(workingDirectory, NOT_WALKED + File.pathSeparator + "../elsewhere");

        census(root, workingDirectory);

        claim("one piece climbs out of the corpus root, so none of the value is read: old/flows is read too",
                () -> assertThat(jdbcTemplate.queryForList("SELECT path FROM file_occurrence ORDER BY path", String.class))
                        .containsExactly(THE_NOTE, "old/flows/deeper/flow2.ok", "old/flows/flow.ok", BESIDE_IT));
        claim("and the walk records no folder not walked",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk_folder_not_walked", Long.class))
                        .isZero());
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A walk given another list of folders not read is another walk, though it reads the same files")
    void aWalkGivenAnotherListIsAnotherWalk(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = theCorpus(temp);
        theProfileListsNotWalked(workingDirectory, "gone");
        census(root, workingDirectory);
        Files.delete(workingDirectory.resolve("profile.yaml"));
        census(root, workingDirectory);
        // The walks by their order, not their ids, which a database shared with other classes hands out as it goes.
        List<Long> walks = jdbcTemplate.queryForList("SELECT id FROM walk ORDER BY id", Long.class);
        List<Long> filesOfEach = walks.stream().map(walk -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_occurrence WHERE walk_id = ?", Long.class, walk)).toList();
        List<List<String>> listOfEach = walks.stream().map(walk -> jdbcTemplate.queryForList(
                "SELECT folder FROM walk_folder_not_walked WHERE walk_id = ? ORDER BY ordinal", String.class, walk))
                .toList();

        claim("the second walk, given no list, is kept as another walk, not discarded as a repetition",
                () -> assertThat(walks).hasSize((int) TWO_WALKS));
        claim("the first list names a folder that is not there, so each walk read every file of the corpus",
                () -> assertThat(filesOfEach).containsExactly(FILES_OF_THE_CORPUS, FILES_OF_THE_CORPUS));
        claim("the first walk recorded its list and the second none",
                () -> assertThat(listOfEach).containsExactly(List.of("gone"), List.of()));
    }

    /** A corpus of a note, a folder of old flows with a file beside it, and two files below the flows. */
    private static Path theCorpus(Path temp) throws IOException {
        Path root = Files.createDirectory(temp.resolve("archivio"));
        write(root, THE_NOTE, "a note about the archive\n");
        write(root, BESIDE_IT, "a file beside the folder not read, which is read\n");
        for (String below : BELOW_IT) {
            write(root, below, "a flow file nobody wants read\n");
        }
        return root;
    }

    /** A profile listing {@code folders} as not walked, with its provenance. */
    private static void theProfileListsNotWalked(Path workingDirectory, String folders) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), """
                ---
                foldersNotWalked:
                  value: '%s'
                  provenance: "set by this test"
                  measurement: null
                """.formatted(folders), StandardCharsets.UTF_8);
    }

    private void census(Path root, Path workingDirectory) throws Exception {
        WalkRecorder walkRecorder = new WalkRecorder(
                new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
        new CensusTasklet(walkRecorder, new ProfileStore(workingDirectory), Clock.fixed(CENSUS_RAN_AT, ZoneOffset.UTC), root)
                .execute(null, null);
    }

    private static void write(Path folder, String path, String text) throws IOException {
        Path file = folder.resolve(path);
        Files.createDirectories(file.getParent() == null ? folder : file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
