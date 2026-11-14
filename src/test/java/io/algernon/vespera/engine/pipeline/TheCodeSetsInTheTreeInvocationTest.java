package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.RunId;
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
import java.util.ArrayList;
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
 * What a whole campaign over an archive holding code sets tells of ADR-377 §9: a set whose folder in the tree's
 * {@code code/} is not the one the tree's earlier {@code code.csv} gave it is told in one line at INFO on the generation
 * step's logger, naming the two folders of {@code code/} and no path of the archive, before the closing line; and a
 * drawing whose earlier listing already names each set's folder tells nothing.
 *
 * <p>The archive is {@code TheTreeInHtmlInvocationTest}'s two documents and two code sets, each of a build file and one
 * file listed as code. The test approves the arrangement in a first invocation, which stops at it, draws the tree in a
 * second, gives both sets other folders in that tree's {@code code.csv}, and claims what the third and fourth say. One
 * static working directory serves the class, and every claim is scoped to this corpus's own lines and runs.
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

    /** The ending listed as code, so that each set's second file is code by its name. */
    private static final String CODE_ENDING = ".java";

    /** The corpus's two documents, by their paths beneath the root. */
    private static final String FIRST = "corpus.txt";

    private static final String SECOND = "another-corpus-document.txt";

    /** The two code sets' folders, each its folder's name in {@code code/}. */
    private static final List<String> THE_SETS = List.of("officina", "bottega");

    /** What the earlier listing gives each set's folder in {@code code/}: its name with this after it. */
    private static final String EARLIER = "-before";

    /** What the line of a set whose folder changed opens with. */
    private static final String A_CHANGED_FOLDER = "A code set's folder in code/ is now ";

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
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        applicationLogger.detachAppender(logged);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A code set whose folder in code/ changed since the tree was last drawn is told in one line at INFO, naming the two folders of code/ alone, before the closing line, and the next drawing tells nothing")
    void aSetWhoseFolderInCodeChangedIsOneLineAtInfo(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpusWithTwoCodeSets(root, seeds);
        cli.run("run", root.toString());
        Path listing = theTreeOf(root).resolve("code.csv");
        List<String> drawnOrder = setsInTheOrderOf(Files.readString(listing, StandardCharsets.UTF_8));
        String earlier = Files.readString(listing, StandardCharsets.UTF_8);
        for (String set : THE_SETS) {
            earlier = earlier.replace("code/" + set + "/", "code/" + set + EARLIER + "/");
        }
        Files.writeString(listing, earlier, StandardCharsets.UTF_8);
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> events = List.copyOf(logged.list);
        List<ILoggingEvent> changed = events.stream()
                .filter(event -> event.getFormattedMessage().startsWith(A_CHANGED_FOLDER))
                .toList();
        List<String> expected = drawnOrder.stream()
                .map(set -> A_CHANGED_FOLDER + set + ", no longer " + set + EARLIER + ", which is left as it is.")
                .toList();
        claim(
                "the precondition: the drawing before put both sets in code/, each under its folder's name",
                () -> assertThat(drawnOrder).containsExactlyInAnyOrderElementsOf(THE_SETS));
        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it tells " + THE_SETS.size() + " lines of a folder changed, one a set, in the order the sets are drawn,"
                        + " each naming the folder now and the earlier one",
                () -> assertThat(changed).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactlyElementsOf(expected));
        claim(
                "each at INFO on the generation step's logger, naming no path of the corpus root or the working"
                        + " directory",
                () -> assertThat(changed).allSatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getLoggerName()).isEqualTo(GenerationTasklet.class.getName());
                    assertThat(event.getFormattedMessage())
                            .doesNotContain(root.toString(), workingDirectory.toString());
                }));
        claim(
                "both come before the closing line, the last line the invocation writes",
                () -> assertThat(changed).allSatisfy(event ->
                        assertThat(events.indexOf(event)).isLessThan(events.size() - 1)));
        claim(
                "and the generation step warns of nothing",
                () -> assertThat(events).noneMatch(event -> event.getLoggerName().equals(GenerationTasklet.class.getName())
                        && event.getLevel().isGreaterOrEqual(Level.WARN)));

        logged.list.clear();
        cli.run("run", root.toString());
        List<String> next = new ArrayList<>();
        logged.list.forEach(event -> next.add(event.getFormattedMessage()));
        claim(
                "the next invocation, drawing the same tree over the listing the last one left, tells no such line",
                () -> assertThat(next).isNotEmpty().noneMatch(line -> line.startsWith(A_CHANGED_FOLDER)));
    }

    /** Each set of {@link #THE_SETS} in the order its first copy appears in {@code listing}'s text. */
    private static List<String> setsInTheOrderOf(String listing) {
        return THE_SETS.stream()
                .filter(set -> listing.contains("code/" + set + "/"))
                .sorted((one, other) -> Integer.compare(
                        listing.indexOf("code/" + one + "/"), listing.indexOf("code/" + other + "/")))
                .toList();
    }

    /**
     * The tree of the latest generation run over {@code root}'s walks, read through {@link RunsOver}, which compares
     * the root the walk stored, its real path (a runner's temporary folder can be given by its short name).
     */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve("deliverable").resolve(new RunsOver(jdbcTemplate, root).latest("generation").value());
    }

    /**
     * A corpus of two documents and two code sets of two members each, walked once, with the arrangement it produced
     * approved.
     */
    private void anApprovedCorpusWithTwoCodeSets(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(FIRST), "a corpus document");
        Files.writeString(root.resolve(SECOND), "a second corpus document");
        for (String set : THE_SETS) {
            Files.createDirectories(root.resolve(set).resolve("src"));
            Files.writeString(root.resolve(set).resolve("Makefile"), "all:\n\tjavac src/Attrezzo.java\n");
            Files.writeString(root.resolve(set).resolve("src/Attrezzo.java"),
                    "class Attrezzo {\n    String dove() { return \"" + set + "\"; }\n}\n");
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .codeNameEndings(CODE_ENDING, "set by this test, so the archive holds code sets")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(root));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    private RunId theLatestArrangement(Path root) {
        return new RunsOver(jdbcTemplate, root).latest("arrangement");
    }
}
