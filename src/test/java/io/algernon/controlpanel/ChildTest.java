package io.algernon.controlpanel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's own child, started in place of the jar by {@link ChildForTests} (ADR-278 §3, §5 and §6, landed
 * by ADR-285 §5): its output merged and drained to the end by one reader into a tail kept in memory, its Stop killing
 * its descendants before it, and how it ended.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class ChildTest {

    /** Long enough for a JVM to start and end on a loaded CI runner; every wait here is bounded by it. */
    private static final Duration BOUND = Duration.ofSeconds(60);

    /** How often a wait for a line looks at the tail again. */
    private static final long POLL_MILLIS = 50;

    /** One mebibyte: more than the pipe buffer of Linux (64 KiB) or of Windows (4 KiB to 64 KiB) many times over. */
    private static final String MORE_THAN_A_PIPE_BUFFER = Integer.toString(1 << 20);

    /** How many lines the tail keeps. */
    private static final int TAIL_LINES = 200;

    /** Fifty more than the tail keeps, so that fifty are dropped from its head. */
    private static final int MORE_LINES_THAN_THE_TAIL = 250;

    /** Lines written alternately to the two streams. */
    private static final int INTERLEAVED_LINES = 50;

    private static final String LAST_LINE = "the last line that is not blank";

    private static final String STOP_CONFIRMATION =
            "Stopping loses the work done since the running step's last commit, and the next vespera run continues"
                    + " from there.";

    private static final String CONTROL_PANEL = "io.algernon.controlpanel";

    @Test
    @Story("The control panel reads its child's output to the end")
    @DisplayName("A child writing a mebibyte to standard error and nothing to standard output ends")
    void aChildWritingMoreThanAPipeBufferToStandardErrorAloneEnds() throws Exception {
        Child child = Child.start(ChildForTests.builder("stderr", MORE_THAN_A_PIPE_BUFFER));
        Optional<Ending> ending = endingOf(child);

        claim("the child ended within the bound, so the stream nobody else reads did not stall it",
                () -> assertThat(ending).isPresent());
        claim("and it ended with exit 0", () -> assertThat(ending.orElseThrow().exitCode()).isZero());
    }

    @Test
    @Story("The control panel reads its child's output to the end")
    @DisplayName("Standard error is merged into standard output, in the order the child wrote them")
    void standardErrorIsMergedIntoOneStreamInTheOrderWritten() throws Exception {
        ProcessBuilder builder = ChildForTests.builder("interleave", Integer.toString(INTERLEAVED_LINES));
        Child child = Child.start(builder);
        endingOf(child).orElseThrow();
        List<String> written = IntStream.rangeClosed(1, INTERLEAVED_LINES)
                .mapToObj(i -> (i % 2 == 1 ? "out " : "err ") + i)
                .toList();

        claim("the control panel asked for standard error to be merged into standard output",
                () -> assertThat(builder.redirectErrorStream()).isTrue());
        claim("and the tail holds the fifty lines, odd ones from standard output and even ones from standard error,"
                        + " in the order they were written",
                () -> assertThat(child.tail()).containsExactlyElementsOf(written));
    }

    @Test
    @Story("The control panel reads its child's output to the end")
    @DisplayName("The tail keeps the last two hundred lines of two hundred and fifty")
    void theTailKeepsTheLastTwoHundredLines() throws Exception {
        Child child = Child.start(ChildForTests.builder("lines", Integer.toString(MORE_LINES_THAN_THE_TAIL)));
        endingOf(child).orElseThrow();
        List<String> lastTwoHundred = IntStream.rangeClosed(MORE_LINES_THAN_THE_TAIL - TAIL_LINES + 1, MORE_LINES_THAN_THE_TAIL)
                .mapToObj(i -> "line " + i)
                .toList();

        claim("the tail the control panel keeps is two hundred lines long",
                () -> assertThat(Child.TAIL_LINES).isEqualTo(TAIL_LINES));
        claim("and of two hundred and fifty lines it holds lines 51 to 250, in order",
                () -> assertThat(child.tail()).containsExactlyElementsOf(lastTwoHundred));
    }

    @Test
    @Story("The control panel reads its child's output to the end")
    @DisplayName("A line holding characters outside every single-byte code page arrives whole")
    void theOutputIsReadAsUtf8() throws Exception {
        Child child = Child.start(ChildForTests.builder("utf8"));
        endingOf(child).orElseThrow();

        claim("the one line the child printed in UTF-8 is read as UTF-8, every character whole",
                () -> assertThat(child.tail()).containsExactly(ChildForTests.WRITTEN_IN_UTF8));
    }

    @Test
    @Story("The control panel reads its child's output to the end")
    @DisplayName("Reading the child's output writes nothing into the folder the child runs in")
    void drainingWritesNothingIntoTheChildsDirectory(@TempDir Path childsDirectory) throws Exception {
        Child child = Child.start(ChildForTests.builder("lines", Integer.toString(MORE_LINES_THAN_THE_TAIL))
                .directory(childsDirectory.toFile()));
        endingOf(child).orElseThrow();

        claim("the child's lines were read", () -> assertThat(child.tail()).hasSize(TAIL_LINES));
        claim("and the folder the child ran in is as empty as it was, since the tail is kept in memory only",
                () -> {
                    try (Stream<Path> entries = Files.list(childsDirectory)) {
                        assertThat(entries).isEmpty();
                    }
                });
    }

    /**
     * A grandchild survives its parent's kill, and once the parent is dead it is no longer among anyone's
     * descendants, so a Stop that killed the child first would leave it running and this test red.
     */
    @Test
    @Story("The control panel stops only its own child")
    @DisplayName("Stop leaves neither the child nor the process the child started")
    void stopLeavesNeitherTheChildNorItsGrandchild() throws Exception {
        Child child = Child.start(ChildForTests.builder("grandchild"));
        long grandchild = grandchildOf(child);
        try {
            child.stop();

            claim("once Stop returns, the process the child started is gone",
                    () -> assertThat(ProcessHandle.of(grandchild).map(ProcessHandle::isAlive)).isNotEqualTo(Optional.of(true)));
            claim("and so is the child",
                    () -> assertThat(ProcessHandle.of(child.pid()).map(ProcessHandle::isAlive)).isNotEqualTo(Optional.of(true)));
        } finally {
            ProcessHandle.of(grandchild).ifPresent(ProcessHandle::destroyForcibly);
            ProcessHandle.of(child.pid()).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    @Story("The control panel stops only its own child")
    @DisplayName("Only the child the control panel started can be stopped, and only that class ends a process")
    void onlyTheChildItStartedCanBeStopped() throws Exception {
        List<String> factories = Arrays.stream(Child.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() == Child.class)
                .map(method -> method.getName() + Arrays.toString(method.getParameterTypes()))
                .toList();
        JavaClasses controlPanel = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(CONTROL_PANEL);
        DescribedPredicate<JavaMethodCall> aDestroy = DescribedPredicate.describe(
                "a destroy of a process or a process handle",
                call -> (call.getTargetOwner().isAssignableTo(Process.class)
                                || call.getTargetOwner().isAssignableTo(ProcessHandle.class))
                        && call.getName().startsWith("destroy"));
        DescribedPredicate<JavaMethodCall> aHandleFoundByPid = DescribedPredicate.describe(
                "a process handle found by its pid",
                call -> call.getTargetOwner().isEquivalentTo(ProcessHandle.class) && call.getName().equals("of"));

        claim("a child is built by no public constructor",
                () -> assertThat(Child.class.getConstructors()).isEmpty());
        claim("and only by starting a process builder, never from a pid or a holder line",
                () -> assertThat(factories).containsExactly("start[" + ProcessBuilder.class + "]"));
        claim("no class of the control panel but the child destroys a process",
                () -> assertThatCode(() -> noClasses()
                                .that().resideInAPackage(CONTROL_PANEL + "..")
                                .and().doNotHaveFullyQualifiedName(Child.class.getName())
                                .should().callMethodWhere(aDestroy)
                                .check(controlPanel))
                        .doesNotThrowAnyException());
        claim("and the child finds no process by its pid, so what it stops is the process it started and those"
                        + " beneath it",
                () -> assertThatCode(() -> noClasses()
                                .that().haveFullyQualifiedName(Child.class.getName())
                                .should().callMethodWhere(aHandleFoundByPid)
                                .check(controlPanel))
                        .doesNotThrowAnyException());
    }

    @Test
    @Story("The control panel stops only its own child")
    @DisplayName("Stop is confirmed by one sentence that is true at every step")
    void stopIsConfirmedByOneSentence() {
        claim("the confirmation says the work since the running step's last commit is lost and the next run continues"
                        + " from there, in one sentence",
                () -> assertThat(Child.STOP_CONFIRMATION).isEqualTo(STOP_CONFIRMATION));
    }

    @Test
    @Story("The control panel says how its child ended")
    @DisplayName("A child ended by the control panel's own Stop is shown as stopped, though it exits as a failure does")
    void aStoppedChildIsShownAsStoppedThoughItExitsAsAFailureDoes() throws Exception {
        Child child = Child.start(ChildForTests.builder("sleep"));
        child.stop();
        Ending ending = endingOf(child).orElseThrow();

        claim("the child's exit code is not 0, as a kill's never is",
                () -> assertThat(ending.exitCode()).isNotZero());
        claim("and the control panel shows it as stopped, since it recorded its own Stop",
                () -> assertThat(ending.howItEnded()).isEqualTo(HowItEnded.STOPPED));
    }

    @Test
    @Story("The control panel says how its child ended")
    @DisplayName("Exits 0, 1 and 2 are shown as ended, failed or refused, and a defect of the control panel, each with its last line that is not blank")
    void eachExitCodeIsShownWithTheLastLineThatIsNotBlank() throws Exception {
        List<Ending> expected = List.of(
                new Ending(HowItEnded.ENDED, 0, Optional.of(LAST_LINE)),
                new Ending(HowItEnded.FAILED_OR_REFUSED, 1, Optional.of(LAST_LINE)),
                new Ending(HowItEnded.DEFECT_OF_THE_CONTROL_PANEL, 2, Optional.of(LAST_LINE)));
        List<Ending> shown = new ArrayList<>();
        for (Ending each : expected) {
            Child child = Child.start(ChildForTests.builder("exit", Integer.toString(each.exitCode()), LAST_LINE));
            shown.add(endingOf(child).orElseThrow());
        }

        claim("a child that prints a line, a line of spaces and an empty line and exits 0, 1 or 2 is shown as ended,"
                        + " failed or refused, or a defect of the control panel, with the line before the blank ones",
                () -> assertThat(shown).containsExactlyElementsOf(expected));
    }

    @Test
    @Story("The control panel says how its child ended")
    @DisplayName("A child killed by another program is shown as failed or refused, with the code it exited with")
    void aChildKilledFromOutsideIsShownAsFailedOrRefusedWithItsCode() throws Exception {
        Child child = Child.start(ChildForTests.builder("sleep"));
        ProcessHandle outside = ProcessHandle.of(child.pid()).orElseThrow();
        outside.destroyForcibly();
        Ending ending = endingOf(child).orElseThrow();

        claim("the child killed by something other than the control panel's Stop exits with a code that is not 0",
                () -> assertThat(ending.exitCode()).isNotZero());
        claim("and is shown as failed or refused, not as stopped, with that code",
                () -> assertThat(ending.howItEnded()).isEqualTo(HowItEnded.FAILED_OR_REFUSED));
        claim("with no last line, since the child printed none",
                () -> assertThat(ending.lastLine()).isEmpty());
    }

    private static Optional<Ending> endingOf(Child child) throws InterruptedException {
        return child.waitForEnding(BOUND);
    }

    /** The pid the {@code grandchild} mode prints, read from the tail as it comes. */
    private static long grandchildOf(Child child) throws InterruptedException {
        Instant deadline = Instant.now().plus(BOUND);
        while (Instant.now().isBefore(deadline)) {
            for (String line : child.tail()) {
                if (line.startsWith(ChildForTests.GRANDCHILD)) {
                    return Long.parseLong(line.substring(ChildForTests.GRANDCHILD.length()).strip());
                }
            }
            Thread.sleep(POLL_MILLIS);
        }
        child.stop();
        throw new IllegalStateException("the child named no grandchild within " + BOUND);
    }
}
