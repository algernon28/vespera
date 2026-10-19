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
 * <p><b>Each component is typed by the kind of answer its key takes</b> (ADR-120): {@link TextValue}
 * for a folder, a model's name or an approval, {@link NumericValue} for a floor, a threshold or a
 * proportion, and {@link NameEndingsValue} for a list of name endings (ADR-256). That is ADR-061's "the record is the schema" carried down one level from the key set to
 * the value — which keys hold a number is now a fact this declaration holds, rather than one each
 * reader re-derived by reaching for a parse.
 *
 * <p>The canonical constructor is where ADR-062's merge lives. A key missing from the file arrives
 * here as null and leaves as the unset value of its own type ({@link TextValue#unset()},
 * {@link NumericValue#unset()} or {@link NameEndingsValue#unset()}), according to the kind of answer its key takes (ADR-120), so simply loading a profile and saving it
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
 * @param degenerateOutputWordFloor stage 2's tier-3 floor (ADR-282): a whole number of words from 0,
 *     below which a converted occurrence's {@code word_count} makes it {@code degenerate-output}
 *     whatever its detected format. A key of its own, as ADR-078 asks of a new measure, since the
 *     confidence floor is a score and this is a count. Ships unset, per <b>observe before enforce</b>,
 *     and is not a gate; unset or ignored, nothing is removed for too few words. Its {@link Measurement}
 *     points at {@code word-count-distribution.html}, and it enters stage 2's run identity where set.
 * @param boilerplateDocumentFrequencyFloor the boilerplate threshold stage 4 applies (ADR-080): a
 *     proportion (0 to 1) of N, the documents measured ({@code shingled_document_count} of the
 *     content census, the {@code similarity.shingle_corpus_size} denominator). A shingle found in at
 *     least {@code ceil(floor × N)} documents, taken in exact decimal arithmetic (ADR-260), is
 *     boilerplate, and stage 4 strips those shingles before it computes signatures. Ships unset, per
 *     <b>observe before enforce</b> — stage 3 only measures document frequency (ADR-038) and judges
 *     nothing. It is read off {@code document-frequency.html}, the page stage 3 writes (ADR-262), and
 *     its {@link Measurement} points at that page. Stated as a proportion rather than an absolute count
 *     because a footer in 400 of 500 documents and one in 400 of 400,000 are different phenomena — the
 *     values {@code similarity} itself stores stay counts regardless (ADR-073's "counters, never
 *     ratios").
 * @param embeddingModel gate 3's key (ADR-084, ADR-298): which embedding model stage 5 scores under.
 *     Ships unset, and unset means a default applies — application configuration carries {@code
 *     bge-m3:latest} and this key overrides it; stage 5 is gated only where neither names a model. The
 *     third such key, after {@code generationModel} and {@code generationContextWindow}. Nothing is
 *     written here when the default applies. No {@code Measurement}-pointer method exists for this
 *     key: naming a model is not something a measurement pass could inform.
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
 *     therefore not a gate, though {@code embeddingModel} now also ships a default (ADR-298) — that
 *     model keys every vector and every relevance verdict, so its default is changed only by a
 *     record; this one keys a run whose output is prose, which the operator can read and judge
 *     directly. No {@link Measurement} pointer, for the reason {@code embeddingModel} has none.
 * @param generationContextWindow how much of the model's window one of stage 6b's calls may work in
 *     (ADR-108, #182). The second key whose unset state means "a default applies": code carries a
 *     number, this key overrides it, and an unanswered key never ends an invocation. It is the key
 *     most likely to be answered wrongly if it were a stop — the right value is a property of the
 *     machine doing the serving rather than of the archive, and most operators could not name one
 *     before their first run. What it buys when it <em>is</em> answered is real: a larger window reads
 *     more of each cluster in one call, and every cluster too large for the window is written from part of
 *     itself. Changing it changes what was read, so it joins the generation run's identity and a
 *     different window is a different run. No {@link Measurement} pointer: what informs it is the
 *     machine, not a pass over the corpus.
 * @param logTimestampShareFloor stage 1's log rule (ADR-171): a share on a 0-to-1 scale, the
 *     timestamped share of the non-blank lines read from a text file's start and end, at or above which
 *     the file is a log and is {@code out-of-scope}. Ships unset, per <b>observe before enforce</b>:
 *     unset (or unreadable) means no log rule, and stage 1 only reports the distribution. Its
 *     {@link Measurement} pointer is at the format-mix page.
 * @param extractionAttempt which attempt stage 2 is on (ADR-185): unset or {@code 1} is the first, a
 *     whole number from 2 up is a later one, and raising it mints a new stage-2 run, so the converter
 *     is asked again about every occurrence it got no answer about, and every later stage runs again
 *     under a run of its own. Anything that is not a whole number of 1 or more is ignored, as ADR-120
 *     ignores an unreadable number. Ships unset and is not a gate: an unset attempt never ends an
 *     invocation. No {@link Measurement} pointer and no {@code with...Measurement} method: no page's
 *     measurement informs it, and a pointer would make stage 2 write {@code profile.yaml}.
 * @param codeNameEndings stage 1's second list of file-name endings (ADR-277 §2), read as {@code
 *     outOfScopeNameEndings} is: a file detected as of no known kind or as text, whose bytes read as text
 *     and whose name ends with one, is {@code code}. Ships unset; unset or unreadable, nothing is code for
 *     its name. Its {@link Measurement} points at the format-mix page.
 * @param outOfScopeNameEndings stage 1's list of file-name endings (ADR-256): one line of endings, each
 *     beginning with a dot, matched at the end of a file's name in any case, and read only for a file
 *     detected as of no known kind or as text. Ships unset; unset or unreadable, it leaves nothing out.
 *     Its {@link Measurement} points at the format-mix page.
 * @param translationSimilarityFloor the cosine of two mean vectors at or above which a likeness across
 *     languages is applied (ADR-271 §6). Ships unset; unset or unreadable, none is applied. Not a gate.
 *     Its {@link Measurement} points at {@code translations.html}.
 * @param preferredLanguages the languages whose copy survives, most preferred first (ADR-271 §6). Ships
 *     unset; unset or unreadable, nothing is removed. Not a gate. Its {@link Measurement} points at
 *     {@code translations.html}.
 * @param configurationSettingShareFloor stage 1's rule for a configuration file (ADR-287 §7): a share on a
 *     0-to-1 scale, the setting lines among the lines counted, at or above which a file is a configuration
 *     file. Ships unset, per <b>observe before enforce</b>; unset or unreadable, no file is one. Not a gate.
 *     The last component. Its {@link Measurement} points at the format-mix page.
 */
public record Profile(
        TextValue seedFolder,
        NumericValue degenerateOutputConfidenceFloor,
        NumericValue degenerateOutputWordFloor,
        NumericValue boilerplateDocumentFrequencyFloor,
        TextValue embeddingModel,
        NumericValue relevanceScoreFloor,
        TextValue arrangementApproved,
        TextValue generationModel,
        NumericValue generationContextWindow,
        NumericValue logTimestampShareFloor,
        NumericValue extractionAttempt,
        NameEndingsValue codeNameEndings,
        NameEndingsValue outOfScopeNameEndings,
        NumericValue translationSimilarityFloor,
        LanguagesValue preferredLanguages,
        NumericValue configurationSettingShareFloor) {

    public Profile {
        seedFolder = seedFolder == null ? TextValue.unset() : seedFolder;
        degenerateOutputConfidenceFloor =
                degenerateOutputConfidenceFloor == null ? NumericValue.unset() : degenerateOutputConfidenceFloor;
        degenerateOutputWordFloor = degenerateOutputWordFloor == null ? NumericValue.unset() : degenerateOutputWordFloor;
        boilerplateDocumentFrequencyFloor =
                boilerplateDocumentFrequencyFloor == null ? NumericValue.unset() : boilerplateDocumentFrequencyFloor;
        embeddingModel = embeddingModel == null ? TextValue.unset() : embeddingModel;
        relevanceScoreFloor = relevanceScoreFloor == null ? NumericValue.unset() : relevanceScoreFloor;
        arrangementApproved = arrangementApproved == null ? TextValue.unset() : arrangementApproved;
        generationModel = generationModel == null ? TextValue.unset() : generationModel;
        generationContextWindow =
                generationContextWindow == null ? NumericValue.unset() : generationContextWindow;
        logTimestampShareFloor = logTimestampShareFloor == null ? NumericValue.unset() : logTimestampShareFloor;
        extractionAttempt = extractionAttempt == null ? NumericValue.unset() : extractionAttempt;
        outOfScopeNameEndings = outOfScopeNameEndings == null ? NameEndingsValue.unset() : outOfScopeNameEndings;
        codeNameEndings = codeNameEndings == null ? NameEndingsValue.unset() : codeNameEndings;
        translationSimilarityFloor =
                translationSimilarityFloor == null ? NumericValue.unset() : translationSimilarityFloor;
        preferredLanguages = preferredLanguages == null ? LanguagesValue.unset() : preferredLanguages;
        configurationSettingShareFloor =
                configurationSettingShareFloor == null ? NumericValue.unset() : configurationSettingShareFloor;
    }

    /**
     * Every key of the profile with its value as the operator wrote it (ADR-186, ADR-226 moving ADR-222's
     * rule 7): one entry per record component in declaration order, the component's name being the key as
     * {@code profile.yaml} names it, and {@code ""} where the value is {@code null}, so that no key is
     * missing and none shows as the word null. No key is named here, so a key the record gains is on the
     * deliverable's index with no change to this method. Unmodifiable, and kept out of {@code profile.yaml}.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.SequencedMap<String, String> keysAsWritten() {
        var keys = new java.util.LinkedHashMap<String, String>();
        for (var component : Profile.class.getRecordComponents()) {
            try {
                String value = ((ProfileValue) component.getAccessor().invoke(this)).value();
                keys.put(component.getName(), value == null ? "" : value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Could not read profile key '" + component.getName() + "' for the deliverable's index", e);
            }
        }
        return java.util.Collections.unmodifiableSequencedMap(keys);
    }

    /** A profile with every key present and none of them answered — what census drafts. */
    static Profile skeleton() {
        return new Profile(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** The same profile, with census's pointer to the seed folder's data brought up to date. */
    public Profile withSeedFolderMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder.measuredBy(measurement),
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
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
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with stage 3's pointer to {@code word-count-distribution.html} brought up to date (ADR-282). */
    public Profile withDegenerateOutputWordFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor.measuredBy(measurement),
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /**
     * The same profile, with stage 3's pointer to {@code document-frequency.html} brought up to date
     * (ADR-262). The operator's value and provenance are left as they are.
     */
    public Profile withBoilerplateDocumentFrequencyFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor.measuredBy(measurement),
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
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
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor.measuredBy(measurement),
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /**
     * The same profile with the relevance floor answered, keeping the pointer at the labelling page.
     * Only the rule of ADR-197 §4 calls this, and only over a key it may write; the provenance says so.
     */
    public Profile withRelevanceScoreFloor(String value, String provenance) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                new NumericValue(value, provenance, relevanceScoreFloor.measurement()),
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with stage 1's pointer to the format-mix page brought up to date (ADR-171). */
    public Profile withLogTimestampShareFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor.measuredBy(measurement),
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with stage 1's pointer to the format-mix page brought up to date (ADR-256). */
    public Profile withOutOfScopeNameEndingsMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings.measuredBy(measurement),
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with the pointer to {@code translations.html} brought up to date (ADR-271 §6). */
    public Profile withTranslationSimilarityFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor.measuredBy(measurement),
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with the pointer to {@code translations.html} brought up to date (ADR-271 §6). */
    public Profile withPreferredLanguagesMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages.measuredBy(measurement),
                configurationSettingShareFloor);
    }

    /** The same profile, with stage 1's pointer to the format-mix page brought up to date (ADR-277 §2). */
    public Profile withCodeNameEndingsMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings.measuredBy(measurement),
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor);
    }

    /** The same profile, with stage 1's pointer to the format-mix page brought up to date (ADR-287 §7). */
    public Profile withConfigurationSettingShareFloorMeasurement(Measurement measurement) {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                degenerateOutputWordFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel,
                generationContextWindow,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor.measuredBy(measurement));
    }
}
