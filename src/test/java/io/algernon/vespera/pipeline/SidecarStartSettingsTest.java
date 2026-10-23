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
 * What the starter reads from configuration (ADR-322 §2, §7 and §9): whether to start the sidecars at all, how long to
 * wait for them, the converter image that chooses the compose files, and where each sidecar is looked for.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-322", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class SidecarStartSettingsTest {

    /** How long a wait for a sidecar or the engine lasts unless configured: three minutes. */
    private static final Duration THREE_MINUTES = Duration.ofSeconds(180);

    private static final String DOCLING_URL = "http://localhost:5001";

    private static final String IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** Where Ollama is looked for unless configured, as Spring AI has it. */
    private static final URI OLLAMA_DEFAULT = URI.create("http://localhost:11434");

    @Test
    @Story("The operator can leave the sidecars to themselves")
    @DisplayName("Unless configured otherwise, the sidecars are started, waited for three minutes, and Ollama is looked for on its usual port")
    void theDefaults() {
        SidecarStartSettings settings = SidecarStartSettings.from(new MockEnvironment()
                .withProperty("vespera.docling.base-url", DOCLING_URL)
                .withProperty("vespera.docling.image", IMAGE));

        claim("starting them is on", () -> assertThat(settings.start()).isTrue());
        claim("a wait lasts three minutes", () -> assertThat(settings.waitAtMost()).isEqualTo(THREE_MINUTES));
        claim("the converter is looked for where vespera.docling.base-url says", () -> assertThat(settings.doclingBaseUrl())
                .isEqualTo(URI.create(DOCLING_URL)));
        claim("Ollama on localhost's port 11434", () -> assertThat(settings.ollamaBaseUrl()).isEqualTo(OLLAMA_DEFAULT));
        claim("and the image is vespera.docling.image, the value the conversions are recorded under",
                () -> assertThat(settings.doclingImage()).isEqualTo(IMAGE));
    }

    @Test
    @Story("The operator can leave the sidecars to themselves")
    @DisplayName("Starting the sidecars is turned off by vespera.sidecars.start, the wait set by vespera.sidecars.wait-at-most, and Ollama moved by its base URL")
    void eachSettingIsRead() {
        SidecarStartSettings settings = SidecarStartSettings.from(new MockEnvironment()
                .withProperty("vespera.sidecars.start", "false")
                .withProperty("vespera.sidecars.wait-at-most", "1s")
                .withProperty("vespera.docling.base-url", DOCLING_URL)
                .withProperty("vespera.docling.image", IMAGE)
                .withProperty("spring.ai.ollama.base-url", "http://127.0.0.1:11500"));

        claim("starting them is off", () -> assertThat(settings.start()).isFalse());
        claim("a wait lasts the one second configured", () -> assertThat(settings.waitAtMost())
                .isEqualTo(Duration.ofSeconds(1)));
        claim("and Ollama is looked for at the base URL configured", () -> assertThat(settings.ollamaBaseUrl())
                .isEqualTo(URI.create("http://127.0.0.1:11500")));
    }
}
