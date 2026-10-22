package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.ClusterFaultKind;
import java.util.List;

/**
 * Every fixed word an Italian deliverable's pages carry (ADR-321 §4, §5), copied out of the record and not out of the
 * code, so the code is held to the record and not the other way round, as {@link UnwrittenSentencesOnRecord} holds the
 * English of ADR-174 and ADR-319.
 *
 * <p>Each is rendered prose for a reader of the tree, so it says <em>gruppo</em> where every name here says cluster
 * (ADR-122). Where the record writes {@code %d} for a number, the constant is a format with the same {@code %d}.
 * Public, because the invocation tests of {@code pipeline} read the pages these words land on.
 */
public final class ItalianWordingOnRecord {

    /** ADR-174's first case: the question came to more than the room. */
    public static final String DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM = "Su questo gruppo non è stato scritto nulla: i"
            + " suoi documenti superavano quanto il modello di scrittura aveva spazio per leggere in una volta.";

    /** An answer that stopped at the length it was allowed. */
    public static final String THE_ANSWER_REACHED_ITS_LENGTH_LIMIT = "Su questo gruppo non è stato scritto nulla: la"
            + " risposta del modello di scrittura ha raggiunto il limite di lunghezza prima di essere completa, quindi"
            + " non è stata usata.";

    /** An answer that could not be read into the shape the call imposed. */
    public static final String THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE = "Su questo gruppo non è stato scritto nulla: la"
            + " risposta del modello di scrittura non è tornata nella forma richiesta, quindi non è stata usata.";

    /** Writing that cited outside the documents sent, or cited nothing. */
    public static final String THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN = "Su questo gruppo non è stato scritto"
            + " nulla: la risposta del modello di scrittura citava documenti che non gli erano stati dati, o non ne"
            + " citava nessuno, quindi non è stata usata.";

    /** A cluster none of whose members this invocation could send (ADR-121's first route). */
    public static final String NO_DOCUMENT_COULD_BE_SENT = "Su questo gruppo non è stato scritto nulla: nessuno dei"
            + " suoi documenti ha potuto essere letto e inviato al modello di scrittura.";

    /** A cluster every sendable member of which the estimate found too long (ADR-121's second route). */
    public static final String NO_DOCUMENT_FITS_THE_ROOM = "Su questo gruppo non è stato scritto nulla: ciascuno dei"
            + " suoi documenti che si è potuto leggere è stato giudicato più lungo di quanto il modello di scrittura"
            + " aveva spazio per leggere in una volta, quindi non ne è stato inviato nessuno.";

    /** A cluster after the five turned-down answers that stopped the step (ADR-111). */
    public static final String WRITING_STOPPED_BEFORE_IT = "Su questo gruppo non è ancora stato scritto nulla: la"
            + " scrittura si è fermata prima di arrivare a questo gruppo. Eseguire di nuovo lo stesso comando fa"
            + " proseguire la scrittura.";

    /** ADR-319 §5.1's line beneath the reason, on the page of a cluster that stands without writing. */
    public static final String STANDS_WITHOUT_WRITING = "La sua risposta è stata scartata ogni volta che è stata"
            + " chiesta, quindi questo gruppo resta senza testo; eseguire di nuovo lo stesso comando non chiede altro su"
            + " di esso.";

    /** ADR-174's seven, in the order its table states them. */
    public static final List<String> EVERY_SENTENCE = List.of(
            DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM,
            THE_ANSWER_REACHED_ITS_LENGTH_LIMIT,
            THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE,
            THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN,
            NO_DOCUMENT_COULD_BE_SENT,
            NO_DOCUMENT_FITS_THE_ROOM,
            WRITING_STOPPED_BEFORE_IT);

    /** A cluster page given no reason, and the index's and the root page's cell for a cluster with no writing. */
    public static final String NOTHING_WAS_WRITTEN_OVER_IT = "su questo gruppo non è stato scritto nulla";

    /** The membership list's heading. */
    public static final String MEMBERSHIP_HEADING = "I documenti di questo gruppo";

    /** The disclosure, its two numbers the documents sent and the documents the cluster holds (ADR-133). */
    public static final String SUBSET_DISCLOSURE =
            "Scritto a partire dai documenti numerati da 1 a %d, su un totale di %d in questo gruppo.";

    /** What the writing's block shows on hover (ADR-275). */
    public static final String WRITING_HOVER = "Ciò che il modello locale ha scritto su questo gruppo a partire dai"
            + " documenti elencati qui sotto; ogni numero tra parentesi quadre è un collegamento al documento che cita.";

    /** The entry at a number whose document the cluster no longer holds (ADR-133). */
    public static final String THE_CLUSTER_NO_LONGER_HOLDS_IT =
            "un documento da cui è stato scritto il testo, che questo gruppo non contiene più";

    /** The navigation's opening, page {@code %d} of {@code %d} (ADR-303 §5). */
    public static final String PAGE_OF = "Pagina %d di %d: ";

    /** The navigation's four links. */
    public static final String FIRST = "prima";

    public static final String PREVIOUS = "precedente";

    public static final String NEXT = "successiva";

    public static final String LAST = "ultima";

    /** A copy's outcome, {@code changed}. */
    public static final String CHANGED = "modificato nell'archivio dopo essere stato letto, quindi non è stato copiato";

    /** A copy's outcome, {@code gone}. */
    public static final String GONE = "non più presente nell'archivio, quindi non è stato copiato";

    /** A copy's outcome, {@code no-room}. */
    public static final String NO_ROOM = "non copiato: sull'unità restava troppo poco spazio";

    /** A copy's outcome, {@code not-copied}. */
    public static final String NOT_COPIED = "non è stato possibile copiarlo";

    /** The line for one picture not shown, after its number. */
    public static final String ONE_MORE_PICTURE = " altra immagine di questo documento non è mostrata.";

    /** The line for more than one picture not shown, after their number. */
    public static final String MORE_PICTURES = " altre immagini di questo documento non sono mostrate.";

    /** The index's and the root page's heading, and the index's title, before the run's id. */
    public static final String THE_DELIVERABLE = "Base di conoscenza ";

    /** The index's three lines, each before its value. */
    public static final String RUN = "Esecuzione: ";

    public static final String WALK = "Esplorazione: ";

    public static final String ARCHIVE_ROOT = "Radice dell'archivio: ";

    /** The index's paragraph on documents.csv and the copies. */
    public static final String THE_INDEXS_PARAGRAPH = "La colonna path di documents.csv indica ogni documento a"
            + " partire dalla radice dell'archivio riportata sopra. La cartella di ogni gruppo contiene la sua pagina e"
            + " una copia di ogni documento che vi è elencato, verificata con l'impronta presa quando è stato"
            + " convertito, e ogni collegamento della pagina di un gruppo porta alla sua copia; la radice"
            + " dell'archivio indica da dove sono state copiate. La colonna seed_partition non è un percorso sotto"
            + " quella radice: indica un documento campione sotto la cartella dei campioni, che è una radice a sé. Le"
            + " colonne copy e copy_outcome dicono quali documenti sono stati copiati e, per quelli che non lo sono"
            + " stati, perché.";

    /** The three heads of a seed partition's table of groups, on the index, the root page and its own page. */
    public static final List<String> THE_TABLE_OF_GROUPS = List.of("Gruppo", "Documenti", "Titolo del testo");

    /** The index's code sets: the heading, the sentence, the four heads and the folder of a set at the corpus root. */
    public static final String CODE = "Codice";

    public static final String THE_CODE_SENTENCE = "Ogni insieme di codice è copiato per intero sotto code/, con le"
            + " sue cartelle, e ciascuno dei suoi file è verificato con la dimensione e la data di modifica registrate"
            + " quando l'archivio è stato esplorato.";

    public static final List<String> THE_TABLE_OF_CODE_SETS =
            List.of("Insieme di codice", "Cartella nell'archivio", "File copiati", "File non copiati");

    public static final String THE_ARCHIVES_TOP = "la cartella principale dell'archivio";

    /** The root page's one sentence (ADR-315 §2). */
    public static final String THE_ROOT_PAGES_SENTENCE = "La pagina di ogni gruppo porta una copia verificata di"
            + " ogni documento che elenca. documents.csv elenca ogni documento di questa esecuzione.";

    /** What stands between a split cluster's place and its page number in a Confluence title (ADR-310 §2). */
    public static final String PAGE_IN_A_TITLE = " pagina ";

    /** The code of the language, as a page's {@code lang} names it. */
    public static final String LANG = "it";

    private ItalianWordingOnRecord() {}

    /** What the page of a cluster carrying a fault row says, by kind (ADR-174 §2). */
    public static String forKind(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> DOCUMENTS_CAME_TO_MORE_THAN_THE_ROOM;
            case ANSWER_RAN_OUT_OF_ROOM -> THE_ANSWER_REACHED_ITS_LENGTH_LIMIT;
            case SCHEMA_VIOLATION -> THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE;
            case CITATION_NOT_IN_RANGE -> THE_ANSWER_CITED_OUTSIDE_WHAT_IT_WAS_GIVEN;
        };
    }
}
