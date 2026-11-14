package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A code set whose folder in the tree's {@code code/} changed since the listing the tree held is told in one line at
 * INFO, through the whole command (ADR-377 §9): a corpus of two documents and two code sets, {@code alpha} and {@code
 * beta}, each a folder holding a build file and a file of code, is taken to the tree; its listing of code is then
 * rewritten as an earlier drawing that named the two sets otherwise would have left it, and the next invocation draws
 * the tree again.
 *
 * <p>The lines are read on the generation step's logger, in the order they were logged, beside the operator's closing
 * line. One static working directory serves the class, as in {@code TheTreeInHtmlInvocationTest}.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("807")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheCodeSetsInTheTreeInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The two code sets, in the order the sets are drawn, by their folders' names, which are their default names. */
    private static final List<String> THE_SETS = List.of("alpha", "beta");

    /** What an earlier drawing put before each set's name in code/. */
    private static final String EARLIER = "old-";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void listen() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void stopListening() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        applicationLogger.detachAppender(logged);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("Each code set whose folder in code/ changed is told in one line at INFO before the closing line, and the next run tells none")
    void aSetWhoseFolderInCodeChangedIsOneLineAtInfo(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpusWithCode(root, seeds);
        cli.run("run", root.toString());
        Path listing = theTreeOf(root).resolve("code.csv");
        String drawn = Files.readString(listing, StandardCharsets.UTF_8);
        String earlier = drawn;
        for (String set : THE_SETS) {
            earlier = earlier.replace("code/" + set + "/", "code/" + EARLIER + set + "/");
        }
        Files.writeString(listing, earlier, StandardCharsets.UTF_8);
        logged.list.clear();

        cli.run("run", root.toString());
        List<ILoggingEvent> second = List.copyOf(logged.list);
        logged.list.clear();
        cli.run("run", root.toString());
        List<ILoggingEvent> third = List.copyOf(logged.list);

        List<String> expected = THE_SETS.stream().map(set -> "A code set's folder in code/ is now " + set
                + ", no longer " + EARLIER + set + ", which is left as it is.").toList();
        claim("the first drawing listed each set's copies in its folder named as the set's folder is",
                () -> assertThat(drawn).contains("code/alpha/", "code/beta/"));
        claim("the invocations report success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the drawing over the earlier listing logs one line at INFO on the generation step's logger for each set"
                        + " whose folder changed, in the order the sets are drawn, each naming its two folders of code/",
                () -> assertThat(changesIn(second)).containsExactlyElementsOf(expected));
        claim("no such line names a path of the corpus root",
                () -> assertThat(changesIn(second)).noneMatch(line -> line.contains(root.toString())
                        || line.contains(root.toString().replace('\\', '/'))));
        claim("both lines come before the closing line",
                () -> assertThat(indexOfLast(second, expected.getLast()))
                        .isLessThan(indexOfClosingLine(second)));
        claim("and the generation step warns of nothing",
                () -> assertThat(second).noneMatch(event -> event.getLoggerName().equals(GenerationTasklet.class.getName())
                        && event.getLevel().isGreaterOrEqual(Level.WARN)));
        claim("the next invocation, drawing the same tree over the listing the last one left, logs no such line",
                () -> assertThat(changesIn(third)).isEmpty());
    }

    /** The generation step's lines that tell a code set's folder changed, in order. */
    private static List<String> changesIn(List<ILoggingEvent> events) {
        return events.stream()
                .filter(event -> event.getLoggerName().equals(GenerationTasklet.class.getName()))
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.startsWith("A code set's folder in code/"))
                .toList();
    }

    private static int indexOfLast(List<ILoggingEvent> events, String line) {
        List<String> lines = events.stream().map(ILoggingEvent::getFormattedMessage).toList();
        return lines.lastIndexOf(line);
    }

    /** Where the closing line stands: the last operator line that is not the report's (ADR-362 section 7). */
    private static int indexOfClosingLine(List<ILoggingEvent> events) {
        for (int at = events.size() - 1; at >= 0; at--) {
            ILoggingEvent event = events.get(at);
            if (event.getLoggerName().equals(OperatorLines.LOGGER_NAME)
                    && !event.getFormattedMessage().startsWith("Report: ")) {
                return at;
            }
        }
        return -1;
    }

    /** The tree this corpus's latest generation run drew. */
    private Path theTreeOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
        return workingDirectory.resolve("deliverable").resolve(runs.getLast());
    }

    /** A corpus of two documents and two code sets, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpusWithCode(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        for (String set : THE_SETS) {
            Files.createDirectories(root.resolve(set));
            Files.writeString(root.resolve(set).resolve("pom.xml"), "<project/>\n");
            Files.writeString(root.resolve(set).resolve("Main.java"), "class Main {}\n");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .codeNameEndings(".java", "set by this test, so each folder's file is code")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement"));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }
}
