package io.algernon.vespera.confluence;

/**
 * The site refused a request a publish could not go on without, or answered one so that the publish could not go on
 * (ADR-313 §3, ADR-316 §4): the publish stops there, and its line says what it had done.
 *
 * <p>Its message names what was being done and what the site answered, never the account or the token.
 */
final class TheSiteRefusedThePublishException extends RuntimeException {

    TheSiteRefusedThePublishException(String message) {
        super(message);
    }
}
