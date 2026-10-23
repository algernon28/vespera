package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.DeliverableLanguage;

/**
 * What a survivor's copy can come to (ADR-274 §2.3). None of them fails the step, throws, or writes a row: it is a
 * fact about one survivor, recorded in its entry and its manifest row.
 */
enum CopyOutcome {
    /** The digest equalled the key stage 2 recorded, or a copy already stood at its name. */
    COPIED("copied", "", ""),
    /** The bytes read do not hash to the key: the file changed since stage 2 read it. */
    CHANGED(
            "changed",
            "changed in the archive since it was read, so it was not copied",
            "modificato nell'archivio dopo essere stato letto, quindi non è stato copiato"),
    /** The file, or a folder above it, is no longer there. */
    GONE(
            "gone",
            "no longer in the archive, so it was not copied",
            "non più presente nell'archivio, quindi non è stato copiato"),
    /** The drive would have been left with less than {@link Deliverable#COPY_HEADROOM_BYTES}. */
    NO_ROOM(
            "no-room",
            "not copied: the drive had too little room left",
            "non copiato: sull'unità restava troppo poco spazio"),
    /** Any other failure to read the original or write the copy. */
    NOT_COPIED("not-copied", "could not be copied", "non è stato possibile copiarlo");

    private final String word;
    private final String sentence;
    private final String sentenceInItalian;

    CopyOutcome(String word, String sentence, String sentenceInItalian) {
        this.word = word;
        this.sentence = sentence;
        this.sentenceInItalian = sentenceInItalian;
    }

    /** The word the manifest's {@code copy_outcome} column carries, a name and the same in either language (ADR-321 §4). */
    String word() {
        return word;
    }

    /**
     * What an entry says in italics where no copy stands, in {@code language} (ADR-321 §4), unescaped; empty for
     * {@link #COPIED}.
     */
    String sentence(DeliverableLanguage language) {
        return switch (language) {
            case ENGLISH -> sentence;
            case ITALIAN -> sentenceInItalian;
        };
    }
}
