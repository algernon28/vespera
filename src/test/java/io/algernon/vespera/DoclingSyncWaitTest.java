package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.extraction.DoclingClient;
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
 * docling-serve answers a 504 once a conversion has waited its sync wait limit, queue time included,
 * rather than aborting. To ensure Vespera's 5-minute call timeout decides when a file is too slow
 * (marking it failed and moving on) rather than stopping the step over a 504 (ADR-172), this test
 * verifies docling-serve's limit is raised well above the call timeout.
 */
@Epic("Extraction")
@Feature("The sidecars")
@Link(name = "ADR-172", url = Adr.DOCLING_SERVES_SYNCHRONOUS_WAIT_OUTLASTS_VESPERAS_CALL_TIMEOUT, type = "adr")
@Issue("362")
class DoclingSyncWaitTest {

    private static final Path COMPOSE_FILE = Path.of("compose.yaml");
    private static final String MAX_SYNC_WAIT_VAR = "DOCLING_SERVE_MAX_SYNC_WAIT";

    @Test
    @Story("Wait limits align to keep long conversions from failing the stage")
    @DisplayName("docling-serve's synchronous wait limit is configured higher than Vespera's own call timeout")
    void doclingServeSyncWaitOutlastsVesperasCallTimeout() throws IOException {
        Map<String, Object> doclingServe = service("docling-serve");

        @SuppressWarnings("unchecked")
        Map<String, Object> environment = (Map<String, Object>) doclingServe.get("environment");

        claim(
                "docling-serve's environment declares " + MAX_SYNC_WAIT_VAR,
                () -> assertThat(environment).containsKey(MAX_SYNC_WAIT_VAR));

        String waitValueStr = environment.get(MAX_SYNC_WAIT_VAR).toString();
        long waitValue = Long.parseLong(waitValueStr);

        claim(
                "the wait value (" + waitValue + " s) is greater than Vespera's own call timeout ("
                        + DoclingClient.CALL_TIMEOUT.toSeconds() + " s), so a timeout fires Vespera-side first",
                () -> assertThat(waitValue).isGreaterThan(DoclingClient.CALL_TIMEOUT.toSeconds()));

        claim(
                "TestcontainersConfiguration sets the same value",
                () -> assertThat(TestcontainersConfiguration.DOCLING_SERVE_MAX_SYNC_WAIT).isEqualTo(waitValueStr));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(String name) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE_FILE));
        Map<String, Map<String, Object>> services = (Map<String, Map<String, Object>>) compose.get("services");
        return services.get(name);
    }
}
