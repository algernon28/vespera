package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules every sentence ADR-174 fixes is held to, checked over the sentences themselves so a later edit to one
 * cannot break a rule the invocation tests would only meet on the page it lands on. The sentences are written by
 * {@code deliverable}'s {@link UnwrittenSentences} since ADR-274 §4; {@code Unwritten} stays a reason with no words.
 *
 * <p>The rules are held over the code's own sentences, and {@link #theCodeSaysWhatTheRecordSays} holds those to the
 * record's; {@code GenerationFaultInvocationTest} and {@code GenerationBreakerInvocationTest} are where the page is
 * held to them.
 *
 * <p><b>Three words are refused because a re-run can make them false</b> (ADR-174 §5, ADR-111): the page is written
 * again by every invocation that reaches the writing, so a sentence about this run is replaced by the writing once a
 * repair pass gets it, and a sentence saying the gap was permanent would have been false the day it was written.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("325")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class UnwrittenPageWordingTest {

    /** The deliverable's language whose sentences ADR-174 and ADR-319 fix word for word; Italian's are ADR-321's. */
    private static final DeliverableLanguage ENGLISH = DeliverableLanguage.ENGLISH;

    /**
     * Every sentence the code writes in place of writing, one for each reason: the rules below are held over these, the
     * code's own, so a sentence reworded in the code against a rule turns them red.
     */
    private static final List<String> EVERY_SENTENCE =
            Arrays.stream(Unwritten.values()).map(why -> UnwrittenSentences.of(why, ENGLISH)).toList();

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("The code gives each case exactly the sentence the record gives it")
    void theCodeSaysWhatTheRecordSays() {
        claim(
                "the code's sentence for every way an answer can be turned down is the record's, word for"
                        + " word, and a new way of turning one down cannot be added without one",
                () -> assertThat(ClusterFaultKind.values()).allSatisfy(kind -> assertThat(
                                UnwrittenSentences.of(Unwritten.of(kind), ENGLISH))
                        .isEqualTo(UnwrittenSentencesOnRecord.forKind(kind))));
        claim(
                "and so is its sentence for each of the three cases no reason is recorded for",
                () -> {
                    assertThat(UnwrittenSentences.of(Unwritten.NO_SENDABLE_DOCUMENT, ENGLISH))
                            .isEqualTo(UnwrittenSentencesOnRecord.NO_DOCUMENT_COULD_BE_SENT);
                    assertThat(UnwrittenSentences.of(Unwritten.NOTHING_FITS_THE_WINDOW, ENGLISH))
                            .isEqualTo(UnwrittenSentencesOnRecord.NO_DOCUMENT_FITS_THE_ROOM);
                    assertThat(UnwrittenSentences.of(Unwritten.NOT_REACHED, ENGLISH))
                            .isEqualTo(UnwrittenSentencesOnRecord.WRITING_STOPPED_BEFORE_IT);
                });
        claim(
                "and the code has no sentence the record does not",
                () -> assertThat(EVERY_SENTENCE)
                        .containsExactlyInAnyOrderElementsOf(UnwrittenSentencesOnRecord.EVERY_SENTENCE));
    }

}
