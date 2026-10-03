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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No way of running Vespera starts or stops a sidecar: the operator starts them, with the {@code up}
 * line the README gives, whether Vespera then runs from the jar, from the IDE or from
 * {@code ./mvnw spring-boot:run} (ADR-179 §1).
 *
 * <p>On 2026-09-30 an IDE run brought Spring Boot's Docker Compose support up from {@code compose.yaml}
 * alone. It replaced the GPU sidecars the operator had started with both compose files, by the CPU
 * ones, and stopped all three when the run ended. The jar never did this, because it nests neither
 * compose artifact (ADR-158). The IDE runs from the module's runtime classpath instead, optional
 * dependencies included, so the only way to keep the support out of every entry point is to keep it
 * out of the pom.
 *
 * <p>The classpath these tests run on contains that runtime classpath: Maven's test classpath is the
 * runtime one plus the test-scoped dependencies, optional ones included. A class absent here is absent
 * from what an IDE launches {@code VesperaApplication} with.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("373")
@Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
class SidecarsAreStartedByTheOperatorTest {

    /** The pom at the repository root, which is where Maven runs the tests from. */
    private static final Path POM = Path.of("pom.xml");

    /** Spring Boot's Docker Compose support: the artifact, and the class that runs {@code up} and {@code stop}. */
    private static final String BOOT_COMPOSE_ARTIFACT = "spring-boot-docker-compose";

    private static final String BOOT_COMPOSE_LIFECYCLE =
            "org.springframework.boot.docker.compose.lifecycle.DockerComposeLifecycleManager";

    /** Spring AI's: the artifact, and the two classes that read Ollama and Chroma back from containers it started. */
    private static final String AI_COMPOSE_ARTIFACT = "spring-ai-spring-boot-docker-compose";

    private static final List<String> AI_COMPOSE_CONNECTION_DETAILS = List.of(
            "org.springframework.ai.docker.compose.service.connection.ollama.OllamaDockerComposeConnectionDetailsFactory",
            "org.springframework.ai.docker.compose.service.connection.chroma.ChromaDockerComposeConnectionDetailsFactory");

    @Test
    @Story("The operator starts the sidecars, and nothing else does")
    @DisplayName("Running Vespera from the IDE starts no container and stops none, because the code that would is not there")
    void anIdeRunStartsNoContainer() {
        claim(
                "Spring Boot's Docker Compose support, which runs its own up from compose.yaml alone and stops"
                        + " the containers when the application exits, is not on the classpath an IDE runs"
                        + " Vespera with",
                () -> assertThat(isOnTheClasspath(BOOT_COMPOSE_LIFECYCLE))
                        .as("%s is on the classpath", BOOT_COMPOSE_LIFECYCLE)
                        .isFalse());
        claim(
                "and neither is Spring AI's support for reading the model server and the vector store back"
                        + " from containers that support started",
                () -> assertThat(AI_COMPOSE_CONNECTION_DETAILS)
                        .as("classes reading containers back that are still on the classpath")
                        .noneMatch(SidecarsAreStartedByTheOperatorTest::isOnTheClasspath));
    }

    @Test
    @Story("The operator starts the sidecars, and nothing else does")
    @DisplayName("The build declares no Docker Compose support, so no setting can turn it back on")
    void thePomDeclaresNoComposeSupport() throws IOException {
        String pom = Files.readString(POM);

        claim(
                "the build does not depend on Spring Boot's Docker Compose support, so there is nothing for an"
                        + " environment variable or a profile to switch on again",
                () -> assertThat(pom).doesNotContain("<artifactId>" + BOOT_COMPOSE_ARTIFACT + "</artifactId>"));
        claim(
                "and it does not depend on Spring AI's either, which only reads back what the first one started",
                () -> assertThat(pom).doesNotContain("<artifactId>" + AI_COMPOSE_ARTIFACT + "</artifactId>"));
    }

    /** Whether {@code className} can be found, without loading or initialising it. */
    private static boolean isOnTheClasspath(String className) {
        return SidecarsAreStartedByTheOperatorTest.class.getClassLoader()
                        .getResource(className.replace('.', '/') + ".class")
                != null;
    }
}
