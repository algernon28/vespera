package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.publish.confluence.Publisher;
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
 * A publish delivers the rendering of the generation run the working directory's last {@code vespera run} arrived at,
 * as the {@code arrivedAt} of that invocation's copy in {@code logs} names it, and not the last generation run minted
 * (ADR-393 §7, amending ADR-333 §6). The last {@code run}'s copy is the one of the greatest id whose command is {@code
 * run}, a later publish's copy passed over and the copies of one second ordered by their number; a last run that
 * arrived at no generation run sends nothing; and a working directory that keeps no copy of a {@code run}'s report
 * publishes the last minted, as before.
 *
 * <p>A bare publish is Confluence's, so each test asks the publisher of {@link PublishRefusesARenderingWhoseDrawingDidNotFinishTest}'s
 * shape which run it was given; nothing reaches a site.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest {

    /** The one line a bare publish refuses in where the last {@code vespera run} arrived at no generation run. */
    private static final String THE_REFUSAL = "vespera publish sent nothing: the last vespera run did not reach the"
            + " deliverable. Run vespera run to the deliverable, and publish again.";

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
    @Story("A publish delivers what the last run reached")
    @DisplayName("The rendering a value put back returned to is published, though another generation was made after it")
    void theRunPutBackIsPublishedThoughAnotherWasMintedAfterIt() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId stricter = drawnGenerationRun(ledger, walk, "the stricter floor");
        RunId looser = drawnGenerationRun(ledger, walk, "the looser floor");
        copy("20261112T080000Z", "run", looser);
        copy("20261113T080000Z", "run", stricter);
        copy("20261113T090000Z", "publish", null);

        Ended ended = publish(ledger);

        claim("the publisher was asked once, for the generation the last run arrived at, the one made first, and not"
                        + " for the one made after it; the later publish's own copy names none and is passed over",
                () -> assertThat(ended.asked()).containsExactly(stricter.value()));
    }

    @Test
    @Story("A publish delivers what the last run reached")
    @DisplayName("Of two runs started in the same second, the tenth comes after the second")
    void copiesOfOneSecondAreOrderedByTheirNumber() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId tenth = drawnGenerationRun(ledger, walk, "the tenth run's");
        RunId second = drawnGenerationRun(ledger, walk, "the second run's");
        copy("20261113T080000Z-2", "run", second);
        copy("20261113T080000Z-10", "run", tenth);

        Ended ended = publish(ledger);

        claim("the copy numbered 10 is the later of the two, though it sorts first as text, so its generation is asked",
                () -> assertThat(ended.asked()).containsExactly(tenth.value()));
    }

    @Test
    @Story("A publish delivers what the last run reached")
    @DisplayName("A last run that stopped before the deliverable sends nothing, though an earlier rendering is drawn")
    void aLastRunThatReachedNoDeliverableSendsNothing() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId earlier = drawnGenerationRun(ledger, walk, "an earlier floor");
        copy("20261112T080000Z", "run", earlier);
        copy("20261113T080000Z", "run", null);

        Ended ended = publish(ledger);

        claim("the publish refused with exit 1, asked the publisher for nothing, and its one line says the last run did"
                        + " not reach the deliverable",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, THE_REFUSAL));
    }

    @Test
    @Story("A publish delivers what the last run reached")
    @DisplayName("A working directory that keeps no report of a run publishes the last generation made, as before")
    void withNoRunsReportKeptTheLastMintedIsPublished() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        drawnGenerationRun(ledger, walk, "the first");
        RunId last = drawnGenerationRun(ledger, walk, "the last");
        copy("20261113T090000Z", "publish", null);

        Ended ended = publish(ledger);

        claim("with no run's copy in logs, only a publish's, the last generation made is asked",
                () -> assertThat(ended.asked()).containsExactly(last.value()));
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

    /** A generation run, its step recorded finished, its tree's {@code confluence/} holding a page list. */
    private RunId drawnGenerationRun(Ledger ledger, WalkId walk, String config) throws IOException {
        RunId run = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", config, walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path confluence = Files.createDirectories(
                workingDirectory.resolve("deliverable").resolve(run.value()).resolve(Publisher.RENDERING_FOLDER));
        Files.writeString(confluence.resolve(Publisher.PAGE_LIST_FILE_NAME), "{}");
        return run;
    }

    /**
     * The copy {@code logs/report-<id>.json} of an ended invocation of {@code command}, its {@code arrivedAt} naming
     * {@code generation} under the generation stage beside an arrangement run, or the arrangement run alone where it is
     * null; a publish's names none. It holds the fields ADR-393 §7's reader reads, and those of ADR-362 §3 around them.
     */
    private void copy(String id, String command, RunId generation) throws IOException {
        String arrivedAt = "publish".equals(command) ? "[]"
                : "[{\"stage\":\"" + StageModules.ARRANGEMENT.stage() + "\",\"run\":\"arrangement-of-" + id + "\"}"
                        + (generation == null ? ""
                                : ",{\"stage\":\"" + StageModules.GENERATION.stage() + "\",\"run\":\""
                                        + generation.value() + "\"}")
                        + "]";
        String report = "{\"format\":" + InvocationReport.FORMAT + ",\"invocation\":{\"id\":\"" + id + "\",\"pid\":4242,\"command\":\"" + command
                + "\",\"started\":\"2026-11-13T08:00:00.000Z\",\"ended\":\"2026-11-13T08:30:00.000Z\",\"state\":\"ended\","
                + "\"outcome\":\"" + ("publish".equals(command) ? "delivered" : "completed")
                + "\",\"exitCode\":0,\"writtenAt\":\"2026-11-13T08:30:00.000Z\",\"interval\":1},"
                + "\"places\":{\"workingDirectory\":" + json(workingDirectory.toString()) + ",\"corpusRoot\":"
                + ("run".equals(command) ? json(workingDirectory.resolve("corpus").toString()) : "null")
                + ",\"deliverableRoot\":null},\"arrivedAt\":" + arrivedAt + "}";
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + id + ".json"), report, StandardCharsets.UTF_8);
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
