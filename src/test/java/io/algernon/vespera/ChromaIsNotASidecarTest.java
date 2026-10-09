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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The operator starts no Chroma: neither compose file declares a service for it, runs its image or keeps
 * a volume for it (ADR-214, amending ADR-158, ADR-164 and ADR-179 where they name the Chroma sidecar).
 *
 * <p>The services the operator starts are the model server and the document converter, and nothing reads
 * a third. What an operator whose machine still holds a Chroma container from an earlier {@code up} may do
 * with it is in ADR-214; no test can see a machine.
 *
 * <p>Read from the files themselves, with no Docker, as {@link SidecarRestartPolicyTest} reads them.
 *
 * <p>Written before the change it pins: against the tree it was written on, the first test fails on the
 * {@code chroma} service. The second passes there, because Chroma never had a volume (ADR-179 §5), and is
 * here so that one is not added back with the service.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("352")
@Link(name = "ADR-214", url = Adr.CHROMA_IS_REMOVED_AND_VECTORS_LIVE_IN_SQLITE_ALONE, type = "adr")
class ChromaIsNotASidecarTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** Both files an operator may name to {@code up}: the one alone, or it and the GPU override. */
    private static final List<Path> COMPOSE_FILES = List.of(COMPOSE_FILE, Path.of("compose.gpu.yaml"));

    /** The word a service, an image or a volume for Chroma is named with, compared without regard to case. */
    private static final String CHROMA = "chroma";

    @Test
    @Story("The operator starts only the sidecars something reads")
    @DisplayName("Neither compose file declares a Chroma service or runs a Chroma image")
    void noComposeFileRunsChroma() throws IOException {
        Map<String, Map<String, Object>> services = services(COMPOSE_FILE);
        Set<String> forChroma = new TreeSet<>();
        for (Path file : COMPOSE_FILES) {
            for (Map.Entry<String, Map<String, Object>> service : services(file).entrySet()) {
                if (namedForChroma(service.getKey()) || namedForChroma(service.getValue().get("image"))) {
                    forChroma.add(file + ": " + service.getKey());
                }
            }
        }

        claim(
                "compose.yaml runs at least one service, so an empty answer below is not an empty file",
                () -> assertThat(services).isNotEmpty());
        claim(
                "no service in either compose file is named for Chroma or runs its image, so the up line the"
                        + " operator is given starts no vector database",
                () -> assertThat(forChroma).as("services still declared for Chroma").isEmpty());
    }

    @Test
    @Story("The operator starts only the sidecars something reads")
    @DisplayName("Neither compose file declares or mounts a volume for Chroma")
    void noComposeFileKeepsAVolumeForChroma() throws IOException {
        Set<String> forChroma = new TreeSet<>();
        for (Path file : COMPOSE_FILES) {
            Map<String, Object> compose = load(file);
            Object declared = compose.get("volumes");
            if (declared instanceof Map<?, ?> volumes) {
                for (Object name : volumes.keySet()) {
                    if (namedForChroma(name)) {
                        forChroma.add(file + ": volume " + name);
                    }
                }
            }
            for (Map.Entry<String, Map<String, Object>> service : services(file).entrySet()) {
                if (service.getValue().get("volumes") instanceof List<?> mounts) {
                    for (Object mount : mounts) {
                        if (namedForChroma(mount)) {
                            forChroma.add(file + ": " + service.getKey() + " mounts " + mount);
                        }
                    }
                }
            }
        }

        claim(
                "no volume either compose file declares, and none a service mounts, is named for Chroma, so"
                        + " nothing outlives a container for a vector database the application does not have",
                () -> assertThat(forChroma).as("volumes still kept for Chroma").isEmpty());
    }

    private static boolean namedForChroma(Object value) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(CHROMA);
    }

    private static Map<String, Object> load(Path file) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(file));
        return compose == null ? Map.of() : compose;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> services(Path file) throws IOException {
        Object services = load(file).get("services");
        return services == null ? Map.of() : (Map<String, Map<String, Object>>) services;
    }
}
