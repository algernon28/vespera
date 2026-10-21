package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.confluence.Publisher;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import picocli.CommandLine;

/**
 * {@code vespera publish} publishes the rendering of the generation run the working directory last drew, found through
 * the ledger (ADR-308 §5): the last generation run minted whose generation step finished and whose tree holds a page
 * list. A working directory with none refuses, and the publisher is not asked.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class PublishFindsTheLastDrawnRenderingTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    /** A publisher that sends nothing, recording which rendering it was asked to publish. */
    private static final class Recording extends Publisher {
        final List<String> asked = new ArrayList<>();

        Recording() {
            super("", "", "", "");
        }

        @Override
        public Outcome publish(Path confluence, String generationRun, Path workingDirectory, Consumer<String> out) {
            asked.add(generationRun + " at " + confluence);
            return new Outcome(true, "published");
        }
    }

    @Test
    @Story("The operator publishes the last rendering drawn")
    @DisplayName("Publish asks for the rendering of the last generation run that finished and whose tree holds a page list")
    void publishAsksForTheLastDrawnRendering() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId drawn = generationRun(ledger, walk, "drawn", true, true);
        generationRun(ledger, walk, "not-finished", false, true);
        generationRun(ledger, walk, "finished-with-no-rendering", true, false);
        Recording publisher = new Recording();

        int exit = new VesperaCommand.Publish(ledger, publisher, workingDirectory, "").call();

        claim("the command ended as the publisher's outcome said",
                () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.OK));
        claim("and asked for the rendering of the one run that finished and has a page list, in its tree's folder",
                () -> assertThat(publisher.asked).containsExactly(drawn.value() + " at " + workingDirectory
                        .resolve("deliverable").resolve(drawn.value()).resolve(Publisher.RENDERING_FOLDER)));
    }

    @Test
    @Story("The operator publishes the last rendering drawn")
    @DisplayName("A working directory with no rendering drawn refuses, and the publisher is not asked")
    void aWorkingDirectoryWithNoRenderingRefuses() {
        Recording publisher = new Recording();

        int exit = new VesperaCommand.Publish(new Ledger(jdbcTemplate), publisher, workingDirectory, "").call();

        claim("the command refused",
                () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("and asked the publisher for nothing", () -> assertThat(publisher.asked).isEmpty());
    }

    private RunId generationRun(Ledger ledger, WalkId walk, String config, boolean finished, boolean drawn)
            throws IOException {
        RunId run = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", config, walk, List.of());
        if (finished) {
            ledger.runs().finishStep(run, StepNames.GENERATION);
        }
        if (drawn) {
            Path confluence = Files.createDirectories(
                    workingDirectory.resolve("deliverable").resolve(run.value()).resolve(Publisher.RENDERING_FOLDER));
            Files.writeString(confluence.resolve(Publisher.PAGE_LIST_FILE_NAME), "{}");
        }
        return run;
    }
}
