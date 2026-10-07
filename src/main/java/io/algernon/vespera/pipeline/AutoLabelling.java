package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.LoseNoDocumentationFloor;
import io.algernon.vespera.embedding.RelevanceLabel;
import io.algernon.vespera.embedding.RelevanceLabeller;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * {@code vespera label --auto} (ADR-197): a local labeller answers the sample's questions, its answers
 * are recorded as the model's, the label file is rewritten so the operator can read and correct them,
 * and the floor is set by the rule that loses no documentation.
 *
 * <p><b>A person's answer is never asked again and never replaced.</b> An entry is put to the labeller
 * only where nobody has answered it or a model has. <b>A floor a person wrote is never overwritten</b>:
 * the key is written only while it is unset or carries this rule's own provenance.
 *
 * <p><b>Nothing is sent before the labeller says it may be used.</b> Its refusal ends the invocation
 * before the label file is read, let alone a document.
 */
@Component
class AutoLabelling {

    private static final Logger LOG = LoggerFactory.getLogger(AutoLabelling.class);

    /** What the floor's provenance opens with when this rule wrote it, and so what marks it as the rule's. */
    static final String FLOOR_PROVENANCE_PREFIX = "set by the rule that loses no documentation";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /** What an invocation did, or why it did nothing. */
    record Outcome(boolean refused, String message) {

        static Outcome refused(String message) {
            return new Outcome(true, message);
        }
    }

    private final RelevanceLabeller labeller;
    private final RelevanceLabels relevanceLabels;
    private final ProfileStore profileStore;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    /** Asked for only when openings are read: building it asks the converter what it is built from. */
    private final ObjectProvider<ExtractorIdentity> extractorIdentity;
    private final HybridChunker hybridChunker;
    private final ExtractionCacheKeys cacheKeys;
    private final Path workingDirectory;

    AutoLabelling(
            RelevanceLabeller labeller,
            RelevanceLabels relevanceLabels,
            ProfileStore profileStore,
            Ledger ledger,
            DoclingExtractor extractor,
            ObjectProvider<ExtractorIdentity> extractorIdentity,
            HybridChunker hybridChunker,
            JdbcTemplate jdbcTemplate,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.labeller = labeller;
        this.relevanceLabels = relevanceLabels;
        this.profileStore = profileStore;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.workingDirectory = workingDirectory;
    }

    Outcome run() {
        Optional<String> refusal = labeller.refusal();
        if (refusal.isPresent()) {
            return Outcome.refused(labeller.identity() + " must not be used: " + refusal.get());
        }
        Path file = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Outcome.refused(LabelIngestion.NO_FILE + " at " + file + ". A sample is written there by a"
                    + " scoring run; run vespera against the corpus first.");
        }
        JsonNode document;
        try {
            document = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        } catch (RuntimeException malformed) {
            return Outcome.refused("the label file could not be read as YAML: " + malformed.getMessage());
        }
        String runName = text(document, "generatedUnderRun");
        String embedder = text(document, "generatedUnderEmbedder");
        String fileSeedSet = text(document, "generatedUnderSeedSet");
        if (runName == null || embedder == null || fileSeedSet == null) {
            return Outcome.refused("the label file at " + file + " does not name its run, embedder and seed set."
                    + " Regenerate it by running vespera against the corpus.");
        }
        RunId run = new RunId(runName);
        if (ledger.walkOf(run).isEmpty()) {
            return Outcome.refused("the label file names run " + runName + ", which this database does not"
                    + " hold. Nothing was recorded.");
        }
        Profile profile = profileStore.load();
        if (!profile.seedFolder().isSet()
                || !Walk.canonicalRoot(Path.of(profile.seedFolder().value())).toString().equals(fileSeedSet)) {
            return Outcome.refused("the label file was generated under the seed set at " + fileSeedSet
                    + ", which the profile does not name now. Nothing was recorded.");
        }

        // An answer typed into the file and not yet recorded is a person's, and asking the model about
        // its entry would overwrite it when the file is rewritten below (ADR-197 §3).
        int unrecorded = 0;
        for (JsonNode entry : document.path("documents")) {
            JsonNode typed = entry.path("relevant");
            if (typed.isMissingNode() || typed.isNull()) {
                continue;
            }
            Optional<Boolean> recorded =
                    relevanceLabels.answerFor(new OccurrencePath(entry.path("path").asString()), fileSeedSet);
            if (recorded.isEmpty() || recorded.get() != typed.asBoolean()) {
                unrecorded++;
            }
        }
        if (unrecorded > 0) {
            return Outcome.refused("the label file holds " + unrecorded + " answer(s) not yet recorded; run"
                    + " vespera label first");
        }

        // This command runs no job, so it has no run of its own to read keys under: the stage-2 run is the one
        // the label file's scoring run was derived from, found by following the recorded upstream runs
        // (ADR-206 section 4), and the survivors are the occurrences of that run's walk. No file under the
        // corpus root is opened.
        RunId extractionRun = extractionRunUpstreamOf(run);
        WalkId walk = ledger.walkOf(run)
                .orElseThrow(() -> new IllegalStateException("run " + run.value() + " has no walk recorded"));
        DocumentOpening documentOpening = new DocumentOpening(
                extractor, extractorIdentity.getObject(), hybridChunker, cacheKeys);
        int questions = 0;
        int relevant = 0;
        int notRelevant = 0;
        int blank = 0;
        int byAPerson = 0;
        for (JsonNode entry : document.path("documents")) {
            questions++;
            OccurrencePath path = new OccurrencePath(entry.path("path").asString());
            if (relevanceLabels.answerFor(path, fileSeedSet).isPresent()
                    && relevanceLabels.labelledBy(path, fileSeedSet).isEmpty()) {
                byAPerson++;
                continue;
            }
            Optional<String> opening = ledger.occurrenceId(walk, path)
                    .map(occurrence -> documentOpening
                            .of(occurrence, extractionRun, "document " + path.value())
                            .asRead())
                    .orElseGet(() -> {
                        LOG.warn(
                                "document {} is in the label file but not in the walk of run {}, so its opening"
                                        + " is not shown",
                                path.value(),
                                run.value());
                        return Optional.empty();
                    });
            Optional<Boolean> answer;
            try {
                answer = labeller.answer(new LabelQuestion(path.value(), text(entry, "closestSeed"), opening));
            } catch (RuntimeException unreachable) {
                LOG.error("the local labeller {} did not answer", labeller.identity(), unreachable);
                return Outcome.refused(labeller.identity() + " did not answer: " + unreachable.getMessage()
                        + ". Answers recorded before it stopped are kept; run the same command again.");
            }
            if (answer.isEmpty()) {
                blank++;
                continue;
            }
            relevanceLabels.recordByModel(
                    path,
                    fileSeedSet,
                    answer.get(),
                    run,
                    entry.path("score").asDouble(),
                    embedder,
                    labeller.identity());
            if (answer.get()) {
                relevant++;
            } else {
                notRelevant++;
            }
        }

        rewrite(file, document, fileSeedSet);

        StringBuilder message = new StringBuilder("labelled ")
                .append(relevant + notRelevant)
                .append(" of ")
                .append(questions)
                .append(" question(s) with ")
                .append(labeller.identity())
                .append(": ")
                .append(relevant)
                .append(" relevant, ")
                .append(notRelevant)
                .append(" not relevant; ")
                .append(blank)
                .append(" left blank because no opening could be read or the model gave no answer; ")
                .append(byAPerson)
                .append(" already answered by a person and left as they are");
        message.append('\n').append(setTheFloor(fileSeedSet, embedder));
        LOG.info("{}", message);
        return new Outcome(false, message.toString());
    }

    /**
     * The run of stage 2 that {@code scoring} was derived from, found by following {@link
     * Ledger#upstreamRuns} breadth-first, however many steps back (ADR-048). It is where the key of every
     * survivor of {@code scoring}'s walk was recorded.
     */
    private RunId extractionRunUpstreamOf(RunId scoring) {
        Set<String> visited = new HashSet<>();
        Deque<RunId> toVisit = new ArrayDeque<>();
        toVisit.add(scoring);
        while (!toVisit.isEmpty()) {
            RunId current = toVisit.poll();
            if (!visited.add(current.value())) {
                continue;
            }
            if (ledger.stageOf(current).filter(StageModules.EXTRACTION.stage()::equals).isPresent()) {
                return current;
            }
            toVisit.addAll(ledger.upstreamRuns(current));
        }
        throw new IllegalStateException("run " + scoring.value()
                + " has no stage-2 run upstream of it, so no extraction cache key can be read for its documents");
    }

    /** The label file with every recorded answer in it and who set each one, so a person can read and correct. */
    private void rewrite(Path file, JsonNode document, String seedSet) {
        Map<String, String> modelAnswers = relevanceLabels.modelAnswers(seedSet);
        for (JsonNode entry : document.path("documents")) {
            ObjectNode question = (ObjectNode) entry;
            String path = entry.path("path").asString();
            relevanceLabels.answerFor(new OccurrencePath(path), seedSet).ifPresent(answer -> question.put("relevant", answer));
            String model = modelAnswers.get(path);
            if (model != null) {
                question.put("labelledBy", model);
            } else {
                question.remove("labelledBy");
            }
        }
        try {
            Files.writeString(file, YAML.writeValueAsString(document), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }

    /** The rule of ADR-197 §4, written only over a key a person has not written. */
    private String setTheFloor(String seedSet, String embedder) {
        Profile profile = profileStore.load();
        boolean ours = !profile.relevanceScoreFloor().isSet()
                || (profile.relevanceScoreFloor().provenance() != null
                        && profile.relevanceScoreFloor().provenance().startsWith(FLOOR_PROVENANCE_PREFIX));
        if (!ours) {
            return "relevanceScoreFloor left as it is: it was written by a person";
        }
        List<RelevanceLabel> labels = relevanceLabels.forSeedSet(seedSet);
        Optional<Double> floor = LoseNoDocumentationFloor.over(labels, embedder);
        if (floor.isEmpty()) {
            return profile.relevanceScoreFloor().isSet()
                    ? "relevanceScoreFloor left as it is: no label says relevant any more"
                    : "relevanceScoreFloor left unset: no label says relevant";
        }
        Map<String, String> modelAnswers = relevanceLabels.modelAnswers(seedSet);
        List<RelevanceLabel> relevantLabels = labels.stream()
                .filter(RelevanceLabel::relevant)
                .filter(label -> label.embedderIdentity().equals(embedder))
                .toList();
        List<RelevanceLabel> bySomeModel = relevantLabels.stream()
                .filter(label -> modelAnswers.containsKey(label.path().value()))
                .toList();
        TreeSet<String> models = new TreeSet<>();
        bySomeModel.forEach(label -> models.add(modelAnswers.get(label.path().value())));
        String basis = "over " + relevantLabels.size() + " relevant label(s) of which " + bySomeModel.size()
                + " were set by " + (models.isEmpty() ? "a model" : String.join(", ", models));
        profileStore.save(profile.withRelevanceScoreFloor(
                Double.toString(floor.get()),
                FLOOR_PROVENANCE_PREFIX + " (the lowest score among the relevant labels), " + basis
                        + ", by vespera label --auto"));
        return "relevanceScoreFloor set to " + floor.get() + " by the rule that loses no documentation, " + basis;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
