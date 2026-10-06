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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;

/**
 * The survivors in ascending size, which stage 1's content identity reads one size at a time instead of
 * draining the set (ADR-200, settling #405): the same occurrences {@code survivors} hands out, ordered by
 * recorded size and then id, paged without sorting the walk. That last is asked of SQLite about the
 * statement the reader issues, kept as it is issued, so a change to the shipped statement is what is
 * explained.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("405")
@Link(name = "ADR-200", url = Adr.STAGE_1_HOLDS_ONE_SIZE_AT_A_TIME, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class SurvivorsBySizeTest {

    /** More than the 1,000 a page holds, so the read crosses two page boundaries. */
    private static final int OCCURRENCES_RECORDED = 2_500;

    /** How many distinct sizes the occurrences are spread over, so many share one. */
    private static final int DISTINCT_SIZES = 37;

    /** Every seventh occurrence is ruled out, so the read has rows to leave out in every page. */
    private static final int EVERY_SEVENTH = 7;

    /** Two full pages of 1,000 and a third of the 500 left, which being short of a page ends the read. */
    private static final int PAGES_READ = 3;

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Survivors read in size order")
    @DisplayName("The survivors arrive by ascending size, then id, and the ruled-out ones are absent, across several pages")
    void arrivesBySizeThenIdAcrossPages() throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus"));
        List<SizedOccurrence> recorded = new ArrayList<>();
        for (int i = 0; i < OCCURRENCES_RECORDED; i++) {
            long size = (i * 13L) % DISTINCT_SIZES;
            OccurrencePath path = new OccurrencePath("f" + i + ".txt");
            ledger.fileOccurrence(walk, path, size, WHEN, WHEN);
            recorded.add(new SizedOccurrence(ledger.occurrenceId(walk, path).orElseThrow(), size));
        }
        RunId run = ledger.startRun("byte-level-reduction", "abc123", "{}", walk, List.of());
        List<SizedOccurrence> survivors = new ArrayList<>();
        for (int i = 0; i < recorded.size(); i++) {
            if (i % EVERY_SEVENTH == 0) {
                ledger.verdict(recorded.get(i).occurrenceId(), run, VerdictKind.BROKEN, "ruled out");
            } else {
                survivors.add(recorded.get(i));
            }
        }
        List<SizedOccurrence> expected = new ArrayList<>(survivors);
        expected.sort(Comparator.comparingLong(SizedOccurrence::sizeBytes)
                .thenComparingLong(entry -> entry.occurrenceId().value()));

        List<SizedOccurrence> read = readAll(ledger.survivorsBySize(run));

        claim(
                "every survivor arrives once, by ascending recorded size and then ascending id, and none that carries a"
                        + " blocking verdict does, though the set is read over three pages",
                () -> assertThat(read).containsExactlyElementsOf(expected));
    }

    @Test
    @Story("Survivors read in size order")
    @DisplayName("A verdict written for each survivor as it arrives loses no survivor still to come")
    void aVerdictWrittenMidReadRemovesNothingToCome() throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus"));
        for (int i = 0; i < OCCURRENCES_RECORDED; i++) {
            ledger.fileOccurrence(walk, new OccurrencePath("f" + i + ".txt"), i % DISTINCT_SIZES, WHEN, WHEN);
        }
        RunId run = ledger.startRun("byte-level-reduction", "abc123", "{}", walk, List.of());

        int seen = 0;
        ItemStreamReader<SizedOccurrence> reader = ledger.survivorsBySize(run);
        reader.open(new ExecutionContext());
        try {
            for (SizedOccurrence entry = reader.read(); entry != null; entry = reader.read()) {
                ledger.verdict(entry.occurrenceId(), run, VerdictKind.BROKEN, "written while reading");
                seen++;
            }
        } finally {
            reader.close();
        }
        int seenAll = seen;

        claim(
                "all " + OCCURRENCES_RECORDED + " occurrences were handed out although each had a blocking verdict"
                        + " written the moment it arrived",
                () -> assertThat(seenAll).isEqualTo(OCCURRENCES_RECORDED));
    }

    @Test
    @Story("Survivors read in size order")
    @DisplayName("A page of the read is answered from the index on the walk and the size, with no sort")
    void aPageNeedsNoSort() throws Exception {
        StatementsIssued issued = new StatementsIssued(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        Ledger ledger = new Ledger(issued);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus"));
        for (int i = 0; i < OCCURRENCES_RECORDED; i++) {
            ledger.fileOccurrence(walk, new OccurrencePath("f" + i + ".txt"), i % DISTINCT_SIZES, WHEN, WHEN);
        }
        RunId run = ledger.startRun("byte-level-reduction", "abc123", "{}", walk, List.of());
        ItemStreamReader<SizedOccurrence> reader = ledger.survivorsBySize(run);

        // Only what the reader issues while it is read: the statement as shipped, with the arguments it was
        // given, and not a copy of it kept here that a change to the shipped one would leave passing.
        issued.keep = true;
        readAll(reader);
        issued.keep = false;
        List<List<String>> plans = new ArrayList<>();
        for (StatementsIssued.Statement page : issued.statements) {
            plans.add(jdbcTemplate.query(
                    "EXPLAIN QUERY PLAN " + page.sql(),
                    (resultSet, rowNumber) -> resultSet.getString("detail"),
                    page.arguments()));
        }

        claim(
                "reading " + OCCURRENCES_RECORDED + " survivors issued " + PAGES_READ + " statements, one a page,"
                        + " so the plans below are those of the statements the read really ran",
                () -> assertThat(plans).hasSize(PAGES_READ));
        claim(
                "each page's plan searches file_occurrence_by_walk_and_size, so each page is a range of it",
                () -> assertThat(plans)
                        .allSatisfy(plan -> assertThat(plan)
                                .anyMatch(detail -> detail.contains("file_occurrence_by_walk_and_size"))));
        claim(
                "and no page's plan names a temp B-tree, so no page sorts the walk it reads from",
                () -> assertThat(plans)
                        .allSatisfy(plan -> assertThat(plan).noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    /** The application's own access to the database, keeping each statement asked of it while told to. */
    private static final class StatementsIssued extends JdbcTemplate {

        record Statement(String sql, Object[] arguments) {}

        final List<Statement> statements = new ArrayList<>();
        boolean keep;

        StatementsIssued(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (keep) {
                statements.add(new Statement(sql, args));
            }
            return super.query(sql, rowMapper, args);
        }
    }

    private static List<SizedOccurrence> readAll(ItemStreamReader<SizedOccurrence> reader) throws Exception {
        List<SizedOccurrence> read = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            for (SizedOccurrence entry = reader.read(); entry != null; entry = reader.read()) {
                read.add(entry);
            }
        } finally {
            reader.close();
        }
        return read;
    }
}
