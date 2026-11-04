package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The folder page, {@code /folders} (ADR-331 §5): a Browse button posts its form so that what was typed is held, and
 * lands on a server-written page listing the subfolders of one folder by name, with links to the roots, Up, and a form
 * that puts the folder chosen in the field and returns to the page it came from. No file's name or content is listed,
 * no {@code GET} writes anything, and the field is named from a closed list.
 *
 * <p>The folders listed are under the test's own temporary folder. A real root is only linked, and listed once, in the
 * test of Up at a root, where nothing of what it holds is kept.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class TheFolderPageScreenTest {

    private static final String CORPUS_ROOT = "vespera.corpus-root";

    private static final String WORKING_DIRECTORY = "vespera.working-dir";

    private static final String DELIVERABLE_ROOT = "vespera.deliverable-root";

    /** The seed folder's control in the run settings' form and the next step's. */
    private static final String SEED_FOLDER = "seedFolder";

    /** The field of the next act's form holding the seed folder, as the folder page names it. */
    private static final String NEXT_SEED_FOLDER = "next-seedFolder";

    /** The field of the profile page's form holding the seed folder, as the folder page names it. */
    private static final String PROFILE_SEED_FOLDER = "profile-seedFolder";

    /** The code sets page's field for a folder of code outside the corpus root, as the folder page names it. */
    private static final String CODE_SET_FOLDER = "code-set-folder";

    /** The relevance page's field for a folder not relevant, relative to the corpus root, as the folder page names it. */
    private static final String NOT_RELEVANT_FOLDER = "not-relevant-folder";

    /** The profile page's one form (ADR-334 §7), and the names of the seed folder's two controls in it. */
    private static final String PROFILE_FORM = "profile";

    private static final String SEED_FOLDER_PROVENANCE = "seedFolder.provenance";

    /** Each field the folder page chooses for, and the page it returns to. */
    private static final Map<String, String> THE_CLOSED_LIST = closedList();

    /** A file beside the subfolders, whose name no page may hold. */
    private static final String A_FILES_NAME = "a-file-whose-name-is-not-listed.pdf";

    /** Names a query has to carry whole: an ampersand, a space, a percent, a plus, a hash and a quote. */
    private static final List<String> NAMES_A_QUERY_MUST_CARRY = List.of("a&b", "c d", "50%", "e+f", "g#h", "i'j");

    /** Markup in a query's path: closing an attribute and opening a script. */
    private static final String MARKUP_IN_THE_QUERY = "\"><script>alert(1)</script><img src=x onerror=alert(2)>";

    /** A Docling image typed on the launch form, which Browse is to hold. */
    private static final String IMAGE_TYPED = "an-image-typed";

    /** A label model typed on the launch form, which Browse is to hold. */
    private static final String MODEL_TYPED = "a-model-typed";

    /** A provenance typed beside a folder field, which Browse is to hold. */
    private static final String REASON_TYPED = "the reason the operator typed before browsing";

    /** A profile {@code ProfileEdit} reads as in no form it wrote, a tab in an indent, so it refuses every save to it. */
    private static final String A_PROFILE_NOT_IN_FORM = "seedFolder:\n\tvalue: null\n";

    /** A profile in the form the control panel edits, so the profile page draws a form for each value. */
    private static final String A_PROFILE_IN_FORM = """
            ---
            extractionAttempt:
              value: "2"
              provenance: "a second attempt"
              measurement: null
            """;

    /** What another invocation holding the working directory leaves in {@code vespera.lock}. */
    private static final String ANOTHER_HOLDERS_LINE = "pid=4242 started=2026-10-19T10:15:30+02:00 command=run /corpus";

    /** The byte of {@code vespera.lock} an invocation locks, far past the end of its holder line. */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    /** The reason the file comes to state, behind the page, beside another seed folder. */
    private static final String ANOTHER_REASON = "a reason written behind the page";

    /** A name a strip of the query's {@code path} would make another folder's. */
    private static final String BEGINS_WITH_A_SPACE = " begins-with-a-space";

    private static final String USER = System.getProperty("user.name");

    @TempDir
    Path folder;

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Each folder field has a Browse button of its own in its own form, which sends the form though its reason is still empty")
    @Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
    void eachFolderFieldHasABrowseButtonOfItsOwnThatSkipsTheRequiredProvenance() throws IOException, InterruptedException {
        Path workingDirectory = wanting("seedFolder");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(aCorpusRoot(), workingDirectory))) {
            Page home = screens.afterARunHasEnded().go("/").page();

            Map<String, List<String>> buttonsOfEachForm = new LinkedHashMap<>();
            for (String each : List.of("launch-values", "next", PROFILE_FORM, "code-set-folders", "not-relevant")) {
                buttonsOfEachForm.put(each, browseNames(form(home, each)));
            }
            List<Element> buttons = home.document().select("button[name^=" + ScreenServer.BROWSE + "]");

            claim("the launch values' form holds the Browse buttons of the corpus root, the working directory and the"
                            + " deliverable root, and each other form the one of its own folder field",
                    () -> assertThat(buttonsOfEachForm).containsExactlyEntriesOf(browseButtonsOfEachForm()));
            claim("no other form of the home page holds one",
                    () -> assertThat(buttons).hasSize(7));
            claim("each is a submit button, and asks the browser not to stop it for a reason left empty",
                    () -> assertThat(buttons).isNotEmpty().allSatisfy(button -> {
                        assertThat(button.attr("type")).isEqualTo("submit");
                        assertThat(button.hasAttr("formnovalidate")).isTrue();
                    }));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Browse on the launch form holds every value typed, keeps nothing on disk, starts nothing, and lands on the folder page at the folder typed")
    void browseOnTheLaunchFormHoldsWhatWasTypedAndLandsOnTheFolderPage() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path launchValues = LaunchValues.fileIn(folder.resolve("tmp"), USER);
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            byte[] keptBefore = Files.readAllBytes(launchValues);
            Page landed = browser.press("launch-values", "browse-" + CORPUS_ROOT, Map.of(CORPUS_ROOT, corpusRoot.toString(),
                    WORKING_DIRECTORY, workingDirectory.toString(), "vespera.docling.image", IMAGE_TYPED,
                    "vespera.label.model", MODEL_TYPED)).page();
            byte[] keptAfter = Files.readAllBytes(launchValues);
            Page launch = browser.go("/").page();
            Page invocation = browser.go("/invocation").page();

            claim("the press lands on the folder page, listing the folder typed into that field",
                    () -> assertThat(List.of(landed.address().getPath(), listing(landed), listedPath(landed)))
                            .containsExactly("/folders", "listed", corpusRoot.toString()));
            claim("the launch page holds every value typed",
                    () -> assertThat(List.of(valueOf(launch, CORPUS_ROOT), valueOf(launch, WORKING_DIRECTORY),
                                    valueOf(launch, "vespera.docling.image"), valueOf(launch, "vespera.label.model")))
                            .containsExactly(corpusRoot.toString(), workingDirectory.toString(), IMAGE_TYPED,
                                    MODEL_TYPED));
            claim("the file of launch values is as it was, since only starting an invocation writes it",
                    () -> assertThat(keptAfter).isEqualTo(keptBefore));
            claim("and no invocation was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    /** A folder the query names whole, whatever the name holds, is the whole of what the 303 and each link must carry. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Browse on a folder whose name holds a space, an ampersand, a percent, a plus, a hash or a quote lands on that folder")
    void browseCarriesAFolderWhateverItsNameHolds() throws IOException {
        Path corpusRoot = aCorpusRoot();
        for (String name : NAMES_A_QUERY_MUST_CARRY) {
            Files.createDirectories(corpusRoot.resolve(name));
        }
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            List<String> landedAt = new ArrayList<>();
            for (String name : NAMES_A_QUERY_MUST_CARRY) {
                landedAt.add(listedPath(browser.go("/").press("launch-values", "browse-" + CORPUS_ROOT,
                        Map.of(CORPUS_ROOT, corpusRoot.resolve(name).toString())).page()));
            }

            claim("each lands on the folder of that name, whole",
                    () -> assertThat(landedAt).containsExactlyElementsOf(NAMES_A_QUERY_MUST_CARRY.stream()
                            .map(name -> corpusRoot.resolve(name).toString()).toList()));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Browse with the field empty lands on a page of the roots, with nothing to choose until a folder is opened")
    void browseWithTheFieldEmptyLandsOnTheRoots() throws IOException {
        List<String> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                .map(Path::toString).toList();
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page landed = screens.browser().press("launch-values", "browse-" + WORKING_DIRECTORY, Map.of()).page();
            Element links = byId(landed, "folder-roots");

            claim("the page says it shows the roots",
                    () -> assertThat(listing(landed)).isEqualTo("roots"));
            claim("it links each root of this system by name, each to that root, in the system's order",
                    () -> assertThat(List.of(links.select("a").eachText(),
                                    links.select("a").eachAttr("href").stream()
                                            .map(TheFolderPageScreenTest::pathIn).toList()))
                            .containsExactly(roots, roots));
            claim("and it lists no subfolder and offers no folder to use",
                    () -> assertThat(List.of(landed.document().select("#folder-list a").size(),
                                    landed.document().select("form[name=use-folder]").size()))
                            .containsExactly(0, 0));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("The folder page lists a folder's subfolders by name, in name order whatever their case, each a link, and never a file's name")
    void theFolderPageListsSubfoldersByNameAndNoFile() throws IOException {
        Path corpusRoot = aCorpusRoot();
        for (String name : List.of("b", "A", "c")) {
            Files.createDirectories(corpusRoot.resolve(name));
        }
        Files.writeString(corpusRoot.resolve(A_FILES_NAME), "the text of a document", StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().go(folders(CORPUS_ROOT, corpusRoot.toString())).page();
            String html = page.document().outerHtml();

            claim("the subfolders are listed by name alone, in name order whatever their case",
                    () -> assertThat(byId(page, "folder-list").select("a").eachText()).containsExactly("A", "b", "c"));
            claim("neither a file's name nor a word of its text is anywhere on the page",
                    () -> assertThat(html).doesNotContain(A_FILES_NAME).doesNotContain("the text of a document"));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Each subfolder's link lands in that folder, whatever its name holds, and its name reaches the page as text")
    void eachSubfolderLinkLandsInThatFolderWhateverItsNameHolds() throws IOException {
        Path corpusRoot = aCorpusRoot();
        for (String name : NAMES_A_QUERY_MUST_CARRY) {
            Files.createDirectories(corpusRoot.resolve(name));
        }
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser().go(folders(CORPUS_ROOT, corpusRoot.toString()));
            Map<String, String> linkOfEachName = new TreeMap<>();
            byId(browser.page(), "folder-list").select("a")
                    .forEach(link -> linkOfEachName.put(link.text(), link.attr("href")));
            Map<String, String> landedAt = new TreeMap<>();
            linkOfEachName.forEach((name, href) -> landedAt.put(name, listedPath(browser.go(href).page())));

            claim("every name is listed as text, whole",
                    () -> assertThat(linkOfEachName.keySet())
                            .containsExactlyInAnyOrderElementsOf(NAMES_A_QUERY_MUST_CARRY));
            claim("and each link lands in the folder of its own name, and not of another's",
                    () -> assertThat(landedAt).allSatisfy((name, path) -> assertThat(path)
                            .isEqualTo(corpusRoot.resolve(name).toString())));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Up leads to the parent folder, and at a root there is no Up")
    void upLeadsToTheParentAndARootHasNone() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Path inside = Files.createDirectories(corpusRoot.resolve("inside"));
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            Page page = browser.go(folders(CORPUS_ROOT, inside.toString())).page();
            Page up = browser.go(byId(page, "folder-up").attr("href")).page();
            Page atARoot = browser.go(folders(CORPUS_ROOT, inside.getRoot().toString())).page();

            claim("Up lands on the folder holding this one",
                    () -> assertThat(listedPath(up)).isEqualTo(corpusRoot.toString()));
            claim("and a root has no Up",
                    () -> assertThat(atARoot.document().select("#folder-up")).isEmpty());
        }
    }

    /** From a folder that is there and from one that is not, each step of a folder not relevant toward the corpus root. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Up below the corpus root leads a step toward it, from a folder that is not there too, and stops at the corpus root")
    void upBelowTheCorpusRootLeadsAStepTowardIt() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Files.createDirectories(corpusRoot.resolve("tools").resolve("build"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page inBuild = browser.go(folders(NOT_RELEVANT_FOLDER, "tools/build")).page();
            Page inTools = browser.go(byId(inBuild, "folder-up").attr("href")).page();
            Page atTheRoot = browser.go(byId(inTools, "folder-up").attr("href")).page();
            Page notThere = browser.go(folders(NOT_RELEVANT_FOLDER, "tools/gone")).page();
            Page fromNotThere = browser.go(byId(notThere, "folder-up").attr("href")).page();
            Page notThereAtTheTop = browser.go(folders(NOT_RELEVANT_FOLDER, "gone")).page();
            Page fromNotThereAtTheTop = browser.go(byId(notThereAtTheTop, "folder-up").attr("href")).page();

            claim("Up from tools/build lands in tools, and Up from there in the corpus root, listed, where it stops",
                    () -> {
                        assertThat(List.of(listedPath(inTools), listing(inTools))).containsExactly("tools", "listed");
                        assertThat(List.of(listedPath(atTheRoot), listing(atTheRoot))).containsExactly("", "listed");
                        assertThat(atTheRoot.document().select("#folder-up")).isEmpty();
                    });
            claim("a folder not there says so",
                    () -> assertThat(List.of(listing(notThere), listing(notThereAtTheTop)))
                            .containsOnly("no-such-folder"));
            claim("and its Up lands in the folder that would hold it",
                    () -> assertThat(List.of(listedPath(fromNotThere), listedPath(fromNotThereAtTheTop)))
                            .containsExactly("tools", ""));
        }
    }

    /** The folder page returns where the field is, the fragment included, whichever way the operator leaves it. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Each field the folder page chooses for leads back to the page the field is on")
    void eachFieldLeadsBackToItsOwnPage() throws IOException {
        Path corpusRoot = aCorpusRoot();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, corpusRoot))) {
            PanelBrowser browser = screens.browser();
            Map<String, String> backOfEach = new LinkedHashMap<>();
            for (String field : THE_CLOSED_LIST.keySet()) {
                backOfEach.put(field, byId(browser.go("/folders?for=" + field).page(), "folder-back").attr("href"));
            }

            claim("the link back from each field's folder page is that field's own page",
                    () -> assertThat(backOfEach).containsExactlyEntriesOf(THE_CLOSED_LIST));
        }
    }

    /** Each field in a server of its own, so that the two fields left empty show the choice went to the third alone. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Use this folder puts the folder in the launch field Browse was pressed for, and in no other, keeps every other value, writes nothing and returns to the launch page")
    void useThisFolderPutsTheChoiceInTheLaunchFieldItWasPressedFor() throws IOException {
        Path chosen = Files.createDirectories(folder.resolve("chosen"));
        for (String field : List.of(CORPUS_ROOT, WORKING_DIRECTORY, DELIVERABLE_ROOT)) {
            Path own = Files.createDirectories(folder.resolve("own-" + field));
            try (ScreenServer screens = ScreenServer.start(own, LaunchValues.none())) {
                PanelBrowser browser = screens.browser();
                browser.press("launch-values", "browse-" + field, Map.of("vespera.label.model", MODEL_TYPED));
                browser.go(folders(field, chosen.toString()));
                byte[] keptBefore = Files.readAllBytes(LaunchValues.fileIn(own.resolve("tmp"), USER));
                Page landed = browser.press("use-folder", "use", Map.of()).page();
                byte[] keptAfter = Files.readAllBytes(LaunchValues.fileIn(own.resolve("tmp"), USER));

                claim("for " + field + ", the choice lands on the launch page",
                        () -> assertThat(landed.address().getPath()).isEqualTo("/"));
                claim("for " + field + ", that field holds the folder, and each of the other two nothing",
                        () -> assertThat(List.of(valueOf(landed, CORPUS_ROOT), valueOf(landed, WORKING_DIRECTORY),
                                        valueOf(landed, DELIVERABLE_ROOT)))
                                .containsExactlyElementsOf(List.of(CORPUS_ROOT, WORKING_DIRECTORY, DELIVERABLE_ROOT)
                                        .stream().map(each -> each.equals(field) ? chosen.toString() : "").toList()));
                claim("for " + field + ", the value typed before Browse is still held",
                        () -> assertThat(valueOf(landed, "vespera.label.model")).isEqualTo(MODEL_TYPED));
                claim("for " + field + ", the file of launch values is as it was",
                        () -> assertThat(keptAfter).isEqualTo(keptBefore));
            }
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Choosing the seed folder on the next act's page keeps every other value and reason typed, writes no profile, and returns there")
    void chooseTheSeedFolderOnTheNextPage() throws IOException, InterruptedException {
        Path corpusRoot = aCorpusRoot();
        Path workingDirectory = wanting("seedFolder boilerplateDocumentFrequencyFloor");
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        Map<String, String> typed = Map.of("seedFolder.provenance", REASON_TYPED,
                "boilerplateDocumentFrequencyFloor", "0.35",
                "boilerplateDocumentFrequencyFloor.provenance", "read off the page");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            browser.press("next", "browse-" + NEXT_SEED_FOLDER, typed);
            browser.go(folders(NEXT_SEED_FOLDER, seeds.toString()));
            Page landed = browser.press("use-folder", "use", Map.of()).page();
            boolean profileWritten = Files.exists(workingDirectory.resolve(ProfileEdit.FILE_NAME));
            Element next = form(landed, "next");

            claim("the choice lands on the next step's box, the folder in the seed folder's field",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getFragment()),
                                    next.select("[name=seedFolder]").val()))
                            .containsExactly("/", "next-step", seeds.toString()));
            claim("every value and reason typed is held",
                    () -> assertThat(typed).allSatisfy((name, value) -> assertThat(
                            next.select("[name='" + name + "']").val()).isEqualTo(value)));
            claim("and no profile was written", () -> assertThat(profileWritten).isFalse());
        }
    }

    /**
     * Once saved, a field holds what the file states (ADR-330 §2, in place of ADR-331 §5's emptied form), so the form
     * reads the same as it did while held. What tells the two apart is the file changing behind the page: a form let go
     * follows it, and one still held would not.
     */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Choosing the seed folder on the profile page keeps the reason typed, writes no profile, returns to that value, and Save then writes both, after which the form holds what the file states and no longer what was chosen")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void chooseTheSeedFolderOnTheProfilePage() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/profile");
            browser.press(PROFILE_FORM, "browse-" + PROFILE_SEED_FOLDER, Map.of(SEED_FOLDER_PROVENANCE, REASON_TYPED));
            browser.go(folders(PROFILE_SEED_FOLDER, seeds.toString()));
            Page landed = browser.press("use-folder", "use", Map.of()).page();
            boolean profileWritten = Files.exists(workingDirectory.resolve(ProfileEdit.FILE_NAME));
            Element seedForm = form(landed, PROFILE_FORM);

            claim("the choice lands in the drawer's run settings, at the seed folder's section",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getQuery()),
                                    String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "open=values-profile", "key-seedFolder"));
            claim("with the folder as the value and the reason as typed",
                    () -> assertThat(List.of(seedForm.select("[name=" + SEED_FOLDER + "]").val(),
                                    seedForm.select("[name='" + SEED_FOLDER_PROVENANCE + "']").val()))
                            .containsExactly(seeds.toString(), REASON_TYPED));
            claim("no profile was written", () -> assertThat(profileWritten).isFalse());

            Page saved = browser.press(PROFILE_FORM, "save", Map.of()).page();

            claim("Save writes the folder chosen with the reason typed",
                    () -> assertThat(profileOf(workingDirectory).get(ProfileKey.SEED_FOLDER))
                            .isEqualTo(new ProfileEdit.Stated(seeds.toString(), REASON_TYPED)));
            claim("once saved, the form holds the folder and the reason the file now states",
                    () -> assertThat(List.of(form(saved, PROFILE_FORM).select("[name=" + SEED_FOLDER + "]").val(),
                                    form(saved, PROFILE_FORM).select("[name='" + SEED_FOLDER_PROVENANCE + "']").val()))
                            .containsExactly(seeds.toString(), REASON_TYPED));

            Path anotherSeedFolder = Files.createDirectories(folder.resolve("other-seeds"));
            Path profile = workingDirectory.resolve(ProfileEdit.FILE_NAME);
            Files.writeString(profile, ProfileEdit.rewrite(Optional.of(Files.readString(profile, StandardCharsets.UTF_8)),
                    ProfileKey.SEED_FOLDER, anotherSeedFolder.toString(), ANOTHER_REASON), StandardCharsets.UTF_8);
            Page reloaded = browser.go("/profile").page();

            claim("and neither is held any longer: with the file changed behind the page, the form holds what the file"
                            + " states and not what was chosen",
                    () -> assertThat(List.of(form(reloaded, PROFILE_FORM).select("[name=" + SEED_FOLDER + "]").val(),
                                    form(reloaded, PROFILE_FORM).select("[name='" + SEED_FOLDER_PROVENANCE + "']").val()))
                            .containsExactly(anotherSeedFolder.toString(), ANOTHER_REASON));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A refused save of the next act's seed folder keeps the folder chosen and the reason typed in their fields")
    void aRefusedSaveOfTheNextPagesSeedFolderKeepsWhatItHeld() throws IOException, InterruptedException {
        Path workingDirectory = wanting(SEED_FOLDER);
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(aCorpusRoot(), workingDirectory))) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            Path profile = Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), A_PROFILE_NOT_IN_FORM,
                    StandardCharsets.UTF_8);
            browser.press("next", "browse-" + NEXT_SEED_FOLDER, Map.of("seedFolder.provenance", REASON_TYPED));
            browser.go(folders(NEXT_SEED_FOLDER, seeds.toString()));
            browser.press("use-folder", "use", Map.of());
            Page refused = browser.press("next", "save", Map.of()).page();
            String held = Files.readString(profile, StandardCharsets.UTF_8);
            Element next = form(refused, "next");

            claim("the save lands on the home page with one line saying why, and the file is as it was",
                    () -> {
                        assertThat(refused.address().getPath()).isEqualTo("/");
                        assertThat(byId(refused, "outcome").text()).isNotBlank();
                        assertThat(held).isEqualTo(A_PROFILE_NOT_IN_FORM);
                    });
            claim("and the field holds the folder chosen, and the reason as typed",
                    () -> assertThat(List.of(next.select("[name=seedFolder]").val(),
                                    next.select("[name='seedFolder.provenance']").val()))
                            .containsExactly(seeds.toString(), REASON_TYPED));
        }
    }

    /**
     * The save is refused by another holder of the working directory's lock, since a file not in the form draws no form
     * of a value on this page to press (ADR-330 §6), and the line names that holder, so no other refusal passes for it.
     */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A save of the profile page's seed folder refused because another invocation holds the working directory keeps the folder chosen and the reason typed in their fields")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void aRefusedSaveOfTheProfilePagesSeedFolderKeepsWhatItHeld() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        Path profile = Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), A_PROFILE_IN_FORM,
                StandardCharsets.UTF_8);
        Path lockFile = Files.writeString(workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME),
                ANOTHER_HOLDERS_LINE + System.lineSeparator(), StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(aCorpusRoot(), workingDirectory));
                FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                FileLock taken = channel.tryLock(LOCKED_BYTE, 1, false)) {
            PanelBrowser browser = screens.browser().go("/profile");
            browser.press(PROFILE_FORM, "browse-" + PROFILE_SEED_FOLDER, Map.of(SEED_FOLDER_PROVENANCE, REASON_TYPED));
            browser.go(folders(PROFILE_SEED_FOLDER, seeds.toString()));
            browser.press("use-folder", "use", Map.of());
            Page refused = browser.press(PROFILE_FORM, "save", Map.of()).page();
            String held = Files.readString(profile, StandardCharsets.UTF_8);
            Element seedForm = form(refused, PROFILE_FORM);

            claim("the save lands on the home page with one line naming the other holder, and the file is as it was",
                    () -> {
                        assertThat(refused.address().getPath()).isEqualTo("/");
                        assertThat(byId(refused, "outcome").text()).contains(ANOTHER_HOLDERS_LINE);
                        assertThat(held).isEqualTo(A_PROFILE_IN_FORM);
                    });
            claim("and the field holds the folder chosen, and the reason as typed",
                    () -> assertThat(List.of(seedForm.select("[name=" + SEED_FOLDER + "]").val(),
                                    seedForm.select("[name='" + SEED_FOLDER_PROVENANCE + "']").val()))
                            .containsExactly(seeds.toString(), REASON_TYPED));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A folder not relevant may not climb out of the corpus root, whether by dots, by an absolute path or by a backslash: nothing outside is listed")
    void aCodeSetsFolderMayNotClimbOutOfTheCorpusRoot() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Files.createDirectories(corpusRoot.resolve("tools").resolve("build"));
        Path outside = Files.createDirectories(folder.resolve("outside"));
        Files.createDirectories(outside.resolve("the-name-of-a-folder-outside"));
        List<String> climbing = List.of("..", "../outside", "tools/../../outside", outside.toString(),
                outside.toString().replace('\\', '/'), "/outside", "C:/x", "tools\\build");
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            for (String path : climbing) {
                Page page = browser.go(folders(NOT_RELEVANT_FOLDER, path)).page();

                claim("'" + path + "' is refused on the page, naming nothing outside and offering nothing to use",
                        () -> {
                            assertThat(listing(page)).isEqualTo("outside-the-corpus-root");
                            assertThat(page.document().outerHtml()).doesNotContain("the-name-of-a-folder-outside");
                            assertThat(page.document().select("#folder-list a, form[name=use-folder]")).isEmpty();
                        });
            }
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A post naming a folder outside the corpus root puts nothing in the field of a folder not relevant, and says so")
    void aPostClimbingOutOfTheCorpusRootChangesNoField() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/folders?for=" + NOT_RELEVANT_FOLDER);
            Page landed = browser.press("use-folder", "use", Map.of("path", "../outside")).page();

            claim("it lands back on the drawer's relevance section, the field empty, with one line saying why",
                    () -> assertThat(List.of(landed.address().getPath(),
                                    form(landed, "not-relevant").select("[name=new-not-relevant-folder]").val(),
                                    String.valueOf(byId(landed, "outcome").text().isBlank())))
                            .containsExactly("/", "", "false"));
        }
    }

    /** A link inside the corpus root leading out of it stays inside by its name and outside by where it lands. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A link inside the corpus root that leads out of it is not followed for a folder not relevant")
    void aLinkInsideTheCorpusRootLeadingOutOfItIsNotFollowed() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Path outside = Files.createDirectories(folder.resolve("outside"));
        Files.createDirectories(outside.resolve("the-name-of-a-folder-outside"));
        try {
            Files.createSymbolicLink(corpusRoot.resolve("leads-out"), outside);
        } catch (IOException | UnsupportedOperationException notAllowed) {
            assumeTrue(false, "this system lets this user create no symbolic link");
        }
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().go(folders(NOT_RELEVANT_FOLDER, "leads-out")).page();

            claim("the page refuses it as outside the corpus root, and names nothing it holds",
                    () -> {
                        assertThat(listing(page)).isEqualTo("outside-the-corpus-root");
                        assertThat(page.document().outerHtml()).doesNotContain("the-name-of-a-folder-outside");
                    });
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("With no corpus root named, the folder page of a folder not relevant says so and lists nothing")
    void withNoCorpusRootACodeSetsFolderPageListsNothing() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().go("/folders?for=" + NOT_RELEVANT_FOLDER).page();

            claim("the page says the corpus root is not named",
                    () -> assertThat(listing(page)).isEqualTo("no-corpus-root"));
            claim("and lists nothing and offers nothing to use",
                    () -> assertThat(page.document().select("#folder-list a, form[name=use-folder]")).isEmpty());
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A folder that is not there, a file, and a path no system could hold each say no such folder, with nothing to use")
    void aFolderThatIsNotThereSaysSoAndOffersNothingToUse() throws IOException {
        Path aFile = Files.writeString(folder.resolve(A_FILES_NAME), "text", StandardCharsets.UTF_8);
        List<String> paths = List.of(folder.resolve("not-there").toString(), aFile.toString(), "not\0a-path");
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            for (String path : paths) {
                Page page = browser.go(folders(WORKING_DIRECTORY, path)).page();

                claim("the page says there is no such folder, in words, lists nothing and offers nothing to use",
                        () -> {
                            assertThat(page.status()).isEqualTo(200);
                            assertThat(listing(page)).isEqualTo("no-such-folder");
                            assertThat(byId(page, "folder-listing").text()).isNotBlank();
                            assertThat(page.document().select("#folder-list a, form[name=use-folder]")).isEmpty();
                        });
            }
        }
    }

    /**
     * A folder not relevant's {@code path} begins with the name itself, so a strip of it would land in another folder:
     * one of the name without its space stands beside it, holding a subfolder of another name.
     */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A subfolder whose name begins with a space is listed, opened and chosen whole, and not as the folder of that name without its space, where the file system holds such a name")
    void aSubfolderWhoseNameBeginsWithASpaceIsCarriedWhole() throws IOException {
        Path corpusRoot = aCorpusRoot();
        String withoutTheSpace = BEGINS_WITH_A_SPACE.strip();
        Path spaced;
        try {
            spaced = Files.createDirectory(corpusRoot.resolve(BEGINS_WITH_A_SPACE));
        } catch (IOException | InvalidPathException refused) {
            assumeTrue(false, "this file system holds no folder whose name begins with a space");
            return;
        }
        Files.createDirectories(spaced.resolve("inside-the-one-with-the-space"));
        Files.createDirectories(corpusRoot.resolve(withoutTheSpace).resolve("inside-the-one-without-it"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page listed = browser.go(folders(NOT_RELEVANT_FOLDER, "")).page();
            List<Element> links = byId(listed, "folder-list").select("a");
            Page opened = browser.go(links.stream()
                    .filter(link -> link.wholeText().equals(BEGINS_WITH_A_SPACE))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no link of the listing is named '" + BEGINS_WITH_A_SPACE + "'"))
                    .attr("href")).page();
            Page chosen = browser.press("use-folder", "use", Map.of()).page();
            Page byItsWholePath = browser.go(folders(CORPUS_ROOT, spaced.toString())).page();

            claim("the link lands in the folder of the whole name, and lists what that folder holds",
                    () -> assertThat(List.of(listedPath(opened), byId(opened, "folder-list").text()))
                            .containsExactly(BEGINS_WITH_A_SPACE, "inside-the-one-with-the-space"));
            claim("Use this folder puts the whole name in the field of a folder not relevant",
                    () -> assertThat(form(chosen, "not-relevant").select("[name=new-not-relevant-folder]").val())
                            .isEqualTo(BEGINS_WITH_A_SPACE));
            claim("and a folder field's page, given the whole path, lists the same folder",
                    () -> assertThat(List.of(listedPath(byItsWholePath), byId(byItsWholePath, "folder-list").text()))
                            .containsExactly(spaced.toString(), "inside-the-one-with-the-space"));
        }
    }

    /** Reading is taken away from the folder alone, and given back, so that the temporary folder can be removed. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A folder that cannot be read says so on the page, with nothing to use")
    void aFolderThatCannotBeReadSaysSoOnThePage() throws IOException {
        Path closed = Files.createDirectories(folder.resolve("closed"));
        Files.createDirectories(closed.resolve("a-subfolder"));
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none());
                ReadingTakenAway ignored = ReadingTakenAway.from(closed)) {
            assumeTrue(cannotBeListed(closed), "this user can read a folder whatever its permissions say");
            Page page = screens.browser().go(folders(CORPUS_ROOT, closed.toString())).page();

            claim("the page says the folder cannot be read, in words",
                    () -> assertThat(List.of(listing(page), String.valueOf(byId(page, "folder-listing").text().isBlank())))
                            .containsExactly("cannot-be-listed", "false"));
            claim("and lists nothing and offers nothing to use",
                    () -> assertThat(page.document().select("#folder-list a, form[name=use-folder]")).isEmpty());
        }
    }

    /** A field not on the closed list, the Docling image among them, which is a launch value and no folder. */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A field off the closed list is answered not found, by address and by post, and no value is changed")
    void aFieldOffTheClosedListIsNotFoundAndChangesNothing() throws IOException {
        Path corpusRoot = aCorpusRoot();
        List<String> offTheList = List.of("vespera.docling.image", "seedFolder.provenance", "spring.datasource.url",
                "<script>alert(1)</script>", "", "new-folder", "seedFolder");
        LaunchValues values = new LaunchValues(corpusRoot.toString(), corpusRoot.toString(), "", IMAGE_TYPED, "", "",
                "", "");
        String[] namesOffTheList = offTheList.stream().filter(name -> !name.isEmpty()).toArray(String[]::new);
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser();
            String own = "Host: 127.0.0.1:" + screens.server().address().getPort();
            for (String field : offTheList) {
                RawHttp.Response answered = send(screens, "GET", folders(field, corpusRoot.toString()), List.of(own));

                claim("the address naming '" + field + "' is not found, in one line of plain text, repeating no name",
                        () -> {
                            assertThat(answered.status()).isEqualTo(404);
                            assertThat(answered.header("Content-Type")).singleElement(as(InstanceOfAssertFactories.STRING))
                                    .startsWith("text/plain");
                            assertThat(answered.body().strip()).isNotEmpty().doesNotContain("\n")
                                    .doesNotContain(namesOffTheList);
                        });
            }
            browser.go(folders(CORPUS_ROOT, corpusRoot.toString()));
            Page posted = browser.press("use-folder", "use", Map.of("for", "vespera.docling.image",
                    "path", "a-value-for-the-wrong-field")).page();
            Page launch = browser.go("/").page();

            claim("the post naming a field off the list is not found, in plain text, repeating neither what it named nor"
                            + " the value it carried",
                    () -> {
                        assertThat(posted.status()).isEqualTo(404);
                        assertThat(posted.header("Content-Type")).startsWith("text/plain");
                        assertThat(posted.document().text()).doesNotContain("vespera.docling.image",
                                "a-value-for-the-wrong-field");
                    });
            claim("and the value that field names is unchanged",
                    () -> assertThat(valueOf(launch, "vespera.docling.image")).isEqualTo(IMAGE_TYPED));
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("The folder page's address with no field goes to the launch page, as a path a form posts to does")
    void theAddressWithNoFieldGoesToTheLaunchPage() throws IOException {
        Path corpusRoot = aCorpusRoot();
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            Page bare = browser.go("/folders").page();
            Page pathAlone = browser.go("/folders?path="
                    + URLEncoder.encode(corpusRoot.toString(), StandardCharsets.UTF_8)).page();

            claim("each lands on the launch page",
                    () -> assertThat(List.of(bare.address().getPath(), pathAlone.address().getPath()))
                            .containsExactly("/", "/"));
        }
    }

    /**
     * Read at two moments, since {@code /next} shows the seed folder's field only after a run, and {@code /profile}
     * holds it at both (ADR-334 §7). The fields are compared
     * whole, so that a {@code GET} holding the folder it shows in any field but the next act's turns this red. The next
     * act's field is first shown after the sweep before the run, and a {@code GET} of that sweep holding the folder
     * would stand in it from then on, so it is read too: it stands empty, the launch values naming no last seed folder.
     */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Opening the folder page for each field writes no file and changes no field of the home page, its drawer or its next step")
    @Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
    void openingTheFolderPageWritesNothing() throws IOException, InterruptedException {
        Path corpusRoot = aCorpusRoot();
        Files.createDirectories(corpusRoot.resolve("inside"));
        Path workingDirectory = wanting(SEED_FOLDER);
        Path launchValues = LaunchValues.fileIn(folder.resolve("tmp"), USER);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Comparison beforeARun = openingEachField(screens.browser(), corpusRoot, List.of("/"));
            Comparison afterARun = openingEachField(screens.afterARunHasEnded(), corpusRoot, List.of("/"));

            claim("the fields compared are the seven the folder page chooses for, each in its own form of the home page",
                    () -> {
                        assertThat(beforeARun.fieldsBefore().get("/")).containsKeys("launch-values/" + CORPUS_ROOT,
                                "launch-values/" + WORKING_DIRECTORY, "launch-values/" + DELIVERABLE_ROOT,
                                PROFILE_FORM + "/" + SEED_FOLDER, "code-set-folders/new-code-set-folder",
                                "not-relevant/new-not-relevant-folder");
                        assertThat(afterARun.fieldsBefore().get("/")).containsKey("next/seedFolder");
                    });
            claim("the next step's field, which the home page showed only after the run, was left empty by the sweep"
                            + " before it",
                    () -> assertThat(afterARun.fieldsBefore().get("/").get("next/seedFolder")).isEmpty());
            claim("before a run, every field of the home page is as it was",
                    () -> assertThat(beforeARun.fieldsAfter()).isEqualTo(beforeARun.fieldsBefore()));
            claim("after a run, every field of the home page, the next step's among them, is as it was",
                    () -> assertThat(afterARun.fieldsAfter()).isEqualTo(afterARun.fieldsBefore()));
            claim("the test's folder held the file of launch values",
                    () -> assertThat(beforeARun.filesBefore().keySet()).contains(folder.relativize(launchValues)));
            for (Comparison comparison : List.of(beforeARun, afterARun)) {
                claim("every file under the test's folder, the launch values among them, is as it was, and none was added",
                        () -> assertThat(comparison.filesAfter().keySet()).isEqualTo(comparison.filesBefore().keySet())
                                .allSatisfy(file -> assertThat(comparison.filesAfter().get(file))
                                        .isEqualTo(comparison.filesBefore().get(file))));
            }
        }
    }

    /** What {@code pages} held in their fields, and what {@code folder} held in its files, around a sweep of the folder page. */
    private record Comparison(Map<String, Map<String, String>> fieldsBefore, Map<Path, byte[]> filesBefore,
            Map<String, Map<String, String>> fieldsAfter, Map<Path, byte[]> filesAfter) {
    }

    private Comparison openingEachField(PanelBrowser browser, Path corpusRoot, List<String> pages) throws IOException {
        Map<String, Map<String, String>> fieldsBefore = fieldsOf(browser, pages);
        Map<Path, byte[]> filesBefore = contents(folder);
        for (String field : THE_CLOSED_LIST.keySet()) {
            boolean fromTheCorpusRoot = field.equals(NOT_RELEVANT_FOLDER);
            browser.go(folders(field, fromTheCorpusRoot ? "inside" : corpusRoot.resolve("inside").toString()));
        }
        Map<Path, byte[]> filesAfter = contents(folder);
        return new Comparison(fieldsBefore, filesBefore, fieldsOf(browser, pages), filesAfter);
    }

    /** Each page's fields, by its form's name and the field's, with what each holds: of a select, the option chosen. */
    private static Map<String, Map<String, String>> fieldsOf(PanelBrowser browser, List<String> pages) {
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        for (String path : pages) {
            Map<String, String> ofThisPage = new TreeMap<>();
            for (Element form : browser.go(path).page().document().select("form")) {
                for (Element control : form.select("input, select")) {
                    ofThisPage.put(form.attr("name") + "/" + control.attr("name"), control.nameIs("select")
                            ? String.join(",", control.select("option[selected]").eachAttr("value")) : control.val());
                }
            }
            fields.put(path, ofThisPage);
        }
        return fields;
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("The folder page passes the server's checks as every page does: another site, another address, a post from elsewhere and another method are refused")
    void theFolderPagePassesTheServersChecks() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Files.createDirectories(corpusRoot.resolve("a-subfolder"));
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            int port = screens.server().address().getPort();
            String own = "Host: 127.0.0.1:" + port;
            String target = folders(CORPUS_ROOT, corpusRoot.toString());
            RawHttp.Response answered = send(screens, "GET", target, List.of(own));
            RawHttp.Response anotherSite = send(screens, "GET", target, List.of(own, "Sec-Fetch-Site: cross-site"));
            RawHttp.Response aSiblingSite = send(screens, "GET", target, List.of(own, "Sec-Fetch-Site: same-site"));
            RawHttp.Response anotherAddress = send(screens, "GET", target, List.of("Host: example.com"));
            RawHttp.Response aPostFromElsewhere = send(screens, "POST", target,
                    List.of(own, "Origin: https://example.com"));
            RawHttp.Response anotherMethod = send(screens, "PUT", target, List.of(own));

            claim("a request from its own address is answered with the listing",
                    () -> assertThat(List.of(String.valueOf(answered.status()),
                                    String.valueOf(answered.body().contains("a-subfolder"))))
                            .containsExactly("200", "true"));
            claim("another site's, a sibling site's and another address's are refused, naming nothing listed",
                    () -> assertThat(List.of(anotherSite, aSiblingSite, anotherAddress)).allSatisfy(response -> {
                        assertThat(response.status()).isEqualTo(403);
                        assertThat(response.body()).isEqualTo(ControlPanelServer.REFUSAL);
                    }));
            claim("a post from another origin is refused",
                    () -> assertThat(aPostFromElsewhere.status()).isEqualTo(403));
            claim("and another method is answered as every other page answers it",
                    () -> assertThat(anotherMethod.status()).isEqualTo(405));
        }
    }

    @Test
    @Story("The control panel's pages run only its own script")
    @DisplayName("The folder page holds one script, its own, and no event attribute, though a folder's name and the address hold markup")
    void theFolderPageHoldsOnlyTheOneScriptThoughTheAddressHoldsMarkup() throws IOException {
        Path corpusRoot = aCorpusRoot();
        Files.createDirectories(corpusRoot.resolve("a&b'c"));
        LaunchValues aCorpusRootNamed = new LaunchValues(corpusRoot.toString(), "", "", "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, aCorpusRootNamed)) {
            PanelBrowser browser = screens.browser();
            Page listed = browser.go(folders(CORPUS_ROOT, corpusRoot.toString())).page();
            Page markup = browser.go(folders(CORPUS_ROOT, MARKUP_IN_THE_QUERY)).page();
            Page markupForACodeSet = browser.go(folders(NOT_RELEVANT_FOLDER, MARKUP_IN_THE_QUERY)).page();

            claim("a folder not relevant's page, a corpus root named, answers the path as it came, markup and all, in its"
                            + " attribute",
                    () -> assertThat(List.of(listing(markupForACodeSet), listedPath(markupForACodeSet)))
                            .containsExactly("no-such-folder", MARKUP_IN_THE_QUERY));
            for (Page page : List.of(listed, markup, markupForACodeSet)) {
                claim("the page holds one script, at the control panel's own address, with no text",
                        () -> assertThat(page.document().select("script"))
                                .singleElement()
                                .satisfies(script -> {
                                    assertThat(script.attr("src")).isEqualTo(TabPaths.SCRIPT);
                                    assertThat(script.data()).isEmpty();
                                }));
                claim("and no element holds an attribute of an event",
                        () -> assertThat(page.document().getAllElements().stream()
                                .flatMap(element -> element.attributes().asList().stream())
                                .map(Attribute::getKey)
                                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("on")))
                                .isEmpty());
            }
            claim("and a folder's name reaches the page as text, whole",
                    () -> assertThat(byId(listed, "folder-list").select("a").eachText()).containsExactly("a&b'c"));
        }
    }

    /** The closed list as ADR-340 §3 gives it: each field and the place of the home page it returns to. */
    private static Map<String, String> closedList() {
        Map<String, String> list = new LinkedHashMap<>();
        list.put(CORPUS_ROOT, "/?open=values-folders#values");
        list.put(WORKING_DIRECTORY, "/?open=values-folders#values");
        list.put(DELIVERABLE_ROOT, "/?open=values-folders#values");
        list.put(NEXT_SEED_FOLDER, "/#next-step");
        list.put(PROFILE_SEED_FOLDER, "/?open=values-profile#key-seedFolder");
        list.put(CODE_SET_FOLDER, "/?open=values-corpus#values-corpus");
        list.put(NOT_RELEVANT_FOLDER, "/?open=values-relevance#values-relevance");
        return list;
    }

    /** Each form of the home page holding a folder field, with its Browse buttons, in the page's order of forms. */
    private static Map<String, List<String>> browseButtonsOfEachForm() {
        Map<String, List<String>> buttons = new LinkedHashMap<>();
        buttons.put("launch-values", List.of("browse-" + CORPUS_ROOT, "browse-" + WORKING_DIRECTORY,
                "browse-" + DELIVERABLE_ROOT));
        buttons.put("next", List.of("browse-" + NEXT_SEED_FOLDER));
        buttons.put(PROFILE_FORM, List.of("browse-" + PROFILE_SEED_FOLDER));
        buttons.put("code-set-folders", List.of("browse-" + CODE_SET_FOLDER));
        buttons.put("not-relevant", List.of("browse-" + NOT_RELEVANT_FOLDER));
        return buttons;
    }

    private Path aCorpusRoot() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    /** A working directory where a child ending with 0 leaves a next act wanting {@code wants}. */
    private Path wanting(String wants) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=%s
                answer=false
                """.formatted(wants), StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    static String folders(String field, String path) {
        return "/folders?for=" + URLEncoder.encode(field, StandardCharsets.UTF_8) + "&path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    static String listing(Page page) {
        return byId(page, "folder-listing").attr("data-listing");
    }

    static String listedPath(Page page) {
        return byId(page, "folder-listing").attr("data-path");
    }

    private static String valueOf(Page page, String name) {
        return page.document().select("[name='" + name + "']").val();
    }

    /** The {@code path} of a link of the folder page's query, decoded. */
    static String pathIn(String href) {
        String query = URI.create(href).getRawQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith("path=")) {
                return URLDecoder.decode(pair.substring("path=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("the link " + href + " names no path");
    }

    private static Map<ProfileKey, ProfileEdit.Stated> profileOf(Path workingDirectory) throws IOException {
        return ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));
    }

    private static RawHttp.Response send(ScreenServer screens, String method, String target, List<String> headers)
            throws IOException {
        return RawHttp.send(screens.server().address(), method, target, headers);
    }

    /** Every regular file under {@code root}, by its path from there, with its bytes. */
    private static Map<Path, byte[]> contents(Path root) throws IOException {
        Map<Path, byte[]> contents = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                contents.put(root.relativize(file), Files.readAllBytes(file));
            }
        }
        return contents;
    }

    private static boolean cannotBeListed(Path closed) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(closed)) {
            return !entries.iterator().hasNext();
        } catch (IOException | RuntimeException refused) {
            return true;
        }
    }

    /** Reading a folder taken away by its permissions or its access list, and given back on close. */
    private static final class ReadingTakenAway implements AutoCloseable {

        private final Path taken;

        private final Runnable giveBack;

        private ReadingTakenAway(Path taken, Runnable giveBack) {
            this.taken = taken;
            this.giveBack = giveBack;
        }

        static ReadingTakenAway from(Path folder) throws IOException {
            AclFileAttributeView acl = Files.getFileAttributeView(folder, AclFileAttributeView.class);
            if (acl != null) {
                var held = acl.getAcl();
                acl.setAcl(List.of());
                return new ReadingTakenAway(folder, () -> {
                    try {
                        acl.setAcl(held);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
            var held = Files.getPosixFilePermissions(folder);
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("---------"));
            return new ReadingTakenAway(folder, () -> {
                try {
                    Files.setPosixFilePermissions(folder, held);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }

        @Override
        public void close() {
            giveBack.run();
        }

        @Override
        public String toString() {
            return "reading taken away from " + taken;
        }
    }
}
