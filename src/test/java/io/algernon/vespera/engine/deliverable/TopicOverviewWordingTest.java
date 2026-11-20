package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a topic's page says where no overview was written, and beneath one written over fewer clusters than the topic
 * has, in English and Italian (ADR-402 §5): the code's words are the record's, word for word, and each keeps the rule
 * ADR-174 §5 holds a page with nothing written to.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class TopicOverviewWordingTest {

    /** Each case's English sentence, as ADR-402 §5 gives it, and ADR-435 §8 for a topic no document is placed under. */
    private static final Map<OverviewUnwritten, String> ENGLISH_ON_RECORD = Map.of(
            OverviewUnwritten.FAULT_CITATION_NOT_IN_RANGE,
            "No overview was written for this topic: each time it was asked, the writing model's answer left a sentence"
                    + " citing no group, or cited a group it had not been given, so it was not used.",
            OverviewUnwritten.FAULT_ANSWER_RAN_OUT_OF_ROOM,
            "No overview was written for this topic: the writing model's answer reached its length limit before it was"
                    + " finished, so it was not used.",
            OverviewUnwritten.FAULT_SCHEMA_VIOLATION,
            "No overview was written for this topic: the writing model's answer did not come back in the shape it was"
                    + " asked for, so it was not used.",
            OverviewUnwritten.FAULT_PROMPT_EVALUATION_CEILING,
            "No overview was written for this topic: what was written on its groups came to more than the writing model"
                    + " was given room to read at once.",
            OverviewUnwritten.NOTHING_WRITTEN_OVER_ANY_CLUSTER,
            "No overview was written for this topic: nothing was written over any of its groups.",
            OverviewUnwritten.NOT_REACHED,
            "No overview has been written for this topic yet: writing stopped before it reached this topic. Running the"
                    + " same command again carries the writing on.",
            OverviewUnwritten.NO_DOCUMENT_PLACED,
            "No overview was written for this topic: no document was placed under it.");

    /** Each case's Italian sentence, as ADR-402 §5 gives it, and ADR-435 §8 for a topic no document is placed under. */
    private static final Map<OverviewUnwritten, String> ITALIAN_ON_RECORD = Map.of(
            OverviewUnwritten.FAULT_CITATION_NOT_IN_RANGE,
            "Per questo argomento non è stata scritta alcuna panoramica: ogni volta che è stata chiesta, la risposta del"
                    + " modello di scrittura lasciava una frase senza citare alcun gruppo, o citava un gruppo che non gli"
                    + " era stato dato, quindi non è stata usata.",
            OverviewUnwritten.FAULT_ANSWER_RAN_OUT_OF_ROOM,
            "Per questo argomento non è stata scritta alcuna panoramica: la risposta del modello di scrittura ha"
                    + " raggiunto il limite di lunghezza prima di essere completa, quindi non è stata usata.",
            OverviewUnwritten.FAULT_SCHEMA_VIOLATION,
            "Per questo argomento non è stata scritta alcuna panoramica: la risposta del modello di scrittura non è"
                    + " tornata nella forma richiesta, quindi non è stata usata.",
            OverviewUnwritten.FAULT_PROMPT_EVALUATION_CEILING,
            "Per questo argomento non è stata scritta alcuna panoramica: i testi dei suoi gruppi superavano quanto il"
                    + " modello di scrittura aveva spazio per leggere in una volta.",
            OverviewUnwritten.NOTHING_WRITTEN_OVER_ANY_CLUSTER,
            "Per questo argomento non è stata scritta alcuna panoramica: su nessuno dei suoi gruppi è stato scritto"
                    + " nulla.",
            OverviewUnwritten.NOT_REACHED,
            "Per questo argomento non è ancora stata scritta una panoramica: la scrittura si è fermata prima di arrivare"
                    + " a questo argomento. Eseguire di nuovo lo stesso comando fa proseguire la scrittura.",
            OverviewUnwritten.NO_DOCUMENT_PLACED,
            "Per questo argomento non è stata scritta alcuna panoramica: nessun documento è stato collocato sotto di"
                    + " esso.");

    private static final String DISCLOSURE_IN_ENGLISH = "Written from what was written on %d of the %d groups in this"
            + " topic.";

    private static final String DISCLOSURE_IN_ITALIAN = "Scritto a partire dai testi di %d dei %d gruppi di questo"
            + " argomento.";

    /** The words no sentence says, since running again can make each false (ADR-174 §5, ADR-321 §4). */
    private static final List<String> REFUSED_WORDS =
            List.of("permanent", "never", "always", "permanente", "mai", "sempre");

    @Test
    @Story("A topic's page with no overview says why, in words a reader of the tree can follow")
    @DisplayName("The code gives each case, and the line beneath a partial overview, exactly the record's words")
    @Issue("898")
    @Link(name = "ADR-435", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
    void theCodeSaysWhatTheRecordSays() {
        claim(
                "every case has the records' English and Italian sentence, word for word, and the cases are the"
                        + " records' seven, a topic no document is placed under among them",
                () -> {
                    assertThat(OverviewUnwritten.values()).containsExactlyInAnyOrderElementsOf(ENGLISH_ON_RECORD.keySet());
                    assertThat(OverviewUnwritten.values()).allSatisfy(why -> {
                        assertThat(TopicOverviewWording.of(why, DeliverableLanguage.ENGLISH))
                                .isEqualTo(ENGLISH_ON_RECORD.get(why));
                        assertThat(TopicOverviewWording.of(why, DeliverableLanguage.ITALIAN))
                                .isEqualTo(ITALIAN_ON_RECORD.get(why));
                    });
                });
        claim(
                "each of the four ways an answer is turned down reads as the case of the same name",
                () -> assertThat(ClusterFaultKind.values()).allSatisfy(kind -> assertThat(OverviewUnwritten.of(kind).name())
                        .isEqualTo("FAULT_" + kind.name())));
        claim(
                "the line beneath an overview written over fewer groups than the topic has is the record's, in both"
                        + " languages",
                () -> {
                    assertThat(TopicOverviewWording.disclosure(DeliverableLanguage.ENGLISH))
                            .isEqualTo(DISCLOSURE_IN_ENGLISH);
                    assertThat(TopicOverviewWording.disclosure(DeliverableLanguage.ITALIAN))
                            .isEqualTo(DISCLOSURE_IN_ITALIAN);
                });
    }

    @Test
    @Story("A topic's page with no overview says why, in words a reader of the tree can follow")
    @DisplayName("Every sentence says topic and never cluster, none of the refused words, and is one line")
    void everySentenceKeepsTheRulesOfAPageWithNothingWritten() {
        List<String> english = new ArrayList<>();
        List<String> italian = new ArrayList<>();
        for (OverviewUnwritten why : OverviewUnwritten.values()) {
            english.add(TopicOverviewWording.of(why, DeliverableLanguage.ENGLISH));
            italian.add(TopicOverviewWording.of(why, DeliverableLanguage.ITALIAN));
        }
        english.add(TopicOverviewWording.disclosure(DeliverableLanguage.ENGLISH));
        italian.add(TopicOverviewWording.disclosure(DeliverableLanguage.ITALIAN));

        claim(
                "every English line names the topic, and none says cluster",
                () -> assertThat(english).allSatisfy(line -> {
                    assertThat(line).contains("topic").doesNotContainIgnoringCase("cluster");
                }));
        claim(
                "every Italian line names the argomento, and none says the borrowed cluster",
                () -> assertThat(italian).allSatisfy(line -> {
                    assertThat(line).contains("argomento").doesNotContainIgnoringCase("cluster");
                }));
        claim(
                "no line says permanent, never or always, in either language, as a whole word",
                () -> assertThat(concat(english, italian)).allSatisfy(line -> assertThat(
                                List.of(line.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")))
                        .doesNotContainAnyElementsOf(REFUSED_WORDS)));
        claim(
                "every line is one plain line, with nothing markup could be made of",
                () -> assertThat(concat(english, italian)).allSatisfy(line -> assertThat(line)
                        .doesNotContain("\n", "*", "_", "`", "[", "<")));
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }
}
