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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The file a person writes their answers into (ADR-088): one entry per sampled document with the
 * answer left blank, on the same author-a-file-and-re-invoke loop the profile already uses.
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
                "and every one of those entries is blank: the file asks the questions and answers none of"
                        + " them, because a pre-filled answer is a guess wearing a person's handwriting",
                () -> assertThat(countOf(yaml, "relevant:")).isEqualTo(SAMPLED_PATHS.size()));
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

    private static List<RelevanceLabelFile.Entry> threeSampledDocuments() {
        return List.of(
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(0), aSampleAt(0.42), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(1), aSampleAt(0.55), "seeds/exemplar.pdf"),
                new RelevanceLabelFile.Entry(SAMPLED_PATHS.get(2), aSampleAt(0.61), "seeds/other.pdf"));
    }

    private static RelevanceDistribution.Sampled aSampleAt(double score) {
        return new RelevanceDistribution.Sampled(new OccurrenceId(1), score, new OccurrenceId(2), 0);
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
