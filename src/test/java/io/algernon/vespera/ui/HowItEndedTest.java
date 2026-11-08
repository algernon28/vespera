package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * How the control panel shows that its child ended (ADR-278 §5, landed by ADR-285 §5): its own Stop first, whatever
 * the exit code, then the exit code.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class HowItEndedTest {

    /** The command ended. */
    private static final int ENDED = 0;

    /** The command failed, or was refused. */
    private static final int FAILED_OR_REFUSED = 1;

    /** The jar refused the command line it was given. */
    private static final int COMMAND_LINE_REFUSED = 2;

    /** What a process killed by SIGKILL exits with on Linux, where a kill on Windows exits 1. */
    private static final int KILLED_ON_LINUX = 137;

    /** Codes no row of the table names: a code above 2, a kill's on Linux, the smallest and largest int, and a negative one. */
    private static final int[] OTHER_CODES = {3, KILLED_ON_LINUX, Integer.MIN_VALUE, -1, Integer.MAX_VALUE};

    @Test
    @Story("The control panel says how its child ended")
    @DisplayName("A child's ending is shown as one of four: ended, failed or refused, a defect of the control panel, or stopped")
    void aChildsEndingIsShownAsOneOfFour() {
        claim("every exit code is shown as one of four: ended, failed or refused, a defect of the control panel, or"
                        + " stopped",
                () -> assertThat(HowItEnded.values()).containsExactlyInAnyOrder(
                        HowItEnded.ENDED,
                        HowItEnded.FAILED_OR_REFUSED,
                        HowItEnded.DEFECT_OF_THE_CONTROL_PANEL,
                        HowItEnded.STOPPED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endings")
    @Story("The control panel says how its child ended")
    @DisplayName("Exit 0 is ended, 1 failed or refused, 2 a defect of the control panel, any other code failed or refused, and its own Stop is stopped whatever the code")
    void howItEndedFollowsTheExitCodeAndTheControlPanelsOwnStop(
            boolean stopped, int exitCode, HowItEnded expected) {
        claim("a child " + (stopped ? "stopped by the control panel's own Stop" : "the control panel did not stop")
                        + " that exits " + exitCode + " is shown as " + expected,
                () -> assertThat(HowItEnded.of(stopped, exitCode)).isEqualTo(expected));
    }

    /**
     * Each exit code of the table and each other code, once for a child the control panel did not stop and once for
     * one its own Stop ended: not stopped, 0 is ended, 1 failed or refused, 2 a defect of the control panel and any
     * other code failed or refused; stopped, every code is stopped.
     */
    static Stream<Arguments> endings() {
        Stream<Arguments> notStopped = Stream.of(
                Arguments.of(Named.of("exit 0, not stopped, is ended", false), ENDED, HowItEnded.ENDED),
                Arguments.of(Named.of("exit 1, not stopped, is failed or refused", false), FAILED_OR_REFUSED,
                        HowItEnded.FAILED_OR_REFUSED),
                Arguments.of(Named.of("exit 2, not stopped, is a defect of the control panel, whose command line the"
                        + " jar refused", false), COMMAND_LINE_REFUSED, HowItEnded.DEFECT_OF_THE_CONTROL_PANEL));
        Stream<Arguments> otherNotStopped = IntStream.of(OTHER_CODES).mapToObj(code -> Arguments.of(
                Named.of("exit " + code + ", not stopped, is failed or refused", false), code,
                HowItEnded.FAILED_OR_REFUSED));
        Stream<Arguments> stopped = IntStream.concat(
                        IntStream.of(ENDED, FAILED_OR_REFUSED, COMMAND_LINE_REFUSED), IntStream.of(OTHER_CODES))
                .mapToObj(code -> Arguments.of(
                        Named.of("exit " + code + ", stopped by the control panel's own Stop, is stopped", true),
                        code, HowItEnded.STOPPED));
        return Stream.of(notStopped, otherNotStopped, stopped).flatMap(cases -> cases);
    }
}
