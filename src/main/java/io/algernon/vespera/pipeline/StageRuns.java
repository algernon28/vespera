package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.OllamaClient;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hands a stage the run it mints or continues from stage 2 on, one accessor per kind of run (ADR-157
 * §3), replacing the seven {@code *Run} beans and the {@code ObjectProvider<…Run>} plumbing every
 * consumer of one used to hold.
 *
 * <p><b>Wraps {@link InvocationRuns} under a new name rather than extending it.</b> {@code
 * InvocationRuns} is the record of what this invocation arrived at, read without side effects in
 * places a mint must never happen ({@link GenerationTasklet}'s reading of the arrangement,
 * {@link NextAction}'s closing line). If it gained the power to mint, "what did this invocation arrive
 * at?" and "mint it now" would be one method call apart on one type — the mistake ADR-154 Context §3
 * names. So {@link InvocationRuns} stays a thin, bean-less record over the job execution's context, and
 * this class is where a stage asks for its run.
 *
 * <p><b>One accessor per kind of run mints the run the first time it is called in an invocation,</b>
 * remembers it in a field, and returns the same id on every later call. The fields are the memory, and
 * {@code @JobScope} is what makes them last exactly one invocation, one instance per job execution. The
 * two memories — this holder's fields and {@link InvocationRuns}'s own record — agree by construction:
 * {@link RunMint} writes the id into {@code invocationRuns} at the moment it writes the field.
 *
 * <p><b>No run row behind a shut gate</b> (ADR-080, kept as ADR-157 §4 describes): the rule is now a
 * property of each call site rather than of scoping. A caller checks its own gate and only past it
 * calls the accessor here; an accessor whose input is a gate's value throws {@link
 * IllegalStateException} if called while that value is absent, so a caller that forgets its gate fails
 * loudly, before {@code Ledger.startRun}, rather than minting.
 *
 * <p>Each stage keeps its own private {@code ConfigConsumed} record and its own module list, in the
 * exact shape and order the run class it replaces used — that is what keeps {@code
 * RunIdentityGoldenTest} passing unedited (ADR-157 §1, §2). Each accessor also reads its inputs in the
 * order today's constructor does, and an input that is not a run (the extractor identity, the
 * relevance floor, the generation model's weights digest) is read fresh when its run is minted, never
 * when this holder is built.
 */
@Component
@JobScope
class StageRuns {

    private final RunMint runMint;
    private final InvocationRuns invocationRuns;
    private final Path canonicalRoot;
    private final ObjectProvider<ExtractorIdentity> extractorIdentity;
    private final DegenerateOutputConfidenceFloor confidenceFloor;
    private final RedundancyGate redundancyGate;
    private final SeedGate seedGate;
    private final EmbeddingModelGate embeddingModelGate;
    private final ProfileStore profileStore;
    private final ArrangementGate arrangementGate;
    private final GenerationModel generationModel;
    private final GenerationContextWindow generationContextWindow;
    private final OllamaClient ollamaClient;

    private RunId extraction;
    private RunId contentCensus;
    private RunId contentRedundancy;
    private double contentRedundancyFloor;
    private RunId seedMeasurement;
    private RunId embeddingScoring;
    private RunId arrangement;
    private RunId generation;

    StageRuns(
            Ledger ledger,
            ImplementationVersions implementationVersions,
            ObjectProvider<ExtractorIdentity> extractorIdentity,
            DegenerateOutputConfidenceFloor confidenceFloor,
            RedundancyGate redundancyGate,
            SeedGate seedGate,
            EmbeddingModelGate embeddingModelGate,
            ProfileStore profileStore,
            ArrangementGate arrangementGate,
            GenerationModel generationModel,
            GenerationContextWindow generationContextWindow,
            OllamaClient ollamaClient,
            @Value("#{jobParameters['root']}") Path root,
            @Value("#{jobExecution.executionContext}") ExecutionContext executionContext) {
        this.invocationRuns = new InvocationRuns(executionContext);
        this.runMint = new RunMint(ledger, implementationVersions, invocationRuns);
        this.canonicalRoot = Walk.canonicalRoot(root);
        this.extractorIdentity = extractorIdentity;
        this.confidenceFloor = confidenceFloor;
        this.redundancyGate = redundancyGate;
        this.seedGate = seedGate;
        this.embeddingModelGate = embeddingModelGate;
        this.profileStore = profileStore;
        this.arrangementGate = arrangementGate;
        this.generationModel = generationModel;
        this.generationContextWindow = generationContextWindow;
        this.ollamaClient = ollamaClient;
    }

    /** The corpus root every stage from here on reads, canonicalised once. */
    Path canonicalRoot() {
        return canonicalRoot;
    }

    /** The run of {@code stage} this invocation minted or continued — a read, so it may precede a gate. */
    RunId upstream(StageModules stage) {
        return new UpstreamRuns(invocationRuns).runOf(stage.stage());
    }

    /** Stage 2's run, minted the first time this is called in this invocation. */
    RunId extraction() {
        if (extraction == null) {
            WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 2");
            RunId byteLevelReductionRunId = upstream(StageModules.BYTE_LEVEL_REDUCTION);
            extraction = runMint.mint(
                    StageModules.EXTRACTION,
                    new ExtractionConfigConsumed(extractorIdentity.getObject().value(), confidenceFloor.value()),
                    walk,
                    Optional.of(byteLevelReductionRunId));
        }
        return extraction;
    }

    /** Stage 3's run, minted the first time this is called in this invocation. */
    RunId contentCensus() {
        if (contentCensus == null) {
            WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 3");
            RunId extractionRunId = upstream(StageModules.EXTRACTION);
            contentCensus = runMint.mint(
                    StageModules.CONTENT_CENSUS,
                    new ContentCensusConfigConsumed(canonicalRoot.toString(), extractionRunId.value()),
                    walk,
                    Optional.of(extractionRunId));
        }
        return contentCensus;
    }

    /**
     * Stage 4's run, minted the first time this is called in this invocation.
     *
     * @throws IllegalStateException if the boilerplate floor is unset — unreachable past {@link
     *     RedundancyGate}'s own gate, and asserted by no test
     */
    RunId contentRedundancy() {
        if (contentRedundancy == null) {
            double floor = redundancyGate
                    .floor()
                    .orElseThrow(() -> new IllegalStateException(
                            "the content-redundancy run must not be minted while the gate is closed"));
            WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 4");
            RunId contentCensusRunId = upstream(StageModules.CONTENT_CENSUS);
            contentRedundancy = runMint.mint(
                    StageModules.CONTENT_REDUNDANCY,
                    new ContentRedundancyConfigConsumed(
                            canonicalRoot.toString(), contentCensusRunId.value(), floor),
                    walk,
                    Optional.of(contentCensusRunId));
            contentRedundancyFloor = floor;
        }
        return contentRedundancy;
    }

    /** The boilerplate floor stage 4's run was minted under (ADR-080). Mints stage 4's run if it has not yet. */
    double contentRedundancyFloor() {
        contentRedundancy();
        return contentRedundancyFloor;
    }

    /**
     * Stage 5's measurement run, minted the first time this is called in this invocation.
     *
     * @throws IllegalStateException if the seed gate is closed — unreachable past {@link SeedGate}'s
     *     own gate, and asserted by no test
     */
    RunId seedMeasurement() {
        if (seedMeasurement == null) {
            SeedGate.SeedWalk seedWalk = seedGate
                    .seedWalk()
                    .orElseThrow(() -> new IllegalStateException(
                            "the seed-measurement run must not be minted while the seed gate is closed"));
            WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 5");
            RunId contentRedundancyRunId = upstream(StageModules.CONTENT_REDUNDANCY);
            seedMeasurement = runMint.mint(
                    StageModules.SEED_MEASUREMENT,
                    new SeedMeasurementConfigConsumed(
                            canonicalRoot.toString(),
                            seedWalk.canonicalRoot().toString(),
                            contentRedundancyRunId.value()),
                    walk,
                    Optional.of(contentRedundancyRunId));
        }
        return seedMeasurement;
    }

    /**
     * Gate 3's scoring run, minted the first time this is called in this invocation.
     *
     * @throws IllegalStateException if no embedding model is named — unreachable past {@link
     *     EmbeddingModelGate}'s own gate, and asserted by no test
     */
    RunId embeddingScoring() {
        if (embeddingScoring == null) {
            String modelName = embeddingModelGate
                    .modelName()
                    .orElseThrow(() -> new IllegalStateException(
                            "the embedding-scoring run must not be minted while embeddingModel is unset"));
            RunId measurementRunId = seedMeasurement();
            // Read here, fresh on every mint, rather than through a bean of its own: this holder is
            // @JobScope, so this is the freshness ADR-117 needs a changed profile value to be seen with.
            Double relevanceScoreFloor = RelevanceScoreFloorValue.readFrom(profileStore).value();
            WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 5");
            embeddingScoring = runMint.mint(
                    StageModules.EMBEDDING_SCORING,
                    new EmbeddingScoringConfigConsumed(
                            canonicalRoot.toString(), modelName, measurementRunId.value(), relevanceScoreFloor),
                    walk,
                    Optional.of(measurementRunId));
        }
        return embeddingScoring;
    }

    /** Stage 6a's run, minted the first time this is called in this invocation. */
    RunId arrangement() {
        if (arrangement == null) {
            RunId scoringRunId = embeddingScoring();
            WalkId walk = runMint.finishedWalk(canonicalRoot, "the documents can be arranged");
            arrangement = runMint.mint(
                    StageModules.ARRANGEMENT,
                    new ArrangementConfigConsumed(canonicalRoot.toString(), scoringRunId.value()),
                    walk,
                    Optional.of(scoringRunId));
        }
        return arrangement;
    }

    /**
     * Stage 6b's run, minted the first time this is called in this invocation.
     *
     * <p><b>The generator identity is what was asked and what answered, never where it was served</b>
     * (ADR-090, ADR-091): the model's name, the digest of the weights actually serving under that
     * name, and — once anything sends them — the options actually sent. "The options actually sent"
     * means the fields of Ollama's {@code options} object — {@code num_ctx} and {@code num_predict}
     * today — and nothing past it: {@code think} and {@code format} are top-level request fields, not
     * members of that object, and a code constant outside it reaches this run's id through the
     * implementation version rather than through {@link GenerationConfigConsumed} (ADR-159, amending
     * ADR-108). The digest is read here because {@code synthesis} may not reach the serving engine, so
     * {@code pipeline} reads it and hands it down as a plain string (ADR-110).
     *
     * @throws IllegalStateException if no approved arrangement stands for this invocation's own
     *     arrangement — unreachable past {@link ArrangementGate}'s own gate, and asserted by no test
     */
    RunId generation() {
        if (generation == null) {
            WalkId walk = runMint.finishedWalk(canonicalRoot, "anything can be generated");
            // Never ArrangementRun.getObject() itself, which would mint an arrangement behind the
            // arrangement step's own gate (ADR-154, Context §3) -- only the arrangement this invocation
            // already arrived at, if it arrived at one, is asked about.
            Optional<RunId> thisInvocationsArrangement = invocationRuns.runOf(StageModules.ARRANGEMENT.stage());
            RunId approvedArrangement = arrangementGate
                    .approvedArrangement(thisInvocationsArrangement)
                    .orElseThrow(() -> new IllegalStateException("no approved arrangement stands for "
                            + canonicalRoot + "; the gate must be open before a generation run is minted"));
            String modelName = generationModel.name();
            generation = runMint.mint(
                    StageModules.GENERATION,
                    new GenerationConfigConsumed(
                            canonicalRoot.toString(),
                            approvedArrangement.value(),
                            modelName,
                            ollamaClient.artefactOf(modelName).digest(),
                            generationContextWindow.size(),
                            ClusterSynthesis.REPLY_ALLOWANCE),
                    walk,
                    Optional.of(approvedArrangement));
        }
        return generation;
    }

    /** Stage 2's own {@code ConfigConsumed}, unchanged from the run class this replaces (ADR-157 §2). */
    private record ExtractionConfigConsumed(String extractorIdentity, Double degenerateOutputConfidenceFloor) {}

    /** Stage 3's own {@code ConfigConsumed}, unchanged. */
    private record ContentCensusConfigConsumed(String root, String extractionRunId) {}

    /** Stage 4's own {@code ConfigConsumed}, unchanged. */
    private record ContentRedundancyConfigConsumed(
            String root, String stage3RunId, double boilerplateDocumentFrequencyFloor) {}

    /** Stage 5's measurement run's own {@code ConfigConsumed}, unchanged. */
    private record SeedMeasurementConfigConsumed(String root, String seedFolder, String redundancyRunId) {}

    /** Gate 3's scoring run's own {@code ConfigConsumed}, unchanged. */
    private record EmbeddingScoringConfigConsumed(
            String root, String embeddingModel, String measurementRunId, Double relevanceScoreFloor) {}

    /** Stage 6a's own {@code ConfigConsumed}, unchanged. */
    private record ArrangementConfigConsumed(String corpusRoot, String scoringRunId) {}

    /**
     * Stage 6b's own {@code ConfigConsumed}, unchanged from the run class this replaces.
     *
     * <p>Of the options ADR-108 names — {@code num_ctx}, {@code num_predict}, {@code temperature},
     * {@code seed} — the first two are here, because those are the two anything sends (#180, #182).
     * {@code temperature} and {@code seed} join this record in the ticket that first puts them on a
     * call, and a changed identity minting a new run is exactly what should happen when it does.
     * <b>{@code think} is deliberately not a fifth</b>: it is a top-level request field, not a member
     * of Ollama's {@code options} object, so it is not one of "the options actually sent" and does not
     * belong here — a code constant, and it reaches this run's id through the implementation version
     * like the prompt text does (ADR-159, amending ADR-108).
     *
     * <p>The window is what an invocation resolved rather than what the code ships with, so an
     * operator who widens it re-generates the corpus under a run of its own: a larger window reads
     * more of each cluster, so the same archive written under two windows is two different pieces of
     * work and neither can be mistaken for the other.
     */
    private record GenerationConfigConsumed(
            String corpusRoot,
            String arrangementRunId,
            String generationModel,
            String weightsDigest,
            int contextWindow,
            int replyAllowance) {}
}
