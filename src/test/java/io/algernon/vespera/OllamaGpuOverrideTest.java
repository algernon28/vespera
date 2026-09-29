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
 * Ollama and the Docling sidecar run on an NVIDIA GPU when the operator names {@code compose.gpu.yaml}
 * beside {@code compose.yaml}, and {@code compose.yaml} on its own asks Docker for no device (ADR-165,
 * as ADR-170 amends it).
 *
 * <p>Opt-in rather than the default because a device request the machine cannot satisfy stops
 * {@code docker compose up} with exit 1 and leaves the service created and never started; with the
 * request in {@code compose.yaml}, the README's one {@code up} line would fail on every machine without
 * an NVIDIA GPU Docker can reach. The {@code deploy} form rather than {@code gpus:}, and
 * {@code count: all} rather than {@code 1}: both pairs reach the GPU on the machine measured, and
 * ADR-165 says why these.
 *
 * <p>For Ollama the override carries the request and nothing else, so its image, port and restart
 * policy stay the ones {@code compose.yaml} names, among them the restart policy
 * {@link SidecarRestartPolicyTest} reads. For the Docling sidecar it carries the request, an image name
 * and a build argument, and nothing else: the GPU build converts slightly differently and the sidecar
 * cannot say which image it is, so it needs a tag of its own (ADR-170), while its build context, its
 * lockdown, its port and its restart policy stay {@code compose.yaml}'s.
 *
 * <p>Read from the files themselves, with no Docker, as {@link SidecarRestartPolicyTest} reads them.
 * What Docker does with the request is ADR-165's probes and ADR-170's measurements, not something a
 * unit test can show.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Link(name = "ADR-165", url = Adr.OLLAMA_IS_GIVEN_A_GPU_BY_AN_OVERRIDE_FILE, type = "adr")
class OllamaGpuOverrideTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** The file the operator names with a second {@code -f} to give Ollama the GPU, beside the first. */
    private static final Path GPU_FILE = Path.of("compose.gpu.yaml");

    /** The service that serves the models, and one of the two given the GPU. */
    private static final String OLLAMA = "ollama";

    /** The service that converts documents, and the other one given the GPU (ADR-170). */
    private static final String DOCLING = "docling-serve";

    /** The build argument the sidecar's {@code Containerfile} takes its base from (ADR-170). */
    private static final String DOCLING_BASE_ARG = "DOCLING_SERVE_BASE";

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
    @DisplayName("The GPU file gives the model server and the document converter every NVIDIA GPU, and nothing else")
    @Link(name = "ADR-170", url = Adr.DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN, type = "adr")
    void theOverrideGivesOllamaAndDoclingEveryNvidiaGpu() throws IOException {
        claim(
                "compose.gpu.yaml sits beside compose.yaml, for an operator with an NVIDIA GPU to name"
                        + " with a second -f",
                () -> assertThat(GPU_FILE).isRegularFile());
        Map<String, Map<String, Object>> overridden = services(GPU_FILE);
        claim(
                "it gives a GPU to two services, the model server and the document converter, and to"
                        + " nothing else",
                () -> assertThat(overridden).containsOnlyKeys(OLLAMA, DOCLING));
        claim(
                "both are services compose.yaml runs, so the file adds to them rather than starting"
                        + " second ones",
                () -> assertThat(services(COMPOSE_FILE)).containsKeys(OLLAMA, DOCLING));
        Map<String, Object> ollama = overridden.get(OLLAMA);
        claim(
                "for the model server the only thing it adds is the device request, so the image, the port"
                        + " and the restart policy stay the ones compose.yaml names",
                () -> assertThat(ollama).containsOnlyKeys("deploy"));
        claim(
                "the model server's request is one device request, for every GPU the NVIDIA driver can"
                        + " see, as a GPU",
                () -> assertThat(devices(ollama)).containsExactly(everyNvidiaGpu()));
        Map<String, Object> docling = overridden.get(DOCLING);
        claim(
                "for the document converter it adds an image name of its own, a build argument and the"
                        + " device request, and nothing else, so the build context, the lockdown, the port"
                        + " and the restart policy stay the ones compose.yaml names",
                () -> assertThat(docling).containsOnlyKeys("image", "build", "deploy"));
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) docling.get("build");
        claim(
                "its build adds build arguments and nothing else, so the build context and the"
                        + " Containerfile stay the ones compose.yaml names, and the GPU build is built from"
                        + " the same file as the processor build",
                () -> assertThat(build).containsOnlyKeys("args"));
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) build.get("args");
        claim(
                "and the one build argument is the base the Containerfile is built on, so nothing"
                        + " but the base differs between the two builds",
                () -> assertThat(args).containsOnlyKeys(DOCLING_BASE_ARG));
        claim(
                "the document converter's request is the same one device request, for every GPU the"
                        + " NVIDIA driver can see, as a GPU",
                () -> assertThat(devices(docling)).containsExactly(everyNvidiaGpu()));
    }

    /** The one device request both services carry: every GPU the NVIDIA driver can see, as a GPU. */
    private static Map<String, Object> everyNvidiaGpu() {
        return Map.of("driver", NVIDIA, "count", EVERY_GPU, "capabilities", List.of(GPU_CAPABILITY));
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
