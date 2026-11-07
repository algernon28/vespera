package io.algernon.controlpanel;

import java.util.Arrays;

/**
 * The main class of the jar {@code PublishToConfluenceAsksForTheTokenTest} starts as the control panel's child: it
 * prints nothing and exits with a code saying what it was given, so that the token is never written to its output, a
 * file or a line of the window. {@link #AS_EXPECTED} where its environment holds {@link #TOKEN} as {@code
 * VESPERA_CONFLUENCE_TOKEN} and {@code VESPERA_STARTED_BY=control-panel}, and no argument holds the token.
 */
public final class ATokenCheckingChildForTests {

    /** The token the test types, synthetic. */
    public static final String TOKEN = "TOKEN-typed-in-the-window-0f1e2d3c";

    /** Each exit code, by what the child was given. */
    public static final int AS_EXPECTED = 0;

    public static final int NOT_THE_TOKEN_TYPED = 3;

    public static final int THE_TOKEN_IN_AN_ARGUMENT = 4;

    public static final int NOT_TOLD_THE_CONTROL_PANEL_STARTED_IT = 5;

    private ATokenCheckingChildForTests() {
    }

    public static void main(String[] args) {
        if (Arrays.stream(args).anyMatch(argument -> argument.contains(TOKEN))) {
            System.exit(THE_TOKEN_IN_AN_ARGUMENT);
        }
        if (!TOKEN.equals(System.getenv("VESPERA_CONFLUENCE_TOKEN"))) {
            System.exit(NOT_THE_TOKEN_TYPED);
        }
        if (!"control-panel".equals(System.getenv("VESPERA_STARTED_BY"))) {
            System.exit(NOT_TOLD_THE_CONTROL_PANEL_STARTED_IT);
        }
        System.exit(AS_EXPECTED);
    }
}
