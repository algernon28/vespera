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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every key of the profile with its value as the operator wrote it, read off the record in {@code
 * profile}, which owns the profile's shape (ADR-226, moving ADR-222's rule 7; ADR-186, ADR-061).
 *
 * <p>The deliverable's index states every key, and one with no value as empty (ADR-186 §1, §2). Since
 * ADR-226 generation's version names {@code profile}, so a change to which keys there are, or to how a value
 * is shown, mints generation again.
 *
 * <p>Compared with {@link ProfileKeys}, the test tree's one reading of the record, so that this claim is
 * not the record compared with itself by the same code; each value is compared with the text this class
 * wrote, or with empty, rather than worked out from the record (#681).
 */
@Epic("Census")
@Feature("Profile")
@Issue("479")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-186", url = Adr.THE_INDEX_STATES_EVERY_PROFILE_KEY_READ_OFF_THE_RECORD, type = "adr")
class ProfileKeysAsWrittenTest {

    private static final String SEED_FOLDER = "C:/seeds";

    /** Written with a trailing zero, so that a value normalised on its way to the page would show. */
    private static final String A_FLOOR_AS_WRITTEN = "0.50";

    @Test
    @Story("The deliverable's index states every profile key")
    @DisplayName("Every key of the profile is there, in the record's order, each with its value as written or empty")
    void everyKeyAsWritten() {
        Profile profile = ProfileFixture.profile()
                .seedFolder(SEED_FOLDER, "set by this test")
                .relevanceScoreFloor(A_FLOOR_AS_WRITTEN, "set by this test")
                .build();

        SequencedMap<String, String> keys = profile.keysAsWritten();

        claim(
                "the keys are every key the record declares, in the order it declares them, so a key the"
                        + " record gains is on the index with no change anywhere else",
                () -> assertThat(new ArrayList<>(keys.keySet())).containsExactlyElementsOf(ProfileKeys.everyKey()));
        claim(
                "the two set here are shown as the text the operator wrote, not a number read back and printed"
                        + " again",
                () -> assertThat(keys)
                        .containsEntry("seedFolder", SEED_FOLDER)
                        .containsEntry("relevanceScoreFloor", A_FLOOR_AS_WRITTEN));
        claim(
                "and every key with no value is empty rather than missing or the word null",
                () -> assertThat(keys.entrySet().stream()
                                .filter(key -> !List.of("seedFolder", "relevanceScoreFloor").contains(key.getKey()))
                                .map(Map.Entry::getValue)
                                .toList())
                        .hasSize(keys.size() - 2)
                        .containsOnly(""));
    }

    @Test
    @Story("The deliverable's index states every profile key")
    @DisplayName("The keys handed out cannot be changed by whoever is handed them")
    void theKeysCannotBeChanged() {
        SequencedMap<String, String> keys = ProfileFixture.profile().build().keysAsWritten();

        claim(
                "the map cannot be added to, so whoever writes the index cannot add a key the profile does not"
                        + " carry",
                () -> assertThatThrownBy(() -> keys.put("aKeyTheProfileDoesNotCarry", ""))
                        .isInstanceOf(UnsupportedOperationException.class));
    }

    @Test
    @Story("The deliverable's index states every profile key")
    @DisplayName("Saving the profile writes no key for the reading of its keys")
    void theReadingIsNotWrittenToTheFile(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        store.save(ProfileFixture.profile().seedFolder(SEED_FOLDER, "set by this test").build());

        claim(
                "profile.yaml carries the keys the operator edits and nothing else: the reading of every key is"
                        + " a method of the record, and saving it as a key of its own would put a key in the"
                        + " file nobody wrote and the record does not declare",
                () -> assertThat(Files.readString(store.file())).doesNotContain("keysAsWritten"));
    }
}
