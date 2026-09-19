package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.WalkId;

/**
 * No run of the stage a stage names as its upstream is recorded over this walk (ADR-099).
 *
 * <p>The fault the {@code run_upstream} foreign key would catch at insert, arriving earlier and saying
 * which stage is missing rather than which key failed. It stops the run because there is nothing to
 * look up: the stage that should have run first has never run against this walk.
 */
class NoUpstreamRunException extends RuntimeException {

    NoUpstreamRunException(String stage, WalkId walkId) {
        super("no run of stage \"%s\" is recorded over walk %d, so it cannot be named as an upstream run;"
                .formatted(stage, walkId.value())
                + " that stage must run first.");
    }
}
