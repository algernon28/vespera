package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one call that leaves {@code shingle_by_hash} built over the rows of one stage-2 run, in each state it
 * can find the database in, and the run ids it refuses (ADR-221 sections 1, 3 and 4, #468).
 *
 * <p>The index is told to be a run's by the statement {@code sqlite_master} keeps for it, compared whole with
 * the statement the build issues for that run. Four states follow, and a test for each: no index, this run's,
 * another run's, and the index over every run's rows that a database last run under an earlier build may
 * hold. The run id is written into the statement's text, a partial index's condition taking no bound value,
 * so the call refuses any id that is not in the form {@code RunId.of} mints before it issues anything.
 *
 * <p>Every call goes through {@link TheRunsHashIndex#buildFor}, which is {@code ShingleHashIndex.buildFor}
 * over the test's own database.
 */
@Epic("Redundancy")
@Feature("Shingling")
@Issue("468")
@Link(name = "ADR-221", url = Adr.THE_HASH_INDEX_IS_OVER_THE_ROWS_OF_THE_RUN_IN_HAND, type = "adr")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
class TheHashIndexIsBuiltForOneRunTest {

    /** The shingle rows each of the two runs is given, a number of no significance. */
    private static final int ROWS_A_RUN = 40;

    /** The length of a run id as it is minted: a SHA-256 in hexadecimal. */
    private static final int MINTED_LENGTH = 64;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private RunId thisRun;
    private RunId anotherRun;

    @BeforeEach
    void twoStageTwoRunsWithShingleRowsEach() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-of-two-runs"));
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath("a.pdf"), 1, Instant.EPOCH, Instant.EPOCH);
        OccurrenceId occurrence =
                ledger.occurrences().occurrenceId(walk, new OccurrencePath("a.pdf")).orElseThrow();
        anotherRun = ledger.runs().startRun("extraction", "x1", "{}", walk, List.of());
        thisRun = ledger.runs().startRun("extraction", "x2", "{}", walk, List.of());
        for (RunId run : List.of(anotherRun, thisRun)) {
            for (long hash = 1; hash <= ROWS_A_RUN; hash++) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrence.value(),
                        run.value(),
                        ShingleParameters.DEFAULT.identity(),
                        hash);
            }
        }
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("The index on word-sequence hashes is built for one extraction")
    @DisplayName("With no hash index in the database, the index is built over the rows of the extraction asked for, and the caller is told once that it started and once that it ended")
    void withNoIndexItIsBuiltForTheRun() {
        Recorder recorder = new Recorder();
        long highestRow = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);

        Optional<Duration> took = TheRunsHashIndex.buildFor(jdbcTemplate, thisRun, recorder);

        claim("the two runs made here have ids in the form a run id is minted in, so the claims below are about"
                + " ids the build accepts", () -> assertThat(List.of(thisRun.value(), anotherRun.value()))
                .allMatch(id -> id.matches(TheRunsHashIndex.A_MINTED_RUN_ID)));
        claim("the call says how long building took, which is how its caller knows something was built",
                () -> assertThat(took).isPresent());
        claim(
                "the database then holds the index exactly as the statement for this extraction builds it",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(thisRun.value())));
        claim(
                "the caller is told the build is starting, over up to the " + highestRow + " rows the table's"
                        + " highest row number allows, every run's, since the build reads them all to find"
                        + " this run's; and that it ended: so few rows make no callback between",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                "starting " + SimilarityStatement.SHINGLE_HASH_INDEX_BUILD + " over up to "
                                        + OptionalLong.of(highestRow),
                                "ended " + SimilarityStatement.SHINGLE_HASH_INDEX_BUILD));
    }

    @Test
    @Story("The index on word-sequence hashes is built for one extraction")
    @DisplayName("With the hash index already built for the extraction asked for, nothing is built and nothing is said")
    void withTheRunsOwnIndexNothingIsBuilt() {
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(thisRun.value()));
        Recorder recorder = new Recorder();

        Optional<Duration> took = TheRunsHashIndex.buildFor(jdbcTemplate, thisRun, recorder);

        claim("the call says nothing was built", () -> assertThat(took).isEmpty());
        claim("the caller hears of no statement", () -> assertThat(recorder.calls).isEmpty());
        claim(
                "and the index is the one that was there",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(thisRun.value())));
    }

    @Test
    @Story("The index on word-sequence hashes is built for one extraction")
    @DisplayName("A hash index built for another extraction is removed and built again for the extraction asked for")
    void anotherRunsIndexIsRemovedAndThisRunsBuilt() {
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(anotherRun.value()));
        Recorder recorder = new Recorder();

        Optional<Duration> took = TheRunsHashIndex.buildFor(jdbcTemplate, thisRun, recorder);

        claim("the call says how long building took", () -> assertThat(took).isPresent());
        claim(
                "the index in the database is this extraction's, and the other's is gone: one index of that"
                        + " name, whatever it was built for before",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(thisRun.value())));
        claim(
                "the caller hears of one statement, the build, started once and ended once: the removal is"
                        + " made inside it and has no callback of its own",
                () -> assertThat(recorder.calls).hasSize(2).first().asString().startsWith("starting "
                        + SimilarityStatement.SHINGLE_HASH_INDEX_BUILD));
        claim("no row of either extraction is touched by it", () -> assertThat(
                        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle", Long.class))
                .isEqualTo(2L * ROWS_A_RUN));
    }

    @Test
    @Story("The index on word-sequence hashes is built for one extraction")
    @DisplayName("A hash index over every extraction's rows, as an earlier version left it, is removed and built again for the extraction asked for")
    void theWholeTableIndexOfAnEarlierBuildIsRemovedAndThisRunsBuilt() {
        jdbcTemplate.execute(TheRunsHashIndex.THE_WHOLE_TABLE_FORM);

        Optional<Duration> took = TheRunsHashIndex.buildFor(jdbcTemplate, thisRun, new Recorder());

        claim("the call says how long building took", () -> assertThat(took).isPresent());
        claim(
                "the index in the database is this extraction's: the one over every run's rows is not any"
                        + " run's, so it is treated as another's",
                () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                        .contains(TheRunsHashIndex.statementFor(thisRun.value())));
    }

    @Test
    @Story("The index on word-sequence hashes is built for one extraction")
    @DisplayName("A run id that is not sixty-four lowercase hexadecimal characters is refused before anything is issued, and the index found is left as it was")
    void anIdNotInTheMintedFormIsRefusedAndNothingIsIssued() {
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(anotherRun.value()));
        String quoteAndMore = "' OR run_id <> '";
        List<String> refused = List.of(
                "a-run",
                "a".repeat(MINTED_LENGTH - 1),
                "a".repeat(MINTED_LENGTH + 1),
                "A".repeat(MINTED_LENGTH),
                "a".repeat(MINTED_LENGTH - 1) + "'",
                "a".repeat(MINTED_LENGTH - 1) + "g",
                quoteAndMore + "a".repeat(MINTED_LENGTH - quoteAndMore.length()));

        claim(
                "each id tried below is outside the minted form: one a name, one a character short, one a"
                        + " character long, one in upper case, and three of the right length holding a quote"
                        + " or a letter past f",
                () -> assertThat(refused).noneMatch(id -> id.matches(TheRunsHashIndex.A_MINTED_RUN_ID)));
        claim(
                "while the same length of the letter a, in lower case, is in it, so the form is what the"
                        + " refusals turn on",
                () -> assertThat("a".repeat(MINTED_LENGTH)).matches(TheRunsHashIndex.A_MINTED_RUN_ID));
        for (String id : refused) {
            Recorder recorder = new Recorder();
            Throwable thrown = catchThrowable(() -> TheRunsHashIndex.buildFor(jdbcTemplate, new RunId(id), recorder));
            claim(
                    "the id \"" + id + "\" is refused as an argument that cannot be built for",
                    () -> assertThat(thrown).isInstanceOf(IllegalArgumentException.class));
            claim("with no statement announced for it", () -> assertThat(recorder.calls).isEmpty());
            claim(
                    "and the index that was there, another extraction's, still there as it was: the refusal"
                            + " comes before the removal",
                    () -> assertThat(TheRunsHashIndex.statementInTheDatabase(jdbcTemplate))
                            .contains(TheRunsHashIndex.statementFor(anotherRun.value())));
        }
    }

    /** What the build tells its caller, in the order it tells it. */
    private static final class Recorder implements SimilarityStatementProgress {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(SimilarityStatement statement, OptionalLong rowsUpTo) {
            calls.add("starting " + statement + " over up to " + rowsUpTo);
        }

        @Override
        public void stepsTaken(SimilarityStatement statement, long steps) {
            calls.add("steps " + statement);
        }

        @Override
        public void statementEnded(SimilarityStatement statement) {
            calls.add("ended " + statement);
        }
    }
}
