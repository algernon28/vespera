package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.NumericValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the profile's {@code degenerateOutputWordFloor} says, read one way for stage 2's run and for the closing line
 * (ADR-282 section 3): unset, a whole number of words from 0 to 2,147,483,647, or something ignored, as ADR-120
 * ignores an unreadable number and ADR-185 an attempt no attempt is numbered by.
 */
@Epic("Extraction")
@Feature("Text with nothing in it to score")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class DegenerateOutputWordFloorTest {

    /** The provenance every value here is written with. */
    private static final String WRITTEN_BY_THIS_TEST = "written by this test";

    /** The largest floor that is read: the largest whole number an {@code int} holds. */
    private static final long THE_LARGEST_FLOOR = Integer.MAX_VALUE;

    @Test
    @Story("The word floor, as the profile holds it")
    @DisplayName("The key as the profile names it is degenerateOutputWordFloor")
    void theKeyIsNamedBesideTheConfidenceFloor() {
        claim(
                "the key is named for the verdict it decides and the measure it reads, beside the confidence floor's",
                () -> assertThat(DegenerateOutputWordFloor.KEY).isEqualTo("degenerateOutputWordFloor"));
    }

    @Test
    @Story("The word floor, as the profile holds it")
    @DisplayName("An unset word floor reads as unset, and gives the run nothing to record")
    void anUnsetFloorIsUnset() {
        DegenerateOutputWordFloor floor = DegenerateOutputWordFloor.of(NumericValue.unset());

        claim(
                "the key as census drafts it reads as unset",
                () -> assertThat(floor).isEqualTo(new DegenerateOutputWordFloor.Unset()));
        claim(
                "and stage 2's run records no word floor for it, so its settings are those it had before the key",
                () -> assertThat(floor.identityMember()).isNull());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "1", "2", " 2 ", "2.0", "2147483647"})
    @Story("The word floor, as the profile holds it")
    @DisplayName("A whole number of words from 0 up is the floor, and the run records it")
    void aWholeNumberIsTheFloor(String written) {
        DegenerateOutputWordFloor floor = DegenerateOutputWordFloor.of(new NumericValue(written, WRITTEN_BY_THIS_TEST, null));
        long words = (long) Double.parseDouble(written.trim());

        claim(
                "\"" + written + "\" is the floor of " + words + " words",
                () -> assertThat(floor).isEqualTo(new DegenerateOutputWordFloor.Floor(words)));
        claim(
                "and stage 2's run records that number",
                () -> assertThat(floor.identityMember()).isEqualTo(words));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"two", "2,5", "2.5", "-1", "NaN", "Infinity", "-Infinity", "2147483648"})
    @Story("A word floor that is not a whole number changes nothing")
    @DisplayName("A word floor that is not a whole number of 0 or more is ignored, with what was written kept to quote back")
    void anythingElseIsIgnored(String written) {
        DegenerateOutputWordFloor floor = DegenerateOutputWordFloor.of(new NumericValue(written, WRITTEN_BY_THIS_TEST, null));

        claim(
                "\"" + written + "\" is no whole number of words from 0 to " + THE_LARGEST_FLOOR + ", so it is ignored,"
                        + " and what was written is kept to be quoted back",
                () -> assertThat(floor).isEqualTo(new DegenerateOutputWordFloor.Ignored(written)));
        claim(
                "an ignored floor removes nothing, so stage 2's run records none, as for an unset one",
                () -> assertThat(floor.identityMember()).isNull());
    }
}
