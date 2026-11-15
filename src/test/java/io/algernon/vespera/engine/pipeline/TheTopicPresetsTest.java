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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The two numbers the topics are found at (ADR-399): presets under ADR-330 §8's rule, shipped at the values measured,
 * {@value #NEIGHBOURS} neighbours and a resolution of {@value #RESOLUTION}, and refused, as every preset is, where no
 * run can be made under them. Neither has a profile key: how many topics there are is never the operator's to set
 * (ADR-396 §1).
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("826")
@Link(name = "ADR-399", url = Adr.TOPICS_ARE_COMMUNITIES_OF_THE_SEED_PASSAGES, type = "adr")
class TheTopicPresetsTest {

    /** The neighbours each passage keeps, measured best of 5 to 30 over seed sets of 24 to 2,702 passages. */
    private static final int NEIGHBOURS = 10;

    /** The resolution, measured best of 0.5 to 2.0 beside those neighbours. */
    private static final double RESOLUTION = 1.0;

    private static final String NEIGHBOURS_KEY = "vespera.topics.neighbours";

    private static final String RESOLUTION_KEY = "vespera.topics.resolution";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    @Test
    @Story("The topics are found at two preset numbers")
    @DisplayName("The topics are found at ten neighbours and a resolution of 1.0 as shipped")
    void shipsTheMeasuredValues() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("the shipped file", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        Presets presets = new Presets(environment);

        claim("application.yaml names ten neighbours and then a resolution of 1.0 for the topics",
                () -> assertThat(List.of(presets.topicNeighbours(), presets.topicResolution()))
                        .containsExactly(NEIGHBOURS, RESOLUTION));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("No neighbours, or a resolution of 0, for the topics is refused with the line naming its key and value")
    void refusesValuesNoRunCanBeMadeUnder() {
        claim("no neighbours is refused as a whole number from 1 is asked for",
                () -> assertThat(new Presets(PresetsFixture.shippedEnvironment().withProperty(NEIGHBOURS_KEY, "0"))
                                .refusal(NOTHING_NAMED))
                        .contains(NEIGHBOURS_KEY + " is 0: a whole number from 1 to 2147483647"));
        claim("and a resolution of 0 as a number above 0 is",
                () -> assertThat(new Presets(PresetsFixture.shippedEnvironment().withProperty(RESOLUTION_KEY, "0"))
                                .refusal(NOTHING_NAMED))
                        .contains(RESOLUTION_KEY + " is 0: a number above 0"));
    }
}
