package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("Any other exit code of a child the control panel did not stop is shown as failed or refused")
    void anyOtherExitCodeOfAChildNotStoppedIsFailedOrRefused() {
        for (int code : OTHER_CODES) {
            claim("exit " + code + ", not stopped, is failed or refused",
                    () -> assertThat(HowItEnded.of(false, code)).isEqualTo(HowItEnded.FAILED_OR_REFUSED));
            claim("and exit " + code + ", stopped by the control panel's own Stop, is stopped",
                    () -> assertThat(HowItEnded.of(true, code)).isEqualTo(HowItEnded.STOPPED));
        }
    }

    @Test
    @Story("The control panel says how its child ended")
    @DisplayName("Exit 0 is ended, 1 failed or refused, 2 a defect of the control panel, and its own Stop is stopped whatever the code")
    void howItEndedFollowsTheExitCodeAndTheControlPanelsOwnStop() {
        claim("exit 0, not stopped, is ended",
                () -> assertThat(HowItEnded.of(false, ENDED)).isEqualTo(HowItEnded.ENDED));
        claim("exit 1, not stopped, is failed or refused",
                () -> assertThat(HowItEnded.of(false, FAILED_OR_REFUSED)).isEqualTo(HowItEnded.FAILED_OR_REFUSED));
        claim("exit 2, not stopped, is a defect of the control panel, whose command line the jar refused",
                () -> assertThat(HowItEnded.of(false, COMMAND_LINE_REFUSED))
                        .isEqualTo(HowItEnded.DEFECT_OF_THE_CONTROL_PANEL));
        claim("stopped by the control panel's own Stop, exit 1 is stopped, not failed",
                () -> assertThat(HowItEnded.of(true, FAILED_OR_REFUSED)).isEqualTo(HowItEnded.STOPPED));
        claim("and so is a kill's exit code on Linux, 137",
                () -> assertThat(HowItEnded.of(true, KILLED_ON_LINUX)).isEqualTo(HowItEnded.STOPPED));
    }
}
