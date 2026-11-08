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
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    /**
     * As the control panel ends, the child is ended as Stop ends it, its descendants killed first, within a bound
     * (ADR-307 §1): a kill of the child alone would leave the grandchild running and this test red.
     */
    @Test
    @Issue("629")
    @Link(name = "ADR-307", url = Adr.THE_CHILD_ENDS_WITH_THE_CONTROL_PANEL, type = "adr")
    @Story("The control panel ends its own child")
    @DisplayName("The control panel's end leaves neither the child nor the process the child started, and sees both end within its bound")
    void theControlPanelsEndLeavesNeitherTheChildNorItsGrandchild() throws Exception {
        Child child = Child.start(ChildForTests.builder("grandchild"));
        long grandchild = grandchildOf(child);
        try {
            boolean seenToEnd = child.endWithin(ControlPanelState.END_BOUND);

            claim("both are seen to end within the control panel's bound of " + ControlPanelState.END_BOUND.toSeconds()
                    + " seconds", () -> assertThat(seenToEnd).isTrue());
            claim("the process the child started is gone",
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
