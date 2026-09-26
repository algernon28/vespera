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
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The packaged jar starts no sidecar, because it carries no compose support (ADR-158).
 *
 * <p>The operator starts the sidecars from {@code compose.yaml} and then runs this jar. That split
 * holds only while Spring Boot's Docker Compose support stays out of the jar. Two settings of the
 * Boot plugin's {@code repackage} goal keep it out, each on its own. {@code excludeDockerCompose},
 * {@code true} by default, strips {@code spring-boot-docker-compose} whatever its scope.
 * {@code includeOptional}, {@code false} by default, leaves out every {@code <optional>} dependency,
 * and both {@code spring-boot-docker-compose} and {@code spring-ai-spring-boot-docker-compose} are
 * {@code <optional>} in {@code pom.xml}. Only both changes together, {@code excludeDockerCompose}
 * at {@code false} and {@code spring-boot-docker-compose} let through, give the jar a compose
 * support that looks for a {@code compose.yaml} wherever it is launched from, and starts or stops
 * containers on every command. That reverses the decision without a word.
 *
 * <p>This test fails whenever either compose artifact is nested, which is stricter than that. Spring
 * AI's artifact holds only connection-details factories and starts nothing, so letting it in alone
 * leaves the jar starting nothing. The test fails on it anyway, so that any change to what the jar
 * carries here comes back to ADR-158.
 *
 * <p>It is an integration test only because the packaged jar exists only after {@code package}. It
 * needs no Docker daemon, and it launches nothing.
 */
@Epic("Architecture")
@Feature("Sidecars")
@Issue("304")
@Link(name = "ADR-158", url = Adr.THE_PACKAGED_JAR_STARTS_NO_SIDECAR, type = "adr")
@Link(name = "ADR-011", url = Adr.THE_TOOL_OWNS_ITS_SIDECARS, type = "adr")
class PackagedJarIT {

    /** The packaged, executable jar the operator runs, at the path README.md names. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** Where the Boot plugin puts the jar's dependencies, one nested jar each. */
    private static final String LIBRARIES = "BOOT-INF/lib/";

    /** The two artifacts that bring {@code compose.yaml} up, as the Boot plugin names their jars. */
    private static final List<String> COMPOSE_SUPPORT =
            List.of("spring-boot-docker-compose-", "spring-ai-spring-boot-docker-compose-");

    @Test
    @Story("The operator starts the sidecars, and the jar starts none")
    @DisplayName("The packaged jar carries no Docker Compose support, so running it starts no container")
    void carriesNoComposeSupport() throws IOException {
        claim("the packaged jar exists, since this test reads it and needs verify to have built it",
                () -> assertThat(Files.exists(EXECUTABLE_JAR)).isTrue());

        List<String> libraries = libraries();

        claim("the jar carries its dependencies, so an absence below is a real one and not an empty jar",
                () -> assertThat(libraries).anyMatch(name -> name.startsWith("picocli-")));
        claim("and none of them is Spring Boot's or Spring AI's Docker Compose support, which is what"
                        + " would start compose.yaml from wherever the jar was launched",
                () -> assertThat(libraries)
                        .noneMatch(name -> COMPOSE_SUPPORT.stream().anyMatch(name::startsWith)));
    }

    /** The file name of every jar nested under {@code BOOT-INF/lib/}. */
    private static List<String> libraries() throws IOException {
        try (JarFile jar = new JarFile(EXECUTABLE_JAR.toFile())) {
            return jar.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith(LIBRARIES) && name.endsWith(".jar"))
                    .map(name -> name.substring(LIBRARIES.length()))
                    .toList();
        }
    }
}
