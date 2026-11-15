package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One Vespera runs on a machine at a time (ADR-391): the machine lock is an operating-system lock on one byte of {@code
 * machine.lock}, in the folder the corpus-root locks lie in, whose holder line ends in the holder's kind; a second take
 * is refused in one line, but for a start whose parent process holds it as the control panel, which is let through and
 * holds {@code machine-child.lock} of its own.
 *
 * <p>Real files in a temporary folder, which every test names through the constructor, so the user's own folder is
 * never touched; and for the cases of two processes a real second one, {@link HoldsTheMachine}, since a lock taken
 * twice inside one JVM fails differently from one another process holds. The parent a take is let through by is given
 * through the constructor where the second process stands for it, and is the system's own where {@link
 * HoldsTheMachine} starts {@link TakesTheMachine} as its child. The lock keeps what it holds for the life of the JVM,
 * so each test hands it back through {@link MachineLock#release()}, and kills every process it started.
 */
@Epic("Architecture")
@Feature("One Vespera on a machine")
@Issue("818")
@Link(name = "ADR-391", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
class MachineLockTest {

    /** The arguments the invocation in these tests was started with, a line break among them, as an operator may type. */
    private static final String[] ARGUMENTS = {"run", "a corpus\nroot"};

    /** The same arguments as the holder line records them: joined by single spaces, the break written as one. */
    private static final String RECORDED = "run a corpus root";

    /** ADR-177's three fields, then the kind, last. */
    private static final Pattern HOLDER_LINE = Pattern.compile("pid=(\\d+) started=(\\S+) command=(.*) kind=(\\S+)");

    /** What a refused command is told to do, and a refused control panel. */
    private static final String THEN_THE_COMMAND = "run the same command again";

    private static final String THEN_THE_CONTROL_PANEL = "open the control panel again";

    /** How long a process this test killed gets to end. */
    private static final long END_BOUND_SECONDS = 30;

    private final List<ProcessHandle> started = new ArrayList<>();

    @BeforeEach
    void startWithNothingHeld() {
        MachineLock.release();
    }

    @AfterEach
    void handBackTheLockAndEndEveryProcess() throws Exception {
        MachineLock.release();
        for (ProcessHandle process : started) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.onExit().get(END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("Taking the machine creates the folder and records who holds it, since when, with what command and as which kind")
    void takingTheMachineRecordsTheHolderAndItsKind(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("not-yet").resolve("root-locks");

        new MachineLock(folder).take(MachineLock.Kind.RUN, ARGUMENTS);

        claim("the folder, not there before, holds the machine lock's file and at most the file of a control panel's"
                        + " child, so that it is neither a working directory's nor a corpus root's",
                () -> assertThat(names(folder)).contains(MachineLock.FILE_NAME)
                        .isSubsetOf(MachineLock.FILE_NAME, MachineLock.CHILD_FILE_NAME));
        List<String> lines = Files.readAllLines(folder.resolve(MachineLock.FILE_NAME), StandardCharsets.UTF_8);
        Matcher holder = HOLDER_LINE.matcher(lines.getFirst());
        claim("the file holds one line of the holder's pid, its start, its command and its kind, in that order",
                () -> assertThat(List.of(lines.size(), holder.matches())).containsExactly(1, true));
        claim("the pid is this process's, the arguments are joined by spaces with the line break written as one, and the"
                        + " kind is run",
                () -> assertThat(List.of(holder.group(1), holder.group(3), holder.group(4))).containsExactly(
                        String.valueOf(ProcessHandle.current().pid()), RECORDED, "run"));
        claim("the start is a date and time with its offset, to the second and no finer",
                () -> assertThat(OffsetDateTime.parse(holder.group(2)).getNano()).isZero());
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A second take in the same process is refused in one line quoting the holder, telling a command and the control panel what to do next")
    void aSecondTakeInThisProcessIsRefusedInTheLineOfItsKind(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("root-locks");
        new MachineLock(folder).take(MachineLock.Kind.RUN, ARGUMENTS);
        String line = holderLine(folder.resolve(MachineLock.FILE_NAME));

        Throwable command = catchThrowable(() -> new MachineLock(folder).take(MachineLock.Kind.LABEL, ARGUMENTS));
        Throwable controlPanel = catchThrowable(
                () -> new MachineLock(folder).take(MachineLock.Kind.CONTROL_PANEL, new String[0]));

        claim("a command is refused with the line quoting the holder whole and telling it to run the same command again",
                () -> assertThat(List.of(command.getClass(), command.getMessage())).containsExactly(
                        AnotherVesperaRunsException.class, refusal(line, THEN_THE_COMMAND)));
        claim("a control panel is refused with the same line, telling it to open the control panel again",
                () -> assertThat(controlPanel.getMessage()).isEqualTo(refusal(line, THEN_THE_CONTROL_PANEL)));
        claim("and the holder line is left as it was",
                () -> assertThat(holderLine(folder.resolve(MachineLock.FILE_NAME))).isEqualTo(line));

        MachineLock.release();
        Path blank = temporary.resolve("blank-locks");
        Files.createDirectories(blank);
        try (FileChannel held = FileChannel.open(blank.resolve(MachineLock.FILE_NAME), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                FileLock ignored = held.lock(Long.MAX_VALUE - 1, 1, false)) {
            Throwable unread = catchThrowable(() -> new MachineLock(blank).take(MachineLock.Kind.PUBLISH, ARGUMENTS));
            claim("a holder whose line is still blank is said as its details could not be read",
                    () -> assertThat(unread.getMessage())
                            .isEqualTo(refusal("its details could not be read", THEN_THE_COMMAND)));
        }
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A machine another process holds refuses a control panel, a command, and a start whose parent is not the holder")
    void aMachineAnotherProcessHoldsIsRefusedWhateverTheKinds(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("root-locks");
        HoldsTheMachine.Holder run = hold(folder, MachineLock.Kind.RUN, false);
        String runsLine = holderLine(folder.resolve(MachineLock.FILE_NAME));

        Throwable controlPanel = catchThrowable(
                () -> new MachineLock(folder).take(MachineLock.Kind.CONTROL_PANEL, new String[0]));
        claim("a vespera run in another process refuses a control panel, quoting that process's line, its pid and its"
                        + " kind, readable while it is held",
                () -> {
                    assertThat(controlPanel.getMessage()).isEqualTo(refusal(runsLine, THEN_THE_CONTROL_PANEL));
                    assertThat(runsLine).matches(holderLine(run.process().pid(), "run"));
                });

        run.process().destroyForcibly().waitFor(END_BOUND_SECONDS, TimeUnit.SECONDS);
        HoldsTheMachine.Holder panel = hold(folder, MachineLock.Kind.CONTROL_PANEL, false);
        String panelsLine = holderLine(folder.resolve(MachineLock.FILE_NAME));
        long notTheHolder = ProcessHandle.current().pid();

        Throwable command = catchThrowable(() -> new MachineLock(folder, OptionalLong.of(notTheHolder))
                .take(MachineLock.Kind.PUBLISH, ARGUMENTS));
        claim("a control panel in another process refuses a command whose parent is another process, quoting the"
                        + " control panel's line",
                () -> {
                    assertThat(command.getMessage()).isEqualTo(refusal(panelsLine, THEN_THE_COMMAND));
                    assertThat(panelsLine).matches(holderLine(panel.process().pid(), "control-panel"));
                });
    }

    @Test
    @Story("The control panel's own invocations are let through")
    @DisplayName("A start whose parent holds the machine as the control panel is let through and holds a file of its own, and so is a real child of such a holder")
    void theControlPanelsOwnChildIsLetThroughAndHoldsTheChildsFile(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("root-locks");
        HoldsTheMachine.Holder panel = hold(folder, MachineLock.Kind.CONTROL_PANEL, false);
        String panelsLine = holderLine(folder.resolve(MachineLock.FILE_NAME));

        Throwable letThrough = catchThrowable(() -> new MachineLock(folder, OptionalLong.of(panel.process().pid()))
                .take(MachineLock.Kind.RUN, ARGUMENTS));

        claim("a start whose parent is the control panel holding the machine is not refused",
                () -> assertThat(letThrough).isNull());
        Matcher child = HOLDER_LINE.matcher(holderLine(folder.resolve(MachineLock.CHILD_FILE_NAME)));
        claim("it wrote its own line in the child's file: this pid, its command and its kind",
                () -> assertThat(List.of(child.matches(), child.group(1), child.group(3), child.group(4))).containsExactly(
                        true, String.valueOf(ProcessHandle.current().pid()), RECORDED, "run"));
        claim("and left the control panel's line in the machine lock's file as it was",
                () -> assertThat(holderLine(folder.resolve(MachineLock.FILE_NAME))).isEqualTo(panelsLine));

        MachineLock.release();
        Path another = temporary.resolve("another-folder-of-locks");
        HoldsTheMachine.Holder withItsChild = hold(another, MachineLock.Kind.CONTROL_PANEL, true);
        claim("a child the control panel's process starts itself, its parent read from the system, is let through",
                () -> assertThat(withItsChild.childSaid()).hasValueSatisfying(
                        said -> assertThat(said).startsWith(TakesTheMachine.LET_THROUGH)));
    }

    @Test
    @Story("The control panel's own invocations are let through")
    @DisplayName("A start whose parent holds the machine as a command, and not as the control panel, is refused")
    void aChildOfAnInvocationThatIsNoControlPanelIsRefused(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("root-locks");
        HoldsTheMachine.Holder run = hold(folder, MachineLock.Kind.RUN, false);
        String runsLine = holderLine(folder.resolve(MachineLock.FILE_NAME));

        Throwable refused = catchThrowable(() -> new MachineLock(folder, OptionalLong.of(run.process().pid()))
                .take(MachineLock.Kind.LABEL, ARGUMENTS));

        claim("the start is refused with the line quoting the holder, though the holder is its parent",
                () -> assertThat(refused.getMessage()).isEqualTo(refusal(runsLine, THEN_THE_COMMAND)));
    }

    @Test
    @Story("The control panel's own invocations are let through")
    @DisplayName("A child left running by a control panel forced to end keeps the machine until it ends")
    void aChildLeftRunningByAControlPanelForcedToEndHoldsTheMachine(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("root-locks");
        HoldsTheMachine.Holder panel = hold(folder, MachineLock.Kind.CONTROL_PANEL, true);
        String said = panel.childSaid().orElseThrow();
        long childPid = Long.parseLong(said.substring(TakesTheMachine.LET_THROUGH.length()).strip());
        ProcessHandle child = ProcessHandle.of(childPid).orElseThrow();
        started.add(child);

        panel.process().destroyForcibly().waitFor(END_BOUND_SECONDS, TimeUnit.SECONDS);
        String childsLine = holderLine(folder.resolve(MachineLock.CHILD_FILE_NAME));
        Throwable refused = catchThrowable(() -> new MachineLock(folder).take(MachineLock.Kind.RUN, ARGUMENTS));

        claim("with the control panel killed and its child still running, a command is refused quoting the child's line,"
                        + " its pid and its kind",
                () -> {
                    assertThat(child.isAlive()).isTrue();
                    assertThat(refused.getMessage()).isEqualTo(refusal(childsLine, THEN_THE_COMMAND));
                    assertThat(childsLine).matches(holderLine(childPid, "run"));
                });

        child.destroyForcibly();
        child.onExit().get(END_BOUND_SECONDS, TimeUnit.SECONDS);
        Throwable taken = catchThrowable(() -> new MachineLock(folder).take(MachineLock.Kind.RUN, ARGUMENTS));
        claim("once the child has ended too, the machine is taken", () -> assertThat(taken).isNull());
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("After its holder is killed, the machine is taken at once and the holder line rewritten")
    void aKilledHolderLeavesTheMachineFree(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("root-locks");
        HoldsTheMachine.Holder run = hold(folder, MachineLock.Kind.RUN, false);
        run.process().destroyForcibly().waitFor(END_BOUND_SECONDS, TimeUnit.SECONDS);

        new MachineLock(folder).take(MachineLock.Kind.CONTROL_PANEL, new String[0]);

        Matcher holder = HOLDER_LINE.matcher(holderLine(folder.resolve(MachineLock.FILE_NAME)));
        claim("the file the killed holder left is taken, and its line now names this process as the control panel",
                () -> assertThat(List.of(holder.matches(), holder.group(1), holder.group(4))).containsExactly(
                        true, String.valueOf(ProcessHandle.current().pid()), "control-panel"));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A folder that cannot be made refuses the start in one line naming the folder and the variable that moves it")
    void aFolderThatCannotBeMadeRefusesTheStart(@TempDir Path temporary) throws IOException {
        Path notAFolder = Files.createFile(temporary.resolve("a-file-where-the-folder-would-be"));
        String reason = whyItCannotBeMade(notAFolder);

        Throwable command = catchThrowable(() -> new MachineLock(notAFolder).take(MachineLock.Kind.RUN, ARGUMENTS));
        Throwable controlPanel = catchThrowable(
                () -> new MachineLock(notAFolder).take(MachineLock.Kind.CONTROL_PANEL, new String[0]));

        claim("a command is refused in the line naming the folder, the system's reason and the variable",
                () -> assertThat(List.of(command.getClass(), command.getMessage())).containsExactly(
                        MachineLockUnavailableException.class, unavailable(notAFolder, reason, THEN_THE_COMMAND)));
        claim("and a control panel in the same line, telling it to open the control panel again",
                () -> assertThat(controlPanel.getMessage())
                        .isEqualTo(unavailable(notAFolder, reason, THEN_THE_CONTROL_PANEL)));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("Only run, label and publish named first take the machine as their kind; serve, help, version and no command take nothing")
    void onlyRunLabelAndPublishNameAKind() {
        claim("run, label and publish named first name their kinds, whatever follows them",
                () -> assertThat(Stream.of(
                                new String[] {"run", "a-root"},
                                new String[] {"label", "--auto"},
                                new String[] {"publish", "--to", "website"})
                        .map(MachineLock.Kind::ofTheCommand).toList())
                        .containsExactly(Optional.of(MachineLock.Kind.RUN), Optional.of(MachineLock.Kind.LABEL),
                                Optional.of(MachineLock.Kind.PUBLISH)));
        claim("serve, --help, --version, no argument, a name that only begins with run, and a command named after an"
                        + " option name none",
                () -> assertThat(Stream.of(
                                new String[] {"serve"},
                                new String[] {"--help"},
                                new String[] {"--version"},
                                new String[0],
                                new String[] {"runs"},
                                new String[] {"--db-dir=a-folder", "run"})
                        .map(MachineLock.Kind::ofTheCommand).toList())
                        .containsOnly(Optional.empty()));
        claim("and the words the holder line writes for the four kinds are control-panel, run, label and publish",
                () -> assertThat(Stream.of(MachineLock.Kind.values()).map(MachineLock.Kind::word).toList())
                        .containsExactly("control-panel", "run", "label", "publish"));
    }

    private HoldsTheMachine.Holder hold(Path folder, MachineLock.Kind kind, boolean withItsChild) throws Exception {
        HoldsTheMachine.Holder holder = HoldsTheMachine.start(folder, kind, withItsChild);
        started.add(holder.process().toHandle());
        return holder;
    }

    private static String refusal(String holder, String then) {
        return "another Vespera is running on this machine (" + holder + "); wait for it to end, or stop it or close its"
                + " control panel, then " + then + ". One Vespera runs on a machine at a time, so that none stops the"
                + " sidecars another is using.";
    }

    private static String unavailable(Path folder, String reason, String then) {
        return "the machine lock could not be taken in the folder " + folder.toAbsolutePath() + " (" + reason + ");"
                + " make that folder one this user can write, or name another in VESPERA_CORPUS_ROOT_LOCK_FOLDER, then "
                + then + ". Vespera does not start while it cannot tell whether another Vespera is running.";
    }

    /** What the system says making {@code folder} fails with, in its own words, as the line quotes it. */
    private static String whyItCannotBeMade(Path folder) {
        try {
            Files.createDirectories(folder);
            throw new IllegalStateException(folder + " was made");
        } catch (IOException refused) {
            return refused.getMessage();
        }
    }

    /** The pattern of a holder line naming {@code pid} and {@code kind}, its start and command matched as any. */
    private static String holderLine(long pid, String kind) {
        return "pid=" + pid + " started=\\S+ command=.* kind=" + Pattern.quote(kind);
    }

    private static String holderLine(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> each = Files.list(folder)) {
            return each.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
