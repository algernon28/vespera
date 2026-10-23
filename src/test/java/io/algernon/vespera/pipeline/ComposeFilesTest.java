package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The compose files Vespera starts its sidecars from are the jar's own copy of the repository's, so that the jar
 * needs nothing from the checkout, and each file's converter image is read from the file (ADR-323 §2 and §4).
 *
 * <p>The copy is compared with the repository's files byte for byte: a build that left one out, or a resource
 * filter that rewrote one, would start the sidecars from files that are not the ones the README and the tests read.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class ComposeFilesTest {

    /** The repository root, which is where Maven runs the tests from. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final String COMPOSE_FILE = "compose.yaml";

    private static final String GPU_FILE = "compose.gpu.yaml";

    /** The folder holding the converter's build context, which {@code compose.yaml} names relative to itself. */
    private static final String BUILD_FOLDER = "docker";

    @Test
    @Story("The jar carries the files it starts the sidecars from")
    @DisplayName("The compose files written out from the jar are the repository's two compose files and every file of the converter's build folder, byte for byte and nothing else")
    void theFilesWrittenOutAreTheRepositorysOwn() throws IOException {
        Map<String, String> repository = theFilesIn(REPOSITORY);
        Path writtenTo;
        Map<String, String> writtenOut;
        try (ComposeFiles files = ComposeFiles.fromTheClasspath()) {
            writtenTo = files.folder();
            writtenOut = theFilesIn(writtenTo);
        }

        claim("the folder written out holds compose.yaml, compose.gpu.yaml and every file under docker/, by the same"
                        + " relative paths and with the same bytes as the repository's, and nothing else",
                () -> assertThat(writtenOut).containsExactlyEntriesOf(repository));
        claim("and once the files are closed the folder they were written to is gone", () -> assertThat(writtenTo)
                .doesNotExist());
    }

    @Test
    @Story("The configured converter image chooses the compose files")
    @DisplayName("Each compose file's converter image is the one its docling-serve service names")
    void eachFilesImageIsTheOneItsConverterNames() throws IOException {
        String cpu = doclingImageIn(REPOSITORY.resolve(COMPOSE_FILE));
        String gpu = doclingImageIn(REPOSITORY.resolve(GPU_FILE));
        ComposeFiles files = ComposeFiles.in(REPOSITORY);

        claim("the processor image is the one compose.yaml's docling-serve names", () -> assertThat(files.cpuImage())
                .isEqualTo(cpu));
        claim("the graphics-card image is the one compose.gpu.yaml's docling-serve names", () -> assertThat(files.gpuImage())
                .isEqualTo(gpu));
        claim("and the two differ, so a configured image chooses one file set and never both", () -> assertThat(cpu)
                .isNotEqualTo(gpu));
    }

    @Test
    @Story("The jar carries the files it starts the sidecars from")
    @DisplayName("Compose files read where they already lie are left there when closed")
    void filesReadWhereTheyLieAreNotDeleted() {
        ComposeFiles.in(REPOSITORY).close();

        claim("the repository's compose.yaml is still there: closing deletes only a folder the files were written to",
                () -> assertThat(REPOSITORY.resolve(COMPOSE_FILE)).isRegularFile());
    }

    /** The two compose files and every file under the build folder, by path relative to {@code root}, to their bytes. */
    private static Map<String, String> theFilesIn(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        for (String name : new String[] {COMPOSE_FILE, GPU_FILE}) {
            files.put(name, Files.readString(root.resolve(name), java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        try (Stream<Path> walk = Files.walk(root.resolve(BUILD_FOLDER))) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1));
            }
        }
        if (root.equals(REPOSITORY)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.putIfAbsent(root.relativize(file).toString().replace('\\', '/'), "(not in the repository)");
            }
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private static String doclingImageIn(Path composeFile) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(composeFile));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }
}
