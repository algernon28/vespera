package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.controlpanel.TheFolderPageScreenTest.folders;
import static io.algernon.controlpanel.TheFolderPageScreenTest.listedPath;
import static io.algernon.controlpanel.TheFolderPageScreenTest.listing;
import static io.algernon.controlpanel.TheFolderPageScreenTest.pathIn;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A folder field holds a folder's name whole (ADR-331 §6): it keeps white space before the name and loses white space
 * after it, as its form is posted, as Browse carries it and as the field is read, on each form holding one. Read without
 * its space, ` x` would be {@code x}, another folder, with nothing on the page to say so. A field that names no folder
 * loses white space at both ends.
 *
 * <p>Most of these name a folder of the current directory the tests run in, {@code src}, after a space, so that read
 * whole the value names nothing and read stripped it names a folder that is there. Nothing is written there.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class FolderFieldsKeepALeadingSpaceScreenTest {

    private static final String CORPUS_ROOT = "vespera.corpus-root";

    private static final String WORKING_DIRECTORY = "vespera.working-dir";

    private static final String DELIVERABLE_ROOT = "vespera.deliverable-root";

    private static final String NEW_FOLDER = "new-folder";

    /** A folder lying in the current directory the tests run in. */
    private static final String THERE_WITHOUT_THE_SPACE = "src";

    /** That name after a space: whole it names nothing, and stripped it names that folder. */
    private static final String WITH_A_LEADING_SPACE = " " + THERE_WITHOUT_THE_SPACE;

    /** The launch form's three folders, each with what reads it back out of the launch values' file. */
    private static final Map<String, Function<LaunchValues, String>> THE_LAUNCH_FOLDERS = launchFolders();

    private static final String NO_SUCH_FOLDER = "no-such-folder";

    private static final String WILL_BE_CREATED = "will-be-created";

    private static final String REASON = "the reason the operator typed";

    private static final String USER = System.getProperty("user.name");

    /** The three forms holding the seed folder: the name Browse gives it, its page, its form, and its two controls. */
    private record SeedForm(String forName, String page, String form, String control, String provenance) {

        Map<String, String> holding(String value) {
            return Map.of(control, value, provenance, REASON);
        }

        String held(Page page) {
            return ScreenServer.form(page, form).select("[name='" + control + "']").val();
        }

        String status(Page page) {
            return byId(page, "status-" + forName).attr("data-status");
        }
    }

    private static final List<SeedForm> THE_SEED_FORMS = List.of(
            new SeedForm("seedFolder", "/", "seed-folder", "seedFolder", "provenance"),
            new SeedForm("next-seedFolder", "/next", "next", "seedFolder", "seedFolder.provenance"),
            new SeedForm("profile-seedFolder", "/profile", "seedFolder", "value", "provenance"));

    @TempDir
    Path folder;

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("Each folder of the launch form keeps a space before its name through Browse, Use this folder and Run, and is not read as the folder of that name without it")
    void eachLaunchFolderKeepsASpaceBeforeItsNameThroughBrowseUseThisFolderAndRun() throws IOException {
        Path corpusRoot = aFolder("corpus");
        Path workingDirectory = aFolder("work");
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        for (Map.Entry<String, Function<LaunchValues, String>> field : THE_LAUNCH_FOLDERS.entrySet()) {
            String name = field.getKey();
            Path own = aFolder("own-" + name);
            try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                PanelBrowser browser = screens.browser();
                Page browsed = browser.press("launch", "browse-" + name, Map.of(name, WITH_A_LEADING_SPACE)).page();
                String heldAfterBrowse = launchValue(browser.go("/").page(), name);
                browser.go(folders(name, corpusRoot.toString()));
                String heldAfterUse = launchValue(browser.press("use-folder", "use",
                        Map.of("path", WITH_A_LEADING_SPACE)).page(), name);
                Page afterRun = browser.press("launch", "run", Map.of()).page();
                String lineOfRun = afterRun.document().select("#outcome").stream().findFirst()
                        .map(Element::wholeText).orElse("");
                String heldAfterRun = launchValue(browser.go("/").page(), name);
                String keptByRun = field.getValue().apply(LaunchValues.load(
                        LaunchValues.fileIn(own.resolve("tmp"), USER), USER, line -> { }));
                boolean noChildCanStart = name.equals(WORKING_DIRECTORY);

                claim("Browse on " + name + " carries the whole value to the folder page, which reads that folder, and"
                                + " not the one of that name without the space",
                        () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed), listedPath(browsed)))
                                .containsExactly(WITH_A_LEADING_SPACE, NO_SUCH_FOLDER,
                                        Path.of(WITH_A_LEADING_SPACE).toAbsolutePath().normalize().toString()));
                claim("and " + name + " holds it whole after Browse, after Use this folder and after Run",
                        () -> assertThat(List.of(heldAfterBrowse, heldAfterUse, heldAfterRun))
                                .containsOnly(WITH_A_LEADING_SPACE));
                claim("Run with " + name + " so held "
                                + (noChildCanStart ? "is refused in its one line, the file as it was"
                                        : "starts, the file keeping it whole"),
                        () -> assertThat(List.of(afterRun.address().getPath(), lineOfRun, keptByRun)).containsExactly(
                                noChildCanStart ? "/" : "/invocation",
                                noChildCanStart ? "Run is not started: the working directory " + WITH_A_LEADING_SPACE
                                        + " is no folder that is there" : "",
                                noChildCanStart ? workingDirectory.toString() : WITH_A_LEADING_SPACE));
                claim("and no folder of that name was created in the current directory",
                        () -> assertThat(Path.of(WITH_A_LEADING_SPACE)).doesNotExist());
            }
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("A folder of the launch form loses a space after its name, so Browse and Run still read the folder, and a field that names no folder loses spaces at both ends")
    void aLaunchFolderLosesASpaceAfterItsNameAndAFieldNamingNoFolderLosesBoth() throws IOException {
        Path corpusRoot = aFolder("corpus");
        String withTrailingSpaces = corpusRoot + "  ";
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            Page browsed = browser.press("launch", "browse-" + CORPUS_ROOT, Map.of(CORPUS_ROOT, withTrailingSpaces,
                    "vespera.docling.image", "  an-image-typed  ", "vespera.label.model", " a-model-typed ")).page();
            Page launch = browser.go("/").page();

            claim("Browse lands on the folder itself, listed, its address holding no space after the name",
                    () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed), listedPath(browsed)))
                            .containsExactly(corpusRoot.toString(), "listed", corpusRoot.toString()));
            claim("the field holds the folder without the spaces after it, and says it found it",
                    () -> assertThat(List.of(launchValue(launch, CORPUS_ROOT),
                                    byId(launch, "status-" + CORPUS_ROOT).attr("data-status")))
                            .containsExactly(corpusRoot.toString(), "found"));
            claim("and the two values typed that name no folder lose the spaces at both ends",
                    () -> assertThat(List.of(launchValue(launch, "vespera.docling.image"),
                                    launchValue(launch, "vespera.label.model")))
                            .containsExactly("an-image-typed", "a-model-typed"));
        }
    }

    /**
     * A value pasted with a space before it, whether it names a folder of the current directory or a whole path. Whole,
     * it is a folder that is not there, or on Windows a path the system cannot hold, a colon no longer following a drive.
     * The one field whose folder the jar creates says that where the system can hold it.
     */
    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("A value pasted with a space before it says so beside each launch folder, and the preflight's rows are red")
    void aValuePastedWithASpaceBeforeItSaysSoBesideEachLaunchFolder() throws IOException {
        Path found = aFolder("found");
        claim("the current directory holds the folder of that name without the space",
                () -> assertThat(Path.of(THERE_WITHOUT_THE_SPACE)).isDirectory());
        for (String pasted : List.of(WITH_A_LEADING_SPACE, " " + found)) {
            String createdByTheJar = theSystemRefuses(pasted) ? NO_SUCH_FOLDER : WILL_BE_CREATED;
            Path own = aFolder("own-" + (pasted.equals(WITH_A_LEADING_SPACE) ? "name" : "path"));
            try (ScreenServer screens = ScreenServer.start(own, LaunchValues.none())) {
                PanelBrowser browser = screens.browser();
                browser.press("seed-folder", "browse-seedFolder", Map.of("seedFolder", pasted));
                Page launch = browser.go("/").press("launch", "check", Map.of(CORPUS_ROOT, pasted,
                        WORKING_DIRECTORY, pasted, DELIVERABLE_ROOT, pasted)).page();

                claim("'" + pasted + "': the corpus root, the seed folder and the working directory say no such folder,"
                                + " and the deliverable root, which the jar creates, says what is to become of it",
                        () -> assertThat(List.of(byId(launch, "status-" + CORPUS_ROOT).attr("data-status"),
                                        byId(launch, "status-seedFolder").attr("data-status"),
                                        byId(launch, "status-" + WORKING_DIRECTORY).attr("data-status"),
                                        byId(launch, "status-" + DELIVERABLE_ROOT).attr("data-status")))
                                .containsExactly(NO_SUCH_FOLDER, NO_SUCH_FOLDER, NO_SUCH_FOLDER, createdByTheJar));
                claim("'" + pasted + "': its corpus root's and its working directory's rows of the preflight are red",
                        () -> assertThat(List.of(rowOfThePreflight(launch, "corpus-root"),
                                        rowOfThePreflight(launch, "working-directory")))
                                .containsExactly("bad", "bad"));
            }
        }
    }

    /** On Windows a colon that follows no drive is a character {@code Path.of} refuses, so a space before a whole path makes one it cannot hold. */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run refuses a corpus root pasted with a space before its drive, in the one line, where the system cannot hold it")
    void runRefusesACorpusRootPastedWithASpaceBeforeItsDriveWhereTheSystemCannotHoldIt() throws IOException {
        Path corpusRoot = aFolder("corpus");
        Path workingDirectory = aFolder("work");
        String pasted = " " + corpusRoot;
        assumeTrue(theSystemRefuses(pasted), "this system holds a path of that text");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Path launchValues = LaunchValues.fileIn(folder.resolve("tmp"), USER);
            byte[] keptBefore = Files.readAllBytes(launchValues);
            Page landed = browser.press("launch", "run", Map.of(CORPUS_ROOT, pasted)).page();
            Page invocation = browser.go("/invocation").page();
            byte[] keptAfter = Files.readAllBytes(launchValues);

            claim("it lands on the launch page with the one line, the field holding what was pasted",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text(),
                                    launchValue(landed, CORPUS_ROOT)))
                            .containsExactly("/", "Run is not started: the corpus root is no path this system can hold",
                                    pasted));
            claim("no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
            claim("and the file of launch values is as it was", () -> assertThat(keptAfter).isEqualTo(keptBefore));
        }
    }

    /**
     * Save reads the seed folder as the profile does, the absolute path of a folder that exists, so a value with a space
     * before it is refused whichever form holds it, where read stripped it would be saved as another value.
     */
    @Test
    @Story("The operator chooses a folder")
    @DisplayName("The seed folder keeps a space before it through Browse and Use this folder on each form holding it, says no such folder, and Save refuses it and keeps it")
    void theSeedFolderKeepsASpaceBeforeItOnEachFormAndSaveRefusesIt() throws IOException, InterruptedException {
        Path corpusRoot = aFolder("corpus");
        Path seeds = aFolder("seeds");
        String pasted = " " + seeds;
        for (SeedForm seedForm : THE_SEED_FORMS) {
            Path own = aFolder("own-" + seedForm.forName());
            Path workingDirectory = wantingTheSeedFolder(own);
            try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                PanelBrowser browser = openedOn(screens, seedForm);
                Page browsed = browser.press(seedForm.form(), "browse-" + seedForm.forName(), seedForm.holding(pasted))
                        .page();
                Page back = browser.go(seedForm.page()).page();
                browser.go(folders(seedForm.forName(), seeds.toString()));
                Page chosen = browser.press("use-folder", "use", Map.of("path", pasted)).page();
                Page saved = browser.press(seedForm.form(), "save", Map.of()).page();
                String savedField = seedForm.held(saved);
                boolean profileWritten = Files.exists(workingDirectory.resolve(ProfileEdit.FILE_NAME));

                claim("Browse on " + seedForm.forName() + " carries the whole value to the folder page, which finds no"
                                + " such folder",
                        () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed)))
                                .containsExactly(pasted, NO_SUCH_FOLDER));
                claim("its field holds it whole after Browse and after Use this folder, and says no such folder",
                        () -> assertThat(List.of(seedForm.held(back), seedForm.status(back), seedForm.held(chosen),
                                        seedForm.status(chosen)))
                                .containsExactly(pasted, NO_SUCH_FOLDER, pasted, NO_SUCH_FOLDER));
                claim("Save answers with one line and writes no profile",
                        () -> {
                            assertThat(byId(saved, "outcome").text()).contains("is not saved");
                            assertThat(profileWritten).isFalse();
                        });
                claim("and the field still holds what was pasted",
                        () -> assertThat(savedField).isEqualTo(pasted));
            }
        }
    }

    @Test
    @Story("The operator chooses a folder")
    @DisplayName("The seed folder loses a space after it on each form holding it, so Browse and Save still read the folder")
    void theSeedFolderLosesASpaceAfterItOnEachForm() throws IOException, InterruptedException {
        Path corpusRoot = aFolder("corpus");
        Path seeds = aFolder("seeds");
        String withTrailingSpaces = seeds + "  ";
        for (SeedForm seedForm : THE_SEED_FORMS) {
            Path own = aFolder("own-" + seedForm.forName());
            Path workingDirectory = wantingTheSeedFolder(own);
            try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                PanelBrowser browser = openedOn(screens, seedForm);
                Page browsed = browser.press(seedForm.form(), "browse-" + seedForm.forName(),
                        seedForm.holding(withTrailingSpaces)).page();
                Page back = browser.go(seedForm.page()).page();
                browser.press(seedForm.form(), "save", Map.of());
                ProfileEdit.Stated written = ProfileEdit.read(Files.readString(
                        workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8)).get(ProfileKey.SEED_FOLDER);

                claim("Browse on " + seedForm.forName() + " lands on the folder itself, its address holding no space after"
                                + " the name",
                        () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed), listedPath(browsed)))
                                .containsExactly(seeds.toString(), "listed", seeds.toString()));
                claim("the field says it found it", () -> assertThat(seedForm.status(back)).isEqualTo("found"));
                claim("the field holds the folder without the spaces",
                        () -> assertThat(seedForm.held(back)).isEqualTo(seeds.toString()));
                claim("and Save writes the folder without the spaces, with the reason typed",
                        () -> assertThat(written).isEqualTo(new ProfileEdit.Stated(seeds.toString(), REASON)));
            }
        }
    }

    /** {@code tools} lies under the corpus root and ` tools` does not, and a code set's folder need not be there yet. */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("A code set's new folder keeps a space before it through Browse and the field, says no such folder, and Save writes it whole and not as the folder without it")
    void aCodeSetsNewFolderKeepsASpaceBeforeItThroughBrowseAndSave() throws IOException {
        Path corpusRoot = aFolder("corpus");
        Files.createDirectories(corpusRoot.resolve("tools"));
        Path workingDirectory = aFolder("work");
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        String withASpace = " tools";
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            Page browsed = browser.press("code-sets", "browse-" + NEW_FOLDER,
                    Map.of(NEW_FOLDER, withASpace, "new-name", "The tools")).page();
            Page back = browser.go("/code-sets").page();
            browser.press("code-sets", "save", Map.of());

            claim("Browse carries the whole value to the folder page, which finds no such folder under the corpus root",
                    () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed), listedPath(browsed)))
                            .containsExactly(withASpace, NO_SUCH_FOLDER, withASpace));
            claim("the field holds it whole, and says no such folder",
                    () -> assertThat(List.of(newFolder(back), byId(back, "status-" + NEW_FOLDER).attr("data-status")))
                            .containsExactly(withASpace, NO_SUCH_FOLDER));
            claim("and Save writes that folder whole under that name",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(names, StandardCharsets.UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename(withASpace, "The tools")));
        }
    }

    @Test
    @Story("The operator names the code sets")
    @DisplayName("A code set's new folder loses a space after it, so Browse, the field and Save read the folder itself")
    void aCodeSetsNewFolderLosesASpaceAfterIt() throws IOException {
        Path corpusRoot = aFolder("corpus");
        Files.createDirectories(corpusRoot.resolve("tools"));
        Path workingDirectory = aFolder("work");
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/code-sets");
            Page browsed = browser.press("code-sets", "browse-" + NEW_FOLDER,
                    Map.of(NEW_FOLDER, "tools  ", "new-name", "The tools")).page();
            Page back = browser.go("/code-sets").page();
            browser.press("code-sets", "save", Map.of());

            claim("Browse lands on the folder itself, its address holding no space after the name",
                    () -> assertThat(List.of(pathIn(browsed.address().toString()), listing(browsed), listedPath(browsed)))
                            .containsExactly("tools", "listed", "tools"));
            claim("the field says it found it",
                    () -> assertThat(byId(back, "status-" + NEW_FOLDER).attr("data-status")).isEqualTo("found"));
            claim("the field holds the folder without the spaces",
                    () -> assertThat(newFolder(back)).isEqualTo("tools"));
            claim("and Save writes the folder without the spaces",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(names, StandardCharsets.UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename("tools", "The tools")));
        }
    }

    /** A folder of that name beside the one without its space: the chain from the listing to the file reads the first. */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("A code set's folder whose name begins with a space is chosen, says found, and is saved whole, where the file system holds such a name")
    void aCodeSetsFolderWhoseNameBeginsWithASpaceIsSavedWhole() throws IOException {
        Path corpusRoot = aFolder("corpus");
        String named = " begins-with-a-space";
        Path spaced;
        try {
            spaced = Files.createDirectory(corpusRoot.resolve(named));
        } catch (IOException | InvalidPathException refused) {
            assumeTrue(false, "this file system holds no folder whose name begins with a space");
            return;
        }
        Files.createDirectories(spaced.resolve("inside-the-one-with-the-space"));
        Files.createDirectories(corpusRoot.resolve(named.strip()).resolve("inside-the-one-without-it"));
        Path workingDirectory = aFolder("work");
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go(folders(NEW_FOLDER, named));
            Page chosen = browser.press("use-folder", "use", Map.of()).page();
            Page browsed = browser.press("code-sets", "browse-" + NEW_FOLDER, Map.of("new-name", "The set")).page();
            browser.go("/code-sets").press("code-sets", "save", Map.of());

            claim("Use this folder puts the whole name in the field, which says it found it",
                    () -> assertThat(List.of(newFolder(chosen), byId(chosen, "status-" + NEW_FOLDER).attr("data-status")))
                            .containsExactly(named, "found"));
            claim("Browse from the field lands in that folder, and not in the one without the space",
                    () -> assertThat(List.of(listedPath(browsed), byId(browsed, "folder-list").text()))
                            .containsExactly(named, "inside-the-one-with-the-space"));
            claim("and Save writes it whole",
                    () -> assertThat(CodeSetRenames.read(Optional.of(Files.readString(names, StandardCharsets.UTF_8))))
                            .containsExactly(new CodeSetRenames.Rename(named, "The set")));
        }
    }

    /** What a Save refuses for climbing out is told by the text after any space before it, which a paste leaves. */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("Saving a new folder that climbs out of the corpus root with a space before it still writes nothing and says so")
    void aNewFolderThatClimbsOutWithASpaceBeforeItIsStillRefused() throws IOException {
        Path corpusRoot = aFolder("corpus");
        Path workingDirectory = aFolder("work");
        Path names = workingDirectory.resolve(CodeSetRenames.FILE_NAME);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            for (String climbing : List.of(" ../outside", " tools/../../x", " C:/x", " /x")) {
                Page landed = browser.go("/code-sets").press("code-sets", "save",
                        Map.of(NEW_FOLDER, climbing, "new-name", "The climbing set")).page();

                claim("'" + climbing + "' lands back on the code sets with one line saying why, and no file is written",
                        () -> {
                            assertThat(landed.address().getPath()).isEqualTo("/code-sets");
                            assertThat(byId(landed, "outcome").text()).isNotBlank();
                            assertThat(names).doesNotExist();
                        });
            }
        }
    }

    /** A code set's folder is read under the corpus root the field holds, whole: {@code main} lies under the folder without the space. */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("With a corpus root holding a space before its name, a code set's folder is read under that folder, and not under the one without the space")
    void aCodeSetsFolderIsReadUnderTheCorpusRootAsTheFieldHoldsIt() throws IOException {
        String inTheFolderWithoutTheSpace = "main";
        claim("the folder without the space holds a folder of that name",
                () -> assertThat(Path.of(THERE_WITHOUT_THE_SPACE).resolve(inTheFolderWithoutTheSpace)).isDirectory());
        Path workingDirectory = aFolder("work");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(aFolder("corpus"), workingDirectory))) {
            PanelBrowser browser = screens.browser().press("launch", "check", Map.of(CORPUS_ROOT, WITH_A_LEADING_SPACE));
            Page atTheCorpusRoot = browser.go(folders(NEW_FOLDER, "")).page();
            browser.go("/code-sets").press("code-sets", "browse-" + NEW_FOLDER, Map.of(NEW_FOLDER, inTheFolderWithoutTheSpace));
            Page codeSets = browser.go("/code-sets").page();

            claim("the folder page finds no such folder, lists nothing and links the corpus root by the field's whole value",
                    () -> {
                        assertThat(listing(atTheCorpusRoot)).isEqualTo(NO_SUCH_FOLDER);
                        assertThat(atTheCorpusRoot.document().select("#folder-list a")).isEmpty();
                        assertThat(byId(atTheCorpusRoot, "folder-roots").select("a").first().wholeText())
                                .isEqualTo(WITH_A_LEADING_SPACE);
                    });
            claim("and a folder lying under the one without the space says no such folder",
                    () -> assertThat(byId(codeSets, "status-" + NEW_FOLDER).attr("data-status")).isEqualTo(NO_SUCH_FOLDER));
        }
    }

    /**
     * Publish is offered once a rendering lies where the launch values say. The tree is written under {@code target},
     * by a path relative to the current directory, so that it can be named after a space, and is removed after the test.
     */
    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("A rendering lying in the folder without the space is not offered to publish, for a deliverable root or a working directory holding a space before its name")
    void aRenderingOfTheFolderWithoutTheSpaceIsNotOfferedToPublish() throws IOException {
        Path tree = Files.createTempDirectory(Path.of("target"), "a-rendering-");
        try {
            Files.createDirectories(tree.resolve("confluence"));
            Files.writeString(tree.resolve("confluence").resolve("pages.json"), "{}", StandardCharsets.UTF_8);
            Files.createDirectories(tree.resolve("deliverable").resolve("a-run").resolve("confluence"));
            Files.writeString(tree.resolve("deliverable").resolve("a-run").resolve("confluence").resolve("pages.json"),
                    "{}", StandardCharsets.UTF_8);
            Path corpusRoot = aFolder("corpus");
            Path workingDirectory = aFolder("work");
            try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
                PanelBrowser browser = screens.browser();
                Map<String, Integer> offered = new LinkedHashMap<>();
                offered.put("the deliverable root as the tree",
                        publishForms(browser, Map.of(DELIVERABLE_ROOT, tree.toString())));
                offered.put("the deliverable root after a space",
                        publishForms(browser, Map.of(DELIVERABLE_ROOT, " " + tree)));
                offered.put("the working directory as the tree",
                        publishForms(browser, Map.of(DELIVERABLE_ROOT, "", WORKING_DIRECTORY, tree.toString())));
                offered.put("the working directory after a space",
                        publishForms(browser, Map.of(DELIVERABLE_ROOT, "", WORKING_DIRECTORY, " " + tree)));

                claim("the tree itself is offered, by either value, and after a space it is not",
                        () -> assertThat(offered).containsExactly(Map.entry("the deliverable root as the tree", 1),
                                Map.entry("the deliverable root after a space", 0),
                                Map.entry("the working directory as the tree", 1),
                                Map.entry("the working directory after a space", 0)));
            }
        } finally {
            removeAll(tree);
        }
    }

    /** How many forms named {@code publish} the invocation page holds, the launch form having been posted with {@code values}. */
    private static int publishForms(PanelBrowser browser, Map<String, String> values) {
        return browser.go("/").press("launch", "check", values).go("/invocation").page().document()
                .select("form[name=publish]").size();
    }

    private static void removeAll(Path tree) throws IOException {
        try (Stream<Path> all = Files.walk(tree)) {
            for (Path each : all.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        }
    }

    /** The browser opened on the page holding {@code seedForm}: the next act's only once a run has ended. */
    private static PanelBrowser openedOn(ScreenServer screens, SeedForm seedForm) throws InterruptedException {
        return seedForm.page().equals("/next") ? screens.afterARunHasEnded().go("/next")
                : screens.browser().go(seedForm.page());
    }

    /**
     * A working directory under {@code own} where a child ending with 0 leaves a next act wanting the seed folder. It is
     * read only by the form on {@code /next}, whose page has seen that child end; {@code /profile} holds the form until then.
     */
    private static Path wantingTheSeedFolder(Path own) throws IOException {
        Path workingDirectory = Files.createDirectories(own.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder
                answer=false
                """, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    private Path aFolder(String name) throws IOException {
        return Files.createDirectories(folder.resolve(name));
    }

    private static boolean theSystemRefuses(String text) {
        try {
            Path.of(text);
            return false;
        } catch (InvalidPathException refused) {
            return true;
        }
    }

    private static String launchValue(Page page, String name) {
        Element launch = form(page, "launch");
        return launch.select("[name='" + name + "']").val();
    }

    private static String newFolder(Page page) {
        return form(page, "code-sets").select("[name=" + NEW_FOLDER + "]").val();
    }

    private static String rowOfThePreflight(Page page, String row) {
        return byId(page, "preflight").select("[data-row=" + row + "]").attr("data-state");
    }

    private static Map<String, Function<LaunchValues, String>> launchFolders() {
        Map<String, Function<LaunchValues, String>> folders = new LinkedHashMap<>();
        folders.put(CORPUS_ROOT, LaunchValues::corpusRoot);
        folders.put(WORKING_DIRECTORY, LaunchValues::workingDirectory);
        folders.put(DELIVERABLE_ROOT, LaunchValues::deliverableRoot);
        return folders;
    }
}
