package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What stage 2's concurrency settles (ADR-140): the conversion width is a fixed default of eight in
 * code, a number ADR-140 fixes the way ADR-071 fixed its timeout and streak counts — an operational
 * parameter about the machine, not a corpus judgement, so it is a code constant rather than a Profile
 * gate.
 *
 * <p>The run-stopping condition, read in the order outcomes were observed, is {@link
 * ExtractionCircuitBreakerTest}'s: a streak of set-asides with nothing between them sends the control
 * conversion and stops a run whose control conversion fails, and one document the converter answered
 * about ends a streak (ADR-184). That the shipped step keeps this many conversions in flight, and
 * converts somewhere other than the thread it was invoked on, is {@link ExtractionStepTest}'s claim,
 * because no unit can make it.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("264")
@Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
class ExtractionConcurrencyTest {

    /** ADR-140: how many Docling calls stage 2 keeps in flight, fixed in code rather than gated. */
    private static final int CONVERSIONS_IN_FLIGHT = 8;

    @Test
    @Story("How many conversions run at once")
    @DisplayName("Stage 2 keeps eight conversions in flight, as a fixed default in code")
    void theConversionWidthIsAFixedDefaultOfEightInCode() {
        claim(
                "the conversion width is " + CONVERSIONS_IN_FLIGHT + " — the number the code fixes rather than"
                        + " one a person sets per corpus — because how many conversions a machine can run at"
                        + " once is a fact about that machine, not about the archive being judged",
                () -> assertThat(ExtractionJobConfiguration.CONVERSION_CONCURRENCY)
                        .isEqualTo(CONVERSIONS_IN_FLIGHT));
    }
}
