package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code AnomalyLog.sameAnomalies}: whether a finished walk noted the same walk anomalies as the walk before it,
 * the other half of the question by which the census discards a walk that saw nothing new (ADR-115), asked a
 * page of each walk's anomalies at a time (ADR-220 section 3), where both walks' anomalies were read whole.
 *
 * <p>One test compares walks of exactly two full pages, where the page after the last full one is empty, the one
 * place a comparison that took a full page for the last, or an empty one for a difference, would answer wrongly.
 */
@Epic("Census")
@Feature("Walk anomalies")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class SameAnomaliesTest {

    /** A full page of 1,000 and some of a second. */
    private static final int ANOMALIES = 1_200;

    /** The most anomalies a page holds. */
    private static final int A_PAGE = 1_000;

    /** Two pages of each of the two walks. */
    private static final int TWO_PAGES_OF_EACH = 4;

    /** Exactly two full pages, so the third page each walk is asked for is empty. */
    private static final int TWO_FULL_PAGES = 2 * A_PAGE;

    /** Three pages of each of the two walks: two full and one empty. */
    private static final int THREE_PAGES_OF_EACH = 6;

    /** The anomaly whose detail differs: on the second page. */
    private static final int ON_THE_SECOND_PAGE = 1_100;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private Ledger ledger;
    private AnomalyLog anomalyLog;

    @BeforeEach
    void aPoolOfTwo() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        ledger = new Ledger(pool.jdbcTemplate());
        anomalyLog = new AnomalyLog(pool.jdbcTemplate());
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("Two walks that noted the same entries they could not take in are the same, compared a page of each at a time")
    void twoWalksOfTheSameAnomaliesAreTheSameReadAPageAtATime() {
        WalkId earlier = walkNoting("earlier", ANOMALIES, -1);
        WalkId later = walkNoting("later", ANOMALIES, -1);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        boolean same = new AnomalyLog(log.jdbcTemplate()).sameAnomalies(earlier, later);

        List<String> pages = log.said().stream()
                .filter(sql -> sql.contains("FROM walk_anomaly") && sql.contains(" LIMIT "))
                .toList();
        claim("the two walks noted the same entries, so they are the same", () -> assertThat(same).isTrue());
        claim(
                "each walk's notes were read a page of at most " + A_PAGE + " at a time, two pages of each, where"
                        + " each walk's were read whole before",
                () -> assertThat(pages).hasSize(TWO_PAGES_OF_EACH));
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("A walk differs from the one before it where one note's detail differs or it noted one entry more, and two walks that noted nothing are the same")
    void aDifferenceAnywhereMakesTwoWalksDifferent() {
        WalkId earlier = walkNoting("earlier", ANOMALIES, -1);
        WalkId detailDiffers = walkNoting("detail", ANOMALIES, ON_THE_SECOND_PAGE);
        WalkId oneMore = walkNoting("more", ANOMALIES + 1, -1);
        WalkId noneHere = walkNoting("none-here", 0, -1);
        WalkId noneThere = walkNoting("none-there", 0, -1);

        claim(
                "a walk in which one note on the second page says something else is not the same",
                () -> assertThat(anomalyLog.sameAnomalies(earlier, detailDiffers)).isFalse());
        claim(
                "a walk that noted one entry more is not the same, whichever is asked about first",
                () -> {
                    assertThat(anomalyLog.sameAnomalies(earlier, oneMore)).isFalse();
                    assertThat(anomalyLog.sameAnomalies(oneMore, earlier)).isFalse();
                });
        claim(
                "two walks that noted nothing are the same",
                () -> assertThat(anomalyLog.sameAnomalies(noneHere, noneThere)).isTrue());
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("Two walks that noted exactly two thousand entries are the same, and one whose last note differs or that noted one more is not")
    void walksOfAnExactNumberOfPagesAreComparedToTheirEnd() {
        WalkId earlier = walkNoting("earlier-exact", TWO_FULL_PAGES, -1);
        WalkId later = walkNoting("later-exact", TWO_FULL_PAGES, -1);
        WalkId lastDiffers = walkNoting("last-exact", TWO_FULL_PAGES, TWO_FULL_PAGES - 1);
        WalkId oneMore = walkNoting("more-exact", TWO_FULL_PAGES + 1, -1);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        boolean same = new AnomalyLog(log.jdbcTemplate()).sameAnomalies(earlier, later);

        claim(
                "two walks that noted exactly " + TWO_FULL_PAGES + " of the same entries are the same, read in three"
                        + " pages each, the third empty",
                () -> {
                    assertThat(same).isTrue();
                    assertThat(log.said().stream()
                                    .filter(sql -> sql.contains("FROM walk_anomaly") && sql.contains(" LIMIT "))
                                    .toList())
                            .hasSize(THREE_PAGES_OF_EACH);
                });
        claim(
                "a walk whose last note, the last row of its second full page, says something else is not the same",
                () -> assertThat(anomalyLog.sameAnomalies(earlier, lastDiffers)).isFalse());
        claim(
                "a walk that noted one entry more, alone on a third page, is not the same, whichever is asked about"
                        + " first",
                () -> {
                    assertThat(anomalyLog.sameAnomalies(earlier, oneMore)).isFalse();
                    assertThat(anomalyLog.sameAnomalies(oneMore, earlier)).isFalse();
                });
    }

    /**
     * A walk noting {@code count} entries it could not take in, the one at {@code differing} with another detail,
     * written in one batch as {@code AnomalyLog} writes each, so that thousands are written in one transaction.
     */
    private WalkId walkNoting(String name, int count, int differing) {
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-noted-" + name));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement anomaly = connection.prepareStatement(
                    "INSERT INTO walk_anomaly (walk_id, path_rendering, kind, detail) VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < count; i++) {
                    anomaly.setLong(1, walk.value());
                    anomaly.setString(2, "dir/unstorable-" + i);
                    anomaly.setString(3, WalkAnomalyKind.UNENCODABLE_PATH.name());
                    anomaly.setString(4, i == differing ? "another detail" : "no UTF-8 encoding");
                    anomaly.addBatch();
                }
                anomaly.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException("could not write the walk's anomalies", e);
        }
        return walk;
    }
}
