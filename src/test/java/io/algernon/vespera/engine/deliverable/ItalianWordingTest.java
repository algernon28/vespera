package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Italian of an Italian deliverable's fixed words, held to the record word for word and to the rules every English
 * sentence keeps (ADR-321 §4, §5): the eight sentences of ADR-174 and ADR-319, a copy's outcome, the line on pictures not
 * shown and the index's code sets. The pages these land on are held by {@link AnItalianTreeIsDrawnInItalianTest}; a
 * split cluster's Confluence title moved with the Confluence rendering into {@code confluence} (ADR-333 §1), where
 * {@code TheConfluenceRenderingIsDrawnInItalianTest} holds it.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the Italian says <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
@Link(name = "ADR-319", url = Adr.A_CLUSTER_TURNED_DOWN_THE_LAST_TWO_TIMES_STANDS_WITHOUT_WRITING, type = "adr")
class ItalianWordingTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    @Test
    @Story("An Italian deliverable says in Italian why nothing was written over a group")
    @DisplayName("The code gives each case the Italian the record gives it, and has no Italian sentence the record does not")
    void theCodesItalianForEveryCaseIsTheRecords() {
        claim(
                "for every way an answer can be turned down, the Italian sentence is the record's, word for word",
                () -> assertThat(ClusterFaultKind.values()).allSatisfy(kind -> assertThat(
                                UnwrittenSentences.of(Unwritten.of(kind), ITALIAN))
                        .isEqualTo(ItalianWordingOnRecord.forKind(kind))));
        claim(
                "and so is the Italian for each of the three cases no reason is recorded for",
                () -> {
                    assertThat(UnwrittenSentences.of(Unwritten.NO_SENDABLE_DOCUMENT, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.NO_DOCUMENT_COULD_BE_SENT);
                    assertThat(UnwrittenSentences.of(Unwritten.NOTHING_FITS_THE_WINDOW, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.NO_DOCUMENT_FITS_THE_ROOM);
                    assertThat(UnwrittenSentences.of(Unwritten.NOT_REACHED, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.WRITING_STOPPED_BEFORE_IT);
                });
        claim(
                "and the code has no Italian sentence the record does not",
                () -> assertThat(Arrays.stream(Unwritten.values()).map(why -> UnwrittenSentences.of(why, ITALIAN)))
                        .containsExactlyInAnyOrderElementsOf(ItalianWordingOnRecord.EVERY_SENTENCE));
    }

    /** {@code html} as a reader reads it: the five entities read back, so an escaped apostrophe reads as one. */
    static String asRead(String html) {
        return html.replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
