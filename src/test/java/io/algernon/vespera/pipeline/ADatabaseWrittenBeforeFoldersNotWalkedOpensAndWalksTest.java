package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.Walk;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;

/**
 * A working directory's database written before the profile could name folders not walked opens and walks with them,
 * no upgrade asked of it (ADR-340 §8.3): what ADR-340 adds to the ledger is tables of their own, which the start-up's
 * {@code schema.sql} builds where they are missing, as ADR-334 §5.3 added {@code walk_folder}, and no column of a table
 * the database already holds, which its {@code CREATE TABLE IF NOT EXISTS} would leave as it was.
 *
 * <p>The database is built as one written before is: its {@code walk} table in the shape it had then, holding a walk,
 * then {@code schema.sql} run over it as the start-up runs it, then a census of a corpus whose profile names a folder
 * not walked.
 */
@Epic("Census")
@Feature("Walk")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class ADatabaseWrittenBeforeFoldersNotWalkedOpensAndWalksTest {

    /** The {@code walk} table as a database written before ADR-340 holds it, word for word as it was created then. */
    private static final String THE_WALK_TABLE_BEFORE = """
            CREATE TABLE walk (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                root TEXT NOT NULL,
                finished INTEGER NOT NULL DEFAULT 0,
                checkpoint_ordinals TEXT,
                checkpoint_path TEXT,
                entries_seen INTEGER NOT NULL DEFAULT 0,
                directories_entered INTEGER NOT NULL DEFAULT 0
            )""";

    /** A walk the database recorded before, of another root, finished. */
    private static final String AN_EARLIER_WALK = "INSERT INTO walk (root, finished, entries_seen, directories_entered)"
            + " VALUES ('/an/earlier/root', 1, 3, 1)";

    private static final Instant CENSUS_RAN_AT = Instant.parse("2026-11-04T09:00:00Z");

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A database written before folders could be left unread opens, with no upgrade, and walks a corpus leaving one unread")
    void aDatabaseWrittenBeforeOpensAndWalks(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = Files.createDirectory(temp.resolve("archivio"));
        write(root, "notes/appunti.txt", "a note about the archive\n");
        write(root, "old/flows/flow.ok", "a flow file nobody wants read\n");
        Files.writeString(workingDirectory.resolve("profile.yaml"), """
                ---
                foldersNotWalked:
                  value: 'old/flows'
                  provenance: "set by this test"
                  measurement: null
                """, StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(THE_WALK_TABLE_BEFORE);
                statement.execute(AN_EARLIER_WALK);
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            WalkRecorder walkRecorder = new WalkRecorder(new Ledger(jdbcTemplate), new AnomalyLog(jdbcTemplate),
                    new JdbcTransactionManager(dataSource));

            new CensusTasklet(walkRecorder, new ProfileStore(workingDirectory),
                    Clock.fixed(CENSUS_RAN_AT, ZoneOffset.UTC), root).execute(null, null);

            List<String> walks = jdbcTemplate.queryForList("SELECT root || ' ' || finished FROM walk ORDER BY id",
                    String.class);
            List<String> paths = jdbcTemplate.queryForList(
                    "SELECT path FROM file_occurrence ORDER BY path", String.class);
            List<String> notEntered = jdbcTemplate.queryForList("SELECT path FROM walk_folder_not_entered",
                    String.class);

            claim("the earlier walk is read as it was, and the census's walk of the corpus root finished beside it",
                    () -> assertThat(walks).containsExactly("/an/earlier/root 1",
                            Walk.canonicalRoot(root) + " 1"));
            claim("the walk recorded the note, and nothing below the folder not read",
                    () -> assertThat(paths).containsExactly("notes/appunti.txt"));
            claim("and the folder it met and did not enter is recorded in a table the start-up built",
                    () -> assertThat(notEntered).containsExactly("old/flows"));
        }
    }

    private static void write(Path folder, String path, String text) throws IOException {
        Path file = folder.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
