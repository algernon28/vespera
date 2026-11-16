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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The share of a survivor's chunks a topic must be nearest to for the survivor to fit it (ADR-400 §4): a preset under
 * ADR-330 §8's rule, shipped at {@value #SHARE}, and refused, as every preset is, where no run can be made under it.
 * It has no profile key, as the topics' own numbers have none (ADR-399 §3).
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("828")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
class TheSeeAlsoSharePresetTest {

    /** The share, measured from 10% to 25% on seed sets of 24 to 1,496 passages under two embedders. */
    private static final double SHARE = 0.15;

    private static final String SHARE_KEY = "vespera.topics.see-also-share";

    /** A profile naming nothing, so that what configuration names applies. */
    private static final Profile NOTHING_NAMED = ProfileFixture.profile().build();

    @Test
    @Story("Other topics a survivor fits link to it")
    @DisplayName("A survivor fits another topic where 15% of its chunks are nearest to it, as shipped")
    void shipsTheMeasuredValue() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader()
                .load("the shipped file", new ClassPathResource("application.yaml"))
                .forEach(environment.getPropertySources()::addLast);

        claim("application.yaml names a share of 0.15 of a survivor's chunks",
                () -> assertThat(new Presets(environment).seeAlsoShare()).isEqualTo(SHARE));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A share of 0, or of more than the whole, for another topic a survivor fits is refused with the line naming its key and value")
    void refusesValuesNoRunCanBeMadeUnder() {
        claim("a share of 0 is refused as a number above 0 and at most 1 is asked for",
                () -> assertThat(new Presets(PresetsFixture.shippedEnvironment().withProperty(SHARE_KEY, "0"))
                                .refusal(NOTHING_NAMED))
                        .contains(SHARE_KEY + " is 0: a number above 0 and at most 1"));
        claim("and a share of 1.5 as the same is",
                () -> assertThat(new Presets(PresetsFixture.shippedEnvironment().withProperty(SHARE_KEY, "1.5"))
                                .refusal(NOTHING_NAMED))
                        .contains(SHARE_KEY + " is 1.5: a number above 0 and at most 1"));
    }
}
