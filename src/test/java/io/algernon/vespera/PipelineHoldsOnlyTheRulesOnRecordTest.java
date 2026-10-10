package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code pipeline} holds the eight rules ADR-222 records and no other, and a stage's version names
 * {@code pipeline} exactly while one of them is that stage's (ADR-222, amending ADR-058).
 *
 * <p>A rule decides a verdict, a cache key, a cluster or text of the deliverable. No test can tell a rule
 * from the wiring around it, so this holds a listed allowance, read off the compiled classes as {@link
 * OnlyPipelineNamesSpringBatchTest} reads them:
 *
 * <ul>
 *   <li>every class of {@code pipeline} is on record, as holding a rule ({@link #RULES_ON_RECORD}) or as
 *       holding none ({@link #HOLDING_NO_RULE}), so a class cannot be added without the question being
 *       answered for it, by a person, in the change that adds it;
 *   <li>the classes that name {@code VerdictKind}, and the ones that name {@code Deliverable}, are the
 *       ones on record, so a class that starts to write or choose a verdict, or to write the deliverable,
 *       fails;
 *   <li>the stages whose version names {@code pipeline} are the stages with a rule on record;
 *   <li>the eight classes of content census, content redundancy and arrangement, whose versions do not
 *       name {@code pipeline}, name the types of the capability modules and of {@code profile} on record
 *       ({@link #COLLABORATORS_ON_RECORD}) and no other, since a rule added to one of them moves no run
 *       id.
 * </ul>
 *
 * <p><b>What it cannot see</b>: a rule added to a class already on record that names no verdict kind,
 * does not write the deliverable and, in one of those eight classes, needs no type the class did not name
 * already. A type nested in a type already named is not seen as new, because the name is read up to the
 * {@code $}. {@code ledger}'s types are not listed for the eight: a verdict is written and discarded by
 * its {@code VerdictKind}, which the second test holds. The other classes those three stages run through,
 * {@code OccurrenceReader}, {@code TaskletSteps}, {@code RunCompletion}, {@code ReportedStatements} and
 * {@code TimedStatement} among them, and the classes of stages 1 and 2, whose versions never named {@code
 * pipeline}, are held by the first two tests alone. A class on record as holding no rule that has since
 * been deleted is not reported either, so that a change which removes one does not have to edit this
 * list.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("353")
@Link(name = "ADR-222", url = Adr.A_STAGE_NAMES_PIPELINE_ONLY_WHILE_PIPELINE_HOLDS_A_RULE_OF_IT, type = "adr")
@Link(name = "ADR-058", url = Adr.IMPLEMENTATION_VERSION_IS_THE_LAST_COMMIT, type = "adr")
class PipelineHoldsOnlyTheRulesOnRecordTest {

    private static final String PIPELINE = "pipeline";

    private static final String IN_PIPELINE = "io.algernon.vespera.pipeline.";

    /** How the verdict vocabulary is written inside a compiled class, in a reference or in a signature. */
    private static final String A_VERDICT_KIND = "io/algernon/vespera/ledger/VerdictKind";

    /** How the class that writes the deliverable is written inside a compiled class that calls it. */
    private static final String THE_DELIVERABLE = "io/algernon/vespera/synthesis/Deliverable";

    /**
     * ADR-222's allowance: the classes of {@code pipeline} that hold a rule, by the persisted name of the
     * stage whose output the rule shapes. {@code SeedConversions} holds rule 1, {@code RelevanceFloor} rule
     * 2, {@code RelevanceFloorTasklet} rule 3 and {@code GenerationTasklet} rules 4 to 8.
     */
    private static final Map<String, Set<String>> RULES_ON_RECORD = Map.of(
            "seed-measurement", Set.of("SeedConversions"),
            "embedding-scoring", Set.of("RelevanceFloor", "RelevanceFloorTasklet"),
            "generation", Set.of("GenerationTasklet"));

    /** Every other class of {@code pipeline} ADR-222's survey read, each found to hold no rule. */
    private static final Set<String> HOLDING_NO_RULE = Set.of(
            "ArchiveGoneException",
            "ArrangementGate",
            "ArrangementReport",
            "ArrangementTasklet",
            "AutoLabelling",
            "ByteLevelReductionTasklet",
            "CensusTasklet",
            "ClusterSizeReport",
            "ClusteringTasklet",
            "ConfidenceDistributionReport",
            "ContentCensusTasklet",
            "ConversionDispatch",
            "CorpusRootCheck",
            "DegenerateOutputConfidenceFloor",
            "DoclingControlConversion",
            "DoclingDidNotComeBackException",
            "DoclingKeepsDroppingConnectionsException",
            "DoclingRunsAnotherImageException",
            "DocumentOpening",
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
            "InvocationAccount",
            "InvocationRuns",
            "LabelFileReader",
            "LabelIngestion",
            "MisshapenProfileRefusal",
            "NextAction",
            "NoGenerationModelNamedException",
            "NoUpstreamRunException",
            "OccurrenceReader",
            "PendingConversions",
            "ProfileShapeCheck",
            "RedundancyBoilerplate",
            "RedundancyGate",
            "RedundancyJobConfiguration",
            "RedundancyResolutionTasklet",
            "RedundancySignatureItemWriter",
            "RelevanceLabelFile",
            "RelevanceLabellingReport",
            "RelevanceReportTasklet",
            "RelevanceScoreFloorValue",
            "RelevanceScoringTasklet",
            "ReportPage",
            "ReportedStatements",
            "ReviewListListener",
            "ReviewListReport",
            "RunCompletion",
            "RunMint",
            "SeedCorpusComparisonReport",
            "SeedCorpusComparisonTasklet",
            "SeedExtractionItemProcessor",
            "SeedExtractionItemWriter",
            "SeedExtractionJobConfiguration",
            "SeedExtractionOutcome",
            "SeedGate",
            "ServiceScopeFailureException",
            "SidecarRecovery",
            "StageFiveGates",
            "StageModules",
            "StageProgress",
            "StageRuns",
            "StartUpIndexAnnouncement",
            "StatementProgress",
            "StepFailure",
            "StepNames",
            "TaskletSteps",
            "TemporaryFilesInTheWorkingDirectory",
            "TimedStatement",
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
            "WorkingDirectoryPreparer");

    /**
     * The classes of {@code pipeline} whose compiled form names the verdict vocabulary. Stage 1's and
     * stage 2's hand on the verdict {@code corpus} and {@code extraction} decided, or discard an unfinished
     * attempt's; stage 4b's discards one too; {@code InvocationAccount} counts the verdicts under each kind
     * of the closed vocabulary; and {@code RelevanceFloorTasklet} is rule 3.
     */
    private static final Set<String> NAMING_A_VERDICT_KIND = Set.of(
            "ByteLevelReductionTasklet",
            "ExtractionItemProcessor",
            "ExtractionItemWriter",
            "ExtractionJobConfiguration",
            "ExtractionOutcome",
            "InvocationAccount",
            "RedundancyResolutionTasklet",
            "RelevanceFloorTasklet");

    /**
     * The classes of {@code pipeline} that name the class the deliverable is written by: {@code
     * GenerationTasklet} calls it, and {@code NextAction} reads the name of its directory off it, to tell
     * the operator where the tree is, and writes nothing there.
     */
    private static final Set<String> NAMING_THE_DELIVERABLE = Set.of("GenerationTasklet", "NextAction");

    /**
     * A type of one of the five capability modules or of {@code profile}, as a compiled class writes it,
     * read up to the first character that is not a letter, a digit or an underscore: a nested type answers
     * for the type it is nested in.
     */
    private static final Pattern A_CAPABILITY_TYPE = Pattern.compile(
            "io/algernon/vespera/(corpus|extraction|similarity|embedding|synthesis|profile)/(\\w+)");

    /**
     * The classes that run content census, content redundancy and arrangement, with every type of {@code
     * corpus}, {@code extraction}, {@code similarity}, {@code embedding}, {@code synthesis} and {@code
     * profile} each names, as {@code module.Type}: what it calls, what it hands over and what comes back. Those stages' versions
     * do not name {@code pipeline}, so each class here was read as handing these their values and deciding
     * nothing by them (ADR-222, the stages with no rule in {@code pipeline}).
     */
    private static final Map<String, Set<String>> COLLABORATORS_ON_RECORD = Map.of(
            "ContentCensusTasklet",
                    Set.of(
                            "extraction.ConfidenceDistribution",
                            "extraction.ExtractionStatement",
                            "extraction.ExtractionStatementProgress",
                            "profile.Measurement",
                            "profile.Profile",
                            "profile.ProfileStore",
                            "similarity.DocumentFrequency",
                            "similarity.FrequencyProgress",
                            "similarity.SimilarityStatement"),
            "RedundancyGate", Set.of("profile.NumericValue", "profile.Profile", "profile.ProfileStore"),
            "RedundancyBoilerplate", Set.of("similarity.BoilerplateShingles"),
            "RedundancySignatureItemWriter", Set.of("similarity.RedundancySignatures"),
            "RedundancyJobConfiguration",
                    Set.of(
                            "profile.NumericValue",
                            "similarity.RedundancySignatures",
                            "similarity.ShingleHashIndex",
                            "similarity.SimilarityStatement",
                            "similarity.SimilarityStatementProgress"),
            "RedundancyResolutionTasklet",
                    Set.of(
                            "extraction.ExtractionMetrics",
                            "similarity.AlphanumericCounts",
                            "similarity.RedundancyResolution",
                            "similarity.ResolutionProgress",
                            "similarity.SimilarityStatement"),
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
                            "synthesis.RecordedCluster"),
            "ArrangementGate", Set.of("profile.Profile", "profile.ProfileStore", "profile.TextValue"));

    @Test
    @Story("A stage is identified by the code that runs the stages only while that code decides something for it")
    @DisplayName("The classes that run the three stages no longer identified by this code call only the code on record for them")
    void theClassesOfTheFreedStagesNameOnlyTheCollaboratorsOnRecord() throws Exception {
        Map<String, Set<String>> named = new TreeMap<>();
        COLLABORATORS_ON_RECORD.keySet().forEach(held -> named.put(held, new TreeSet<>()));
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.namesByClass().entrySet()) {
            if (!PIPELINE.equals(ShippedClasses.moduleOf(shipped.getKey()))) {
                continue;
            }
            Set<String> ofItsClass = named.get(shipped.getKey().substring(IN_PIPELINE.length()).split("\\$")[0]);
            if (ofItsClass == null) {
                continue;
            }
            for (String name : shipped.getValue()) {
                Matcher type = A_CAPABILITY_TYPE.matcher(name);
                while (type.find()) {
                    ofItsClass.add(type.group(1) + "." + type.group(2));
                }
            }
        }

        Map<String, Set<String>> onRecord = new TreeMap<>();
        COLLABORATORS_ON_RECORD.forEach((held, types) -> onRecord.put(held, new TreeSet<>(types)));

        claim(
                "each of the eight classes names the measuring, judging and writing code on record for it and"
                        + " no other. They run stages that are not done again when one of them changes, so"
                        + " nothing in them may decide what those stages write. A type named beyond the"
                        + " record is a new collaborator, often met when two changes are merged: read what"
                        + " the class now does with it. If it only hands it values or acts on its answer,"
                        + " add the type to that class's list. If the class decides something by it, move"
                        + " that decision into the collaborator's own code, or record it and have the stage"
                        + " identified by this code again. A type on record that is no longer named comes"
                        + " off the list",
                () -> assertThat(named)
                        .as("the types of the six modules each class names, by class")
                        .containsExactlyInAnyOrderEntriesOf(onRecord));
    }

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
        claim(
                "every class on record as deciding something is still there: one that has gone took its"
                        + " decision with it, so its entry goes too, and its stage stops being identified by"
                        + " this code if that was the last one",
                () -> assertThat(shipped).containsAll(holdingARule));
        claim(
                "and no class is on record both as deciding something and as deciding nothing",
                () -> assertThat(holdingARule).doesNotContainAnyElementsOf(HOLDING_NO_RULE));
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
                "the classes that name the kinds of removal are the eight on record. One more is a class"
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

    @Test
    @Story("A stage is identified by the code that runs the stages only while that code decides something for it")
    @DisplayName("The stages identified by the code that runs the stages are exactly the three it still decides something for")
    void aStageNamesPipelineExactlyWhileARuleOfItIsOnRecord() throws Exception {
        Set<String> namingPipeline = new TreeSet<>();
        Set<String> stagesRead = new TreeSet<>();
        Class<?> table = Class.forName(
                IN_PIPELINE + "StageModules", true, Thread.currentThread().getContextClassLoader());
        Method stage = table.getDeclaredMethod("stage");
        Method modules = table.getDeclaredMethod("modules");
        stage.setAccessible(true);
        modules.setAccessible(true);
        for (Object row : table.getEnumConstants()) {
            String name = (String) stage.invoke(row);
            stagesRead.add(name);
            if (((List<?>) modules.invoke(row)).contains(PIPELINE)) {
                namingPipeline.add(name);
            }
        }

        claim(
                "the table read holds the stages the record names, so the claim below is about them",
                () -> assertThat(stagesRead).containsAll(RULES_ON_RECORD.keySet()).contains("content-census"));
        claim(
                "the stages identified by the code that runs the stages are seed measurement, embedding and"
                        + " scoring, and generation: the three that code still decides something for. A"
                        + " stage named here beyond those is done again after every change to a progress"
                        + " line or a command-line option, for nothing; one of the three missing would keep"
                        + " its old results after a change to what decides them",
                () -> assertThat(namingPipeline).containsExactlyInAnyOrderElementsOf(RULES_ON_RECORD.keySet()));
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
