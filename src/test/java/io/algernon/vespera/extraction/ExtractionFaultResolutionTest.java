package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The end-of-step resolution of extraction faults (ADR-139 sections 2 and 3), in {@code extraction} since
 * ADR-189: every set-aside occurrence is held until the step ends, then written as a fault row, and only
 * where the step completed is each one also resolved into {@code extraction-failed}, its reason the
 * category and the converter's message as {@code category: message}.
 *
 * <p>When it runs, in which transaction, and how the step's exit status is read stay {@code pipeline}'s,
 * in {@code ExtractionFaultRecorder}. Each claim here was true of that class's {@code afterStep} before
 * the move.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
class ExtractionFaultResolutionTest {

    /** Two set-aside occurrences, so a resolution that wrote only the first or the last would show. */
    private static final int TWO_HELD = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A file set aside is resolved at the end of the stage")
    @DisplayName("Holding a set-aside file writes nothing until the end")
    void holdingWritesNothing() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, 1);
        ExtractionFaultResolution resolution = resolution();

        boolean emptyBefore = resolution.nothingHeld();
        resolution.hold(occurrences.get(0), "capacity", "no room");

        claim(
                "before anything is held there is nothing to resolve",
                () -> assertThat(emptyBefore).isTrue());
        claim(
                "and holding one writes neither a fault row nor a verdict: the chunk it happened in is still"
                        + " open, and a second writer beside it is what the end of the stage exists to avoid",
                () -> {
                    assertThat(resolution.nothingHeld()).isFalse();
                    assertThat(count("extraction_fault")).isZero();
                    assertThat(count("verdict")).isZero();
                });
    }

    @Test
    @Story("A file set aside is resolved at the end of the stage")
    @DisplayName("A stage that completed records every set-aside file and resolves each into a verdict naming the category and the converter's message")
    void aCompletedStageRecordsAndResolvesEveryHeldFault() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, TWO_HELD);
        ExtractionFaultResolution resolution = resolution();
        resolution.hold(occurrences.get(0), "capacity", "no room for this call");
        resolution.hold(occurrences.get(1), "timeout", "the converter ran out of time");

        resolution.resolve(occurrences.run(), true);

        claim(
                "each of the " + TWO_HELD + " is a fault row under the run, carrying the category and the"
                        + " message as the converter gave them",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT category, detail FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id",
                                occurrences.run().value()))
                        .containsExactly(
                                Map.of("category", "capacity", "detail", "no room for this call"),
                                Map.of("category", "timeout", "detail", "the converter ran out of time")));
        claim(
                "and each is resolved into extraction-failed, the reason the category, a colon and the message,"
                        + " the shape every other such reason has",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT kind, reason FROM verdict WHERE run_id = ? ORDER BY occurrence_id",
                                occurrences.run().value()))
                        .containsExactly(
                                Map.of("kind", "EXTRACTION_FAILED", "reason", "capacity: no room for this call"),
                                Map.of("kind", "EXTRACTION_FAILED", "reason", "timeout: the converter ran out of time")));
    }

    @Test
    @Story("A file set aside is resolved at the end of the stage")
    @DisplayName("A stage that stopped records every set-aside file and judges none of them")
    void aStoppedStageRecordsAndJudgesNothing() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, TWO_HELD);
        ExtractionFaultResolution resolution = resolution();
        resolution.hold(occurrences.get(0), "internal", "the converter failed");
        resolution.hold(occurrences.get(1), "internal", "the converter failed again");

        resolution.resolve(occurrences.run(), false);

        claim(
                "both are recorded as fault rows, so the next invocation knows to ask about them again",
                () -> assertThat(count("extraction_fault")).isEqualTo(TWO_HELD));
        claim(
                "and neither is judged: in a stage that stopped, the converter did not answer for the files"
                        + " around them, so nothing says the refusal was about the file",
                () -> assertThat(count("verdict")).isZero());
    }

    private ExtractionFaultResolution resolution() {
        return new ExtractionFaultResolution(new ExtractionFaults(jdbcTemplate), new Ledger(jdbcTemplate));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
