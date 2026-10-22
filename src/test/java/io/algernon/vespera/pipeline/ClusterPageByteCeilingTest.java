package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.ClusterPageCeiling;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the profile's {@code clusterPageByteCeiling} says, read one way for the drawing and for the closing line (ADR-303
 * §2), in the shape of ADR-282 §3's word floor: unset, a whole number of bytes from 1 to {@code Long.MAX_VALUE} read
 * exactly from its text, or something ignored, with its text kept to quote back; and what the tree is handed of it, a
 * value of {@code deliverable}'s own carrying no text (§11).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class ClusterPageByteCeilingTest {

    /** The provenance every value here is written with. */
    private static final String WRITTEN_BY_THIS_TEST = "written by this test";

    /** The key, as the profile spells it. */
    private static final String THE_KEY = "clusterPageByteCeiling";

    @Test
    @Story("The ceiling on a group's page, as the profile holds it")
    @DisplayName("The key is clusterPageByteCeiling, the same name in the profile, in its reading and in what the tree is handed")
    void theKeyIsNamedTheSameEverywhere() {
        claim(
                "the reading and the tree's value each name the key as profile.yaml spells it",
                () -> assertThat(java.util.List.of(ClusterPageByteCeiling.KEY, ClusterPageCeiling.KEY))
                        .containsExactly(THE_KEY, THE_KEY));
        claim(
                "and the profile has a key of that name",
                () -> assertThat(Arrays.stream(Profile.class.getRecordComponents()).map(RecordComponent::getName))
                        .contains(THE_KEY));
    }

    @Test
    @Story("The ceiling on a group's page, as the profile holds it")
    @DisplayName("An unset ceiling reads as unset, and the tree is handed no ceiling")
    void anUnsetCeilingIsUnset() {
        ClusterPageByteCeiling ceiling = ClusterPageByteCeiling.of(NumericValue.unset());

        claim(
                "the key as census drafts it reads as unset",
                () -> assertThat(ceiling).isEqualTo(new ClusterPageByteCeiling.Unset()));
        claim(
                "and the tree is handed unset, so no page is split",
                () -> assertThat(ceiling.forTheTree()).isEqualTo(new ClusterPageCeiling.Unset()));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"1", " 300000 ", "2.0", "1e3", "9223372036854775807"})
    @Story("The ceiling on a group's page, as the profile holds it")
    @DisplayName("A whole number of bytes from 1 up is the ceiling, read exactly from what is written, and the tree is handed it")
    void aWholeNumberOfBytesIsTheCeiling(String written) {
        ClusterPageByteCeiling ceiling = ClusterPageByteCeiling.of(new NumericValue(written, WRITTEN_BY_THIS_TEST, null));
        long bytes = new java.math.BigDecimal(written.trim()).longValueExact();

        claim(
                "\"" + written + "\" is the ceiling of " + bytes + " bytes, read exactly, so the largest a long holds is"
                        + " itself and not a number rounded to a double",
                () -> assertThat(ceiling).isEqualTo(new ClusterPageByteCeiling.Ceiling(bytes)));
        claim(
                "and the tree is handed those bytes",
                () -> assertThat(ceiling.forTheTree()).isEqualTo(new ClusterPageCeiling.Bytes(bytes)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "-1", "1.5", "NaN", "Infinity", "-Infinity", "big", "1,000", "9223372036854775808"})
    @Story("A ceiling that is not a whole number of bytes splits nothing")
    @DisplayName("A ceiling that is not a whole number of bytes from 1 to the largest a long holds is ignored, with what was written kept to quote back")
    void anythingElseIsIgnored(String written) {
        ClusterPageByteCeiling ceiling = ClusterPageByteCeiling.of(new NumericValue(written, WRITTEN_BY_THIS_TEST, null));

        claim(
                "\"" + written + "\" is no whole number of bytes from 1 to " + Long.MAX_VALUE + ", so it is ignored, and"
                        + " what was written is kept to be quoted back",
                () -> assertThat(ceiling).isEqualTo(new ClusterPageByteCeiling.Ignored(written)));
        claim(
                "and the tree is handed ignored, with no text, so no page is split and the report says only that",
                () -> assertThat(ceiling.forTheTree()).isEqualTo(new ClusterPageCeiling.Ignored()));
    }
}
