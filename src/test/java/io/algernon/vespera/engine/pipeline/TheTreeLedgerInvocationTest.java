package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.TextValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.sqlite.SQLiteConfig;

/**
 * Whole invocations that draw a tree, and the tree's ledger at its top (ADR-429): one SQLite file holding the ledger's
 * rows of the runs the tree was drawn from and the rows its topics, clusters and writing were drawn from, and nothing
 * else of the working store, written whole at every drawing of the tree.
 *
 * <p>The tree is drawn at a named root, the one kind of root ADR-276 section 3 checks for what it holds at the start of
 * each {@code vespera run}, so the second drawing also holds that a root holding its own tree's ledger is accepted. The
 * root is taken away before each test; one static working directory serves the class, as in {@code
 * TheDeliverableRootInvocationTest}, and every claim about a run is scoped to the corpus root this test walked.
 *
 * <p>The test profile's working store is a database in memory, so no file of it exists to copy: what the tree's ledger
 * holds can only have been read as rows through the application's connection, which is what ADR-429 section 4 asks.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("The tree's ledger")
@Issue("900")
@Link(name = "ADR-429", url = Adr.EVERY_TREE_HOLDS_THE_LEDGER_OF_THE_RUNS_IT_WAS_DRAWN_FROM, type = "adr")
class TheTreeLedgerInvocationTest {

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The corpus's two documents, by their paths beneath the corpus root. */
    private static final String FIRST = "corpus.txt";

    private static final String SECOND = "another-corpus-document.txt";

    /** What the tree's ledger is called, at the top of the tree. */
    private static final String THE_TREES_LEDGER = "vespera-run.db";

    /** What SQLite leaves beside a database it has open, and what a write moved into place leaves until it is moved. */
    private static final List<String> LEFT_BESIDE_A_FILE_IN_USE = List.of("-wal", "-shm", "-journal", ".part");

    /** The names a working directory is known by (ADR-196), which nothing in a tree may carry. */
    private static final List<String> A_WORKING_DIRECTORYS_FILES = List.of("vespera.db", "vespera.lock");

    /** The rollback journal SQLite deletes when a transaction ends, which a file written whole and closed is in. */
    private static final String THE_ROLLBACK_JOURNAL = "delete";

    /** The stages whose runs one tree is drawn from, one run of each: eight. */
    private static final Set<String> THE_EIGHT_STAGES = Set.of(
            "byte-level-reduction",
            "extraction",
            "content-census",
            "content-redundancy",
            "seed-measurement",
            "embedding-scoring",
            "arrangement",
            "generation");

    /** The ledger's tables a tree's ledger holds, whose rows are chosen by the tree's walk. */
    private static final List<String> BY_THE_WALK =
            List.of("walk_folder", "walk_folder_not_walked", "walk_folder_not_entered", "file_occurrence");

    /** The tables a tree's ledger holds whose rows are chosen by the runs the tree was drawn from. */
    private static final List<String> BY_THE_RUNS = List.of(
            "run_upstream",
            "finished_step",
            "verdict",
            "topic",
            "topic_passage",
            "seed_feeding_no_topic",
            "winning_topic",
            "see_also",
            "cluster_member",
            "arranged_cluster",
            "topic_name",
            "subtopic_name",
            "cluster_synthesis_doc",
            "cluster_call_exemplar",
            "standing_cluster_fault",
            "topic_overview",
            "topic_overview_source",
            "topic_overview_fault");

    /** Every table the tree's ledger holds, twenty-four: the walk, the runs and the tables above. */
    private static final Set<String> THE_TABLES_IT_HOLDS = Stream.of(List.of("walk", "run"), BY_THE_WALK, BY_THE_RUNS)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(TreeSet::new));

    /** A run of the corpus's walk that no tree was drawn from, the shape of id the ledger mints. */
    private static final String A_RUN_NO_TREE_WAS_DRAWN_FROM = "f".repeat(48) + "0123456789abcdef";

    /** A verdict that removes nothing, written by this test under the first stage's run. */
    private static final String A_KIND_THAT_REMOVES_NOTHING = "passed";

    /** A removal written by this test under the run no tree was drawn from. */
    private static final String A_KIND_THAT_REMOVES = "below-threshold";

    /** A table this test leaves in the tree's ledger between two drawings, which a file written whole does not hold. */
    private static final String A_TABLE_LEFT_BY_THIS_TEST = "left_by_this_test";

    /** The exit code of an invocation that ended as it should: 0. */
    private static final int ENDED = 0;

    @TempDir
    static Path workingDirectory;

    /** Where the named root lies. */
    @TempDir
    static Path base;

    @DynamicPropertySource
    static void workingDirectoryAndDeliverableRoot(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY, () -> deliverableRoot().toString());
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void startFromARootNotMadeYet() throws IOException {
        GenerationScriptedBeans.forgetScriptedAnswers();
        takeAway(deliverableRoot());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load()).seedFolder(TextValue.unset()).build());
    }

    @Test
    @Story("A tree carries the ledger of the runs it was drawn from")
    @DisplayName("A drawn tree holds at its top one closed SQLite file of the walk, the runs and the rows it was drawn from, and nothing else of the working store")
    void aDrawnTreeHoldsTheLedgerOfItsRunsAtItsTop(@TempDir Path corpusRoot, @TempDir Path seeds) throws Exception {
        anApprovedCorpus(corpusRoot, seeds);
        long walk = theCorpusWalk(corpusRoot);
        aVerdictOutsideAndAVerdictInsideTheRunsDrawnFrom(corpusRoot, walk);

        cli.run("run", corpusRoot.toString());

        RunId generation = new RunsOver(jdbcTemplate, corpusRoot).latest("generation");
        List<String> runsDrawnFrom = runsDrawnFrom(generation);
        Path ledger = deliverableRoot().resolve(THE_TREES_LEDGER);
        claim("the invocation that draws the tree exits " + ENDED, () -> assertThat(cli.getExitCode()).isEqualTo(ENDED));
        claim(
                "the tree holds its ledger at its top, and nowhere else: no rendering and no cluster's folder carries one",
                () -> assertThat(everyFileNamed(THE_TREES_LEDGER)).containsExactly(ledger));
        claim(
                "nothing is left beside it, no write-ahead log, shared-memory file, rollback journal or part file: it is"
                        + " one closed file that can be copied alone",
                () -> assertThat(LEFT_BESIDE_A_FILE_IN_USE)
                        .allSatisfy(suffix -> assertThat(ledger.resolveSibling(THE_TREES_LEDGER + suffix)).doesNotExist()));
        claim(
                "and the tree holds no file a working directory is known by, so nothing takes the tree for one",
                () -> assertThat(A_WORKING_DIRECTORYS_FILES)
                        .allSatisfy(name -> assertThat(everyFileNamed(name)).isEmpty()));
        claim(
                "the runs the tree was drawn from are " + THE_EIGHT_STAGES.size() + ", one of each stage, so the claims"
                        + " below are about a whole chain",
                () -> assertThat(stagesOf(runsDrawnFrom)).containsExactlyInAnyOrderElementsOf(THE_EIGHT_STAGES));

        withTheTreesLedger(ledger, file -> {
            claim(
                    "it is in SQLite's rollback journal, not its write-ahead log",
                    () -> assertThat(file.queryForObject("PRAGMA journal_mode", String.class)).isEqualTo(THE_ROLLBACK_JOURNAL));
            claim(
                    "it holds exactly the " + THE_TABLES_IT_HOLDS.size() + " tables of the ledger and of what the tree was"
                            + " drawn from: no cache, no vector, no shingle and no label a person gave",
                    () -> assertThat(tablesIn(file)).containsExactlyInAnyOrderElementsOf(THE_TABLES_IT_HOLDS));
            claim(
                    "its walk is the walk the tree's runs are over, alone: not the seed folder's, nor any other",
                    () -> assertThat(rows(file, "SELECT * FROM walk")).containsExactlyElementsOf(theWorkingStores("walk", "id = ?", walk)));
            claim(
                    "its runs are exactly the generation run and every run upstream of it",
                    () -> assertThat(file.queryForList("SELECT id FROM run", String.class))
                            .containsExactlyInAnyOrderElementsOf(runsDrawnFrom)
                            .doesNotContain(A_RUN_NO_TREE_WAS_DRAWN_FROM));
            claim(
                    "each run's row is the working store's, whole",
                    () -> assertThat(rows(file, "SELECT * FROM run"))
                            .containsExactlyElementsOf(theWorkingStores("run", "id " + inThe(runsDrawnFrom), runsDrawnFrom.toArray())));
            claim(
                    "each table chosen by the walk holds the working store's rows of that walk, whole",
                    () -> assertThat(BY_THE_WALK).allSatisfy(table -> assertThat(rows(file, "SELECT * FROM " + table))
                            .as(table)
                            .containsExactlyElementsOf(theWorkingStores(table, "walk_id = ?", walk))));
            claim(
                    "each table chosen by the runs holds the working store's rows under those runs, whole, and no other run's",
                    () -> assertThat(BY_THE_RUNS).allSatisfy(table -> assertThat(rows(file, "SELECT * FROM " + table))
                            .as(table)
                            .containsExactlyElementsOf(
                                    theWorkingStores(table, "run_id " + inThe(runsDrawnFrom), runsDrawnFrom.toArray()))));
            claim(
                    "so the verdict written under the first stage's run of the tree is in it, and the removal written under a"
                            + " run no tree was drawn from is not",
                    () -> assertThat(file.queryForList("SELECT kind FROM verdict", String.class))
                            .contains(A_KIND_THAT_REMOVES_NOTHING)
                            .doesNotContain(A_KIND_THAT_REMOVES));
            claim(
                    "the tables that write the tree were written: it holds the arrangement's clusters and the writing over them",
                    () -> {
                        assertThat(file.queryForObject("SELECT COUNT(*) FROM arranged_cluster", Long.class)).isPositive();
                        assertThat(file.queryForObject("SELECT COUNT(*) FROM cluster_synthesis_doc", Long.class)).isPositive();
                    });
            claim(
                    "every reference its rows hold resolves inside it",
                    () -> assertThat(file.queryForList("PRAGMA foreign_key_check")).isEmpty());
        });
    }

    @Test
    @Story("A tree carries the ledger of the runs it was drawn from")
    @DisplayName("A tree drawn again in place has its ledger written again whole, and its folder is still accepted as this working directory's tree")
    void aTreeDrawnAgainHasItsLedgerWrittenAgainWhole(@TempDir Path corpusRoot, @TempDir Path seeds) throws Exception {
        anApprovedCorpus(corpusRoot, seeds);
        long walk = theCorpusWalk(corpusRoot);
        cli.run("run", corpusRoot.toString());
        Path ledger = deliverableRoot().resolve(THE_TREES_LEDGER);
        claim(
                "the first drawing leaves the tree's ledger at its top, so the second has one to write over",
                () -> assertThat(ledger).isRegularFile());
        withTheTreesLedgerToWrite(ledger, file -> file.execute("CREATE TABLE " + A_TABLE_LEFT_BY_THIS_TEST + " (x INTEGER)"));

        cli.run("run", corpusRoot.toString());

        List<String> runsDrawnFrom = runsDrawnFrom(new RunsOver(jdbcTemplate, corpusRoot).latest("generation"));
        claim(
                "the second invocation exits " + ENDED + ": a root holding its own tree, its ledger at its top, is accepted at"
                        + " the start and at the drawing",
                () -> assertThat(cli.getExitCode()).isEqualTo(ENDED));
        claim(
                "nothing is left beside the file written again: no rollback journal and no part file",
                () -> assertThat(LEFT_BESIDE_A_FILE_IN_USE)
                        .allSatisfy(suffix -> assertThat(ledger.resolveSibling(THE_TREES_LEDGER + suffix)).doesNotExist()));
        withTheTreesLedger(ledger, file -> {
            claim(
                    "the file was written whole again: the table left in it between the two drawings is gone, and it holds"
                            + " its " + THE_TABLES_IT_HOLDS.size() + " tables and no other",
                    () -> assertThat(tablesIn(file))
                            .containsExactlyInAnyOrderElementsOf(THE_TABLES_IT_HOLDS)
                            .doesNotContain(A_TABLE_LEFT_BY_THIS_TEST));
            claim(
                    "and its runs are the same runs, the tree having been drawn again from the same generation run",
                    () -> assertThat(file.queryForList("SELECT id FROM run", String.class))
                            .containsExactlyInAnyOrderElementsOf(runsDrawnFrom));
            claim(
                    "and its occurrences are the walk's, whole",
                    () -> assertThat(rows(file, "SELECT * FROM file_occurrence"))
                            .containsExactlyElementsOf(theWorkingStores("file_occurrence", "walk_id = ?", walk)));
        });
    }

    /** The folder named as the deliverable root for the whole class. */
    private static Path deliverableRoot() {
        return base.resolve("the deliverable root");
    }

    /** Every file beneath the root called {@code name}, in path order. */
    private static List<Path> everyFileNamed(String name) throws IOException {
        try (Stream<Path> walk = Files.walk(deliverableRoot())) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(name))
                    .sorted()
                    .toList();
        }
    }

    /** Takes {@code folder} and everything beneath it away, where it is there. */
    private static void takeAway(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    /** What the tree's ledger is asked, through a connection of its own opened to read only, and closed after. */
    private static void withTheTreesLedger(Path ledger, ThrowingConsumer<JdbcTemplate> asked) throws Exception {
        SQLiteConfig readOnly = new SQLiteConfig();
        readOnly.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + ledger, readOnly.toProperties())) {
            asked.accept(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        }
    }

    /** What is done to the tree's ledger through a connection of its own that may write, closed after. */
    private static void withTheTreesLedgerToWrite(Path ledger, ThrowingConsumer<JdbcTemplate> done) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + ledger)) {
            done.accept(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        }
    }

    /** The tables a SQLite file holds, but SQLite's own. */
    private static List<String> tablesIn(JdbcTemplate file) {
        return file.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'", String.class);
    }

    /** Every row {@code sql} reads, each as its columns in order, the rows sorted so two sets compare as sets. */
    private static List<String> rows(JdbcTemplate jdbc, String sql, Object... arguments) {
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, arguments)) {
            rows.add(new LinkedHashMap<>(row).toString());
        }
        Collections.sort(rows);
        return rows;
    }

    /** The working store's rows of {@code table} where {@code condition} holds. */
    private List<String> theWorkingStores(String table, String condition, Object... arguments) {
        return rows(jdbcTemplate, "SELECT * FROM " + table + " WHERE " + condition, arguments);
    }

    /** A condition that a value is one of {@code ids}, with a placeholder for each. */
    private static String inThe(List<String> ids) {
        return "IN (" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")";
    }

    /** The generation run and every run reached from it through the runs each names upstream, in the working store. */
    private List<String> runsDrawnFrom(RunId generation) {
        return jdbcTemplate.queryForList(
                "WITH RECURSIVE drawn_from(id) AS (SELECT ? UNION SELECT run_upstream.upstream_run_id FROM run_upstream"
                        + " JOIN drawn_from ON run_upstream.run_id = drawn_from.id) SELECT id FROM drawn_from",
                String.class,
                generation.value());
    }

    /** The stage of each of {@code runs}, in the working store. */
    private List<String> stagesOf(List<String> runs) {
        return jdbcTemplate.queryForList("SELECT stage FROM run WHERE id " + inThe(runs), String.class, runs.toArray());
    }

    /** The walk of the corpus root the runs of this test are over. */
    private long theCorpusWalk(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT walk_id FROM run WHERE id = ?",
                Long.class,
                new RunsOver(jdbcTemplate, corpusRoot).latest("arrangement").value());
    }

    /**
     * Two verdicts against the corpus's first occurrence: one that removes nothing under the first stage's run of the
     * walk, which the tree is drawn from, and a removal under a run of the walk no tree is drawn from, which names no run
     * upstream and so removes nothing from any run the invocation arrives at (ADR-156).
     */
    private void aVerdictOutsideAndAVerdictInsideTheRunsDrawnFrom(Path corpusRoot, long walk) {
        long occurrence = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM file_occurrence WHERE walk_id = ?", Long.class, walk);
        String firstStage = new RunsOver(jdbcTemplate, corpusRoot).latest("byte-level-reduction").value();
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id) VALUES (?, ?, ?, ?, ?)",
                A_RUN_NO_TREE_WAS_DRAWN_FROM,
                "embedding-scoring",
                "written by this test",
                "{}",
                walk);
        jdbcTemplate.update(
                "INSERT INTO verdict (occurrence_id, run_id, kind, reason) VALUES (?, ?, ?, ?)",
                occurrence,
                A_RUN_NO_TREE_WAS_DRAWN_FROM,
                A_KIND_THAT_REMOVES,
                "written by this test");
        jdbcTemplate.update(
                "INSERT INTO verdict (occurrence_id, run_id, kind, reason) VALUES (?, ?, ?, ?)",
                occurrence,
                firstStage,
                A_KIND_THAT_REMOVES_NOTHING,
                "written by this test");
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpus(Path corpusRoot, Path seeds) throws IOException {
        Files.writeString(corpusRoot.resolve(FIRST), "a corpus document");
        Files.writeString(corpusRoot.resolve(SECOND), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
        cli.run("run", corpusRoot.toString());
        String approval = ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, corpusRoot).latest("arrangement"));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    /** What is done with a connection that may throw what JDBC throws. */
    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws SQLException;
    }
}
