package io.algernon.vespera.pipeline;

import java.util.List;

/** {@code docker compose up} exited non-zero (ADR-322 §8). */
final class SidecarsDidNotStartException extends SidecarStartRefusedException {

    SidecarsDidNotStartException(List<String> up, ProcessOutcome outcome) {
        super("docker compose could not start the sidecars: " + asRun(up) + " exited " + outcome.exitCode() + ": "
                + outcome.lastLine() + ". Fix what that names and run the same command again.");
    }
}
