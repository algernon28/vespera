package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The Docling sidecar's image is named in three places, and the three have to agree (ADR-147).
 *
 * <p>{@code compose.yaml} runs it, {@code TestcontainersConfiguration} tests against it, and
 * {@code application.yaml}'s {@code vespera.docling.image} is what the extractor identity carries. An
 * identity naming an image the sidecar is not running is a cache key that claims something untrue
 * about every row under it, which is the failure the image was put in the key to prevent.
 *
 * <p>Read from the files themselves, with no Docker: each fact here is a line an operator can edit.
 */
@Epic("Extraction")
@Feature("The Docling sidecar")
@Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
class DoclingSidecarImageTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** The service the sidecar runs as in it. */
    private static final String SERVICE = "docling-serve";

    @Test
    @Story("One image, named the same everywhere")
    @DisplayName("The image the sidecar runs, the image tests run, and the image the extraction cache names are one")
    void namesOneImageEverywhere() throws IOException {
        String composed = (String) service().get("image");

        claim(
                "the image the tests start is the image compose.yaml runs",
                () -> assertThat(TestcontainersConfiguration.DOCLING_SERVE_IMAGE).isEqualTo(composed));
        claim(
                "and it is the image the extractor identity names, so every cache row says truly which"
                        + " image made it",
                () -> assertThat(configuredImage()).isEqualTo(composed));
    }

    @Test
    @Story("The image with LibreOffice is built here and runs locked down")
    @DisplayName("compose.yaml builds the image from the repository and runs it with an init process and no privileges")
    void buildsTheImageAndRunsItLockedDown() throws IOException {
        Map<String, Object> service = service();
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) service.get("build");

        claim(
                "the image is built from the repository's own Containerfile, because no published image"
                        + " carries LibreOffice",
                () -> assertThat(Path.of((String) build.get("context")).resolve((String) build.get("dockerfile")))
                        .isRegularFile());
        claim(
                "it runs with an init process, without which every conversion leaves a dead soffice behind",
                () -> assertThat(service.get("init")).isEqualTo(true));
        claim(
                "every Linux capability is dropped, since converting a file needs none of them",
                () -> assertThat(service.get("cap_drop")).isEqualTo(List.of("ALL")));
        claim(
                "and no process in it can gain privileges it did not start with",
                () -> assertThat(service.get("security_opt")).isEqualTo(List.of("no-new-privileges:true")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE_FILE));
        return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(SERVICE);
    }

    @SuppressWarnings("unchecked")
    private static String configuredImage() throws IOException {
        try (InputStream in = DoclingSidecarImageTest.class.getResourceAsStream("/application.yaml")) {
            Map<String, Object> application = new Yaml().load(in);
            Map<String, Object> docling =
                    (Map<String, Object>) ((Map<String, Object>) application.get("vespera")).get("docling");
            return (String) docling.get("image");
        }
    }
}
