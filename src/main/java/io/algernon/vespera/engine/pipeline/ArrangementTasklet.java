package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.embedding.Clustering;
import io.algernon.vespera.engine.embedding.DocumentCluster;
import io.algernon.vespera.engine.embedding.DocumentClusters;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.RelevanceScoring;
import io.algernon.vespera.engine.embedding.ScoringProgress;
import io.algernon.vespera.engine.embedding.SeedChunks;
import io.algernon.vespera.engine.embedding.SeedPassage;
import io.algernon.vespera.engine.embedding.SeedVectors;
import io.algernon.vespera.engine.embedding.Topic;
import io.algernon.vespera.engine.embedding.Topics;
import io.algernon.vespera.engine.embedding.UnusableSeeds;
import io.algernon.vespera.engine.extraction.Chunk;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.DocumentTitles;
import io.algernon.vespera.engine.extraction.ExtractionCacheKeys;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.extraction.LeadingChunks;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceFacts;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.Arrangement;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.ClusteredDocument;
import io.algernon.vespera.engine.synthesis.Clusters;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.GivenName;
import io.algernon.vespera.engine.synthesis.LabelledCluster;
import io.algernon.vespera.engine.synthesis.LeadDocument;
import io.algernon.vespera.engine.synthesis.NameCall;
import io.algernon.vespera.engine.synthesis.NameOrigin;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.TopicClusters;
import io.algernon.vespera.engine.synthesis.TopicFallbackName;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.algernon.vespera.engine.synthesis.TopicNames;
import io.algernon.vespera.engine.synthesis.TopicNaming;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6a (ADR-105, ADR-106, ADR-110, ADR-112, ADR-409, #175): the clusters stage 5 formed are given a name,
 * a size and a place, under a run of their own, beneath the topics and subtopics that are named first.
 *
 * <p><b>It adds a level rather than restating one.</b> Stage 5 records which documents share a
 * cluster, and records it without the cluster having a row anywhere. What this writes is the row that
 * did not exist; not one membership row is written again.
 *
 * <p><b>It judges nothing.</b> No verdict of any kind is written, including a passing one: ADR-049
 * says every stage writes a row per occurrence and no stage does, and that gap deserves its own
 * decision rather than being closed as a side effect of the one stage in the cascade that removes
 * nothing.
 *
 * <p><b>The arrangement is total, and nothing here papers over a gap in it.</b> Every survivor has a
 * score, a topic and a cluster ordinal, so a cluster whose members carry no score is a broken
 * invariant rather than a case to accommodate — the rows go to {@code synthesis} exactly as they were
 * read, and it stops, in the same way and for the same reason {@code scoreAndRecord} refuses to score
 * an unvectored survivor. A defensive miscellaneous bucket here would turn that into a silently
 * rendered section of the deliverable.
 *
 * <p><b>The topics are the ones stage 5 kept, and are named first</b> (ADR-409 sections 5 and 6): each topic and then
 * each crowded topic's subtopics are named, a name recorded under the run as soon as it is given and read back, and
 * never asked for again, where the run already holds it. A serving engine that gives no answer fails the step without
 * throwing, so that the names given before it are kept.
 *
 * <p><b>One topic at a time</b> (ADR-223 section 3, ADR-409 section 13). The topics are ordered from their sizes and
 * their keys alone; then each is read, arranged, labelled, recorded, read back and written to the page, and let go
 * before the next is read.
 *
 * <p>Gated exactly as the clustering step before it is, and for the same reasons: with no model
 * named, no seed folder or no usable seed, no score exists, so there is nothing to arrange.
 */
@Component
@StepScope
class ArrangementTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ArrangementTasklet.class);

    /** Stage 6a's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 6a (arrangement)";

    /**
     * The page's name in the working directory, beside the profile and the database (ADR-054).
     *
     * <p>Written inside this step rather than by a report step of its own, following
     * {@code cluster-sizes.html} inside {@link ClusteringTasklet}: it is this step's own gate page and
     * not a report over somebody else's run (ADR-110).
     */
    static final String ARRANGEMENT_FILE_NAME = "arrangement.html";

    /** The topics report's page, written beside it once the names stand (ADR-416 section 1). */
    static final String TOPICS_FILE_NAME = "topics.html";

    /** The topics analysis report's JSON, written beside {@link #TOPICS_FILE_NAME} (ADR-362 section 9). */
    static final String TOPICS_JSON_FILE_NAME = "topics.json";

    /** Its JSON, beside it, written from the same values (ADR-362 section 9). */
    static final String ARRANGEMENT_JSON_FILE_NAME = "arrangement.json";

    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final DocumentClusters documentClusters;
    private final RelevanceScoring relevanceScoring;
    private final Clusters clusters;
    private final Topics topicLedger;
    private final TopicNames topicNames;
    private final Clustering clustering;
    private final Ledger ledger;
    private final ExtractionCacheKeys cacheKeys;
    private final DocumentTitles documentTitles;
    private final LeadingChunks leadingChunks;
    private final Path root;
    private final Path workingDirectory;
    private final ModelPull modelPull;
    private final TopicNaming topicNaming;
    private final HybridChunker hybridChunker;
    private final RelevanceDistribution relevanceDistribution;
    private final UnusableSeeds unusableSeeds;
    private final RecordedClosingLines recordedClosingLines;

    /** Where each name is logged, apart from the operator lines (ADR-407 section 9, ADR-409 section 6). */
    private static final Logger NAMES = LoggerFactory.getLogger(ArrangementTasklet.class.getName() + ".names");

    ArrangementTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            DocumentClusters documentClusters,
            RelevanceScoring relevanceScoring,
            Clusters clusters,
            Topics topicLedger,
            TopicNames topicNames,
            Clustering clustering,
            Ledger ledger,
            JdbcTemplate jdbcTemplate,
            DocumentTitles documentTitles,
            LeadingChunks leadingChunks,
            ModelPull modelPull,
            TopicNaming topicNaming,
            UnusableSeeds unusableSeeds,
            HybridChunker hybridChunker,
            RelevanceDistribution relevanceDistribution,
            RecordedClosingLines recordedClosingLines,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.modelPull = modelPull;
        this.topicNaming = topicNaming;
        this.hybridChunker = hybridChunker;
        this.relevanceDistribution = relevanceDistribution;
        this.unusableSeeds = unusableSeeds;
        this.recordedClosingLines = recordedClosingLines;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.documentClusters = documentClusters;
        this.relevanceScoring = relevanceScoring;
        this.clusters = clusters;
        this.topicLedger = topicLedger;
        this.topicNames = topicNames;
        this.clustering = clustering;
        this.ledger = ledger;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.documentTitles = documentTitles;
        this.leadingChunks = leadingChunks;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "the arrangement step", stageRuns, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            ShutGates.in(chunkContext).record(StepNames.ARRANGEMENT, preamble.key().orElse(null));
            return RepeatStatus.FINISHED;
        }

        RunId scoring = stageRuns.embeddingScoring();
        // The topics the profile listed when the scoring run was minted, in the list's order, none where they are
        // inferred (ADR-429 section 4): their places are the topics' places on the page and in the tree.
        List<Topics.ListedName> listedNames = TimedStatement.of(
                STAGE, "reading", "read", "the listed topics", () -> topicLedger.listedUnder(scoring));
        Map<TopicKey, Integer> listedPlaces = new LinkedHashMap<>();
        listedNames.forEach(listed -> listedPlaces.put(keyOf(listed.key()), listed.place()));
        // The topics that hold a clustered survivor, and how many documents sit under each: a count a topic, so that
        // the topics are ordered without reading a document of any of them (ADR-223 section 3, ADR-409 section 13).
        // Made on both branches, before the step knows which it is on.
        Map<io.algernon.vespera.engine.embedding.TopicKey, Integer> sizes = TimedStatement.of(
                STAGE, "reading", "read", "the topics", () -> documentClusters.sizesOfTopics(scoring));
        Map<TopicKey, Integer> memberCounts = new LinkedHashMap<>();
        sizes.forEach((topic, members) -> {
            if (members > 0) {
                memberCounts.put(keyOf(topic), members);
            }
        });
        if (memberCounts.isEmpty()) {
            LOG.info(
                    "the arrangement step is gated: no survivor was grouped under {}, so there is nothing"
                            + " to arrange.",
                    scoring.value());
            ShutGates.in(chunkContext).record(StepNames.ARRANGEMENT);
            return RepeatStatus.FINISHED;
        }
        long members = memberCounts.values().stream().mapToLong(Integer::longValue).sum();
        StageProgress scoresRead = StageProgress.over("Stage 6a (arrangement, scores read)", members);
        StageProgress gathered = StageProgress.over("Stage 6a (arrangement, members gathered)", members);

        // The naming model is pulled where Ollama does not serve it, once the gates are open and before the run is
        // resolved, as generation pulls its own (ADR-407 section 1, ADR-329). A blank value stops the invocation
        // here, naming vespera.naming.model, where the run would be minted (section 2).
        // The language is read first, so that a value that is no language stops the invocation before the pull.
        DeliverableLanguage language = stageRuns.deliverableLanguage();
        String namingModel = stageRuns.namingModelName();
        Optional<String> refusal = modelPull.refusalOf(STAGE, namingModel);
        if (refusal.isPresent()) {
            stopOnANamingModelNotServedHere(contribution, chunkContext, namingModel, refusal.get());
            return RepeatStatus.FINISHED;
        }

        RunId arrangement = stageRuns.arrangement();

        // The page an approval copies its name off is written every time this invocation arrives at an
        // arrangement, from the rows recorded under it, including when they were recorded by an
        // earlier invocation (ADR-154 §2, amending ADR-115's "a skipped step writes no report" for this
        // one page) -- kept as its own alreadyRecorded action (ADR-157 §5).
        return TaskletSteps.once(
                ledger,
                arrangement,
                StepNames.ARRANGEMENT,
                () -> {
                    LOG.info("the arrangement step was already recorded under run {}", arrangement.value());
                    List<TopicKey> recordedTopics = TimedStatement.of(
                            STAGE, "reading", "read", "the topics of the arrangement",
                            () -> clusters.keysOf(arrangement));
                    writeTopicsAnalysisReport(scoring, arrangement, preamble, memberCounts, listedNames);
                    writePage(
                            scoring,
                            arrangement,
                            inTheListsOrder(recordedTopics, listedPlaces),
                            listedPlaces,
                            scoresRead,
                            gathered,
                            false);
                },
                () -> clusters.discardForRun(arrangement),
                () -> {
                    // The topics are named first, before anything is ordered, labelled or recorded (ADR-407 section 1);
                    // a name is recorded as soon as it is given, and one the run already holds is read back.
                    Optional<String> stopped =
                            nameTheTopics(scoring, arrangement, preamble, namingModel, language, listedNames);
                    if (stopped.isPresent()) {
                        // The step fails without throwing, so that the names given before it are kept (ADR-409
                        // section 6): an exception would roll back the step's transaction, the run among it.
                        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
                        recordedClosingLines.record(stepExecution, stopped.get());
                        stepExecution.setStatus(BatchStatus.FAILED);
                        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(stopped.get()));
                        return false;
                    }
                    List<TopicKey> inOrder = listedPlaces.isEmpty()
                            ? Arrangement.topicsInOrder(memberCounts)
                            : inTheListsOrder(memberCounts.keySet(), listedPlaces);
                    writeTopicsAnalysisReport(scoring, arrangement, preamble, memberCounts, listedNames);
                    int clustersArranged =
                            writePage(scoring, arrangement, inOrder, listedPlaces, scoresRead, gathered, true);
                    LOG.info(
                            "The arrangement step finished under {}: {} topic(s), {} cluster(s), {}"
                                    + " document(s)",
                            arrangement.value(),
                            inOrder.size(),
                            clustersArranged,
                            members);
                    return true;
                });
    }

    /**
     * Writes {@code topics.json} and then {@code topics.html} once every topic's name stands under {@code arrangement}
     * (ADR-416 section 1): one row a topic in the arrangement's order, then the topics no survivor is placed under in
     * key order, each with its seeds, and the seeds feeding no topic. Each read is of one topic at a time, none held past
     * the topic's row, and a topic's linked survivors are counted a page of 1,000 at a time (ADR-416 section 8).
     */
    private void writeTopicsAnalysisReport(
            RunId scoring,
            RunId arrangement,
            StageFiveGates.Preamble preamble,
            Map<TopicKey, Integer> memberCounts,
            List<Topics.ListedName> listedNames) {
        if (!listedNames.isEmpty()) {
            writeTheListedTopicsAnalysisReport(scoring, memberCounts, listedNames);
            return;
        }
        Map<io.algernon.vespera.engine.embedding.TopicKey, Integer> standing = TimedStatement.of(
                STAGE, "reading", "read", "the topics kept", () -> topicLedger.passageCountsUnder(scoring));
        Map<OccurrenceId, SeedChunks> seedChunks = TaskletSteps.seedContentHashes(
                STAGE,
                seeds -> StageProgress.over("Stage 6a (arrangement, seed passages counted)", seeds),
                preamble.seedWalk().orElseThrow(),
                stageRuns.seedMeasurement(),
                ledger,
                unusableSeeds,
                cacheKeys,
                hybridChunker);
        Map<String, SeedChunks> chunksByPath = new HashMap<>();
        ledger.occurrences().pathsOf(seedChunks.keySet())
                .forEach((occurrence, path) -> chunksByPath.put(path.value(), seedChunks.get(occurrence)));
        long seedPassages = Topics.seedPassagesOf(chunksByPath);

        List<TopicKey> inOrder = new ArrayList<>(Arrangement.topicsInOrder(memberCounts));
        standing.keySet().stream()
                .map(ArrangementTasklet::keyOf)
                .filter(key -> !memberCounts.containsKey(key))
                .forEach(inOrder::add);
        List<TopicsAnalysisReport.Topic> topics = new ArrayList<>();
        for (TopicKey key : inOrder) {
            io.algernon.vespera.engine.embedding.TopicKey held = embeddingKey(key);
            if (!standing.containsKey(held)) {
                continue;
            }
            List<TopicsAnalysisReport.Seed> seeds = topicLedger.seedsOf(scoring, held).stream()
                    .map(seed -> new TopicsAnalysisReport.Seed(seed.seedPath(), seed.passages()))
                    .toList();
            int[] linked = {0};
            clustering.eachPageLinkedTo(
                    scoring, held, page -> linked[0] += ledger.verdicts().survivingAmong(scoring, page).size());
            String name = topicNames
                    .ofTopic(arrangement, key)
                    .orElseThrow(() -> new IllegalStateException(
                            "no name is recorded for topic " + key.shown() + " under arrangement " + arrangement.value()))
                    .name();
            topics.add(new TopicsAnalysisReport.Topic(
                    name,
                    key.seedPath(),
                    key.chunkIndex(),
                    seeds,
                    memberCounts.getOrDefault(key, 0),
                    linked[0]));
        }
        List<String> feedingNone = topicLedger.seedsFeedingNoTopic(scoring);

        // The JSON is moved into place before the page (ADR-362 section 9).
        try {
            Files.createDirectories(workingDirectory);
            AnalysisReportJson.write(
                    workingDirectory.resolve(TOPICS_JSON_FILE_NAME),
                    TopicsAnalysisReport.document(seedPassages, topics, feedingNone, Instant.now()));
            Path part = workingDirectory.resolve(TOPICS_FILE_NAME + ".part");
            Files.writeString(
                    part, TopicsAnalysisReport.render(seedPassages, topics, feedingNone), StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, workingDirectory.resolve(TOPICS_FILE_NAME));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the topics analysis report in " + workingDirectory, e);
        }
    }

    /**
     * Writes {@code topics.json} and then {@code topics.html} where the profile lists the topics (ADR-429 section 10):
     * one row a listed topic in the list's order, each with the documents placed under it and the documents linked from
     * it, and no seed passage, seed document or topic's key. A listed topic's name is its name as listed.
     */
    private void writeTheListedTopicsAnalysisReport(
            RunId scoring, Map<TopicKey, Integer> memberCounts, List<Topics.ListedName> listedNames) {
        List<TopicsAnalysisReport.ListedTopic> topics = new ArrayList<>();
        for (Topics.ListedName listed : listedNames) {
            int[] linked = {0};
            clustering.eachPageLinkedTo(
                    scoring,
                    listed.key(),
                    page -> linked[0] += ledger.verdicts().survivingAmong(scoring, page).size());
            topics.add(new TopicsAnalysisReport.ListedTopic(
                    listed.name(), memberCounts.getOrDefault(keyOf(listed.key()), 0), linked[0]));
        }
        // The JSON is moved into place before the page (ADR-362 section 9).
        try {
            Files.createDirectories(workingDirectory);
            AnalysisReportJson.write(
                    workingDirectory.resolve(TOPICS_JSON_FILE_NAME),
                    TopicsAnalysisReport.documentListed(topics, Instant.now()));
            Path part = workingDirectory.resolve(TOPICS_FILE_NAME + ".part");
            Files.writeString(part, TopicsAnalysisReport.renderListed(topics), StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, workingDirectory.resolve(TOPICS_FILE_NAME));
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the topics analysis report in " + workingDirectory, e);
        }
    }

    /**
     * {@code topics} in the order the profile lists them, a topic with no place last, in the order handed over; the
     * order handed over where the topics are inferred.
     */
    private static List<TopicKey> inTheListsOrder(
            java.util.Collection<TopicKey> topics, Map<TopicKey, Integer> listedPlaces) {
        List<TopicKey> ordered = new ArrayList<>(topics);
        if (!listedPlaces.isEmpty()) {
            ordered.sort(java.util.Comparator.comparingInt(topic -> listedPlaces.getOrDefault(topic, Integer.MAX_VALUE)));
        }
        return ordered;
    }

    /**
     * Stops the step on a naming model Ollama does not serve on this machine, and says why, as generation's stop
     * does: nothing is asked and no run is minted (ADR-407 section 1).
     */
    private void stopOnANamingModelNotServedHere(
            StepContribution contribution, ChunkContext chunkContext, String modelName, String refusal) {
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        recordedClosingLines.record(
                stepExecution,
                OperatorLines.format(
                        "the arrangement step stopped: the naming model {} was refused, so no topic was named and"
                                + " no run was minted -- {}",
                        modelName,
                        refusal));
        stepExecution.setStatus(BatchStatus.FAILED);
        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(refusal));
    }

    /**
     * The topics the scoring step kept, each named from its strongest passages, then each crowded topic's subtopics
     * (ADR-407 sections 4 to 8, ADR-409 section 6): the topics read from the rows 5d recorded under the scoring run, in
     * key order, and not found again. A name the arrangement run already holds is read back and not asked for; each
     * other is recorded as soon as it is given, before the next sibling is asked. The passages' texts are read from the
     * chunk cache.
     *
     * @return the step's closing line where the serving engine gave no answer, and nothing where every name stands
     */
    private Optional<String> nameTheTopics(
            RunId scoring,
            RunId arrangement,
            StageFiveGates.Preamble preamble,
            String namingModel,
            DeliverableLanguage language,
            List<Topics.ListedName> listedNames) {
        List<Topic> topics = TimedStatement.of(
                STAGE, "reading", "read", "the topics kept", () -> topicLedger.standingUnder(scoring));
        RunId measurementRun = stageRuns.seedMeasurement();
        Map<OccurrenceId, SeedChunks> seedContentHashes =
                TaskletSteps.seedContentHashes(
                        STAGE,
                        seeds -> StageProgress.over("Stage 6a (arrangement, seed cache keys read)", seeds),
                        preamble.seedWalk().orElseThrow(),
                        measurementRun,
                        ledger,
                        unusableSeeds,
                        cacheKeys,
                        hybridChunker);
        String identity = relevanceDistribution
                .embedderIdentityFor(preamble.modelName().orElseThrow(), stageRuns.embeddingModelArtefact())
                .orElseThrow(() -> new IllegalStateException(
                        "no embedder identity answers for " + preamble.modelName().orElseThrow()
                                + ", so no topic can be named"));
        Map<OccurrenceId, List<float[]>> resident = relevanceScoring.residentSeedVectors(
                seedContentHashes,
                hybridChunker.identity(),
                ChunkingRule.DEFAULT.identity().value(),
                identity,
                ScoringProgress.NONE);
        Map<String, SeedVectors> bySeedPath = TaskletSteps.seedVectorsByPath(ledger, seedContentHashes, resident);

        Map<String, List<Chunk>> chunksOfContent = new HashMap<>();
        List<GivenName> names = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (int place = 1; place <= topics.size(); place++) {
            Topic topic = topics.get(place - 1);
            TopicKey key = keyOf(topic.key());
            if (topic.key().isListed()) {
                // A listed topic's name is the operator's, as written: never asked of the model, never translated, with
                // no fallback, and in no comparison of ADR-407 section 8; no line is logged for it (ADR-429 section 7).
                if (topicNames.ofTopic(arrangement, key).isEmpty()) {
                    String listed = listedNames.stream()
                            .filter(each -> each.key().equals(topic.key()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "topic " + key.shown() + " is listed under no place in scoring run " + scoring.value()))
                            .name();
                    topicNames.recordTopic(
                            arrangement, key, new GivenName(listed, NameOrigin.LISTED, Optional.empty()));
                }
                continue;
            }
            Optional<GivenName> kept = topicNames.ofTopic(arrangement, key);
            if (kept.isPresent()) {
                names.add(kept.get());
                taken.add(kept.get().name());
                continue;
            }
            List<String> texts = new ArrayList<>();
            for (SeedPassage passage : topic.passagesStrongestFirst(bySeedPath)) {
                String contentHash = bySeedPath.get(passage.seedPath()).contentHash();
                List<Chunk> chunks = chunksOfContent.computeIfAbsent(
                        contentHash, hash -> hybridChunker.stored(hash, ChunkingRule.DEFAULT));
                if (passage.chunkIndex() >= chunks.size()) {
                    throw new IllegalStateException("the chunk " + passage.chunkIndex() + " of seed "
                            + passage.seedPath() + " (content hash " + contentHash
                            + ") is not stored in the chunk cache, so the topic cannot be named from it");
                }
                texts.add(chunks.get(passage.chunkIndex()).text());
            }
            String seedPath = topic.strongestContributingSeed();
            Optional<String> title = documentTitles.forContentHash(bySeedPath.get(seedPath).contentHash());
            NameCall call = new NameCall(texts, TopicFallbackName.of(title, seedPath, place, language));
            GivenName name;
            try {
                name = topicNaming.nameOne(call, taken, namingModel, language);
            } catch (RuntimeException noAnswer) {
                return Optional.of(noAnswerLine(OptionalInt.empty(), shownTopic(key), noAnswer));
            }
            topicNames.recordTopic(arrangement, key, name);
            names.add(name);
            taken.add(name.name());
            NAMES.info(
                    "{} topic {} chunk {} named \"{}\" ({})",
                    STAGE,
                    key.seedPath(),
                    key.chunkIndex(),
                    name.name(),
                    originWord(name));
        }
        long written = names.stream().filter(name -> name.origin() != NameOrigin.FALLBACK).count();
        if (listedNames.isEmpty()) {
            LOG.info(
                    "The arrangement step named {} topic(s) under {}: {} written, {} fell back",
                    names.size(),
                    namingModel,
                    written,
                    names.size() - written);
        }

        // Each crowded topic's subtopics, topic by topic in key order, each topic's in ADR-406 section 4's order.
        RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
        for (Topic topic : topics) {
            TopicKey key = keyOf(topic.key());
            Optional<String> stopped =
                    nameTheSubtopicsOf(scoring, arrangement, key, extractionRun, identity, namingModel, language);
            if (stopped.isPresent()) {
                return stopped;
            }
        }
        return Optional.empty();
    }

    /**
     * The subtopics of one crowded topic, named in their order (ADR-406 section 4) from their survivors' leading
     * chunks, strongest first (ADR-407 section 4, ADR-409 section 6). A topic with no subtopic has nothing to name.
     */
    private Optional<String> nameTheSubtopicsOf(
            RunId scoring,
            RunId arrangement,
            TopicKey key,
            RunId extractionRun,
            String embedderIdentity,
            String namingModel,
            DeliverableLanguage language) {
        List<DocumentCluster> members = documentClusters.membersOf(scoring, embeddingKey(key));
        Map<Integer, List<OccurrenceId>> bySubtopic = new LinkedHashMap<>();
        for (DocumentCluster member : members) {
            if (member.subtopic().isPresent()) {
                bySubtopic.computeIfAbsent(member.subtopic().getAsInt(), subtopic -> new ArrayList<>())
                        .add(member.occurrenceId());
            }
        }
        if (bySubtopic.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        bySubtopic.forEach((subtopic, occurrences) -> counts.put(subtopic, occurrences.size()));
        List<Integer> inOrder = Arrangement.subtopicsInOrder(counts);
        List<String> taken = new ArrayList<>();
        for (int place = 1; place <= inOrder.size(); place++) {
            int subtopic = inOrder.get(place - 1);
            Optional<GivenName> kept = topicNames.ofSubtopic(arrangement, key, subtopic);
            if (kept.isPresent()) {
                taken.add(kept.get().name());
                continue;
            }
            List<OccurrenceId> occurrences = bySubtopic.get(subtopic);
            Map<OccurrenceId, String> hashes = cacheKeys.requireForOccurrences(occurrences, extractionRun);
            Map<OccurrenceId, String> paths = new HashMap<>();
            ledger.occurrences().pathsOf(occurrences).forEach((occurrence, path) -> paths.put(occurrence, path.value()));
            List<OccurrenceId> strongestFirst = clustering.strongestFirst(
                    hashes,
                    paths,
                    hybridChunker.identity(),
                    ChunkingRule.DEFAULT.identity().value(),
                    embedderIdentity);
            List<String> texts = new ArrayList<>();
            for (OccurrenceId survivor : strongestFirst) {
                leadingChunks.forContentHash(hashes.get(survivor)).ifPresent(chunk -> texts.add(chunk.text()));
            }
            OccurrenceId strongest = strongestFirst.getFirst();
            NameCall call = new NameCall(
                    texts,
                    TopicFallbackName.ofSubtopic(
                            documentTitles.forContentHash(hashes.get(strongest)),
                            paths.get(strongest),
                            place,
                            language));
            GivenName name;
            try {
                name = topicNaming.nameOne(call, taken, namingModel, language);
            } catch (RuntimeException noAnswer) {
                return Optional.of(noAnswerLine(OptionalInt.of(subtopic), shownTopic(key), noAnswer));
            }
            topicNames.recordSubtopic(arrangement, key, subtopic, name);
            taken.add(name.name());
            NAMES.info(
                    "{} subtopic {} of topic {} named \"{}\" ({})",
                    STAGE,
                    subtopic,
                    shownTopic(key),
                    name.name(),
                    originWord(name));
        }
        return Optional.empty();
    }

    /**
     * The step's closing line where the serving engine gave no answer (ADR-409 section 6), naming what was being named,
     * the topic's key as ADR-407 section 9's line writes it, and what the engine said, word for word.
     */
    /**
     * The topic as a line or a page writes it: {@code <path> chunk <i>}, or, for a topic the profile lists, its name as
     * listed between quotation marks (ADR-429 section 10).
     */
    private String shownTopic(TopicKey key) {
        return stageRuns.listedNameOf(embeddingKey(key)).map(name -> "\"" + name + "\"").orElse(key.shown());
    }

    private static String noAnswerLine(OptionalInt subtopicNumber, String topic, RuntimeException noAnswer) {
        if (subtopicNumber.isPresent()) {
            return OperatorLines.format(
                    "the arrangement step stopped: the naming model gave no answer for subtopic {} of topic {} -- {};"
                            + " the names given before it are kept, and the next invocation asks only for the rest",
                    subtopicNumber.getAsInt(),
                    topic,
                    noAnswer.getMessage());
        }
        return OperatorLines.format(
                "the arrangement step stopped: the naming model gave no answer for topic {} -- {}; the names given"
                        + " before it are kept, and the next invocation asks only for the rest",
                topic,
                noAnswer.getMessage());
    }

    private static String originWord(GivenName name) {
        return switch (name.origin()) {
            case WRITTEN -> "written";
            case WRITTEN_WHEN_ASKED_AGAIN -> "asked again";
            case FALLBACK -> "fallback";
            case LISTED -> "listed";
        };
    }

    /**
     * Writes the page an approval names, a topic at a time, to a file beside {@code arrangement.html} and
     * moves it over that file once it is whole, so that a step that fails partway leaves the page before it
     * or none (ADR-223 section 3), the move attempted again where it is refused as access denied or, on Windows or
     * a drive Windows serves, as another program's (ADR-242, ADR-244, ADR-245, ADR-246).
     * Where {@code recording}, each topic is arranged, labelled and recorded first; either way its rows are
     * read back before its part of the page is written, so what a reviewer is shown, and an approval then names,
     * is the same whether this invocation just wrote those rows or is only rendering a page for a run an earlier
     * invocation finished (ADR-112, ADR-115, ADR-154 §2).
     *
     * @param topics the topics in the order the arrangement places them
     * @return how many clusters the page shows, read back from the rows recorded under {@code arrangement}
     */
    private int writePage(
            RunId scoring,
            RunId arrangement,
            List<TopicKey> topics,
            Map<TopicKey, Integer> listedPlaces,
            StageProgress scoresRead,
            StageProgress gathered,
            boolean recording) {
        Path file = workingDirectory.resolve(ARRANGEMENT_FILE_NAME);
        Path part = workingDirectory.resolve(ARRANGEMENT_FILE_NAME + ".part");
        Path jsonFile = workingDirectory.resolve(ARRANGEMENT_JSON_FILE_NAME);
        Path jsonPart = workingDirectory.resolve(ARRANGEMENT_JSON_FILE_NAME + ".part");
        int clustersShown = 0;
        long documentsShown = 0;
        boolean listed = !listedPlaces.isEmpty();
        try {
            Files.createDirectories(workingDirectory);
            try (Writer page = Files.newBufferedWriter(part, StandardCharsets.UTF_8);
                    Writer jsonPage = Files.newBufferedWriter(jsonPart, StandardCharsets.UTF_8);
                    AnalysisReportJson json = ArrangementReport.openJson(
                            jsonPage,
                            ArrangementGate.shortNameOf(arrangement),
                            Walk.canonicalRoot(root).toString(),
                            Instant.now(),
                            listed)) {
                ArrangementReport.open(
                        page, ArrangementGate.shortNameOf(arrangement), Walk.canonicalRoot(root).toString(), listed);
                StageProgress topicsDrawn = StageProgress.over("Stage 6a (arrangement, page topics)", topics.size());
                // A listed topic's place is its place in the list and the list's length is how many there are, a topic
                // no document lands under having no heading and keeping its place (ADR-429 sections 7 and 8).
                int of = listed ? listedPlaces.size() : topics.size();
                for (int drawn = 1; drawn <= topics.size(); drawn++) {
                    TopicKey key = topics.get(drawn - 1);
                    int place = listed ? listedPlaces.get(key) : drawn;
                    String whichTopic = "topic " + place + " of " + of;
                    List<DocumentCluster> read = TimedStatement.of(
                            STAGE, "reading", "read", "the members of " + whichTopic,
                            () -> documentClusters.membersOf(scoring, embeddingKey(key)));
                    Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                            scoring, read.stream().map(DocumentCluster::occurrenceId).toList(), scoresReadProgress(scoresRead));
                    List<ClusteredDocument> documents = clusteredDocuments(read, scores, key, gathered);

                    Map<ClusterSlot, OccurrenceId> leads = new HashMap<>();
                    if (recording) {
                        recordTheClustersOf(arrangement, key, place, of, documents, leads);
                    }
                    List<RecordedCluster> recorded = TimedStatement.of(
                            STAGE, "reading", "read", "the recorded clusters of " + whichTopic,
                            () -> clusters.ofTopic(arrangement, key));
                    clustersShown += recorded.size();
                    ArrangementReport.Topic shown = shownTopic(arrangement, key, place, recorded, leads, documents, of);
                    documentsShown += shown.documentCount();
                    ArrangementReport.topicJson(json, place, shown);
                    ArrangementReport.topic(page, shown);
                    topicsDrawn.itemDone();
                }
                ArrangementReport.closeJson(json, topics.size(), clustersShown, documentsShown);
                ArrangementReport.close(page, topics.size(), listed);
            }
            // The JSON is moved into place before the page is (ADR-362 section 9): a JSON that cannot be moved
            // leaves the earlier pair, and a page that cannot be moved leaves the new JSON beside the earlier page.
            ReplacingMove.ATOMIC.intoPlace(jsonPart, jsonFile);
            ReplacingMove.ATOMIC.intoPlace(part, file);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
        return clustersShown;
    }

    /**
     * Arranges, labels and records one topic's clusters: each subtopic's in the place ADR-406 section 4 gives the
     * subtopic, or the topic's own where it has none (ADR-409 section 3).
     */
    private void recordTheClustersOf(
            RunId arrangement,
            TopicKey key,
            int place,
            int of,
            List<ClusteredDocument> documents,
            Map<ClusterSlot, OccurrenceId> leads) {
        List<TopicClusters> parts = Arrangement.clustersOf(documents);
        Map<Integer, Integer> survivorsInSubtopic = new LinkedHashMap<>();
        for (TopicClusters part : parts) {
            if (part.subtopic().isPresent()) {
                survivorsInSubtopic.put(
                        part.subtopic().getAsInt(),
                        part.clusters().stream().mapToInt(cluster -> cluster.documentCount()).sum());
            }
        }
        List<Integer> subtopicsInOrder = Arrangement.subtopicsInOrder(survivorsInSubtopic);
        List<ArrangedCluster> ordered = new ArrayList<>();
        for (TopicClusters part : parts) {
            int subtopicOrder = part.subtopic().isPresent() ? subtopicsInOrder.indexOf(part.subtopic().getAsInt()) + 1 : 0;
            ordered.addAll(Arrangement.order(part, place, subtopicOrder));
        }
        StageProgress labelledAndRecorded = StageProgress.over(
                "Stage 6a (arrangement, clusters, topic " + place + " of " + of + ")", ordered.size());
        for (ArrangedCluster cluster : ordered) {
            LabelledCluster labelled = LeadDocument.labelled(cluster, documents, this::titleOf, this::pathObjectOf);
            clusters.record(arrangement, cluster, labelled.label());
            leads.put(new ClusterSlot(cluster.topic(), cluster.subtopic(), cluster.ordinal()), labelled.leadDocument());
            labelledAndRecorded.itemDone();
        }
    }

    /**
     * One topic as the page shows it, read back off the rows recorded under this run (ADR-112, ADR-154 §2) rather than
     * off the arithmetic that produced them: its recorded name, and its subtopics, each with its recorded name, in the
     * order the arrangement stores them.
     */
    private ArrangementReport.Topic shownTopic(
            RunId arrangement,
            TopicKey key,
            int place,
            List<RecordedCluster> recorded,
            Map<ClusterSlot, OccurrenceId> leads,
            List<ClusteredDocument> documents,
            int of) {
        GivenName topicName = topicNames
                .ofTopic(arrangement, key)
                .orElseThrow(() -> new IllegalStateException(
                        "no name is recorded for topic " + key.shown() + " under arrangement " + arrangement.value()));
        Map<OptionalInt, List<RecordedCluster>> bySubtopic = new LinkedHashMap<>();
        for (RecordedCluster cluster : recorded) {
            bySubtopic.computeIfAbsent(cluster.cluster().subtopic(), subtopic -> new ArrayList<>()).add(cluster);
        }
        List<ArrangementReport.Part> parts = new ArrayList<>();
        for (Map.Entry<OptionalInt, List<RecordedCluster>> inSubtopic : bySubtopic.entrySet()) {
            GivenName subtopicName = null;
            int order = 0;
            if (inSubtopic.getKey().isPresent()) {
                subtopicName = topicNames
                        .ofSubtopic(arrangement, key, inSubtopic.getKey().getAsInt())
                        .orElseThrow(() -> new IllegalStateException("no name is recorded for subtopic "
                                + inSubtopic.getKey().getAsInt() + " of topic " + key.shown() + " under arrangement "
                                + arrangement.value()));
                order = inSubtopic.getValue().getFirst().cluster().subtopicOrder();
            }
            parts.add(new ArrangementReport.Part(
                    order, subtopicName, rowsOf(inSubtopic.getValue(), leads, documents, place, of)));
        }
        return new ArrangementReport.Topic(topicName, parts);
    }

    /**
     * Some of one topic's clusters as the page shows them, read back off the rows recorded under this run
     * (ADR-112, ADR-154 §2) rather than off the arithmetic that produced them.
     *
     * <p>Each cluster's lead is the one {@link LeadDocument#labelled} found while the cluster was being
     * labelled where this invocation arranged it, and {@link LeadDocument#of} where the rows were
     * recorded earlier (ADR-106, ADR-190).
     */
    private List<ArrangementReport.Cluster> rowsOf(
            List<RecordedCluster> recordedClusters,
            Map<ClusterSlot, OccurrenceId> kept,
            List<ClusteredDocument> documents,
            int place,
            int of) {
        List<ArrangementReport.Cluster> rows = new ArrayList<>();
        StageProgress rowsDrawn = StageProgress.over(
                "Stage 6a (arrangement, page rows, topic " + place + " of " + of + ")",
                recordedClusters.size());
        for (RecordedCluster recorded : recordedClusters) {
            ArrangedCluster cluster = recorded.cluster();
            OccurrenceId lead = kept.get(ClusterSlot.of(recorded));
            if (lead == null) {
                lead = LeadDocument.of(cluster, documents);
            }
            rows.add(new ArrangementReport.Cluster(
                    recorded.label().value(), cluster.documentCount(), pathOf(lead), linkTo(lead)));
            rowsDrawn.itemDone();
        }
        return rows;
    }

    /**
     * One topic's membership rows as plain values {@code synthesis} can gather into the clusters it
     * orders (ADR-110) — the score joined on here, because only this module may read it.
     *
     * <p>A survivor that carries no score is passed through as such rather than dropped: whether that
     * is something the arrangement can survive is {@code synthesis}'s rule, and it refuses.
     */
    private static List<ClusteredDocument> clusteredDocuments(
            List<DocumentCluster> membership,
            Map<OccurrenceId, Double> scores,
            TopicKey topic,
            StageProgress gathered) {
        List<ClusteredDocument> documents = new ArrayList<>(membership.size());
        for (DocumentCluster member : membership) {
            documents.add(new ClusteredDocument(
                    member.occurrenceId(),
                    topic,
                    member.subtopic(),
                    member.clusterOrdinal(),
                    scores.get(member.occurrenceId())));
            gathered.itemDone();
        }
        return List.copyOf(documents);
    }

    /** The key of a topic as {@code synthesis} holds it, from the key {@code embedding} recorded it under. */
    private static TopicKey keyOf(io.algernon.vespera.engine.embedding.TopicKey topic) {
        return new TopicKey(topic.firstPassage().seedPath(), topic.firstPassage().chunkIndex());
    }

    /** The key of a topic as {@code embedding} holds it, for the reads of the rows it recorded. */
    private static io.algernon.vespera.engine.embedding.TopicKey embeddingKey(TopicKey topic) {
        return new io.algernon.vespera.engine.embedding.TopicKey(new SeedPassage(topic.seedPath(), topic.chunkIndex()));
    }

    /** {@code embedding} tells this stage each score read; this stage owns the line, and the total is the run's. */
    private static ScoringProgress scoresReadProgress(StageProgress read) {
        return new ScoringProgress() {
            @Override
            public void scoreRead() {
                read.itemDone();
            }
        };
    }

    /**
     * The lead document's own title, read out of the conversion {@code extraction} already cached for
     * it (ADR-106).
     *
     * <p>Resolved here because only {@code pipeline} may name both modules — {@code synthesis} is handed
     * the string (ADR-110). It costs no conversion and opens no file: the response is in the cache, keyed
     * by the content hash stage 2 recorded for the document (ADR-206), which is read from there.
     */
    private Optional<String> titleOf(OccurrenceId occurrenceId) {
        return documentTitles.forContentHash(
                cacheKeys.requireForOccurrence(occurrenceId, stageRuns.upstream(StageModules.EXTRACTION)));
    }

    /**
     * Where a document actually is, as something a reader's browser can open (ADR-104): the recorded
     * corpus root joined to the occurrence's root-relative path, rendered as an absolute {@code file:}
     * target.
     *
     * <p>Nothing is stat-ed. A link that has gone dead because the archive moved is a fact about the
     * archive, and a page that quietly dropped such a link would be hiding the document rather than
     * reporting it.
     */
    private String linkTo(OccurrenceId occurrenceId) {
        return Walk.canonicalRoot(root)
                .resolve(pathObjectOf(occurrenceId).value())
                .toUri()
                .toString();
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return pathObjectOf(occurrenceId).value();
    }

    private OccurrencePath pathObjectOf(OccurrenceId occurrenceId) {
        Optional<OccurrenceFacts> facts = ledger.occurrences().factsFor(occurrenceId);
        return facts.map(OccurrenceFacts::path)
                .orElseThrow(() -> new IllegalStateException(
                        "no facts recorded for occurrence " + occurrenceId.value()));
    }
}
