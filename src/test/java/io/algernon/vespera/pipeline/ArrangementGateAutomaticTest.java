package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What {@link ArrangementGate} opens on when {@code arrangementApproved} holds {@code auto} (ADR-317), beside what
 * it opens on when the value names an arrangement (ADR-107, ADR-154 §2).
 */
@Epic("Pipeline")
@Feature("Gates")
@Link(name = "ADR-317", url = Adr.AN_APPROVAL_OF_AUTO_OPENS_THE_GATE_ON_EVERY_ARRANGEMENT, type = "adr")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
class ArrangementGateAutomaticTest {

    /** One arrangement's id: sixty-four hexadecimal characters, as every run id is. */
    private static final RunId ONE_ARRANGEMENT = new RunId("9f2c41ab77de" + "0".repeat(52));

    /** Another arrangement's id, sharing no prefix with the first. */
    private static final RunId ANOTHER_ARRANGEMENT = new RunId("0123456789ab" + "f".repeat(52));

    @TempDir
    Path workingDirectory;

    @Test
    @Story("A standing approval writes over every arrangement without asking")
    @DisplayName("With the approval set to auto, the gate opens on whichever arrangement the invocation arrived at")
    void autoOpensOnAnyArrangement() {
        ArrangementGate gate = gateWithApproval("auto");

        claim("the gate opens on one arrangement",
                () -> assertThat(gate.approvedArrangement(Optional.of(ONE_ARRANGEMENT))).contains(ONE_ARRANGEMENT));
        claim("and on another, so a re-arrangement does not shut it",
                () -> assertThat(gate.approvedArrangement(Optional.of(ANOTHER_ARRANGEMENT)))
                        .contains(ANOTHER_ARRANGEMENT));
    }

    @Test
    @Story("A standing approval writes over every arrangement without asking")
    @DisplayName("The word auto is read in any case and with spaces around it")
    void autoIsReadInAnyCase() {
        claim("written in capitals with spaces around it, it still opens the gate",
                () -> assertThat(gateWithApproval(" AUTO ").approvedArrangement(Optional.of(ONE_ARRANGEMENT)))
                        .contains(ONE_ARRANGEMENT));
    }

    @Test
    @Story("A standing approval writes over every arrangement without asking")
    @DisplayName("With the approval set to auto and nothing arranged, the gate stays shut")
    void autoWithNothingArrangedStaysShut() {
        claim("there is nothing to write over, so nothing is opened",
                () -> assertThat(gateWithApproval("auto").approvedArrangement(Optional.empty())).isEmpty());
    }

    @Test
    @Story("An approval is of one arrangement, and does not carry over to the next")
    @DisplayName("An approval by name still opens only on the arrangement it names")
    void aNamedApprovalStillOpensOnlyOnItsOwn() {
        ArrangementGate gate = gateWithApproval(ArrangementGate.shortNameOf(ONE_ARRANGEMENT));

        claim("the gate opens on the arrangement the name was copied from",
                () -> assertThat(gate.approvedArrangement(Optional.of(ONE_ARRANGEMENT))).contains(ONE_ARRANGEMENT));
        claim("and stays shut on any other",
                () -> assertThat(gate.approvedArrangement(Optional.of(ANOTHER_ARRANGEMENT))).isEmpty());
    }

    private ArrangementGate gateWithApproval(String approval) {
        ProfileStore store = new ProfileStore(workingDirectory);
        store.save(ProfileFixture.profile().arrangementApproved(approval, "written by this test").build());
        return new ArrangementGate(store);
    }
}
