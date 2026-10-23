package io.algernon.controlpanel;

/**
 * The two paths a tab of the control panel asks for beside its pages (ADR-325 §4.2, §4.3), for the tests that play the
 * tab or read what the server answers it.
 */
final class TabPaths {

    /** The path a tab polls, answered with the start's mark. */
    static final String ALIVE = "/alive";

    /** The path of the control panel's one script. */
    static final String SCRIPT = "/control-panel.js";

    private TabPaths() {
    }
}
