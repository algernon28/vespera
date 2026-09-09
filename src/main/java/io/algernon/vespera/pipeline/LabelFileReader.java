package io.algernon.vespera.pipeline;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads back the file a person wrote their relevance answers into (ADR-088), on the same YAML
 * machinery that wrote it (ADR-061).
 *
 * <p><b>A file generated under a different sample is refused whole.</b> Salvaging the entries whose
 * paths happen to line up would record answers about documents that sample never asked about, and
 * nothing downstream could tell afterwards which was which. The refusal names both runs, because an
 * operator who is told only that something did not match cannot go and find the right file.
 *
 * <p><b>A file with some answers and some blanks is the ordinary mid-state</b>, not an error: the
 * sitting is meant to be resumable across days (ADR-047). What must never be lost is that the pass
 * was partial, so the blanks are counted rather than dropped — a threshold read off a partial pass
 * looks exactly as authoritative as one read off a complete one, which is the failure ADR-088 sized
 * the sample to prevent.
 *
 * <p><b>A file nobody has answered at all is refused</b>, per #105's resolution: an operator running
 * ingestion believes they have finished, and succeeding on nothing would tell them their answers
 * landed when none did.
 */
final class LabelFileReader {

    private LabelFileReader() {}

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /** What reading a file produced: either answers to record, or a reason not to record any. */
    sealed interface Outcome permits Answers, Refused {}

    /**
     * The answers a file carries, and how much of it is still blank.
     *
     * @param embedderIdentity what the file was generated under, recorded beside each answer
     * @param answers the entries a person has actually answered
     * @param unanswered how many entries are still waiting, so a partial pass is recorded as partial
     */
    record Answers(String embedderIdentity, List<Answer> answers, int unanswered) implements Outcome {}

    /** One answered entry: the document, what was said about it, and the score that was on screen. */
    record Answer(String path, boolean relevant, double scoreShown) {}

    /** Why nothing in the file was recorded. */
    record Refused(String reason) implements Outcome {}

    /** The answers in {@code yaml}, or a refusal when it is not a file about {@code expectedRunId}. */
    static Outcome read(String yaml, String expectedRunId) {
        JsonNode root;
        try {
            root = YAML.readTree(yaml);
        } catch (RuntimeException malformed) {
            return new Refused("the label file could not be read as YAML: " + malformed.getMessage());
        }

        String namedRun = text(root, "generatedUnderRun");
        if (namedRun == null) {
            return new Refused("the label file names no run, so there is no way to tell which sample it"
                    + " was answered against; regenerate it and label the new file");
        }
        if (!namedRun.equals(expectedRunId)) {
            return new Refused("the label file was generated under run " + namedRun + ", but the current"
                    + " sample is run " + expectedRunId + "; answers about one sample are not answers"
                    + " about another, so nothing in it was recorded");
        }

        String embedderIdentity = text(root, "generatedUnderEmbedder");
        List<Answer> answers = new ArrayList<>();
        int unanswered = 0;
        for (JsonNode entry : root.path("documents")) {
            JsonNode relevant = entry.path("relevant");
            if (relevant.isMissingNode() || relevant.isNull()) {
                unanswered++;
                continue;
            }
            answers.add(new Answer(
                    entry.path("path").asString(), relevant.asBoolean(), entry.path("score").asDouble()));
        }

        if (answers.isEmpty()) {
            return new Refused("the label file has no answers in it yet: " + unanswered + " document(s)"
                    + " are still waiting, so nothing was recorded");
        }
        return new Answers(embedderIdentity, List.copyOf(answers), unanswered);
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
