package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * {@link Verdicts#eachPageOfSurvivorsByPath}: what stage 2's survivor list is read from (ADR-250 section 3).
 * It hands over a run's survivors, the ones {@link Verdicts#survivors} hands out and no others, each with its
 * path, in path order, a page of at most {@value KeysetPages#ROWS_IN_A_PAGE} at a time, and no page sorts:
 * each is a range of the index {@code UNIQUE (walk_id, path)} makes, begun after the last path read.
 *
 * <p>In the first test the order recorded is fixed to disagree with the paths, and each thing the read must
 * leave out sits beside what it must hand over. In the second the plans are those of the statements the read
 * really issues, captured with their arguments as {@code ReadsInIdOrderSortNothingTest} captures them.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Link(name = "ADR-250", url = Adr.STAGE_2_WRITES_THE_SURVIVOR_LIST_BESIDE_THE_REVIEW_LIST, type = "adr")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class SurvivorsByPathTest {

    /** Recorded first and named last of the three that survive, so the two orders disagree. */
    private static final String RECORDED_FIRST = "reports/c-recorded-first.pdf";

    /** Recorded second and named first. */
    private static final String RECORDED_SECOND = "reports/a-recorded-second.pdf";

    /** Removed under the run asked. */
    private static final String REMOVED_UNDER_THE_RUN = "reports/b-removed-under-the-run.pdf";

    /** Given the verdict degenerate output under the run asked, and so removed under it (ADR-250 section 1). */
    private static final String REMOVED_AS_DEGENERATE_OUTPUT = "reports/f-removed-as-degenerate-output.pdf";

    /** Removed under the run upstream of the run asked. */
    private static final String REMOVED_UPSTREAM = "reports/d-removed-upstream.pdf";

    /** Removed only under another run of the same stage, which the run asked does not read. */
    private static final String REMOVED_UNDER_A_SIBLING = "reports/e-removed-under-a-sibling.pdf";

    /** Found by another walk, and named before every path of the walk read. */
    private static final String OF_ANOTHER_WALK = "reports/0-of-another-walk.pdf";

    /**
     * Four paths that are only strings here, no file being made for them, so the test reads the same on a
     * file system that tells case apart and on one that does not. Two differ only in case, and one is the
     * start of another. Compared byte by byte, as the review list's order is, the capital comes before every
     * small letter, and a path comes before every path it begins: {@code A.txt}, {@code a}, {@code
     * a-recorded-second.pdf}, {@code a.txt}, {@code a/b}, the hyphen, the full stop and the slash following
     * one another in that order. Compared without regard to case, {@code A.txt} would come after {@code
     * a-recorded-second.pdf}.
     */
    private static final String IN_CAPITALS = "reports/A.txt";

    private static final String IN_SMALL_LETTERS = "reports/a.txt";

    private static final String THE_START_OF_ANOTHER = "reports/a";

    private static final String BEGUN_BY_ANOTHER = "reports/a/b";

    /** Two full pages and a short third: the 2,250 that survive. */
    private static final List<Integer> THE_SIZES_OF_THE_PAGES = List.of(1_000, 1_000, 250);

    private static final Instant WHEN = Instant.parse("2026-10-13T10:15:30Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The documents a piece of work left standing, by name")
    @DisplayName("Asking for the documents still standing by name returns them in the order of their names, without the ones removed here or earlier, and with the ones removed only by work this one does not read")
    void handsOverTheRunsSurvivorsInPathOrderAndNothingElse() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-of-249-" + System.nanoTime()));
        WalkId anotherWalk = ledger.walks().startWalk(Path.of("C:/another-corpus-of-249-" + System.nanoTime()));
        RunId upstream = ledger.runs().startRun("byte-level-reduction", "v" + System.nanoTime(), "{}", walk, List.of());
        RunId asked = ledger.runs().startRun("extraction", "v" + System.nanoTime(), "{}", walk, List.of(upstream));
        RunId sibling =
                ledger.runs().startRun("extraction", "another v" + System.nanoTime(), "{}", walk, List.of(upstream));
        OccurrenceId first = record(ledger, walk, RECORDED_FIRST);
        OccurrenceId second = record(ledger, walk, RECORDED_SECOND);
        OccurrenceId removedUnderTheRun = record(ledger, walk, REMOVED_UNDER_THE_RUN);
        OccurrenceId removedUpstream = record(ledger, walk, REMOVED_UPSTREAM);
        OccurrenceId removedUnderASibling = record(ledger, walk, REMOVED_UNDER_A_SIBLING);
        OccurrenceId begunByAnother = record(ledger, walk, BEGUN_BY_ANOTHER);
        OccurrenceId inSmallLetters = record(ledger, walk, IN_SMALL_LETTERS);
        OccurrenceId theStartOfAnother = record(ledger, walk, THE_START_OF_ANOTHER);
        OccurrenceId inCapitals = record(ledger, walk, IN_CAPITALS);
        OccurrenceId removedAsDegenerateOutput = record(ledger, walk, REMOVED_AS_DEGENERATE_OUTPUT);
        record(ledger, anotherWalk, OF_ANOTHER_WALK);
        ledger.verdicts().verdict(removedUnderTheRun, asked, VerdictKind.EXTRACTION_FAILED, "removed under the run");
        ledger.verdicts()
                .verdict(removedAsDegenerateOutput, asked, VerdictKind.DEGENERATE_OUTPUT, "removed as degenerate output");
        ledger.verdicts().verdict(removedUpstream, upstream, VerdictKind.BROKEN, "removed upstream");
        ledger.verdicts().verdict(removedUnderASibling, sibling, VerdictKind.DEGENERATE_OUTPUT, "removed by a sibling");

        List<OccurrenceAtPath> handedOver = new ArrayList<>();
        ledger.verdicts().eachPageOfSurvivorsByPath(asked, handedOver::addAll);

        claim(
                "the seven documents still standing are returned, each with its name, in the order of their names"
                        + " and not the order recorded; the one removed only by work this one does not read is"
                        + " among them; and names are compared character by character, so the name in capitals"
                        + " comes before every name in small letters, and a name comes before the name it begins;"
                        + " and nothing else, so not the document removed by this work, the one it removed as"
                        + " degenerate output (which does not go on to the work after this one), the one removed by"
                        + " the work before it, or the one another walk found",
                () -> assertThat(handedOver)
                        .containsExactly(
                                new OccurrenceAtPath(inCapitals, new OccurrencePath(IN_CAPITALS)),
                                new OccurrenceAtPath(theStartOfAnother, new OccurrencePath(THE_START_OF_ANOTHER)),
                                new OccurrenceAtPath(second, new OccurrencePath(RECORDED_SECOND)),
                                new OccurrenceAtPath(inSmallLetters, new OccurrencePath(IN_SMALL_LETTERS)),
                                new OccurrenceAtPath(begunByAnother, new OccurrencePath(BEGUN_BY_ANOTHER)),
                                new OccurrenceAtPath(first, new OccurrencePath(RECORDED_FIRST)),
                                new OccurrenceAtPath(removedUnderASibling, new OccurrencePath(REMOVED_UNDER_A_SIBLING))));
        List<OccurrenceId> inIdOrder = new ArrayList<>();
        ledger.verdicts().survivors(asked).forEach(inIdOrder::add);
        claim(
                "they are the same documents the read in the order recorded hands out: there is one answer to"
                        + " which documents are still standing, asked in two orders",
                () -> assertThat(handedOver)
                        .extracting(OccurrenceAtPath::occurrenceId)
                        .containsExactlyInAnyOrderElementsOf(inIdOrder));
    }

    private static OccurrenceId record(Ledger ledger, WalkId walk, String path) {
        OccurrencePath occurrencePath = new OccurrencePath(path);
        ledger.occurrences().fileOccurrence(walk, occurrencePath, 1, WHEN, WHEN);
        return ledger.occurrences().occurrenceId(walk, occurrencePath).orElseThrow();
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
