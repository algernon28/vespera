package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the control document converted (ADR-184 section 2), read in {@code extraction} since ADR-189:
 * the answer is a conversion and its text carries the sentence the shipped PDF holds. Sending it is
 * {@code pipeline}'s; this is only the reading of what came back.
 *
 * <p>Each case below was read by today's {@code ControlConversion.carriesTheSentence} before the move.
 * Two are chosen because a plausible other test gives the other answer: a failure whose raw answer
 * carries the sentence (a test that read the text first would call it converted), and a line that
 * carries the sentence among other words (a test that compared whole lines would call it not).
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-184", url = Adr.FIVE_FAILURES_IN_A_ROW_STOP_STAGE_2_ONLY_AFTER_A_FAILED_CONTROL_CONVERSION, type = "adr")
class ControlReadingTest {

    /** The one line of text the shipped control PDF carries, written out. */
    private static final String THE_SENTENCE = "Vespera control document";

    @Test
    @Story("Whether the control document converted")
    @DisplayName("The control document's sentence is the one line the shipped PDF carries")
    void theSentenceIsTheShippedOne() {
        claim(
                "the text the answer must carry is \"" + THE_SENTENCE + "\", the line written into the PDF",
                () -> assertThat(ControlConversion.SENTENCE).isEqualTo(THE_SENTENCE));
    }

    @Test
    @Story("Whether the control document converted")
    @DisplayName("A conversion whose text carries the sentence converted, partial or whole, and wherever in a line the sentence sits")
    void aConversionCarryingTheSentenceConverted() {
        claim(
                "a successful conversion whose one line is the sentence converted",
                () -> assertThat(ControlReading.of(Optional.of(converting(ConversionStatus.SUCCESS, THE_SENTENCE))))
                        .isEqualTo(ControlReading.CONVERTED));
        claim(
                "so did a partial one, because a partial conversion is a conversion",
                () -> assertThat(ControlReading.of(
                                Optional.of(converting(ConversionStatus.PARTIAL_SUCCESS, THE_SENTENCE))))
                        .isEqualTo(ControlReading.CONVERTED));
        claim(
                "and so did one whose line carries the sentence among other words: the text is searched for the"
                        + " sentence, not compared line by line",
                () -> assertThat(ControlReading.of(Optional.of(
                                converting(ConversionStatus.SUCCESS, "page 1: " + THE_SENTENCE + " (end)"))))
                        .isEqualTo(ControlReading.CONVERTED));
    }

    @Test
    @Story("Whether the control document converted")
    @DisplayName("No answer, a failure, and a conversion without the sentence each did not convert, and each is told apart")
    void everythingElseDidNotConvert() {
        DoclingResponse failureCarryingTheSentence = new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError("pdf_backend", "docling", "could not convert", FailureCategory.INTERNAL, null)),
                0d,
                null,
                textOf(THE_SENTENCE));

        claim(
                "a call that brought no answer -- refused, timed out or cut off -- did not convert",
                () -> assertThat(ControlReading.of(Optional.empty())).isEqualTo(ControlReading.NOT_ANSWERED));
        claim(
                "an answer that is not a conversion did not convert, even where its raw text carries the"
                        + " sentence: what the converter said it did comes before what the text says",
                () -> assertThat(ControlReading.of(Optional.of(failureCarryingTheSentence)))
                        .isEqualTo(ControlReading.NOT_A_CONVERSION));
        claim(
                "and a conversion into text without the sentence did not convert either",
                () -> assertThat(ControlReading.of(
                                Optional.of(converting(ConversionStatus.SUCCESS, "a page of something else"))))
                        .isEqualTo(ControlReading.LACKS_THE_SENTENCE));
        claim(
                "only the reading that converted says so",
                () -> assertThat(List.of(ControlReading.values()))
                        .filteredOn(ControlReading::converted)
                        .containsExactly(ControlReading.CONVERTED));
    }

    private static DoclingResponse converting(ConversionStatus status, String text) {
        return new DoclingResponse(status, List.of(), 0d, null, textOf(text));
    }

    private static String textOf(String text) {
        return "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + text + "\"}]}}}";
    }
}
