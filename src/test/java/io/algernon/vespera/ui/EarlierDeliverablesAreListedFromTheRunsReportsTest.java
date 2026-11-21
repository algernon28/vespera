package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window's list of the earlier deliverables of the working directory chosen (ADR-439 §6): of the copies in {@code
 * logs}, each {@code vespera run}'s that reached a generation run and names the output folder it drew in, one row for
 * each generation run, from the newest copy that reached it, newest first; listed only where its tree still stands, at
 * the unnamed root its folder there and at a named root its marker naming this working directory and that run, and
 * holds a destination's list; the row the newest run's copy reached marked as the latest. Read from the copies and the
 * trees' markers and lists alone, never the database.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("923")
@Link(name = "ADR-439", url = Adr.A_PUBLISH_MAY_NAME_AN_EARLIER_RUN_BY_ITS_INVOCATION, type = "adr")
class EarlierDeliverablesAreListedFromTheRunsReportsTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** The corpus root each run's copy names, as text: the row shows it as the report holds it. */
    private static final String CORPUS_ROOT = "/archives/Letters";

    /** Three generation runs, by the folder names the unnamed root gives their trees. */
    private static final String FIRST = "aaaa000000000001";

    private static final String SECOND = "bbbb000000000002";

    private static final String THIRD = "cccc000000000003";

    @TempDir
    Path workingDirectory;

    @TempDir
    Path elsewhere;

    @Test
    @Story("The window lists the earlier deliverables of a working folder")
    @DisplayName("Each generation a kept run reached is one row, newest first, dated by the last run that reached it, the latest marked, labels and publishes passed over")
    void eachGenerationRunAKeptRunReachedIsOneRowNewestFirstAndTheLatestMarked() throws IOException {
        Path first = unnamedTree(FIRST, "confluence", "website");
        Path second = elsewhere.resolve("named-output");
        markedTree(second, SECOND, "confluence");
        copy("20261110T080000Z", "run", "2026-11-10T08:00:00.000Z", FIRST, unnamedRoot());
        copy("20261111T080000Z", "run", "2026-11-11T08:00:00.000Z", SECOND, second.toString());
        copy("20261112T233000Z", "run", "2026-11-12T23:30:00.000Z", FIRST, unnamedRoot());
        copy("20261113T080000Z", "label", "2026-11-13T08:00:00.000Z", null, null);
        copy("20261113T090000Z", "publish", "2026-11-13T09:00:00.000Z", null, null);

        List<EarlierDeliverable> rows = EarlierDeliverable.of(workingDirectory, ROME);

        claim("two rows: the first generation, from the run of 12 November at 23:30 UTC, 13 November in Rome, which"
                        + " reached it again and is the latest, its tree under the working folder holding Confluence's"
                        + " and the website's lists; then the second, from the run of 11 November, its tree the folder"
                        + " named then, holding Confluence's list alone; the label's and the publish's copies passed"
                        + " over",
                () -> assertThat(rows).containsExactly(
                        new EarlierDeliverable("20261112T233000Z", "2026-11-13 00:30", CORPUS_ROOT, first,
                                List.of("confluence", "website"), true),
                        new EarlierDeliverable("20261111T080000Z", "2026-11-11 09:00", CORPUS_ROOT, second,
                                List.of("confluence"), false)));
    }

    @Test
    @Story("The window lists the earlier deliverables of a working folder")
    @DisplayName("A run that drew no deliverable, a deliverable removed, a named folder drawn again for another, and a deliverable with nothing to publish are not listed")
    void aDeliverableThatNoLongerStandsOrDrewNoRenderingIsNotListed() throws IOException {
        Path redrawn = elsewhere.resolve("redrawn-output");
        markedTree(redrawn, THIRD, "confluence");
        Files.createDirectories(workingDirectory.resolve("deliverable").resolve(SECOND).resolve("website"));
        Path kept = unnamedTree(THIRD, "retrieval");
        copy("20261109T080000Z", "run", "2026-11-09T08:00:00.000Z", FIRST, null);
        copy("20261110T080000Z", "run", "2026-11-10T08:00:00.000Z", FIRST, unnamedRoot());
        copy("20261111T080000Z", "run", "2026-11-11T08:00:00.000Z", SECOND, unnamedRoot());
        copy("20261112T080000Z", "run", "2026-11-12T08:00:00.000Z", "dddd000000000004", redrawn.toString());
        copy("20261113T080000Z", "run", "2026-11-13T08:00:00.000Z", THIRD, unnamedRoot());

        List<EarlierDeliverable> rows = EarlierDeliverable.of(workingDirectory, ROME);

        claim("only the third generation's deliverable is listed, holding the retrieval records: the first's copy names"
                        + " no output folder once and its folder is gone the other time, the second's folder holds no"
                        + " list, and the named folder's marker names another generation than the one its copy reached",
                () -> assertThat(rows).containsExactly(new EarlierDeliverable("20261113T080000Z", "2026-11-13 09:00",
                        CORPUS_ROOT, kept, List.of("retrieval"), true)));
    }

    @Test
    @Story("The window lists the earlier deliverables of a working folder")
    @DisplayName("A row reads its local date, the latest marked, its documents folder, its output folder and what is drawn there")
    void aRowsWordsAreItsDateItsRootItsFolderAndWhatIsDrawn() throws IOException {
        Path tree = workingDirectory.resolve("deliverable").resolve(FIRST);

        EarlierDeliverable latest = new EarlierDeliverable("20261112T233000Z", "2026-11-13 00:30", CORPUS_ROOT, tree,
                List.of("confluence", "website"), true);
        EarlierDeliverable another = new EarlierDeliverable("20261111T080000Z", "2026-11-11 09:00", CORPUS_ROOT, tree,
                List.of("retrieval"), false);

        claim("the latest reads its date, (latest), its documents folder, its output folder and both destinations",
                () -> assertThat(latest.words()).isEqualTo("2026-11-13 00:30 (latest) — corpus root: " + CORPUS_ROOT
                        + "; output folder: " + tree + "; drawn: confluence, website"));
        claim("and another reads the same without (latest)",
                () -> assertThat(another.words()).isEqualTo("2026-11-11 09:00 — corpus root: " + CORPUS_ROOT
                        + "; output folder: " + tree + "; drawn: retrieval"));
    }

    @Test
    @Story("The window lists the earlier deliverables of a working folder")
    @DisplayName("A working folder with no logs, or with a label's report alone, lists nothing")
    void aFolderKeepingNoRunsReportListsNothing() throws IOException {
        List<EarlierDeliverable> noLogs = EarlierDeliverable.of(workingDirectory, ROME);
        unnamedTree(FIRST, "confluence");
        copy("20261113T080000Z", "label", "2026-11-13T08:00:00.000Z", null, null);

        List<EarlierDeliverable> aLabelsAlone = EarlierDeliverable.of(workingDirectory, ROME);

        claim("nothing is listed with no logs folder, nor with a label's copy alone though a tree stands",
                () -> {
                    assertThat(noLogs).isEmpty();
                    assertThat(aLabelsAlone).isEmpty();
                });
    }

    private String unnamedRoot() {
        return workingDirectory.resolve("deliverable").toAbsolutePath().normalize().toString();
    }

    /** A tree under the working directory's {@code deliverable} folder for {@code run}, a list for each destination. */
    private Path unnamedTree(String run, String... destinations) throws IOException {
        Path tree = workingDirectory.resolve("deliverable").resolve(run).toAbsolutePath().normalize();
        listsIn(tree, destinations);
        return tree;
    }

    /** A tree at the named root {@code root}, its marker naming this working directory and {@code run}. */
    private void markedTree(Path root, String run, String... destinations) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("vespera-deliverable.txt"), "working-directory=" + workingDirectory.toRealPath()
                + "\ngeneration-run=" + run + "\nvespera=0-SNAPSHOT\n", StandardCharsets.UTF_8);
        listsIn(root, destinations);
    }

    private static void listsIn(Path tree, String... destinations) throws IOException {
        for (String destination : destinations) {
            Files.writeString(Files.createDirectories(tree.resolve(destination))
                    .resolve(ChildCommand.DESTINATION_LISTS.get(destination)), "{}", StandardCharsets.UTF_8);
        }
    }

    /**
     * The copy {@code logs/report-<id>.json} of an ended invocation of {@code command}, in ADR-362 §3's shape, its
     * {@code arrivedAt} naming {@code generation} under the generation stage where it is not null, and {@code
     * places.deliverableRoot} {@code deliverableRoot}, or {@code null}.
     */
    private void copy(String id, String command, String started, String generation, String deliverableRoot)
            throws IOException {
        String arrivedAt = generation == null ? "[]"
                : "[{\"stage\":\"arrangement\",\"run\":\"arrangement-of-" + id + "\"},{\"stage\":\"generation\","
                        + "\"run\":\"" + generation + "\"}]";
        String report = "{\"format\":" + InvocationReportFile.FORMAT + ",\"invocation\":{\"id\":\"" + id
                + "\",\"pid\":4242,\"command\":\"" + command + "\",\"started\":\"" + started + "\",\"ended\":\""
                + started + "\",\"state\":\"ended\",\"outcome\":\"completed\",\"exitCode\":0,\"writtenAt\":\"" + started
                + "\",\"interval\":1},\"places\":{\"workingDirectory\":" + json(workingDirectory.toString())
                + ",\"corpusRoot\":" + ("run".equals(command) ? json(CORPUS_ROOT) : "null") + ",\"deliverableRoot\":"
                + (deliverableRoot == null ? "null" : json(deliverableRoot)) + "},\"steps\":[],\"legs\":[],"
                + "\"arrivedAt\":" + arrivedAt + ",\"next\":null}";
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + id + ".json"), report, StandardCharsets.UTF_8);
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
