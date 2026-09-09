package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.RelevanceDistribution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The file a person writes their relevance answers into (ADR-088): one entry per sampled document,
 * the answer left blank, on the same author-a-file-and-re-invoke loop the profile already uses
 * (ADR-061) and the same YAML machinery.
 *
 * <p>It names the run and the embedder identity it was generated under. That is not provenance for
 * its own sake: it is what lets a completed file offered against a different sample be refused
 * outright rather than partially matched, which #111 does when it ingests one. Sixty answers about
 * documents nobody was asked about is worse than no answers, because nothing about it looks wrong.
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

    /** The file's whole text: the stamps, then one unanswered entry per sampled document. */
    static String render(String scoringRunId, String embedderIdentity, List<Entry> entries) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("generatedUnderRun", scoringRunId);
        document.put("generatedUnderEmbedder", embedderIdentity);

        List<Map<String, Object>> questions = new ArrayList<>();
        for (Entry entry : entries) {
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("path", entry.path());
            question.put("score", entry.sampled().score());
            question.put("band", entry.sampled().bandOrdinal());
            question.put("closestSeed", entry.winningSeedPath());
            // Written as an explicit null rather than omitted: an absent key reads as a question
            // nobody thought to ask, where a blank one reads as a question waiting for its answer.
            question.put("relevant", null);
            questions.add(question);
        }
        document.put("documents", questions);

        return YAML.writeValueAsString(document);
    }
}
