package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The code-set names, {@code /code-sets} (ADR-302 §8): each entry of {@code code-set-names.yaml} with a field to
 * rename it, and an empty row to add one, written whole as the list the operator left.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class CodeSetsScreenTest {

    /** One code set's name, as the operator wrote it. */
    private static final String ONE_NAME = """
            ---
            - folder: "tools/build"
              name: "Build tools"
            """;

    @TempDir
    Path folder;

    @Test
    @Story("The operator names the code sets")
    @DisplayName("Each code set's name can be changed and one added, and the list is written as left")
    void aCodeSetIsRenamedAndOneAddedAndTheListWritten() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        Files.writeString(names, ONE_NAME, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            Element codeSets = form(browser.page(), "code-sets");

            claim("the form holds the file's one entry, its folder and its name, then an empty row to add one",
                    () -> assertThat(controlNames(codeSets))
                            .containsExactly("folder-1", "name-1", "new-folder", "new-name"));
            claim("the entry is the file's",
                    () -> assertThat(List.of(codeSets.select("[name=folder-1]").val(),
                                    codeSets.select("[name=name-1]").val()))
                            .containsExactly("tools/build", "Build tools"));
            claim("its one button is Save",
                    () -> assertThat(buttonNames(codeSets)).containsExactly("save"));

            Page landed = browser.press("code-sets", "save",
                    Map.of("name-1", "Builders", "new-folder", "app", "new-name", "The app")).page();

            claim("saving lands back on the code sets",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/code-sets"));
            claim("and the file holds the entry renamed, then the one added, in that order",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(names, StandardCharsets.UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename("tools/build", "Builders"),
                                    new CodeSetRenames.Rename("app", "The app")));
        }
    }
}
