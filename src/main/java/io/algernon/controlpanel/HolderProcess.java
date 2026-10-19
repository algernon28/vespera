package io.algernon.controlpanel;

import java.time.Instant;
import java.util.Optional;

/**
 * What the system says of the process a holder line names (ADR-285 §5): whether it is alive and, when the system gives
 * it, when it started.
 */
public record HolderProcess(boolean alive, Optional<Instant> startInstant) {
}
