package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile record itself, apart from {@link ProfileStoreTest}'s file round-trip: what a fresh
 * skeleton carries for each key a ticket has added, and that the record still has exactly one way to
 * build it (ADR-118).
 *
 * <p>ADR-062's "a key the file predates is added unset" is pinned at the YAML boundary, by {@link
 * ProfileStoreTest}, which is where a file written before a key existed is actually read. It was
 * pinned here too, once per arity, by four tests over the constructor overloads ADR-118 deleted.
 */
@Epic("Census")
@Feature("Profile")
@Issue("58")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
@Link(name = "ADR-062", url = Adr.CENSUS_MERGES_AND_NEVER_OVERWRITES, type = "adr")
class ProfileTest {

    @Test
    @Story("Stage 3's boilerplate floor ships unset")
    @DisplayName("A fresh skeleton carries the boilerplate-document-frequency-floor key, unset")
    void skeletonCarriesTheBoilerplateFloorUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the key #58 adds is present rather than missing, and unanswered rather than guessed at"
                        + " -- stage 3 only measures document frequency, nothing here applies a floor yet",
                () -> assertThat(skeleton.boilerplateDocumentFrequencyFloor().isSet()).isFalse());
    }

    @Test
    @Story("Gate 3's model key ships unset")
    @DisplayName("A fresh skeleton carries the embedding-model key, unset")
    @Issue("107")
    void skeletonCarriesTheEmbeddingModelKeyUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the model key #107 adds is present rather than missing, and unanswered rather than"
                        + " guessed at -- naming a model is purely the operator's call",
                () -> assertThat(skeleton.embeddingModel().isSet()).isFalse());
    }

    @Test
    @Story("Stage 5's relevance floor ships unset")
    @DisplayName("A fresh skeleton carries the relevance-score-floor key, unset")
    @Issue("110")
    @Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
    void skeletonCarriesTheRelevanceScoreFloorUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the floor #110 adds is present rather than missing, and unanswered rather than guessed"
                        + " at -- the value is read off sixty answers a person has not given yet, and"
                        + " while it is unset the run scores everything and removes nothing",
                () -> assertThat(skeleton.relevanceScoreFloor().isSet()).isFalse());
    }

    @Test
    @Story("Stage 5's relevance floor ships unset")
    @DisplayName("The floor points at the labelling report, so the person setting it knows what to read")
    @Issue("110")
    @Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
    void theFloorPointsAtTheLabellingReport() {
        Measurement labellingReport = new Measurement("relevance-labelling.html", java.time.Instant.parse("2026-09-09T10:00:00Z"));

        Profile pointed = Profile.skeleton().withRelevanceScoreFloorMeasurement(labellingReport);

        claim(
                "the key names the page the number is read off, which is the whole of what tells an"
                        + " operator where a threshold is supposed to come from",
                () -> assertThat(pointed.relevanceScoreFloor().measurement()).isEqualTo(labellingReport));
        claim(
                "pointing at the measurement does not answer the key: the value stays unset until a"
                        + " person writes one, because nothing may guess a threshold",
                () -> assertThat(pointed.relevanceScoreFloor().isSet()).isFalse());
    }

    @Test
    @Story("The arrangement approval ships unset")
    @DisplayName("A fresh skeleton carries the arrangement-approved key, unset")
    @Issue("175")
    @Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
    void skeletonCarriesTheArrangementApprovedKeyUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the approval is present as a question rather than missing, and unanswered rather than"
                        + " assumed -- nothing may be written over an arrangement nobody has looked at, and"
                        + " an approval that defaulted to yes would be exactly that",
                () -> assertThat(skeleton.arrangementApproved().isSet()).isFalse());
    }

    @Test
    @Story("The record has one way to build it")
    @DisplayName("Profile declares exactly one public constructor")
    @Issue("197")
    @Link(name = "ADR-118", url = Adr.PROFILES_CANONICAL_CONSTRUCTOR_IS_ITS_ONLY_PUBLIC_ONE, type = "adr")
    void profileDeclaresExactlyOnePublicConstructor() {
        claim(
                "the canonical constructor is the only one, so a call site cannot name a subset of the"
                        + " keys by arity alone -- five such overloads had accreted, one per key, every"
                        + " parameter the same type, and all forty-three of their callers were tests",
                () -> assertThat(Profile.class.getConstructors()).hasSize(1));
        claim(
                "and the one it declares takes every key, so nothing can be built with some of them"
                        + " silently unset by position",
                () -> assertThat(Profile.class.getConstructors()[0].getParameterCount())
                        .isEqualTo(Profile.class.getRecordComponents().length));
    }
}
