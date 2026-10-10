package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code similarity} tells its caller around each of its statements, and in which order (ADR-193
 * sections 6 to 8, ADR-204 section 4, #411): {@code statementStarting} once before the statement, with its
 * total for a counted one and an empty total for a timed one; {@code stepsTaken} at each callback of SQLite's
 * handler; {@code statementEnded} once after it, on every path but one that throws.
 *
 * <p>The statements are the build of {@code shingle_by_hash}; {@code DocumentFrequency.measure}'s grouping of
 * the shingle rows, in the database since ADR-211, one statement, counted by SQLite's steps like the read it
 * replaces though it sorts; and the two of {@code RedundancyResolution.resolve} that stay reads of their own
 * since ADR-220: the signed occurrences, counted, and the near-duplicates' extraction metrics, timed. The
 * signature bands and the shingle document frequencies, each timed until ADR-220, are read a page of signed
 * occurrences or an occurrence at a time, inside the loops ADR-192 section 5 already reports.
 *
 * <p>It runs on {@link PoolOfTwo}, so a counted read handed back to the template would report no steps over
 * many rows.
 */
@Epic("Redundancy")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class SimilarityStatementProgressOrderTest {

    private static final double NO_BOILERPLATE_FLOOR = 0.9;

    /** Measuring reports one statement, started once and ended once: two callbacks about statements. */
    private static final int A_START_AND_AN_END = 2;

    /** The two documents the first test's walk holds, one page of them. */
    private static final int TWO_OCCURRENCES = 2;

    /** The interval ADR-193 section 2 fixes. */
    private static final long EVERY_HUNDRED_THOUSAND_STEPS = 100_000L;

    /** Enough signature rows, at 5 steps a row, for at least two callbacks of SQLite's handler. */
    private static final int MANY = 50_000;

    /** A hundred shingles a document: enough for a signature, and for two documents one apart to share a band. */
    private static final int A_HUNDRED_SHINGLES = 100;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private Ledger ledger;
    private WalkId walk;
    private RunId stage2;
    private RunId stage3;
    private RunId stage4;

    @BeforeEach
    void aPoolOfTwoAndThreeRunsOverOneWalk() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        ledger = new Ledger(jdbcTemplate);
        walk = ledger.walks().startWalk(Path.of("C:/corpus-statements"));
        stage2 = ledger.runs().startRun("extraction", "x1", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "y1", "{}", walk, List.of(stage2));
        stage4 = ledger.runs().startRun("content-redundancy", "z1", "{}", walk, List.of(stage3));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    /**
     * ADR-211 sections 3 and 9: the shingle rows are grouped by the database in one statement, started with the
     * span of the run's rows for ADR-191's line and ended; no drain of the survivors comes before it; and the
     * check of the walk's occurrences against the ledger follows, a page at a time. Two hundred rows take far
     * fewer than the 100,000 steps at which SQLite first calls back, so no steps come between.
     */
    @Test
    @Story("Measuring document frequency says what it is reading")
    @DisplayName("Measuring starts its grouping of the shingle rows with their span and ends it, with nothing before it, and then checks the collection's documents a page at a time")
    @Issue("456")
    @Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
    void measuringStartsAndEndsItsGroupingAndThenChecksTheOccurrences() {
        document("a.pdf", 0);
        document("b.pdf", 1);
        long shingleRows = spanOf("shingle", stage2);
        Recorder recorder = new Recorder();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the grouping of the shingle rows is the first thing the caller hears of, started over up to the "
                        + shingleRows + " rows of stage 2's run and ended, so few rows making no callback between;"
                        + " then the one page of the walk's " + TWO_OCCURRENCES + " documents is checked; and"
                        + " nothing else is reported",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(SimilarityStatement.SHINGLE_ROWS, OptionalLong.of(shingleRows)),
                                ended(SimilarityStatement.SHINGLE_ROWS),
                                "occurrencesChecked(" + TWO_OCCURRENCES + ")"));
        claim(
                "the callbacks about statements are the " + A_START_AND_AN_END + " above",
                () -> assertThat(recorder.statements()).hasSize(A_START_AND_AN_END));
    }

    /**
     * ADR-211 section 9: the grouping is one statement that SQLite counts, though it sorts. Its callbacks come
     * while it reads the rows and while it counts them sorted, so over many rows the caller is told the steps
     * between the start and the end. The rows are of an occurrence no walk holds, so nothing is checked after.
     */
    @Test
    @Story("Measuring document frequency says what it is reading")
    @DisplayName("The grouping of many shingle rows reports its steps between its start and its end, in one statement")
    @Issue("456")
    @Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
    void theGroupingOfManyRowsReportsItsSteps() throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                            + " VALUES (?, ?, ?, ?)")) {
                for (int row = 1; row <= MANY; row++) {
                    insert.setLong(1, AN_OCCURRENCE_NO_WALK_HOLDS);
                    insert.setString(2, stage2.value());
                    insert.setString(3, ShingleParameters.DEFAULT.identity());
                    insert.setLong(4, row);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        Recorder recorder = new Recorder();

        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the caller is told first that the grouping is starting, over up to " + MANY + " rows, and last"
                        + " that it ended: the walk holds no document, so nothing is checked after it",
                () -> {
                    assertThat(recorder.calls)
                            .first()
                            .isEqualTo(starting(SimilarityStatement.SHINGLE_ROWS, OptionalLong.of(MANY)));
                    assertThat(recorder.calls).last().isEqualTo(ended(SimilarityStatement.SHINGLE_ROWS));
                });
        claim(
                "and between them only the steps SQLite took, a hundred thousand more each time, at least twice:"
                        + " one statement, counted from its first step to its last, the sorting included",
                () -> {
                    List<String> between = recorder.calls.subList(1, recorder.calls.size() - 1);
                    assertThat(between).hasSizeGreaterThanOrEqualTo(2);
                    for (int i = 0; i < between.size(); i++) {
                        assertThat(between.get(i))
                                .isEqualTo("stepsTaken(" + SimilarityStatement.SHINGLE_ROWS + ", "
                                        + (i + 1) * EVERY_HUNDRED_THOUSAND_STEPS + ")");
                    }
                });
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    @Test
    @Story("Resolving redundancy says what it is reading")
    @DisplayName("Resolving starts and ends its two reads where it makes them, and reads the bands and the shingle frequencies inside its loops")
    @Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
    void resolvingStartsAndEndsEachReadWhereItMakesIt() {
        document("a.pdf", 0);
        document("b.pdf", 1);
        document("c.pdf", 10_000);
        new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2);
        signEveryDocument();
        long signatureRows = spanOf("minhash_signature", stage4);
        Recorder recorder = new Recorder();

        new RedundancyResolution(jdbcTemplate, ledger).resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), recorder);

        claim(
                "two reads are reported, once each, in the order resolution makes them: the count of the signed"
                        + " documents, over up to the " + signatureRows + " signature rows of its run, and the"
                        + " near-duplicates' metrics, with no total. The bands and the shingle frequencies are read a"
                        + " page of signed documents or a document at a time inside the loops that report, so neither"
                        + " is a read of its own (ADR-220)",
                () -> assertThat(recorder.statements())
                        .containsExactly(
                                starting(SimilarityStatement.SIGNED_OCCURRENCES, OptionalLong.of(signatureRows)),
                                ended(SimilarityStatement.SIGNED_OCCURRENCES),
                                starting(SimilarityStatement.NEAR_DUPLICATE_METRICS, OptionalLong.empty()),
                                ended(SimilarityStatement.NEAR_DUPLICATE_METRICS)));
        claim(
                "the signed documents are counted before the first loop is announced, the near-duplicates' metrics"
                        + " are read after their loop is announced and before the first of them is reported read,"
                        + " and the loops come in the order resolution goes through them",
                () -> assertThat(recorder.calls)
                        .containsSubsequence(
                                ended(SimilarityStatement.SIGNED_OCCURRENCES),
                                "toScorePairs",
                                "toReadProfiles",
                                starting(SimilarityStatement.NEAR_DUPLICATE_METRICS, OptionalLong.empty()),
                                ended(SimilarityStatement.NEAR_DUPLICATE_METRICS),
                                "profileRead",
                                "toCheckForContainment"));
    }

    @Test
    @Story("Resolving redundancy says what it is reading")
    @DisplayName("With nothing signed, the read of the signed occurrences is started with an empty total and ended, and nothing follows")
    void withNothingSignedOnlyTheFirstReadIsReported() {
        Recorder recorder = new Recorder();

        new RedundancyResolution(jdbcTemplate, ledger).resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), recorder);

        claim(
                "the one read is started with an empty total and ended, and resolution returns: no later read is"
                        + " made and no loop is announced",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(SimilarityStatement.SIGNED_OCCURRENCES, OptionalLong.empty()),
                                ended(SimilarityStatement.SIGNED_OCCURRENCES)));
    }

    /**
     * The recorder stops the resolution once the read has ended: what follows it would need fifty thousand
     * real documents, and the claim is about the read alone.
     */
    @Test
    @Story("Resolving redundancy says what it is reading")
    @DisplayName("The read of the signed occurrences over many rows reports its steps between its start and its end")
    void theReadOfTheSignedOccurrencesReportsItsSteps() throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO minhash_signature"
                    + " (occurrence_id, run_id, signature_identity, signature) VALUES (?, ?, 'identity', x'00')")) {
                for (int row = 1; row <= MANY; row++) {
                    insert.setLong(1, 1_000_000L + row);
                    insert.setString(2, stage4.value());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        Recorder recorder = new Recorder() {
            @Override
            public void statementEnded(SimilarityStatement statement) {
                super.statementEnded(statement);
                throw new StoppedByTheTest();
            }
        };

        assertThatThrownBy(() -> new RedundancyResolution(jdbcTemplate, ledger)
                        .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), recorder))
                .isInstanceOf(StoppedByTheTest.class);

        claim(
                "the caller is told first that the read is starting, over up to " + MANY + " rows, and last that it"
                        + " ended",
                () -> {
                    assertThat(recorder.calls)
                            .first()
                            .isEqualTo(starting(SimilarityStatement.SIGNED_OCCURRENCES, OptionalLong.of(MANY)));
                    assertThat(recorder.calls).last().isEqualTo(ended(SimilarityStatement.SIGNED_OCCURRENCES));
                });
        claim(
                "and between them only the steps SQLite took, a hundred thousand more each time, at least twice:"
                        + " with a second connection in the pool and no transaction, the read ran on the one the"
                        + " handler was set on",
                () -> {
                    List<String> between = recorder.calls.subList(1, recorder.calls.size() - 1);
                    assertThat(between).hasSizeGreaterThanOrEqualTo(2);
                    for (int i = 0; i < between.size(); i++) {
                        assertThat(between.get(i))
                                .isEqualTo("stepsTaken(" + SimilarityStatement.SIGNED_OCCURRENCES + ", "
                                        + (i + 1) * EVERY_HUNDRED_THOUSAND_STEPS + ")");
                    }
                });
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    @Test
    @Story("Building the index over the shingles says how far it has gone")
    @DisplayName("The build is started with the highest row number of the shingle table and ended, once each")
    void theBuildIsStartedWithItsTotalAndEnded() {
        document("a.pdf", 0);
        long highestRow = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        List<String> calls = new ArrayList<>();

        // Built for the stage-2 run, the one call ADR-221 leaves; the total is still the table's highest row
        // number, every run's rows being read to find this run's.
        TheRunsHashIndex.buildFor(jdbcTemplate, stage2, new SimilarityStatementProgress() {
            @Override
            public void statementStarting(SimilarityStatement statement, OptionalLong rowsUpTo) {
                calls.add(starting(statement, rowsUpTo));
            }

            @Override
            public void statementEnded(SimilarityStatement statement) {
                calls.add(ended(statement));
            }
        });

        claim(
                "the build is started over up to the " + highestRow + " rows the table's highest row number"
                        + " allows, and ended: so few rows make no callback between",
                () -> assertThat(calls)
                        .containsExactly(
                                starting(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, OptionalLong.of(highestRow)),
                                ended(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD)));
    }

    /**
     * ADR-204 section 4, "on every path but one that throws", for a counted statement of {@code similarity}:
     * the table is dropped once the read has been announced, so the read itself is what fails.
     */
    @Test
    @Story("Resolving redundancy says what it is reading")
    @DisplayName("A read of the signed occurrences that throws is not said to have ended, and leaves no handler behind")
    void aCountedReadThatThrowsIsNotSaidToHaveEnded() throws SQLException {
        oneSignatureRow();
        Recorder recorder = new DroppingATableWhenStarted(SimilarityStatement.SIGNED_OCCURRENCES, "minhash_signature");

        claim(
                "resolution fails as the template reports any statement's failure, once the table is gone",
                () -> assertThatThrownBy(() -> new RedundancyResolution(jdbcTemplate, ledger)
                                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the read was starting, over the " + ONE_ROW + " row the run held, and never"
                        + " that it ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(SimilarityStatement.SIGNED_OCCURRENCES, OptionalLong.of(ONE_ROW))));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    /**
     * A page of the signature bands that throws, which since ADR-220 is no read of its own but a statement of the
     * loop over the signed documents: the count was started and ended, the loop announced, and nothing after.
     */
    @Test
    @Story("Resolving redundancy says what it is reading")
    @DisplayName("A page of the signature bands that throws stops resolution inside its loop, after the count of the signed documents ended")
    @Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
    void aTimedReadThatThrowsIsNotSaidToHaveEnded() throws SQLException {
        oneSignatureRow();
        Recorder recorder = new Recorder() {
            @Override
            public void toScorePairs(long total) {
                super.toScorePairs(total);
                jdbcTemplate.execute("DROP TABLE signature_band");
            }
        };

        claim(
                "resolution fails as the template reports any statement's failure, once the table is gone",
                () -> assertThatThrownBy(() -> new RedundancyResolution(jdbcTemplate, ledger)
                                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(jdbcTemplate), recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the count of the signed documents started and ended, and that the loop over"
                        + " their candidates began, and nothing more",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(SimilarityStatement.SIGNED_OCCURRENCES, OptionalLong.of(ONE_ROW)),
                                ended(SimilarityStatement.SIGNED_OCCURRENCES),
                                "toScorePairs"));
    }

    /** The one signature row the two tests of a read that throws give the run, so resolution has something signed. */
    private static final int ONE_ROW = 1;

    /** An occurrence number far above any this fixture's walk holds; foreign keys are not enforced on this pool. */
    private static final long AN_OCCURRENCE_NO_WALK_HOLDS = 1_000_000L;

    private void oneSignatureRow() throws SQLException {
        try (Connection connection = pool.connection();
                PreparedStatement insert = connection.prepareStatement("INSERT INTO minhash_signature"
                        + " (occurrence_id, run_id, signature_identity, signature) VALUES (?, ?, 'identity', x'00')")) {
            insert.setLong(1, AN_OCCURRENCE_NO_WALK_HOLDS);
            insert.setString(2, stage4.value());
            insert.executeUpdate();
        }
    }

    /** A recorder that drops {@code table} when {@code statement} is announced, so that statement is what fails. */
    private final class DroppingATableWhenStarted extends Recorder {

        private final SimilarityStatement statement;
        private final String table;

        DroppingATableWhenStarted(SimilarityStatement statement, String table) {
            this.statement = statement;
            this.table = table;
        }

        @Override
        public void statementStarting(SimilarityStatement started, OptionalLong rowsUpTo) {
            super.statementStarting(started, rowsUpTo);
            if (started == statement) {
                jdbcTemplate.execute("DROP TABLE " + table);
            }
        }
    }

    private static String starting(SimilarityStatement statement, OptionalLong rowsUpTo) {
        return "statementStarting(" + statement + ", " + rowsUpTo + ")";
    }

    private static String ended(SimilarityStatement statement) {
        return "statementEnded(" + statement + ")";
    }

    /** What stops a resolution this test does not need the rest of. */
    private static final class StoppedByTheTest extends RuntimeException {}

    /**
     * Every callback, in the order it came: a statement's as the call it was, a loop's as the name of its
     * method, which is all the claims here need of a loop.
     */
    private static class Recorder implements FrequencyProgress, ResolutionProgress {

        final List<String> calls = new ArrayList<>();

        List<String> statements() {
            return calls.stream().filter(call -> call.startsWith("statement")).toList();
        }

        @Override
        public void statementStarting(SimilarityStatement statement, OptionalLong rowsUpTo) {
            calls.add(starting(statement, rowsUpTo));
        }

        @Override
        public void stepsTaken(SimilarityStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        @Override
        public void statementEnded(SimilarityStatement statement) {
            calls.add(ended(statement));
        }

        /** Written without {@code @Override}: it is gone once ADR-211 is built. */
        public void toGoThrough(long hashes) {
            calls.add("toGoThrough");
        }

        /** Written without {@code @Override}: it is gone once ADR-211 is built. */
        public void hashGoneThrough() {
            calls.add("hashGoneThrough");
        }

        /** ADR-211's callback, written without {@code @Override} so the class compiles before it exists. */
        public void occurrencesChecked(int occurrences) {
            calls.add("occurrencesChecked(" + occurrences + ")");
        }

        @Override
        public void toScorePairs(long pairs) {
            calls.add("toScorePairs");
        }

        @Override
        public void candidatesScored() {
            calls.add("candidatesScored");
        }

        @Override
        public void toReadProfiles(long occurrences) {
            calls.add("toReadProfiles");
        }

        @Override
        public void profileRead() {
            calls.add("profileRead");
        }

        @Override
        public void toResolveComponents(long components) {
            calls.add("toResolveComponents");
        }

        @Override
        public void componentResolved() {
            calls.add("componentResolved");
        }

        @Override
        public void toWriteNearDuplicateVerdicts(long members) {
            calls.add("toWriteNearDuplicateVerdicts");
        }

        @Override
        public void nearDuplicateVerdictWritten() {
            calls.add("nearDuplicateVerdictWritten");
        }

        @Override
        public void toCheckForContainment(long occurrences) {
            calls.add("toCheckForContainment");
        }

        @Override
        public void checkedForContainment() {
            calls.add("checkedForContainment");
        }

        @Override
        public void containmentCandidateGoneThrough() {
            calls.add("containmentCandidateGoneThrough");
        }
    }

    /** A document of a hundred shingle hashes from {@code from} on, with the metric row the survivor rule reads. */
    private void document(String path, long from) {
        ledger.occurrences().fileOccurrence(
                walk, new OccurrencePath(path), 1, Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2021-01-01T00:00:00Z"));
        long occurrence = ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value();
        for (long hash = from; hash < from + A_HUNDRED_SHINGLES; hash++) {
            jdbcTemplate.update(
                    "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                            + " VALUES (?, ?, ?, ?)",
                    occurrence,
                    stage2.value(),
                    ShingleParameters.DEFAULT.identity(),
                    hash);
        }
        jdbcTemplate.update(
                "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time, character_count,"
                        + " alphanumeric_char_count, word_count, word_character_length_total,"
                        + " vowelless_word_count, single_character_word_count)"
                        + " VALUES (?, ?, 'success', 0.5, 10000, 10000, 1, 1, 0, 0)",
                occurrence,
                stage2.value());
    }

    private void signEveryDocument() {
        RedundancySignatures signatures = new RedundancySignatures(jdbcTemplate);
        for (Long occurrence : jdbcTemplate.queryForList(
                "SELECT DISTINCT occurrence_id FROM shingle WHERE run_id = ?", Long.class, stage2.value())) {
            signatures.write(new OccurrenceId(occurrence), stage4, stage2, Set.of(), NO_BOILERPLATE_FLOOR);
        }
    }

    /** The span of {@code run}'s rowids in {@code table}: greatest less least plus one. */
    private long spanOf(String table, RunId run) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) - MIN(rowid) + 1 FROM " + table + " WHERE run_id = ?", Long.class, run.value());
    }
}
