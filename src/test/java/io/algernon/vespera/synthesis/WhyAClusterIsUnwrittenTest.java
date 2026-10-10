package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Why a cluster with no synthesis doc is unwritten, decided in {@code synthesis} (ADR-226, moving ADR-222's
 * rule 5; ADR-174 §4).
 *
 * <p>What this invocation found stands over a fault on record, a fault on record stands over nothing, and a
 * cluster with neither was not reached. The fault on record is asked for only where this invocation found
 * nothing, which is the read ADR-223 left: a cluster this invocation already explained costs no lookup.
 *
 * <p>Written before {@code Unwritten.of(Optional, Supplier)} exists, so this class does not compile until the
 * build does.
 */
@Epic("Synthesis")
@Feature("A page nothing was written over")
@Issue("479")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
class WhyAClusterIsUnwrittenTest {

    @Test
    @Story("What this invocation found is why a cluster is unwritten now")
    @DisplayName("What this invocation found stands over a fault on record, which is not even read")
    void whatThisInvocationFoundStands() {
        AtomicInteger read = new AtomicInteger();

        Unwritten why = Unwritten.of(
                Optional.of(Unwritten.NOTHING_FITS_THE_WINDOW),
                counted(read, Optional.of(new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "an earlier answer"))));

        claim(
                "the cluster is unwritten for what this invocation found, since a page naming an earlier"
                        + " answer's reason would have the reader expect running again to help",
                () -> assertThat(why).isEqualTo(Unwritten.NOTHING_FITS_THE_WINDOW));
        claim(
                "and the fault on record is not read for a cluster this invocation already explained",
                () -> assertThat(read).hasValue(0));
    }

    @ParameterizedTest(name = "a {0} fault on record")
    @EnumSource(ClusterFaultKind.class)
    @Story("A fault on record says why a cluster is unwritten")
    @DisplayName("Where this invocation found nothing, the fault on record says why, by its kind")
    void aFaultOnRecordSaysWhy(ClusterFaultKind kind) {
        AtomicInteger read = new AtomicInteger();

        Unwritten why = Unwritten.of(Optional.empty(), counted(read, Optional.of(new ClusterFault(kind, "a detail"))));

        claim(
                "the sentence is the one for the recorded fault's kind",
                () -> assertThat(why).isEqualTo(Unwritten.of(kind)));
        claim("and the fault on record is read once", () -> assertThat(read).hasValue(1));
    }

    @Test
    @Story("A cluster the walk never reached says so")
    @DisplayName("With nothing found and no fault on record, the cluster was not reached")
    void neitherIsNotReached() {
        claim(
                "a cluster with no doc, nothing found this invocation and no fault on record was never reached:"
                        + " the walk stopped before it",
                () -> assertThat(Unwritten.of(Optional.empty(), Optional::empty)).isEqualTo(Unwritten.NOT_REACHED));
    }

    /** {@code fault}, counting each time it is read. */
    private static Supplier<Optional<ClusterFault>> counted(AtomicInteger read, Optional<ClusterFault> fault) {
        return () -> {
            read.incrementAndGet();
            return fault;
        };
    }
}
