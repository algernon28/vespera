package io.algernon.vespera.ui;

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
 * What a folder field of the window says of its folder (ADR-331 §6, which ADR-342 keeps): not chosen, found with the
 * free space of its drive, to be created where the jar creates it, or no such folder, by {@code FolderStatus}, where
 * the rule the pages held now lives (ADR-344 §8).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class FolderStatusTest {

    @TempDir
    Path folder;

    @Test
    @Story("The operator chooses the folders")
    @DisplayName("A folder field says not chosen, found with its free space, will be created, or no such folder")
    void aFolderFieldSaysHowItsFolderStands() throws IOException {
        Path there = Files.createDirectory(folder.resolve("there"));
        String missing = folder.resolve("not-there").toString();

        claim("an empty field: not chosen",
                () -> assertThat(FolderStatus.of("  ", false)).isEqualTo(new FolderStatus("not-chosen", "Not chosen")));
        claim("a folder that is there: found, with the free space of its drive in GiB to one decimal",
                () -> assertThat(FolderStatus.of(there.toString(), false)).satisfies(status -> {
                    assertThat(status.state()).isEqualTo("found");
                    assertThat(status.words()).matches("Found, [0-9]+\\.[0-9] GiB free");
                }));
        claim("a folder that is not there, where the jar makes it, as it makes the working directory: will be created",
                () -> assertThat(FolderStatus.of(missing, true))
                        .isEqualTo(new FolderStatus("will-be-created", "Will be created")));
        claim("and where the jar makes none, as of the corpus root: no such folder",
                () -> assertThat(FolderStatus.of(missing, false))
                        .isEqualTo(new FolderStatus("no-such-folder", "No such folder")));
    }
}
