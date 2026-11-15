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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A row of the window's list of working directories (ADR-393 §5): the folder's name; the corpus root and the furthest
 * leg reached, from the copy in {@code logs} of the last {@code vespera run}'s report; the last invocation's command,
 * local date and outcome, and the next value, from {@code report.json}; read without opening the database. A folder no
 * invocation wrote in says so, and one another process holds says since when, from its holder line.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class EachWorkingDirectoryIsARowOfWhatItsReportSaysTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private static final String NAME = "Letters_20261114";

    /** The corpus root the last run's copy names, as text: the row shows it as the report holds it. */
    private static final String CORPUS_ROOT = "/archives/Letters";

    @TempDir
    Path parent;

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A row shows the folder's name, its documents folder, its last command's local date and outcome, the furthest part its last run reached and the value wanted next")
    void aRowShowsItsNameRootLastInvocationLegAndNextValue() throws IOException {
        Path folder = Files.createDirectories(parent.resolve(NAME));
        String run = report("20261112T080000Z", "run", "2026-11-12T08:00:00.000Z", "stopped", json(CORPUS_ROOT),
                "[" + leg("deduplication", "finished") + "," + leg("relevance", "finished") + ","
                        + leg("clustering", "stopped at a gate") + "," + leg("generation", "not started") + "]",
                "{\"invocation\":\"run\",\"wants\":[\"relevanceScoreFloor\"],\"answer\":false,\"arrangement\":null}");
        String label = report("20261113T233000Z", "label", "2026-11-13T23:30:00.000Z", "recorded", "null",
                "[" + leg("deduplication", "not started") + "," + leg("relevance", "not started") + ","
                        + leg("clustering", "not started") + "," + leg("generation", "not started") + "]",
                "{\"invocation\":\"run\",\"wants\":[\"relevanceScoreFloor\",\"clusterResolution\"],\"answer\":false,"
                        + "\"arrangement\":null}");
        Path logs = Files.createDirectories(folder.resolve("logs"));
        Files.writeString(logs.resolve("report-20261112T080000Z.json"), run, StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("report-20261113T233000Z.json"), label, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve(InvocationReportFile.FILE_NAME), label, StandardCharsets.UTF_8);

        WorkingDirectoryRow row = WorkingDirectoryRow.read(folder, ROME);

        claim("the row is the folder's name; the root and the clustering part stopped at a gate from the run's copy,"
                        + " not the later label's, which names no root and reached no part; label on 14 November in"
                        + " Rome, though 13 November in UTC, recorded; and the two values the last report wants next",
                () -> assertThat(row).isEqualTo(new WorkingDirectoryRow(NAME, CORPUS_ROOT,
                        "label on 2026-11-14, recorded", "clustering, stopped at a gate",
                        "relevanceScoreFloor, clusterResolution")));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A folder no command has written in says it has no run and nothing yet")
    void aFolderNoInvocationWroteInSaysSo() throws IOException {
        Path folder = Files.createDirectories(parent.resolve(NAME));

        WorkingDirectoryRow row = WorkingDirectoryRow.read(folder, ROME);

        claim("the row reads its name, no run yet, nothing yet, none and nothing",
                () -> assertThat(row).isEqualTo(
                        new WorkingDirectoryRow(NAME, "no run yet", "nothing yet", "none", "nothing")));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A folder another process holds says it is in use by another process since the time its lock says")
    void aFolderAnotherProcessHoldsSaysSinceWhen() throws IOException {
        Path folder = Files.createDirectories(parent.resolve(NAME));
        long another = ProcessHandle.current().parent().orElseThrow().pid();
        Instant started = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Files.writeString(folder.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME),
                "pid=" + another + " started=" + started.atOffset(ZoneOffset.UTC) + " command=run working-directory="
                        + folder + "\n",
                StandardCharsets.UTF_8);
        String since = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(started);

        WorkingDirectoryRow row = WorkingDirectoryRow.read(folder, ZoneOffset.UTC);

        claim("the last invocation reads in use by another process since the lock's start, to the minute, in place of"
                        + " what a report would say",
                () -> assertThat(row.lastInvocation()).isEqualTo("in use by another process since " + since));
    }

    /** A report of an ended invocation of {@code command}, in ADR-362 §3's shape, with the fields a row reads. */
    private static String report(String id, String command, String started, String outcome, String corpusRoot,
            String legs, String next) {
        return "{\"format\":" + InvocationReportFile.FORMAT + ",\"invocation\":{\"id\":\"" + id + "\",\"pid\":4242,\"command\":\"" + command
                + "\",\"started\":\"" + started + "\",\"ended\":\"" + started + "\",\"state\":\"ended\",\"outcome\":\""
                + outcome + "\",\"exitCode\":0,\"writtenAt\":\"" + started + "\",\"interval\":1},"
                + "\"places\":{\"workingDirectory\":\"/work/" + NAME + "\",\"corpusRoot\":" + corpusRoot
                + ",\"deliverableRoot\":null},\"steps\":[],\"legs\":" + legs + ",\"arrivedAt\":[],\"next\":" + next
                + "}";
    }

    private static String leg(String name, String state) {
        return "{\"name\":\"" + name + "\",\"state\":\"" + state + "\",\"open\":false}";
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
