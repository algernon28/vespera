package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.NextInvocationFile.Contents;
import io.algernon.vespera.pipeline.NextInvocationFile.Invocation;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * The file the jar writes beside the closing line for the control panel (ADR-294 §1, §3): {@code
 * next-invocation.properties} in the working directory, in {@code java.util.Properties} form, holding the next
 * command, the keys wanted, whether answers are wanted and the arrangement to approve, and no path.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class NextInvocationFileTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "next-invocation.properties";

    /** An arrangement's short name, as the closing line quotes it. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A seed folder the profile names, and the closing line may quote. */
    private static final String THE_SEED_FOLDER = "/corpus/exemplars";

    /** A deliverable tree, as the closing line names it once nothing is left to set. */
    private static final Path THE_TREE = Path.of("/work/deliverable/7c1e");

    /** ADR-088's sample of sixty questions, answered in full. */
    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    /** The approval in the profile names the arrangement this invocation arrived at. */
    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    /** The deliverable drawn at {@link #THE_TREE}, no group left unwritten, no document not copied, nothing left behind. */
    private static final NextAction.DeliverableSummary NOTHING_LEFT_UNWRITTEN_OR_UNCOPIED =
            new NextAction.DeliverableSummary(THE_TREE, 0, OptionalInt.of(0), false, 0, 0);


    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A run wanting keys is written as three properties: the command, the keys in order separated by one space, and no answers")
    void aRunWantingKeysIsWrittenAsThreeProperties(@TempDir Path workingDirectory) throws IOException {
        NextInvocationFile.write(workingDirectory, new Contents(Invocation.RUN,
                List.of("seedFolder", "boilerplateDocumentFrequencyFloor", "embeddingModel"), false, Optional.empty()));

        claim("the file holds invocation=run, the three keys in the order given, and answer=false, and nothing else",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "run",
                        "wants", "seedFolder boilerplateDocumentFrequencyFloor embeddingModel",
                        "answer", "false")));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A label wanting answers is written with an empty list of keys and answer=true")
    void aLabelWantingAnswersIsWrittenWithAnEmptyListOfKeys(@TempDir Path workingDirectory) throws IOException {
        NextInvocationFile.write(workingDirectory, new Contents(Invocation.LABEL, List.of(), true, Optional.empty()));

        claim("the file holds invocation=label, an empty wants and answer=true, and nothing else",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "label", "wants", "", "answer", "true")));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("An approval wanted is written with the arrangement's short name, the only state with a fourth property")
    void anApprovalWantedIsWrittenWithTheArrangement(@TempDir Path workingDirectory) throws IOException {
        NextInvocationFile.write(workingDirectory, new Contents(Invocation.RUN, List.of("arrangementApproved"), false,
                Optional.of(AN_ARRANGEMENT)));

        claim("the file holds invocation=run, wants=arrangementApproved, answer=false and the arrangement's short name",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "run",
                        "wants", "arrangementApproved",
                        "answer", "false",
                        "arrangement", AN_ARRANGEMENT)));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("Nothing left to set is written with no path in it, though the closing line names the deliverable's path")
    void nothingLeftIsWrittenAsNoneAndHoldsNoPath(@TempDir Path workingDirectory) throws IOException {
        Profile profile = ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor("0.4", RECORDED_BY_THE_OPERATOR)
                .embeddingModel("a-third-comparing-model", RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor("0.62", RECORDED_BY_THE_OPERATOR)
                .arrangementApproved(AN_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                .build();
        NextAction.Closing closing = NextAction.closing(profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, AN_ARRANGEMENT,
                THE_GENERATION_MODEL, NOTHING_LEFT_UNWRITTEN_OR_UNCOPIED, APPROVAL_MATCHES_THIS_INVOCATION);

        NextInvocationFile.write(workingDirectory, closing.next());
        String written = Files.readString(workingDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8);

        claim("the closing line names the deliverable's tree, so the file could have carried it",
                () -> assertThat(closing.line()).contains(THE_TREE.toString()));
        claim("the file's text holds no path at all, written or escaped: not the tree's, not the working directory's,"
                        + " and no separator of one, in its properties or its comments",
                () -> assertThat(written)
                        .doesNotContain(THE_TREE.toString())
                        .doesNotContain("deliverable")
                        .doesNotContain(workingDirectory.toString())
                        .doesNotContain("/")
                        .doesNotContain("\\"));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("Contents that break the file's rules are refused before anything is written")
    void contentsThatBreakTheRulesAreRefused() {
        claim("an arrangement given where arrangementApproved is not wanted is refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of("relevanceScoreFloor"),
                        false, Optional.of(AN_ARRANGEMENT)))).isInstanceOf(IllegalArgumentException.class));
        claim("arrangementApproved wanted with no arrangement given is refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of("arrangementApproved"),
                        false, Optional.empty()))).isInstanceOf(IllegalArgumentException.class));
        claim("answers wanted of a command that is not label are refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of(), true, Optional.empty())))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("keys wanted where nothing is left to set are refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.NONE, List.of("seedFolder"), false,
                        Optional.empty()))).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("Writing over an earlier file replaces it whole")
    void writingReplacesAnEarlierFileWhole(@TempDir Path workingDirectory) throws IOException {
        NextInvocationFile.write(workingDirectory, new Contents(Invocation.RUN, List.of("arrangementApproved"), false,
                Optional.of(AN_ARRANGEMENT)));

        NextInvocationFile.write(workingDirectory, new Contents(Invocation.NONE, List.of(), false, Optional.empty()));

        claim("the file holds only what the second write gave, with no arrangement left from the first",
                () -> assertThat(propertiesIn(workingDirectory)).isEqualTo(Map.of(
                        "invocation", "none", "wants", "", "answer", "false")));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A file that cannot be written ends no invocation, and leaves what stands at its place as it is")
    void aFileThatCannotBeWrittenThrowsNothing(@TempDir Path workingDirectory) throws IOException {
        Path inTheWay = Files.createDirectory(workingDirectory.resolve(FILE_NAME));

        Throwable thrown = catchThrowable(() -> NextInvocationFile.write(workingDirectory,
                new Contents(Invocation.LABEL, List.of(), true, Optional.empty())));

        claim("writing where a folder stands at the file's place throws nothing, so the closing line still follows",
                () -> assertThat(thrown).isNull());
        claim("and the folder is left a folder",
                () -> assertThat(inTheWay).isDirectory());
    }

    /**
     * A file that cannot be written is said at WARN, naming the file, so the operator learns why the control panel offers
     * no next act; the caller prints the closing line after {@code write} returns, so the warning comes before it
     * (ADR-294 §3).
     */
    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A file that cannot be written is said in one warning naming the file")
    void aFileThatCannotBeWrittenIsLoggedAtWarn(@TempDir Path workingDirectory) throws IOException {
        Files.createDirectory(workingDirectory.resolve(FILE_NAME));
        Logger logger = (Logger) LoggerFactory.getLogger(NextInvocationFile.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            NextInvocationFile.write(workingDirectory,
                    new Contents(Invocation.LABEL, List.of(), true, Optional.empty()));
        } finally {
            logger.detachAppender(logged);
        }
        List<Level> levels = logged.list.stream().map(ILoggingEvent::getLevel).toList();
        List<String> lines = logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        claim("writing where a folder stands at the file's place is said in exactly one line, at WARN",
                () -> assertThat(levels).containsExactly(Level.WARN));
        claim("and that line names the file that could not be written",
                () -> assertThat(lines.getFirst()).contains(workingDirectory.resolve(FILE_NAME).toString()));
    }

    @Test
    @Story("An invocation starts with no file from the one before")
    @DisplayName("Deleting removes the file, and deleting where there is none does nothing")
    void deletingRemovesTheFileAndNoneIsNoFault(@TempDir Path workingDirectory) {
        NextInvocationFile.write(workingDirectory, new Contents(Invocation.LABEL, List.of(), true, Optional.empty()));

        NextInvocationFile.deleteIn(workingDirectory);
        claim("the file is gone",
                () -> assertThat(workingDirectory.resolve(FILE_NAME)).doesNotExist());
        claim("and deleting again, with no file there, throws nothing",
                () -> assertThat(catchThrowable(() -> NextInvocationFile.deleteIn(workingDirectory))).isNull());
    }

    /** The file's properties as {@code java.util.Properties} reads them in UTF-8, comments left out. */
    private static Map<String, String> propertiesIn(Path workingDirectory) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> read = new TreeMap<>();
        properties.stringPropertyNames().forEach(name -> read.put(name, properties.getProperty(name)));
        return read;
    }
}
