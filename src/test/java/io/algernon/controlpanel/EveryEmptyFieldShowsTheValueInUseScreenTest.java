package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every field left empty shows the value in use where one exists, and keeps its example and its Browse button where
 * none does (ADR-340 §6): a value of the profile the jar says lies beneath it shows that value; the documents folder,
 * the work folder, the seed folder and a code set folder, none of which has a default the control panel would use,
 * keep their examples. The field stays empty either way, so that nothing the operator did not type is saved.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class EveryEmptyFieldShowsTheValueInUseScreenTest {

    /** The writing model the jar said it would use where the profile names none. */
    private static final String THE_JARS_WRITING_MODEL = "a-writing-model:8b";

    @TempDir
    Path folder;

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("An empty value of the run settings shows the value the jar said it uses, and one with nothing beneath it keeps its example")
    void anEmptyValueOfTheProfileShowsTheValueTheJarSaidItUses() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        Files.writeString(work.resolve(EffectiveValues.FILE_NAME), """
                written-at=2026-11-04T10:15:30+01:00
                generationModel=%s
                generationModel.source=shipped
                """.formatted(THE_JARS_WRITING_MODEL), UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work))) {
            Element profile = form(screens.browser().go("/profile").page(), "profile");
            Element model = profile.selectFirst("[name=" + ProfileKey.GENERATION_MODEL.key() + "]");
            Element floor = profile.selectFirst("[name=" + ProfileKey.RELEVANCE_SCORE_FLOOR.key() + "]");

            claim("the writing model's field is empty, so a save writes nothing for it",
                    () -> assertThat(model.val()).isEmpty());
            claim("its placeholder is the model the jar said it uses, in use",
                    () -> assertThat(model.attr("placeholder")).isEqualTo("in use: " + THE_JARS_WRITING_MODEL));
            claim("and the relevance floor, which has nothing beneath it, keeps its example",
                    () -> assertThat(floor.attr("placeholder")).isEqualTo("e.g. " + ProfileKey.RELEVANCE_SCORE_FLOOR.example()));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("The documents folder, the seed folder and a code set folder have no default, and keep their examples and their Browse buttons")
    void aFolderWithNoDefaultKeepsItsExampleAndItsBrowseButton() throws IOException {
        String corpusRoot;
        List<String> launchBrowse;
        try (ScreenServer screens = ScreenServer.start(folder.resolve("nothing-named"),
                new LaunchValues("", "", "", "", "", "", "", ""))) {
            Element values = form(screens.browser().page(), "launch-values");
            corpusRoot = placeholderOf(values, "vespera.corpus-root");
            launchBrowse = browseNames(values);
        }
        Path work = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder.resolve("work-named"),
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work))) {
            Page drawer = screens.browser().go("/?open=values-profile").page();
            Element profile = form(drawer, "profile");
            Element codeSetFolders = form(drawer, "code-set-folders");

            claim("the documents folder, left empty, shows its example",
                    () -> assertThat(corpusRoot).isEqualTo("e.g. D:\\archive\\corpus"));
            claim("and keeps its Browse button",
                    () -> assertThat(launchBrowse).contains("browse-vespera.corpus-root"));
            claim("the seed folder of the run settings shows its example, beside its Browse button",
                    () -> assertThat(List.of(placeholderOf(profile, "seedFolder"),
                                    browseNames(profile).contains("browse-profile-seedFolder")))
                            .containsExactly("e.g. " + ProfileKey.SEED_FOLDER.example(), true));
            claim("and so does a new code set folder outside the corpus root",
                    () -> assertThat(List.of(placeholderOf(codeSetFolders, "new-code-set-folder"),
                                    browseNames(codeSetFolders).contains("browse-code-set-folder")))
                            .containsExactly("e.g. " + ProfileKey.CODE_SET_FOLDERS.example(), true));
        }
    }

    /**
     * The control panel's current directory is the test JVM's, which the test cannot choose, so the default is worked
     * out from it, the rule being the record's; where a {@code .vespera} lies there, as in a checkout someone runs
     * Vespera from, Run would start a command in it, so the test is aborted rather than run.
     */
    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("An empty work folder is the jar's default, .vespera in the folder the control panel was started from, which Run uses")
    void anEmptyWorkFolderIsTheJarsDefaultWhichRunPasses() throws IOException {
        Path theDefault = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve(".vespera");
        assumeFalse(Files.exists(theDefault), "a .vespera folder lies where the tests run, and Run would start in it");
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), "", "", "", "", "", "", ""))) {
            PanelBrowser browser = screens.browser();
            Page home = browser.page();
            Element field = form(home, "launch-values").selectFirst("[name='vespera.working-dir']");
            Element inUse = byId(home, "in-use-vespera.working-dir");
            Page refused = browser.press("launch", "run", Map.of()).page();

            claim("the field stays empty, its placeholder the default in use",
                    () -> assertThat(List.of(field.val(), field.attr("placeholder")))
                            .containsExactly("", "in use: " + theDefault));
            claim("and beside it the page says the default is in use, at that folder",
                    () -> assertThat(List.of(inUse.attr("data-value"), inUse.attr("data-source")))
                            .containsExactly(theDefault.toString(), "default"));
            claim("Run no longer asks for a work folder: it starts on the default, which is not there, and is refused"
                            + " by the line of a work folder that is no folder, naming it",
                    () -> assertThat(List.of(refused.address().getPath(), byId(refused, "outcome").text()))
                            .containsExactly("/", "Run is not started: the working directory " + theDefault
                                    + " is no folder that is there"));
            claim("and nothing made the folder",
                    () -> assertThat(theDefault).doesNotExist());
        }
    }

    private static String placeholderOf(Element form, String name) {
        Element field = form.selectFirst("[name='" + name + "']");
        if (field == null) {
            throw new AssertionError("the form '" + form.attr("name") + "' holds no control named '" + name + "'");
        }
        return field.attr("placeholder");
    }
}
