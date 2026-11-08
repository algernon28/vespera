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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
 * A publish reads the state of the last drawing of the Confluence rendering before anything else of it (ADR-333 §11,
 * Decided here 4 and 5): {@code drawing.txt} in {@code confluence/}, which {@code Deliverable} writes. {@code drawn}
 * goes on, and so does a rendering drawn before ADR-333, which has none, the page list alone deciding as today;
 * {@code failed} or {@code drawing} sends nothing, the publisher is not asked, and the one line names the destination.
 * The last generation run whose tree holds the page list or a {@code drawing.txt} is the one, so an older run's
 * rendering is never sent in place of one whose drawing did not finish.
 *
 * <p>This is the class ADR-333's Tests table, as corrected in ADR-333 step 1, and ADR-338 §3 name. It lies in {@code
 * pipeline} beside {@code PublishFindsTheLastDrawnRenderingIT}, because the run a publish takes is found by {@code
 * VesperaCommand.Publish}, whose constructor this package alone reaches; and it is a test of the build and untagged,
 * since its publisher sends nothing and reaches no site, so CI runs it. Step 1 of ADR-333 §13 keeps that
 * constructor and its {@link Publisher}; a bare publish is Confluence, the only destination before {@code --to} (§6).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class PublishRefusesARenderingWhoseDrawingDidNotFinishTest {

    /** The file of the rendering's folder holding the state of its last drawing (ADR-333 §11). */
    private static final String THE_STATE_OF_THE_DRAWING = "drawing.txt";

    /** The one line a publish refuses an unfinished drawing in, naming the destination (ADR-333 §11). */
    private static final String THE_REFUSAL = "vespera publish sent nothing to confluence: the last drawing of its"
            + " rendering did not finish. Run vespera run to draw it again, and publish again.";

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
            asked.add(generationRun);
            return new Outcome(true, "published");
        }
    }

    /** How one publish ended: its exit, what the publisher was asked and what it wrote on standard error. */
    private record Ended(int exit, List<String> asked, String errors) {}

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("A rendering whose last drawing failed is not sent: the publisher is not asked and the one line names Confluence")
    void aFailedDrawingIsNotSent() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        generationRun(ledger, walk, "failed-over-an-earlier-list", true, "failed");

        Ended ended = publish(ledger);

        claim(
                "the publish refused, exit 1, though a page list an earlier drawing wrote stands beside the state",
                () -> assertThat(ended.exit()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim(
                "and asked the publisher for nothing, so no site is touched",
                () -> assertThat(ended.asked()).isEmpty());
        claim(
                "its one line names the destination and says to draw it again",
                () -> assertThat(ended.errors().strip()).isEqualTo(THE_REFUSAL));
    }

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("An older run's rendering is not sent in place of the last, whose drawing failed before it wrote a page list")
    void anOlderRenderingIsNotSentInPlaceOfAFailedOne() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        generationRun(ledger, walk, "older-and-drawn", true, "drawn");
        generationRun(ledger, walk, "newer-and-failed", false, "failed");

        Ended ended = publish(ledger);

        claim(
                "the last run's folder holds drawing.txt and no page list, so it is the one, and it is refused: the"
                        + " older run's rendering, drawn whole, is not sent in its place",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, THE_REFUSAL));
    }

    /** One bare publish over {@code ledger}, standard error captured. */
    private Ended publish(Ledger ledger) {
        Recording publisher = new Recording();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream before = System.err;
        int exit;
        try (PrintStream captured = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            exit = new VesperaCommand.Publish(ledger, publisher, workingDirectory, "").call();
        } finally {
            System.setErr(before);
        }
        return new Ended(exit, List.copyOf(publisher.asked), errors.toString(StandardCharsets.UTF_8));
    }

    /**
     * A generation run, its step recorded finished, its tree's {@code confluence/} holding a page list where {@code
     * withAList} and a {@code drawing.txt} reading {@code state} where it is not null.
     */
    private RunId generationRun(Ledger ledger, WalkId walk, String config, boolean withAList, String state)
            throws IOException {
        RunId run = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", config, walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path confluence = Files.createDirectories(
                workingDirectory.resolve("deliverable").resolve(run.value()).resolve(Publisher.RENDERING_FOLDER));
        if (withAList) {
            Files.writeString(confluence.resolve(Publisher.PAGE_LIST_FILE_NAME), "{}");
        }
        if (state != null) {
            Files.writeString(confluence.resolve(THE_STATE_OF_THE_DRAWING), state + "\n");
        }
        return run;
    }
}
