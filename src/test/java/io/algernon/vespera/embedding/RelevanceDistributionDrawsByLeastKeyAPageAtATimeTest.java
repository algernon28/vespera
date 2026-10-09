package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The relevance report's spread and sample, read a page of scores at a time and holding no list of the scored
 * occurrences (ADR-220 section 13). Until ADR-220 every score of the run was read into a list, twice, and each
 * band's members were shuffled whole to draw its twelve.
 *
 * <p><b>The draw.</b> Each band gives the twelve occurrences of least key, in ascending order of key, the key of
 * an occurrence in a band being SplitMix64's finaliser applied to the band's seed exclusive-or the occurrence's
 * number, compared as an unsigned number. The band's seed is the one the shuffle was seeded with: the run id's
 * UTF-8 bytes folded by 64-bit FNV-1a, plus the band's ordinal. This test computes the draw itself, from that
 * rule and the rows it wrote, so the rule is what is pinned.
 *
 * <p>2,500 scores of the run, 500 in each of the five bands, written in an order unrelated to their occurrences;
 * and another run's scores beside them, which nothing may count.
 *
 * <p>It names nothing ADR-220 adds, and so compiles before it; it fails until the report draws as ADR-220 says.
 */
@Epic("Relevance")
@Feature("Score distribution")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceDistributionDrawsByLeastKeyAPageAtATimeTest {

    private static final int BANDS = 5;

    private static final int PER_BAND = 12;

    /** Scored occurrences in each band. */
    private static final int IN_A_BAND = 500;

    private static final int SCORED = BANDS * IN_A_BAND;

    /** The lowest and highest scores written, so each band is 0.1 wide and every score sits well inside one. */
    private static final double LOWEST = 0.2;

    private static final double WIDTH = 0.1;

    /** Occurrences the other run scored, none of which may count. */
    private static final int OTHER_RUNS = 300;

    /** One answer in every so many scored occurrences, for the spread. */
    private static final int ANSWERED_ONE_IN = 25;

    /** FNV-1a's 64-bit starting value and multiplier, as {@code RelevanceDistribution} seeds a band. */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;

    private static final long FNV_PRIME = 0x100000001b3L;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId scoring;
    private final Map<OccurrenceId, Integer> bandOf = new HashMap<>();
    private final List<RelevanceDistribution.Scored> written = new ArrayList<>();

    @BeforeEach
    void scoresInEveryBand() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-drawn-by-key"));
        scoring = ledger.runs().startRun("embedding-scoring", "k214", "{}", walk, List.of());
        RunId other = ledger.runs().startRun("embedding-scoring", "l214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < SCORED; i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, "f" + i + ".pdf");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
        OccurrenceId seed = new OccurrenceId(ids.getFirst());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement score = connection.prepareStatement(
                    "INSERT INTO relevance_score (occurrence_id, run_id, score, winning_seed_occurrence_id)"
                            + " VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < SCORED; i++) {
                    // Written in an order unrelated to the occurrences: every seventh, round and round.
                    int index = (int) ((long) i * 7 % SCORED);
                    int band = index % BANDS;
                    int within = index / BANDS;
                    double value = index == 0
                            ? LOWEST
                            : index == SCORED - 1
                                    ? LOWEST + BANDS * WIDTH
                                    : LOWEST + band * WIDTH + WIDTH * (within + 1) / (IN_A_BAND + 2);
                    OccurrenceId occurrence = new OccurrenceId(ids.get(index));
                    score.setLong(1, occurrence.value());
                    score.setString(2, scoring.value());
                    score.setDouble(3, value);
                    score.setLong(4, seed.value());
                    score.addBatch();
                    bandOf.put(occurrence, index == SCORED - 1 ? BANDS - 1 : band);
                    written.add(new RelevanceDistribution.Scored(occurrence, value, seed));
                }
                for (int i = 0; i < OTHER_RUNS; i++) {
                    score.setLong(1, ids.get(i));
                    score.setString(2, other.value());
                    score.setDouble(3, 0.99);
                    score.setLong(4, seed.value());
                    score.addBatch();
                }
                score.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Sixty documents, chosen the same way every time")
    @DisplayName("Each band gives the twelve occurrences of least key under the run, read a page of scores at a time")
    void eachBandGivesItsTwelveOfLeastKeyReadAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        RelevanceDistribution.Distribution distribution = new RelevanceDistribution(log.jdbcTemplate()).measure(scoring);

        claim(
                "every band holds the " + IN_A_BAND + " occurrences scored in it, and the other run's scores are not"
                        + " counted",
                () -> assertThat(distribution.bands())
                        .extracting(RelevanceDistribution.Band::documentCount)
                        .containsExactly(IN_A_BAND, IN_A_BAND, IN_A_BAND, IN_A_BAND, IN_A_BAND));
        claim(
                "each band gives the " + PER_BAND + " occurrences of least key, in ascending order of key, the bands"
                        + " lowest first: the key is the run's seed for the band mixed with the occurrence's number, so"
                        + " the same run always draws the same sixty, and no band is held whole to draw them",
                () -> assertThat(distribution.sample())
                        .extracting(RelevanceDistribution.Sampled::occurrenceId)
                        .containsExactlyElementsOf(expectedDraw()));
        List<String> reads = readsOfTheScores(log);
        claim(
                "the scores are read by one statement that finds their range and count, and then a page at a"
                        + " time, each page going on by the index on the run from the last row read and sorting"
                        + " nothing; no statement reads the run's scores whole in the order of their occurrences",
                () -> {
                    assertThat(reads).filteredOn(sql -> sql.contains("MIN(")).hasSize(1);
                    assertThat(reads)
                            .filteredOn(sql -> !sql.contains("MIN("))
                            .isNotEmpty()
                            .allSatisfy(sql -> {
                                assertThat(sql).contains(" LIMIT ");
                                assertThat(planOf(sql))
                                        .anyMatch(detail -> detail.contains("relevance_score_by_run_id (run_id=? AND rowid>?)"))
                                        .noneMatch(detail -> detail.contains("TEMP B-TREE"));
                            });
                });
    }

    @Test
    @Story("Sixty documents, chosen the same way every time")
    @DisplayName("The answers are counted against the bands and the candidate cuts as before, reading the scores a page at a time")
    void theSpreadOfTheAnswersIsTheSameReadAPageAtATime() {
        Map<OccurrenceId, Boolean> answers = new HashMap<>();
        for (int i = 0; i < written.size(); i += ANSWERED_ONE_IN) {
            answers.put(written.get(i).occurrenceId(), i % 2 == 0);
        }
        RelevanceDistribution.Distribution distribution =
                new RelevanceDistribution(pool.jdbcTemplate()).measure(scoring);
        LabelledSpread.Spread overEveryScoreHeld = LabelledSpread.of(distribution, written, answers);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        LabelledSpread.Spread spread = new RelevanceDistribution(log.jdbcTemplate()).spreadOf(scoring, answers);

        claim(
                "the answers fall in the same bands and the candidate cuts keep and discard the same numbers as"
                        + " when every score of the run is held and gone through",
                () -> assertThat(spread).isEqualTo(overEveryScoreHeld));
        claim(
                "and every read of the scores is a page, or the one statement that finds their range",
                () -> assertThat(readsOfTheScores(log))
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql).containsAnyOf(" LIMIT ", "MIN(")));
    }

    /** The twelve of least key in each band, lowest band first, computed from the rule and the rows written. */
    private List<OccurrenceId> expectedDraw() {
        List<OccurrenceId> draw = new ArrayList<>();
        for (int band = 0; band < BANDS; band++) {
            long seed = seedFor(scoring, band);
            List<OccurrenceId> inBand = new ArrayList<>();
            for (Map.Entry<OccurrenceId, Integer> entry : bandOf.entrySet()) {
                if (entry.getValue() == band) {
                    inBand.add(entry.getKey());
                }
            }
            inBand.sort(Comparator.comparing(
                    (OccurrenceId occurrence) -> mix(seed ^ occurrence.value()), Long::compareUnsigned));
            draw.addAll(inBand.subList(0, PER_BAND));
        }
        return draw;
    }

    private static long seedFor(RunId run, int band) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : run.value().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & 0xff)) * FNV_PRIME;
        }
        return hash + band;
    }

    /** SplitMix64's finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static List<String> readsOfTheScores(StatementLog log) {
        return log.said().stream().filter(sql -> sql.contains("FROM relevance_score")).toList();
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
