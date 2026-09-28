package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The file a person writes their answers into (ADR-088): one entry per sampled document, on the same
 * author-a-file-and-re-invoke loop the profile already uses. An entry is blank unless an answer was
 * already recorded for its document (ADR-169). This class pins how an answer handed in is written;
 * {@code LabelFileShowsRecordedAnswersInvocationTest} pins, through the commands, that the answers
 * handed in are the ones the ledger holds.
 *
 * <p>The stamps matter more than they look. The file names the run and the embedder it was generated
 * under so that a file offered against a different sample can be refused outright rather than
 * partially matched — sixty answers about documents nobody was asked about is worse than no answers,
 * because nothing about it looks wrong.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceLabelFileTest {

    /** The run the sample was drawn under, which the file has to name. */
    private static final String SCORING_RUN = "9f2c41ab";

    /** The embedder the scores were produced by, which the file also has to name. */
    private static final String EMBEDDER = "nomic-embed-text@1.5/ollama-0.33.2";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    /** Three sampled documents is enough to claim one entry each, and to read the file by eye. */
    private static final List<String> SAMPLED_PATHS =
            List.of("reports/q1.pdf", "notes/meeting.docx", "archive/old letter.txt");

    @Test
    @Story("One blank answer per document, and the file says what it was generated against")
    @DisplayName("The label file carries one unanswered entry per sampled document")
    void carriesOneUnansweredEntryPerSampledDocument() {
        String yaml = RelevanceLabelFile.render(SCORING_RUN, EMBEDDER, threeSampledDocuments());

        claim(
                "every document that was sampled has an entry waiting for an answer, so the person"
                        + " labelling works through the file itself rather than keeping a list of their own",
                () -> assertThat(SAMPLED_PATHS).allSatisfy(path -> assertThat(yaml).contains(path)));
        claim(
                "and with no recorded answers handed in, every one of those entries is blank: the"
                        + " engine never guesses an answer",
                () -> assertThat(countOf(yaml, "relevant: null")).isEqualTo(SAMPLED_PATHS.size()));
        claim(
                "each entry carries the score the document got, so the person can see what the engine"
                        + " thought while they decide -- that is the context ADR-088 keeps beside a label,"
                        + " never part of what identifies it",
                () -> assertThat(yaml).contains("0.42"));
    }

    @Test
    @Story("One blank answer per document, and the file says what it was generated against")
    @DisplayName("The file names the run and the embedder it was generated under, so a stale one can be refused")
    void namesTheRunAndEmbedderItWasGeneratedUnder() {
        String yaml = RelevanceLabelFile.render(SCORING_RUN, EMBEDDER, threeSampledDocuments());

        claim(
                "the file names the run it was generated under, which is what lets a file offered against"
                        + " a different sample be refused outright rather than partially matched",
                () -> assertThat(yaml).contains(SCORING_RUN));
        claim(
                "and it names the embedder the scores came from: the same documents scored by a different"
                        + " model are different numbers, so answers gathered against one are not answers"
                        + " against the other",
                () -> assertThat(yaml).contains(EMBEDDER));
    }

    @Test
    @Story("The label file shows the answers already given")
    @DisplayName("An entry carries the answer recorded for its document, true or false, and an entry with none stays blank")
    @Link(name = "ADR-169", url = Adr.THE_LABEL_FILE_SHOWS_THE_ANSWERS_ALREADY_RECORDED, type = "adr")
    void carriesTheAnswerRecordedForEachDocument() {
        List<RelevanceLabelFile.Entry> entries = List.of(
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(0), aSampleOf(11, 0.42), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(1), aSampleOf(12, 0.55), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(2), aSampleOf(13, 0.61), "seeds/other.pdf"));
        Map<OccurrenceId, Boolean> recorded = Map.of(new OccurrenceId(11), true, new OccurrenceId(12), false);

        String yaml = RelevanceLabelFile.render(SCORING_RUN, EMBEDDER, entries, recorded);

        claim(
                "the document recorded as relevant shows that answer, so the person who gave it is not"
                        + " asked for it again",
                () -> assertThat(answerShownFor(yaml, SAMPLED_PATHS.get(0))).isEqualTo("true"));
        claim(
                "the document recorded as not relevant shows that answer too: a no is an answer as much"
                        + " as a yes",
                () -> assertThat(answerShownFor(yaml, SAMPLED_PATHS.get(1))).isEqualTo("false"));
        claim(
                "and the document with nothing recorded is blank: an answer is shown only where one"
                        + " was given, and each is matched to its own document",
                () -> assertThat(answerShownFor(yaml, SAMPLED_PATHS.get(2))).isEqualTo("null"));
    }

    private static List<RelevanceLabelFile.Entry> threeSampledDocuments() {
        return List.of(
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(0), aSampleAt(0.42), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(1), aSampleAt(0.55), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(2), aSampleAt(0.61), "seeds/other.pdf"));
    }

    private static RelevanceDistribution.Sampled aSampleAt(double score) {
        return new RelevanceDistribution.Sampled(new OccurrenceId(1), score, new OccurrenceId(2), 0);
    }

    /** A document sampled as {@code occurrence}, so answers keyed by occurrence can tell entries apart. */
    private static RelevanceDistribution.Sampled aSampleOf(long occurrence, double score) {
        return new RelevanceDistribution.Sampled(new OccurrenceId(occurrence), score, new OccurrenceId(2), 0);
    }

    /** The answer the file shows for {@code path}, as the file spells it. */
    private static String answerShownFor(String yaml, String path) {
        for (JsonNode entry : YAML.readTree(yaml).path("documents")) {
            if (path.equals(entry.path("path").asString())) {
                JsonNode relevant = entry.path("relevant");
                return relevant.isMissingNode() || relevant.isNull() ? "null" : relevant.asString();
            }
        }
        return "(no entry)";
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
