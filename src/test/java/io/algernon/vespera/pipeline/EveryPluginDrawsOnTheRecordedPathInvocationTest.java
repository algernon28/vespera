package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.confluence.Publisher;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.ScriptedPlugin;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The plugins reach the drawing through {@code pipeline}, which hands {@code Deliverable.writeTo} every {@code Plugin}
 * bean (ADR-333 §1, §4): on the generation step's first drawing, and on its recorded path, where a generation step
 * already recorded redraws the whole tree from stored rows with no question put (ADR-274 §6, ADR-333 §12). A scripted
 * plugin bean beside the Confluence rendering's writes down what it hears.
 *
 * <p>And a plugin's failed drawing stops that plugin alone (ADR-333 §11): the invocation exits as it would have, the
 * tree and the Confluence rendering are drawn, and the closing line names the plugin and the type it threw; the next
 * invocation, whose drawing finishes, says nothing of it.
 *
 * <p>One static working directory serves the class, as in {@code DeliverableInvocationTest}; every claim is scoped to
 * the generation run this corpus's own walk recorded. The scripted plugin is one instance for the class's context, so
 * what it heard and what it was told to throw are reset before each test.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, EveryPluginDrawsOnTheRecordedPathInvocationTest.AScriptedPlugin.class})
@Epic("The deliverable")
@Feature("The plugins")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class EveryPluginDrawsOnTheRecordedPathInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** The scripted plugin's name, and its folder at the tree's root. */
    private static final String SCRIPTED = "scripted";

    /** The website rendering's plugin's name, and its folder at the tree's root (ADR-333 §3). */
    private static final String WEBSITE = "website";

    /** The file of a plugin's folder holding the state of its drawing (ADR-333 §11). */
    private static final String THE_STATE_OF_THE_DRAWING = "drawing.txt";

    /** What the closing line says of the scripted plugin's drawing, failed at an {@link IOException} (ADR-333 §11). */
    private static final String THE_CLAUSE = "; the " + SCRIPTED + " rendering could not be drawn (IOException), so a"
            + " publish to it is refused until a drawing finishes.";

    /** The scripted plugin bean, one for the context, beside the Confluence rendering's. */
    @TestConfiguration
    static class AScriptedPlugin {

        static final List<String> HEARD = Collections.synchronizedList(new ArrayList<>());

        static final ScriptedPlugin THE_PLUGIN = new ScriptedPlugin(SCRIPTED, HEARD);

        @Bean
        ScriptedPlugin scriptedPlugin() {
            return THE_PLUGIN;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void forgetWhatAnEarlierTestScriptedOrHeard() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        AScriptedPlugin.THE_PLUGIN.healthy();
        AScriptedPlugin.HEARD.clear();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        AScriptedPlugin.THE_PLUGIN.healthy();
    }

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Every plugin draws on every drawing")
    @DisplayName("Every plugin bean draws on the generation step's first drawing, and again on its recorded path")
    void everyPluginDrawsOnTheFirstDrawingAndOnTheRecordedPath(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        AScriptedPlugin.HEARD.clear();

        cli.run("run", root.toString());
        List<String> first = AScriptedPlugin.THE_PLUGIN.calls();
        int firstExit = cli.getExitCode();
        AScriptedPlugin.HEARD.clear();

        cli.run("run", root.toString());
        List<String> again = AScriptedPlugin.THE_PLUGIN.calls();

        claim(
                "both invocations succeeded, under one generation run, so the second drew from what the first recorded",
                () -> {
                    assertThat(firstExit).isZero();
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(generationRuns(root)).hasSize(1);
                });
        claim(
                "the first drawing opened the scripted plugin's drawing, started the seed partition and finished",
                () -> assertThat(first).startsWith(ScriptedPlugin.DRAWING)
                        .anyMatch(call -> call.startsWith(ScriptedPlugin.PARTITION_STARTED))
                        .contains(ScriptedPlugin.FINISH + " " + Deliverable.MANIFEST_FILE_NAME + " standing"));
        claim(
                "and the recorded path drew it again, call for call",
                () -> assertThat(again).isEqualTo(first));
        claim(
                "each plugin's drawing.txt in the tree says drawn, the Confluence rendering's, the website rendering's"
                        + " and the scripted one's",
                () -> {
                    assertThat(stateOf(theTreeOf(root), Publisher.RENDERING_FOLDER)).isEqualTo("drawn");
                    assertThat(stateOf(theTreeOf(root), WEBSITE)).isEqualTo("drawn");
                    assertThat(stateOf(theTreeOf(root), SCRIPTED)).isEqualTo("drawn");
                });
        claim(
                "and the website rendering's list, files.json, stands beside its pages, as the page list stands in"
                        + " confluence/ (ADR-333 §3)",
                () -> assertThat(theTreeOf(root).resolve(WEBSITE).resolve("files.json")).isRegularFile());
    }

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("A plugin whose drawing fails is named on the closing line, the invocation succeeding and the tree drawn, and the next drawing says nothing of it")
    void aPluginWhoseDrawingFailsIsNamedOnTheClosingLine(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        AScriptedPlugin.THE_PLUGIN.failingAt(ScriptedPlugin.PARTITION_STARTED, ScriptedPlugin.Failure.IO_EXCEPTION);

        cli.run("run", root.toString());
        int exit = cli.getExitCode();
        String closing = operatorLines().getLast();
        Path tree = theTreeOf(root);

        claim(
                "the invocation exits as it would have, the deliverable being drawn",
                () -> assertThat(exit).isZero());
        claim(
                "the HTML tree is in place and the Confluence rendering drawn whole beside it",
                () -> {
                    assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).isRegularFile();
                    assertThat(tree.resolve(Deliverable.MANIFEST_FILE_NAME)).isRegularFile();
                    assertThat(tree.resolve(Publisher.RENDERING_FOLDER).resolve(Publisher.PAGE_LIST_FILE_NAME))
                            .isRegularFile();
                    assertThat(stateOf(tree, Publisher.RENDERING_FOLDER)).isEqualTo("drawn");
                });
        claim(
                "the scripted plugin's drawing.txt says failed",
                () -> assertThat(stateOf(tree, SCRIPTED)).isEqualTo("failed"));
        claim(
                "and the closing line names the tree's index and ends on the plugin and the type it threw, no message"
                        + " of the exception's in it",
                () -> assertThat(closing)
                        .contains(tree.resolve(Deliverable.INDEX_FILE_NAME).toString())
                        .endsWith(THE_CLAUSE)
                        .doesNotContain("scripted at"));

        AScriptedPlugin.THE_PLUGIN.healthy();
        cli.run("run", root.toString());

        claim(
                "the next invocation draws it, its drawing.txt saying drawn, and its closing line says nothing of a"
                        + " rendering not drawn",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(stateOf(theTreeOf(root), SCRIPTED)).isEqualTo("drawn");
                    assertThat(operatorLines().getLast()).doesNotContain("rendering could not be drawn");
                });
    }

    /** What {@code plugin}'s drawing.txt in {@code tree} says, stripped. */
    private static String stateOf(Path tree, String plugin) throws IOException {
        return Files.readString(tree.resolve(plugin).resolve(THE_STATE_OF_THE_DRAWING), StandardCharsets.UTF_8).strip();
    }

    /** The tree this corpus's one generation run drew. */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRuns(root).getLast());
    }

    /** Every line this application wrote for an operator, in the order they were written. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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
                .boilerplateDocumentFrequencyFloor("1.0", "set by this test, so stage 4's gate is open")
                .embeddingModel("qwen3-embedding:0.6b", "set by this test, so gate 3 is open")
                .relevanceScoreFloor("0.0", "set by this test, so nothing earlier is asked for")
                .generationContextWindow("4096", "set by this test, so every test here reads alike")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .build());
    }

    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** The generation runs recorded over {@code root}, oldest first. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
