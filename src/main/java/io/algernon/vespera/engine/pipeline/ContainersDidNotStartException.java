package io.algernon.vespera.engine.pipeline;

import java.util.List;

/** {@code docker compose up} exited non-zero (ADR-323 §8). */
final class ContainersDidNotStartException extends ContainerStartRefusedException {

    ContainersDidNotStartException(List<String> up, ProcessOutcome outcome) {
        super("docker compose could not start the containers: " + asRun(up) + " exited " + outcome.exitCode() + ": "
                + outcome.lastLine() + ". Fix what that names and run the same command again.");
    }
}
