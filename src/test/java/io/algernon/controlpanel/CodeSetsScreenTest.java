package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
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
 * The code-set names (ADR-302 §8), named in the tree of folders to process since ADR-340 §5 took the page
 * {@code /code-sets} in: each entry of {@code code-set-names.yaml} shown on its folder's row with its name, which can be
 * changed, and a folder marked as code with a name added, the file written as the operator left it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class CodeSetsScreenTest {

    /** One code set's name, as the operator wrote it. */
    private static final String ONE_NAME = """
            ---
            - folder: "tools/build"
              name: "Build tools"
            """;

    /** The tree's form. */
    private static final String TREE = "folders-to-process";

    @TempDir
    Path folder;

    @Test
    @Story("The operator names the code sets")
    @DisplayName("Each code set's name can be changed and one added, and the list is written as left")
    void aCodeSetIsRenamedAndOneAddedAndTheListWritten() throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("tools").resolve("build"));
        Files.createDirectories(corpus.resolve("app"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        Files.writeString(names, ONE_NAME, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus&tree=tools");
            Page atTools = browser.page();
            Element build = rowOf(atTools, "tools/build");
            String b = indexOf(build);

            claim("the folder the file names is shown marked, its name the file's",
                    () -> assertThat(List.of(build.attr("data-code-set"),
                                    form(atTools, TREE).select("[name=code-set-name-" + b + "]").val()))
                            .containsExactly("marked", "Build tools"));

            Page renamed = browser.press(TREE, "save", Map.of("code-set-name-" + b, "Builders")).page();
            browser.go("/?open=values-corpus");
            String a = indexOf(rowOf(browser.page(), "app"));
            browser.press(TREE, "save", Map.of("code-set-" + a, "on", "code-set-name-" + a, "The app"));

            claim("saving lands back in the drawer's folders to process",
                    () -> assertThat(List.of(renamed.address().getPath(), String.valueOf(renamed.address().getFragment())))
                            .containsExactly("/", "values-corpus"));
            claim("and the file holds the entry renamed, then the one added, in that order",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(names, StandardCharsets.UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename("tools/build", "Builders"),
                                    new CodeSetRenames.Rename("app", "The app")));
        }
    }

    private static Element rowOf(Page page, String folder) {
        Element row = byId(page, "tree-rows").selectFirst("li[data-folder='" + folder + "']");
        if (row == null) {
            throw new AssertionError("the tree holds no row of " + folder);
        }
        return row;
    }

    /** The number {@code i} of a row, read off its hidden {@code folder-<i>}. */
    private static String indexOf(Element row) {
        return row.selectFirst("input[name^=folder-]").attr("name").substring("folder-".length());
    }
}
