package io.algernon.controlpanel;

/**
 * How the control panel shows that its child ended (ADR-278 §5, with the other exit codes of ADR-285 §5).
 */
public enum HowItEnded {
    ENDED,
    FAILED_OR_REFUSED,
    DEFECT_OF_THE_CONTROL_PANEL,
    STOPPED;

    /** Stopped by the control panel's own Stop whatever the code; 0 ends, 2 is a defect, every other code fails or refuses. */
    public static HowItEnded of(boolean stoppedByItsOwnStop, int exitCode) {
        if (stoppedByItsOwnStop) {
            return STOPPED;
        }
        return switch (exitCode) {
            case 0 -> ENDED;
            case 2 -> DEFECT_OF_THE_CONTROL_PANEL;
            default -> FAILED_OR_REFUSED;
        };
    }
}
