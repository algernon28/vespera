package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile as a file a person edits and census merges into (ADR-061, ADR-062).
 *
 * <p>The asymmetry is the whole decision, so it is what these pin: an answer already in the file is
 * census's to leave alone, and census's own pointer to where the data lives is census's to refresh.
 * Getting that backwards would silently overwrite a judgement nobody can recompute.
 */
@Epic("Census")
@Feature("Profile")
@Issue("13")
@Link(name = "ADR-061", url = Adr.PROFILE_IS_YAML_TYPED_RECORDS, type = "adr")
@Link(name = "ADR-062", url = Adr.CENSUS_MERGES_AND_NEVER_OVERWRITES, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
class ProfileStoreTest {

    private static final Instant FIRST_RUN = Instant.parse("2026-08-29T10:15:30Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-08-30T11:00:00Z");

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A corpus with no profile yet gets every key the code knows about, all unset")
    void writesACompleteSkeletonForAFreshCorpus(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the seed-folder key is present rather than missing, and unanswered rather than guessed",
                () -> assertThat(loaded.seedFolder().isSet()).isFalse());
        claim(
                "so is stage 2's tier-2 confidence floor -- unset per observe-before-enforce, not guessed at"
                        + " (ADR-070)",
                () -> assertThat(loaded.degenerateOutputConfidenceFloor().isSet()).isFalse());
        claim(
                "the file now exists, so an operator has something to edit",
                () -> assertThat(Files.exists(workingDirectory.resolve("profile.yaml"))).isTrue());
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A value and its provenance survive a second census run untouched")
    void neverTouchesAnAnswerAlreadyInTheFile(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        store.save(new Profile(
                new ProfileValue(
                        "C:/seeds", "chosen by the archivist from the 2019 handover", new Measurement("walk 1", FIRST_RUN)),
                null));

        Profile reloaded = store.load();
        store.save(reloaded.withSeedFolderMeasurement(new Measurement("walk 7", SECOND_RUN)));
        Profile afterTheSecondRun = store.load();

        claim(
                "the operator's value is exactly what it was before the second run",
                () -> assertThat(afterTheSecondRun.seedFolder().value()).isEqualTo("C:/seeds"));
        claim(
                "so is the provenance they wrote beside it",
                () -> assertThat(afterTheSecondRun.seedFolder().provenance())
                        .isEqualTo("chosen by the archivist from the 2019 handover"));
        claim(
                "and census's own pointer moved on to the walk the second run made",
                () -> assertThat(afterTheSecondRun.seedFolder().measurement())
                        .isEqualTo(new Measurement("walk 7", SECOND_RUN)));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A key the file predates is added unset, without disturbing the file's answers")
    void addsAKeyTheFileDoesNotYetMention(@TempDir Path workingDirectory) throws IOException {
        // A profile written before the seed-folder key existed: no keys at all.
        Files.writeString(workingDirectory.resolve("profile.yaml"), "{}");
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();

        claim(
                "the key the code has since learned about is present and unset, which is the same gated"
                        + " state as a key nobody has answered",
                () -> assertThat(loaded.seedFolder()).isEqualTo(ProfileValue.unset()));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A key nobody recognises fails the load rather than being ignored")
    void refusesAKeyTheCodeDoesNotKnow(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), "seedFolders:\n  value: C:/seeds\n");
        ProfileStore store = new ProfileStore(workingDirectory);

        claim(
                "a misspelled key stops the load, rather than leaving the real key silently unset and the"
                        + " pipeline gating on it",
                () -> assertThatThrownBy(store::load).hasMessageContaining("seedFolders"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A profile written out is a profile that reads back the same")
    void writesWhatItCanReadBack(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        Profile written = new Profile(
                new ProfileValue("C:/seeds", "the archivist's pick", new Measurement("walk 1", FIRST_RUN)), null);

        store.save(written);

        claim(
                "the profile round-trips through YAML unchanged, timestamps included",
                () -> assertThat(store.load()).isEqualTo(written));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("An operator-set tier-2 confidence floor round-trips like any other answered key")
    void anOperatorSetConfidenceFloorRoundTrips(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        Profile written = new Profile(
                null,
                new ProfileValue(
                        "0.5", "matched to Docling's own poor/fair cut-off", new Measurement("run 3", FIRST_RUN)));

        store.save(written);
        Profile reloaded = store.load();

        claim(
                "the operator's threshold value survives the round trip",
                () -> assertThat(reloaded.degenerateOutputConfidenceFloor().value()).isEqualTo("0.5"));
        claim(
                "and it now reads as set, the same isSet() check every other answered key uses",
                () -> assertThat(reloaded.degenerateOutputConfidenceFloor().isSet()).isTrue());
    }
}
