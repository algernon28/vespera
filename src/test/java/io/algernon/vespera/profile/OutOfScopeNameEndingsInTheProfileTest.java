package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile's {@code outOfScopeNameEndings} key (ADR-254 §2): one line of text under {@code value}, as every
 * key holds, census writes it unset into a fresh profile and writes an answer back exactly as it was written,
 * and a YAML list under {@code value} is not in the shape a key takes.
 *
 * <p>The files are written as YAML text and read through {@link ProfileStore}, and the key is asked for only by
 * its name, so this compiles before the key exists and fails where the store does not know it.
 */
@Epic("Census")
@Feature("Profile")
@Link(name = "ADR-254", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
class OutOfScopeNameEndingsInTheProfileTest {

    /** The key, as the profile names it. */
    private static final String KEY = "outOfScopeNameEndings";

    /** Endings as an operator may type them: mixed case, two spaces, a comma. */
    private static final String AS_WRITTEN = ".P3A  .p3l, .DGN";

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A fresh profile carries the list of name endings, unanswered")
    void aFreshProfileCarriesTheKeyUnset(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the key is present, and empty, among every key the profile states",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, ""));
        claim(
                "and the file census writes carries it, so an operator has it to fill in",
                () -> assertThat(Files.readString(store.file())).contains(KEY + ":"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("The name endings are read and written back exactly as the operator wrote them")
    void theEndingsAreKeptAsWritten(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(
                store.file(),
                """
                outOfScopeNameEndings:
                  value: ".P3A  .p3l, .DGN"
                  provenance: "the terminal files are not documents"
                """);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the profile states the value as written, with its case, spacing and comma",
                () -> assertThat(loaded.keysAsWritten()).containsEntry(KEY, AS_WRITTEN));
        claim(
                "and saving it again leaves the operator's text as it was",
                () -> assertThat(new ProfileStore(workingDirectory).load().keysAsWritten()).containsEntry(KEY, AS_WRITTEN));
    }

    @Test
    @Story("What a profile that does not load says")
    @DisplayName("A YAML list under the value of the name endings is refused, naming the key and the shape it takes")
    void aYamlListUnderTheValueIsRefusedNamingTheKey(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(
                store.file(),
                """
                outOfScopeNameEndings:
                  value:
                    - .P3A
                    - .DGN
                  provenance: "written as a list"
                """);

        claim(
                "the load fails with the one line the operator is told, naming the key and saying it is not in the"
                        + " shape a key takes, which is one line of text under value",
                () -> assertThatThrownBy(store::load)
                        .isInstanceOf(MisshapenProfileException.class)
                        .hasMessageContaining(KEY)
                        .hasMessageContaining("is not in the shape a profile key takes"));
    }
}
