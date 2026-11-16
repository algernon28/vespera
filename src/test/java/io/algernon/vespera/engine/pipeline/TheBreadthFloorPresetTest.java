package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The breadth floor (ADR-401): a preset under ADR-330 §8's rule, as ADR-396 §1 has it, shipped at the share measured,
 * {@value #FLOOR} of the seed passages, and refused, as every preset is, where no run can be made under it. It has no
 * profile key: how many topics there are is never the operator's to set (ADR-396 §1).
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("827")
@Link(name = "ADR-401", url = Adr.A_TOPIC_UNDER_THE_BREADTH_FLOOR_IS_DROPPED, type = "adr")
class TheBreadthFloorPresetTest {

    /**
     * The share of the seed passages a topic must hold to stand: half of one percent, the highest share that dropped no
     * subject the topics recovered in any measured seed set of 24 to 5,512 passages.
     */
    private static final double FLOOR = 0.005;

    private static final String KEY = "vespera.topics.breadth-floor";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("A topic must hold half of one percent of the seed passages to stand, as shipped")
    void shipsTheMeasuredValue() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("the shipped file", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);

        claim("application.yaml names a breadth floor of half of one percent of the seed passages",
                () -> assertThat(new Presets(environment).topicBreadthFloor()).isEqualTo(FLOOR));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A breadth floor of a whole share or more, or below nothing, is refused with the line naming its key and value")
    void refusesValuesNoRunCanBeMadeUnder() {
        claim("a floor of the whole of the passages is refused as a share below 1 is asked for",
                () -> assertThat(refusalOf("1")).contains(KEY + " is 1: a number from 0 and below 1"));
        claim("and so is a floor below nothing",
                () -> assertThat(refusalOf("-0.01")).contains(KEY + " is -0.01: a number from 0 and below 1"));
        claim("and so is a value that is no number",
                () -> assertThat(refusalOf("NaN")).contains(KEY + " is NaN: a number from 0 and below 1"));
        claim("a floor of 0, which keeps every topic, is not refused",
                () -> assertThat(refusalOf("0")).isEmpty());
    }

    private static Optional<String> refusalOf(String value) {
        return new Presets(PresetsFixture.shippedEnvironment().withProperty(KEY, value)).refusal(NOTHING_NAMED);
    }
}
