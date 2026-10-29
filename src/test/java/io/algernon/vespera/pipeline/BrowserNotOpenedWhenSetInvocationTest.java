package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera.open-browser} set false opens nothing (ADR-327 section 5): neither the progress page, by {@code
 * vespera run} or {@code vespera label}, nor the deliverable's index, by the generation step, while the command's
 * first line still names the page and the closing line the index. Reached through whole invocations, with the
 * slice's opener, which records what it is asked and opens nothing; so what is held is that it is asked nothing.
 *
 * <p>The default, true unless set, is held by {@link ProgressPageOpensInvocationTest} and {@link
 * TheTreeInHtmlInvocationTest}, whose contexts set no such value and whose opener is asked once for each page.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-327", url = Adr.AN_ENDED_PROGRESS_PAGE_KEEPS_RELOADING_AND_A_SETTING_OPENS_NO_BROWSER, type = "adr")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
@Issue("664")
class BrowserNotOpenedWhenSetInvocationTest {

    /** The setting, and the value that turns opening off. */
    private static final String OPEN_BROWSER = "vespera.open-browser";

    private static final String OFF = "false";

    /** The page's name in the working directory. */
    private static final String PAGE = "progress.html";

    /** What the first line of a command that writes the page opens with, before the page's absolute path. */
    private static final String PATH_LINE = "Progress page: ";

    /** The loggers of the application, whose last line is the closing line. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The file a scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndNoBrowser(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(OPEN_BROWSER, () -> OFF);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingPageOpener opener;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    @BeforeEach
    void nothingAskedOrLogged() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLines() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        rootLogger.detachAppender(logged);
        logged.stop();
        opener.forget();
    }

    @Test
    @Story("A setting stops a command from opening the browser")
    @DisplayName("With opening the browser turned off, two runs, the second writing the deliverable, open neither the progress page nor the deliverable's index, and the second still names both")
    void vesperaRunOpensNeitherThePageNorTheIndexAndStillNamesBoth(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the second run reported success", () -> assertThat(cli.getExitCode()).isZero());
        Path index = theTreeOf(root).resolve("index.html");
        claim(
                "the second run wrote the deliverable's index, so it reached the step that opens it",
                () -> assertThat(index).isRegularFile());
        claim(
                "the opener was asked nothing by either run: not for the progress page as each started, and not for"
                        + " the index once it was in place",
                () -> assertThat(opener.requests()).isEmpty());
        claim(
                "the second run's first line is still \"" + PATH_LINE + "\" and the page's absolute path, at the level"
                        + " the console shows",
                () -> {
                    assertThat(logged.list).isNotEmpty();
                    assertThat(logged.list.getFirst().getFormattedMessage()).isEqualTo(PATH_LINE + thePagesPath());
                    assertThat(logged.list.getFirst().getLevel()).isEqualTo(Level.INFO);
                });
        claim(
                "and its last line still names the index by its whole path",
                () -> assertThat(applicationLines().getLast())
                        .contains(index + ", with 0 groups left unwritten and 0 documents not copied."));
    }

    @Test
    @Story("A setting stops a command from opening the browser")
    @DisplayName("With opening the browser turned off, the label command records its answers, opens nothing, and still names the progress page on its first line")
    void vesperaLabelOpensNothingAndStillNamesThePageFirst(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();
        opener.forget();
        logged.list.clear();

        cli.run("label");

        claim("the label command reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the opener was asked nothing", () -> assertThat(opener.requests()).isEmpty());
        claim(
                "the label command's first line is still \"" + PATH_LINE + "\" and the page's absolute path, at the"
                        + " level the console shows",
                () -> {
                    assertThat(logged.list).isNotEmpty();
                    assertThat(logged.list.getFirst().getFormattedMessage()).isEqualTo(PATH_LINE + thePagesPath());
                    assertThat(logged.list.getFirst().getLevel()).isEqualTo(Level.INFO);
                });
    }

    /** The page's absolute path in the working directory, as the command names it. */
    private static Path thePagesPath() {
        return workingDirectory.resolve(PAGE).toAbsolutePath().normalize();
    }

    /** The lines the application's own loggers wrote, in order. */
    private List<String> applicationLines() {
        return logged.list.stream()
                .filter(event -> event.getLoggerName().startsWith(APPLICATION_LOGGER))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus-document.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString())));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    /** The tree of the latest generation run over {@code root}'s walks, in the working directory's deliverable. */
    private Path theTreeOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
        return workingDirectory.resolve("deliverable").resolve(runs.isEmpty() ? "none" : runs.getLast());
    }

    /** One corpus document and one seed, with every value the run needs, so it goes as far as the label file. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private static void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }
}
