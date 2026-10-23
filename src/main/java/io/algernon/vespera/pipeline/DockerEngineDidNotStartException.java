package io.algernon.vespera.pipeline;

import java.time.Duration;
import java.util.List;

/** The Docker engine was not running and either no command started it or it did not answer in time (ADR-322 §5, §8). */
final class DockerEngineDidNotStartException extends SidecarStartRefusedException {

    private DockerEngineDidNotStartException(String line) {
        super(line);
    }

    /** Every start command exited non-zero; {@code each} is one {@code <command> exited <code>: <last line>}. */
    static DockerEngineDidNotStartException noCommandStartedIt(List<String> each) {
        return new DockerEngineDidNotStartException("the Docker engine is not running and Vespera could not start it: "
                + String.join("; ", each) + ". Start Docker yourself and run the same command again.");
    }

    /** A start command exited 0 and the engine still did not answer within the bound. */
    static DockerEngineDidNotStartException didNotAnswer(Duration waited) {
        return new DockerEngineDidNotStartException("the Docker engine was started but did not answer within "
                + waited.toSeconds() + " seconds. Wait until Docker has finished starting and run the same command"
                + " again.");
    }
}
