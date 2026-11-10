package io.algernon.vespera.engine.ledger;

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
import java.util.Set;
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
 * (ADR-211 section 5). Stage 5f asks it of each partition's members, and stage 3 of each page of the walk's
 * occurrences (section 3).
 *
 * <p>It answers to the runs a run's survivors answer to (ADR-156), and to the run's walk; it names at most
 * 1,000 occurrences in a statement; it asks nothing about nothing; and each statement is a lookup by the
 * occurrences' own numbers, not a search of the walk (ADR-211 section 8, where the walk search cost every
 * ask a read of the whole walk).
 *
 * <p>It was parked under {@code docs/adr/0211/tests/} until the method existed, since it names it and would
 * have stopped the test tree compiling, and came into {@code src/test} with the commit that built ADR-211.
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

    private static OccurrenceId occurrence(Ledger ledger, WalkId walk, String name) {
        OccurrencePath path = new OccurrencePath(name);
        ledger.occurrences().fileOccurrence(walk, path, 1, WHEN, WHEN);
        return ledger.occurrences().occurrenceId(walk, path).orElseThrow();
    }

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
