package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.Unwritten;
import java.util.Optional;

/**
 * The line a cluster's page carries in place of the writing, by the reason nothing was written (ADR-174, ADR-274
 * §1.4): the same words as before, without the asterisks that were Markdown's, shown in italics of HTML by the page,
 * and, where the deliverable is Italian, the Italian ADR-321 §4 gives each.
 *
 * <p>Each says <em>group</em> (<em>gruppo</em>) where the code says cluster (ADR-122), names no kind of fault, and
 * describes one run only, so none says the gap is for good: the next invocation that reaches the writing rewrites the
 * page.
 */
final class UnwrittenSentences {

    /**
     * The line beneath the reason on the page of a cluster whose answer was turned down each time it was asked, in a
     * run whose writing is finished (ADR-319 §5.1): it says group, and none of permanent, never or always, and
     * describes this run alone.
     */
    private static final String STANDS_WITHOUT_WRITING = "Its answer was turned down each time it was asked, so this"
            + " group stands without writing; running the same command again asks nothing more about it.";

    /** {@link #STANDS_WITHOUT_WRITING} in Italian (ADR-321 §4). */
    private static final String STANDS_WITHOUT_WRITING_IN_ITALIAN = "La sua risposta è stata scartata ogni volta che è"
            + " stata chiesta, quindi questo gruppo resta senza testo; eseguire di nuovo lo stesso comando non chiede"
            + " altro su di esso.";

    private UnwrittenSentences() {}

    /**
     * The line saying the group stands, for a reason that is a fault on record in a run whose generation step is
     * recorded finished, or being recorded by the invocation drawing it; empty otherwise (ADR-319 §5.1). In a finished
     * run every fault standing is a repeated fault, so every such page may say it.
     */
    static Optional<String> standing(Unwritten why, boolean writingFinished, DeliverableLanguage language) {
        if (!writingFinished || why == null) {
            return Optional.empty();
        }
        String line = switch (language) {
            case ENGLISH -> STANDS_WITHOUT_WRITING;
            case ITALIAN -> STANDS_WITHOUT_WRITING_IN_ITALIAN;
        };
        return switch (why) {
            case FAULT_PROMPT_EVALUATION_CEILING,
                    FAULT_ANSWER_RAN_OUT_OF_ROOM,
                    FAULT_SCHEMA_VIOLATION,
                    FAULT_CITATION_NOT_IN_RANGE -> Optional.of(line);
            case NO_SENDABLE_DOCUMENT, NOTHING_FITS_THE_WINDOW, NOT_REACHED -> Optional.empty();
        };
    }

    /** The sentence for {@code why}; an exhaustive {@code switch}, so a new reason stops this compiling. */
    static String of(Unwritten why, DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> english(why);
            case ITALIAN -> italian(why);
        };
    }

    private static String english(Unwritten why) {
        return switch (why) {
            case FAULT_PROMPT_EVALUATION_CEILING -> "Nothing was written over this group: its documents came to more"
                    + " than the writing model was given room to read at once.";
            case FAULT_ANSWER_RAN_OUT_OF_ROOM -> "Nothing was written over this group: the writing model's answer"
                    + " reached its length limit before it was finished, so it was not used.";
            case FAULT_SCHEMA_VIOLATION -> "Nothing was written over this group: the writing model's answer did not"
                    + " come back in the shape it was asked for, so it was not used.";
            case FAULT_CITATION_NOT_IN_RANGE -> "Nothing was written over this group: the writing model's answer cited"
                    + " documents it had not been given, or cited none, so it was not used.";
            case NO_SENDABLE_DOCUMENT -> "Nothing was written over this group: none of its documents could be read"
                    + " and sent to the writing model.";
            case NOTHING_FITS_THE_WINDOW -> "Nothing was written over this group: each of its documents that could be"
                    + " read was judged longer than the writing model was given room to read at once, so none was sent.";
            case NOT_REACHED -> "Nothing has been written over this group yet: writing stopped before it reached this"
                    + " group. Running the same command again carries the writing on.";
        };
    }

    private static String italian(Unwritten why) {
        return switch (why) {
            case FAULT_PROMPT_EVALUATION_CEILING -> "Su questo gruppo non è stato scritto nulla: i suoi documenti"
                    + " superavano quanto il modello di scrittura aveva spazio per leggere in una volta.";
            case FAULT_ANSWER_RAN_OUT_OF_ROOM -> "Su questo gruppo non è stato scritto nulla: la risposta del modello"
                    + " di scrittura ha raggiunto il limite di lunghezza prima di essere completa, quindi non è stata"
                    + " usata.";
            case FAULT_SCHEMA_VIOLATION -> "Su questo gruppo non è stato scritto nulla: la risposta del modello di"
                    + " scrittura non è tornata nella forma richiesta, quindi non è stata usata.";
            case FAULT_CITATION_NOT_IN_RANGE -> "Su questo gruppo non è stato scritto nulla: la risposta del modello"
                    + " di scrittura citava documenti che non gli erano stati dati, o non ne citava nessuno, quindi"
                    + " non è stata usata.";
            case NO_SENDABLE_DOCUMENT -> "Su questo gruppo non è stato scritto nulla: nessuno dei suoi documenti ha"
                    + " potuto essere letto e inviato al modello di scrittura.";
            case NOTHING_FITS_THE_WINDOW -> "Su questo gruppo non è stato scritto nulla: ciascuno dei suoi documenti"
                    + " che si è potuto leggere è stato giudicato più lungo di quanto il modello di scrittura aveva"
                    + " spazio per leggere in una volta, quindi non ne è stato inviato nessuno.";
            case NOT_REACHED -> "Su questo gruppo non è ancora stato scritto nulla: la scrittura si è fermata prima di"
                    + " arrivare a questo gruppo. Eseguire di nuovo lo stesso comando fa proseguire la scrittura.";
        };
    }
}
