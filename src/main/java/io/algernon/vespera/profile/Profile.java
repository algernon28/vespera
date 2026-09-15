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
 *     mean_score} specifically (ADR-070, narrowed by ADR-078): a number on Docling's own 0-to-1
 *     scale, below which a converted document is {@code degenerate-output}. It is the mean and never
 *     {@code low_score} — a worst-page floor would condemn a long scan for one bad page, which is a
 *     usability judgement rather than a degeneracy one, and if such a rule is ever wanted it gets a
 *     key of its own rather than a second meaning for this one. Ships unset, per <b>observe before
 *     enforce</b> — the score distribution over a corpus is never known before a first stage-2 run
 *     measures it. Once set, a {@code null} score (the {@code .docx}/{@code .txt} case, where
 *     confidence is never computed) never crosses this floor, whatever it is set to.
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
 * @param embeddingModel gate 3's key (ADR-084, #107): which embedding model turns stage 5's scoring
 *     run on. Ships unset and purely operator-supplied — which model is a profile key, where it is
 *     served is application configuration. No {@code Measurement}-pointer method exists for this key
 *     either: naming a model is not something a measurement pass could inform.
 * @param relevanceScoreFloor stage 5's relevance threshold (ADR-088, #110): a score on the scale
 *     ADR-020's function produces, below which a scored survivor is {@code below-threshold}. Ships
 *     unset, per <b>observe before enforce</b>, and an unset floor does not stop the run — stage 5
 *     scores every survivor, clusters every partition and writes its reports regardless, removing
 *     nothing. The value is meant to be read off a stratified sample of sixty labelled documents, so
 *     it carries a {@link Measurement} pointer at the labelling report the way {@code
 *     degenerateOutputConfidenceFloor} points at the confidence distribution (ADR-075's shape).
 *     Nothing checks that a threshold was ever labelled: the floor is an ordinary profile value and
 *     what stands between a guessed one and the archive is the operator's own {@code provenance}.
 * @param arrangementApproved the arrangement gate's key (ADR-107, #175): the first twelve characters
 *     of the id of the arrangement the operator read and approved. Ships unset, and unset means the
 *     gate is shut — nothing is generated over an arrangement nobody has looked at. It names a
 *     <em>specific</em> arrangement rather than being a yes, because an approval of one shape of the
 *     archive must not be spent on a different shape re-derived later: a boolean never expires, and a
 *     gate that cannot close again is a switch flipped once. Twelve characters rather than the whole
 *     id is the entire concession to typing, and the ledger keeps the whole one. No {@link
 *     Measurement} pointer: what informs this key is a page, not a pass, and the operator records
 *     what they actually checked in {@code provenance}.
 * @param generationModel which model stage 6b writes the synthesis docs with (ADR-114, #179).
 *     <b>The first key whose unset state means neither "no threshold" nor "gate shut" but "a default
 *     applies"</b>: application configuration carries a name, this key overrides it, and an
 *     unanswered key resolves to the configured value rather than ending the invocation. It is
 *     therefore not a gate, unlike {@code embeddingModel} — that model keys every vector and every
 *     relevance verdict, so a corpus scored under a model nobody chose has verdicts nobody can
 *     defend; this one keys a run whose output is prose, which the operator can read and judge
 *     directly. No {@link Measurement} pointer, for the reason {@code embeddingModel} has none.
 * @param generationContextWindow how much of the model's window one of stage 6b's calls may work in
 *     (ADR-108, #182). The second key whose unset state means "a default applies": code carries a
 *     number, this key overrides it, and an unanswered key never ends an invocation. It is the key
 *     most likely to be answered wrongly if it were a stop — the right value is a property of the
 *     machine doing the serving rather than of the archive, and most operators could not name one
 *     before their first run. What it buys when it <em>is</em> answered is real: a larger window reads
 *     more of each group in one call, and every group too large for the window is written from part of
 *     itself. Changing it changes what was read, so it joins the generation run's identity and a
 *     different window is a different run. No {@link Measurement} pointer: what informs it is the
 *     machine, not a pass over the corpus.
 */
public record Profile(
        ProfileValue seedFolder,
        ProfileValue degenerateOutputConfidenceFloor,
        ProfileValue boilerplateDocumentFrequencyFloor,
        ProfileValue embeddingModel,
        ProfileValue relevanceScoreFloor,
        ProfileValue arrangementApproved,
        ProfileValue generationModel,
        ProfileValue generationContextWindow) {

    public Profile {
        seedFolder = seedFolder == null ? ProfileValue.unset() : seedFolder;
        degenerateOutputConfidenceFloor =
                degenerateOutputConfidenceFloor == null ? ProfileValue.unset() : degenerateOutputConfidenceFloor;
        boilerplateDocumentFrequencyFloor =
                boilerplateDocumentFrequencyFloor == null ? ProfileValue.unset() : boilerplateDocumentFrequencyFloor;
        embeddingModel = embeddingModel == null ? ProfileValue.unset() : embeddingModel;
        relevanceScoreFloor = relevanceScoreFloor == null ? ProfileValue.unset() : relevanceScoreFloor;
        arrangementApproved = arrangementApproved == null ? ProfileValue.unset() : arrangementApproved;
        generationModel = generationModel == null ? ProfileValue.unset() : generationModel;
        generationContextWindow =
                generationContextWindow == null ? ProfileValue.unset() : generationContextWindow;
    }

    /** A profile with every key present and none of them answered. */
    static Profile skeleton() {
        return new Profile(null, null, null, null, null, null, null, null);
    }

    /** The same profile, with census's pointer to the seed folder's data brought up to date. */
    public Profile withSeedFolderMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder.measuredBy(measurement),
                degenerateOutputConfidenceFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow);
    }

    /**
     * The same profile, with stage 3's pointer to the confidence-distribution report brought up to
     * date (ADR-075) — closing the asymmetry ADR-070 left between {@code seedFolder} (already wired to
     * a {@link Measurement}) and this key.
     */
    public Profile withDegenerateOutputConfidenceFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor.measuredBy(measurement),
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow);
    }

    /**
     * The same profile, with stage 5's pointer to the labelling report brought up to date (ADR-088) —
     * ADR-075's shape, and the third key to use it.
     *
     * <p>Pointing at the report never answers the key. ADR-088 is explicit that nothing writes the
     * threshold into the profile: doing so would break {@code CONTEXT.md}'s "authored by a person,
     * never guessed at" and ADR-062's census that never touches an existing value, and any automatic
     * rule would need a target proportion that is itself an unmeasured threshold.
     */
    public Profile withRelevanceScoreFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor.measuredBy(measurement),
                arrangementApproved,
                generationModel,
                generationContextWindow);
    }
}
