package io.algernon.vespera.publish.confluence;

/**
 * The site refused a request a publish could not go on without, or answered one so that the publish could not go on
 * (ADR-313 §3, ADR-316 §4): the publish stops there, and its line says what it had done.
 *
 * <p>Its message names what was being done and what the site answered, never the account or the token.
 */
final class TheSiteRefusedThePublishException extends RuntimeException {

    private final int exitCode;

    TheSiteRefusedThePublishException(String message) {
        this(message, 1);
    }

    /** A stop whose publish exits with {@code exitCode}, 3 or 4 where the control panel reads it (ADR-383 §1). */
    TheSiteRefusedThePublishException(String message, int exitCode) {
        super(message);
        this.exitCode = exitCode;
    }

    int exitCode() {
        return exitCode;
    }
}
