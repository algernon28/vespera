package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelledSpread;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code relevance-labelling.html} and {@code relevance-labelling.json} call no relevance label an answer and no
 * document to label a question (ADR-424 §4): the headline, the spread's column, the section on what the labels say,
 * the documents to label and the threshold not applied say labels, in the page and in the JSON alike, and the ids the
 * control panel reads by stay (ADR-424 §8).
 */
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class TheRelevanceLabellingReportCallsNoLabelAnAnswerTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-18T08:00:00Z");

    /** Answer and question in any of their forms, as whole words. */
    private static final Pattern AN_ANSWER_OR_A_QUESTION = Pattern.compile("(?i)\\b(answer|question)\\w*");

    /** The one sentence of the page whose answer is no label, which ADR-424 §4 leaves as it is. */
    private static final String THE_ANSWER_THAT_IS_NO_LABEL = "The answer is a different seed folder";

    /** The model the operator's labels were set under, and the one that produced this run's scores. */
    private static final String THE_EARLIER_MODEL = "an-earlier-comparing-model";

    private static final String THE_MODEL_NOW = "the-comparing-model-now";

    private static final double A_FLOOR = 0.62;

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("With nothing labelled yet, the page and its JSON speak of documents to label and of labels, under the ids they had")
    void withNothingLabelledThePageAndItsJsonSayLabels() {
        String page = RelevanceLabellingReport.render(aDistribution(), List.of(), nothingLabelled(), null);
        AnalysisReportJson.Document json =
                RelevanceLabellingReport.json(aDistribution(), List.of(), nothingLabelled(), null, WRITTEN_AT);

        claim("the page heads the section on what has been labelled What the labels say so far, and says no document"
                        + " has been labelled yet",
                () -> assertThat(page)
                        .contains("What the labels say so far")
                        .contains("No document has been labelled yet. Fill in the label file beside this page, run the"
                                + " tool again, and this section will say what the labels came to and what each"
                                + " possible cut would cost."));
        claim("the page names the spread's column To label, and reads a proportion off four labels",
                () -> assertThat(page)
                        .contains("To label")
                        .contains("A proportion read off four labels is not the same evidence"));
        claim("the page says the same run always draws the same documents, and asks for their labels to be set in the"
                        + " label file",
                () -> assertThat(page).contains("the same run always draws the same documents, so you can stop and"
                        + " come back. Set their labels in the label file beside this page."));
        claim("and, but for the one sentence whose answer is a different seed folder, the page says neither answer nor"
                        + " question",
                () -> assertThat(page.replace(THE_ANSWER_THAT_IS_NO_LABEL, ""))
                        .doesNotContainPattern(AN_ANSWER_OR_A_QUESTION));

        claim("the JSON's headline names the documents drawn Documents to label, under its key sampled",
                () -> assertThat(json.headline()).filteredOn(figure -> figure.key().equals("sampled"))
                        .singleElement()
                        .satisfies(figure -> assertThat(figure.label()).isEqualTo("Documents to label")));
        claim("its spread's column asked is titled To label",
                () -> assertThat(section(json, "score-spread").table().columns())
                        .filteredOn(column -> column.id().equals("asked"))
                        .singleElement()
                        .satisfies(column -> assertThat(column.label()).isEqualTo("To label")));
        claim("its section what-the-answers-say-so-far is titled What the labels say so far, and says no document has"
                        + " been labelled yet",
                () -> {
                    AnalysisReportJson.Section labels = section(json, "what-the-answers-say-so-far");
                    assertThat(labels.title()).isEqualTo("What the labels say so far");
                    assertThat(labels.text()).containsExactly("No document has been labelled yet.");
                });
        claim("its documents to label say the same run always draws the same documents",
                () -> assertThat(section(json, "the-documents-to-judge").text()).containsExactly(
                        "Each one below is drawn from a band, and the same run always draws the same documents."));
        claim("and no title, paragraph, column or headline label of the JSON says answer or question",
                () -> assertThat(wordsOf(json)).noneMatch(words -> AN_ANSWER_OR_A_QUESTION.matcher(words).find()));
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("A threshold not applied is said to have been read off labels set under another model, in the page and its JSON")
    void aThresholdNotAppliedSaysTheLabelsWereSetUnderAnotherModel() {
        RelevanceLabellingReport.IgnoredFloor ignored =
                new RelevanceLabellingReport.IgnoredFloor(A_FLOOR, THE_EARLIER_MODEL, THE_MODEL_NOW);
        String page = RelevanceLabellingReport.render(aDistribution(), List.of(), nothingLabelled(), ignored);
        AnalysisReportJson.Document json =
                RelevanceLabellingReport.json(aDistribution(), List.of(), nothingLabelled(), ignored, WRITTEN_AT);

        claim("the page says the labels the number was read off were set while the earlier model was in use",
                () -> assertThat(page).contains("The labels this number was read off were set while <code>"
                        + THE_EARLIER_MODEL + "</code> was in use"));
        claim("and so does the JSON's section on the threshold not applied",
                () -> assertThat(String.join(" ", section(json, "threshold-not-applied").text()))
                        .contains("The labels this number was read off were set while " + THE_EARLIER_MODEL
                                + " was in use"));
    }

    /** Five narrow bands of twelve documents drawn each, and one document drawn. */
    private static RelevanceDistribution.Distribution aDistribution() {
        List<RelevanceDistribution.Band> bands = List.of(
                new RelevanceDistribution.Band(0, 0.80, 0.82, 40, 12, 0),
                new RelevanceDistribution.Band(1, 0.82, 0.84, 44, 12, 0),
                new RelevanceDistribution.Band(2, 0.84, 0.86, 39, 12, 0),
                new RelevanceDistribution.Band(3, 0.86, 0.88, 41, 12, 0),
                new RelevanceDistribution.Band(4, 0.88, 0.90, 36, 12, 0));
        return new RelevanceDistribution.Distribution(200, 0.80, 0.90, bands,
                List.of(new RelevanceDistribution.Sampled(new OccurrenceId(1), 0.83, new OccurrenceId(2), 1)));
    }

    /** No label recorded against the seed set, which is every first run. */
    private static LabelledSpread.Spread nothingLabelled() {
        return new LabelledSpread.Spread(List.of(), List.of(), 0);
    }

    private static AnalysisReportJson.Section section(AnalysisReportJson.Document json, String id) {
        return json.sections().stream().filter(section -> section.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("the JSON has no section " + id));
    }

    /** Every text of the JSON a reader is shown: its title, the headline's labels, and each section's words. */
    private static List<String> wordsOf(AnalysisReportJson.Document json) {
        List<String> words = new ArrayList<>();
        words.add(json.title());
        json.headline().forEach(figure -> words.add(figure.label()));
        for (AnalysisReportJson.Section section : json.sections()) {
            words.add(section.title());
            words.addAll(section.text());
            if (section.table() != null) {
                section.table().columns().forEach(column -> words.add(column.label()));
            }
        }
        return words.stream().filter(Objects::nonNull).toList();
    }
}
