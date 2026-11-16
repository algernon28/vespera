package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a sentence of a topic's overview ends, so that the check can ask each one for a citation (ADR-402 §2): at
 * {@code .}, {@code ?}, {@code !} or {@code …} before a blank and a capital, a quotation mark, a parenthesis or a
 * bracket, or at the end of the writing; never after a run of single letters each followed by a full stop, a single
 * capital or one of the record's titles, nor before a digit or a lower-case letter; and a citation opening a sentence
 * belongs to the one before it.
 */
@Epic("Synthesis")
@Feature("Writing over a topic")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class OverviewSentencesTest {

    @Test
    @Story("Each sentence of a topic's overview is asked for a citation")
    @DisplayName("A sentence ends at a full stop, a question mark or an exclamation mark before a capital letter")
    void endsASentenceAtAFullStopQuestionOrExclamationMarkBeforeACapital() {
        String writing = "The groups share a harvest [1]. Do they differ on cost [2]? They do [3]!";

        claim(
                "writing of three sentences ended by a full stop, a question mark and an exclamation mark reads as"
                        + " those three sentences, in the order written",
                () -> assertThat(OverviewSentences.of(writing))
                        .containsExactly(
                                "The groups share a harvest [1].", "Do they differ on cost [2]?", "They do [3]!"));
    }

    @Test
    @Story("Each sentence of a topic's overview is asked for a citation")
    @DisplayName("A number written just after a full stop belongs to the sentence before it")
    void aCitationAfterTheFullStopBelongsToTheSentenceBefore() {
        String writing = "Both groups track the harvest. [2] The second adds its cost [3].";

        claim(
                "the bracketed number opening the second sentence is read with the first, the sentence it follows",
                () -> assertThat(OverviewSentences.of(writing))
                        .containsExactly("Both groups track the harvest. [2]", "The second adds its cost [3]."));
    }

    @Test
    @Story("Each sentence of a topic's overview is asked for a citation")
    @DisplayName("No sentence ends after an abbreviation of single letters, an initial or a title")
    void doesNotEndAfterLettersEachFollowedByAFullStopAnInitialOrATitle() {
        String writing = "The U.S. Treasury and Rossi S.p.A. Milano agree [1]. J. Smith and Dott. Verdi and"
                + " L'ing. Neri differ [2].";

        claim(
                "U.S., S.p.A., the initial J., Dott. and L'ing. each stand before a capital letter and end no"
                        + " sentence, so the writing is two sentences",
                () -> assertThat(OverviewSentences.of(writing))
                        .containsExactly(
                                "The U.S. Treasury and Rossi S.p.A. Milano agree [1].",
                                "J. Smith and Dott. Verdi and L'ing. Neri differ [2]."));
    }

    @Test
    @Story("Each sentence of a topic's overview is asked for a citation")
    @DisplayName("No sentence ends before a digit or a lower-case letter")
    void doesNotEndBeforeADigitOrALowerCaseLetter() {
        String writing = "Under art. 5 the turns are fixed, e.g. weekly, at a rate of 3.5 per field [1].";

        claim(
                "art. before a digit, e.g. before a lower-case word and the full stop inside 3.5 end no sentence,"
                        + " so the writing is one sentence",
                () -> assertThat(OverviewSentences.of(writing)).containsExactly(writing));
    }
}
