package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * What one stage is told when it asks which run of the stage before it to read (ADR-154, amending
 * ADR-099).
 *
 * <p>The answer is the run of that stage this invocation minted or continued, as the invocation
 * recorded it, and never a lookup over the walk. So there is at most one answer. ADR-099's test that
 * two runs over one walk stop the successor is retired with the lookup: over a reused walk two runs
 * are ordinary, and which one is meant is the one this invocation arrived at, which {@code
 * UpstreamRunOverAReusedWalkTest} pins through whole invocations.
 */
@Epic("Census")
@Feature("Ledger")
@Issue("290")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
class UpstreamRunsTest {

    /** A stage name a successor asks about — any stage before another would do. */
    private static final String A_STAGE = "content-redundancy";

    /** A run id, as the stage would have recorded it. */
    private static final RunId A_RUN = new RunId("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    @Story("Which run of the stage before it a stage reads")
    @DisplayName("A stage with no run this invocation leaves its successor nothing to name")
    void refusesWhenThisInvocationHoldsNoRunOfTheStage() {
        InvocationRuns nothingRecorded = new InvocationRuns(new ExecutionContext());

        claim(
                "asking for stage " + A_STAGE + "'s run when this invocation recorded none is a fault rather"
                        + " than an empty answer: a stage that named nothing upstream would fail later at the"
                        + " foreign key instead, and the message says it is a defect in the job, not a value"
                        + " to set",
                () -> assertThatThrownBy(() -> new UpstreamRuns(nothingRecorded).runOf(A_STAGE))
                        .isInstanceOf(NoUpstreamRunException.class)
                        .hasMessageContaining(A_STAGE)
                        .hasMessageContaining("defect"));
    }

    @Test
    @Story("Which run of the stage before it a stage reads")
    @DisplayName("The run this invocation recorded for a stage is the id its successor reads")
    void readsBackTheRunThisInvocationRecorded() {
        InvocationRuns invocation = new InvocationRuns(new ExecutionContext());
        invocation.record(A_STAGE, A_RUN);

        claim(
                "the run this invocation recorded for stage " + A_STAGE + " comes back as its own id, so a"
                        + " successor reads exactly the run the invocation arrived at rather than one"
                        + " re-derived from inputs that could drift from what was written",
                () -> assertThat(new UpstreamRuns(invocation).runOf(A_STAGE)).isEqualTo(A_RUN));
    }
}
