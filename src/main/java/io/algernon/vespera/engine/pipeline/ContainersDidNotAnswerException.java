package io.algernon.vespera.engine.pipeline;

import java.time.Duration;

/** {@code up} succeeded and a container still did not answer within the bound (ADR-323 §8). */
final class ContainersDidNotAnswerException extends ContainerStartRefusedException {

    /** {@code which} is {@code docling-serve}, {@code Ollama}, or {@code docling-serve and Ollama}. */
    ContainersDidNotAnswerException(String which, Duration waited) {
        super("the containers were started but " + which + " did not answer within " + waited.toSeconds()
                + " seconds; docker compose -p vespera logs says why. Run the same command again once they answer.");
    }
}
