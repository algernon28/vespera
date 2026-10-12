package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.StatementLog;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * {@link KeysetPages}, {@code ledger}'s pager for a long read by keyset (ADR-209 section 2), in its two forms
 * for a key that is not the last argument and a column the read hands out: a statement that binds its key
 * before another argument, given each page's arguments whole, and a key read into a carrier row that {@link
 * KeysetPages#map} leaves behind. {@code DocumentClusters.eachPage} is the first form, and {@code
 * RelevanceDistribution.scoredUnder} and {@code Occurrences.sameOccurrences} the second (ADR-220).
 *
 * <p>Over a table of the test's own in a database in memory: 5,000 numbered rows on two shelves taken in turn,
 * so a read of one shelf is 2,500 rows, two full pages of 1,000 and a short third, and every page of it passes
 * over the other shelf's rows between its own.
 */
@Epic("Ledger")
@Feature("Long reads")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class KeysetPagesTest {

    /** Rows numbered 1 to 5,000, the odd ones on one shelf and the even ones on the other. */
    private static final int ROWS = 5_000;

    /** The shelf of the odd-numbered rows. */
    private static final long ODD_SHELF = 1L;

    /** The shelf of the even-numbered rows. */
    private static final long EVEN_SHELF = 0L;

    /** Half of the rows are on each shelf. */
    private static final int ROWS_ON_A_SHELF = ROWS / 2;

    /** The most rows one page holds. */
    private static final int A_PAGE = KeysetPages.ROWS_IN_A_PAGE;

    /** Two full pages and the 500 rows left. */
    private static final List<Integer> PAGE_SIZES = List.of(A_PAGE, A_PAGE, ROWS_ON_A_SHELF - 2 * A_PAGE);

    private SingleConnectionDataSource database;
    private StatementLog log;

    @BeforeEach
    void numberedRowsOnTwoShelves() {
        database = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate template = new JdbcTemplate(database);
        template.execute(
                "CREATE TABLE numbered_row (id INTEGER PRIMARY KEY, shelf INTEGER NOT NULL, label TEXT NOT NULL)");
        template.batchUpdate(
                "INSERT INTO numbered_row (id, shelf, label) VALUES (?, ?, ?)",
                LongStream.rangeClosed(1, ROWS)
                        .mapToObj(id -> new Object[] {id, id % 2, labelOf(id)})
                        .toList());
        log = new StatementLog(database);
    }

    @AfterEach
    void closeTheDatabase() {
        database.destroy();
    }

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("A read whose statement asks for its key before another value is given every value of each page in its own order")
    void aKeyBeforeAnotherArgumentIsBoundWhereTheStatementPutsIt() {
        KeysetPages<NumberedRow> oddShelf = new KeysetPages<>(
                log.jdbcTemplate(),
                "SELECT id, label FROM numbered_row WHERE id > ? AND shelf = ? ORDER BY id",
                List.of(Long.MIN_VALUE, ODD_SHELF),
                (NumberedRow last) -> List.of(last.id(), ODD_SHELF),
                (resultSet, rowNumber) -> new NumberedRow(resultSet.getLong("id"), resultSet.getString("label")));

        List<List<NumberedRow>> pages = new ArrayList<>();
        oddShelf.eachPage(pages::add);

        List<Long> numbers = pages.stream().flatMap(List::stream).map(NumberedRow::id).toList();
        List<Integer> sizes = pages.stream().map(List::size).toList();
        claim(
                "the read handed over the " + ROWS_ON_A_SHELF + " rows of the shelf asked for, and only those, in"
                        + " number order: each page went on from the last number of the one before, on the same shelf",
                () -> assertThat(numbers)
                        .hasSize(ROWS_ON_A_SHELF)
                        .allMatch(id -> id % 2 == ODD_SHELF)
                        .isSorted()
                        .doesNotHaveDuplicates());
        claim(
                "it was handed over as two full pages of " + A_PAGE + " and a short third, which ends the read",
                () -> assertThat(sizes).containsExactlyElementsOf(PAGE_SIZES));
        claim(
                "one statement was asked for each page, three in all",
                () -> assertThat(pagesAsked()).hasSize(PAGE_SIZES.size()));
    }

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("A read that pages by a number it does not hand out hands out the rest of each row, a page asked only once the one before is used up")
    void aKeyReadIntoACarrierRowIsLeftBehindByMap() {
        Iterable<String> evenShelf = new KeysetPages<CarriedLabel>(
                        log.jdbcTemplate(),
                        "SELECT rowid, label FROM numbered_row WHERE shelf = ? AND rowid > ? ORDER BY rowid",
                        List.of(EVEN_SHELF),
                        List.of(Long.MIN_VALUE),
                        carried -> List.of(carried.rowid()),
                        (resultSet, rowNumber) -> new CarriedLabel(resultSet.getLong(1), resultSet.getString("label")))
                .map(CarriedLabel::label);

        Iterator<String> labels = evenShelf.iterator();
        int beforeTheFirstRow = pagesAsked().size();
        List<String> handedOut = new ArrayList<>();
        List<Integer> pagesAskedWhenEachPageBegan = new ArrayList<>();
        while (labels.hasNext()) {
            if (handedOut.size() % A_PAGE == 0) {
                pagesAskedWhenEachPageBegan.add(pagesAsked().size());
            }
            handedOut.add(labels.next());
        }
        List<String> expected = LongStream.rangeClosed(1, ROWS)
                .filter(id -> id % 2 == EVEN_SHELF)
                .mapToObj(KeysetPagesTest::labelOf)
                .toList();

        claim(
                "nothing was asked of the database before the first row was asked for",
                () -> assertThat(beforeTheFirstRow).isZero());
        claim(
                "the read handed out the label of each of the shelf's " + ROWS_ON_A_SHELF + " rows in number order,"
                        + " and not the number it paged by",
                () -> assertThat(handedOut).containsExactlyElementsOf(expected));
        claim(
                "when the first row of each page was handed out, the statements asked so far were one, then two, then"
                        + " three: a page is asked for only once the one before it has been handed out",
                () -> assertThat(pagesAskedWhenEachPageBegan).containsExactly(1, 2, PAGE_SIZES.size()));

        log.clear();
        List<String> again = new ArrayList<>();
        evenShelf.forEach(again::add);
        claim(
                "going through the read again starts at its first row and asks for its three pages again",
                () -> {
                    assertThat(again).containsExactlyElementsOf(expected);
                    assertThat(pagesAsked()).hasSize(PAGE_SIZES.size());
                });
    }

    /**
     * A page that comes back short ends the read, so the pager writes each page's bound itself, the number it
     * reads a short page by, and a caller's statement carries none (ADR-245, amending ADR-244).
     */
    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("A read's statement is bounded by the pager's own page, which the statement it is given does not name")
    @Link(name = "ADR-245", url = Adr.A_PAGE_IS_ASKED_WHETHER_IT_IS_KEPT_AND_A_SHARING_VIOLATION_IS_TRIED_AGAIN_ONLY_ON_WINDOWS, type = "adr")
    void thePagerBoundsEachPageItself() {
        List<List<NumberedRow>> pages = new ArrayList<>();
        oddShelf().eachPage(pages::add);

        claim(
                "the " + ROWS_ON_A_SHELF + " rows of the shelf were handed over as two full pages of " + A_PAGE
                        + " and a short third, which ends the read",
                () -> assertThat(pages.stream().map(List::size).toList()).containsExactlyElementsOf(PAGE_SIZES));
        claim(
                "each page's statement ended in the pager's bound, LIMIT " + A_PAGE,
                () -> assertThat(pagesAsked())
                        .hasSize(PAGE_SIZES.size())
                        .allSatisfy(sql -> assertThat(sql).endsWith(" LIMIT " + A_PAGE)));
    }

    private KeysetPages<NumberedRow> oddShelf() {
        return new KeysetPages<>(
                log.jdbcTemplate(),
                "SELECT id, label FROM numbered_row WHERE id > ? AND shelf = ? ORDER BY id",
                List.of(Long.MIN_VALUE, ODD_SHELF),
                (NumberedRow last) -> List.of(last.id(), ODD_SHELF),
                (resultSet, rowNumber) -> new NumberedRow(resultSet.getLong("id"), resultSet.getString("label")));
    }

    private List<String> pagesAsked() {
        return log.said().stream()
                .filter(sql -> sql.contains("FROM numbered_row") && sql.contains(" LIMIT "))
                .toList();
    }

    private static String labelOf(long id) {
        return "row " + id;
    }

    /** A row as a read hands it out, its number among its columns. */
    private record NumberedRow(long id, String label) {}

    /** A row's label with the number the read pages by, which is not handed out. */
    private record CarriedLabel(long rowid, String label) {}
}
