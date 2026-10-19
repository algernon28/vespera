package io.algernon.controlpanel;

import java.util.Optional;

/**
 * How a child ended (ADR-278 §5, ADR-285 §5): the way, its exit code, and the last tail line that is not blank, empty
 * when the child printed none.
 */
public record Ending(HowItEnded howItEnded, int exitCode, Optional<String> lastLine) {
}
