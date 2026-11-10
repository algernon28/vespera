package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * After a run and after a label, the working directory holds none of the five progress pages and its {@code logs}
 * folder no invocation account: the report and its copy replace them (ADR-362 sections 4 and 6).
 *
 * <p>The retired names are written out here, as the files an earlier version left are no part of the jar's code.
 */
@CascadeSliceTest
@Import({
    SeedScriptedExtractionBeans.class,
    AutoLabelling.class,
    NoInvocationWritesTheRetiredFilesInvocationTest.Beans.class
})
@Epic("Pipeline")
@Feature("The retired progress pages")
@Issue("762")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class NoInvocationWritesTheRetiredFilesInvocationTest {

    /** The four pages of {@code vespera run}, and the page of {@code vespera label}, which shared the first's name. */
    private static final List<String> THE_PROGRESS_FILES = List.of(
            "progress.html", "progress-steps.html", "progress-samples.html", "progress-runs.html", "progress.json");

    private static final String THE_ACCOUNTS_PREFIX = "invocation-account-";

    /** What the invocation writes instead. */
    private static final String THE_REPORT = "report.json";

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "scripted-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    return Optional.of(true);
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("The progress pages and the account are no longer written")
    @DisplayName("After a run and after a label, no progress page and no invocation account text is written")
    void aRunAndALabelWriteNoProgressPageAndNoAccountText(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        int runExit = cli.getExitCode();
        List<String> afterTheRun = namesIn(workingDirectory);
        List<String> accountsAfterTheRun = namesIn(workingDirectory.resolve("logs"));
        cli.run("label", "--auto");
        int labelExit = cli.getExitCode();

        claim("the run succeeded, so its files are the ones a finished run leaves", () -> assertThat(runExit).isZero());
        claim("and the label did", () -> assertThat(labelExit).isZero());
        claim(
                "the run wrote its report in the working directory, which holds none of the five progress files",
                () -> assertThat(afterTheRun).contains(THE_REPORT).doesNotContainAnyElementsOf(THE_PROGRESS_FILES));
        claim(
                "and the logs folder holds no invocation account after it",
                () -> assertThat(accountsAfterTheRun).noneMatch(name -> name.startsWith(THE_ACCOUNTS_PREFIX)));
        List<String> afterTheLabel = namesIn(workingDirectory);
        claim(
                "after the label too the working directory holds none of the five progress files",
                () -> assertThat(afterTheLabel).contains(THE_REPORT).doesNotContainAnyElementsOf(THE_PROGRESS_FILES));
        claim(
                "and the logs folder holds no invocation account after both",
                () -> assertThat(namesIn(workingDirectory.resolve("logs")))
                        .noneMatch(name -> name.startsWith(THE_ACCOUNTS_PREFIX)));
    }

    private static List<String> namesIn(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }
}
