package io.algernon.vespera.pipeline;

import java.time.Duration;

/** {@code up} succeeded and a sidecar still did not answer within the bound (ADR-322 §8). */
final class SidecarsDidNotAnswerException extends SidecarStartRefusedException {

    /** {@code which} is {@code docling-serve}, {@code Ollama}, or {@code docling-serve and Ollama}. */
    SidecarsDidNotAnswerException(String which, Duration waited) {
        super("the sidecars were started but " + which + " did not answer within " + waited.toSeconds()
                + " seconds; docker compose -p vespera logs says why. Run the same command again once they answer.");
    }
}
