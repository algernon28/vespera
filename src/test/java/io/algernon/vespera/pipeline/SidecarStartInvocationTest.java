package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The two commands that use a sidecar ask the starter before they do anything else, and a refusal ends the command
 * with its line and exit code 1; the commands that use none never ask (ADR-322 §1, §3 and §8).
 *
 * <p>The starter is the production one, given a {@link ScriptedDocker} on a machine with no {@code docker} command and
 * a {@link ScriptedSidecarHealth} under which neither sidecar answers, so every start ends in the refusal that says
 * Docker is not installed. A context built by Spring's test support has no starter of its own, as ADR-322 §1 has it,
 * so this class adds one.
 */
@CascadeSliceTest
@Import({StubbedExtractionBeans.class, SidecarStartInvocationTest.AStarterWithNoDocker.class})
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-322", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class SidecarStartInvocationTest {

    /** The exit code of a refused start, the one a corpus root in use gets. */
    private static final int REFUSED = 1;

    /** The line a start refused for want of Docker prints. */
    private static final String NO_DOCKER = "the sidecars are not answering and Vespera cannot start them: no docker"
            + " command was found. Install Docker -- Docker Desktop on Windows and macOS, Docker Engine on Linux -- and"
            + " run the same command again, or start the sidecars yourself and set VESPERA_SIDECARS_START=false.";

    /** The sidecars the starter asks; reset before each test so each counts its own questions. */
    private static final ScriptedSidecarHealth HEALTH = ScriptedSidecarHealth.neitherAnswering();

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path lockFolder;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int questionsBefore;

    @BeforeEach
    void countFromHere() {
        questionsBefore = HEALTH.questionsAsked();
    }

    @Test
    @Story("A command that uses the sidecars starts them first")
    @DisplayName("vespera run that cannot start the sidecars ends with the line saying why and exit code 1, before it walks anything")
    void runIsRefusedBeforeItWalks(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");

        String printed = standardErrorOf(() -> cli.run("run", root.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("the last line on standard error is the refusal saying Docker is not installed",
                () -> assertThat(lastLineOf(printed)).isEqualTo(NO_DOCKER));
        claim("and no walk of the corpus is recorded: the start was asked before the job", () -> assertThat(walksOf(root))
                .isZero());
    }

    @Test
    @Story("A command that uses the sidecars starts them first")
    @DisplayName("vespera label --auto that cannot start the sidecars ends with the line saying why and exit code 1")
    void labelAutoIsRefused() {
        String printed = standardErrorOf(() -> cli.run("label", "--auto"));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim("and the last line on standard error is the refusal saying Docker is not installed",
                () -> assertThat(lastLineOf(printed)).isEqualTo(NO_DOCKER));
    }

    @Test
    @Story("A command that uses no sidecar starts none")
    @DisplayName("vespera label given a file never asks whether the sidecars answer")
    void labelWithAFileNeverAsks(@TempDir Path folder) {
        cli.run("label", folder.resolve("relevance-labels.yaml").toString());

        claim("neither sidecar was asked anything", () -> assertThat(HEALTH.questionsAsked()).isEqualTo(questionsBefore));
    }

    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
    }

    private static String lastLineOf(String printed) {
        List<String> lines = printed.lines().filter(line -> !line.isBlank()).toList();
        return lines.isEmpty() ? "" : lines.getLast();
    }

    /** What {@code invocation} wrote on standard error. */
    private static String standardErrorOf(Runnable invocation) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            invocation.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(original);
        }
    }

    /** The production starter, on a machine with no {@code docker} command and no sidecar answering. */
    @TestConfiguration
    static class AStarterWithNoDocker {

        @Bean
        SidecarStarter sidecarStarter() {
            Path repository = Path.of("").toAbsolutePath();
            SidecarStartSettings settings = new SidecarStartSettings(
                    true,
                    URI.create("http://localhost:5001"),
                    URI.create("http://localhost:11434"),
                    ComposeFiles.in(repository).cpuImage(),
                    Duration.ofSeconds(1));
            return new SidecarStarter(settings, ScriptedDocker.notInstalled(), HEALTH, HostSystem.LINUX,
                    () -> ComposeFiles.in(repository), lockFolder, Duration.ofMillis(20));
        }
    }
}
