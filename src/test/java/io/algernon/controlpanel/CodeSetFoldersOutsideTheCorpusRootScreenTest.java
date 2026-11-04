package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
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
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * A code set's folder may lie outside the corpus root (ADR-334 §5.2, §5.5): the code-sets page holds a form of its own,
 * {@code code-set-folders}, which adds a folder anywhere on the machine to the profile's {@code codeSetFolders} with its
 * provenance, and removes one; a folder over the corpus root, the working directory or one listed is refused; the
 * folder page for it lists the whole machine; and a set below such a folder is renamed by its folder spelt absolute.
 *
 * <p>The profile is read as YAML, by its key's name, so this compiles before {@code ProfileKey} has the key.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class CodeSetFoldersOutsideTheCorpusRootScreenTest {

    /** The profile key, as {@code profile.yaml} spells it. */
    private static final String KEY = "codeSetFolders";

    /** The form of the folders outside the corpus root. */
    private static final String FORM = "code-set-folders";

    /** Its field for a folder to add. */
    private static final String NEW_FOLDER = "new-code-set-folder";

    /** Its field for the key's provenance. */
    private static final String PROVENANCE = KEY + ".provenance";

    /** The name the folder page gives the field. */
    private static final String FOR = "code-set-folder";

    private static final String WHY_BILLING = "the billing engine's code lives beside the archive";

    private static final String WHY_BOTH = "and the tools the billing engine is built with";

    private static final String WHY_TOOLS_ALONE = "the billing engine moved into the archive";

    @TempDir
    Path folder;

    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("A folder outside the corpus root is added to the profile with the reason given, a second after it, and one removed")
    void aFolderOutsideTheCorpusRootIsAddedAndRemoved() throws IOException {
        Path work = workingDirectory();
        Path billing = Files.createDirectories(folder.resolve("elsewhere").resolve("billing"));
        Path tools = Files.createDirectories(folder.resolve("elsewhere").resolve("tools"));
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            Page page = browser.page();

            claim("the page holds a form of the folders outside the corpus root, its field for a new one and the reason",
                    () -> assertThat(controlNames(form(page, FORM))).containsExactly(NEW_FOLDER, PROVENANCE));
            claim("its one button is Save, and its Browse button chooses the new folder",
                    () -> assertThat(List.of(buttonNames(form(page, FORM)), browseNames(form(page, FORM))))
                            .containsExactly(List.of("save"), List.of("browse-" + FOR)));
            claim("and it lists no folder, the profile naming none",
                    () -> assertThat(byId(page, FORM).select("li")).isEmpty());

            browser.press(FORM, "save", Map.of(NEW_FOLDER, billing.toString(), PROVENANCE, WHY_BILLING));
            claim("the folder is written into the profile with the reason given",
                    () -> assertThat(stated(work)).hasValue(List.of(billing.toString(), WHY_BILLING)));

            Page added = browser.press(FORM, "save", Map.of(NEW_FOLDER, tools.toString(), PROVENANCE, WHY_BOTH)).page();
            claim("a second is written after it, joined by the system's separator of paths",
                    () -> assertThat(stated(work))
                            .hasValue(List.of(billing + File.pathSeparator + tools, WHY_BOTH)));
            claim("and the page lists both, in that order, each with a button to remove it",
                    () -> {
                        assertThat(byId(added, FORM).select("li").eachAttr("data-folder"))
                                .containsExactly(billing.toString(), tools.toString());
                        assertThat(buttonNames(form(added, FORM))).containsExactly("remove-1", "remove-2", "save");
                    });

            browser.press(FORM, "remove-1", Map.of(PROVENANCE, WHY_TOOLS_ALONE));
            claim("removing the first leaves the second alone, with the reason given",
                    () -> assertThat(stated(work)).hasValue(List.of(tools.toString(), WHY_TOOLS_ALONE)));
        }
    }

    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("A folder that is the working directory, lies inside or above the corpus root, is not there or is not a whole path is refused, and nothing is written")
    void aFolderOverTheCorpusOrTheWorkingDirectoryIsRefused() throws IOException {
        Path work = workingDirectory();
        Path corpus = corpus();
        Path inside = Files.createDirectories(corpus.resolve("tools"));
        Map<String, String> refused = new LinkedHashMap<>();
        refused.put("the working directory", work.toString());
        refused.put("a folder inside the corpus root", inside.toString());
        refused.put("a folder above the corpus root", folder.toString());
        refused.put("a folder that is not there", folder.resolve("nowhere").toString());
        refused.put("a path from the corpus root", "tools");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            for (Map.Entry<String, String> each : refused.entrySet()) {
                Page landed = browser.press(FORM, "save", Map.of(NEW_FOLDER, each.getValue(), PROVENANCE, WHY_BILLING))
                        .page();

                claim(each.getKey() + " lands back on the code sets with one line saying it is not added",
                        () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                                .satisfiesExactly(
                                        path -> assertThat(path).isEqualTo("/code-sets"),
                                        line -> assertThat(line).contains("is not added")));
                claim("and the profile is not written", () -> assertThat(work.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
            }
        }
    }

    /** The form's heading and its cost, ADR-334 §5.2: the cost of the key as {@code /profile} draws it for every key. */
    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("The form of the folders outside the corpus root stands below the code sets' own under its heading, and says what a change costs as the profile page says it")
    void theFormIsHeadedAndSaysWhatAChangeCosts() throws IOException {
        Path work = workingDirectory();
        try (ScreenServer screens = start(work)) {
            Page codeSets = screens.browser().go("/code-sets").page();
            Page profile = screens.browser().go("/profile").page();

            claim("the page holds the code sets' own form, then the one of the folders outside the corpus root",
                    () -> assertThat(codeSets.document().select("form").eachAttr("name"))
                            .containsSubsequence("code-sets", FORM));
            claim("which is headed Folders outside the corpus root",
                    () -> assertThat(form(codeSets, FORM).select("h2").eachText())
                            .containsExactly("Folders outside the corpus root"));
            claim("and says, inside it, what saving the folders costs, word for word as the profile page says it of the"
                            + " same value, and whether the sample is drawn again as that page says",
                    () -> {
                        Element here = form(codeSets, FORM).selectFirst("#cost-" + KEY);
                        Element there = byId(profile, "cost-" + KEY);
                        assertThat(here).isNotNull();
                        assertThat(List.of(here.text(), here.attr("data-sample-drawn-again")))
                                .containsExactly(there.text(), there.attr("data-sample-drawn-again"));
                        assertThat(here.text()).isNotBlank();
                    });
        }
    }

    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("Save with no folder typed adds none, says so in one line, writes nothing and keeps the reason typed")
    void saveWithNoFolderTypedAddsNone() throws IOException {
        Path work = workingDirectory();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("an empty field", "");
        fields.put("a field of spaces alone, its trailing white space lost", "   ");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            for (Map.Entry<String, String> each : fields.entrySet()) {
                Page landed = browser.press(FORM, "save", Map.of(NEW_FOLDER, each.getValue(), PROVENANCE, WHY_BILLING))
                        .page();

                claim(each.getKey() + " lands back on the code sets with the one line saying no folder is added, none"
                                + " being typed",
                        () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                                .containsExactly("/code-sets", "no folder is added: none is typed in the field"));
                claim("the profile is not written", () -> assertThat(work.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
                claim("and the reason typed stands in its field",
                        () -> assertThat(form(landed, FORM).select("[name='" + PROVENANCE + "']").val())
                                .isEqualTo(WHY_BILLING));
            }
        }
    }

    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("Browse for a folder outside the corpus root lists every drive and any folder, and puts the one chosen in the field")
    void browseListsTheWholeMachineAndPutsTheFolderChosenInTheField() throws IOException {
        Path work = workingDirectory();
        Path elsewhere = Files.createDirectories(folder.resolve("elsewhere"));
        Path billing = Files.createDirectories(elsewhere.resolve("billing"));
        List<String> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                .map(Path::toString).toList();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            Page browsed = browser.press(FORM, "browse-" + FOR, Map.of(PROVENANCE, WHY_BILLING)).page();
            Page listed = browser.go("/folders?for=" + FOR + "&path="
                    + URLEncoder.encode(elsewhere.toString(), UTF_8)).page();

            claim("Browse lands on the folder page for the field",
                    () -> assertThat(browsed.address().getPath()).isEqualTo("/folders"));
            claim("which links every drive of the machine, and not the corpus root alone",
                    () -> assertThat(byId(listed, "folder-roots").select("a").eachText()).containsExactlyElementsOf(roots));
            claim("and lists a folder outside the corpus root, by its whole path",
                    () -> assertThat(List.of(byId(listed, "folder-listing").attr("data-listing"),
                                    byId(listed, "folder-listing").attr("data-path"),
                                    String.join(",", byId(listed, "folder-list").select("a").eachText())))
                            .containsExactly("listed", elsewhere.toAbsolutePath().normalize().toString(), "billing"));

            browser.go("/folders?for=" + FOR + "&path=" + URLEncoder.encode(billing.toString(), UTF_8));
            Page back = browser.press("use-folder", "use", Map.of()).page();

            claim("the folder chosen stands in the field, with the reason typed before Browse, on the code sets",
                    () -> assertThat(List.of(back.address().getPath(),
                                    form(back, FORM).select("[name=" + NEW_FOLDER + "]").val(),
                                    form(back, FORM).select("[name='" + PROVENANCE + "']").val()))
                            .containsExactly("/code-sets", billing.toAbsolutePath().normalize().toString(), WHY_BILLING));
            claim("and nothing is written by choosing it",
                    () -> assertThat(work.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
        }
    }

    @Test
    @Story("The operator keeps code that lies outside the archive")
    @DisplayName("A set below a folder outside the corpus root is named by that folder's whole path, and a whole path below no such folder is still refused")
    void aSetBelowAFolderOutsideTheCorpusRootIsNamedByItsWholePath() throws IOException {
        Path work = workingDirectory();
        Path billing = Files.createDirectories(folder.resolve("elsewhere").resolve("billing"));
        Path unlisted = Files.createDirectories(folder.resolve("elsewhere").resolve("unlisted"));
        String spelt = billing.toRealPath().toString().replace(File.separatorChar, '/');
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            browser.press(FORM, "save", Map.of(NEW_FOLDER, billing.toString(), PROVENANCE, WHY_BILLING));

            browser.go("/code-sets").press("code-sets", "save", Map.of("new-folder", spelt, "new-name", "Billing engine"));
            claim("the set's name is written under its folder, spelt as its occurrences' paths spell it",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(
                                    work.resolve(CodeSetRenames.FILE_NAME), UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename(spelt, "Billing engine")));

            String before = Files.readString(work.resolve(CodeSetRenames.FILE_NAME), UTF_8);
            Page refused = browser.go("/code-sets").press("code-sets", "save",
                    Map.of("new-folder", unlisted.toRealPath().toString().replace(File.separatorChar, '/'),
                            "new-name", "Not a set")).page();
            claim("a whole path below no folder the profile lists is refused as climbing out, and nothing is written",
                    () -> {
                        assertThat(byId(refused, "outcome").text()).contains("lies under the corpus root");
                        assertThat(Files.readString(work.resolve(CodeSetRenames.FILE_NAME), UTF_8)).isEqualTo(before);
                    });
        }
    }

    /** What the profile states of the key, its value and its provenance; empty where the file or the key is not there. */
    @SuppressWarnings("unchecked")
    private static Optional<List<String>> stated(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(ProfileEdit.FILE_NAME);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Map<String, Object> profile = YAMLMapper.builder().build().readValue(Files.readString(file, UTF_8), Map.class);
        Object answer = profile == null ? null : profile.get(KEY);
        if (!(answer instanceof Map<?, ?> fields)) {
            return Optional.empty();
        }
        List<String> both = new ArrayList<>();
        both.add(String.valueOf(((Map<String, Object>) fields).get("value")));
        both.add(String.valueOf(((Map<String, Object>) fields).get("provenance")));
        return Optional.of(both);
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(corpus(), workingDirectory));
    }
}
