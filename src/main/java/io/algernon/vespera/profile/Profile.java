package io.algernon.vespera.profile;

/**
 * The corpus profile: every key the current code knows about, whether or not anyone has answered it
 * (ADR-061, ADR-062).
 *
 * <p>A record and not a map, and that is the whole design. The schema is the type, so a key that
 * does not exist is a compile error rather than a silent null at three in the morning; a typo in the
 * file is a deserialisation failure rather than a key nobody reads; and "every currently-known key"
 * — which is what census has to write out — is a fact the compiler already holds.
 *
 * <p>The canonical constructor is where ADR-062's merge lives. A key missing from the file arrives
 * here as null and leaves as {@link ProfileValue#unset()}, so simply loading a profile and saving it
 * again adds every key the code has learned about since the file was written, without touching a
 * single answer already in it.
 *
 * @param seedFolder where the seed set lives, if the operator has said. Purely operator-supplied:
 *     census cannot guess which folder holds the exemplars, and walks it when it is set (ADR-064).
 * @param degenerateOutputConfidenceFloor stage 2's tier-2 quality floor over Docling's {@code
 *     ConfidenceScores} (ADR-070): a number on Docling's own 0-to-1 scale, below which a converted
 *     document is {@code degenerate-output}. Ships unset, per <b>observe before enforce</b> — the
 *     score distribution over a corpus is never known before a first stage-2 run measures it. Once
 *     set, a {@code null} score (the {@code .docx}/{@code .txt} case, where confidence is never
 *     computed) never crosses this floor, whatever it is set to.
 * @param boilerplateDocumentFrequencyFloor stage 3's eventual boilerplate threshold (ADR-074): a
 *     proportion (0 to 1) of a shingle-parameter identity's {@code shingled_document_count} (the
 *     {@code similarity.shingle_corpus_size} denominator), above which a shingle's measured document
 *     frequency would mark it as boilerplate. Ships unset, per <b>observe before enforce</b> — stage 3
 *     only measures document frequency (ADR-038); stage 4 (out of scope here) is where this floor
 *     would actually exclude a shingle from dedup, and nothing here reads it yet. Stated as a
 *     proportion rather than an absolute count because a footer in 400 of 500 documents and one in
 *     400 of 400,000 are different phenomena — the values {@code similarity} itself stores stay
 *     counts regardless (ADR-073's "counters, never ratios"). Unlike {@code
 *     degenerateOutputConfidenceFloor}, no {@code Measurement}-pointer method exists for this key:
 *     {@code similarity.shingle_document_frequency} and {@code similarity.shingle_corpus_size} are
 *     already what an operator or stage 4 would query directly.
 */
public record Profile(
        ProfileValue seedFolder, ProfileValue degenerateOutputConfidenceFloor, ProfileValue boilerplateDocumentFrequencyFloor) {

    public Profile {
        seedFolder = seedFolder == null ? ProfileValue.unset() : seedFolder;
        degenerateOutputConfidenceFloor =
                degenerateOutputConfidenceFloor == null ? ProfileValue.unset() : degenerateOutputConfidenceFloor;
        boilerplateDocumentFrequencyFloor =
                boilerplateDocumentFrequencyFloor == null ? ProfileValue.unset() : boilerplateDocumentFrequencyFloor;
    }

    /**
     * The two-key constructor every call site before ADR-074 used, kept so that adding a third key
     * mints no compile break at every existing caller — the third key arrives unset, the same "a key
     * the file predates is added unset" merge the canonical constructor already gives a key missing
     * from the file.
     */
    public Profile(ProfileValue seedFolder, ProfileValue degenerateOutputConfidenceFloor) {
        this(seedFolder, degenerateOutputConfidenceFloor, null);
    }

    /** A profile with every key present and none of them answered. */
    static Profile skeleton() {
        return new Profile(null, null, null);
    }

    /** The same profile, with census's pointer to the seed folder's data brought up to date. */
    public Profile withSeedFolderMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder.measuredBy(measurement), degenerateOutputConfidenceFloor, boilerplateDocumentFrequencyFloor);
    }
}
