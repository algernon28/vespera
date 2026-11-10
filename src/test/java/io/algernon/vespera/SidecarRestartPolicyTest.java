package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Every sidecar {@code compose.yaml} runs is started again by Docker when it dies, and stays stopped
 * when the operator stopped it (ADR-164).
 *
 * <p>{@code unless-stopped} and not {@code on-failure}, which leaves the sidecars down after the machine
 * restarts, nor {@code on-failure:N}, whose count a healthy stretch never resets, nor {@code always},
 * which starts again at the next restart of the engine a sidecar the operator stopped. ADR-164 records
 * the probes behind each of those; what Docker does is not something a unit test can show.
 *
 * <p>The policy brings the sidecar back for the next invocation. It does not let a step survive the
 * sidecar dying under it: both steps that call Docling fail on the first call the dying sidecar drops,
 * unchanged, and whether to wait and retry is still open on #326.
 *
 * <p>Read from the file itself, with no Docker, as {@link DoclingSidecarImageTest} reads it.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Link(name = "ADR-164", url = Adr.EVERY_SIDECAR_RESTARTS_UNLESS_THE_OPERATOR_STOPPED_IT, type = "adr")
@Issue("326")
class SidecarRestartPolicyTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /**
     * Docker's policy that restarts a container which exits on its own, including after the machine
     * restarts, and leaves one that was stopped by hand stopped.
     */
    private static final String UNLESS_STOPPED = "unless-stopped";

    @Test
    @Story("A service that dies comes back, and one stopped by hand stays stopped")
    @DisplayName("Every service compose.yaml runs is restarted by Docker when it exits on its own, and not after the operator stops it")
    void everySidecarComesBackUnlessTheOperatorStoppedIt() throws IOException {
        Map<String, Map<String, Object>> services = services();

        claim(
                "compose.yaml runs at least one service, so the claims below are about something",
                () -> assertThat(services).isNotEmpty());
        for (Map.Entry<String, Map<String, Object>> service : services.entrySet()) {
            claim(
                    service.getKey() + " is restarted by Docker when it crashes and when the machine"
                            + " restarts, and stays stopped once the operator stops it (restart: "
                            + UNLESS_STOPPED + ")",
                    () -> assertThat(service.getValue().get("restart")).isEqualTo(UNLESS_STOPPED));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> services() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE_FILE));
        return (Map<String, Map<String, Object>>) compose.get("services");
    }
}
