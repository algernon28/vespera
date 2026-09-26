package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.ClusteringBeans;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.DocumentTitles;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.SynthesisDocs;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole job, from the command line down to the rows, in a context narrow enough to need neither
 * Chroma nor a live Ollama nor a Docling sidecar: the one slice every whole-job test in this package
 * is built on (ADR-153, amending ADR-131).
 *
 * <p><b>What a test adds on top of it is what makes it that test, and nothing else.</b> This carries
 * every class the whole job needs that no test varies — each stage's configuration class, tasklets,
 * run beans and gates, the capability beans behind them, the scripted generation and embedding
 * runtimes, and the CLI. A test names, in an {@code @Import} of its own, the one extraction double
 * its claims rest on ({@link StubbedExtractionBeans}, {@link SeedScriptedExtractionBeans},
 * {@link PictureScriptedExtractionBeans}, {@link io.algernon.vespera.extraction.CountingDoclingBeans}
 * or {@link io.algernon.vespera.extraction.ExtractionBeans}), plus whatever probe or extra bean it
 * alone asks about. No extraction double is named here, because the five differ in what they script
 * and each whole-job test claims something about one particular script.
 *
 * <p><b>A class named both here and in a test's own list is not an error Spring reports.</b> The two
 * {@code @Import}s are gathered into one set before anything is registered, so the duplicate is
 * quietly registered once, bean overriding or not. {@link CascadeSliceImportsTest} is what fails
 * instead, because a second copy of this wiring is the thing this annotation exists to remove.
 *
 * <p>Before this existed the same list of 85 to 89 classes was written out in each of 24 classes, so
 * moving, renaming or deleting one class the job wires meant editing all 24 — which is why ADR-131
 * kept one configuration class per stage as "the seam the slice tests import". This list is where a
 * whole-job test's wiring now lives, so a change to how the job is composed is a change here.
 *
 * <p>{@code UnconfiguredRootTest} was the one whose list differed: it named the real
 * {@link io.algernon.vespera.extraction.ExtractionBeans} and none of {@link HybridChunkerBeans},
 * {@link ExtractionMetrics} or {@link LanguageDetection}, because its invocation refuses before any
 * stage runs and nothing it asks about needed them. It is on this annotation too: the three are
 * constructed and never called there, and its claims are about the refusal, not about what is wired.
 *
 * <p>Why a slice at all rather than the whole application: the whole application starts Chroma and
 * Ollama, and none of these tests is about either. The one non-obvious piece is
 * {@code @Transactional(NOT_SUPPORTED)}: census deliberately runs outside a transaction so that a
 * walk commits at its own checkpoints, and a test-managed transaction wrapped around it would be
 * suspended and then hold the only connection the test datasource has.
 *
 * <p>Lives in {@code pipeline}'s test package on purpose, beside the tests that use it: most of the
 * classes it imports are package-private to {@code pipeline}. ADR-052 also records that a test-support
 * package of its own would read as a further module to {@code ApplicationModules}, the reason {@code
 * TestSteps} and {@code Adr} sit in the root package.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    // The CLI, and the job it launches.
    VesperaCli.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    NextAction.class,
    VesperaJobConfiguration.class,
    // Every stage from 2 on asks for its run through one holder (ADR-157).
    StageRuns.class,
    // Stage 0: census.
    CensusTasklet.class,
    WalkRecorder.class,
    AnomalyLog.class,
    // Stage 1: byte-level reduction.
    ByteLevelReductionTasklet.class,
    ContentIdentity.class,
    DetectedFormats.class,
    // Stage 2: extraction. The extractor itself is the one double each test names.
    ExtractionJobConfiguration.class,
    ExtractionItemProcessor.class,
    ExtractionItemWriter.class,
    ExtractionTimeoutStreak.class,
    ExtractionCircuitBreaker.class,
    ExtractionHealthCheckListener.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    HybridChunkerBeans.class,
    ConfidenceDistribution.class,
    // Stage 3: content census.
    ContentCensusTasklet.class,
    Shingler.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    // Stage 4: content redundancy.
    RedundancyJobConfiguration.class,
    RedundancyGate.class,
    RedundancyBoilerplate.class,
    RedundancySignatureItemWriter.class,
    RedundancyResolutionTasklet.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    // Stage 5: seed extraction and measurement, scoring, the floor, the report, clustering.
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonTasklet.class,
    SeedGate.class,
    UsableSeedGate.class,
    SeedCorpusComparison.class,
    UnusableSeeds.class,
    EmbeddingModelGate.class,
    EmbeddingScoringTasklet.class,
    ChunkEmbedderBeans.class,
    EmbeddingScriptedBeans.class,
    RelevanceScoringTasklet.class,
    RelevanceScoringBeans.class,
    RelevanceFloorTasklet.class,
    RelevanceFloor.class,
    RelevanceReportTasklet.class,
    RelevanceDistribution.class,
    LabelIngestion.class,
    RelevanceLabels.class,
    ClusteringTasklet.class,
    ClusteringBeans.class,
    DocumentClusters.class,
    // Stage 6a: arrangement.
    ArrangementTasklet.class,
    ArrangementGate.class,
    Clusters.class,
    DocumentTitles.class,
    // Stage 6b: generation.
    GenerationTasklet.class,
    GenerationModel.class,
    GenerationContextWindow.class,
    GenerationScriptedBeans.class,
    ClusterSynthesis.class,
    SynthesisDocs.class,
    LeadingChunks.class,
    // The ledger and the profile every stage reads and writes through.
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class
})
@interface CascadeSliceTest {}
