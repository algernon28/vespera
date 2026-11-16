package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;

/**
 * What a topic's page says where no overview was written, and beneath one written over fewer clusters than the topic
 * has (ADR-402 section 5), in English and in the Italian ADR-321 section 4 gives. Each says <em>topic</em>
 * (<em>argomento</em>) and <em>group</em> (<em>gruppo</em>) where the code says cluster (ADR-122), names no kind of
 * fault, and is one plain line that says none of permanent, never or always.
 */
public final class TopicOverviewWording {

    private TopicOverviewWording() {}

    /** The sentence for {@code why}; an exhaustive {@code switch}, so a new case stops this compiling. */
    public static String of(OverviewUnwritten why, DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> english(why);
            case ITALIAN -> italian(why);
        };
    }

    /**
     * The line beneath an overview written over fewer clusters than the topic has, as a format string for {@link
     * String#formatted}: the clusters read, then the topic's.
     */
    public static String disclosure(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Written from what was written on %d of the %d groups in this topic.";
            case ITALIAN -> "Scritto a partire dai testi di %d dei %d gruppi di questo argomento.";
        };
    }

    private static String english(OverviewUnwritten why) {
        return switch (why) {
            case FAULT_CITATION_NOT_IN_RANGE -> "No overview was written for this topic: each time it was asked, the"
                    + " writing model's answer left a sentence citing no group, or cited a group it had not been given,"
                    + " so it was not used.";
            case FAULT_ANSWER_RAN_OUT_OF_ROOM -> "No overview was written for this topic: the writing model's answer"
                    + " reached its length limit before it was finished, so it was not used.";
            case FAULT_SCHEMA_VIOLATION -> "No overview was written for this topic: the writing model's answer did not"
                    + " come back in the shape it was asked for, so it was not used.";
            case FAULT_PROMPT_EVALUATION_CEILING -> "No overview was written for this topic: what was written on its"
                    + " groups came to more than the writing model was given room to read at once.";
            case NOTHING_WRITTEN_OVER_ANY_CLUSTER -> "No overview was written for this topic: nothing was written over"
                    + " any of its groups.";
            case NOT_REACHED -> "No overview has been written for this topic yet: writing stopped before it reached"
                    + " this topic. Running the same command again carries the writing on.";
        };
    }

    private static String italian(OverviewUnwritten why) {
        return switch (why) {
            case FAULT_CITATION_NOT_IN_RANGE -> "Per questo argomento non è stata scritta alcuna panoramica: ogni volta"
                    + " che è stata chiesta, la risposta del modello di scrittura lasciava una frase senza citare alcun"
                    + " gruppo, o citava un gruppo che non gli era stato dato, quindi non è stata usata.";
            case FAULT_ANSWER_RAN_OUT_OF_ROOM -> "Per questo argomento non è stata scritta alcuna panoramica: la"
                    + " risposta del modello di scrittura ha raggiunto il limite di lunghezza prima di essere completa,"
                    + " quindi non è stata usata.";
            case FAULT_SCHEMA_VIOLATION -> "Per questo argomento non è stata scritta alcuna panoramica: la risposta del"
                    + " modello di scrittura non è tornata nella forma richiesta, quindi non è stata usata.";
            case FAULT_PROMPT_EVALUATION_CEILING -> "Per questo argomento non è stata scritta alcuna panoramica: i testi"
                    + " dei suoi gruppi superavano quanto il modello di scrittura aveva spazio per leggere in una volta.";
            case NOTHING_WRITTEN_OVER_ANY_CLUSTER -> "Per questo argomento non è stata scritta alcuna panoramica: su"
                    + " nessuno dei suoi gruppi è stato scritto nulla.";
            case NOT_REACHED -> "Per questo argomento non è ancora stata scritta una panoramica: la scrittura si è"
                    + " fermata prima di arrivare a questo argomento. Eseguire di nuovo lo stesso comando fa proseguire"
                    + " la scrittura.";
        };
    }
}
