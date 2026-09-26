package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one sequence every stage's mint used to repeat, written once (ADR-157 §2): find the finished
 * walk or refuse, serialise a private {@code ConfigConsumed}, call {@link Ledger#startRun} with a
 * module list, and record the id into {@link InvocationRuns} the moment {@code startRun} returns
 * (ADR-154 §1).
 *
 * <p>Plain, not a bean — built inline the shape {@link UpstreamRuns} already has, from the {@code
 * Ledger} and {@link ImplementationVersions} the caller already holds and the {@link InvocationRuns}
 * the caller's own execution context backs.
 */
final class RunMint {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final Ledger ledger;
    private final ImplementationVersions implementationVersions;
    private final InvocationRuns invocationRuns;

    RunMint(Ledger ledger, ImplementationVersions implementationVersions, InvocationRuns invocationRuns) {
        this.ledger = ledger;
        this.implementationVersions = implementationVersions;
        this.invocationRuns = invocationRuns;
    }

    /**
     * The finished walk of {@code canonicalRoot}.
     *
     * @throws IllegalStateException naming {@code beforeWhat}, if census has not yet finished walking it
     */
    WalkId finishedWalk(Path canonicalRoot, String beforeWhat) {
        return ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException(
                        "no finished walk is recorded for " + canonicalRoot + "; census must run before " + beforeWhat));
    }

    /**
     * Serialises {@code configConsumed} with one default {@link JsonMapper}, the mapper every run class
     * used to build for itself, and delegates to {@link #mint(StageModules, String, WalkId, Optional)}.
     */
    RunId mint(StageModules stage, Record configConsumed, WalkId walk, Optional<RunId> upstream) {
        return mint(stage, JSON_MAPPER.writeValueAsString(configConsumed), walk, upstream);
    }

    /**
     * Mints or continues {@code stage}'s run (ADR-115), and records the id into {@link InvocationRuns}
     * before returning it — ADR-154 §1's "at the moment {@code startRun} returns".
     */
    RunId mint(StageModules stage, String configConsumed, WalkId walk, Optional<RunId> upstream) {
        RunId runId = ledger.startRun(
                stage.stage(),
                implementationVersions.of(stage.modules().toArray(new String[0])),
                configConsumed,
                walk,
                upstream.map(List::of).orElseGet(List::of));
        invocationRuns.record(stage.stage(), runId);
        return runId;
    }
}
