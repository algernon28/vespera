package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code pipeline} holds no rule, and so no stage's version names {@code pipeline} (ADR-222, amending
 * ADR-058; ADR-226, which moved the eight rules ADR-222 found into {@code extraction}, {@code embedding},
 * {@code synthesis} and {@code profile}).
 *
 * <p>A rule decides a verdict, a cache key, a cluster or text of the deliverable. No test can tell a rule
 * from the wiring around it, so this holds a listed allowance, read off the compiled classes as {@link
 * OnlyPipelineNamesSpringBatchTest} reads them:
 *
 * <ul>
 *   <li>every class of {@code pipeline} is on record, as holding a rule ({@link #RULES_ON_RECORD}, empty
 *       since ADR-226) or as holding none ({@link #HOLDING_NO_RULE}), so a class cannot be added without
 *       the question being answered for it, by a person, in the change that adds it;
 *   <li>the classes that name {@code VerdictKind}, and the ones that name {@code Deliverable}, are the
 *       ones on record, so a class that starts to write or choose a verdict, or to write the deliverable,
 *       fails;
 *   <li>the stages whose version names {@code pipeline} are the stages with a rule on record, which is
 *       none;
 *   <li>the classes that run content census, content redundancy, both runs of stage 5, arrangement and
 *       generation, none of whose versions names {@code pipeline}, name the types of the capability modules
 *       and of {@code profile} on record ({@link #COLLABORATORS_ON_RECORD}) and no other, since a rule
 *       added to one of them moves no run id.
 * </ul>
 *
 * <p><b>What it cannot see</b>: a rule added to a class already on record that names no verdict kind,
 * does not write the deliverable and, in one of the classes held closer, needs no type the class did not
 * name already: a condition on values it already holds, or a constant. Since ADR-226 no such rule moves a
 * run id in any stage, so whoever reads the change is the only one who asks the question, and the lists
 * are what tell them where to ask it. A type nested in a type already named is not seen as new, because
 * the name is read up to the {@code $}. {@code ledger}'s types are not listed: a verdict is written and
 * discarded by its {@code VerdictKind}, which the second test holds. The other classes those stages run
 * through, {@code OccurrenceReader}, {@code TaskletSteps}, {@code RunCompletion}, {@code
 * ReportedStatements} and {@code TimedStatement} among them, the pages and lines written for the operator,
 * the labelling classes, and the classes of stages 1 and 2, whose versions never named {@code pipeline},
 * are held by the first two tests alone. A class on record as holding no rule that has since been deleted
 * is not reported either, so that a change which removes one does not have to edit this list.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("353")
@Issue("479")
@Link(name = "ADR-222", url = Adr.A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT, type = "adr")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class PipelineHoldsOnlyTheRulesOnRecordTest {

    private static final String PIPELINE = "pipeline";

    private static final String IN_PIPELINE = "io.algernon.vespera.engine.pipeline.";

    /** How the verdict vocabulary is written inside a compiled class, in a reference or in a signature. */
    private static final String A_VERDICT_KIND = "io/algernon/vespera/engine/ledger/VerdictKind";

    /** How the class that writes the deliverable is written inside a compiled class that calls it. */
    private static final String THE_DELIVERABLE = "io/algernon/vespera/engine/deliverable/Deliverable";

    /**
     * ADR-222's allowance: the classes of {@code pipeline} that hold a rule, by the persisted name of the
     * stage whose output the rule shapes. Empty since ADR-226, which moved the eight ADR-222 found. A rule
     * added to {@code pipeline} is added here by a record, with its stage, and that stage names {@code
     * pipeline} again in the same change.
     */
    private static final Map<String, Set<String>> RULES_ON_RECORD = Map.of();

    /**
     * Every other class of {@code pipeline} ADR-222's survey read, each found to hold no rule, and the four
     * that held its eight rules until ADR-226 moved them: {@code SeedConversions}, {@code RelevanceFloor},
     * {@code RelevanceFloorTasklet} and {@code GenerationTasklet}.
     */
    private static final Set<String> HOLDING_NO_RULE = Set.of(
            // ADR-362 section 9: writes the JSON the analysis reports share the shape of, whole or a row at a time, and
            // reads a headline back for report.json; what a report says is its writer's, and it decides no verdict,
            // cache key, cluster or text of the deliverable.
            "AnalysisReportJson",
            "ArchiveGoneException",
            "ArrangementGate",
            "ArrangementReport",
            "ArrangementTasklet",
            "AutoLabelling",
            "ByteLevelReductionTasklet",
            "CensusTasklet",
            "ClusterSizeReport",
            // ADR-303: reads the page ceiling the profile holds and hands the tree a plain value; drawing is no stage's output.
            "ClusterPageByteCeiling",
            "ClusteringTasklet",
            "ConfidenceDistributionReport",
            // ADR-359 §5: reads the five Confluence values the profile holds and hands confluence plain values; the
            // publish's order of reading and its refusals are confluence's.
            "ConfluenceValuesOfTheProfile",
            "ContentCensusTasklet",
            "ConversionDispatch",
            "CorpusRootCheck",
            // ADR-279: refuses a second vespera run on a corpus root; keys and locks a file, and decides no verdict.
            "CorpusRootInUseException",
            "CorpusRootLock",
            "CorpusRootLockUnavailableException",
            "DegenerateOutputConfidenceFloor",
            // ADR-282: reads the word floor the profile holds, as ExtractionAttempt reads the attempt; the floor's rule is extraction's.
            "DegenerateOutputWordFloor",
            "DoclingControlConversion",
            "DoclingDidNotComeBackException",
            "DoclingKeepsDroppingConnectionsException",
            "DoclingRunsAnotherImageException",
            // ADR-262: renders similarity's shares of the documents; the rounding and the grouping are similarity's.
            // ADR-362 §1: asks the system whether a window can open; decides no verdict and shapes no stage's output.
            "DisplayPresence",
            "DocumentFrequencyReport",
            "DocumentOpening",
            // ADR-330 §1: writes effective-values.properties, what lies beneath the profile; it decides no verdict.
            "EffectiveValuesFile",
            "EmbeddingModelGate",
            "EmbeddingScoringTasklet",
            "ExtractionAttempt",
            "ExtractionCircuitBreaker",
            "ExtractionFaultRecorder",
            "ExtractionHealthCheckListener",
            "ExtractionItemProcessor",
            "ExtractionItemWriter",
            "ExtractionJobConfiguration",
            "ExtractionOutcome",
            "ExtractorStoppedAnsweringException",
            "FormatMixReport",
            "GenerationContextWindow",
            "GenerationModel",
            "GenerationTasklet",
            "InvocationAccount",
            // ADR-362: writes report.json from typed values the sampler of the job and the commands hand it; it decides no
            // verdict, cache key, cluster or text of the deliverable.
            "InvocationReport",
            "InvocationRuns",
            // ADR-265, ADR-362 §6: reads what the job holds in memory and hands the report its readings, and decides
            // nothing it shows.
            "JobSampler",
            "LabelFileReader",
            "LabelIngestion",
            // ADR-321: reads the deliverable's language the profile holds and hands synthesis and the tree a plain value;
            // the two languages, the default and what each says are synthesis's and deliverable's.
            "LanguageOfTheDeliverable",
            "MisshapenProfileRefusal",
            "NextAction",
            // ADR-294: writes and deletes what NextAction gives for the control panel, and decides none of it.
            "NextInvocationFile",
            "NoGenerationModelNamedException",
            "NoUpstreamRunException",
            "OccurrenceReader",
            "PendingConversions",
            // ADR-330 §9: reads the presets and the profile's resolution; what it resolves names the run.
            "Presets",
            "ProfileShapeCheck",
            "RedundancyBoilerplate",
            "RedundancyGate",
            "RedundancyJobConfiguration",
            "RedundancyResolutionTasklet",
            "RedundancySignatureItemWriter",
            "RelevanceFloor",
            "RelevanceFloorTasklet",
            "RelevanceLabelFile",
            "RelevanceLabellingReport",
            "RelevanceReportTasklet",
            "RelevanceScoreFloorValue",
            "RelevanceScoringTasklet",
            // ADR-319 §4: no question, answer or fault the generation run records is decided outside synthesis (ADR-274
            // §5); this decides only the step's finished_step row, and the standing line it lets a page carry is
            // drawing, which keys no run. A change to it moves no run id and reopens no run already finished.
            "RepeatedFaults",
            // ADR-362 section 5: draws report.html from report.json through a template; it decides no verdict, cache
            // key, cluster or text of the deliverable.
            "ReportHtml",
            "ReportPage",
            "ReportedStatements",
            "ReviewListListener",
            "ReviewListReport",
            "RunCompletion",
            "RunMint",
            "SeedConversions",
            "SeedCorpusComparisonReport",
            "SeedCorpusComparisonTasklet",
            "SeedExtractionItemProcessor",
            "SeedExtractionItemWriter",
            "SeedExtractionJobConfiguration",
            "SeedExtractionOutcome",
            "SeedGate",
            // ADR-333 §8: resolves the working directory and the port of vespera serve and starts the website's server;
            // it reads no row and writes no text of the deliverable.
            "ServeCommand",
            "ServiceScopeFailureException",
            // ADR-265: records which step ended at a shut gate; the gate's decision is the gate's.
            "ShutGates",
            "SidecarRecovery",
            // ADR-323: start the Docker engine and the sidecars, and say why where they cannot; none decides a
            // verdict, a cache key, a cluster or text of the deliverable.
            "ComposeFiles",
            "ComposeFilesUnwritableException",
            // ADR-326: say that a command's output file failed, apart from a program that cannot be started.
            "CommandOutputFileException",
            "CommandOutputFileUnusableException",
            "DockerEngineDidNotStartException",
            "DockerIsNotInstalledException",
            "HostSystem",
            "HttpSidecarHealth",
            "NoComposeFileBuildsTheImageException",
            "ProcessOutcome",
            "ProcessRunner",
            "SidecarHealth",
            "SidecarLockUnavailableException",
            "SidecarStartRefusedException",
            "SidecarStartSettings",
            "SidecarStarter",
            "SidecarsDidNotAnswerException",
            "SidecarsDidNotStartException",
            "SystemProcessRunner",
            // ADR-329: pull into Ollama a model it does not serve, and say why where it cannot; neither decides a verdict,
            // a cache key, a cluster or text of the deliverable.
            "ModelPull",
            "ModelPullSettings",
            "StageFiveGates",
            "StageModules",
            "StageProgress",
            "StageRuns",
            "StartUpIndexAnnouncement",
            "StatementProgress",
            "StepFailure",
            "StepFailureClosingLine",
            "StepNames",
            // ADR-364: the jar's own lines and the closing lines a tasklet records for after its step are logged on
            // the operator logger; neither decides a verdict, a cache key, a cluster or text of the deliverable.
            "OperatorLines",
            "RecordedClosingLines",
            "InvocationOutput",
            "InvocationOutputAppender",
            // ADR-250: the survivor list reads who survived and what was measured, and decides neither.
            "SurvivorListListener",
            "SurvivorListReport",
            "TaskletSteps",
            "TemporaryFilesInTheWorkingDirectory",
            "TimedStatement",
            // ADR-271: renders embedding's translation sets and rows; the pairing, the copy kept and the rounding are embedding's.
            "TranslationReport",
            // ADR-271: reads the survivors, their paths and languages, and writes what embedding's Translations decides.
            "TranslationTasklet",
            "UnrecordedOccurrences",
            "UpstreamRuns",
            "UsableSeedGate",
            "VesperaCli",
            "VesperaCommand",
            "VesperaJobConfiguration",
            "WorkingDirectoryInUseException",
            "WorkingDirectoryInUseRefusal",
            "WorkingDirectoryLock",
            "WorkingDirectoryOption",
            "WorkingDirectoryPreparer",
            // ADR-282: renders extraction's word counts by detected format; the bands and what a floor removes are extraction's.
            "WordCountDistributionReport");

    /**
     * The classes of {@code pipeline} whose compiled form names the verdict vocabulary. Stage 1's and
     * stage 2's hand on the verdict {@code corpus} and {@code extraction} decided, or discard an unfinished
     * attempt's; stage 4b's discards one too; {@code InvocationAccount} counts the verdicts under each kind
     * of the closed vocabulary; {@code RelevanceFloorTasklet} withdraws and writes the below-threshold
     * verdicts {@code embedding}'s {@code FloorReach} answers for, with the kind and the reason it hands
     * the step (ADR-226); {@code TranslationTasklet} withdraws and writes the translation-of verdicts
     * {@code embedding}'s {@code Translations} decides; and {@code RelevanceScoringTasklet} names
     * translation-of to leave the copies it removed unscored (ADR-271 §5).
     */
    private static final Set<String> NAMING_A_VERDICT_KIND = Set.of(
            "ByteLevelReductionTasklet",
            "ExtractionItemProcessor",
            "ExtractionItemWriter",
            "ExtractionJobConfiguration",
            "ExtractionOutcome",
            "InvocationAccount",
            "RedundancyResolutionTasklet",
            "RelevanceFloorTasklet",
            "RelevanceScoringTasklet",
            "TranslationTasklet");

    /**
     * The classes of {@code pipeline} that name the class the deliverable is written by: {@code
     * GenerationTasklet} calls it, and {@code NextAction} reads the name of its directory off it, to tell
     * the operator where the tree is, and writes nothing there.
     */
    private static final Set<String> NAMING_THE_DELIVERABLE = Set.of("GenerationTasklet", "NextAction");

    /**
     * The classes that run content census, content redundancy, both runs of stage 5, arrangement and
     * generation, with every type of {@code corpus}, {@code extraction}, {@code similarity}, {@code
     * embedding}, {@code synthesis} and {@code profile} each names, as {@code module.Type}: what it calls,
     * what it hands over and what comes back. Those stages' versions do not name {@code pipeline}, so each
     * class here was read as handing these their values and deciding nothing by them (ADR-222, the stages
     * with no rule in {@code pipeline}; ADR-226 for the seventeen classes of stage 5 and generation, whose
     * lists are those of the build that moved the rules out of them).
     */
    private static final Map<String, Set<String>> COLLABORATORS_ON_RECORD = Map.ofEntries(
            Map.entry(
                    "ContentCensusTasklet",
                    Set.of(
                            // ADR-282: the detected formats it hands extraction's word counts, a page at a time.
                            "corpus.DetectedFormat",
                            "corpus.DetectedFormats",
                            "extraction.ConfidenceDistribution",
                            "extraction.ExtractionStatement",
                            "extraction.ExtractionStatementProgress",
                            "extraction.WordCountDistribution",
                            "profile.Measurement",
                            "profile.Profile",
                            "profile.ProfileStore",
                            "similarity.DocumentFrequency",
                            "similarity.FrequencyProgress",
                            // ADR-330 §9: the window the run named, handed to the read of the shares.
                            "similarity.ShingleParameters",
                            "similarity.SimilarityStatement")),
            Map.entry("RedundancyGate", Set.of("profile.NumericValue", "profile.Profile", "profile.ProfileStore")),
            // ADR-330 §9: the window and the signature's numbers the run named, handed over.
            Map.entry("RedundancyBoilerplate", Set.of("similarity.BoilerplateShingles", "similarity.ShingleParameters")),
            Map.entry(
                    "RedundancySignatureItemWriter",
                    Set.of(
                            "similarity.MinHashParameters",
                            "similarity.RedundancySignatures",
                            "similarity.ShingleParameters")),
            Map.entry(
                    "RedundancyJobConfiguration",
                    Set.of(
                            "profile.NumericValue",
                            "similarity.RedundancySignatures",
                            "similarity.ShingleHashIndex",
                            "similarity.SimilarityStatement",
                            "similarity.SimilarityStatementProgress")),
            Map.entry(
                    "RedundancyResolutionTasklet",
                    Set.of(
                            "extraction.ExtractionMetrics",
                            "similarity.AlphanumericCounts",
                            "similarity.RedundancyResolution",
                            // ADR-330 §9: the window and the cuts the run named, handed over.
                            "similarity.RedundancyThresholds",
                            "similarity.ResolutionProgress",
                            "similarity.ShingleParameters",
                            "similarity.SimilarityStatement")),
            Map.entry(
                    "SeedConversions",
                    Set.of(
                            "corpus.BrokenCheck",
                            "corpus.DetectedFormat",
                            "corpus.DetectedSubtype",
                            "extraction.DoclingExtractor",
                            "extraction.DoclingResponse",
                            "extraction.ExtractorIdentity")),
            Map.entry(
                    "SeedExtractionItemProcessor",
                    Set.of(
                            "extraction.DoclingCallRejectedException",
                            "extraction.DoclingConnectionLostException",
                            // ADR-232: the seed pass asks UsableText about the conversion and reads no text itself.
                            "extraction.DoclingExtractor",
                            "extraction.DoclingResponse",
                            "extraction.ExtractionMetrics",
                            "extraction.ExtractorIdentity",
                            "extraction.UsableText")),
            Map.entry(
                    "SeedExtractionItemWriter",
                    Set.of("embedding.UnusableSeeds", "extraction.ExtractionCacheKeys", "extraction.ExtractionMetrics")),
            Map.entry("SeedExtractionJobConfiguration", Set.of()),
            Map.entry("SeedExtractionOutcome", Set.of("extraction.ExtractionMetrics")),
            Map.entry(
                    "SeedCorpusComparisonTasklet",
                    Set.of(
                            "embedding.EmbeddingStatement",
                            "embedding.EmbeddingStatementProgress",
                            "embedding.MeasuredForms",
                            "embedding.SeedCorpusComparison",
                            "extraction.ExtractionMetrics",
                            "extraction.MeasuredFormRow")),
            Map.entry("EmbeddingModelGate", Set.of("profile.Profile", "profile.ProfileStore", "profile.TextValue")),
            Map.entry(
                    "SeedGate", Set.of("corpus.Walk", "profile.Profile", "profile.ProfileStore", "profile.TextValue")),
            Map.entry("UsableSeedGate", Set.of()),
            // ADR-329: run by stage 5's scoring step and the generation step; it asks ADR-202's check and pulls, and
            // hands the stage the check's answer.
            Map.entry("ModelPull", Set.of("embedding.LocalOllamaModel", "embedding.OllamaClient")),
            Map.entry("StageFiveGates", Set.of()),
            Map.entry(
                    "EmbeddingScoringTasklet",
                    Set.of(
                            // ADR-329 §11: ADR-202's check is asked through ModelPull, which names it and its client.
                            "embedding.ChunkEmbedder",
                            "embedding.UnusableSeed",
                            "embedding.UnusableSeeds",
                            "extraction.Chunk",
                            "extraction.ChunkingRule",
                            "extraction.ChunkingRuleIdentity",
                            "extraction.DoclingExtractor",
                            "extraction.DoclingResponse",
                            "extraction.ExtractionCacheKeys",
                            "extraction.ExtractorIdentity",
                            "extraction.HybridChunker")),
            Map.entry(
                    "RelevanceScoringTasklet",
                    Set.of(
                            "embedding.ModelArtefact",
                            "embedding.RelevanceDistribution",
                            "embedding.RelevanceScoring",
                            "embedding.ScoringProgress",
                            "embedding.SeedChunks",
                            "embedding.UnusableSeed",
                            "embedding.UnusableSeeds",
                            "extraction.ChunkingRule",
                            "extraction.ChunkingRuleIdentity",
                            "extraction.ExtractionCacheKeys",
                            "extraction.HybridChunker")),
            Map.entry(
                    "TranslationTasklet",
                    Set.of(
                            "embedding.ClusteringProgress",
                            "embedding.ModelArtefact",
                            "embedding.RelevanceDistribution",
                            "embedding.TranslationOf",
                            "embedding.Translations",
                            "extraction.ChunkingRule",
                            "extraction.ChunkingRuleIdentity",
                            "extraction.ExtractionCacheKeys",
                            "extraction.ExtractionMetrics",
                            // Asked for its identity only, the key the vectors are stored under, as 5d and 5f ask.
                            "extraction.HybridChunker",
                            // The callback the languages are handed to, as the seed comparison reads them.
                            "extraction.MeasuredFormRow",
                            "profile.LanguagesValue",
                            "profile.Measurement",
                            "profile.NumericValue",
                            "profile.Profile",
                            "profile.ProfileStore")),
            Map.entry(
                    "RelevanceFloor",
                    Set.of(
                            "corpus.Walk",
                            "embedding.FloorReach",
                            "embedding.RelevanceLabels",
                            "profile.Profile",
                            "profile.ProfileStore",
                            "profile.TextValue")),
            Map.entry(
                    "RelevanceFloorTasklet",
                    Set.of(
                            "embedding.FloorReach",
                            "embedding.ModelArtefact",
                            "embedding.RelevanceDistribution",
                            "embedding.RelevanceScoring")),
            Map.entry(
                    "ClusteringTasklet",
                    Set.of(
                            "embedding.Clustering",
                            "embedding.ClusteringProgress",
                            // Read to ask which occurrences the run's clusters hold, and decides nothing by
                            // it: whether its recorded work is done again is wiring (ADR-230 section 2).
                            "embedding.DocumentCluster",
                            "embedding.DocumentClusters",
                            "embedding.ModelArtefact",
                            "embedding.RelevanceDistribution",
                            "embedding.RetainedEdgeSpread",
                            "extraction.ChunkingRule",
                            "extraction.ChunkingRuleIdentity",
                            "extraction.ExtractionCacheKeys",
                            "extraction.HybridChunker")),
            Map.entry(
                    "RelevanceReportTasklet",
                    Set.of(
                            "corpus.Walk",
                            "embedding.FloorReach",
                            "embedding.LabelledSpread",
                            "embedding.ModelArtefact",
                            "embedding.RelevanceDistribution",
                            "embedding.RelevanceLabel",
                            "embedding.RelevanceLabels",
                            "extraction.DoclingExtractor",
                            "extraction.ExtractionCacheKeys",
                            "extraction.ExtractorIdentity",
                            "extraction.HybridChunker",
                            "profile.Measurement",
                            "profile.Profile",
                            "profile.ProfileStore",
                            "profile.TextValue")),
            Map.entry(
                    "GenerationTasklet",
                    Set.of(
                            "corpus.CodeSets",
                            "corpus.DetectedFormat",
                            "corpus.DetectedFormats",
                            "corpus.Walk",
                            "embedding.DocumentCluster",
                            "embedding.DocumentClusters",
                            "embedding.EmbeddingStatement",
                            // ADR-329 §11: ADR-202's check is asked through ModelPull, which names it and its client.
                            "embedding.RelevanceScoring",
                            "extraction.Chunk",
                            "extraction.DocumentPicture",
                            "extraction.DocumentPictures",
                            "extraction.ExtractionCacheKeys",
                            "extraction.ExtractorIdentity",
                            "extraction.LeadingChunks",
                            "extraction.PicturePlace",
                            "deliverable.ArrangedSurvivors",
                            // ADR-303: the page ceiling, read through ClusterPageByteCeiling and handed to the tree.
                            "deliverable.ClusterPageCeiling",
                            "deliverable.CodeSetNames",
                            "deliverable.Deliverable",
                            "deliverable.DeliverableProgress",
                            "deliverable.DeliverableProvenance",
                            "deliverable.DeliverableRoot",
                            "deliverable.ListedCodeFile",
                            "deliverable.ListedCodeSet",
                            "deliverable.ListedCodeSets",
                            "deliverable.ListedPartition",
                            "deliverable.ListedPicture",
                            "deliverable.ListedPicturePlace",
                            "deliverable.ListedSurvivor",
                            "deliverable.NamedValue",
                            // ADR-333 §1: the plugin beans, handed to Deliverable.writeTo unread.
                            "deliverable.Plugin",
                            "deliverable.SurvivorPictures",
                            // ADR-303: the page ceiling's value, handed to ClusterPageByteCeiling's reading.
                            "profile.NumericValue",
                            "profile.Profile",
                            "profile.ProfileStore",
                            "synthesis.ArrangedCluster",
                            "synthesis.ArrangedPartition",
                            "synthesis.ClusterExemplars",
                            "synthesis.ClusterFault",
                            "synthesis.ClusterFaultKind",
                            "synthesis.ClusterFaults",
                            "synthesis.ClusterGeneration",
                            "synthesis.ClusterMaterial",
                            "synthesis.ClusterSlot",
                            "synthesis.Clusters",
                            // ADR-321: the deliverable's language, read through LanguageOfTheDeliverable and handed to
                            // the writing and to the tree as a plain value.
                            "synthesis.DeliverableLanguage",
                            "synthesis.GenerationOutcome",
                            "synthesis.GenerationProgress",
                            "synthesis.OpeningChunk",
                            "synthesis.OpeningText",
                            "synthesis.RecordedCluster",
                            "synthesis.SynthesisDocs",
                            "synthesis.SynthesisStatement",
                            "synthesis.Unwritten")),
            Map.entry(
                    "ArrangementTasklet",
                    Set.of(
                            "corpus.Walk",
                            "embedding.DocumentCluster",
                            "embedding.DocumentClusters",
                            "embedding.RelevanceScoring",
                            "embedding.ScoringProgress",
                            "extraction.DocumentTitles",
                            "extraction.ExtractionCacheKeys",
                            "synthesis.ArrangedCluster",
                            "synthesis.Arrangement",
                            "synthesis.ClusterLabel",
                            "synthesis.ClusterSlot",
                            "synthesis.ClusteredDocument",
                            "synthesis.Clusters",
                            "synthesis.DocumentTitle",
                            "synthesis.LabelledCluster",
                            "synthesis.LeadDocument",
                            "synthesis.Partition",
                            "synthesis.RecordedCluster")),
            Map.entry("ArrangementGate", Set.of("profile.Profile", "profile.ProfileStore", "profile.TextValue")));

    @Test
    @Story("The code that runs the stages decides nothing that is not on record")
    @DisplayName("Every class of the code that runs the stages has been asked whether it decides what is written, and its answer is on record")
    void everyClassOfPipelineIsOnRecord() throws Exception {
        Set<String> shipped = classesOfPipeline(names -> true);
        Set<String> holdingARule = new TreeSet<>();
        RULES_ON_RECORD.values().forEach(holdingARule::addAll);
        Set<String> notOnRecord = new TreeSet<>(shipped);
        notOnRecord.removeAll(HOLDING_NO_RULE);
        notOnRecord.removeAll(holdingARule);

        claim(
                "the classes read include the table of what each stage is identified by and the class that"
                        + " writes the final documents, so an empty answer below is not an empty scan",
                () -> assertThat(shipped).contains("StageModules", "GenerationTasklet"));
        claim(
                "no class of the code that runs the stages is missing from the record. A class named here"
                        + " was added without anyone answering, for it, whether a change to it could alter"
                        + " which file is removed and why, a key something is cached under, which documents"
                        + " are put together, or a sentence of the final documents. Answer that, and list"
                        + " the class as deciding nothing, or record what it decides and the stage it"
                        + " decides it for",
                () -> assertThat(notOnRecord)
                        .as("the classes of the code that runs the stages that are on record neither way")
                        .isEmpty());
    }

    @Test
    @Story("The code that runs the stages decides nothing that is not on record")
    @DisplayName("The classes that can write a removal or the final documents are the ones on record")
    void theClassesNamingAVerdictKindOrTheDeliverableAreOnRecord() throws Exception {
        Set<String> namingAKind =
                classesOfPipeline(names -> names.stream().anyMatch(name -> name.contains(A_VERDICT_KIND)));
        Set<String> namingTheDeliverable = classesOfPipeline(names -> names.stream()
                .anyMatch(name -> name.equals(THE_DELIVERABLE) || name.contains("L" + THE_DELIVERABLE + ";")));

        claim(
                "the classes that name the kinds of removal are the ten on record. One more is a class"
                        + " that has started to write a removal, to choose its kind or to branch on one,"
                        + " and whether it decides anything has to be answered and recorded; one fewer is"
                        + " an entry to take off the record",
                () -> assertThat(namingAKind).containsExactlyInAnyOrderElementsOf(NAMING_A_VERDICT_KIND));
        claim(
                "and two classes name the code that writes the final documents: the stage that writes the"
                        + " final documents, which calls it, and the closing line, which reads only the"
                        + " name of the folder they are written in. A third would be handing that code"
                        + " something to write that nobody recorded",
                () -> assertThat(namingTheDeliverable).containsExactlyInAnyOrderElementsOf(NAMING_THE_DELIVERABLE));
    }

    /**
     * The top-level classes of {@code pipeline} whose compiled form, or that of a class nested in them,
     * holds names that {@code hold}; {@code package-info} is no class and is left out.
     */
    private static Set<String> classesOfPipeline(Predicate<List<String>> hold) throws Exception {
        Set<String> classes = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.namesByClass().entrySet()) {
            if (PIPELINE.equals(ShippedClasses.moduleOf(shipped.getKey())) && hold.test(shipped.getValue())) {
                String topLevel = shipped.getKey().substring(IN_PIPELINE.length()).split("\\$")[0];
                if (!"package-info".equals(topLevel)) {
                    classes.add(topLevel);
                }
            }
        }
        return classes;
    }
}
