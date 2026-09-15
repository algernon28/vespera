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
    private NumericValue boilerplateDocumentFrequencyFloor = NumericValue.unset();
    private TextValue embeddingModel = TextValue.unset();
    private NumericValue relevanceScoreFloor = NumericValue.unset();
    private TextValue arrangementApproved = TextValue.unset();
    private TextValue generationModel = TextValue.unset();

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
        fixture.boilerplateDocumentFrequencyFloor = existing.boilerplateDocumentFrequencyFloor();
        fixture.embeddingModel = existing.embeddingModel();
        fixture.relevanceScoreFloor = existing.relevanceScoreFloor();
        fixture.arrangementApproved = existing.arrangementApproved();
        fixture.generationModel = existing.generationModel();
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

    /** The profile itself, through the one constructor {@link Profile} has. */
    public Profile build() {
        return new Profile(
                seedFolder,
                degenerateOutputConfidenceFloor,
                boilerplateDocumentFrequencyFloor,
                embeddingModel,
                relevanceScoreFloor,
                arrangementApproved,
                generationModel);
    }
}
