package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * A tree's ledger that cannot be written (ADR-429 section 4): the drawing fails with the failure said, and nothing of
 * the file being written is left beside its place, neither the part file nor its rollback journal.
 *
 * <p>Each test builds a working store of its own, a database in memory made by {@code schema.sql}, holding one walk, one
 * occurrence of it and one generation run over it, and names only what {@code TreeLedger} offers: its constructor,
 * {@code writeInto} and {@code FILE_NAME}.
 */
@Epic("The deliverable")
@Feature("The tree's ledger")
@Issue("900")
@Link(name = "ADR-429", url = Adr.EVERY_TREE_HOLDS_THE_LEDGER_OF_THE_RUNS_IT_WAS_DRAWN_FROM, type = "adr")
class TreeLedgerTest {

    /** A generation run's id of the shape the ledger mints. */
    private static final RunId A_GENERATION_RUN = new RunId("a".repeat(48) + "0123456789abcdef");

    /** What is left beside a file being written: the part file, and SQLite's rollback journal of it. */
    private static final List<String> LEFT_BESIDE_A_FILE_BEING_WRITTEN = List.of(".part", ".part-journal");

    /** A column the working store's verdict table has and {@code schema.sql}'s does not, so no row of it can be written. */
    private static final String A_VERDICT_TABLE_WITH_A_COLUMN_THE_SCHEMA_LACKS = "CREATE TABLE verdict ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),"
            + " run_id TEXT NOT NULL REFERENCES run (id),"
            + " kind TEXT NOT NULL,"
            + " reason TEXT,"
            + " a_column_the_schema_lacks TEXT)";

    private SingleConnectionDataSource workingStore;

    @AfterEach
    void closeTheWorkingStore() {
        if (workingStore != null) {
            workingStore.destroy();
        }
    }

    @Test
    @Story("A tree's ledger that cannot be written fails the drawing and leaves nothing half written")
    @DisplayName("Where the file's place is a folder holding something, the move is refused, the failure is said, and no part file or journal is left")
    void aPlaceThatCannotBeWrittenOverFailsAndLeavesNothingBehind(@TempDir Path tree) throws Exception {
        JdbcTemplate store = aWorkingStore(List.of());
        Path place = tree.resolve(TreeLedger.FILE_NAME);
        Files.createDirectories(place);
        Files.writeString(place.resolve("held.txt"), "a file in the folder at the file's place", StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> new TreeLedger(store).writeInto(tree, A_GENERATION_RUN));

        claim(
                "writing the tree's ledger fails with an UncheckedIOException, which fails the drawing",
                () -> assertThat(failure).isInstanceOf(UncheckedIOException.class));
        claim(
                "and neither the part file nor its rollback journal is left beside the file's place",
                () -> assertThat(LEFT_BESIDE_A_FILE_BEING_WRITTEN)
                        .allSatisfy(suffix -> assertThat(tree.resolve(TreeLedger.FILE_NAME + suffix)).doesNotExist()));
        claim(
                "and the folder at the file's place is as it was",
                () -> assertThat(place.resolve("held.txt")).hasContent("a file in the folder at the file's place"));
    }

    @Test
    @Story("A tree's ledger that cannot be written fails the drawing and leaves nothing half written")
    @DisplayName("Where a row of the working store cannot be written into the file, the failure is said partway, and no part file or journal is left")
    void aRowThatCannotBeWrittenFailsPartwayAndLeavesNothingBehind(@TempDir Path tree) throws Exception {
        JdbcTemplate store = aWorkingStore(List.of(A_VERDICT_TABLE_WITH_A_COLUMN_THE_SCHEMA_LACKS));
        long occurrence = store.queryForObject("SELECT MIN(id) FROM file_occurrence", Long.class);
        store.update(
                "INSERT INTO verdict (occurrence_id, run_id, kind, reason, a_column_the_schema_lacks) VALUES (?, ?, ?, ?, ?)",
                occurrence,
                A_GENERATION_RUN.value(),
                "passed",
                "written by this test",
                "a value the file has no column for");

        Throwable failure = catchThrowable(() -> new TreeLedger(store).writeInto(tree, A_GENERATION_RUN));

        claim(
                "writing the tree's ledger fails with an UncheckedIOException, which fails the drawing, and it is the row"
                        + " carrying the column the file lacks that failed it",
                () -> assertThat(failure)
                        .isInstanceOf(UncheckedIOException.class)
                        .hasStackTraceContaining("a_column_the_schema_lacks"));
        claim(
                "and neither the part file nor its rollback journal is left beside the file's place",
                () -> assertThat(LEFT_BESIDE_A_FILE_BEING_WRITTEN)
                        .allSatisfy(suffix -> assertThat(tree.resolve(TreeLedger.FILE_NAME + suffix)).doesNotExist()));
        claim(
                "and no file was moved into the file's place",
                () -> assertThat(tree.resolve(TreeLedger.FILE_NAME)).doesNotExist());
    }

    /**
     * A working store in memory: {@code first}'s statements, then {@code schema.sql}, whose {@code CREATE TABLE IF NOT
     * EXISTS} leaves a table {@code first} made as it is; then one finished walk, one occurrence of it, and the
     * generation run over it.
     */
    private JdbcTemplate aWorkingStore(List<String> first) {
        workingStore = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate store = new JdbcTemplate(workingStore);
        first.forEach(store::execute);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(workingStore);
        store.update("INSERT INTO walk (root, finished) VALUES (?, 1)", "C:\\a corpus root");
        long walk = store.queryForObject("SELECT MAX(id) FROM walk", Long.class);
        store.update(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time) VALUES (?, ?, ?, ?, ?)",
                walk,
                "a document.txt",
                1,
                "2026-11-19T00:00:00Z",
                "2026-11-19T00:00:00Z");
        store.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id) VALUES (?, ?, ?, ?, ?)",
                A_GENERATION_RUN.value(),
                "generation",
                "written by this test",
                "{}",
                walk);
        return store;
    }
}
