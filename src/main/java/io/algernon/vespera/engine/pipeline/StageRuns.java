package io.algernon.vespera.engine.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.embedding.ModelArtefact;
import io.algernon.vespera.engine.embedding.OllamaClient;
import io.algernon.vespera.engine.embedding.RelevanceScale;
import io.algernon.vespera.engine.extraction.ExtractorIdentity;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.similarity.MinHashParameters;
import io.algernon.vespera.engine.similarity.RedundancyThresholds;
import io.algernon.vespera.engine.similarity.ShingleParameters;
import io.algernon.vespera.engine.synthesis.ClusterSynthesis;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.TopicNaming;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
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
 * loudly, before {@code Runs.startRun}, rather than minting.
 *
 * <p>Each stage keeps its own private {@code ConfigConsumed} record and its own module list, in the
 * shape and order {@code RunIdentityGoldenTest} holds for it (ADR-157 §1, §2). Each accessor also reads
 * its inputs in a fixed order, and an input that is not a run (the extractor identity, the relevance
 * floor, the embedding model's name and artefact, the generation model's weights digest) is read fresh when its
 * run is minted, never when this holder is built. The scoring run's three are then kept, and handed to
 * the steps after it, which read what the run names and not the profile again (ADR-228, ADR-117).
 *
 * <p>The twenty presets of ADR-330 section 9, as ADR-399, ADR-400 and ADR-401 amend it, are read the same way: each run
 * reads its own through {@link Presets} as it is minted, writes into its configuration the ones that differ from what
 * every earlier run was minted under, and hands them on through the accessors below, so the number that names the run
 * is the number that works.
 */
@Component
@JobScope
class StageRuns {

    /*
     * What every run was minted under before the twenty presets of ADR-330 section 9, as ADR-399, ADR-400 and ADR-401
     * amend it, existed. They are held here and not read from what ships: a preset shipped at another value later is
     * written, so that it cannot be taken for a run of the earlier one, and the configuration text of a run at these
     * values is what it was (RunIdentityGoldenTest). A member of a run's configuration is left out where the value
     * resolved is one of these.
     */
    private static final double RESOLUTION_EVERY_EARLIER_RUN = 1.0;
    private static final int GRAPH_K_EVERY_EARLIER_RUN = 15;
    private static final int SIMILARITIES_EVERY_EARLIER_RUN = 3;
    private static final int BANDS_EVERY_EARLIER_RUN = 5;
    private static final int PER_BAND_EVERY_EARLIER_RUN = 12;
    private static final double TOPIC_RESOLUTION_EVERY_EARLIER_RUN = 1.0;
    private static final int TOPIC_K_EVERY_EARLIER_RUN = 10;
    private static final double TOPIC_BREADTH_FLOOR_EVERY_EARLIER_RUN = 0.005;
    private static final double SEE_ALSO_SHARE_EVERY_EARLIER_RUN = 0.15;
    private static final int TIMEOUTS_IN_A_ROW_EVERY_EARLIER_RUN = 3;
    private static final String SHINGLE_IDENTITY_EVERY_EARLIER_RUN = "word:5";
    private static final String MINHASH_IDENTITY_EVERY_EARLIER_RUN =
            "perms:128;bands:16;rows:8;seed:6840335614489015382";
    private static final String THRESHOLDS_IDENTITY_EVERY_EARLIER_RUN =
            "near-duplicate:0.8;containment:0.95;sample:32;hits:24";

    private final Ledger ledger;
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
    private final LanguageOfTheDeliverable languageOfTheDeliverable;
    private final OllamaClient ollamaClient;
    private final Presets presets;

    private RunId extraction;
    private OptionalLong degenerateOutputWordFloor;
    private int timeoutsInARow;
    private ShingleParameters shingleWindow;
    private RunId contentCensus;
    private RunId contentRedundancy;
    private double contentRedundancyFloor;
    private MinHashParameters minHash;
    private RedundancyThresholds redundancyThresholds;
    private double clusterResolution;
    private int clusterNeighbours;
    private int topSimilarities;
    private int sampleBands;
    private int sampledPerBand;
    private double topicResolution;
    private int topicNeighbours;
    private double topicBreadthFloor;
    private double seeAlsoShare;
    private RunId seedMeasurement;
    private RunId embeddingScoring;
    private String embeddingModelName;
    private ModelArtefact embeddingModelArtefact;
    private Double relevanceScoreFloor;
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
            Presets presets,
            @Value("#{jobParameters['root']}") Path root,
            @Value("#{jobExecution.executionContext}") ExecutionContext executionContext) {
        this.ledger = ledger;
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
        this.languageOfTheDeliverable = new LanguageOfTheDeliverable(profileStore);
        this.ollamaClient = ollamaClient;
        this.presets = presets;
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
            // One load serves both keys, so the number that names the run is the number that removes (ADR-117).
            Profile profile = profileStore.load();
            DegenerateOutputWordFloor wordFloor = DegenerateOutputWordFloor.of(profile.degenerateOutputWordFloor());
            // The two presets stage 2's run is minted under, read here as the run is, and kept to be handed to the
            // steps that use them (ADR-330 section 9, ADR-117).
            int timeouts = presets.timeoutsInARow();
            ShingleParameters window = presets.shingleWindow();
            extraction = runMint.mint(
                    StageModules.EXTRACTION,
                    new ExtractionConfigConsumed(
                            extractorIdentity.getObject().value(),
                            confidenceFloor.value(),
                            // Read here, fresh on every mint (ADR-185 §1), as embeddingScoring() reads
                            // the relevance floor -- never a bean built at start-up.
                            ExtractionAttempt.of(profile.extractionAttempt()).identityMember(),
                            wordFloor.identityMember(),
                            timeouts == TIMEOUTS_IN_A_ROW_EVERY_EARLIER_RUN ? null : timeouts,
                            window.identity().equals(SHINGLE_IDENTITY_EVERY_EARLIER_RUN) ? null : window.identity()),
                    walk,
                    Optional.of(byteLevelReductionRunId));
            degenerateOutputWordFloor = wordFloor instanceof DegenerateOutputWordFloor.Floor floor
                    ? OptionalLong.of(floor.words())
                    : OptionalLong.empty();
            timeoutsInARow = timeouts;
            shingleWindow = window;
        }
        return extraction;
    }

    /**
     * How many timeouts in a row set an occurrence aside, as stage 2's run was minted (ADR-330 section 9), which
     * mints that run if it has not yet been. The number that names the run is the number that works (ADR-117).
     */
    int timeoutsInARow() {
        extraction();
        return timeoutsInARow;
    }

    /**
     * The shingle window stage 2's run was minted under, which mints that run if it has not yet been: the window of
     * the shingles written under it, and so of every later stage that reads them (ADR-330 section 9).
     */
    ShingleParameters shingleWindow() {
        extraction();
        return shingleWindow;
    }

    /**
     * The word floor stage 2's run was minted under (ADR-282 section 3), minting the run if it has not been:
     * empty while the key is unset or ignored.
     */
    OptionalLong degenerateOutputWordFloor() {
        extraction();
        return degenerateOutputWordFloor;
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
            // The signature's numbers and the cuts, read as the run is minted and kept for the steps (ADR-330
            // section 9).
            MinHashParameters permutations = presets.minHash();
            RedundancyThresholds cuts = presets.redundancyThresholds();
            contentRedundancy = runMint.mint(
                    StageModules.CONTENT_REDUNDANCY,
                    new ContentRedundancyConfigConsumed(
                            canonicalRoot.toString(),
                            contentCensusRunId.value(),
                            floor,
                            permutations.identity().equals(MINHASH_IDENTITY_EVERY_EARLIER_RUN)
                                    ? null
                                    : permutations.identity(),
                            cuts.identity().equals(THRESHOLDS_IDENTITY_EVERY_EARLIER_RUN) ? null : cuts.identity()),
                    walk,
                    Optional.of(contentCensusRunId));
            contentRedundancyFloor = floor;
            minHash = permutations;
            redundancyThresholds = cuts;
        }
        return contentRedundancy;
    }

    /** The signature's numbers stage 4's run was minted under, which mints that run if it has not yet been. */
    MinHashParameters minHash() {
        contentRedundancy();
        return minHash;
    }

    /** The cuts stage 4's run was minted under, which mints that run if it has not yet been. */
    RedundancyThresholds redundancyThresholds() {
        contentRedundancy();
        return redundancyThresholds;
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
     * @throws IllegalStateException if neither the profile nor configuration names an embedding model
     *     (ADR-298) — unreachable past {@link
     *     EmbeddingModelGate}'s own gate, and asserted by no test
     */
    RunId embeddingScoring() {
        if (embeddingScoring == null) {
            mintEmbeddingScoring(embeddingModelGate
                    .modelName()
                    .orElseThrow(() -> new IllegalStateException(
                            "the embedding-scoring run must not be minted while no embedding model is named")));
        }
        return embeddingScoring;
    }

    /**
     * Gate 3's scoring run, minted the first time this or {@link #embeddingScoring()} is called in this
     * invocation, under {@code modelName}: the model the caller's gate read and asked Ollama about, so that a
     * profile saved between that read and the mint is read by the next run and not by this one (ADR-228
     * section 2, ADR-202 section 2, ADR-244). Where the run was minted already, it is that run.
     */
    RunId embeddingScoringUnder(String modelName) {
        if (embeddingScoring == null) {
            mintEmbeddingScoring(modelName);
        }
        return embeddingScoring;
    }

    private void mintEmbeddingScoring(String modelName) {
        RunId measurementRunId = seedMeasurement();
        // Read here, fresh on every mint, rather than through a bean of its own: this holder is
        // @JobScope, so this is the freshness ADR-117 needs a changed profile value to be seen with.
        // One load of the profile serves the values it is minted under.
        Profile profile = profileStore.load();
        Double relevanceScoreFloor = RelevanceScoreFloorValue.from(profile).value();
        // The five presets the run is minted under, read beside them: the resolution is the profile's where that can be
        // acted on, from the same load (ADR-330 section 9). They are kept to be handed to the steps that use them.
        double resolution = presets.clusterResolution(profile);
        int neighbours = presets.clusterNeighbours();
        int similarities = presets.topSimilarities();
        int bands = presets.sampleBands();
        int perBand = presets.sampledPerBand();
        double topicResolutionValue = presets.topicResolution();
        int topicNeighboursValue = presets.topicNeighbours();
        double topicBreadthFloorValue = presets.topicBreadthFloor();
        double seeAlsoShareValue = presets.seeAlsoShare();
        // The artefact is read once, here, beside the floor (ADR-228).
        ModelArtefact artefact = ollamaClient.artefactOf(modelName);
        WalkId walk = runMint.finishedWalk(canonicalRoot, "stage 5");
        embeddingScoring = runMint.mint(
                StageModules.EMBEDDING_SCORING,
                new EmbeddingScoringConfigConsumed(
                        canonicalRoot.toString(),
                        modelName,
                        artefact.digest(),
                        artefact.weightDtype(),
                        measurementRunId.value(),
                        relevanceScoreFloor,
                        resolution == RESOLUTION_EVERY_EARLIER_RUN ? null : resolution,
                        neighbours == GRAPH_K_EVERY_EARLIER_RUN ? null : neighbours,
                        similarities == SIMILARITIES_EVERY_EARLIER_RUN ? null : similarities,
                        bands == BANDS_EVERY_EARLIER_RUN ? null : bands,
                        perBand == PER_BAND_EVERY_EARLIER_RUN ? null : perBand,
                        topicResolutionValue == TOPIC_RESOLUTION_EVERY_EARLIER_RUN ? null : topicResolutionValue,
                        topicNeighboursValue == TOPIC_K_EVERY_EARLIER_RUN ? null : topicNeighboursValue,
                        topicBreadthFloorValue == TOPIC_BREADTH_FLOOR_EVERY_EARLIER_RUN ? null : topicBreadthFloorValue,
                        seeAlsoShareValue == SEE_ALSO_SHARE_EVERY_EARLIER_RUN ? null : seeAlsoShareValue),
                walk,
                Optional.of(measurementRunId));
        this.clusterResolution = resolution;
        this.clusterNeighbours = neighbours;
        this.topSimilarities = similarities;
        this.sampleBands = bands;
        this.sampledPerBand = perBand;
        this.topicResolution = topicResolutionValue;
        this.topicNeighbours = topicNeighboursValue;
        this.topicBreadthFloor = topicBreadthFloorValue;
        this.seeAlsoShare = seeAlsoShareValue;
        embeddingModelName = modelName;
        embeddingModelArtefact = artefact;
        this.relevanceScoreFloor = relevanceScoreFloor;
    }

    /**
     * The embedding model's name the scoring run was minted under, which mints that run if it has not yet
     * been. Every later step of the run reads by this name and not by the profile's, which an operator may
     * have saved since (ADR-228 sections 2 and 4, ADR-117).
     */
    String embeddingModelName() {
        embeddingScoring();
        return embeddingModelName;
    }

    /**
     * The embedding model's name a step's gate reads (ADR-084): the profile's, else the configured default (ADR-298),
     * until the scoring run is minted in this invocation, and the name it was minted under from then on, without
     * minting it. A model cleared or renamed in the profile after the mint is read by the next run, not by the later
     * steps of this one (ADR-228 section 2, ADR-117).
     */
    Optional<String> embeddingModelNamed() {
        return embeddingScoring == null ? embeddingModelGate.modelName() : Optional.of(embeddingModelName);
    }

    /**
     * The relevance floor the scoring run was minted under, empty where it was unset then, which mints that
     * run if it has not yet been. The number that removes is the number that names the run (ADR-117).
     */
    OptionalDouble relevanceScoreFloor() {
        embeddingScoring();
        return relevanceScoreFloor == null ? OptionalDouble.empty() : OptionalDouble.of(relevanceScoreFloor);
    }

    /**
     * The resolution the scoring run was minted under, which mints that run if it has not yet been: the profile's
     * {@code clusterResolution} where that can be acted on, else the preset (ADR-330 section 9).
     */
    double clusterResolution() {
        embeddingScoring();
        return clusterResolution;
    }

    /**
     * The neighbours of each document the scoring run was minted under, which mints that run if it has not yet been.
     */
    int clusterNeighbours() {
        embeddingScoring();
        return clusterNeighbours;
    }

    /**
     * The number of similarities a score is the mean of, as the scoring run was minted, which mints that run if it
     * has not yet been.
     */
    int topSimilarities() {
        embeddingScoring();
        return topSimilarities;
    }

    /** The bands the relevance report cuts the scores into, as the scoring run was minted. */
    int sampleBands() {
        embeddingScoring();
        return sampleBands;
    }

    /** The modularity resolution the topics are found at, as the scoring run was minted (ADR-399). */
    double topicResolution() {
        embeddingScoring();
        return topicResolution;
    }

    /** The neighbours each seed passage keeps in the topics' graph, as the scoring run was minted (ADR-399). */
    int topicNeighbours() {
        embeddingScoring();
        return topicNeighbours;
    }

    /** The share of the seed passages a topic must hold to stand, as the scoring run was minted (ADR-401). */
    double topicBreadthFloor() {
        embeddingScoring();
        return topicBreadthFloor;
    }

    /**
     * The share of a survivor's chunks a topic must be nearest to for it to fit it, as the scoring run was minted
     * (ADR-400).
     */
    double seeAlsoShare() {
        embeddingScoring();
        return seeAlsoShare;
    }

    /** The documents each band is asked for, as the scoring run was minted. */
    int sampledPerBand() {
        embeddingScoring();
        return sampledPerBand;
    }

    /**
     * The scale a score of this run is on, given the embedder identity its vectors carry: the identity and, where the
     * run's number of similarities is not 3, {@code ;top:<n>} after it (ADR-330 section 9). Composed in {@code
     * embedding}, which decides whether a floor removes by it (ADR-222).
     */
    String relevanceScale(String embedderIdentity) {
        return RelevanceScale.of(embedderIdentity, topSimilarities());
    }

    /** The artefact the scoring run was minted under, which mints that run if it has not yet been (ADR-228). */
    ModelArtefact embeddingModelArtefact() {
        embeddingScoring();
        return embeddingModelArtefact;
    }

    /**
     * Whether Ollama, asked now, still reports the artefact the scoring run was minted under (ADR-231
     * section 1). False after the embedding model was pulled again with another digest or weight dtype.
     */
    boolean embeddingModelArtefactStillReported() {
        ModelArtefact minted = embeddingModelArtefact();
        return ollamaClient.artefactOf(embeddingModelName).equals(minted);
    }

    /** The language the topics are named in and the deliverable is written in (ADR-321 section 1, ADR-407 section 3). */
    DeliverableLanguage deliverableLanguage() {
        return languageOfTheDeliverable.language();
    }

    /**
     * The model the arrangement step names the topics under (ADR-407 section 2).
     *
     * @throws NoNamingModelNamedException where the value is blank
     */
    String namingModelName() {
        return presets.namingModel();
    }

    /** Stage 6a's run, minted the first time this is called in this invocation. */
    RunId arrangement() {
        if (arrangement == null) {
            // The two values only this run reads, resolved where it is minted, before any call (ADR-407 sections 2, 3).
            String namingModel = namingModelName();
            DeliverableLanguage language = languageOfTheDeliverable.language();
            RunId scoringRunId = embeddingScoring();
            WalkId walk = runMint.finishedWalk(canonicalRoot, "the documents can be arranged");
            long standingRemovals = ledger.verdicts().verdictsUnder(scoringRunId);
            arrangement = runMint.mint(
                    StageModules.ARRANGEMENT,
                    new ArrangementConfigConsumed(
                            canonicalRoot.toString(),
                            scoringRunId.value(),
                            standingRemovals == 0 ? null : standingRemovals,
                            namingModel,
                            ollamaClient.artefactOf(namingModel).digest(),
                            TopicNaming.NAMING_WINDOW,
                            TopicNaming.NAME_REPLY_ALLOWANCE,
                            ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD,
                            TopicNaming.NAMING_TEMPERATURE,
                            TopicNaming.NAMING_SEED,
                            language.code()),
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
     * means the fields of Ollama's {@code options} object — {@code num_ctx}, {@code num_predict} and,
     * since ADR-166 §3, {@code num_keep} — and nothing past it: {@code think} and {@code format} are
     * top-level request fields, not members of that object, and a code constant outside it reaches this
     * run's id through the implementation version rather than through {@link GenerationConfigConsumed}
     * (ADR-159, amending ADR-108). The digest is read here because {@code synthesis} may not reach the
     * serving engine, so {@code pipeline} reads it and hands it down as a plain string (ADR-110).
     *
     * @throws IllegalStateException if no approved arrangement stands for this invocation's own
     *     arrangement — unreachable past {@link ArrangementGate}'s own gate, and asserted by no test
     */
    RunId generation() {
        if (generation == null) {
            WalkId walk = runMint.finishedWalk(canonicalRoot, "anything can be generated");
            // Never arrangement() itself, which would mint an arrangement behind the arrangement
            // step's own gate (ADR-154, Context §3) -- only the arrangement this invocation already
            // arrived at, if it arrived at one, is asked about.
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
                            ClusterSynthesis.REPLY_ALLOWANCE,
                            ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD,
                            languageOfTheDeliverable.language().code()),
                    walk,
                    Optional.of(approvedArrangement));
        }
        return generation;
    }

    /** Stage 2's own {@code ConfigConsumed}, unchanged from the run class this replaces (ADR-157 §2). */
    private record ExtractionConfigConsumed(
            String extractorIdentity,
            Double degenerateOutputConfidenceFloor,
            // Per component, never per class: the floor must keep serialising as null (ADR-185 §1).
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer extractionAttempt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long degenerateOutputWordFloor,
            // The two presets of ADR-330 section 9, each written only where it is not what every run before the
            // presets was minted under, so that an earlier run's text stays as it was.
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer timeoutsInARow,
            @JsonInclude(JsonInclude.Include.NON_NULL) String shingleIdentity) {}

    /** Stage 3's own {@code ConfigConsumed}, unchanged. */
    private record ContentCensusConfigConsumed(String root, String extractionRunId) {}

    /**
     * Stage 4's own {@code ConfigConsumed}, extended by the two presets of ADR-330 section 9, each written only where
     * it is not what every run before the presets was minted under.
     */
    private record ContentRedundancyConfigConsumed(
            String root,
            String stage3RunId,
            double boilerplateDocumentFrequencyFloor,
            @JsonInclude(JsonInclude.Include.NON_NULL) String minHash,
            @JsonInclude(JsonInclude.Include.NON_NULL) String redundancyThresholds) {}

    /** Stage 5's measurement run's own {@code ConfigConsumed}, unchanged. */
    private record SeedMeasurementConfigConsumed(String root, String seedFolder, String redundancyRunId) {}

    /** Gate 3's scoring run's own {@code ConfigConsumed}: the model's name and the artefact it names (ADR-228). */
    private record EmbeddingScoringConfigConsumed(
            String root,
            String embeddingModel,
            String embeddingModelDigest,
            String embeddingModelWeightDtype,
            String measurementRunId,
            Double relevanceScoreFloor,
            // The five of ADR-330 section 9, in that order, each written only where it is not what every run before
            // the presets was minted under: the number a scoring run's cluster and sample are made at, and the mean a
            // score is.
            @JsonInclude(JsonInclude.Include.NON_NULL) Double clusterResolution,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer clusterNeighbours,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer topSimilarities,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer sampleBands,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer sampledPerBand,
            // ADR-399: the topics' two numbers, after sampledPerBand, each written only where it is not what ships.
            @JsonInclude(JsonInclude.Include.NON_NULL) Double topicResolution,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer topicNeighbours,
            // ADR-401 and ADR-400: the breadth floor and then the see-also share, after the topics' two numbers, each
            // written only where it is not what ships.
            @JsonInclude(JsonInclude.Include.NON_NULL) Double topicBreadthFloor,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double seeAlsoShare) {}

    /**
     * Stage 6a's own {@code ConfigConsumed}, extended once (ADR-230 section 3). {@code standingRemovals}
     * is how many verdicts stand under the scoring run when this run is minted, which names which of the
     * scoring run's two sets of survivors the arrangement is of. It is written only where it is not zero,
     * so an arrangement over a scoring run with no removal standing keeps the id it had.
     */
    private record ArrangementConfigConsumed(
            String corpusRoot,
            String scoringRunId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long standingRemovals,
            // ADR-407 section 3, after standingRemovals, each always written: the model that names the topics and the
            // options its calls send, and the language the names are written in.
            String namingModel,
            String namingModelDigest,
            int namingContextWindow,
            int namingReplyAllowance,
            int namingNumKeep,
            double namingTemperature,
            int namingSeed,
            String deliverableLanguage) {}

    /**
     * Stage 6b's own {@code ConfigConsumed}, extended once from the run class this replaces.
     *
     * <p>Of the options ADR-108 names — {@code num_ctx}, {@code num_predict}, {@code temperature},
     * {@code seed} — {@code num_ctx} and {@code num_predict} are here because those are the two
     * anything sends (#180, #182), and, since ADR-166 §3, so is {@code num_keep}. {@code temperature}
     * and {@code seed} join this record in the ticket that first puts them on a call, and a changed
     * identity minting a new run is exactly what should happen when it does. <b>{@code think} is
     * deliberately not a member</b>: it is a top-level request field, not a member of Ollama's {@code
     * options} object, so it is not one of "the options actually sent" and does not belong here — a
     * code constant, and it reaches this run's id through the implementation version like the prompt
     * text does (ADR-159, amending ADR-108).
     *
     * <p><b>{@code numKeep} is recorded for the same reason as the two beside it, not a different
     * one</b> (ADR-166 §3): {@code num_keep} is a member of Ollama's {@code options} object exactly as
     * {@code num_ctx} and {@code num_predict} are, sent {@code -1} on every call so a question the
     * engine has to cut is counted where the answer check can see it — ADR-159's rule that a member of
     * that object belongs in this record is what puts it here, not merely that a changed constant would
     * also move the implementation version this run already carries.
     *
     * <p>The window is what an invocation resolved rather than what the code ships with, so an
     * operator who widens it re-generates the corpus under a run of its own: a larger window reads
     * more of each cluster, so the same archive written under two windows is two different pieces of
     * work and neither can be mistaken for the other.
     *
     * <p>{@code deliverableLanguage} is the code as read, in lower case, after {@code numKeep} (ADR-321 §1): the
     * same arrangement written in the other language is another generation run.
     */
    private record GenerationConfigConsumed(
            String corpusRoot,
            String arrangementRunId,
            String generationModel,
            String weightsDigest,
            int contextWindow,
            int replyAllowance,
            int numKeep,
            String deliverableLanguage) {}
}
