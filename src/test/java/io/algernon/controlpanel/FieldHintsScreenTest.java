package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every field the operator fills carries a real example and a one-line hint in plain words (ADR-331 §8), each hint of a
 * profile key being what the README's table of values says of it. The words are no test's but where a record decides
 * them: a test reads that the example and the hint are there, tied to their field, and the hint of a key to its row.
 *
 * <p>Every field stands on the home page and its drawer since ADR-340 §2 and §3, which is asked for in the state it
 * shows the most fields: after a child ended with 0 beside a file wanting every key, so that the next step's box holds
 * a field of each, with one code set named, one question, and a folder in the corpus for the tree to list. A checkbox
 * carries its label and no example (ADR-340 §7).
 *
 * <p>A field whose value in use the page knows shows that value in place of an example, after {@code in use: }
 * (ADR-334 §1, §2): the Output folder always, the working directory being named here. Such a placeholder is no example,
 * so it may hold a folder the page was given.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class FieldHintsScreenTest {

    /** The operator's guide, at the root of the repository the tests run in. */
    private static final Path README = Path.of("README.md");

    /** The header row of the README's table of values. */
    private static final String TABLE_HEADER = "| The value | Read | Which reports |";

    /** What every example begins with, so that no example can be read as a value the page holds. */
    private static final String EXAMPLE = "e.g. ";

    /** What a placeholder showing the value in use begins with, in place of an example. */
    private static final String IN_USE = "in use: ";

    /** The fields of the home page and its drawer, one of each kind: the launch values, then one of each other form. */
    private static final List<String> THE_FIELDS_OF_THE_HOME_PAGE = List.of("vespera.corpus-root",
            "vespera.working-dir", "vespera.deliverable-root", "vespera.docling.image", "vespera.docling.base-url",
            "spring.ai.ollama.base-url", "vespera.label.model", "seedFolder", "seedFolder.provenance",
            "new-code-set-folder", "codeSetFolders.provenance", "new-not-relevant-folder", "code-set-name-1",
            "foldersNotWalked.provenance");

    /** The folder under which the pages below were asked for, once for the class. */
    @TempDir
    static Path folderOfThePages;

    /** Each page in the state it shows its most fields: a server and a child's run, the same for every claim on them. */
    private static List<Page> pagesWithTheirFields;

    @TempDir
    Path folder;

    @BeforeAll
    static void askForThePagesWithTheirFields() throws IOException, InterruptedException {
        pagesWithTheirFields = thePagesWithTheirFields(folderOfThePages);
    }

    /** Each page's fields, which is what the claims below are about: a page that holds none could not fail them. */
    @Test
    @Story("The operator is told what each field wants")
    @DisplayName("Every field of every page has an example and a one-line hint tied to it, and a label, or a name for a choice of its own")
    void everyFieldOfEveryPageHasAnExampleAndAHint() {
        List<String> faults = new ArrayList<>();
        List<String> read = new ArrayList<>();
        for (Page page : pagesWithTheirFields) {
            for (Element control : fieldsOf(page)) {
                String name = page.address().getPath() + " " + control.attr("name");
                read.add(name);
                faults.addAll(whatIsMissing(page, control, name));
            }
        }

        claim("the fields of the launch values, the run settings, the next step, the code set folders, the folders not"
                        + " relevant and the tree were among those read",
                () -> assertThat(read).containsAll(THE_FIELDS_OF_THE_HOME_PAGE.stream().map(name -> "/ " + name).toList()));
        claim("and each field has what it should: an example beginning e.g., or the value in use, which a choice has no"
                        + " room for, a hint it is tied to, and a label",
                () -> assertThat(faults).isEmpty());
    }

    @Test
    @Story("The operator is told what each field wants")
    @DisplayName("No example holds a folder the page was given: an example is the same whatever the operator's own folders are")
    void noExampleHoldsAFolderThePageWasGiven() {
        List<String> examples = pagesWithTheirFields.stream()
                .flatMap(page -> fieldsOf(page).stream())
                .map(control -> control.attr("placeholder"))
                .filter(example -> example.startsWith(EXAMPLE))
                .toList();

        claim("the pages hold examples, so the next claim is about something",
                () -> assertThat(examples).isNotEmpty());
        claim("and none holds the test's own folder, which every folder the pages were given lies under",
                () -> assertThat(examples).allSatisfy(
                        example -> assertThat(example).doesNotContain(folderOfThePages.toString())));
    }

    /**
     * The key's hint is paired with its row: a table of hints shifted by one would hold every key a hint and still fail.
     * A hint may start with a capital and end with a stop, which is the page's sentence around the guide's cell.
     */
    @Test
    @Story("The operator is told what each field wants")
    @DisplayName("Every profile value's hint, in the run settings and in the next step, is what the operator's guide says that value is read from")
    void everyProfileKeyHasTheHintTheReadmesTableGivesIt() throws IOException {
        Map<String, String> table = readOffTheReadmesTable();
        Page home = pageAt("/");
        Map<String, String> onTheProfile = new TreeMap<>();
        Map<String, String> onTheNext = new TreeMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            onTheProfile.put(key.key(), plain(byId(home, "hint-" + key.key()).text()));
            onTheNext.put(key.key(), plain(byId(home, "next-hint-" + key.key()).text()));
        }
        Map<String, String> expected = new TreeMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            expected.put(key.key(), table.get(key.key()));
        }

        claim("the guide's table has a row for every profile value",
                () -> assertThat(table.keySet()).containsAll(
                        Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList()));
        claim("in the run settings each value's hint is its own row's words",
                () -> assertThat(onTheProfile).isEqualTo(expected));
        claim("and in the next step too",
                () -> assertThat(onTheNext).isEqualTo(expected));
    }

    @Test
    @Story("The operator is told what each field wants")
    @DisplayName("The deliverable root is called the output folder in the drawer's folders")
    void theDeliverableRootIsCalledTheOutputFolder() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().page();
            String id = form(page, "launch-values").select("[name='vespera.deliverable-root']").attr("id");

            claim("its label reads Output folder, the key beside it apart",
                    () -> assertThat(page.document().select("label[for='" + id + "']").first().ownText())
                            .isEqualTo("Output folder"));
            claim("and its hint does not call it by the project's own name for it",
                    () -> assertThat(byId(page, form(page, "launch-values").select("[name='vespera.deliverable-root']")
                            .attr("aria-describedby")).text().toLowerCase(Locale.ROOT))
                            .doesNotContain("deliverable root"));
        }
    }

    /** A hint is tied to its field by an id, and a label by another, so an id written twice ties it to the wrong one. */
    @Test
    @Story("The operator is told what each field wants")
    @DisplayName("No page gives one id to two elements")
    void noPageGivesOneIdToTwoElements() {
        Map<String, List<String>> repeated = new TreeMap<>();
        for (Page page : pagesWithTheirFields) {
            Set<String> seen = new HashSet<>();
            List<String> again = page.document().select("[id]").eachAttr("id").stream()
                    .filter(id -> !seen.add(id))
                    .collect(Collectors.toList());
            if (!again.isEmpty()) {
                repeated.put(page.address().getPath(), again);
            }
        }

        claim("every id on every page is written once", () -> assertThat(repeated).isEmpty());
    }

    /** The one of {@link #pagesWithTheirFields} at {@code path}. */
    private static Page pageAt(String path) {
        return pagesWithTheirFields.stream()
                .filter(page -> page.address().getPath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no page was asked for at " + path));
    }

    private static List<String> whatIsMissing(Page page, Element control, String name) {
        List<String> missing = new ArrayList<>();
        String placeholder = control.attr("placeholder");
        if (control.nameIs("input") && !placeholder.startsWith(EXAMPLE) && !placeholder.startsWith(IN_USE)) {
            missing.add(name + " has no example beginning '" + EXAMPLE + "', nor the value in use: '" + placeholder + "'");
        }
        if (placeholder.equals(EXAMPLE) || placeholder.equals(IN_USE)) {
            missing.add(name + "'s placeholder says nothing");
        }
        String describedBy = control.attr("aria-describedby");
        Element hint = describedBy.isEmpty() ? null : page.document().getElementById(describedBy);
        if (hint == null || hint.text().isBlank()) {
            missing.add(name + " is tied to no hint: '" + describedBy + "'");
        }
        String id = control.attr("id");
        boolean labelled = !control.attr("aria-label").isBlank()
                || (!id.isEmpty() && !page.document().select("label[for='" + id + "']").isEmpty());
        if (!labelled) {
            missing.add(name + " has no label");
        }
        return missing;
    }

    /** The fields of the page's forms the operator sees: text, number and select, buttons and hidden fields apart. */
    private static List<Element> fieldsOf(Page page) {
        return page.document().select("form input:not([type=hidden]):not([type=submit]):not([type=checkbox]), form select");
    }

    private static List<Page> thePagesWithTheirFields(Path folder) throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpusRoot.resolve("tools"));
        Path workingDirectory = wantingEveryKey(folder);
        Files.writeString(workingDirectory.resolve(CodeSetRenames.FILE_NAME), """
                ---
                - folder: "tools/build"
                  name: "Build tools"
                """, StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), """
                ---
                generatedUnderRun: "run-of-the-sample"
                generatedUnderEmbedder: "embedder@digest"
                generatedUnderSeedSet: "/seeds"
                documents:
                - path: "sample/one.pdf"
                  score: 0.83
                  band: 2
                  closestSeed: "seed/one.docx"
                  relevant: null
                """, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            return List.of(screens.afterARunHasEnded().go("/").page());
        }
    }

    /** A working directory where a child ending with 0 leaves a next act wanting every key. */
    private static Path wantingEveryKey(Path folder) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=%s
                answer=false
                arrangement=0123456789ab
                """.formatted(Arrays.stream(ProfileKey.values()).map(ProfileKey::key).collect(Collectors.joining(" "))),
                StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    /** Each key of the README's table and what its second cell says it is read from, its code quotes taken out. */
    private static Map<String, String> readOffTheReadmesTable() throws IOException {
        List<String> lines = Files.readAllLines(README, StandardCharsets.UTF_8);
        int header = lines.indexOf(TABLE_HEADER);
        claim("the guide holds its table of values, headed by the value, what to read and which reports",
                () -> assertThat(header).isNotNegative());
        Map<String, String> rows = new TreeMap<>();
        for (String line : lines.subList(header + 2, lines.size())) {
            if (!line.startsWith("|")) {
                break;
            }
            String[] cells = line.split("\\|");
            rows.put(cells[1].strip().replace("`", ""), plain(cells[2].replace("`", "")));
        }
        return rows;
    }

    /** A hint as the guide's cell gives it: no stop at its end, and its first letter small. */
    private static String plain(String hint) {
        String stripped = hint.strip();
        String withoutAStop = stripped.endsWith(".") ? stripped.substring(0, stripped.length() - 1) : stripped;
        return withoutAStop.isEmpty() ? withoutAStop
                : withoutAStop.substring(0, 1).toLowerCase(Locale.ROOT) + withoutAStop.substring(1);
    }
}
