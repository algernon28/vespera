package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.OllamaClient;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mints stage 6b's run, once per job execution (ADR-108, ADR-110, ADR-114, #179): {@link
 * ArrangementRun}'s sibling one stage along, and the last run the cascade mints.
 *
 * <p><b>Its upstream is the arrangement the operator approved</b>, never the latest one. Naming the
 * approved arrangement is what makes the approval mean something downstream: a re-arrangement mints a
 * new 6a run, closes the gate, and — once re-approved — changes this run's id and therefore the tree
 * it writes (ADR-103), by construction rather than by a rule someone has to remember.
 *
 * <p><b>The generator identity is what was asked and what answered, never where it was served</b>
 * (ADR-090, ADR-091): the model's name, the digest of the weights actually serving under that name,
 * and — once anything sends them — the options actually sent. The digest is read here because {@code
 * synthesis} may not reach the serving engine, so {@code pipeline} reads it and hands it down as a
 * plain string (ADR-110).
 *
 * <p><b>No run row exists while the gate is shut</b> (ADR-080's rule, applied again), and it is
 * {@code @JobScope} that gets that: the injected reference is a scoped proxy, and the constructor
 * below — the thing that mints the row — runs on the first call to {@link #runId()}, which the
 * tasklet makes only past the gate. The {@code ObjectProvider} every caller reaches it through buys
 * nothing on top of that today; it is there so that this bean losing {@code @JobScope} and becoming
 * an eager singleton cannot silently start minting a row per invocation.
 *
 * <p>{@code @JobScope} rather than {@code @StepScope}, for {@link ArrangementRun}'s reason: one
 * instance serves the invocation, so the id is derived once from inputs that cannot change within it.
 * Not because a second call would be refused — since ADR-115 {@code Ledger.startRun} carries on under
 * the row already there.
 */
@Component
@JobScope
class GenerationRun {

    /**
     * The stage name this run is minted under, matching its step's own name. Capability-named with no
     * ordinal in it, like the seven that exist — which is also what keeps an ordinal out of anything
     * the operator reads (ADR-098).
     */
    static final String STAGE = "generation";

    /** The module owning the rows this run writes. */
    static final String OWNING_MODULE = "synthesis";

    /** Named because the call is filled from the leading chunks this module cached (ADR-108). */
    static final String EXTRACTION_MODULE = "extraction";

    /** Named because which documents a call carries, and in what order, rests on the scores. */
    static final String EMBEDDING_MODULE = "embedding";

    /** The composing step's own module. */
    static final String PIPELINE_MODULE = "pipeline";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RunId runId;

    GenerationRun(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ArrangementGate arrangementGate,
            GenerationModel generationModel,
            OllamaClient ollamaClient,
            @Value("#{jobParameters['root']}") Path root) {
        Path canonicalRoot = Walk.canonicalRoot(root);
        WalkId walkId = ledger.finishedWalkFor(canonicalRoot)
                .orElseThrow(() -> new IllegalStateException("no finished walk is recorded for " + canonicalRoot
                        + "; census must run before anything can be generated"));
        RunId arrangement = arrangementGate.approvedArrangement(walkId)
                .orElseThrow(() -> new IllegalStateException("no approved arrangement stands for " + canonicalRoot
                        + "; the gate must be open before a generation run is minted"));
        String modelName = generationModel.name();

        this.runId = ledger.startRun(
                STAGE,
                implementationVersions.of(OWNING_MODULE, EXTRACTION_MODULE, EMBEDDING_MODULE, PIPELINE_MODULE),
                configConsumed(
                        canonicalRoot,
                        arrangement,
                        modelName,
                        ollamaClient.artefactOf(modelName).digest(),
                        ClusterSynthesis.CONTEXT_WINDOW),
                walkId,
                List.of(arrangement));
    }

    /**
     * What this run's id is derived from: the corpus root, the approved arrangement, and the generator
     * identity.
     *
     * <p>The serving URL is deliberately absent. Two deployments answering alike are one instrument and
     * moving a port is not a change, so an identity carrying the URL would mint a second run for the
     * same work — the rule {@code ExtractorIdentity} and {@code EmbedderIdentity} already follow.
     *
     * <p>Of the options ADR-108 names — {@code num_ctx}, {@code num_predict}, {@code temperature},
     * {@code seed} — only the context window is here, because it is the only one anything sends
     * (#180). The other three join this string in the ticket that first puts them on a call, and a
     * changed identity minting a new run is exactly what should happen when it does.
     */
    static String configConsumed(
            Path canonicalRoot,
            RunId arrangement,
            String modelName,
            String weightsDigest,
            int contextWindow) {
        return JSON_MAPPER.writeValueAsString(new ConfigConsumed(
                canonicalRoot.toString(), arrangement.value(), modelName, weightsDigest, contextWindow));
    }

    RunId runId() {
        return runId;
    }

    private record ConfigConsumed(
            String corpusRoot,
            String arrangementRunId,
            String generationModel,
            String weightsDigest,
            int contextWindow) {}
}
