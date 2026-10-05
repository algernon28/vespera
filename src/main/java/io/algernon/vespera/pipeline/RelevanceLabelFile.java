package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The file a person writes their relevance answers into (ADR-088): one entry per sampled document,
 * on the same author-a-file-and-re-invoke loop the profile already uses (ADR-061) and the same YAML
 * machinery.
 *
 * <p><b>An entry carries the answer already recorded for it, where there is one (ADR-169).</b> A
 * re-run that reaches this step no longer costs the operator their view of what they answered. Every
 * answer the file shows is one the labelling page counts, because both are built from the same read of
 * {@code relevance_label}; the page may also count answers to documents the current sample does not
 * ask about. Every other entry stays blank.
 *
 * <p>It names the run, the embedder identity and the seed set it was generated under. That is not
 * provenance for its own sake: it is what lets a completed file offered against a different sample be
 * refused outright rather than partially matched, which ingestion does (#111, and #354 for the seed
 * set). Sixty answers about documents nobody was asked about is worse than no answers, because nothing
 * about it looks wrong.
 *
 * <p>The score and the winning seed travel beside each question as the context that was on screen
 * when the judgement was made. ADR-088 keeps them beside a label and never part of what identifies
 * it, because a label answers "is this document relevant to this seed set" — which stays true
 * however the document was scored, and is exactly why a re-score under a new model re-calibrates for
 * free.
 */
final class RelevanceLabelFile {

    private RelevanceLabelFile() {}

    /** The name the file carries in the working directory, beside the database and the profile. */
    static final String FILE_NAME = "relevance-labels.yaml";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /**
     * One question put to the person labelling.
     *
     * @param path the document, as the walk spells it relative to the corpus root (ADR-051)
     * @param sampled which band it came from, and what it scored
     * @param winningSeedPath the seed its score was taken against, so the person can see what it was
     *     judged similar to rather than only how similar
     */
    record Entry(String path, RelevanceDistribution.Sampled sampled, String winningSeedPath) {}

    /** The file's whole text with every entry blank, for a caller with no recorded answers to show. */
    static String render(String scoringRunId, String embedderIdentity, String seedSet, List<Entry> entries) {
        return render(scoringRunId, embedderIdentity, seedSet, entries, Map.of());
    }

    /**
     * The file's whole text: the stamps, then one entry per sampled document, carrying the answer
     * already recorded for it where {@code recordedAnswers} has one (ADR-169 §1) and blank otherwise.
     *
     * @param seedSet the canonical seed set (ADR-097) this run's answers are read against, written as
     *     {@code generatedUnderSeedSet} so a file offered once the profile names a different seed set
     *     is refused rather than recorded against a seed set nobody asked about (ADR-169 §4)
     */
    static String render(
            String scoringRunId,
            String embedderIdentity,
            String seedSet,
            List<Entry> entries,
            Map<OccurrenceId, Boolean> recordedAnswers) {
        return render(scoringRunId, embedderIdentity, seedSet, entries, recordedAnswers, Map.of());
    }

    /**
     * As above, and an entry whose recorded answer a local model set carries {@code labelledBy}, naming
     * the model (ADR-197 §3). The file reader ignores the key; a person overrules by editing
     * {@code relevant}, and the key is gone the next time the file is written.
     */
    static String render(
            String scoringRunId,
            String embedderIdentity,
            String seedSet,
            List<Entry> entries,
            Map<OccurrenceId, Boolean> recordedAnswers,
            Map<OccurrenceId, String> labelledBy) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("generatedUnderRun", scoringRunId);
        document.put("generatedUnderEmbedder", embedderIdentity);
        document.put("generatedUnderSeedSet", seedSet);

        List<Map<String, Object>> questions = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("path", entry.path());
            question.put("score", entry.sampled().score());
            question.put("band", entry.sampled().bandOrdinal());
            question.put("closestSeed", entry.winningSeedPath());
            // Written as an explicit null rather than omitted: an absent key reads as a question
            // nobody thought to ask, where a blank one reads as a question waiting for its answer.
            // Where an answer is already recorded for this document (ADR-169 §1), it is written here
            // instead, so a re-run does not cost the operator their view of what they answered.
            question.put("relevant", recordedAnswers.get(entry.sampled().occurrenceId()));
            String model = labelledBy.get(entry.sampled().occurrenceId());
            if (model != null) {
                question.put("labelledBy", model);
            }
            questions.add(question);
        }
        document.put("documents", questions);

        return YAML.writeValueAsString(document);
    }
}
