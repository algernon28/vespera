package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Beside each folder field the server writes what it could check as the page was asked for (ADR-331 §6): found, with the
 * free space of its drive in GiB, will be created, no such folder, or not chosen. {@code data-status} is what a test
 * reads, as ADR-302 §9 has a test read an attribute and no words. The free space list keeps {@code data-usable-bytes}
 * (ADR-302 §6) and shows GiB.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class FolderFieldStatusScreenTest {

    private static final String FOUND = "found";

    private static final String WILL_BE_CREATED = "will-be-created";

    private static final String NO_SUCH_FOLDER = "no-such-folder";

    private static final String NOT_CHOSEN = "not-chosen";

    /** A gibibyte, the unit the free space is shown in. */
    private static final double BYTES_IN_A_GIB = 1024d * 1024d * 1024d;

    private static final String CORPUS_ROOT = "vespera.corpus-root";

    private static final String WORKING_DIRECTORY = "vespera.working-dir";

    private static final String DELIVERABLE_ROOT = "vespera.deliverable-root";

    /** A path {@code Path.of} refuses on every system; on Windows one pasted with its quotes is another. */
    private static final String A_PATH_NO_SYSTEM_CAN_HOLD = "not\0a-path";

    /** What a child started again would do: end with a code other than the first child's 0, so its ending shows. */
    private static final String A_SECOND_CHILD = "exit 7 a second child";

    /** One question, as the jar writes it, for the questions' form to hold. */
    private static final String ONE_QUESTION = """
            ---
            generatedUnderRun: "run-of-the-sample"
            generatedUnderEmbedder: "embedder@digest"
            generatedUnderSeedSet: "/seeds"
            documents:
            - path: "a/first.pdf"
              score: 0.83
              band: 2
              closestSeed: "seed/one.docx"
              relevant: null
            """;

    /** The status of a page that answered. */
    private static final String ANSWERED = "200";

    /** What a test reads of a request answered by no reply at all, as a handler that throws is. */
    private static final String NO_REPLY = "no reply";

    /** Every page, and the report that is not there, which is not found, by what each is to answer with such a path held. */
    private static final Map<String, String> ANSWER_OF_EACH_PAGE = answerOfEachPage();

    @TempDir
    Path folder;

    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("With no folder named, each folder field of the launch page says not chosen")
    void withNoFolderNamedEachFieldSaysNotChosen() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().page();

            claim("the corpus root, the working directory, the deliverable root and the seed folder each say not chosen",
                    () -> assertThat(launchStatuses(page)).containsOnly(NOT_CHOSEN).hasSize(4));
        }
    }

    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("Folders that exist say found, each with the free space of its drive in GiB and no count of bytes")
    void foldersThatExistSayFoundWithTheFreeSpaceInGib() throws IOException {
        Path found = Files.createDirectories(folder.resolve("found"));
        LaunchValues values = new LaunchValues(found.toString(), found.toString(), found.toString(), "", "", "", "",
                found.toString());
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            Page page = screens.browser().page();
            List<String> texts = List.of("vespera.corpus-root", "vespera.working-dir", "vespera.deliverable-root",
                    "seedFolder").stream().map(field -> byId(page, "status-" + field).text()).toList();

            claim("all four say found", () -> assertThat(launchStatuses(page)).containsOnly(FOUND).hasSize(4));
            claim("each gives the free space as a number with one decimal and GiB, and says nothing of bytes",
                    () -> assertThat(texts).allSatisfy(text -> assertThat(text)
                            .containsPattern("\\d+\\.\\d GiB").doesNotContain("bytes")));
        }
    }

    /** A file where a folder is named is no folder, and a working directory cannot be created over it. */
    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("A folder that is not there will be created where the jar creates one, and is no such folder where it must already be there; a file in its place is no such folder for any")
    void aFolderThatIsNotThereWillBeCreatedOnlyWhereTheJarCreatesIt() throws IOException {
        Path notThere = folder.resolve("not-there");
        Path aFile = Files.writeString(folder.resolve("a-file.txt"), "not a folder", StandardCharsets.UTF_8);
        LaunchValues missing = new LaunchValues(notThere.toString(), notThere.toString(), notThere.toString(), "", "",
                "", "", notThere.toString());
        LaunchValues files = new LaunchValues(aFile.toString(), aFile.toString(), aFile.toString(), "", "", "", "",
                aFile.toString());
        List<String> statusesOfMissing;
        try (ScreenServer screens = ScreenServer.start(folder, missing)) {
            statusesOfMissing = launchStatuses(screens.browser().page());
        }
        List<String> statusesOfFiles;
        try (ScreenServer screens = ScreenServer.start(folder, files)) {
            statusesOfFiles = launchStatuses(screens.browser().page());
        }

        claim("not there: the corpus root, the working directory and the seed folder must already be there, and the"
                        + " deliverable root will be created",
                () -> assertThat(statusesOfMissing)
                        .containsExactly(NO_SUCH_FOLDER, NO_SUCH_FOLDER, WILL_BE_CREATED, NO_SUCH_FOLDER));
        claim("a file in the place of each: no such folder, for all four",
                () -> assertThat(statusesOfFiles).containsOnly(NO_SUCH_FOLDER).hasSize(4));
    }

    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("The next act's seed folder says found or no such folder, as what its field holds")
    void theNextActsSeedFolderSaysWhatItsFieldHolds() throws IOException, InterruptedException {
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder
                answer=false
                """, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        LaunchValues withASeedFolder = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "",
                "", "", seeds.toString());
        try (ScreenServer screens = ScreenServer.start(folder, withASeedFolder)) {
            Page page = screens.afterARunHasEnded().go("/next").page();

            claim("the field filled with the last seed folder says found",
                    () -> assertThat(byId(page, "status-next-seedFolder").attr("data-status")).isEqualTo(FOUND));
        }
        Files.delete(seeds);
        try (ScreenServer screens = ScreenServer.start(folder, withASeedFolder)) {
            Page page = screens.afterARunHasEnded().go("/next").page();

            claim("and once that folder is gone, no such folder",
                    () -> assertThat(byId(page, "status-next-seedFolder").attr("data-status"))
                            .isEqualTo(NO_SUCH_FOLDER));
        }
    }

    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("The profile's seed folder says not chosen while its field is empty, and found once a folder is chosen into it")
    void theProfilesSeedFolderSaysWhatItsFieldHolds() throws IOException {
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(
                Files.createDirectories(folder.resolve("corpus")), Files.createDirectories(folder.resolve("work"))))) {
            PanelBrowser browser = screens.browser();
            Page empty = browser.go("/profile").page();
            browser.go("/folders?for=profile-seedFolder&path="
                    + java.net.URLEncoder.encode(seeds.toString(), StandardCharsets.UTF_8));
            Page chosen = browser.press("use-folder", "use", Map.of()).page();

            claim("while the field is empty it says not chosen",
                    () -> assertThat(byId(empty, "status-profile-seedFolder").attr("data-status"))
                            .isEqualTo(NOT_CHOSEN));
            claim("and once a folder is chosen into it, found",
                    () -> assertThat(byId(chosen, "status-profile-seedFolder").attr("data-status")).isEqualTo(FOUND));
        }
    }

    /** A code set's folder is relative to the corpus root, so it is that folder the status is of. */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("A code set's new folder says not chosen while empty and found once chosen, read from the corpus root")
    void aCodeSetsNewFolderIsReadFromTheCorpusRoot() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpusRoot.resolve("tools"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot,
                Files.createDirectories(folder.resolve("work"))))) {
            PanelBrowser browser = screens.browser();
            Page empty = browser.go("/code-sets").page();
            browser.go("/folders?for=new-folder&path=tools");
            Page chosen = browser.press("use-folder", "use", Map.of()).page();

            claim("while the field is empty it says not chosen",
                    () -> assertThat(byId(empty, "status-new-folder").attr("data-status")).isEqualTo(NOT_CHOSEN));
            claim("and once a folder under the corpus root is chosen into it, found",
                    () -> assertThat(byId(chosen, "status-new-folder").attr("data-status")).isEqualTo(FOUND));
        }
    }

    /**
     * Browse then Back leaves what was typed in the field. Each value names a folder that is on the disk, so the field
     * says no such folder for where it lies, and not because nothing is there.
     */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("A code set's new folder that climbs out of the corpus root says no such folder, though a folder is there")
    void aCodeSetsNewFolderThatClimbsOutSaysNoSuchFolder() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpusRoot.resolve("tools"));
        Path outside = Files.createDirectories(folder.resolve("outside"));
        List<String> climbing = List.of("../outside", "tools/../../outside", outside.toString(),
                outside.toString().replace('\\', '/'));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot,
                Files.createDirectories(folder.resolve("work"))))) {
            PanelBrowser browser = screens.browser();
            for (String typed : climbing) {
                browser.go("/code-sets").press("code-sets", "browse-new-folder", Map.of("new-folder", typed));
                Page back = browser.go("/code-sets").page();

                claim("'" + typed + "' stands in the field, which says no such folder",
                        () -> assertThat(List.of(form(back, "code-sets").select("[name=new-folder]").val(),
                                        byId(back, "status-new-folder").attr("data-status")))
                                .containsExactly(typed, NO_SUCH_FOLDER));
            }
        }
    }

    /**
     * Named by {@code src}, which lies under the current directory the tests run in: a field read anywhere but under a
     * corpus root would say found.
     */
    @Test
    @Story("The operator names the code sets")
    @DisplayName("With no corpus root named, a code set's new folder says not chosen while empty, and no such folder once a folder stands in its field")
    void aCodeSetsNewFolderSaysNoSuchFolderWithNoCorpusRootNamed() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        LaunchValues noCorpusRoot = new LaunchValues("", workingDirectory.toString(), "", "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, noCorpusRoot)) {
            PanelBrowser browser = screens.browser();
            Page empty = browser.go("/code-sets").page();
            browser.press("code-sets", "browse-new-folder", Map.of("new-folder", "src"));
            Page held = browser.go("/code-sets").page();

            claim("while the field is empty it says not chosen",
                    () -> assertThat(byId(empty, "status-new-folder").attr("data-status")).isEqualTo(NOT_CHOSEN));
            claim("and with a folder in it, there being no corpus root to read it under, no such folder",
                    () -> assertThat(List.of(form(held, "code-sets").select("[name=new-folder]").val(),
                                    byId(held, "status-new-folder").attr("data-status")))
                            .containsExactly("src", NO_SUCH_FOLDER));
        }
    }

    /**
     * Each of the two fields, pressed by Browse and by Check, in a server of its own, the other field naming a folder
     * that is there, so that the status and the row are paired with the field that holds the path.
     */
    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("A corpus root or a working directory the system cannot hold says no such folder, its row of the preflight is red, and every page still answers")
    void aFolderTheSystemCannotHoldSaysNoSuchFolderAndEveryPageAnswers() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        for (String field : List.of(CORPUS_ROOT, WORKING_DIRECTORY)) {
            for (String button : List.of("check", "browse-" + field)) {
                Path own = Files.createDirectories(folder.resolve("own-" + button + "-" + field));
                try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                    PanelBrowser browser = screens.browser();
                    pressed(browser, "launch", button, Map.of(field, A_PATH_NO_SYSTEM_CAN_HOLD));
                    Map<String, String> answers = new LinkedHashMap<>();
                    for (String path : ANSWER_OF_EACH_PAGE.keySet()) {
                        answers.put(path, answerAt(browser, path));
                    }

                    claim("with " + field + " holding it by " + button + ", every page answers",
                            () -> assertThat(answers).containsExactlyEntriesOf(ANSWER_OF_EACH_PAGE));

                    Page launch = browser.go("/").page();
                    boolean corpusRootHoldsIt = field.equals(CORPUS_ROOT);

                    claim("that field says no such folder, and the other found",
                            () -> assertThat(List.of(byId(launch, "status-" + CORPUS_ROOT).attr("data-status"),
                                            byId(launch, "status-" + WORKING_DIRECTORY).attr("data-status")))
                                    .containsExactly(corpusRootHoldsIt ? NO_SUCH_FOLDER : FOUND,
                                            corpusRootHoldsIt ? FOUND : NO_SUCH_FOLDER));
                    claim("and its row of the preflight is red, and the other's green",
                            () -> assertThat(List.of(rowOfThePreflight(launch, "corpus-root"),
                                            rowOfThePreflight(launch, "working-directory")))
                                    .containsExactly(corpusRootHoldsIt ? "bad" : "ok", corpusRootHoldsIt ? "ok" : "bad"));
                }
            }
        }
    }

    /**
     * Label names no corpus root to its child and refuses one all the same, since the file of launch values would keep
     * it. The launch values given name folders that are there, so nothing else stands in the way of a start.
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run and Label refuse a corpus root, a working directory or a deliverable root the system cannot hold, in the one line naming which, start nothing and keep nothing")
    void runAndLabelRefuseAFolderTheSystemCannotHold() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Map<String, String> wordsOfEachField = Map.of(CORPUS_ROOT, "the corpus root", WORKING_DIRECTORY,
                "the working directory", DELIVERABLE_ROOT, "the output folder");
        Map<String, String> captionOfEachButton = Map.of("run", "Run", "label", "Label");
        for (String field : wordsOfEachField.keySet()) {
            for (String button : captionOfEachButton.keySet()) {
                Path own = Files.createDirectories(folder.resolve("own-" + button + "-" + field));
                try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                    PanelBrowser browser = screens.browser();
                    Path launchValues = LaunchValues.fileIn(own.resolve("tmp"), System.getProperty("user.name"));
                    byte[] keptBefore = Files.readAllBytes(launchValues);
                    String answered = pressed(browser, "launch", button, Map.of(field, A_PATH_NO_SYSTEM_CAN_HOLD));

                    claim(button + " with " + field + " holding it is answered",
                            () -> assertThat(answered).isEqualTo(ANSWERED));

                    Page landed = browser.page();
                    Page invocation = browser.go("/invocation").page();
                    byte[] keptAfter = Files.readAllBytes(launchValues);

                    claim(button + " lands back on the launch page with the one line naming " + field,
                            () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                                    .containsExactly("/", captionOfEachButton.get(button) + " is not started: "
                                            + wordsOfEachField.get(field) + " is no path this system can hold"));
                    claim("no child was started",
                            () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
                    claim("and the file of launch values is as it was",
                            () -> assertThat(keptAfter).isEqualTo(keptBefore));
                }
            }
        }
    }

    /**
     * The working directory is the child's current directory (ADR-278 §1), so no child can be started in one that is no
     * folder, and the control panel creates none (ADR-295 §1). The launch values given name folders that are there, so
     * the value posted is all that stands in the way of a start.
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run and Label refuse a working directory that is not there, or a file in its place, in the one line naming it, start nothing, keep nothing and create nothing")
    void runAndLabelRefuseAWorkingDirectoryThatIsNoFolder() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path notThere = folder.resolve("not-there");
        Path aFile = Files.writeString(folder.resolve("a-file.txt"), "not a folder", StandardCharsets.UTF_8);
        Map<String, String> captionOfEachButton = Map.of("run", "Run", "label", "Label");
        for (Path named : List.of(notThere, aFile)) {
            for (String button : captionOfEachButton.keySet()) {
                Path own = Files.createDirectories(folder.resolve("own-" + button + "-" + named.getFileName()));
                try (ScreenServer screens = ScreenServer.start(own, ScreenServer.values(corpusRoot, workingDirectory))) {
                    PanelBrowser browser = screens.browser();
                    Path launchValues = LaunchValues.fileIn(own.resolve("tmp"), System.getProperty("user.name"));
                    byte[] keptBefore = Files.readAllBytes(launchValues);
                    Page landed = browser.press("launch", button, Map.of(WORKING_DIRECTORY, named.toString())).page();
                    Page invocation = browser.go("/invocation").page();
                    byte[] keptAfter = Files.readAllBytes(launchValues);

                    claim(button + " on " + named.getFileName() + " lands back on the launch page with the one line naming it",
                            () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                                    .containsExactly("/", captionOfEachButton.get(button)
                                            + " is not started: the working directory " + named
                                            + " is no folder that is there"));
                    claim("no child was started",
                            () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
                    claim("the file of launch values is as it was",
                            () -> assertThat(keptAfter).isEqualTo(keptBefore));
                    claim("and nothing was created in its place",
                            () -> {
                                assertThat(notThere).doesNotExist();
                                assertThat(aFile).isRegularFile();
                            });
                }
            }
        }
    }

    /** The rendering lies under the deliverable root, so Publish stays offered with the working directory not there. */
    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("Publish refuses a working directory that is not there, in the one line saying Publish, back on the invocation page, starts nothing and keeps nothing")
    void publishRefusesAWorkingDirectoryThatIsNoFolder() throws IOException {
        Path deliverableRoot = Files.createDirectories(folder.resolve("deliverables"));
        Files.createDirectories(deliverableRoot.resolve("confluence"));
        Files.writeString(deliverableRoot.resolve("confluence").resolve("pages.json"), "{}", StandardCharsets.UTF_8);
        LaunchValues withARendering = new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("work")).toString(), deliverableRoot.toString(), "", "", "", "", "");
        Path notThere = folder.resolve("not-there");
        try (ScreenServer screens = ScreenServer.start(folder, withARendering)) {
            PanelBrowser browser = screens.browser();
            byte[] keptBefore = Files.readAllBytes(launchValues());
            pressed(browser, "launch", "check", Map.of(WORKING_DIRECTORY, notThere.toString()));
            Page landed = browser.go("/invocation").press("publish", "publish", Map.of()).page();

            claimTheStartWasRefusedInTheLine("Publish is not started: the working directory " + notThere
                    + " is no folder that is there", "/invocation", landed, "none", browser, keptBefore);
            claim("and nothing was created there", () -> assertThat(notThere).doesNotExist());
        }
    }

    /** The field's hint and the page's status tell the operator the same thing of a folder that is not there. */
    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("The working directory's hint asks for the folder to be created first, and says nothing of its being created for them")
    void theWorkingDirectorysHintAsksForItToBeCreatedFirst() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().page();
            String tiedTo = form(page, "launch").select("[name='" + WORKING_DIRECTORY + "']").attr("aria-describedby");

            claim("the hint tied to the field is the one the record gives",
                    () -> assertThat(byId(page, tiedTo).text()).isEqualTo(
                            "Where the database, the reports and profile.yaml live. Create it if it is not there yet."));
        }
    }

    /**
     * Publish is offered once a rendering is drawn, here under the deliverable root, which stays a path the system holds:
     * the check on the corpus root stands though the publish reads no corpus root.
     */
    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("Publish refuses a corpus root the system cannot hold, in the one line saying Publish, back on the invocation page, starts nothing and keeps nothing")
    void publishRefusesACorpusRootTheSystemCannotHold() throws IOException {
        Path deliverableRoot = Files.createDirectories(folder.resolve("deliverables"));
        Files.createDirectories(deliverableRoot.resolve("confluence"));
        Files.writeString(deliverableRoot.resolve("confluence").resolve("pages.json"), "{}", StandardCharsets.UTF_8);
        LaunchValues withARendering = new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("work")).toString(), deliverableRoot.toString(), "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, withARendering)) {
            PanelBrowser browser = screens.browser();
            byte[] keptBefore = Files.readAllBytes(launchValues());
            pressed(browser, "launch", "check", Map.of(CORPUS_ROOT, A_PATH_NO_SYSTEM_CAN_HOLD));
            Page landed = browser.go("/invocation").press("publish", "publish", Map.of()).page();

            claimTheStartWasRefused("Publish", "/invocation", landed, "none", browser, keptBefore);
        }
    }

    /** A second child would end with another code, so an ended page shows the first child's ending alone. */
    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Save, then Run refuses a corpus root the system cannot hold, in the one line saying Run, back on the next act's page, starts nothing and keeps nothing")
    void saveThenRunRefusesACorpusRootTheSystemCannotHold() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = wanting("""
                invocation=run
                wants=seedFolder
                answer=false
                """);
        String seeds = Files.createDirectories(folder.resolve("seeds")).toString();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.afterARunHasEnded();
            byte[] keptBefore = Files.readAllBytes(launchValues());
            ScreenServer.childWill(workingDirectory, A_SECOND_CHILD);
            pressed(browser.go("/"), "launch", "check", Map.of(CORPUS_ROOT, A_PATH_NO_SYSTEM_CAN_HOLD));
            Page landed = browser.go("/next").press("next", "save-then-run",
                    Map.of("seedFolder", seeds, "seedFolder.provenance", "a reason")).page();

            claimTheStartWasRefused("Run", "/next", landed, "ended", browser, keptBefore);
        }
    }

    /** {@code label --auto} is offered where the next act wants answers, and its name on the line is Label's. */
    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("Having the local model answer refuses a corpus root the system cannot hold, in the one line saying Label, back on the next act's page, starts nothing and keeps nothing")
    void labelAutoRefusesACorpusRootTheSystemCannotHold() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = wanting("""
                invocation=label
                wants=
                answer=true
                """);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.afterARunHasEnded();
            byte[] keptBefore = Files.readAllBytes(launchValues());
            ScreenServer.childWill(workingDirectory, A_SECOND_CHILD);
            pressed(browser.go("/"), "launch", "check", Map.of(CORPUS_ROOT, A_PATH_NO_SYSTEM_CAN_HOLD));
            Page landed = browser.go("/next").press("label-auto", "label-auto", Map.of()).page();

            claimTheStartWasRefused("Label", "/next", landed, "ended", browser, keptBefore);
        }
    }

    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("Save, then Label refuses a corpus root the system cannot hold, in the one line saying Label, back on the questions, starts nothing and keeps nothing")
    void saveThenLabelRefusesACorpusRootTheSystemCannotHold() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), ONE_QUESTION, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            byte[] keptBefore = Files.readAllBytes(launchValues());
            pressed(browser, "launch", "check", Map.of(CORPUS_ROOT, A_PATH_NO_SYSTEM_CAN_HOLD));
            Page landed = browser.go("/labels").press("labels", "save-then-label", Map.of("relevant-1", "true")).page();

            claimTheStartWasRefused("Label", "/labels", landed, "none", browser, keptBefore);
        }
    }

    /** {@code landed} shows the one line naming {@code start}, the child is as it was, and the launch values' file too. */
    private void claimTheStartWasRefused(String start, String landsAt, Page landed, String childAsItWas,
            PanelBrowser browser, byte[] keptBefore) throws IOException {
        claimTheStartWasRefusedInTheLine(start + " is not started: the corpus root is no path this system can hold",
                landsAt, landed, childAsItWas, browser, keptBefore);
    }

    /** {@code landed} shows {@code line}, the child is as it was, and the launch values' file too. */
    private void claimTheStartWasRefusedInTheLine(String line, String landsAt, Page landed, String childAsItWas,
            PanelBrowser browser, byte[] keptBefore) throws IOException {
        Page invocation = browser.go("/invocation").page();
        byte[] keptAfter = Files.readAllBytes(launchValues());

        claim("the start lands where its refusals land, with the one line",
                () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                        .containsExactly(landsAt, line));
        claim("no child was started",
                () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo(childAsItWas));
        claim("and the file of launch values is as it was", () -> assertThat(keptAfter).isEqualTo(keptBefore));
    }

    /** A working directory where a child ending with 0 leaves {@code nextInvocation}. */
    private Path wanting(String nextInvocation) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), nextInvocation, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    private Path launchValues() {
        return LaunchValues.fileIn(folder.resolve("tmp"), System.getProperty("user.name"));
    }

    /** A working directory that cannot be held is one none is named by: each save says so, as it does of none named. */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("With a working directory the system cannot hold, a save on the launch page and on the profile page is answered with one line, and writes nothing")
    void aSaveWithAWorkingDirectoryTheSystemCannotHoldIsAnsweredWithOneLine() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        String seeds = Files.createDirectories(folder.resolve("seeds")).toString();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            String answered = pressed(browser, "launch", "check", Map.of(WORKING_DIRECTORY, A_PATH_NO_SYSTEM_CAN_HOLD));

            claim("the launch page answers, with that held", () -> assertThat(answered).isEqualTo(ANSWERED));

            Page afterTheLaunchPagesSave = browser.press("seed-folder", "save",
                    Map.of("seedFolder", seeds, "provenance", "a reason")).page();
            Page afterTheProfilePagesSave = browser.go("/profile").press("seedFolder", "save",
                    Map.of("value", seeds, "provenance", "a reason")).page();

            claim("each save lands back on its page, with its one line",
                    () -> assertThat(List.of(afterTheLaunchPagesSave, afterTheProfilePagesSave))
                            .extracting(page -> page.address().getPath(), page -> byId(page, "outcome").text().isBlank())
                            .containsExactly(tuple("/", false), tuple("/profile", false)));
            claim("and no profile was written",
                    () -> assertThat(Files.exists(workingDirectory.resolve(ProfileEdit.FILE_NAME))).isFalse());
        }
    }

    /** Each item is read as a pair: the number on the attribute and the GiB the same item shows, so neither can drift. */
    @Test
    @Story("The operator sees whether a folder will do")
    @DisplayName("The free space list shows GiB, worked out from the bytes it keeps on each item, and the free space tile shows the working directory's")
    void theFreeSpaceListShowsGibFromTheBytesItKeeps() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path deliverableRoot = Files.createDirectories(folder.resolve("deliverables"));
        LaunchValues values = new LaunchValues("", workingDirectory.toString(), deliverableRoot.toString(), "", "", "",
                "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            Page page = screens.browser().page();
            List<Element> items = byId(page, "free-space").select("[data-folder]");
            Element tile = byId(page, "tiles").select("[data-tile=free-space]").first();

            claim("the working directory's and the deliverable root's items are both there",
                    () -> assertThat(items).hasSize(2));
            claim("each item's words give the GiB of the bytes on its own attribute, to one decimal, and no count of"
                            + " bytes",
                    () -> assertThat(items).allSatisfy(item -> assertThat(item.text())
                            .contains(gib(item.attr("data-usable-bytes")))
                            .doesNotContain(item.attr("data-usable-bytes"))
                            .doesNotContain("bytes")));
            claim("and the tile gives the working directory's",
                    () -> assertThat(tile.text()).contains(gib(items.getFirst().attr("data-usable-bytes"))));
        }
    }

    /** The statuses of the corpus root, the working directory, the deliverable root and the seed folder, in that order. */
    private static List<String> launchStatuses(Page page) {
        return List.of("vespera.corpus-root", "vespera.working-dir", "vespera.deliverable-root", "seedFolder").stream()
                .map(field -> byId(page, "status-" + field).attr("data-status"))
                .toList();
    }

    /** Presses a button, and gives the status of the page it lands on, or {@link #NO_REPLY}. */
    private static String pressed(PanelBrowser browser, String form, String button, Map<String, String> fields) {
        try {
            return String.valueOf(browser.press(form, button, fields).page().status());
        } catch (Exception noReply) {
            return NO_REPLY;
        }
    }

    /** Goes to {@code path}, and gives the status of the page it lands on, or {@link #NO_REPLY}. */
    private static String answerAt(PanelBrowser browser, String path) {
        try {
            return String.valueOf(browser.go(path).page().status());
        } catch (Exception noReply) {
            return NO_REPLY;
        }
    }

    private static String rowOfThePreflight(Page page, String row) {
        return byId(page, "preflight").select("[data-row=" + row + "]").attr("data-state");
    }

    private static Map<String, String> answerOfEachPage() {
        Map<String, String> answers = new LinkedHashMap<>();
        for (String page : List.of("/", "/invocation", "/next", "/labels", "/code-sets", "/profile",
                "/folders?for=" + CORPUS_ROOT, "/folders?for=" + WORKING_DIRECTORY, "/folders?for=new-folder")) {
            answers.put(page, ANSWERED);
        }
        answers.put("/report/progress.html", "404");
        return answers;
    }

    private static String gib(String usableBytes) {
        return String.format(Locale.ROOT, "%.1f GiB", Long.parseLong(usableBytes) / BYTES_IN_A_GIB);
    }
}
