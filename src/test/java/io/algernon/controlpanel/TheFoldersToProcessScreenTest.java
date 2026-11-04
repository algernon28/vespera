package io.algernon.controlpanel;

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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The folders to process and the code sets are one tree of the corpus's folders, shown one folder at a time (ADR-340
 * §5): every folder is read at first; a folder unticked and saved is written into the profile's
 * {@code foldersNotWalked}, with its provenance, and a folder below it cannot be ticked; a folder marked as code is
 * named in {@code code-set-names.yaml}, and a folder not read cannot be; moving through the tree holds what was ticked
 * and writes nothing, and Reset lets go of it; a save says what it wrote, or that nothing was; a changed list with no
 * provenance, or from a page drawn before the file changed, writes neither file; and on Windows a folder listed in
 * another case is the folder itself.
 *
 * <p>The corpus holds three folders whose names sort without regard to case, {@code Archive}, {@code billing} and
 * {@code notes}, a folder below {@code billing}, and a file at its root, which no row may name.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheFoldersToProcessScreenTest {

    /** The tree's form. */
    private static final String TREE = "folders-to-process";

    /** The file at the corpus root, which the tree never lists. */
    private static final String A_FILE = "readme.txt";

    /** What a checkbox sends ticked, carrying no value, and what unticks it in the test's browser. */
    private static final String TICKED = "on";

    private static final String UNTICKED = "";

    /** The provenance the operator writes for a change of the list. */
    private static final String WHY = "the notes folder holds exports, not documentation";

    /** The value of the profile's foldersNotWalked as written: its value line under the key. */
    private static final Pattern THE_LIST = Pattern.compile(
            "(?m)^foldersNotWalked:\\s*\\R\\s+value:\\s*(?:\"([^\"]*)\"|'([^']*)'|(\\S*))");

    @TempDir
    Path folder;

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("At first every folder of the corpus is read: the tree lists each, ticked, by name and no file")
    void everyFolderOfTheCorpusIsReadAtFirst() throws IOException {
        try (ScreenServer screens = aServer(corpus(), work())) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            Element tree = byId(page, "folder-tree");

            claim("the tree shows the corpus root, listed",
                    () -> assertThat(List.of(tree.attr("data-tree"), tree.attr("data-path"))).containsExactly("listed", ""));
            claim("its rows are the three folders, in name order without regard to case, each read and none a code set",
                    () -> assertThat(rows(page)).containsExactly(
                            List.of("Archive", "walked", "not-marked"),
                            List.of("billing", "walked", "not-marked"),
                            List.of("notes", "walked", "not-marked")));
            claim("each row's Read box is ticked",
                    () -> assertThat(form(page, TREE).select("input[name^=walk-]").stream()
                                    .map(box -> box.hasAttr("checked")).toList())
                            .containsExactly(true, true, true));
            claim("and the file at the corpus root is named nowhere on the page",
                    () -> assertThat(page.document().html()).doesNotContain(A_FILE));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A folder unticked and saved is written into the profile as not read, with its provenance, and shown so")
    void aFolderUntickedAndSavedIsNotRead() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            Page landed = screens.browser().go("/?open=values-corpus").press(TREE, "save", Map.of(
                    "walk-" + rowOf(page, "notes"), UNTICKED, "foldersNotWalked.provenance", WHY)).page();
            String profile = Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8);

            claim("the profile lists the folder as not read, with the provenance written",
                    () -> assertThat(List.of(listIn(profile), profile.contains(WHY))).containsExactly("notes", true));
            claim("the save lands back in the drawer's folders to process",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "values-corpus"));
            claim("and the tree shows the folder not read, its box unticked",
                    () -> assertThat(List.of(rows(landed).get(2), form(landed, TREE)
                                    .selectFirst("[name=walk-" + rowOf(landed, "notes") + "]").hasAttr("checked")))
                            .containsExactly(List.of("notes", "not-walked", "not-marked"), false));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A folder below one not read is shown not read, and its boxes cannot be ticked")
    void aFolderBelowOneNotReadCannotBeTicked() throws IOException {
        Path work = work();
        theProfileListsNotWalked(work, "billing");
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus&tree=billing").page();
            Element tree = form(page, TREE);

            claim("the tree shows billing, whose one folder is not read because billing is not",
                    () -> assertThat(List.of(byId(page, "folder-tree").attr("data-path"), rows(page)))
                            .containsExactly("billing", List.of(List.of("billing/src", "not-walked-above", "not-marked"))));
            claim("and both its boxes are disabled, so that a save sends neither",
                    () -> assertThat(List.of(tree.selectFirst("[name=walk-1]").hasAttr("disabled"),
                                    tree.selectFirst("[name=code-set-1]").hasAttr("disabled")))
                            .containsExactly(true, true));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A folder ticked again and saved is read again, the list left with no folder")
    void aFolderTickedAgainIsReadAgain() throws IOException {
        Path work = work();
        theProfileListsNotWalked(work, "notes");
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            Page landed = screens.browser().go("/?open=values-corpus").press(TREE, "save", Map.of(
                    "walk-" + rowOf(page, "notes"), TICKED,
                    "foldersNotWalked.provenance", "the notes are documentation after all")).page();
            String profile = Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8);

            claim("the profile lists no folder as not read",
                    () -> assertThat(listIn(profile)).isIn("", "null"));
            claim("and the tree shows every folder read",
                    () -> assertThat(rows(landed).stream().map(row -> row.get(1)).toList())
                            .containsOnly("walked"));
        }
    }

    @Test
    @Story("The operator names the code sets")
    @DisplayName("A folder marked as code in the same tree is named, by the name typed or its own, and one unmarked loses its name")
    void aCodeSetIsMarkedInTheSameTreeWithItsName() throws IOException {
        Path work = work();
        Files.writeString(work.resolve(CodeSetRenames.FILE_NAME),
                CodeSetRenames.render(List.of(new CodeSetRenames.Rename("notes", "Notes"))), UTF_8);
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            int archive = rowOf(page, "Archive");
            int billing = rowOf(page, "billing");
            int notes = rowOf(page, "notes");
            String placeholder = form(page, TREE).selectFirst("[name=code-set-name-" + billing + "]").attr("placeholder");
            List<String> marked = rows(page).stream().map(row -> row.get(2)).toList();
            screens.browser().go("/?open=values-corpus").press(TREE, "save", Map.of(
                    "code-set-" + archive, TICKED,
                    "code-set-" + billing, TICKED, "code-set-name-" + billing, "Billing engine",
                    "code-set-" + notes, UNTICKED));
            List<CodeSetRenames.Rename> names = CodeSetRenames.read(
                    Optional.of(Files.readString(work.resolve(CodeSetRenames.FILE_NAME), UTF_8)));

            claim("the tree showed notes marked, as the file names it, and the other two not",
                    () -> assertThat(marked).containsExactly("not-marked", "not-marked", "marked"));
            claim("an empty name shows the name the set has anyway, its folder's own",
                    () -> assertThat(placeholder).isEqualTo("in use: billing"));
            claim("the file names Archive by its own name and billing by the name typed, in the order of their paths,"
                            + " and notes no more",
                    () -> assertThat(names).containsExactly(new CodeSetRenames.Rename("Archive", "Archive"),
                            new CodeSetRenames.Rename("billing", "Billing engine")));
            claim("and the profile is not written, the list of folders not read being unchanged",
                    () -> assertThat(work.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("Opening a folder of the tree and coming back holds what was unticked, writes nothing, and Save writes it")
    void openingAFolderHoldsWhatWasTickedAndWritesNothing() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus");
            Page atTheRoot = browser.page();
            int notes = rowOf(atTheRoot, "notes");
            Page inBilling = browser.press(TREE, "open-" + rowOf(atTheRoot, "billing"),
                    Map.of("walk-" + notes, UNTICKED)).page();
            boolean writtenWhileMoving = Files.exists(work.resolve(ProfileEdit.FILE_NAME));
            Page backAtTheRoot = browser.press(TREE, "up-0", Map.of()).page();
            browser.press(TREE, "save", Map.of("foldersNotWalked.provenance", WHY));

            claim("Open lands in the folder opened",
                    () -> assertThat(byId(inBilling, "folder-tree").attr("data-path")).isEqualTo("billing"));
            claim("moving through the tree wrote no file",
                    () -> assertThat(writtenWhileMoving).isFalse());
            claim("back at the corpus root, notes is still shown unticked, as held",
                    () -> assertThat(List.of(rows(backAtTheRoot).get(2).get(1), form(backAtTheRoot, TREE)
                                    .selectFirst("[name=walk-" + notes + "]").hasAttr("checked")))
                            .containsExactly("not-walked", false));
            claim("and Save, pressed with nothing else changed, writes the folder held",
                    () -> assertThat(listIn(Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8)))
                            .isEqualTo("notes"));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A folder unticked with no provenance is refused in one line, and neither file is written")
    void aChangedListWithNoProvenanceIsRefusedAndNothingIsWritten() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            Page landed = screens.browser().go("/?open=values-corpus").press(TREE, "save", Map.of(
                    "walk-" + rowOf(page, "notes"), UNTICKED, "foldersNotWalked.provenance", "")).page();

            claim("the save is refused in one line naming the value",
                    () -> assertThat(byId(landed, "outcome").text()).startsWith("foldersNotWalked is not saved: "));
            claim("and neither the profile nor the code sets' names is written",
                    () -> assertThat(List.of(Files.exists(work.resolve(ProfileEdit.FILE_NAME)),
                                    Files.exists(work.resolve(CodeSetRenames.FILE_NAME))))
                            .containsExactly(false, false));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("With no corpus root named the tree says so and lists nothing")
    void withNoCorpusRootTheTreeListsNothing() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder,
                new LaunchValues("", work().toString(), "", "", "", "", "", ""))) {
            Page page = screens.browser().go("/?open=values-corpus").page();

            claim("the tree says no corpus root is named, and holds no row",
                    () -> assertThat(List.of(byId(page, "folder-tree").attr("data-tree"), rows(page).size()))
                            .containsExactly("no-corpus-root", 0));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("Save with nothing changed writes neither file and says every folder is as the files state it")
    void aSaveWithNothingChangedWritesNothingAndSaysSo() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page landed = screens.browser().go("/?open=values-corpus").press(TREE, "save", Map.of()).page();

            claim("the save says nothing was written, in these words",
                    () -> assertThat(byId(landed, "outcome").text())
                            .isEqualTo("nothing was written: every folder is as the files state it"));
            claim("and neither the profile nor the code sets' names is written",
                    () -> assertThat(List.of(Files.exists(work.resolve(ProfileEdit.FILE_NAME)),
                                    Files.exists(work.resolve(CodeSetRenames.FILE_NAME))))
                            .containsExactly(false, false));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A save says how many folders are not read and how many code sets are named")
    void aSaveSaysHowManyFoldersAreNotReadAndHowManyCodeSetsAreNamed() throws IOException {
        try (ScreenServer screens = aServer(corpus(), work())) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus");
            Page page = browser.page();
            int notes = rowOf(page, "notes");
            int billing = rowOf(page, "billing");
            Page landed = browser.press(TREE, "save", Map.of("walk-" + notes, UNTICKED,
                    "code-set-" + billing, TICKED, "code-set-name-" + billing, "Billing engine",
                    "foldersNotWalked.provenance", WHY)).page();

            claim("the save says one folder is not read and one code set is named, in these words",
                    () -> assertThat(byId(landed, "outcome").text())
                            .isEqualTo("the folders to process are saved: 1 not read, 1 named as code sets"));
        }
    }

    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("Reset lets go of every tick held, writes nothing, and shows the tree as the files state it")
    void resetLetsGoOfWhatIsHeldAndWritesNothing() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus");
            Page atTheRoot = browser.page();
            int notes = rowOf(atTheRoot, "notes");
            browser.press(TREE, "open-" + rowOf(atTheRoot, "billing"), Map.of("walk-" + notes, UNTICKED));
            Page held = browser.press(TREE, "up-0", Map.of()).page();
            Page reset = browser.press(TREE, "reset", Map.of()).page();

            claim("before Reset, notes is held unticked",
                    () -> assertThat(rows(held).get(2).get(1)).isEqualTo("not-walked"));
            claim("Reset lands on the folder shown, every folder read again as the files state it",
                    () -> assertThat(List.of(byId(reset, "folder-tree").attr("data-path"),
                                    rows(reset).stream().map(row -> row.get(1)).toList()))
                            .containsExactly("", List.of("walked", "walked", "walked")));
            claim("and nothing was written",
                    () -> assertThat(List.of(Files.exists(work.resolve(ProfileEdit.FILE_NAME)),
                                    Files.exists(work.resolve(CodeSetRenames.FILE_NAME))))
                            .containsExactly(false, false));
        }
    }

    /** The file changes behind the page between its drawing and the save, as {@code vespera label --auto} may write it. */
    @Test
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("A save from a page drawn before the profile's list changed is refused in one line, and the file is as it was")
    void aSaveFromAPageDrawnBeforeTheListChangedIsRefused() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(corpus(), work)) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus");
            int notes = rowOf(browser.page(), "notes");
            theProfileListsNotWalked(work, "Archive");
            String behindThePage = Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8);
            Page landed = browser.press(TREE, "save", Map.of("walk-" + notes, UNTICKED,
                    "foldersNotWalked.provenance", WHY)).page();

            claim("the save is refused, the value having changed since the page was drawn",
                    () -> assertThat(byId(landed, "outcome").text()).isEqualTo("foldersNotWalked is not saved:"
                            + " profile.yaml has changed this value since the page was drawn; look at it and save again"));
            claim("and the file is as it was written behind the page",
                    () -> assertThat(Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8))
                            .isEqualTo(behindThePage));
        }
    }

    @Test
    @Story("The operator names the code sets")
    @DisplayName("A folder not read cannot be marked as code")
    void aFolderNotReadCannotBeMarkedAsCode() throws IOException {
        Path work = work();
        theProfileListsNotWalked(work, "notes");
        try (ScreenServer screens = aServer(corpus(), work)) {
            Page page = screens.browser().go("/?open=values-corpus").page();
            Element tree = form(page, TREE);
            int notes = rowOf(page, "notes");

            claim("the row of the folder not read has its Code box disabled, and the others not",
                    () -> assertThat(List.of(tree.selectFirst("[name=code-set-" + notes + "]").hasAttr("disabled"),
                                    tree.selectFirst("[name=code-set-" + rowOf(page, "billing") + "]")
                                            .hasAttr("disabled")))
                            .containsExactly(true, false));
        }
    }

    /**
     * On Windows a folder listed in another case is the folder itself, as the walk compares them (ADR-340 §5, §8.2): the
     * tree shows it not read, and ticking it again takes it off the list.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("The operator leaves a folder of the corpus unread")
    @DisplayName("On Windows a folder listed in another case is shown not read, and ticking it again takes it off the list")
    void onWindowsAFolderListedInAnotherCaseIsTheFolderItself() throws IOException {
        Path work = work();
        theProfileListsNotWalked(work, "Notes");
        try (ScreenServer screens = aServer(corpus(), work)) {
            PanelBrowser browser = screens.browser().go("/?open=values-corpus");
            Page page = browser.page();
            int notes = rowOf(page, "notes");
            List<String> notesAsShown = rows(page).get(2);
            Element box = form(page, TREE).selectFirst("[name=walk-" + notes + "]");
            List<Boolean> boxAsDrawn = List.of(box.hasAttr("checked"), box.hasAttr("disabled"));
            browser.press(TREE, "save", Map.of("walk-" + notes, TICKED,
                    "foldersNotWalked.provenance", "the notes are documentation after all"));

            claim("the folder notes is shown not read, the list naming it as Notes, and not as below a folder not read",
                    () -> assertThat(notesAsShown).containsExactly("notes", "not-walked", "not-marked"));
            claim("its Read box is drawn unticked and can be ticked again",
                    () -> assertThat(boxAsDrawn).containsExactly(false, false));
            claim("and ticked again and saved, the list names no folder",
                    () -> assertThat(listIn(Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8)))
                            .isIn("", "null"));
        }
    }

    /** Each row of the tree: its folder, whether it is read, and whether it is marked as code. */
    private static List<List<String>> rows(Page page) {
        return byId(page, "tree-rows").select("li[data-folder]").stream()
                .map(row -> List.of(row.attr("data-folder"), row.attr("data-walked"), row.attr("data-code-set")))
                .toList();
    }

    /** The number {@code i} of the row of {@code folderName}, read off its hidden {@code folder-<i>}. */
    private static int rowOf(Page page, String folderName) {
        Element row = byId(page, "tree-rows").selectFirst("li[data-folder='" + folderName + "']");
        if (row == null) {
            throw new AssertionError("the tree holds no row of " + folderName);
        }
        return Integer.parseInt(row.selectFirst("input[name^=folder-]").attr("name").substring("folder-".length()));
    }

    /** The profile's list of folders not walked, as written; empty where the key names no value. */
    private static String listIn(String profile) {
        Matcher list = THE_LIST.matcher(profile);
        if (!list.find()) {
            return "";
        }
        for (int group = 1; group <= list.groupCount(); group++) {
            if (list.group(group) != null) {
                return list.group(group);
            }
        }
        return "";
    }

    /** A profile listing {@code folders} as not walked, with a provenance of its own. */
    private static void theProfileListsNotWalked(Path work, String folders) throws IOException {
        Files.writeString(work.resolve(ProfileEdit.FILE_NAME), """
                ---
                foldersNotWalked:
                  value: "%s"
                  provenance: "written by this test"
                  measurement: null
                """.formatted(folders), UTF_8);
    }

    /** The corpus of three folders, one with a folder below it, and a file at its root. */
    private Path corpus() throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        for (String each : List.of("Archive", "billing/src", "notes")) {
            Files.createDirectories(corpus.resolve(each));
        }
        Files.writeString(corpus.resolve(A_FILE), "a file at the corpus root", UTF_8);
        return corpus;
    }

    private Path work() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer aServer(Path corpus, Path work) throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(corpus, work));
    }
}
