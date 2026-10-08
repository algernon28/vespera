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
 * ExtractionFaultResolution.resolve(RunId, boolean, FaultResolutionProgress)} calls {@code toResolve(long)}
 * once with the faults held, before writing the first, and {@code faultResolved()} after each fault's row,
 * and its verdict where the step completed, is written.
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
    @DisplayName("Two held faults are announced once, and each is reported after it is written, on a completed step")
    void announcesTheHeldFaultsAndReportsEachOnCompletion() {
        reportsTwoFaults(true);
    }

    @Test
    @Story("Stage 2 tells its caller how many faults it resolves")
    @DisplayName("Two held faults are announced and reported the same way on a step that stopped, which writes no verdict")
    void announcesTheHeldFaultsAndReportsEachOnAStop() {
        reportsTwoFaults(false);
    }

    @Test
    @Story("Stage 2 tells its caller how many faults it resolves")
    @DisplayName("With no fault held the loop is announced with zero and nothing is reported")
    void announcesZeroWithNothingHeld() {
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
        resolution.hold(anOccurrence(ledger, run, "first.pdf"), "internal", "the converter failed");
        resolution.hold(anOccurrence(ledger, run, "second.pdf"), "capacity", "the converter was busy");
        List<String> events = new ArrayList<>();

        resolution.resolve(run, completed, recording(events));

        claim(
                "the loop is announced once with the two faults held, before the first is written, and each is"
                        + " reported once after it",
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
