package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What each numeric key does with a value no number can be read from (ADR-120).
 *
 * <p>Three keys hold numbers, and before this they answered the same mistake three different ways:
 * the relevance floor ignored it and said so, stage 4's floor ended the invocation where it stood,
 * and stage 2's floor stopped the application from starting at all. None of that was decided
 * anywhere — it fell out of which reader had been written first, and whether that reader happened to
 * catch.
 *
 * <p>All three now leave the value unapplied and the work standing, which is what the profile being a
 * hand-edited file already implies: a person fixes a typo between invocations, and an invocation that
 * threw away a whole pass over one mistyped character would be charging them a great deal for it.
 *
 * <p><b>Unapplied is not unmentioned.</b> {@link NextActionTest} covers the other half — that the
 * closing line names the key and quotes back what was written — because a value quietly dropped is
 * worse than one that was never set.
 */
@Epic("Census")
@Feature("Profile")
@Issue("203")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-047", url = Adr.THE_PIPELINE_NEVER_BLOCKS, type = "adr")
class UnreadableFloorsTest {

    /** A decimal comma: the likeliest real mistake, and the one that used to be fatal in two keys. */
    private static final String A_DECIMAL_COMMA = "0,4";

    /** Why a test wrote the value, in the place an operator would explain themselves. */
    private static final String WHY = "set by this test, mistyped on purpose";

    @Test
    @Story("A mistyped threshold costs the value, never the invocation")
    @DisplayName("A mistyped repetition floor leaves that step waiting rather than ending the run")
    void aMistypedBoilerplateFloorLeavesTheGateShut(@TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(A_DECIMAL_COMMA, WHY)
                .build());
        RedundancyGate gate = new RedundancyGate(profileStore);

        claim(
                "asking for the floor does not raise: until now this read the value with no catch and no"
                        + " trimming, so one comma in one key ended the invocation partway through, with"
                        + " whatever it had already done left behind and nothing saying which value was at"
                        + " fault",
                () -> assertThatCode(gate::floor).doesNotThrowAnyException());
        claim(
                "and the step simply has no floor to work with, which is exactly the state it is in"
                        + " before anybody sets one -- the work already done stands, and the person fixes"
                        + " the value and runs again",
                () -> assertThat(gate.floor()).isEmpty());
    }

    @Test
    @Story("A mistyped threshold costs the value, never the invocation")
    @DisplayName("A mistyped conversion-quality floor still lets the tool start")
    void aMistypedConfidenceFloorStillStarts(@TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(A_DECIMAL_COMMA, WHY)
                .build());
        ExtractionJobConfiguration configuration = new ExtractionJobConfiguration();

        claim(
                "reading the floor does not raise, and that matters more here than anywhere else: this"
                        + " value is read while the tool is still wiring itself up, so a single mistyped"
                        + " character used to mean the tool would not start -- no invocation, no report,"
                        + " and no line naming the key that stopped it",
                () -> assertThatCode(() -> configuration.degenerateOutputConfidenceFloor(profileStore))
                        .doesNotThrowAnyException());
        claim(
                "and no floor is applied, which is how this key ships and how every corpus runs before"
                        + " anyone has measured a distribution to set it from",
                () -> assertThat(configuration.degenerateOutputConfidenceFloor(profileStore).value())
                        .isNull());
    }

    @Test
    @Story("Two readers of one value never disagree about it")
    @DisplayName("A mistyped relevance floor reads the same to the step and to the record of the work")
    void theStepAndTheRunIdentityAgree(@TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile()
                .relevanceScoreFloor(A_DECIMAL_COMMA, WHY)
                .build());

        claim(
                "the value that names the work carries no number, matching the step that would have"
                        + " applied one: two spellings of the same mistake have to name one piece of work,"
                        + " or the same corpus is recorded twice over for reasons nobody can see",
                () -> assertThat(RelevanceScoreFloorValue.readFrom(profileStore).value()).isNull());
    }
}
