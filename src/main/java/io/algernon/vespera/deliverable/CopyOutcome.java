package io.algernon.vespera.deliverable;

/**
 * What a survivor's copy can come to (ADR-274 §2.3). None of them fails the step, throws, or writes a row: it is a
 * fact about one survivor, recorded in its entry and its manifest row.
 */
enum CopyOutcome {
    /** The digest equalled the key stage 2 recorded, or a copy already stood at its name. */
    COPIED("copied", ""),
    /** The bytes read do not hash to the key: the file changed since stage 2 read it. */
    CHANGED("changed", "changed in the archive since it was read, so it was not copied"),
    /** The file, or a folder above it, is no longer there. */
    GONE("gone", "no longer in the archive, so it was not copied"),
    /** The drive would have been left with less than {@link Deliverable#COPY_HEADROOM_BYTES}. */
    NO_ROOM("no-room", "not copied: the drive had too little room left"),
    /** Any other failure to read the original or write the copy. */
    NOT_COPIED("not-copied", "could not be copied");

    private final String word;
    private final String sentence;

    CopyOutcome(String word, String sentence) {
        this.word = word;
        this.sentence = sentence;
    }

    /** The word the manifest's {@code copy_outcome} column carries. */
    String word() {
        return word;
    }

    /** What an entry says in italics where no copy stands, unescaped; empty for {@link #COPIED}. */
    String sentence() {
        return sentence;
    }
}
