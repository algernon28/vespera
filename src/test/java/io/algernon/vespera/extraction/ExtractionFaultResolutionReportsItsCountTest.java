package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
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
 * The contract of {@code FaultResolutionProgress} (ADR-192 section 5, #412): {@code
 * ExtractionFaultResolution.resolve(RunId, boolean, FaultResolutionProgress)}, on a step that completed, calls
 * {@code toResolve(long)} once with the run's fault rows, before resolving the first, and {@code faultResolved()}
 * after each fault's verdict is written.
 *
 * <p>Since ADR-220, §14, a fault's row is written when it is recorded, so a step that stopped has nothing left to
 * do at its end: its rows stand, it judges none of them, and it announces no loop.
 *
 * <p><b>Part (b) of ADR-192.</b> Does not compile until the interface and the overload exist; part (b) moves it
 * into {@code src/test}. What is written is {@code ExtractionFaultResolutionTest}'s to pin.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
class ExtractionFaultResolutionReportsItsCountTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Stage 2 tells its caller how many faults it resolves")
    @DisplayName("Two recorded faults are announced once, and each is reported after its verdict is written, on a completed step")
    void announcesTheRecordedFaultsAndReportsEachOnCompletion() {
        reportsTwoFaults(true);
    }

    @Test
    @Story("Stage 2 tells its caller how many faults it resolves")
    @DisplayName("A step that stopped has nothing to resolve at its end, and announces nothing")
    @Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
    void announcesNothingOnAStop() {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = aRun(ledger);
        ExtractionFaultResolution resolution = new ExtractionFaultResolution(new ExtractionFaults(jdbcTemplate), ledger);
        resolution.record(anOccurrence(ledger, run, "first.pdf"), run, "internal", "the converter failed");
        List<String> events = new ArrayList<>();

        resolution.resolve(run, false, recording(events));

        claim(
                "the fault was written when it was recorded and stays unjudged, so the end of a stopped step"
                        + " announces no loop and reports nothing",
                () -> assertThat(events).isEmpty());
    }

    @Test
    @Story("Stage 2 tells its caller how many faults it resolves")
    @DisplayName("With no fault recorded the loop is announced with zero and nothing is reported")
    void announcesZeroWithNothingRecorded() {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = aRun(ledger);
        List<String> events = new ArrayList<>();

        new ExtractionFaultResolution(new ExtractionFaults(jdbcTemplate), ledger).resolve(run, true, recording(events));

        claim("the one call is the total, zero", () -> assertThat(events).containsExactly("to-resolve 0"));
    }

    private void reportsTwoFaults(boolean completed) {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = aRun(ledger);
        ExtractionFaultResolution resolution = new ExtractionFaultResolution(new ExtractionFaults(jdbcTemplate), ledger);
        resolution.record(anOccurrence(ledger, run, "first.pdf"), run, "internal", "the converter failed");
        resolution.record(anOccurrence(ledger, run, "second.pdf"), run, "capacity", "the converter was busy");
        List<String> events = new ArrayList<>();

        resolution.resolve(run, completed, recording(events));

        claim(
                "the loop is announced once with the run's two fault rows, before the first is resolved, and"
                        + " each is reported once after its verdict",
                () -> assertThat(events).containsExactly("to-resolve 2", "resolved", "resolved"));
    }

    private RunId aRun(Ledger ledger) {
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        return ledger.runs().startRun("extraction", "e" + System.nanoTime(), "{}", walk, List.of());
    }

    private OccurrenceId anOccurrence(Ledger ledger, RunId run, String path) {
        WalkId walk = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM run WHERE id = ?", Long.class, run.value()));
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private static FaultResolutionProgress recording(List<String> events) {
        return new FaultResolutionProgress() {
            @Override
            public void toResolve(long faults) {
                events.add("to-resolve " + faults);
            }

            @Override
            public void faultResolved() {
                events.add("resolved");
            }
        };
    }
}
