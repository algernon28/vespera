package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rows of what the next invocation wants are the working directory shown's own (ADR-404): read from that
 * directory's {@code next-invocation.properties} as the window opens (§1), as another working directory is chosen
 * (§1, §2), as the window's own child ends however it ended (§5) and as another process's hold of the directory shown
 * ends (§5); a directory with no file, or one that cannot be read, draws no row; and the arrangement offered is the
 * one the directory's own file names (§3).
 *
 * <p>Each test shows the window as the control panel shows it, on the Headless platform (ADR-342 §6), with dialogs the
 * test answers, and with the machine's folder one under the test's folder, so that no test reads the folder the tests
 * are started in. A working directory is changed as the operator changes it: by choosing its row in the list (ADR-393
 * §4).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("814")
@Link(name = "ADR-404", url = Adr.THE_VALUES_WANTED_ARE_READ_FROM_THE_WORKING_DIRECTORY_SHOWN, type = "adr")
class TheValuesWantedFollowTheWorkingDirectoryTest {

    private static final String USER = System.getProperty("user.name");

    /** The prefix of the id of each row the window draws for a value wanted, followed by its key. */
    private static final String ROW = "wanted-row-";

    /** The value the first working directory's file wants. */
    private static final ProfileKey THE_FIRSTS = ProfileKey.RELEVANCE_SCORE_FLOOR;

    /** The value the second working directory's file wants, which its own profile states. */
    private static final ProfileKey THE_SECONDS = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** The value the second working directory's profile states for {@link #THE_SECONDS}, as it states it. */
    private static final String THE_SECONDS_VALUE = "0.4";

    /** The arrangement each of the two working directories' files names to approve. */
    private static final String THE_FIRSTS_ARRANGEMENT = "first-arrangement";
    private static final String THE_SECONDS_ARRANGEMENT = "second-arrangement";

    /** The Profile section's title with no value wanted, and with one. */
    private static final String PROFILE = "Profile";

    /** What the row of the approval reads where no row of it is drawn. */
    private static final String NO_ROW = "no row";

    @Test
    @Story("The values Vespera's next start wants belong to the working directory shown")
    @DisplayName("The window opens showing the values the working directory's last command said the next start wants, with no command started")
    void theWindowOpensOnTheValuesItsWorkingDirectoryWants(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        wants(working, THE_SECONDS);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        state.hold(launchValues(folder, working));

        List<String> drawn;
        try (WindowForTests window = WindowForTests.shown(state)) {
            drawn = window.read(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn);
        }

        claim("as the window opens, before any command is started, it draws one row, for the one value the working"
                        + " directory's file wants",
                () -> assertThat(drawn).containsExactly(THE_SECONDS.key()));
    }

    @Test
    @Story("The values Vespera's next start wants belong to the working directory shown")
    @DisplayName("Choosing another working directory shows the values its own file wants, filled from its own profile, none where it has no file or one that cannot be read, and never the first one's")
    void aChangeOfWorkingDirectoryDrawsTheNewOnesValuesAndNoneOfTheEarliers(@TempDir Path folder) throws Exception {
        Path first = Files.createDirectories(folder.resolve("first"));
        Path second = Files.createDirectories(folder.resolve("second"));
        Path withNoFile = Files.createDirectories(folder.resolve("with-no-file"));
        Path withAnUnreadableFile = Files.createDirectories(folder.resolve("with-an-unreadable-file"));
        wants(first, THE_FIRSTS);
        wants(second, THE_SECONDS);
        Files.writeString(second.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(), THE_SECONDS,
                THE_SECONDS_VALUE, "read off the frequency table"), StandardCharsets.UTF_8);
        // an invocation that is none of run, label and none, wanting a key: read as unreadable, never as its key
        Files.writeString(withAnUnreadableFile.resolve(NextInvocation.FILE_NAME),
                "invocation=perhaps\nwants=" + THE_FIRSTS.key() + "\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(second, withNoFile, withAnUnreadableFile), line -> { });
        state.hold(launchValues(folder, first));

        List<String> inTheSecond;
        String theSecondsValue;
        List<String> inTheOneWithNoFile;
        String profileWithNoFile;
        List<String> inTheOneWithAnUnreadableFile;
        List<String> inTheFirstAgain;
        try (WindowForTests window = WindowForTests.shown(state)) {
            choose(window, second);
            inTheSecond = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List.of(THE_SECONDS.key())::equals);
            theSecondsValue = window.read(root -> HeadlessToolkit.nodeWithId(root, "wanted-" + THE_SECONDS.key())
                    .map(spinner -> ((Spinner<?>) spinner).getEditor().getText()).orElse(NO_ROW));
            choose(window, withNoFile);
            inTheOneWithNoFile = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List::isEmpty);
            profileWithNoFile = window.awaiting(root -> WindowForTests.section(root, "profile").getText(),
                    PROFILE::equals);
            choose(window, second);
            window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List.of(THE_SECONDS.key())::equals);
            choose(window, withAnUnreadableFile);
            inTheOneWithAnUnreadableFile = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List::isEmpty);
            choose(window, first);
            inTheFirstAgain = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List.of(THE_FIRSTS.key())::equals);
        }

        claim("the second working directory chosen, the one row drawn is for the value its own file wants, and none"
                        + " for the value the first one's wants",
                () -> assertThat(inTheSecond).containsExactly(THE_SECONDS.key()));
        claim("that row holds the value the second working directory's own profile states",
                () -> assertThat(theSecondsValue).isEqualTo(THE_SECONDS_VALUE));
        claim("a working directory with no such file chosen, no row is drawn",
                () -> assertThat(inTheOneWithNoFile).isEmpty());
        claim("and the Profile section no longer says a value is needed",
                () -> assertThat(profileWithNoFile).isEqualTo(PROFILE));
        claim("a working directory whose file cannot be read chosen, no row is drawn, not even for the value that file"
                        + " names",
                () -> assertThat(inTheOneWithAnUnreadableFile).isEmpty());
        claim("the first working directory chosen again, the one row drawn is again for the value its own file wants",
                () -> assertThat(inTheFirstAgain).containsExactly(THE_FIRSTS.key()));
    }

    @Test
    @Story("The values Vespera's next start wants belong to the working directory shown")
    @DisplayName("Choosing another working directory offers, for its approval, the arrangement its own file names and not the first one's")
    void theArrangementOfferedIsTheWorkingDirectorysOwn(@TempDir Path folder) throws Exception {
        Path first = Files.createDirectories(folder.resolve("first"));
        Path second = Files.createDirectories(folder.resolve("second"));
        approves(first, THE_FIRSTS_ARRANGEMENT);
        approves(second, THE_SECONDS_ARRANGEMENT);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(second), line -> { });
        state.hold(launchValues(folder, first));

        String offered;
        try (WindowForTests window = WindowForTests.shown(state)) {
            choose(window, second);
            offered = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::approvalOffered,
                    THE_SECONDS_ARRANGEMENT::equals);
        }

        claim("the second working directory chosen, its approval's row offers the arrangement the second one's file"
                        + " names",
                () -> assertThat(offered).isEqualTo(THE_SECONDS_ARRANGEMENT));
    }

    @Test
    @Story("The values Vespera's next start wants belong to the working directory shown")
    @DisplayName("When the window's own command ends, however it ended, the window shows what the working directory's file then wants")
    void theWindowsOwnChildEndingAnyWayReadsTheFile(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        state.hold(launchValues(folder, working));

        List<String> afterItWasStopped;
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the sleeping child is started").isEmpty();
            // no invocation of the jar writes the file and is then stopped: the file stands for whatever the working
            // directory holds as the child ends, which is what the window reads
            wants(working, THE_SECONDS);
            state.stop();
            assumeThat(state.ending().map(Ending::howItEnded)).as("the child ended as stopped")
                    .contains(HowItEnded.STOPPED);
            afterItWasStopped = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List.of(THE_SECONDS.key())::equals);
        }

        claim("the window having opened on no file, once its child is stopped, ending with no exit code of 0, the one"
                        + " row drawn is for the value the file then holds",
                () -> assertThat(afterItWasStopped).containsExactly(THE_SECONDS.key()));
    }

    @Test
    @Story("The values Vespera's next start wants belong to the working directory shown")
    @DisplayName("When another process lets go of the working directory shown, the window shows what its file then wants")
    void theEndOfAnotherProcesssHoldReadsTheFile(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        state.hold(launchValues(folder, working));

        boolean heldSeen;
        List<String> afterTheHold;
        Process holder = ChildForTests.builder("sleep").start();
        try (WindowForTests window = WindowForTests.shown(state)) {
            Instant lockStarted = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Files.writeString(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), "pid=" + holder.pid()
                    + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(OffsetDateTime.ofInstant(lockStarted, ZoneOffset.UTC))
                    + " command=run\n", StandardCharsets.UTF_8);
            heldSeen = window.awaiting(root -> WindowForTests.node(root, "run", Button.class).isDisabled(),
                    Boolean::booleanValue);
            // the holder writes the file before it ends, as an invocation does with its closing line
            wants(working, THE_SECONDS);
            holder.destroyForcibly().waitFor();
            afterTheHold = window.awaiting(TheValuesWantedFollowTheWorkingDirectoryTest::keysDrawn,
                    List.of(THE_SECONDS.key())::equals);
        } finally {
            holder.destroyForcibly();
        }
        assumeThat(heldSeen).as("the window saw the working directory held by the other process").isTrue();

        claim("the window having opened on no file, once the other process has let go, the one row drawn is for the"
                        + " value the file then holds",
                () -> assertThat(afterTheHold).containsExactly(THE_SECONDS.key()));
    }

    // ---- the files

    /** Writes {@code folder}'s file as a {@code vespera run} wanting {@code key} alone writes it. */
    private static void wants(Path folder, ProfileKey key) throws Exception {
        Files.writeString(folder.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=" + key.key() + "\nanswer=false\n", StandardCharsets.UTF_8);
    }

    /** Writes {@code folder}'s file as a {@code vespera run} wanting {@code arrangement} approved writes it. */
    private static void approves(Path folder, String arrangement) throws Exception {
        Files.writeString(folder.resolve(NextInvocation.FILE_NAME), "invocation=run\nwants="
                + ProfileKey.ARRANGEMENT_APPROVED.key() + "\nanswer=false\narrangement=" + arrangement + "\n",
                StandardCharsets.UTF_8);
    }

    // ---- the window

    /** The keys of the rows drawn for the values wanted, in their order. */
    private static List<String> keysDrawn(Parent root) {
        return WindowForTests.every(root, "wanted", Node.class).stream().map(Node::getId)
                .filter(id -> id != null && id.startsWith(ROW)).map(id -> id.substring(ROW.length())).toList();
    }

    /** What the approval's row offers, or {@link #NO_ROW} where none is drawn. */
    private static String approvalOffered(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "wanted-" + ProfileKey.ARRANGEMENT_APPROVED.key())
                .map(box -> ((ComboBox<?>) box).getEditor().getText()).orElse(NO_ROW);
    }

    /** Chooses {@code folder}'s row in the list of working directories, as the operator does. */
    @SuppressWarnings("unchecked")
    private static void choose(WindowForTests window, Path folder) throws Exception {
        window.act(root -> {
            ListView<Path> list = WindowForTests.node(root, "working-directories", ListView.class);
            Path row = list.getItems().stream()
                    .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), folder.toString()))
                    .findFirst().orElseThrow(() -> new AssertionError("the list holds no row for " + folder));
            list.getSelectionModel().select(row);
        });
    }

    // ---- the state

    /** The launch values of a corpus root under {@code folder} and the working directory {@code working}. */
    private static LaunchValues launchValues(Path folder, Path working) throws Exception {
        return new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(), "",
                "", "", "", "", "");
    }

    /**
     * A state as {@link WindowForTests#aState} makes it, its machine's folder {@code <folder>/machine/.vespera} as
     * {@code vespera.working-dir} set on the JVM names it.
     */
    private static ControlPanelState aStateOver(Path folder, ChildRuntime runtime) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, runtime);
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", folder.resolve("machine").resolve(".vespera").toString()), Map.of()));
        return state;
    }

    /** A runtime whose java is not there, for a test that starts no child. */
    private static ChildRuntime notStarting(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }
}
