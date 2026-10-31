package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * What the pull reads from configuration (ADR-329 §4, §6 and §9): whether to pull at all, where Ollama is, and how
 * long a pull may go without moving.
 */
@Epic("Relevance")
@Feature("A model Ollama does not serve is pulled")
@Issue("669")
@Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
class ModelPullSettingsTest {

    /** How long a pull may go without moving unless configured: ten minutes. */
    private static final Duration TEN_MINUTES = Duration.ofSeconds(600);

    /** Where Ollama is looked for unless configured, as Spring AI has it. */
    private static final URI OLLAMA_DEFAULT = URI.create("http://localhost:11434");

    @Test
    @Story("The operator can keep pulling to themselves")
    @DisplayName("Unless configured otherwise, a missing model is pulled, from Ollama on its usual port, and given up after ten minutes without moving")
    void theDefaults() {
        ModelPullSettings settings = ModelPullSettings.from(new MockEnvironment());

        claim("pulling is on", () -> assertThat(settings.pull()).isTrue());
        claim("Ollama is looked for on localhost's port 11434", () -> assertThat(settings.ollamaBaseUrl())
                .isEqualTo(OLLAMA_DEFAULT));
        claim("and a pull that has not moved for ten minutes is given up", () -> assertThat(settings.stalledAtMost())
                .isEqualTo(TEN_MINUTES));
    }

    @Test
    @Story("The operator can keep pulling to themselves")
    @DisplayName("Pulling is turned off by vespera.models.pull, Ollama moved by its base URL, and the bound set by vespera.models.pull-stalled-at-most")
    void eachSettingIsRead() {
        ModelPullSettings settings = ModelPullSettings.from(new MockEnvironment()
                .withProperty("vespera.models.pull", "false")
                .withProperty("spring.ai.ollama.base-url", "http://127.0.0.1:11500")
                .withProperty("vespera.models.pull-stalled-at-most", "90s"));

        claim("pulling is off", () -> assertThat(settings.pull()).isFalse());
        claim("Ollama is looked for at the base URL configured", () -> assertThat(settings.ollamaBaseUrl())
                .isEqualTo(URI.create("http://127.0.0.1:11500")));
        claim("and the bound is the ninety seconds configured", () -> assertThat(settings.stalledAtMost())
                .isEqualTo(Duration.ofSeconds(90)));
    }

    @Test
    @Story("The operator can keep pulling to themselves")
    @DisplayName("Starting the sidecars turned off leaves pulling on")
    void startingTheSidecarsOffLeavesPullingOn() {
        ModelPullSettings settings =
                ModelPullSettings.from(new MockEnvironment().withProperty("vespera.sidecars.start", "false"));

        claim(
                "a machine whose sidecars are started some other way still has a missing model pulled",
                () -> assertThat(settings.pull()).isTrue());
    }
}
