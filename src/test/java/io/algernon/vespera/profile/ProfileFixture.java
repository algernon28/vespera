package io.algernon.vespera.profile;

/**
 * A {@link Profile} built one named key at a time, for tests (ADR-119).
 *
 * <p>It exists because the alternative was in {@code src/main}. {@link Profile} carried five
 * non-canonical constructors of arity two to six, every parameter the same type, every one of them
 * added so that the test call sites of the previous key would keep compiling when a new key arrived.
 * No production code ever called one. ADR-119 deleted them and put the convenience here, on the side
 * of the tree that wanted it.
 *
 * <p>Two properties are the whole point:
 *
 * <ul>
 *   <li><b>A call site names the keys it sets.</b> {@code profile().seedFolder(path, why)} says
 *       which key it is; {@code new Profile(a, null, b, c, null)} did not, and two call sites wanting
 *       different subsets of the same arity were indistinguishable to the compiler.
 *   <li><b>An unnamed key is unset, not absent.</b> Every field starts unset, which is the same
 *       state {@link Profile}'s canonical constructor gives a null and the same state a key missing
 *       from {@code profile.yaml} loads as (ADR-062). So an
 *       eighth key costs a field and a pair of methods here, and changes no existing call site —
 *       which is exactly what the deleted constructors were buying, bought in test code instead.
 * </ul>
 *
 * <p><b>It still builds a profile an operator could have mistyped</b> (ADR-120). Both value types
 * take any string, so {@code relevanceScoreFloor("nought point four", why)} is expressible here and
 * reads as unreadable wherever it is looked at — which is what lets a test cover what an operator can
 * actually type. A fixture that refused it would leave that behaviour untestable.
 *
 * <p>{@link #profileFrom(Profile)} is for the common shape of "load it, change one key, save it":
 * copying six keys by hand to change the seventh is how a positional call site got long enough to
 * stop being read.
 */
public final class ProfileFixture {

    private TextValue seedFolder = TextValue.unset();
    private NumericValue degenerateOutputConfidenceFloor = NumericValue.unset();
    private NumericValue degenerateOutputWordFloor = NumericValue.unset();
    private NumericValue boilerplateDocumentFrequencyFloor = NumericValue.unset();
    private TextValue embeddingModel = TextValue.unset();
    private NumericValue relevanceScoreFloor = NumericValue.unset();
    private TextValue arrangementApproved = TextValue.unset();
    private TextValue generationModel = TextValue.unset();

    private NumericValue generationContextWindow = NumericValue.unset();

    private NumericValue clusterPageByteCeiling = NumericValue.unset();

    private NumericValue logTimestampShareFloor = NumericValue.unset();

    private NumericValue extractionAttempt = NumericValue.unset();

    private NameEndingsValue codeNameEndings = NameEndingsValue.unset();

    private NameEndingsValue outOfScopeNameEndings = NameEndingsValue.unset();

    private NumericValue translationSimilarityFloor = NumericValue.unset();

    private LanguagesValue preferredLanguages = LanguagesValue.unset();

    private NumericValue configurationSettingShareFloor = NumericValue.unset();

    private TextValue deliverableLanguage = TextValue.unset();

    private ProfileFixture() {
    }

    /** A profile with every key present and none of them answered — what census drafts. */
    public static ProfileFixture profile() {
        return new ProfileFixture();
    }

    /** The profile as it stands, so a test can change one key and leave the rest exactly as found. */
    public static ProfileFixture profileFrom(Profile existing) {
        ProfileFixture fixture = new ProfileFixture();
        fixture.seedFolder = existing.seedFolder();
        fixture.degenerateOutputConfidenceFloor = existing.degenerateOutputConfidenceFloor();
        fixture.degenerateOutputWordFloor = existing.degenerateOutputWordFloor();
        fixture.boilerplateDocumentFrequencyFloor = existing.boilerplateDocumentFrequencyFloor();
        fixture.embeddingModel = existing.embeddingModel();
        fixture.relevanceScoreFloor = existing.relevanceScoreFloor();
        fixture.arrangementApproved = existing.arrangementApproved();
        fixture.generationModel = existing.generationModel();
        fixture.generationContextWindow = existing.generationContextWindow();
        fixture.clusterPageByteCeiling = existing.clusterPageByteCeiling();
        fixture.logTimestampShareFloor = existing.logTimestampShareFloor();
        fixture.extractionAttempt = existing.extractionAttempt();
        fixture.codeNameEndings = existing.codeNameEndings();
        fixture.outOfScopeNameEndings = existing.outOfScopeNameEndings();
        fixture.translationSimilarityFloor = existing.translationSimilarityFloor();
        fixture.preferredLanguages = existing.preferredLanguages();
        fixture.configurationSettingShareFloor = existing.configurationSettingShareFloor();
        fixture.deliverableLanguage = existing.deliverableLanguage();
        return fixture;
    }

    /**
     * An answered key, in the shape almost every call site wants: a value and the operator's note
     * about where it came from, with no measurement pointer.
     *
     * <p>A null {@code value} is how a test says "this key is unanswered" without reaching for a
     * different method, which matters where the value under test is the parameter of a parameterised
     * test and null is one of its cases.
     */
    private static NumericValue aNumber(String value, String provenance) {
        return new NumericValue(value, provenance, null);
    }

    /** The same, for a key whose answer is text. */
    private static TextValue someText(String value, String provenance) {
        return new TextValue(value, provenance, null);
    }

    public ProfileFixture seedFolder(String value, String provenance) {
        return seedFolder(someText(value, provenance));
    }

    public ProfileFixture seedFolder(TextValue value) {
        this.seedFolder = value == null ? TextValue.unset() : value;
        return this;
    }

    public ProfileFixture degenerateOutputConfidenceFloor(String value, String provenance) {
        return degenerateOutputConfidenceFloor(aNumber(value, provenance));
    }

    public ProfileFixture degenerateOutputConfidenceFloor(NumericValue value) {
        this.degenerateOutputConfidenceFloor = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture degenerateOutputWordFloor(String value, String provenance) {
        return degenerateOutputWordFloor(aNumber(value, provenance));
    }

    /** Stage 2's word floor (ADR-282): fewer words than it is {@code degenerate-output}. */
    public ProfileFixture degenerateOutputWordFloor(NumericValue value) {
        this.degenerateOutputWordFloor = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture boilerplateDocumentFrequencyFloor(String value, String provenance) {
        return boilerplateDocumentFrequencyFloor(aNumber(value, provenance));
    }

    public ProfileFixture boilerplateDocumentFrequencyFloor(NumericValue value) {
        this.boilerplateDocumentFrequencyFloor = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture embeddingModel(String value, String provenance) {
        return embeddingModel(someText(value, provenance));
    }

    public ProfileFixture embeddingModel(TextValue value) {
        this.embeddingModel = value == null ? TextValue.unset() : value;
        return this;
    }

    public ProfileFixture relevanceScoreFloor(String value, String provenance) {
        return relevanceScoreFloor(aNumber(value, provenance));
    }

    public ProfileFixture relevanceScoreFloor(NumericValue value) {
        this.relevanceScoreFloor = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture arrangementApproved(String value, String provenance) {
        return arrangementApproved(someText(value, provenance));
    }

    public ProfileFixture arrangementApproved(TextValue value) {
        this.arrangementApproved = value == null ? TextValue.unset() : value;
        return this;
    }

    public ProfileFixture generationModel(String value, String provenance) {
        return generationModel(someText(value, provenance));
    }

    public ProfileFixture generationModel(TextValue value) {
        this.generationModel = value == null ? TextValue.unset() : value;
        return this;
    }

    public ProfileFixture generationContextWindow(String value, String provenance) {
        return generationContextWindow(aNumber(value, provenance));
    }

    public ProfileFixture generationContextWindow(NumericValue value) {
        this.generationContextWindow = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture logTimestampShareFloor(String value, String provenance) {
        return logTimestampShareFloor(aNumber(value, provenance));
    }

    /** Stage 1's log floor (ADR-171): the share of a text file's lines that must begin with a timestamp. */
    public ProfileFixture logTimestampShareFloor(NumericValue value) {
        this.logTimestampShareFloor = value == null ? NumericValue.unset() : value;
        return this;
    }

    public ProfileFixture extractionAttempt(String value, String provenance) {
        return extractionAttempt(aNumber(value, provenance));
    }

    /** Stage 2's attempt (ADR-185): raising it asks the converter again under a run of its own. */
    public ProfileFixture extractionAttempt(NumericValue value) {
        this.extractionAttempt = value == null ? NumericValue.unset() : value;
        return this;
    }

    /** Stage 1's list of name endings kept as code (ADR-277 §2), written as one line. */
    public ProfileFixture codeNameEndings(String value, String provenance) {
        this.codeNameEndings = new NameEndingsValue(value, provenance, null);
        return this;
    }

    /** Stage 1's list of name endings left out (ADR-256), written as one line. */
    public ProfileFixture outOfScopeNameEndings(String value, String provenance) {
        this.outOfScopeNameEndings = new NameEndingsValue(value, provenance, null);
        return this;
    }

    /** Stage 5's similarity floor for copies in different languages (ADR-271 §6), a cosine. */
    public ProfileFixture translationSimilarityFloor(String value, String provenance) {
        this.translationSimilarityFloor = aNumber(value, provenance);
        return this;
    }

    /** The languages whose copy survives, most preferred first (ADR-271 §6), written as one line. */
    public ProfileFixture preferredLanguages(String value, String provenance) {
        this.preferredLanguages = new LanguagesValue(value, provenance, null);
        return this;
    }

    /** The bytes past which a cluster's page is split at an entry's end (ADR-303 §2), a whole number from 1. */
    public ProfileFixture clusterPageByteCeiling(String value, String provenance) {
        this.clusterPageByteCeiling = aNumber(value, provenance);
        return this;
    }

    /** Stage 1's floor on a text file's share of setting lines (ADR-287 §7), on a 0-to-1 scale. */
    public ProfileFixture configurationSettingShareFloor(String value, String provenance) {
        this.configurationSettingShareFloor = aNumber(value, provenance);
        return this;
    }

    /** The language the deliverable is written and drawn in (ADR-322), {@code en} or {@code it}. */
    public ProfileFixture deliverableLanguage(String value, String provenance) {
        this.deliverableLanguage = someText(value, provenance);
        return this;
    }

    /** The profile itself, through the one constructor {@link Profile} has. */
    public Profile build() {
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
                clusterPageByteCeiling,
                logTimestampShareFloor,
                extractionAttempt,
                codeNameEndings,
                outOfScopeNameEndings,
                translationSimilarityFloor,
                preferredLanguages,
                configurationSettingShareFloor,
                deliverableLanguage);
    }
}
