package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.DeliverableLanguage;
import java.util.Comparator;
import java.util.List;

/**
 * The fixed words of the tree's pages that more than one class draws, in each language a deliverable can be in
 * (ADR-321 §4): the English as its record fixes it, and the Italian as ADR-321 §4's table gives it.
 *
 * <p>Each word is an exhaustive {@code switch} over the language, so a third language is a compile error here
 * until its table is written (ADR-321 §1); {@code longestNavigationWord} and {@code groupTableHeadRow} derive from
 * those words. The English of the navigation ({@code pageOf}, first, previous, next, last), of the index's lines
 * (run, walk, archive root), of the index's and root page's heading ({@code theDeliverable}) and the heads of the
 * group table ({@code groupTableHeads}), of its code sets (heading, sentence, heads, the archive's top) and of the page in a
 * title lives here; the rest of the English stays with the constants of the class that first held it. The words
 * of the unwritten sentences and of a copy's outcome are held by {@link UnwrittenSentences} and {@link
 * CopyOutcome}, and the pictures' by {@link EntryPictures}; each says <em>group</em> and <em>gruppo</em> where the
 * code says cluster (ADR-122).
 */
final class FixedWords {

    private FixedWords() {}

    /** The heading a cluster page's membership list opens with. */
    static String membershipHeading(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> ClusterPage.MEMBERSHIP_HEADING;
            case ITALIAN -> "I documenti di questo gruppo";
        };
    }

    /** The disclosure, a format taking the documents sent and the documents the cluster holds (ADR-133). */
    static String subsetDisclosure(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> ClusterPage.SUBSET_DISCLOSURE;
            case ITALIAN -> "Scritto a partire dai documenti numerati da 1 a %d, su un totale di %d in questo gruppo.";
        };
    }

    /** What the writing's block shows on hover (ADR-275 §2). */
    static String writingHover(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> ClusterPage.WRITING_HOVER;
            case ITALIAN -> "Ciò che il modello locale ha scritto su questo gruppo a partire dai documenti elencati"
                    + " qui sotto; ogni numero tra parentesi quadre è un collegamento al documento che cita.";
        };
    }

    /** The cell, and the line of a cluster page given no reason, for a cluster nothing was written over. */
    static String nothingWasWrittenOverIt(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> Deliverable.NOTHING_WAS_WRITTEN_OVER_IT;
            case ITALIAN -> "su questo gruppo non è stato scritto nulla";
        };
    }

    /** The entry at a number whose document the cluster no longer holds (ADR-133). */
    static String theClusterNoLongerHoldsIt(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT;
            case ITALIAN -> "un documento da cui è stato scritto il testo, che questo gruppo non contiene più";
        };
    }

    /** The navigation's opening, a format taking the page and the number of pages (ADR-303 §5). */
    static String pageOf(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Page %d of %d: ";
            case ITALIAN -> "Pagina %d di %d: ";
        };
    }

    /** The navigation's link to the first page. */
    static String first(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "first";
            case ITALIAN -> "prima";
        };
    }

    /** The navigation's link to the previous page. */
    static String previous(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "previous";
            case ITALIAN -> "precedente";
        };
    }

    /** The navigation's link to the next page. */
    static String next(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "next";
            case ITALIAN -> "successiva";
        };
    }

    /** The navigation's link to the last page. */
    static String last(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "last";
            case ITALIAN -> "ultima";
        };
    }

    /** The longest of the navigation's four words, which a plan counts a link at an upper bound with (ADR-303 §3). */
    static String longestNavigationWord(DeliverableLanguage language) {
        return List.of(first(language), previous(language), next(language), last(language)).stream()
                .max(Comparator.comparingInt(String::length))
                .orElseThrow();
    }

    /** The index's and the root page's heading, and the index's title, before the run's id. */
    static String theDeliverable(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Deliverable ";
            case ITALIAN -> "Base di conoscenza ";
        };
    }

    /** The index's line naming the run, before its value. */
    static String run(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Run: ";
            case ITALIAN -> "Esecuzione: ";
        };
    }

    /** The index's line naming the walk, before its value. */
    static String walk(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Walk: ";
            case ITALIAN -> "Esplorazione: ";
        };
    }

    /** The index's line naming the archive root, before its value. */
    static String archiveRoot(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Archive root: ";
            case ITALIAN -> "Radice dell'archivio: ";
        };
    }

    /** The index's paragraph on documents.csv and the copies. */
    static String indexParagraph(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> IndexPage.THE_INDEXS_PARAGRAPH;
            case ITALIAN -> "La colonna path di documents.csv indica ogni documento a partire dalla radice"
                    + " dell'archivio riportata sopra. La cartella di ogni gruppo contiene la sua pagina e una copia"
                    + " di ogni documento che vi è elencato, verificata con l'impronta presa quando è stato"
                    + " convertito, e ogni collegamento della pagina di un gruppo porta alla sua copia; la radice"
                    + " dell'archivio indica da dove sono state copiate. La colonna seed_partition non è un percorso"
                    + " sotto quella radice: indica un documento campione sotto la cartella dei campioni, che è una"
                    + " radice a sé. Le colonne copy e copy_outcome dicono quali documenti sono stati copiati e, per"
                    + " quelli che non lo sono stati, perché.";
        };
    }

    /** The heads of a seed partition's table of groups: group, documents, and what the writing is titled. */
    static List<String> groupTableHeads(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> List.of("Group", "Documents", "Written up as");
            case ITALIAN -> List.of("Gruppo", "Documenti", "Titolo del testo");
        };
    }

    /** The row of heads of a seed partition's table of groups, as the index, the root page and the partition write it. */
    static String groupTableHeadRow(DeliverableLanguage language) {
        StringBuilder row = new StringBuilder("<tr>");
        groupTableHeads(language).forEach(head -> row.append("<th>").append(head).append("</th>"));
        return row.append("</tr>\n").toString();
    }

    /** The index's code sets: the heading. */
    static String code(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Code";
            case ITALIAN -> "Codice";
        };
    }

    /** The index's code sets: the one sentence (ADR-296 §4). */
    static String codeSentence(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "Each code set is copied whole under code/, keeping its folders, and each of its files is"
                    + " checked against the size and modification time recorded when the archive was walked.";
            case ITALIAN -> "Ogni insieme di codice è copiato per intero sotto code/, con le sue cartelle, e ciascuno"
                    + " dei suoi file è verificato con la dimensione e la data di modifica registrate quando"
                    + " l'archivio è stato esplorato.";
        };
    }

    /** The index's code sets: the heads of the table. */
    static List<String> codeTableHeads(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> List.of("Code set", "Folder in the archive", "Files copied", "Files not copied");
            case ITALIAN -> List.of("Insieme di codice", "Cartella nell'archivio", "File copiati", "File non copiati");
        };
    }

    /** The folder of a code set at the corpus root (ADR-296 §4). */
    static String archivesTop(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> "the archive's top";
            case ITALIAN -> "la cartella principale dell'archivio";
        };
    }

    /** The root page's one sentence (ADR-315 §2). */
    static String rootSentence(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> ConfluencePages.THE_ROOT_PAGES_SENTENCE;
            case ITALIAN -> "La pagina di ogni gruppo porta una copia verificata di ogni documento che elenca."
                    + " documents.csv elenca ogni documento di questa esecuzione.";
        };
    }

    /** What stands between a split cluster's place and its page number in a Confluence title (ADR-310 §2). */
    static String pageInATitle(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> " page ";
            case ITALIAN -> " pagina ";
        };
    }
}
