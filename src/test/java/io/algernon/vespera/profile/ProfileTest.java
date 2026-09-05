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
 * skeleton carries for the key #58 adds, and that a two-key call site the key predates still compiles
 * and still merges the third key in unset (ADR-062's "a key the file predates is added unset,"
 * exercised here at the constructor rather than through YAML).
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
    @Story("A key the file predates is added unset")
    @DisplayName("The two-key constructor every call site before #58 used still defaults the third key unset")
    void theTwoKeyConstructorDefaultsTheThirdKeyUnset() {
        Profile profile = new Profile(ProfileValue.unset(), ProfileValue.unset());

        claim(
                "a call site that has not been touched since #58 still gets a profile whose third key"
                        + " reads exactly like any other key nobody has answered",
                () -> assertThat(profile.boilerplateDocumentFrequencyFloor()).isEqualTo(ProfileValue.unset()));
    }
}
