package io.algernon.vespera.profile;

import java.nio.file.Path;

/**
 * A profile, written the way a test means it: one named key at a time (ADR-118).
 *
 * <p>{@link Profile} has one public constructor, and seven components of the same type. A test that
 * called it positionally said nothing about which keys it was naming, and a test that called one of
 * the arity-per-key overloads it used to carry said even less — which is why those overloads are
 * gone and this is here instead. Everything a test finds convenient about building a profile lives
 * on this side of {@code src/main}.
 *
 * <p>A key nobody names stays {@link ProfileValue#unset()}, which is what a key the file does not
 * mention loads as (ADR-062) — so a fixture that names one key produces exactly the profile an
 * operator who answered one key would have.
 *
 * <p>Each key takes either a {@code String}, which becomes an answered value with the provenance an
 * operator would have written beside it, or a whole {@link ProfileValue}, for the tests that assert
 * on provenance or on census's measurement pointer. A null {@code String} is unset rather than a
 * value of null, so a test parameterised over "answered or not" needs no ternary at the call site.
 */
public final class ProfileFixture {

    /**
     * What a test writes where an operator would have written how they arrived at the value.
     *
     * <p>Fixed rather than per-key: ADR-062 has {@code value} and {@code provenance} written
     * together, so a fixture that left provenance null would produce profiles no operator could
     * have authored. A test that cares what the provenance says passes a {@link ProfileValue}.
     */
    public static final String SET_BY_THIS_TEST = "set by this test";

    private ProfileValue seedFolder = ProfileValue.unset();
    private ProfileValue degenerateOutputConfidenceFloor = ProfileValue.unset();
    private ProfileValue boilerplateDocumentFrequencyFloor = ProfileValue.unset();
    private ProfileValue embeddingModel = ProfileValue.unset();
    private ProfileValue relevanceScoreFloor = ProfileValue.unset();
    private ProfileValue arrangementApproved = ProfileValue.unset();
    private ProfileValue generationModel = ProfileValue.unset();

    private ProfileFixture() {}

    /** A profile census has created and nobody has answered — every key present and unset. */
    public static ProfileFixture aProfile() {
        return new ProfileFixture();
    }

    /**
     * The profile as it stands, for a test that changes one key and means every other to carry.
     *
     * <p>Carrying them by hand was what the deleted overloads were for, and it is where naming keys
     * by arity did its quietest damage: a six-argument call over a seven-key record carried six and
     * silently unset the seventh.
     */
    public static ProfileFixture from(Profile profile) {
        ProfileFixture fixture = new ProfileFixture();
        fixture.seedFolder = profile.seedFolder();
        fixture.degenerateOutputConfidenceFloor = profile.degenerateOutputConfidenceFloor();
        fixture.boilerplateDocumentFrequencyFloor = profile.boilerplateDocumentFrequencyFloor();
        fixture.embeddingModel = profile.embeddingModel();
        fixture.relevanceScoreFloor = profile.relevanceScoreFloor();
        fixture.arrangementApproved = profile.arrangementApproved();
        fixture.generationModel = profile.generationModel();
        return fixture;
    }

    /**
     * An answered key, with the provenance an operator is asked to record beside it — or an unset
     * one, when there is no value, so a test parameterised over "answered or not" needs no ternary.
     */
    private static ProfileValue answeredOrUnset(String value) {
        return value == null ? ProfileValue.unset() : new ProfileValue(value, SET_BY_THIS_TEST, null);
    }

    /** Where the seed set lives (ADR-064). */
    public ProfileFixture seedFolder(Path value) {
        return seedFolder(value == null ? null : value.toString());
    }

    /** Where the seed set lives (ADR-064). */
    public ProfileFixture seedFolder(String value) {
        return seedFolder(answeredOrUnset(value));
    }

    /** Where the seed set lives, with the provenance or measurement this test is about. */
    public ProfileFixture seedFolder(ProfileValue value) {
        this.seedFolder = value;
        return this;
    }

    /** Stage 2's tier-2 quality floor over Docling's mean score (ADR-070, ADR-078). */
    public ProfileFixture degenerateOutputConfidenceFloor(String value) {
        return degenerateOutputConfidenceFloor(answeredOrUnset(value));
    }

    /** Stage 2's tier-2 quality floor, with the provenance or measurement this test is about. */
    public ProfileFixture degenerateOutputConfidenceFloor(ProfileValue value) {
        this.degenerateOutputConfidenceFloor = value;
        return this;
    }

    /** Stage 3's eventual boilerplate threshold (ADR-074). */
    public ProfileFixture boilerplateDocumentFrequencyFloor(String value) {
        return boilerplateDocumentFrequencyFloor(answeredOrUnset(value));
    }

    /** Stage 3's boilerplate threshold, with the provenance or measurement this test is about. */
    public ProfileFixture boilerplateDocumentFrequencyFloor(ProfileValue value) {
        this.boilerplateDocumentFrequencyFloor = value;
        return this;
    }

    /** Gate 3's key: which embedding model turns stage 5's scoring run on (ADR-084). */
    public ProfileFixture embeddingModel(String value) {
        return embeddingModel(answeredOrUnset(value));
    }

    /** Gate 3's key, with the provenance or measurement this test is about. */
    public ProfileFixture embeddingModel(ProfileValue value) {
        this.embeddingModel = value;
        return this;
    }

    /** Stage 5's relevance threshold (ADR-088). */
    public ProfileFixture relevanceScoreFloor(String value) {
        return relevanceScoreFloor(answeredOrUnset(value));
    }

    /** Stage 5's relevance threshold, with the provenance or measurement this test is about. */
    public ProfileFixture relevanceScoreFloor(ProfileValue value) {
        this.relevanceScoreFloor = value;
        return this;
    }

    /** The arrangement gate's key: the short id of the arrangement the operator approved (ADR-107). */
    public ProfileFixture arrangementApproved(String value) {
        return arrangementApproved(answeredOrUnset(value));
    }

    /** The arrangement gate's key, with the provenance or measurement this test is about. */
    public ProfileFixture arrangementApproved(ProfileValue value) {
        this.arrangementApproved = value;
        return this;
    }

    /** Which model stage 6b writes the synthesis docs with, overriding the default (ADR-114). */
    public ProfileFixture generationModel(String value) {
        return generationModel(answeredOrUnset(value));
    }

    /** Stage 6b's model override, with the provenance or measurement this test is about. */
    public ProfileFixture generationModel(ProfileValue value) {
        this.generationModel = value;
        return this;
    }

    /** The profile as named so far, with every key nobody named left unset. */
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
