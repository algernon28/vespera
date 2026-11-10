package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The two lines of the report a command prints (ADR-362 section 7), reached through whole invocations: the first, naming
 * the page the report is drawn as, where the progress page's line was; the last, naming what was written and why nothing
 * was opened, after the closing line; and the refusals after the lock, which print no first line and end on their
 * own line and then the report line.
 *
 * <p>The display is answered by a seam this class gives the application. The sidecar starter is the production one,
 * over a health check this class switches, so that one test of the refusal of a start can be made in a class whose
 * other tests start nothing.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, TheReportLinesInvocationTest.Seams.class})
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("761")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheReportLinesInvocationTest {

    private static final String REPORT_PREFIX = "Report: ";

    private static final String OPENED_WHERE_A_DISPLAY_IS =
            "a command opens no window, and the control panel's is the only one";

    private static final String OPENED_WHERE_NONE_IS = "there is no display here (neither DISPLAY nor WAYLAND_DISPLAY is set)";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path lockFolder;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    /** The display the application is given and the sidecars' health, as each test sets them. */
    @TestConfiguration
    static class Seams {

        static volatile DisplayPresence.Answer answer = new DisplayPresence.Answer(true, "display-variable", null);

        static final AtomicBoolean SIDECARS_ANSWER = new AtomicBoolean(true);

        @Bean
        InvocationReport.Display scriptedDisplay() {
            return () -> answer;
        }

        @Bean
        SidecarStarter sidecarStarter() {
            Path repository = Path.of("").toAbsolutePath();
            SidecarStartSettings settings = new SidecarStartSettings(
                    true,
                    URI.create("http://localhost:5001"),
                    URI.create("http://localhost:11434"),
                    ComposeFiles.in(repository).cpuImage(),
                    Duration.ofSeconds(1));
            SidecarHealth health = new SidecarHealth() {
                @Override
                public boolean doclingAnswers() {
                    return SIDECARS_ANSWER.get();
                }

                @Override
                public boolean ollamaAnswers() {
                    return SIDECARS_ANSWER.get();
                }
            };
            return new SidecarStarter(settings, ScriptedDocker.notInstalled(), health, HostSystem.LINUX,
                    () -> ComposeFiles.in(repository), lockFolder, Duration.ofMillis(20));
        }
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureTheLinesAndSetTheSeams() throws IOException {
        profileStore.save(ProfileFixture.profile().build());
        Seams.answer = new DisplayPresence.Answer(true, "display-variable", null);
        Seams.SIDECARS_ANSWER.set(true);
        deleteTheFolders();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLines() throws IOException {
        applicationLogger.detachAppender(logged);
        logged.stop();
        deleteTheFolders();
    }

    @Test
    @Story("A run says first where its report is drawn")
    @DisplayName("The first line a run prints is Report: and the absolute path of report.html, where the progress page's line was, and no line before it names the page")
    void theFirstLineNamesTheReportsDrawing(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "a corpus document");

        cli.run("run", root.toString());

        List<String> lines = operatorLines();
        int first = firstReportLine(lines);
        claim("the first line that starts Report: is that word and report.html's absolute path, in the form the system"
                        + " writes it",
                () -> assertThat(lines.get(first)).isEqualTo(REPORT_PREFIX + ReportHtml.pathIn(workingDirectory)));
        claim("the line after it is the one that names the root walked, as the progress page's line was followed",
                () -> assertThat(lines.get(first + 1)).startsWith("Walking "));
        claim("and no line names a progress page any more",
                () -> assertThat(lines).noneMatch(line -> line.startsWith("Progress page: ")));
        claim("the report's own lines are two: this first one and the last",
                () -> assertThat(lines.stream().filter(line -> line.startsWith(REPORT_PREFIX))).hasSize(2));
    }

    @Test
    @Story("A run says last what was written and why nothing was opened")
    @DisplayName("The last line names report.json and report.html and the clause of a display present and of none, and the line before it is the closing line")
    void theLastLineNamesBothFilesAndWhyNothingWasOpened(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "a corpus document");

        cli.run("run", root.toString());
        List<String> withADisplay = operatorLines();
        logged.list.clear();
        Seams.answer = new DisplayPresence.Answer(false, "no-display-variable", null);
        cli.run("run", root.toString());
        List<String> withNone = operatorLines();

        String files = InvocationReport.pathIn(workingDirectory) + ", drawn as " + ReportHtml.pathIn(workingDirectory);
        claim("with a display present, the last line names both files and says a command opens no window",
                () -> assertThat(withADisplay.getLast())
                        .isEqualTo(REPORT_PREFIX + files + ". Nothing was opened: " + OPENED_WHERE_A_DISPLAY_IS + "."));
        claim("the line before it is the closing line, naming the next value",
                () -> assertThat(withADisplay.get(withADisplay.size() - 2)).contains("Next:"));
        claim("with no display, the last line says there is none, and why in words",
                () -> assertThat(withNone.getLast())
                        .isEqualTo(REPORT_PREFIX + files + ". Nothing was opened: " + OPENED_WHERE_NONE_IS + "."));
        claim("and the closing line is still the last line that does not start Report: ",
                () -> assertThat(lastThatIsNotAReportLine(withNone)).contains("Next:"));
    }

    @Test
    @Story("A last write that fails names the file that failed")
    @DisplayName("A folder in report.json's place, in report.html's and in the copy's: not written to the first, not drawn as the second and not copied to the third, each with the exception's class name")
    void aLastWriteThatFailsNamesTheFileThatFailed(@TempDir Path folder) throws IOException {
        Path first = Files.createDirectories(folder.resolve("first"));
        Files.createDirectories(InvocationReport.pathIn(first).resolve("holds-the-folder-there"));
        InvocationReport withoutJson = SixJobsForTests.aReport(first);
        withoutJson.begins(InvocationReport.RUN);
        withoutJson.runEnds(0, "the closing line", null);

        Path second = Files.createDirectories(folder.resolve("second"));
        Files.createDirectories(ReportHtml.pathIn(second).resolve("holds-the-folder-there"));
        InvocationReport withoutHtml = SixJobsForTests.aReport(second);
        withoutHtml.begins(InvocationReport.RUN);
        withoutHtml.runEnds(0, "the closing line", null);

        Path third = Files.createDirectories(folder.resolve("third"));
        InvocationReport withoutCopy = SixJobsForTests.aReport(third);
        withoutCopy.begins(InvocationReport.RUN);
        Path copy = onlyFileIn(third.resolve("logs"));
        Files.createDirectories(copy.resolveSibling(copy.getFileName() + ".part").resolve("holds-the-folder-there"));
        withoutCopy.runEnds(0, "the closing line", null);

        String opened = ". Nothing was opened: " + OPENED_WHERE_A_DISPLAY_IS + ".";
        claim("a folder where report.json goes: not written to it, with the exception's class name, and nothing of"
                        + " report.html, which is drawn from it",
                () -> assertThat(withoutJson.reportLine()).startsWith(REPORT_PREFIX + "not written to "
                        + InvocationReport.pathIn(first) + ": java.").endsWith(opened).doesNotContain("report.html"));
        claim("a folder where report.html goes: report.json written, and not drawn as report.html with the class name",
                () -> assertThat(withoutHtml.reportLine()).startsWith(REPORT_PREFIX + InvocationReport.pathIn(second)
                        + ", not drawn as " + ReportHtml.pathIn(second) + ": java.").endsWith(opened));
        claim("a folder where the copy goes: both written, and the copy named as not copied, after them",
                () -> assertThat(withoutCopy.reportLine()).startsWith(REPORT_PREFIX + InvocationReport.pathIn(third)
                        + ", drawn as " + ReportHtml.pathIn(third) + ", not copied to " + copy + ": java.")
                        .endsWith(opened));
    }

    @Test
    @Story("A refusal after the lock ends on its own line, then the report line")
    @DisplayName("A missing root, a misplaced code set folder and a sidecar start refused: no first line, the refusal line on standard error, one Report: line after it, and the report's refusals and closing holding it")
    void aRunRefusedAfterTheLockPrintsNoFirstLineAndEndsOnItsRefusalThenTheReportLine(@TempDir Path root)
            throws IOException {
        Files.writeString(root.resolve("document.txt"), "a corpus document");

        Refused missingRoot = refused(() -> cli.run("run"));

        ACodeSetFolderOverlappingIsRefusedInvocationTest.TheProfileAsText.write(profileStore, workingDirectory.toAbsolutePath().toString(), java.util.Optional.empty());
        Refused misplaced = refused(() -> cli.run("run", root.toString()));
        profileStore.save(ProfileFixture.profile().build());

        Seams.SIDECARS_ANSWER.set(false);
        Refused noSidecar = refused(() -> cli.run("run", root.toString()));

        for (Refused each : List.of(missingRoot, misplaced, noSidecar)) {
            claim("the refusal is the invocation's closing line, printed whole on standard error as it was",
                    () -> assertThat(each.printed().lines().filter(line -> !line.isBlank()).toList())
                            .containsExactly(each.closing()));
            claim("no line Report: <report.html> comes before it, and one report line, the last, comes after",
                    () -> {
                        assertThat(each.lines()).hasSize(1);
                        assertThat(each.lines().getFirst()).startsWith(REPORT_PREFIX
                                + InvocationReport.pathIn(workingDirectory) + ", drawn as ");
                        assertThat(each.lines().getFirst()).endsWith("Nothing was opened: "
                                + OPENED_WHERE_A_DISPLAY_IS + ".");
                    });
            claim("and the report's refusals and closing hold the line",
                    () -> {
                        var report = TheReportFile.parse(each.reportJson());
                        assertThat(report.get("refusals").get(0).asString()).isEqualTo(each.closing());
                        assertThat(report.get("closing").asString()).isEqualTo(each.closing());
                    });
        }
        claim("the three refusals are three different lines",
                () -> assertThat(List.of(missingRoot.closing(), misplaced.closing(), noSidecar.closing()))
                        .doesNotHaveDuplicates());
    }

    /** What one refused invocation printed, the report lines it logged, and the report it left. */
    private record Refused(String printed, List<String> lines, String reportJson) {

        /** The refusal line: the one line printed on standard error. */
        String closing() {
            return printed.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        }
    }

    private Refused refused(Runnable invocation) {
        logged.list.clear();
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            invocation.run();
        } finally {
            System.setErr(original);
        }
        assertThat(cli.getExitCode()).as("the invocation was refused").isNotZero();
        return new Refused(
                captured.toString(StandardCharsets.UTF_8),
                operatorLines().stream().filter(line -> line.startsWith(REPORT_PREFIX)).toList(),
                TheReportFile.textIn(workingDirectory));
    }

    private static int firstReportLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(REPORT_PREFIX)) {
                return i;
            }
        }
        throw new AssertionError("no line starts Report: ");
    }

    private static String lastThatIsNotAReportLine(List<String> lines) {
        return lines.reversed().stream().filter(line -> !line.startsWith(REPORT_PREFIX)).findFirst().orElse("");
    }

    private static Path onlyFileIn(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static void deleteTheFolders() throws IOException {
        for (String name : List.of(InvocationReport.FILE_NAME, "report.html")) {
            Files.deleteIfExists(workingDirectory.resolve(name));
        }
    }
}
