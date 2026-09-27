package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Ollama runs its models on an NVIDIA GPU when the operator names {@code compose.gpu.yaml} beside
 * {@code compose.yaml}, and {@code compose.yaml} on its own asks Docker for no device (ADR-165).
 *
 * <p>Opt-in rather than the default because a device request the machine cannot satisfy stops
 * {@code docker compose up} with exit 1 and leaves Ollama created and never started; with the request in
 * {@code compose.yaml}, the README's one {@code up} line would fail on every machine without an NVIDIA
 * GPU Docker can reach. The {@code deploy} form rather than {@code gpus:}, and {@code count: all}
 * rather than {@code 1}: both pairs reach the GPU on the machine measured, and ADR-165 says why these.
 *
 * <p>The override carries the request and nothing else, so the image, the port and the restart policy
 * stay the ones {@code compose.yaml} names, among them the restart policy {@link SidecarRestartPolicyTest}
 * reads.
 *
 * <p>Read from the files themselves, with no Docker, as {@link SidecarRestartPolicyTest} reads them.
 * What Docker does with the request is ADR-165's probes, not something a unit test can show.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Link(name = "ADR-165", url = Adr.OLLAMA_IS_GIVEN_A_GPU_BY_AN_OVERRIDE_FILE, type = "adr")
class OllamaGpuOverrideTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** The file the operator names with a second {@code -f} to give Ollama the GPU, beside the first. */
    private static final Path GPU_FILE = Path.of("compose.gpu.yaml");

    /** The service that serves the models, and the only one given the GPU. */
    private static final String OLLAMA = "ollama";

    /**
     * Service keys through which a Compose service can ask Docker for a GPU on their own: {@code gpus},
     * {@code runtime: nvidia}, and a device name such as {@code nvidia.com/gpu=all} under {@code devices}.
     * The fourth way, a request under {@code deploy.resources.reservations.devices}, is checked apart,
     * because the rest of {@code deploy} (a memory limit, say) asks for no GPU and stays free.
     */
    private static final List<String> DEVICE_KEYS = List.of("gpus", "runtime", "devices");

    /** Docker's device driver for NVIDIA GPUs. */
    private static final String NVIDIA = "nvidia";

    /** Every GPU the driver can see, rather than a number of them. */
    private static final String EVERY_GPU = "all";

    /** The capability a device request names to be given a GPU. */
    private static final String GPU_CAPABILITY = "gpu";

    @Test
    @Story("The GPU is asked for only where the operator asks for it")
    @DisplayName("The compose file on its own asks for no GPU, so it starts on a machine without one")
    void composeYamlAloneAsksForNoDevice() throws IOException {
        Map<String, Map<String, Object>> services = services(COMPOSE_FILE);

        claim(
                "compose.yaml runs at least one service, so the claims below are about something",
                () -> assertThat(services).isNotEmpty());
        for (Map.Entry<String, Map<String, Object>> service : services.entrySet()) {
            claim(
                    service.getKey() + " asks Docker for no GPU in compose.yaml, since a request the"
                            + " machine cannot satisfy stops the whole start and leaves the service not running",
                    () -> {
                        assertThat(service.getValue()).doesNotContainKeys(DEVICE_KEYS.toArray(String[]::new));
                        assertThat(devices(service.getValue())).isNull();
                    });
        }
    }

    @Test
    @Story("The GPU is asked for only where the operator asks for it")
    @DisplayName("The GPU file gives the model server every NVIDIA GPU and changes nothing else")
    void theOverrideGivesOllamaEveryNvidiaGpu() throws IOException {
        claim(
                "compose.gpu.yaml sits beside compose.yaml, for an operator with an NVIDIA GPU to name"
                        + " with a second -f",
                () -> assertThat(GPU_FILE).isRegularFile());
        Map<String, Map<String, Object>> overridden = services(GPU_FILE);
        claim(
                "it gives a GPU to one service, the model server, and to nothing else",
                () -> assertThat(overridden).containsOnlyKeys(OLLAMA));
        claim(
                "that is a service compose.yaml runs, so the file adds to it rather than starting a"
                        + " second one",
                () -> assertThat(services(COMPOSE_FILE)).containsKey(OLLAMA));
        Map<String, Object> ollama = overridden.get(OLLAMA);
        claim(
                "the only thing it adds is the device request, so the image, the port and the restart"
                        + " policy stay the ones compose.yaml names",
                () -> assertThat(ollama).containsOnlyKeys("deploy"));
        claim(
                "the request is one device request, for every GPU the NVIDIA driver can see, as a GPU",
                () -> assertThat(devices(ollama))
                        .containsExactly(Map.of(
                                "driver", NVIDIA,
                                "count", EVERY_GPU,
                                "capabilities", List.of(GPU_CAPABILITY))));
    }

    /** {@code deploy.resources.reservations.devices} of one service, or {@code null} where any level is missing. */
    @SuppressWarnings("unchecked")
    private static List<Object> devices(Map<String, Object> service) {
        Object level = service;
        for (String key : List.of("deploy", "resources", "reservations", "devices")) {
            if (!(level instanceof Map<?, ?> map)) {
                return null;
            }
            level = map.get(key);
        }
        return (List<Object>) level;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> services(Path file) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(file));
        return (Map<String, Map<String, Object>>) compose.get("services");
    }
}
