package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile's {@code configurationSettingShareFloor} key (ADR-287 §7): a number under {@code value}, as every key
 * holds one line of text, the last key of the profile; census writes it unset into a fresh profile, writes an answer
 * back exactly as it was written, and loads a value no number can be read from, since an unreadable value is a state
 * of its own and not a profile that fails to load (ADR-120).
 *
 * <p>The files are written as YAML text and read through {@link ProfileStore}, and the key is asked for only by its
 * name, so this compiles before the key exists and fails where the store does not know it.
 */
@Epic("Census")
@Feature("Profile")
@Issue("575")
@Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
class ConfigurationSettingShareFloorInTheProfileTest {

    /** The key, as the profile names it. */
    private static final String KEY = "configurationSettingShareFloor";

    /** A floor as an operator may write it. */
    private static final String A_FLOOR = "0.80";

    /** A floor written with a decimal comma, from which no number is read. */
    private static final String NOT_A_NUMBER = "0,8";

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A fresh profile carries the floor on the share of settings, unanswered, as its last key")
    void aFreshProfileCarriesTheKeyUnsetAsItsLastKey(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the key is present, and empty, among every key the profile states",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, ""));
        claim(
                "it is the last of the profile's keys",
                () -> assertThat(ProfileKeys.everyKey().getLast()).isEqualTo(KEY));
        claim(
                "and the file census writes carries it, so an operator has it to fill in",
                () -> assertThat(Files.readString(store.file())).contains(KEY + ":"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The floor on the share of settings is read and written back exactly as the operator wrote it")
    void theFloorIsKeptAsWritten(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), KEY + ":\n  value: \"" + A_FLOOR + "\"\n  provenance: \"read off the page\"\n");

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the profile states the value as written, its trailing nought included",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, A_FLOOR));
        claim(
                "and saving it again leaves the operator's text as it was",
                () -> assertThat(new ProfileStore(workingDirectory).load().keysAsWritten()).containsEntry(KEY, A_FLOOR));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A floor on the share of settings that is not a number still loads, as written")
    void aFloorThatIsNotANumberLoads(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), KEY + ":\n  value: \"" + NOT_A_NUMBER + "\"\n  provenance: \"typed in a hurry\"\n");

        Profile loaded = store.load();

        claim(
                "the profile loads and states what is written, for the closing line to quote back",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, NOT_A_NUMBER));
    }
}
