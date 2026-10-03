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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Ollama keeps its models in a named volume, so replacing or removing its container keeps them
 * (ADR-179 §5).
 *
 * <p>The image declares no volume of its own, and keeps its models and its key pair under
 * {@code /root/.ollama}. Without a mount they lived in the container's own layer, so every {@code up}
 * that replaced the container, and every {@code down}, lost them, and gigabytes had to be fetched
 * again. On 2026-09-30 a single {@code up} from the wrong set of compose files did exactly that.
 *
 * <p>A named volume rather than a bind mount, because a bind ties {@code compose.yaml} to a host path
 * that differs on every machine. Not {@code external}, so the first {@code up} creates it. The whole of
 * {@code /root/.ollama}, so the key pair beside the models survives too.
 *
 * <p>Read from the files themselves, with no Docker, as {@link SidecarRestartPolicyTest} reads them.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("373")
@Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
class OllamaKeepsItsModelsTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** The file that gives the sidecars the GPU, named beside the first. */
    private static final Path GPU_FILE = Path.of("compose.gpu.yaml");

    /** The model server's service. */
    private static final String OLLAMA = "ollama";

    /** The volume its models live in. */
    private static final String MODELS_VOLUME = "ollama-models";

    /** Where the image keeps its models and its key pair. */
    private static final String OLLAMA_HOME = "/root/.ollama";

    @Test
    @Story("The model server keeps its models when its container is replaced")
    @DisplayName("The model server keeps its models in a volume of their own, which outlives any one container")
    void ollamaKeepsItsModelsInANamedVolume() throws IOException {
        Map<String, Object> compose = load(COMPOSE_FILE);
        Map<String, Object> ollama = service(compose, OLLAMA);

        claim(
                "compose.yaml mounts the volume " + MODELS_VOLUME + " at " + OLLAMA_HOME + ", where the model"
                        + " server keeps its models and its key pair, and nothing else",
                () -> assertThat(ollama.get("volumes"))
                        .as("the model server's mounts")
                        .isEqualTo(List.of(MODELS_VOLUME + ":" + OLLAMA_HOME)));
        @SuppressWarnings("unchecked")
        Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");
        claim(
                "the file declares that volume itself, so the first up creates it",
                () -> assertThat(volumes).as("the volumes compose.yaml declares").containsKey(MODELS_VOLUME));
        @SuppressWarnings("unchecked")
        Map<String, Object> declared = volumes == null || volumes.get(MODELS_VOLUME) == null
                ? Map.of()
                : (Map<String, Object>) volumes.get(MODELS_VOLUME);
        claim(
                "and declares it as a volume Docker manages: not one that must already exist, and not a folder"
                        + " on this machine, whose path would differ on every other",
                () -> {
                    assertThat(declared).as("how the volume is declared").doesNotContainKey("external");
                    assertThat(declared).as("how the volume is declared").doesNotContainKey("driver_opts");
                });
    }

    @Test
    @Story("The model server keeps its models when its container is replaced")
    @DisplayName("The GPU file gives the model server its card and leaves its volume alone")
    void theGpuOverrideLeavesOllamasVolumeAlone() throws IOException {
        Map<String, Object> ollama = service(load(GPU_FILE), OLLAMA);

        claim(
                "compose.gpu.yaml mounts nothing on the model server, so starting with or without it keeps the"
                        + " same volume and the same models",
                () -> assertThat(ollama).as("the GPU file's model server").doesNotContainKey("volumes"));
    }

    private static Map<String, Object> load(Path file) throws IOException {
        return new Yaml().load(Files.readString(file));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(Map<String, Object> compose, String name) {
        Map<String, Object> service = (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
        return service == null ? Map.of() : service;
    }
}
