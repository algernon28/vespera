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
import java.util.List;
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
 * The two reads of the ledger in id order, a run's survivors and a walk's occurrences, go forward from the
 * last id read on every page, and no page sorts the walk (ADR-211 section 8).
 *
 * <p>As shipped at {@code 4b99a03}, SQLite plans each page as a search of {@code
 * file_occurrence_by_walk_and_size} on the walk, then sorts what it found in a temp B-tree to keep 1,000: so
 * every page goes through the whole walk, and a whole read grows as the square of the walk. ADR-211 measured
 * a read of 240,000 survivors at about thirteen seconds that way, and at a quarter of a second planned by the
 * primary key. {@code SurvivorsBySizeTest} pins the same of the read by size; this pins it of the other two.
 *
 * <p>The plans are of the statements the reads really issue, captured with their arguments as {@code
 * SurvivorsBySizeTest} captures them, and not of a copy kept here. A later walk is recorded after the one
 * read, so the walk read is not the whole table.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
class ReadsInIdOrderSortNothingTest {

    /** More than two pages of 1,000, so a read crosses two page boundaries. */
    private static final int OCCURRENCES_RECORDED = 2_500;

    /** Every tenth occurrence is ruled out, so the survivors are fewer than the walk's occurrences. */
    private static final int EVERY_TENTH = 10;

    /** The occurrences left once every tenth is ruled out. */
    private static final int SURVIVORS = OCCURRENCES_RECORDED - OCCURRENCES_RECORDED / EVERY_TENTH;

    /** Two full pages and a short third, for either read: 2,500 occurrences, or the 2,250 that survive. */
    private static final int PAGES_IN_A_WHOLE_READ = 3;

    /** A later walk's occurrences, recorded after the walk read, so its ids come after every one of it. */
    private static final int LATER_OCCURRENCES = 40;

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("Each page of the surviving documents goes on from the last one read and sorts nothing")
    void eachPageOfTheSurvivorsGoesOnFromTheLastAndSortsNothing() {
        StatementsIssued issued = new StatementsIssued(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        Ledger ledger = new Ledger(issued);
        WalkId walk = aWalk(ledger, "C:/corpus-in-id-order-" + System.nanoTime());
        RunId run = ledger.runs().startRun("byte-level-reduction", "v" + System.nanoTime(), "{}", walk, List.of());
        List<OccurrenceId> recorded = new ArrayList<>();
        for (OccurrenceId occurrence : ledger.occurrences().occurrencesOf(walk)) {
            recorded.add(occurrence);
        }
        List<OccurrenceId> expected = new ArrayList<>();
        for (int i = 0; i < recorded.size(); i++) {
            if (i % EVERY_TENTH == 0) {
                ledger.verdicts().verdict(recorded.get(i), run, VerdictKind.BROKEN, "ruled out by this test");
            } else {
                expected.add(recorded.get(i));
            }
        }
        aWalk(ledger, "C:/later-walk-" + System.nanoTime(), LATER_OCCURRENCES);

        List<OccurrenceId> read = readCapturing(issued, () -> ledger.verdicts().survivors(run));

        claim(
                "the read hands out the " + SURVIVORS + " survivors, every tenth occurrence being ruled out, in"
                        + " ascending order, and nothing of the later walk",
                () -> assertThat(read).containsExactlyElementsOf(expected));
        thePagesSortNothing("the surviving documents", issued);
    }

    @Test
    @Story("A long read is held a page at a time")
    @DisplayName("Each page of a walk's files goes on from the last one read and sorts nothing")
    void eachPageOfAWalksOccurrencesGoesOnFromTheLastAndSortsNothing() {
        StatementsIssued issued = new StatementsIssued(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        Ledger ledger = new Ledger(issued);
        WalkId walk = aWalk(ledger, "C:/walk-in-id-order-" + System.nanoTime());
        aWalk(ledger, "C:/later-walk-" + System.nanoTime(), LATER_OCCURRENCES);

        List<OccurrenceId> read = readCapturing(issued, () -> ledger.occurrences().occurrencesOf(walk));

        claim(
                "the read hands out the walk's " + OCCURRENCES_RECORDED + " files in ascending order, and nothing of"
                        + " the later walk",
                () -> assertThat(read).hasSize(OCCURRENCES_RECORDED).isSortedAccordingTo(
                        (left, right) -> Long.compare(left.value(), right.value())));
        thePagesSortNothing("a walk's files", issued);
    }

    private void thePagesSortNothing(String what, StatementsIssued issued) {
        List<List<String>> plans = new ArrayList<>();
        for (StatementsIssued.Page page : issued.pages) {
            plans.add(jdbcTemplate.query(
                    "EXPLAIN QUERY PLAN " + page.sql(),
                    (resultSet, rowNumber) -> resultSet.getString("detail"),
                    page.arguments()));
        }
        claim(
                what + ": the read issued " + PAGES_IN_A_WHOLE_READ + " statements, one a page, so the plans below"
                        + " are those of the statements it really ran",
                () -> assertThat(plans).hasSize(PAGES_IN_A_WHOLE_READ));
        claim(
                what + ": each page's plan searches the files by their own number, starting after the last one"
                        + " read, so a page goes no further back than where the one before it ended",
                () -> assertThat(plans)
                        .allSatisfy(plan -> assertThat(plan)
                                .anyMatch(detail -> detail.contains("file_occurrence USING INTEGER PRIMARY KEY")
                                        && detail.contains("rowid>"))));
        claim(
                what + ": and no page's plan sorts in a structure of its own, so no page goes through the whole"
                        + " walk to keep a thousand of it",
                () -> assertThat(plans)
                        .allSatisfy(plan -> assertThat(plan).noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    private static <T> List<T> readCapturing(StatementsIssued issued, Supplier<Iterable<T>> read) {
        Iterable<T> rows = read.get();
        issued.keep = true;
        List<T> handedOut = new ArrayList<>();
        for (T row : rows) {
            handedOut.add(row);
        }
        issued.keep = false;
        return handedOut;
    }

    private static WalkId aWalk(Ledger ledger, String root) {
        return aWalk(ledger, root, OCCURRENCES_RECORDED);
    }

    private static WalkId aWalk(Ledger ledger, String root, int occurrences) {
        WalkId walk = ledger.walks().startWalk(Path.of(root));
        for (int i = 0; i < occurrences; i++) {
            ledger.occurrences().fileOccurrence(walk, new OccurrencePath("f" + i + ".txt"), i % 37, WHEN, WHEN);
        }
        return walk;
    }

    /** The application's own access to the database, keeping each page a read issues while told to. */
    private static final class StatementsIssued extends JdbcTemplate {

        record Page(String sql, Object[] arguments) {}

        final List<Page> pages = new ArrayList<>();
        boolean keep;

        StatementsIssued(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (keep && sql.contains(" LIMIT ")) {
                pages.add(new Page(sql, args));
            }
            return super.query(sql, rowMapper, args);
        }
    }
}
