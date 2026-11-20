package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A compose file beside the jar that cannot be read for the image its {@code docling-serve} service builds refuses the
 * start of the containers in one line, as every other refusal of ADR-323 §8 does, and never as a stack trace
 * (ADR-397 §12). The compose files are the operator's to edit once they lie beside the jar, so one that does not load,
 * names no image, or is not there is a state the operator can reach.
 *
 * <p>The folder beside the jar is a temporary folder of the test's own, holding the repository's compose files as the
 * jar writes them there, one of them then changed or deleted. No Docker is needed: the starter is given a
 * {@link ScriptedDocker} and a {@link ScriptedContainerHealth}, and the refusal is shown to come before any command.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("836")
@Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class AComposeFileThatCannotBeReadRefusesTheStartTest {

    /** The repository root, where the tests run and where the compose files the jar ships lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    /** What every line of this refusal begins with, before the file it names. */
    private static final String BEGINS = "the containers are not answering and Vespera cannot start them: ";

    /** What follows the file's path, before the reason. */
    private static final String CANNOT_BE_READ = " cannot be read for the image its docling-serve service builds (";

    /** What every line of this refusal ends with, after the reason. */
    private static final String ENDS = "). Put that file right, or delete it so that Vespera writes its own there, and"
            + " run the same command again.";

    /** A compose file that loads, and whose {@code docling-serve} service names no image. */
    private static final String NAMES_NO_IMAGE = "services:\n  docling-serve:\n    build: docker/docling-serve\n";

    /** A compose file that does not load as YAML. */
    private static final String DOES_NOT_LOAD = "services: [a list the operator never closed\n";

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    @TempDir
    Path besideTheJar;

    @TempDir
    Path lockFolder;

    @Test
    @Story("Vespera says in one line why it cannot start the containers")
    @DisplayName("A compose.yaml whose docling-serve service names no image refuses the start in one line naming the file and why, before Docker is asked anything")
    void aComposeFileNamingNoImageRefusesTheStartInOneLine() throws IOException {
        String configured = theRepositorysFiles().cpuImage();
        theRepositorysComposeFilesBesideTheJar();
        Path composeFile = besideTheJar.resolve(ComposeFiles.COMPOSE_FILE);
        Files.writeString(composeFile, NAMES_NO_IMAGE);
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        Throwable refused = catchThrowable(() -> starter(configured, docker, ScriptedContainerHealth.neitherAnswering())
                .ensureRunning());

        claim("the start is refused as the commands catch a refusal, in the line naming compose.yaml where it lies and"
                        + " that it names no image for its docling-serve service",
                () -> assertThat(refused)
                        .isInstanceOf(ComposeFileUnreadableException.class)
                        .isInstanceOf(ContainerStartRefusedException.class)
                        .hasMessage(BEGINS + composeFile + CANNOT_BE_READ
                                + "it names no image for its docling-serve service" + ENDS));
        claim("and Docker was asked nothing", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera says in one line why it cannot start the containers")
    @DisplayName("A compose.yaml that does not load refuses the start in one line saying so, and is never read while both containers answer")
    void aComposeFileThatDoesNotLoadRefusesTheStartInOneLine() throws IOException {
        String configured = theRepositorysFiles().cpuImage();
        theRepositorysComposeFilesBesideTheJar();
        Path composeFile = besideTheJar.resolve(ComposeFiles.COMPOSE_FILE);
        Files.writeString(composeFile, DOES_NOT_LOAD);
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        Throwable refused = catchThrowable(() -> starter(configured, docker, ScriptedContainerHealth.neitherAnswering())
                .ensureRunning());
        Throwable whileAnswering = catchThrowable(() -> starter(configured, ScriptedDocker.engineRunning(),
                ScriptedContainerHealth.bothAnswering()).ensureRunning());

        claim("the start is refused in one line naming compose.yaml where it lies and that it does not load as YAML",
                () -> assertThat(refused)
                        .isInstanceOf(ComposeFileUnreadableException.class)
                        .message()
                        .startsWith(BEGINS + composeFile + CANNOT_BE_READ + "it does not load as YAML: ")
                        .endsWith(ENDS)
                        .doesNotContain("\n")
                        .doesNotContain("\r"));
        claim("Docker was asked nothing", () -> assertThat(docker.commands()).isEmpty());
        claim("and while both containers answer, the file is not read and nothing is refused",
                () -> assertThat(whileAnswering).as("what a start with both containers answering threw").isNull());
    }

    @Test
    @Story("Vespera says in one line why it cannot start the containers")
    @DisplayName("A compose.gpu.yaml that is not there refuses the start in one line naming it, though compose.yaml builds the image configured")
    void aGpuFileThatIsNotThereRefusesTheStartInOneLine() throws IOException {
        String configured = theRepositorysFiles().cpuImage();
        theRepositorysComposeFilesBesideTheJar();
        Path gpuFile = besideTheJar.resolve(ComposeFiles.GPU_FILE);
        Files.delete(gpuFile);
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        Throwable refused = catchThrowable(() -> starter(configured, docker, ScriptedContainerHealth.neitherAnswering())
                .ensureRunning());

        claim("both files are read before the image is compared, compose.yaml first, so the start is refused in the"
                        + " line naming compose.gpu.yaml where it should lie, and that it is not there",
                () -> assertThat(refused)
                        .isInstanceOf(ComposeFileUnreadableException.class)
                        .hasMessage(BEGINS + gpuFile + CANNOT_BE_READ + "it is not there" + ENDS));
        claim("and Docker was asked nothing", () -> assertThat(docker.commands()).isEmpty());
    }

    /** The two compose files of the repository, copied into the folder beside the jar of this test. */
    private void theRepositorysComposeFilesBesideTheJar() throws IOException {
        for (String name : new String[] {ComposeFiles.COMPOSE_FILE, ComposeFiles.GPU_FILE}) {
            Files.copy(REPOSITORY.resolve(name), besideTheJar.resolve(name));
        }
    }

    private static ComposeFiles theRepositorysFiles() {
        return ComposeFiles.in(REPOSITORY);
    }

    private ContainerStarter starter(String image, ScriptedDocker docker, ScriptedContainerHealth health) {
        return new ContainerStarter(new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, image, Duration.ofSeconds(1)),
                docker, health, HostSystem.LINUX, () -> ComposeFiles.in(besideTheJar), lockFolder,
                Duration.ofMillis(20));
    }
}
