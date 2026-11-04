package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

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
 * the seed folder and a code set folder, none of which has a default the control panel would use, keep their examples;
 * and the work folder is the one the jar would use on this machine, named by a system property or an environment
 * variable, or else {@code .vespera} under the control panel's current directory, which Run, Label and Publish pass and
 * start in. The field stays empty either way, so that nothing the operator did not type is saved.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class EveryEmptyFieldShowsTheValueInUseScreenTest {

    /** The writing model the jar said it would use where the profile names none. */
    private static final String THE_JARS_WRITING_MODEL = "a-writing-model:8b";

    /** What the child is told in the folder it is started in: print its arguments, then end. */
    private static final String SAYS_ITS_ARGUMENTS_AND_ENDS = JarForScreenTests.SAY_THE_ARGUMENTS
            + "exit 0 the closing line";

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

    /** The control panel's current directory is the test's folder, which {@link ScreenServer#start} gives it. */
    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("An empty work folder is the jar's default, .vespera in the folder the control panel was started from, which Run uses")
    void anEmptyWorkFolderIsTheJarsDefaultWhichRunPasses() throws IOException {
        Path theDefault = ScreenServer.defaultWorkingDirectoryIn(folder);
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
            claim("and beside it the page says the default is in use, at that folder, named after no property or variable",
                    () -> assertThat(List.of(inUse.attr("data-value"), inUse.attr("data-source"),
                                    inUse.hasAttr("data-source-name"), inUse.text()))
                            .containsExactly(theDefault.toString(), "default", false, "In use: " + theDefault
                                    + ", the .vespera folder of the folder the control panel was started from."));
            claim("Run no longer asks for a work folder: it starts on the default, which is not there, and is refused"
                            + " by the line of a work folder that is no folder, naming it",
                    () -> assertThat(List.of(refused.address().getPath(), byId(refused, "outcome").text()))
                            .containsExactly("/", "Run is not started: the working directory " + theDefault
                                    + " is no folder that is there"));
            claim("and nothing made the folder",
                    () -> assertThat(theDefault).doesNotExist());
        }
    }

    /**
     * The child prints its arguments only where the file in the directory it is started in tells it to, so a line of
     * them in the tail says both what it was given and where it was started (ADR-340 §6).
     */
    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("With the work folder left empty and the default there, Run, Label and Publish pass the default and start in it")
    void anEmptyWorkFolderThatIsThereIsPassedToRunLabelAndPublishAndTheyStartInIt()
            throws IOException, InterruptedException {
        Path theDefault = Files.createDirectories(ScreenServer.defaultWorkingDirectoryIn(folder));
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        ScreenServer.childWill(theDefault, SAYS_ITS_ARGUMENTS_AND_ENDS);
        Path confluence = Files.createDirectories(
                theDefault.resolve("deliverable").resolve("abcdef0123456789").resolve("confluence"));
        Files.writeString(confluence.resolve("pages.json"), "{}", UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues(corpusRoot.toString(), "", "", "", "", "", "", ""))) {
            PanelBrowser browser = screens.browser();
            String run = byId(ScreenServer.whenTheChildHasEnded(browser.press("launch", "run", Map.of())), "tail")
                    .text();
            String label = byId(ScreenServer.whenTheChildHasEnded(browser.go("/").press("launch", "label", Map.of())),
                    "tail").text();
            String publish = byId(ScreenServer.whenTheChildHasEnded(
                    browser.go("/invocation").press("publish", "publish", Map.of())), "tail").text();

            claim("Run passed the default as the working directory, and was started in it",
                    () -> assertThat(run).contains("run " + corpusRoot.toAbsolutePath() + " --db-dir=" + theDefault));
            claim("so did Label",
                    () -> assertThat(label).contains("label --db-dir=" + theDefault));
            claim("and so did Publish",
                    () -> assertThat(publish).containsPattern("publish --to \\S+ --db-dir="
                            + java.util.regex.Pattern.quote(theDefault.toString())));
        }
    }

    /**
     * The machine names its own work folder by {@code VESPERA_WORKING_DIR}, as Spring's relaxed binding reads
     * {@code vespera.working-dir} (ADR-340 §6): the field left empty shows that folder, and Run passes it and starts in
     * it, never in {@code .vespera} under the current directory, which would override the machine's folder.
     */
    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("With the work folder left empty, the folder this machine's environment names is in use, which Run passes and starts in")
    void anEmptyWorkFolderIsTheOneTheEnvironmentNamesWhichRunPassesAndStartsIn()
            throws IOException, InterruptedException {
        Path configured = Files.createDirectories(folder.resolve("this-machines-work-folder"));
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        ScreenServer.childWill(configured, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues(corpusRoot.toString(), "", "", "", "", "", "", ""), Map.of(),
                Map.of("VESPERA_WORKING_DIR", configured.toString()))) {
            PanelBrowser browser = screens.browser();
            Page home = browser.page();
            Element field = form(home, "launch-values").selectFirst("[name='vespera.working-dir']");
            Element inUse = byId(home, "in-use-vespera.working-dir");
            String run = byId(ScreenServer.whenTheChildHasEnded(browser.press("launch", "run", Map.of())), "tail")
                    .text();

            claim("the field stays empty, its placeholder the folder the environment names",
                    () -> assertThat(List.of(field.val(), field.attr("placeholder")))
                            .containsExactly("", "in use: " + configured));
            claim("and beside it the page says that folder is in use, from the environment variable it names",
                    () -> assertThat(List.of(inUse.attr("data-value"), inUse.attr("data-source"),
                                    inUse.attr("data-source-name"), inUse.text()))
                            .containsExactly(configured.toString(), "environment", "VESPERA_WORKING_DIR",
                                    "In use: " + configured + ", from the environment variable VESPERA_WORKING_DIR."));
            claim("Run passed that folder as the working directory, and was started in it",
                    () -> assertThat(run).contains("run " + corpusRoot.toAbsolutePath() + " --db-dir=" + configured));
            claim("and nothing made .vespera under the current directory",
                    () -> assertThat(ScreenServer.defaultWorkingDirectoryIn(folder)).doesNotExist());
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A system property naming the work folder comes before the environment's, and Label passes it")
    void aSystemPropertyNamingTheWorkFolderComesBeforeTheEnvironmentAndLabelPassesIt()
            throws IOException, InterruptedException {
        Path byProperty = Files.createDirectories(folder.resolve("named-by-a-property"));
        Path byVariable = Files.createDirectories(folder.resolve("named-by-a-variable"));
        ScreenServer.childWill(byProperty, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), "", "", "", "", "", "",
                        ""),
                Map.of("vespera.working-dir", byProperty.toString()),
                Map.of("VESPERA_WORKING_DIR", byVariable.toString()))) {
            PanelBrowser browser = screens.browser();
            Element inUse = byId(browser.page(), "in-use-vespera.working-dir");
            String label = byId(ScreenServer.whenTheChildHasEnded(browser.press("launch", "label", Map.of())), "tail")
                    .text();

            claim("the folder in use is the system property's, and the page says so in words",
                    () -> assertThat(List.of(inUse.attr("data-value"), inUse.attr("data-source"),
                                    inUse.attr("data-source-name"), inUse.text()))
                            .containsExactly(byProperty.toString(), "system-property", "vespera.working-dir",
                                    "In use: " + byProperty + ", from the system property vespera.working-dir the"
                                            + " control panel was started with."));
            claim("and Label passed it as the working directory, started in it",
                    () -> assertThat(label).contains("label --db-dir=" + byProperty));
        }
    }

    /**
     * A value the machine names that no path can hold is the work folder as it stands (ADR-340 §6): the page names it as
     * given, a character below U+0020 in it written as {@code U+} and four hexadecimal digits, says no working directory
     * this system can hold is chosen and holds no folder in use, and Run refuses it as it refuses such a folder typed.
     */
    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A work folder the environment names that no path can hold is named as given, no working directory is chosen, and Run refuses it")
    void aWorkFolderTheEnvironmentNamesThatNoPathCanHoldIsNamedAndRunRefusesIt() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), "", "", "", "", "", "",
                        ""),
                Map.of(), Map.of("VESPERA_WORKING_DIR", "not\0a-path"))) {
            PanelBrowser browser = screens.browser();
            Page home = browser.page();
            Element field = form(home, "launch-values").selectFirst("[name='vespera.working-dir']");
            Element inUse = byId(home, "in-use-vespera.working-dir");
            Element progress = byId(home, "progress-view");
            Page refused = browser.press("launch", "run", Map.of()).page();

            claim("the page names the value the environment variable gives, as given, and says no working directory this"
                            + " system can hold is chosen, holding no folder in use",
                    () -> assertThat(List.of(inUse.attr("data-value"), inUse.attr("data-source"),
                                    inUse.attr("data-source-name"), inUse.text()))
                            .containsExactly("", "environment", "VESPERA_WORKING_DIR",
                                    "None in use: the environment variable VESPERA_WORKING_DIR names notU+0000a-path,"
                                            + " which is no path this system can hold, so no working directory is"
                                            + " chosen."));
            claim("the field stays empty and shows the example, there being no folder in use to show",
                    () -> assertThat(List.of(field.val(), field.attr("placeholder")))
                            .containsExactly("", "e.g. D:\\vespera\\work"));
            claim("the progress view says no working directory this system can hold is chosen",
                    () -> assertThat(progress.attr("data-progress")).isEqualTo("no-working-directory"));
            claim("and Run is refused as it refuses such a folder typed, landing on the home page",
                    () -> assertThat(List.of(refused.address().getPath(), byId(refused, "outcome").text()))
                            .containsExactly("/",
                                    "Run is not started: the working directory is no path this system can hold"));
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
