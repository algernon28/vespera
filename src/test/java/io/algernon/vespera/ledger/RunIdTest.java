package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A run's identity is derived from what it read, not minted from a sequence (ADR-048).
 *
 * <p>That is the whole difference between a run and a walk, and it only buys anything if it holds
 * exactly: two runs that would produce identical verdicts must be recognisable as the same run, and
 * two that would not must never collide. A walk's identity is a surrogate key for the opposite
 * reason — an observation of a filesystem is not determined by anything the tool holds.
 */
@Epic("Ledger")
@Feature("Run identity")
@Issue("8")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
class RunIdTest {

    private static final WalkId WALK = new WalkId(1);
    private static final RunId UPSTREAM_A = new RunId("aaaa");
    private static final RunId UPSTREAM_B = new RunId("bbbb");

    @Test
    @Story("What determines a run's identity")
    @DisplayName("The same inputs mint the same run identity")
    void isDeterministic() {
        RunId first = RunId.of("v1", "{}", WALK, List.of(UPSTREAM_A, UPSTREAM_B));
        RunId second = RunId.of("v1", "{}", WALK, List.of(UPSTREAM_A, UPSTREAM_B));

        claim(
                "a run derived from the same version, configuration, walk and upstream runs has the same"
                        + " identity, which is what makes re-running an unchanged stage recognisable as such",
                () -> assertThat(second).isEqualTo(first));
    }

    @Test
    @Story("What determines a run's identity")
    @DisplayName("The order upstream runs are named in does not change the identity")
    void isOrderIndependentOverUpstreamRuns() {
        RunId namedInOneOrder = RunId.of("v1", "{}", WALK, List.of(UPSTREAM_A, UPSTREAM_B));
        RunId namedInTheOther = RunId.of("v1", "{}", WALK, List.of(UPSTREAM_B, UPSTREAM_A));

        claim(
                "the set of runs read is what determines the output; the order a caller happened to list"
                        + " them in is not, so both orders mint one identity rather than two",
                () -> assertThat(namedInTheOther).isEqualTo(namedInOneOrder));
    }

    @Test
    @Story("What determines a run's identity")
    @DisplayName("Changing any one of the four inputs changes the identity")
    void changesWithEachInput() {
        RunId original = RunId.of("v1", "{}", WALK, List.of(UPSTREAM_A));

        claim(
                "a different implementation version is a different run: the code that judged changed",
                () -> assertThat(RunId.of("v2", "{}", WALK, List.of(UPSTREAM_A))).isNotEqualTo(original));
        claim(
                "a different configuration is a different run: the thresholds it judged by changed",
                () -> assertThat(RunId.of("v1", "{\"threshold\":1}", WALK, List.of(UPSTREAM_A)))
                        .isNotEqualTo(original));
        claim(
                "a different walk is a different run: the occurrences it judged changed",
                () -> assertThat(RunId.of("v1", "{}", new WalkId(2), List.of(UPSTREAM_A))).isNotEqualTo(original));
        claim(
                "a different set of upstream runs is a different run: what it read changed",
                () -> assertThat(RunId.of("v1", "{}", WALK, List.of(UPSTREAM_A, UPSTREAM_B)))
                        .isNotEqualTo(original));
    }

    @Test
    @Story("What determines a run's identity")
    @DisplayName("Two inputs cannot be run together to look like one")
    void doesNotConfuseAdjacentInputs() {
        RunId versionThenConfig = RunId.of("ab", "c", WALK, List.of());
        RunId splitDifferently = RunId.of("a", "bc", WALK, List.of());

        claim(
                "the same characters split differently between version and configuration mint different"
                        + " identities, because the inputs are separated rather than concatenated",
                () -> assertThat(splitDifferently).isNotEqualTo(versionThenConfig));
    }
}
