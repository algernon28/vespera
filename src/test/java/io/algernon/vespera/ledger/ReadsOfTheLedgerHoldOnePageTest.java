package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;

/**
 * The three reads of the ledger that can be as long as a walk, each held a page at a time (ADR-209
 * section 2): a run's survivors by id, the same by size, and a walk's occurrences.
 *
 * <p>Each is an {@code Iterable}, where it was a Spring Batch reader, and what ADR-060 asked of the reader
 * is asked of it here: that nothing holds the set. It is shown by what is asked of the database and when.
 * Every statement the read issues is kept with the rows it brought back, as {@code SurvivorsBySizeTest}
 * keeps them: none is issued until the first row is asked for, each brings back a page at most, and the
 * next is issued only once the page in hand has been handed out.
 *
 * <p>A statement is one of the read's pages when it carries a {@code LIMIT}; the statements that find
 * which runs a run's survivors answer to carry none, and are not counted.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class ReadsOfTheLedgerHoldOnePageTest {

    /** The most rows one statement of a read brings back. */
    private static final int ROWS_IN_A_PAGE = 1_000;

    /** More than two pages, so a read crosses two page boundaries and ends on a page that is not full. */
    private static final int OCCURRENCES_RECORDED = 2_500;

    /** Two full pages of 1,000 and a third of the 500 left, which being short of a page ends the read. */
    private static final int PAGES_IN_A_WHOLE_READ = 3;

    /** How many distinct sizes the occurrences are spread over, so the read by size has ties to order. */
    private static final int DISTINCT_SIZES = 37;

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("A read of the surviving documents asks the database for one page, and for the next only when that one is used up")
    void eachReadIssuesAPageOnlyWhenTheLastIsUsedUp() {
        StatementsIssued issued = new StatementsIssued(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        Map<String, Supplier<Iterable<?>>> reads = readsOver(issued);

        for (Map.Entry<String, Supplier<Iterable<?>>> read : reads.entrySet()) {
            String what = read.getKey();
            issued.pages.clear();
            Iterator<?> rows = read.getValue().get().iterator();

            int beforeTheFirstRow = issued.pages.size();
            claim(
                    what + ": nothing has been asked of the database before the first row is asked for, so"
                            + " holding the read costs nothing",
                    () -> assertThat(beforeTheFirstRow).isZero());

            int handedOut = 0;
            List<Integer> pagesIssuedWhenEachPageBegan = new ArrayList<>();
            while (rows.hasNext()) {
                if (handedOut % ROWS_IN_A_PAGE == 0) {
                    pagesIssuedWhenEachPageBegan.add(issued.pages.size());
                }
                rows.next();
                handedOut++;
            }
            int handedOutInAll = handedOut;

            claim(
                    what + ": all " + OCCURRENCES_RECORDED + " rows recorded were handed out",
                    () -> assertThat(handedOutInAll).isEqualTo(OCCURRENCES_RECORDED));
            claim(
                    what + ": when the first row of each page of " + ROWS_IN_A_PAGE + " was handed out, the"
                            + " statements issued so far were one, then two, then three: a page is asked for"
                            + " only once the one before it has been handed out, so no more than one is held",
                    () -> assertThat(pagesIssuedWhenEachPageBegan).containsExactly(1, 2, PAGES_IN_A_WHOLE_READ));
            claim(
                    what + ": the whole read issued " + PAGES_IN_A_WHOLE_READ + " statements, the third coming"
                            + " back short of a page, which is what ends it without a fourth",
                    () -> assertThat(issued.pages).hasSize(PAGES_IN_A_WHOLE_READ));
            claim(
                    what + ": no statement brought back more than " + ROWS_IN_A_PAGE + " rows, so that is the"
                            + " most the read ever holds, however long the walk",
                    () -> assertThat(issued.pages).allMatch(rowsBroughtBack -> rowsBroughtBack <= ROWS_IN_A_PAGE));
        }
    }

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("Going through a read a second time starts again from its first row")
    void goingThroughAReadAgainStartsFromTheFirstRow() {
        Map<String, Supplier<Iterable<?>>> reads = readsOver(jdbcTemplate);

        for (Map.Entry<String, Supplier<Iterable<?>>> read : reads.entrySet()) {
            Iterable<?> rows = read.getValue().get();
            List<Object> first = new ArrayList<>();
            rows.forEach(first::add);
            List<Object> second = new ArrayList<>();
            rows.forEach(second::add);

            claim(
                    read.getKey() + ": the second pass hands out the same " + OCCURRENCES_RECORDED + " rows in"
                            + " the same order as the first, so a caller that needs two passes asks once and"
                            + " holds nothing between them",
                    () -> assertThat(second).hasSize(OCCURRENCES_RECORDED).containsExactlyElementsOf(first));
        }
    }

    /** A walk of 2,500 occurrences with a run over it, and the three reads of it through {@code template}. */
    private Map<String, Supplier<Iterable<?>>> readsOver(JdbcTemplate template) {
        Ledger ledger = new Ledger(template);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        for (int i = 0; i < OCCURRENCES_RECORDED; i++) {
            ledger.occurrences()
                    .fileOccurrence(walk, new OccurrencePath("f" + i + ".txt"), i % DISTINCT_SIZES, WHEN, WHEN);
        }
        RunId run = ledger.runs().startRun("byte-level-reduction", "v" + System.nanoTime(), "{}", walk, List.of());

        Map<String, Supplier<Iterable<?>>> reads = new LinkedHashMap<>();
        reads.put("the surviving documents in the order they were recorded", () -> ledger.verdicts().survivors(run));
        reads.put("the surviving documents by size", () -> ledger.verdicts().survivorsBySize(run));
        reads.put("every file of a walk", () -> ledger.occurrences().occurrencesOf(walk));
        return reads;
    }

    /** The application's own access to the database, keeping how many rows each page of a read brought back. */
    private static final class StatementsIssued extends JdbcTemplate {

        final List<Integer> pages = new ArrayList<>();

        StatementsIssued(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = super.query(sql, rowMapper, args);
            if (sql.contains(" LIMIT ")) {
                pages.add(rows.size());
            }
            return rows;
        }
    }
}
