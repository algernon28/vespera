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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The survivor set: the occurrences of a run's walk carrying no blocking verdict (ADR-057,
 * ADR-060).
 *
 * <p>Nothing in the census slice reads it — census writes no verdicts, so there is nothing yet to
 * survive — but stage 1 calls it on its first day, and a query nobody has run is a query nobody
 * knows is right.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Ledger")
@Feature("Survivors")
@Issue("10")
@Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class SurvivorsTest {

    /** Files this test records against the walk: two that survive, one that is ruled out. */
    private static final int OCCURRENCES_RECORDED = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What survives a stage")
    @DisplayName("An occurrence carrying a blocking verdict is not a survivor; one carrying none is")
    void excludesOnlyOccurrencesCarryingABlockingVerdict() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        OccurrenceId broken = record(ledger, walkId, "broken.txt");
        OccurrenceId passed = record(ledger, walkId, "passed.txt");
        OccurrenceId unjudged = record(ledger, walkId, "unjudged.txt");
        RunId runId = ledger.runs().startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());

        ledger.verdicts().verdict(broken, runId, VerdictKind.BROKEN, "zero bytes");
        ledger.verdicts().verdict(passed, runId, VerdictKind.PASSED, null);

        List<OccurrenceId> survivors = drain(ledger.verdicts().survivors(runId));

        claim(
                OCCURRENCES_RECORDED + " occurrences were recorded and one was ruled out, so two survive: the"
                        + " occurrence carrying the blocking verdict is the one that is gone",
                () -> assertThat(survivors).containsExactly(passed, unjudged));
    }

    @Test
    @Story("What survives a stage")
    @DisplayName("A blocking verdict from an earlier run still removes an occurrence from a later one")
    void survivalIsCumulativeAcrossRuns() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        OccurrenceId ruledOut = record(ledger, walkId, "broken.txt");
        OccurrenceId survivor = record(ledger, walkId, "fine.txt");

        RunId stageOne = ledger.runs().startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());
        ledger.verdicts().verdict(ruledOut, stageOne, VerdictKind.BROKEN, "zero bytes");
        RunId stageTwo = ledger.runs().startRun("extraction", "def456", "{}", walkId, List.of(stageOne));

        claim(
                "the later stage does not see what an earlier stage ruled out, which is what makes the"
                        + " cascade cumulative rather than a set of opinions",
                () -> assertThat(drain(ledger.verdicts().survivors(stageTwo))).containsExactly(survivor));
    }

    @Test
    @Story("Survival over a reused walk")
    @DisplayName("A verdict under a sibling run removes nothing from the other run's survivors")
    @Issue("297")
    @Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
    void aSiblingRunsVerdictRemovesNothing() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        OccurrenceId removedByTheStricterFloor = record(ledger, walkId, "irrelevant-at-1.5.txt");
        OccurrenceId neverRemoved = record(ledger, walkId, "relevant.txt");
        RunId measurement = ledger.runs().startRun("seed-measurement", "abc123", "{}", walkId, List.of());
        RunId stricter = ledger.runs().startRun(
                "embedding-scoring", "abc123", "{\"relevanceScoreFloor\":1.5}", walkId, List.of(measurement));
        RunId looser = ledger.runs().startRun(
                "embedding-scoring", "abc123", "{\"relevanceScoreFloor\":0.1}", walkId, List.of(measurement));

        ledger.verdicts().verdict(removedByTheStricterFloor, stricter, VerdictKind.BELOW_THRESHOLD, "under 1.5");

        claim(
                "the looser run's survivors still hold the document the stricter run removed: the two runs"
                        + " name the same upstream and neither reaches the other, so a floor lowered over a"
                        + " reused walk brings back what the higher one took (#297)",
                () -> assertThat(drain(ledger.verdicts().survivors(looser)))
                        .containsExactly(removedByTheStricterFloor, neverRemoved));
        claim(
                "and the stricter run's own survivors still lack it, because its verdict is kept rather than"
                        + " deleted, and counts again the moment an invocation arrives at that run",
                () -> assertThat(drain(ledger.verdicts().survivors(stricter))).containsExactly(neverRemoved));
    }

    @Test
    @Story("Survival over a reused walk")
    @DisplayName("A verdict two runs upstream still removes an occurrence")
    @Issue("297")
    @Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
    void aVerdictSeveralRunsUpstreamStillRemoves() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        OccurrenceId broken = record(ledger, walkId, "broken.txt");
        OccurrenceId redundant = record(ledger, walkId, "redundant.txt");
        OccurrenceId survivor = record(ledger, walkId, "fine.txt");
        RunId stageOne = ledger.runs().startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());
        RunId stageTwo = ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of(stageOne));
        RunId stageThree = ledger.runs().startRun("content-census", "abc123", "{}", walkId, List.of(stageTwo));
        RunId stageFour = ledger.runs().startRun("content-redundancy", "abc123", "{}", walkId, List.of(stageThree));
        RunId measurement = ledger.runs().startRun("seed-measurement", "abc123", "{}", walkId, List.of(stageFour));

        ledger.verdicts().verdict(broken, stageOne, VerdictKind.BROKEN, "zero bytes");
        ledger.verdicts().verdict(redundant, stageFour, VerdictKind.REDUNDANT_WITH, "says what fine.txt says");

        claim(
                "the measurement run's survivors lack what stage 1 and stage 4 removed, though it names only"
                        + " stage 4 upstream: the rule follows the upstream runs all the way back, which is"
                        + " what keeps the cascade cumulative",
                () -> assertThat(drain(ledger.verdicts().survivors(measurement))).containsExactly(survivor));
    }

    private static OccurrenceId record(Ledger ledger, WalkId walkId, String path) {
        ledger.occurrences().fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    /** Reads the survivors to the end, the way a step does but without a step. */
    private static List<OccurrenceId> drain(Iterable<OccurrenceId> survivors) {
        List<OccurrenceId> read = new ArrayList<>();
        for (OccurrenceId id : survivors) {
            read.add(id);
        }
        return read;
    }
}
