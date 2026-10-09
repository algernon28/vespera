package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
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
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code Occurrences.sameOccurrences}: whether a finished walk recorded what the walk before it recorded, the
 * question by which the census discards a walk that saw nothing new (ADR-115), asked a page of each walk at a
 * time (ADR-220 section 3). Until ADR-220 both walks were read whole into lists and the lists compared, two
 * lists of every occurrence with its path, on every invocation after the first.
 *
 * <p>Each walk is 2,500 occurrences, so each is read in three pages of 1,000, and they are compared on what the
 * walk observed: the path, the size and the two times, and not the numbers the ledger gave the rows.
 *
 * <p>Two more tests compare walks of exactly two full pages, where the page after the last full one is empty:
 * a comparison that took a full page for the last, or an empty one for a difference, would answer wrongly only
 * there.
 */
@Epic("Census")
@Feature("Ledger")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class SameOccurrencesTest {

    /** Two full pages of 1,000 and a short third. */
    private static final int OCCURRENCES = 2_500;

    /** The most rows a page of a walk holds. */
    private static final int A_PAGE = 1_000;

    /** One page of each of the two walks. */
    private static final int ONE_PAGE_OF_EACH = 2;

    /** The occurrence whose size differs: on the third page. */
    private static final int ON_THE_THIRD_PAGE = 2_100;

    /** The occurrence whose path differs: on the first page. */
    private static final int ON_THE_FIRST_PAGE = 10;

    private static final String PAGE = "a page of a walk";

    /** Exactly two full pages, so the third page each walk is asked for is empty. */
    private static final int TWO_FULL_PAGES = 2 * A_PAGE;

    /** The last occurrence of {@link #TWO_FULL_PAGES}: the last row of the second, full, page. */
    private static final int THE_LAST_ONE = TWO_FULL_PAGES - 1;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private Ledger ledger;

    @BeforeEach
    void aPoolOfTwo() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        ledger = new Ledger(pool.jdbcTemplate());
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("Two walks that recorded the same thousands of files are the same, compared a page of each at a time")
    void twoWalksOfTheSameOccurrencesAreTheSameReadAPageAtATime() throws SQLException {
        WalkId earlier = walk("earlier", OCCURRENCES, i -> "dir/f" + i + ".pdf", i -> i);
        WalkId later = walk("later", OCCURRENCES, i -> "dir/f" + i + ".pdf", i -> i);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        boolean same = new Ledger(log.jdbcTemplate()).occurrences().sameOccurrences(earlier, later);

        List<String> pages = log.said().stream().filter(SameOccurrencesTest::isAPage).toList();
        claim("the two walks recorded the same files, so they are the same", () -> assertThat(same).isTrue());
        claim(
                "each walk was read a page of at most " + A_PAGE + " at a time, three pages of each, six in all,"
                        + " where each was read whole before",
                () -> assertThat(pages).hasSize(3 * ONE_PAGE_OF_EACH));
        claim(
                "each page is found by the occurrences' own numbers, going on from the last one read, and none"
                        + " searches the walk's index by size or sorts",
                () -> assertThat(pages).allSatisfy(sql -> assertThat(planOf(sql))
                        .anyMatch(detail -> detail.contains("file_occurrence USING INTEGER PRIMARY KEY (rowid>?)"))
                        .noneMatch(detail -> detail.contains("file_occurrence_by_walk_and_size"))
                        .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("A walk differs from the one before it where one file's size differs, one path differs, or it holds one file more")
    void aDifferenceAnywhereMakesTwoWalksDifferent() throws SQLException {
        WalkId earlier = walk("earlier", OCCURRENCES, i -> "dir/f" + i + ".pdf", i -> i);
        WalkId sizeDiffers = walk("size", OCCURRENCES, i -> "dir/f" + i + ".pdf", i -> i == ON_THE_THIRD_PAGE ? i + 1 : i);
        WalkId pathDiffers =
                walk("path", OCCURRENCES, i -> i == ON_THE_FIRST_PAGE ? "dir/renamed.pdf" : "dir/f" + i + ".pdf", i -> i);
        WalkId oneMore = walk("more", OCCURRENCES + 1, i -> "dir/f" + i + ".pdf", i -> i);

        claim(
                "a walk in which one file on the third page has another size is not the same",
                () -> assertThat(ledger.occurrences().sameOccurrences(earlier, sizeDiffers)).isFalse());
        claim(
                "a walk in which one file on the first page has another path is not the same",
                () -> assertThat(ledger.occurrences().sameOccurrences(earlier, pathDiffers)).isFalse());
        claim(
                "a walk holding one file more is not the same, whichever of the two is asked about first",
                () -> {
                    assertThat(ledger.occurrences().sameOccurrences(earlier, oneMore)).isFalse();
                    assertThat(ledger.occurrences().sameOccurrences(oneMore, earlier)).isFalse();
                });
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("A difference on the first page is found having read one page of each walk")
    void aDifferenceOnTheFirstPageStopsTheComparisonThere() throws SQLException {
        WalkId earlier = walk("earlier", OCCURRENCES, i -> "dir/f" + i + ".pdf", i -> i);
        WalkId pathDiffers =
                walk("path", OCCURRENCES, i -> i == ON_THE_FIRST_PAGE ? "dir/renamed.pdf" : "dir/f" + i + ".pdf", i -> i);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        boolean same = new Ledger(log.jdbcTemplate()).occurrences().sameOccurrences(earlier, pathDiffers);

        claim("the walks are not the same", () -> assertThat(same).isFalse());
        claim(
                "and the comparison stopped at the first page that differed, having read " + ONE_PAGE_OF_EACH
                        + " pages, one of each walk",
                () -> assertThat(log.said().stream().filter(SameOccurrencesTest::isAPage).toList())
                        .hasSize(ONE_PAGE_OF_EACH));
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("Two walks of exactly two thousand files are the same, the empty page after the second full one ending the comparison")
    void twoWalksOfAnExactNumberOfPagesAreTheSame() throws SQLException {
        WalkId earlier = walk("earlier-exact", TWO_FULL_PAGES, i -> "dir/f" + i + ".pdf", i -> i);
        WalkId later = walk("later-exact", TWO_FULL_PAGES, i -> "dir/f" + i + ".pdf", i -> i);
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        boolean same = new Ledger(log.jdbcTemplate()).occurrences().sameOccurrences(earlier, later);

        claim(
                "two walks of exactly " + TWO_FULL_PAGES + " of the same files are the same: a second page that is"
                        + " full is not taken for the last, and the empty page after it is not taken for a"
                        + " difference",
                () -> assertThat(same).isTrue());
        claim(
                "each walk was read in three pages, two full and one empty, six in all, and no more",
                () -> assertThat(log.said().stream().filter(SameOccurrencesTest::isAPage).toList())
                        .hasSize(3 * ONE_PAGE_OF_EACH));
    }

    @Test
    @Story("A walk that saw nothing new is discarded")
    @DisplayName("Of two walks of exactly two thousand files, one differing in its last file or holding one file more is not the same")
    void aDifferenceAtTheEdgeOfAnExactNumberOfPagesMakesTwoWalksDifferent() throws SQLException {
        WalkId earlier = walk("earlier-edge", TWO_FULL_PAGES, i -> "dir/f" + i + ".pdf", i -> i);
        WalkId lastDiffers =
                walk("last-edge", TWO_FULL_PAGES, i -> "dir/f" + i + ".pdf", i -> i == THE_LAST_ONE ? i + 1 : i);
        WalkId oneMore = walk("more-edge", TWO_FULL_PAGES + 1, i -> "dir/f" + i + ".pdf", i -> i);

        claim(
                "a walk whose last file, the last row of its second full page, has another size is not the same",
                () -> assertThat(ledger.occurrences().sameOccurrences(earlier, lastDiffers)).isFalse());
        claim(
                "a walk holding one file more, alone on a third page where the other walk's third page is empty,"
                        + " is not the same, whichever of the two is asked about first",
                () -> {
                    assertThat(ledger.occurrences().sameOccurrences(earlier, oneMore)).isFalse();
                    assertThat(ledger.occurrences().sameOccurrences(oneMore, earlier)).isFalse();
                });
    }

    /** A finished-looking walk of {@code count} occurrences, written in one batch, the i-th named and sized by the two functions. */
    private WalkId walk(String name, int count, IntFunction<String> path, IntFunction<Integer> size) throws SQLException {
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-same-" + name));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, ?, '2026-01-01T00:00:00Z', '2025-12-01T00:00:00Z')")) {
                for (int i = 0; i < count; i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, path.apply(i));
                    occurrence.setLong(3, size.apply(i));
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        return walk;
    }

    private static boolean isAPage(String sql) {
        return sql.contains("FROM file_occurrence") && sql.contains(" LIMIT ");
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
