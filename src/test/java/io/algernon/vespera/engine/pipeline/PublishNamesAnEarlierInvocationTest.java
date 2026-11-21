package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
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
 * {@code vespera publish --invocation <id>} delivers the rendering of the generation run that invocation's copy in
 * {@code logs} names in {@code arrivedAt}, and not the one the last {@code vespera run} reached (ADR-439 §1, §4); its
 * tree is looked for at the output folder the copy names in {@code places.deliverableRoot}, not at the one named now
 * (§3). An id not of an invocation's form is refused with exit 2; a copy not kept or unreadable, an invocation that was
 * no {@code vespera run}, one that did not reach the deliverable, a tree no longer at its place and a tree drawn again
 * for another generation run are each refused once, before any destination, exit 1, in a line of their own (§2, §3); and
 * a rendering that invocation did not draw is refused for its destination (§4).
 *
 * <p>A bare publish is Confluence's, so each test asks a publisher that sends nothing which run it was given, as
 * {@link PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest} does; nothing reaches a site.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("923")
@Link(name = "ADR-439", url = Adr.A_PUBLISH_MAY_NAME_AN_EARLIER_RUN_BY_ITS_INVOCATION, type = "adr")
class PublishNamesAnEarlierInvocationTest {

    /** The earlier invocation the operator names, and the last one, a day after it. */
    private static final String EARLIER = "20261112T080000Z";

    private static final String LAST = "20261113T080000Z";

    /** What every refusal of an invocation named ends with. */
    private static final String NAME_ANOTHER = " Name another, or publish without --invocation.";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    @TempDir
    Path elsewhere;

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
    @Story("A publish may name an earlier run")
    @DisplayName("The deliverable an earlier run drew is published when that run is named, though a later run reached another")
    void theGenerationRunTheNamedInvocationReachedIsPublishedThoughALaterRunReachedAnother() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId earlier = finished(ledger, walk, "the earlier floor");
        RunId last = finished(ledger, walk, "the later floor");
        confluenceListIn(unnamedTreeOf(earlier));
        confluenceListIn(unnamedTreeOf(last));
        copy(EARLIER, "run", earlier, unnamedRoot());
        copy(LAST, "run", last, unnamedRoot());

        Ended ended = publish(ledger, "", "--invocation", EARLIER);

        claim("the publisher was asked once, for the generation the earlier run reached, and not for the one the last"
                        + " run reached, and the publish ended delivered",
                () -> assertThat(List.of(ended.exit(), ended.asked()))
                        .containsExactly(CommandLine.ExitCode.OK, List.of(earlier.value())));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("The deliverable is looked for in the output folder the named run drew it in, though another folder is named now")
    void theTreeIsLookedForAtTheOutputFolderTheInvocationDrewIn() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId earlier = finished(ledger, walk, "the earlier floor");
        RunId last = finished(ledger, walk, "the later floor");
        Path earlierRoot = elsewhere.resolve("earlier-output");
        Path namedNow = elsewhere.resolve("output-named-now");
        markedTree(earlierRoot, earlier);
        markedTree(namedNow, last);
        copy(EARLIER, "run", earlier, earlierRoot.toString());
        copy(LAST, "run", last, namedNow.toString());

        Ended ended = publish(ledger, namedNow.toString(), "--invocation", EARLIER);

        claim("the earlier run's tree was found in the folder its report names, the folder named now holding the later"
                        + " run's, and its generation was the one asked",
                () -> assertThat(List.of(ended.exit(), ended.asked()))
                        .containsExactly(CommandLine.ExitCode.OK, List.of(earlier.value())));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A name that is not of a run's dated form is refused before anything is read, as a usage error")
    void anIdNotOfAnInvocationsFormIsRefusedWithExitTwo() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId last = finished(ledger, walk, "the floor");
        confluenceListIn(unnamedTreeOf(last));
        copy(LAST, "run", last, unnamedRoot());

        Ended ended = publish(ledger, "", "--invocation", "yesterday");

        claim("the publish exited with the usage code, asked the publisher nothing, and its one line says how an"
                        + " invocation is named",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.USAGE, 0, "vespera publish has no invocation yesterday:"
                                + " an invocation is named by its id, as 20261114T083000Z or 20261114T083000Z-2."));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A run whose report this working folder does not keep, or keeps unreadable, is refused and nothing is sent")
    void anInvocationWhoseReportIsNotKeptIsRefused() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId last = finished(ledger, walk, "the floor");
        confluenceListIn(unnamedTreeOf(last));
        copy(LAST, "run", last, unnamedRoot());
        Files.writeString(workingDirectory.resolve("logs").resolve("report-" + EARLIER + ".json"), "not json",
                StandardCharsets.UTF_8);

        Ended notKept = publish(ledger, "", "--invocation", "20261111T080000Z");
        Ended unreadable = publish(ledger, "", "--invocation", EARLIER);

        claim("a run of which no report is kept is refused with exit 1, nothing asked, in its one line",
                () -> assertThat(List.of(notKept.exit(), notKept.asked().size(), notKept.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: this working"
                                + " directory keeps no report of the invocation 20261111T080000Z that can be read."
                                + NAME_ANOTHER));
        claim("and so is one whose report is kept but is not JSON",
                () -> assertThat(List.of(unreadable.exit(), unreadable.asked().size(), unreadable.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: this working"
                                + " directory keeps no report of the invocation " + EARLIER + " that can be read."
                                + NAME_ANOTHER));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A label named in place of a run is refused, a label drawing no deliverable")
    void anInvocationThatWasNoRunIsRefused() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId last = finished(ledger, walk, "the floor");
        confluenceListIn(unnamedTreeOf(last));
        copy(LAST, "run", last, unnamedRoot());
        copy(EARLIER, "label", null, null);

        Ended ended = publish(ledger, "", "--invocation", EARLIER);

        claim("the publish refused with exit 1, asked nothing, and its one line names the command it was",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the"
                                + " invocation " + EARLIER + " was a vespera label, which draws no deliverable."
                                + NAME_ANOTHER));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A run named that stopped before the deliverable, or whose drawing did not finish, is refused")
    void anInvocationThatDidNotReachTheDeliverableIsRefused() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId last = finished(ledger, walk, "the floor");
        RunId unfinished = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", "a drawing stopped", walk,
                List.of());
        confluenceListIn(unnamedTreeOf(last));
        confluenceListIn(unnamedTreeOf(unfinished));
        copy("20261110T080000Z", "run", null, null);
        copy(EARLIER, "run", unfinished, unnamedRoot());
        copy(LAST, "run", last, unnamedRoot());

        Ended stoppedBefore = publish(ledger, "", "--invocation", "20261110T080000Z");
        Ended notFinished = publish(ledger, "", "--invocation", EARLIER);

        claim("a run that reached no generation is refused with exit 1, nothing asked, in its one line",
                () -> assertThat(List.of(stoppedBefore.exit(), stoppedBefore.asked().size(),
                        stoppedBefore.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the"
                                + " invocation 20261110T080000Z did not reach the deliverable." + NAME_ANOTHER));
        claim("and so is one whose generation's step is not recorded finished, though its folder holds a page list",
                () -> assertThat(List.of(notFinished.exit(), notFinished.asked().size(), notFinished.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the"
                                + " invocation " + EARLIER + " did not reach the deliverable." + NAME_ANOTHER));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A deliverable no longer where the named run drew it is refused, in the working folder's own folder or in a folder named")
    void aTreeNoLongerWhereTheInvocationDrewItIsRefused() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId gone = finished(ledger, walk, "a tree removed");
        RunId emptied = finished(ledger, walk, "a named folder emptied");
        RunId last = finished(ledger, walk, "the floor");
        confluenceListIn(unnamedTreeOf(last));
        Path emptiedRoot = Files.createDirectories(elsewhere.resolve("emptied-output"));
        copy("20261110T080000Z", "run", gone, unnamedRoot());
        copy(EARLIER, "run", emptied, emptiedRoot.toString());
        copy(LAST, "run", last, unnamedRoot());

        Ended unnamed = publish(ledger, "", "--invocation", "20261110T080000Z");
        Ended named = publish(ledger, "", "--invocation", EARLIER);

        claim("a tree whose folder under the working directory's deliverable folder is gone is refused with exit 1,"
                        + " nothing asked, its line naming that folder",
                () -> assertThat(List.of(unnamed.exit(), unnamed.asked().size(), unnamed.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the tree the"
                                + " invocation 20261110T080000Z drew is no longer at " + unnamedTreeOf(gone) + "."
                                + NAME_ANOTHER));
        claim("and a named folder holding no marker is refused in the same line, naming that folder",
                () -> assertThat(List.of(named.exit(), named.asked().size(), named.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the tree the"
                                + " invocation " + EARLIER + " drew is no longer at " + emptiedRoot + "."
                                + NAME_ANOTHER));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A named folder drawn again for another generation after the named run is refused in a line of its own")
    void aTreeDrawnAgainForAnotherGenerationRunIsRefused() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId earlier = finished(ledger, walk, "the earlier floor");
        RunId another = finished(ledger, walk, "another floor");
        RunId last = finished(ledger, walk, "the floor");
        confluenceListIn(unnamedTreeOf(last));
        Path root = elsewhere.resolve("output");
        markedTree(root, another);
        copy(EARLIER, "run", earlier, root.toString());
        copy(LAST, "run", last, unnamedRoot());

        Ended ended = publish(ledger, "", "--invocation", EARLIER);

        claim("the publish refused with exit 1, asked nothing, and its one line says the tree there was drawn again",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the tree at "
                                + root + " was drawn again for another generation run or working directory after the"
                                + " invocation " + EARLIER + "." + NAME_ANOTHER));
    }

    @Test
    @Story("A publish may name an earlier run")
    @DisplayName("A deliverable the named run drew with no Confluence pages sends nothing to Confluence")
    void aRenderingTheInvocationDidNotDrawIsRefusedForThatDestination() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId earlier = finished(ledger, walk, "the earlier floor");
        RunId last = finished(ledger, walk, "the floor");
        Files.createDirectories(unnamedTreeOf(earlier).resolve("website"));
        confluenceListIn(unnamedTreeOf(last));
        copy(EARLIER, "run", earlier, unnamedRoot());
        copy(LAST, "run", last, unnamedRoot());

        Ended ended = publish(ledger, "", "--invocation", EARLIER);

        claim("the bare publish refused with exit 1, asked nothing, and its one line says the invocation drew no"
                        + " Confluence rendering",
                () -> assertThat(List.of(ended.exit(), ended.asked().size(), ended.errors().strip()))
                        .containsExactly(CommandLine.ExitCode.SOFTWARE, 0, "vespera publish sent nothing: the"
                                + " invocation " + EARLIER + " drew no Confluence rendering." + NAME_ANOTHER));
    }

    /** One publish over {@code ledger} with the deliverable root named now and {@code arguments}, standard error captured. */
    private Ended publish(Ledger ledger, String deliverableRootNamedNow, String... arguments) {
        Recording publisher = new Recording();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream before = System.err;
        int exit;
        try (PrintStream captured = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            exit = new CommandLine(new VesperaCommand.Publish(ledger, publisher, workingDirectory,
                    deliverableRootNamedNow)).execute(arguments);
        } finally {
            System.setErr(before);
        }
        return new Ended(exit, List.copyOf(publisher.asked), errors.toString(StandardCharsets.UTF_8));
    }

    /** A generation run whose generation step is recorded finished. */
    private static RunId finished(Ledger ledger, WalkId walk, String config) {
        RunId run = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", config, walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        return run;
    }

    /** The working directory's {@code deliverable} folder, absolute, as a report names the unnamed root. */
    private String unnamedRoot() {
        return workingDirectory.resolve("deliverable").toAbsolutePath().normalize().toString();
    }

    private Path unnamedTreeOf(RunId run) {
        return workingDirectory.resolve("deliverable").resolve(run.value()).toAbsolutePath().normalize();
    }

    /** A page list in {@code tree}'s {@code confluence/}, which says its rendering is drawn. */
    private static void confluenceListIn(Path tree) throws IOException {
        Path confluence = Files.createDirectories(tree.resolve(Publisher.RENDERING_FOLDER));
        Files.writeString(confluence.resolve(Publisher.PAGE_LIST_FILE_NAME), "{}");
    }

    /** A tree at the named root {@code root}, its marker naming this working directory and {@code run}, and a page list. */
    private void markedTree(Path root, RunId run) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(DeliverableRoot.MARKER_FILE_NAME), "working-directory="
                + workingDirectory.toRealPath() + "\ngeneration-run=" + run.value() + "\n", StandardCharsets.UTF_8);
        confluenceListIn(root);
    }

    /**
     * The copy {@code logs/report-<id>.json} of an ended invocation of {@code command}, its {@code arrivedAt} naming
     * {@code generation} under the generation stage beside an arrangement run, or the arrangement run alone where it is
     * null, a label's naming none; {@code places.deliverableRoot} {@code deliverableRoot}, or {@code null}.
     */
    private void copy(String id, String command, RunId generation, String deliverableRoot) throws IOException {
        String arrivedAt = !"run".equals(command) ? "[]"
                : "[{\"stage\":\"" + StageModules.ARRANGEMENT.stage() + "\",\"run\":\"arrangement-of-" + id + "\"}"
                        + (generation == null ? ""
                                : ",{\"stage\":\"" + StageModules.GENERATION.stage() + "\",\"run\":\""
                                        + generation.value() + "\"}")
                        + "]";
        String report = "{\"format\":" + InvocationReport.FORMAT + ",\"invocation\":{\"id\":\"" + id
                + "\",\"pid\":4242,\"command\":\"" + command
                + "\",\"started\":\"2026-11-13T08:00:00.000Z\",\"ended\":\"2026-11-13T08:30:00.000Z\",\"state\":\"ended\","
                + "\"outcome\":\"completed\",\"exitCode\":0,\"writtenAt\":\"2026-11-13T08:30:00.000Z\",\"interval\":1},"
                + "\"places\":{\"workingDirectory\":" + json(workingDirectory.toString()) + ",\"corpusRoot\":"
                + json(workingDirectory.resolve("corpus").toString()) + ",\"deliverableRoot\":"
                + (deliverableRoot == null ? "null" : json(deliverableRoot)) + "},\"arrivedAt\":" + arrivedAt + "}";
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + id + ".json"), report, StandardCharsets.UTF_8);
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
