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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * {@code Verdicts.survivingAmong}: which of some occurrences a caller already holds survive a run, asked of
 * the ledger a page at a time, so that a caller with a list in hand need hold no set of the run's survivors
 * (ADR-211 section 3). Grouping asks it of each partition's members.
 *
 * <p>It answers to the runs a run's survivors answer to (ADR-156), and to the run's walk; it names at most
 * 1,000 occurrences in a statement; it asks nothing about nothing; and each statement is a lookup by the
 * occurrences' own numbers, not a search of the walk (ADR-211 section 6, where the walk search cost every
 * ask a pass over the walk).
 *
 * <p>Parked under {@code docs/adr/0211/tests/} until the method exists: it names it, and would stop the test
 * tree compiling.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
class SurvivingAmongTest {

    /** More than two statements' worth of 1,000 occurrences. */
    private static final int MANY = 2_500;

    /** The most occurrences one statement names. */
    private static final int A_PAGE = 1_000;

    /** Every tenth of the many is ruled out under the run asked about. */
    private static final int EVERY_TENTH = 10;

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Asking which of some documents survive")
    @DisplayName("Of the documents asked about, the answer is those that survive the run, by the run's own verdicts and those of the runs before it, and of its own walk")
    void answersTheOccurrencesThatSurviveTheRunsScope() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-asked-" + System.nanoTime()));
        WalkId otherWalk = ledger.walks().startWalk(Path.of("C:/other-walk-" + System.nanoTime()));
        OccurrenceId kept = occurrence(ledger, walk, "kept.pdf");
        OccurrenceId ruledOutUnderTheRun = occurrence(ledger, walk, "ruled-out-here.pdf");
        OccurrenceId ruledOutUpstream = occurrence(ledger, walk, "ruled-out-upstream.pdf");
        OccurrenceId ruledOutBySibling = occurrence(ledger, walk, "ruled-out-by-a-sibling.pdf");
        OccurrenceId ofAnotherWalk = occurrence(ledger, otherWalk, "elsewhere.pdf");
        RunId upstream = ledger.runs().startRun("byte-level-reduction", "u" + System.nanoTime(), "{}", walk, List.of());
        RunId run = ledger.runs().startRun("extraction", "r" + System.nanoTime(), "{}", walk, List.of(upstream));
        RunId sibling = ledger.runs().startRun("extraction", "s" + System.nanoTime(), "{}", walk, List.of(upstream));
        ledger.verdicts().verdict(ruledOutUnderTheRun, run, VerdictKind.DEGENERATE_OUTPUT, "ruled out by this test");
        ledger.verdicts().verdict(ruledOutUpstream, upstream, VerdictKind.BROKEN, "ruled out by this test");
        ledger.verdicts().verdict(ruledOutBySibling, sibling, VerdictKind.EXTRACTION_FAILED, "ruled out by this test");

        Set<OccurrenceId> surviving = ledger.verdicts()
                .survivingAmong(
                        run, List.of(kept, ruledOutUnderTheRun, ruledOutUpstream, ruledOutBySibling, ofAnotherWalk));

        claim(
                "the answer is the document nothing ruled out and the one ruled out only under a run this run does"
                        + " not read; not the one ruled out under the run, nor under the run before it, nor the"
                        + " document of another walk",
                () -> assertThat(surviving).containsExactlyInAnyOrder(kept, ruledOutBySibling));
    }

    @Test
    @Story("Asking which of some documents survive")
    @DisplayName("Asking about thousands of documents names at most a thousand in each question, each a lookup by number, and asking about none asks nothing")
    void asksAPageAtATimeByKeyAndNothingAboutNothing() {
        StatementsIssued issued = new StatementsIssued(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        Ledger ledger = new Ledger(issued);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-many-asked-" + System.nanoTime()));
        List<OccurrenceId> occurrences = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            occurrences.add(occurrence(ledger, walk, "f" + i + ".txt"));
        }
        RunId run = ledger.runs().startRun("byte-level-reduction", "m" + System.nanoTime(), "{}", walk, List.of());
        Set<OccurrenceId> expected = new HashSet<>();
        for (int i = 0; i < MANY; i++) {
            if (i % EVERY_TENTH == 0) {
                ledger.verdicts().verdict(occurrences.get(i), run, VerdictKind.BROKEN, "ruled out by this test");
            } else {
                expected.add(occurrences.get(i));
            }
        }

        issued.keep = true;
        Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(run, occurrences);
        issued.keep = false;
        List<StatementsIssued.Asked> asks = issued.asked.stream()
                .filter(asked -> asked.sql().contains("FROM file_occurrence"))
                .toList();

        claim(
                "the answer is the " + expected.size() + " documents not ruled out",
                () -> assertThat(surviving).containsExactlyInAnyOrderElementsOf(expected));
        claim(
                "the " + MANY + " documents were asked about in at least three questions, none naming more than "
                        + A_PAGE,
                () -> {
                    assertThat(asks).hasSizeGreaterThanOrEqualTo(3);
                    assertThat(asks).allSatisfy(asked -> assertThat(occurrencesNamedBy(asked.sql())).isBetween(1, A_PAGE));
                });
        List<List<String>> plans = new ArrayList<>();
        for (StatementsIssued.Asked asked : asks) {
            plans.add(jdbcTemplate.query(
                    "EXPLAIN QUERY PLAN " + asked.sql(),
                    (resultSet, rowNumber) -> resultSet.getString("detail"),
                    asked.arguments()));
        }
        claim(
                "each question looks the documents up by their own numbers, and none searches the whole walk",
                () -> assertThat(plans).allSatisfy(plan -> {
                    assertThat(plan).anyMatch(detail -> detail.contains("file_occurrence USING INTEGER PRIMARY KEY (rowid=?)"));
                    assertThat(plan).noneMatch(detail -> detail.contains("file_occurrence_by_walk_and_size"));
                }));

        issued.asked.clear();
        issued.keep = true;
        Set<OccurrenceId> ofNothing = ledger.verdicts().survivingAmong(run, List.of());
        issued.keep = false;
        claim(
                "asking about no document answers nothing and asks the database nothing at all",
                () -> {
                    assertThat(ofNothing).isEmpty();
                    assertThat(issued.asked).isEmpty();
                });
    }

    private static OccurrenceId occurrence(Ledger ledger, WalkId walk, String name) {
        OccurrencePath path = new OccurrencePath(name);
        ledger.occurrences().fileOccurrence(walk, path, 1, WHEN, WHEN);
        return ledger.occurrences().occurrenceId(walk, path).orElseThrow();
    }

    /**
     * The placeholders inside the statement's list of occurrences, {@code id IN (…)} on {@code file_occurrence}'s
     * own column, and not those of {@code run_id IN (…)} or {@code kind IN (…)}; none where there is no such list.
     */
    private static int occurrencesNamedBy(String sql) {
        Matcher list = OCCURRENCE_LIST.matcher(sql);
        if (!list.find()) {
            return 0;
        }
        return (int) list.group(1).chars().filter(character -> character == '?').count();
    }

    /** A list of occurrences on the bare {@code id} column: not preceded by a letter, a digit, an underscore or a dot. */
    private static final Pattern OCCURRENCE_LIST = Pattern.compile("(?<![\\w.])id IN \\(([^)]*)\\)");

    /** The application's own access to the database, keeping each statement asked of it while told to. */
    private static final class StatementsIssued extends JdbcTemplate {

        record Asked(String sql, Object[] arguments) {}

        final List<Asked> asked = new ArrayList<>();
        boolean keep;

        StatementsIssued(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (keep) {
                asked.add(new Asked(sql, args));
            }
            return super.query(sql, rowMapper, args);
        }
    }
}
