package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TheShippedCopy;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.mock.env.MockEnvironment;

/**
 * The subtopic floor (ADR-406 §1, §5): a preset under ADR-330 §8's rule, shipped at the count measured,
 * {@value #FLOOR} survivors placed under a topic, and refused, as every preset is, where no run can be made under it.
 * It has no profile key: how the arrangement divides is never the operator's to set (ADR-396 §1, §3).
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("831")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class TheSubtopicFloorPresetTest {

    /**
     * The survivors a topic must hold to be divided into subtopics: the smallest count measured at which every topic's
     * clusters, found over the whole topic, held a median of more than 32 documents, under both embedders.
     */
    private static final int FLOOR = 300;

    private static final String KEY = "vespera.clustering.subtopic-floor";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic must hold 300 survivors to be divided into subtopics, as shipped")
    void shipsTheMeasuredValue() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("the shipped file", TheShippedCopy.of("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);

        claim("application.yaml names a subtopic floor of 300 survivors",
                () -> assertThat(new Presets(environment).subtopicFloor()).isEqualTo(FLOOR));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A subtopic floor of no survivors, or of no whole number, is refused with the line naming its key and value")
    void refusesValuesNoRunCanBeMadeUnder() {
        claim("a floor of 0 is refused as a whole number from 1 is asked for",
                () -> assertThat(refusalOf("0"))
                        .contains(KEY + " is 0: a whole number from 1 to 2147483647"));
        claim("and so is a floor that is no whole number",
                () -> assertThat(refusalOf("2.5"))
                        .contains(KEY + " is 2.5: a whole number from 1 to 2147483647"));
        claim("a floor of 1, which divides every topic of two communities or more, is not refused",
                () -> assertThat(refusalOf("1")).isEmpty());
    }

    private static Optional<String> refusalOf(String value) {
        return new Presets(PresetsFixture.shippedEnvironment().withProperty(KEY, value)).refusal(NOTHING_NAMED);
    }
}
