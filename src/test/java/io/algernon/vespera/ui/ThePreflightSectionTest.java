package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidebar's Preflight section (ADR-440): a grid {@code preflight-list} of rows, each a name in its first column, a
 * mark in its second and words in its third, for the corpus root and the working directory, each one's holder said in
 * words with its line whole as the caption, one row where one process holds both, and Docling's image where the
 * header's check found it running another than the Services field's; free space said in each folder's words alone, and
 * nothing of Docling's or Ollama's answers, nor a Check.
 *
 * <p>A holder is a live process of the test's own, named by a holder line as the jar writes one, in the working
 * directory's {@code vespera.lock} and in the corpus root's lock file in the state's folder of corpus-root locks; the
 * window reads both each second. The check is a {@link RecordingDockerControl}'s, whose answer the test sets.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("924")
@Link(name = "ADR-440", url = Adr.THE_PREFLIGHT_IS_THE_FOLDERS_AND_THEIR_HOLDERS_A_HOLDER_SAID_ONCE_IN_WORDS, type = "adr")
class ThePreflightSectionTest {

    private static final long BOUND_SECONDS = 20;

    /** The folder under the test's own that a window's state keeps its files and its folder of corpus-root locks in. */
    private static final String STATE = "state";

    /** How long a holder runs before its lines are written, so that both start after it did, the slack included. */
    private static final long BEFORE_THE_LINES_MILLIS = 1100;

    /** The words of a folder found on a drive whose free space can be read (ADR-331 §6). */
    private static final String FOUND_WITH_FREE_SPACE = "Found, \\d+\\.\\d GiB free";

    /** The image the Services field holds, which Run would use. */
    private static final String NAMED_IMAGE = "registry.example:5000/a-converter:1.2-gpu";

    /** Another image, which the test's Docling says it runs. */
    private static final String ANOTHER_IMAGE = "registry.example:5000/a-converter:1.1-cpu";

    private static final String IMAGE_CAPTION =
            "Extraction refuses a Docling that runs another image than the one Run would use.";

    /** What one row of the grid reads: its three texts, its mark's class and the caption of its words. */
    private record Row(String id, String name, String mark, String markClass, String words, String caption,
            boolean inItsColumns) {
    }

    @TempDir
    Path folder;

    @Test
    @Story("The Preflight section is a row for each folder and holder")
    @DisplayName("Each folder is a row of its name, a mark and its words, free space said in them alone, with no line of Docling's or Ollama's answers and no Check")
    void eachFolderIsARowOfANameAMarkAndWordsAndFreeSpaceIsSaidOnce() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        List<Row> found;
        String everyText;
        boolean holdsACheck;
        try (WindowForTests window = shown(corpus, working, new RecordingDockerControl())) {
            found = window.awaiting(ThePreflightSectionTest::rows, read -> read.size() == 2);
            everyText = window.read(root -> WindowForTests.textUnder(root, "preflight-list"));
            holdsACheck = window.read(root -> HeadlessToolkit.nodeWithId(root, "check").isPresent());
        }
        List<Row> missing;
        try (WindowForTests window = shown(folder.resolve("another-state"), folder.resolve("no-corpus-here"), working,
                new RecordingDockerControl())) {
            missing = window.awaiting(ThePreflightSectionTest::rows,
                    read -> !read.isEmpty() && read.getFirst().mark().equals("KO"));
        }

        claim("both folders found and nothing holding them, the grid holds two rows, the corpus root's then the"
                        + " working directory's, each name, mark and words in its own column of one line",
                () -> assertThat(found).extracting(Row::id, Row::name, Row::mark, Row::markClass, Row::inItsColumns)
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("corpus-root", "Corpus root", "OK", "ok", true),
                                org.assertj.core.groups.Tuple.tuple("working-directory", "Working directory", "OK", "ok",
                                        true)));
        claim("each folder's words say it is found and its drive's free space",
                () -> assertThat(found).extracting(Row::words).allMatch(words -> words.matches(FOUND_WITH_FREE_SPACE)));
        claim("no other text of the section says free space, the converter, Docling or Ollama, or what was not checked",
                () -> assertThat(everyText).doesNotContain("Free space", "Converter", "Docling", "Ollama",
                        "not checked", "held by"));
        claim("and the window holds no Check", () -> assertThat(holdsACheck).isFalse());
        claim("a corpus root that is not there reads KO, in the ending red's class, and says so",
                () -> assertThat(missing.getFirst()).extracting(Row::id, Row::mark, Row::markClass, Row::words)
                        .containsExactly("corpus-root", "KO", "ko", "No such folder"));
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("One process holding both folders is said once, Both held by, in words, its two lines the caption")
    void oneProcessHoldingBothFoldersIsSaidOnceInWordsItsTwoLinesTheCaption() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        Process holder = ChildForTests.builder("sleep").start();
        try {
            Thread.sleep(BEFORE_THE_LINES_MILLIS);
            OffsetDateTime started = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            String command = " command=run " + corpus + " --db-dir=" + working;
            // the corpus root's lock is taken a second before the working directory's: each line has its own start
            String rootLine = "pid=" + holder.pid() + " started=" + iso(started.minusSeconds(1)) + command
                    + " root=" + corpus + " working-directory=" + working;
            String workingLine = "pid=" + holder.pid() + " started=" + iso(started) + command;
            writeLine(rootLockOf(corpus), rootLine);
            writeLine(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), workingLine);
            List<Row> read;
            try (WindowForTests window = shown(corpus, working, new RecordingDockerControl())) {
                read = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 3);
            }

            claim("the grid holds the corpus root's row, the working directory's, then one holder's row, and no holder"
                            + " row of either folder",
                    () -> assertThat(read).extracting(Row::id)
                            .containsExactly("corpus-root", "working-directory", "holder"));
            claim("the holder's row reads Both held by, KO in the ending red's class, the command, the pid and the"
                            + " time it started, in its own columns",
                    () -> assertThat(read.get(2)).extracting(Row::name, Row::mark, Row::markClass, Row::words,
                            Row::inItsColumns).containsExactly("Both held by", "KO", "ko",
                            "vespera run (pid " + holder.pid() + ") since " + hhmm(started), true));
            claim("and its caption is the corpus root's line whole, a line break, then the working directory's",
                    () -> assertThat(read.get(2).caption()).isEqualTo(rootLine + "\n" + workingLine));
        } finally {
            holder.destroyForcibly().waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("Two holders are each said beneath their folder, in words, each one's line whole its caption")
    void twoHoldersAreEachSaidBeneathTheirFolderEachLineItsCaption() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        Process running = ChildForTests.builder("sleep").start();
        Process writing = ChildForTests.builder("sleep").start();
        try {
            Thread.sleep(BEFORE_THE_LINES_MILLIS);
            OffsetDateTime started = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            String rootLine = "pid=" + running.pid() + " started=" + iso(started) + " command=run " + corpus
                    + " --db-dir=" + folder.resolve("elsewhere") + " root=" + corpus + " working-directory="
                    + folder.resolve("elsewhere");
            String workingLine = "pid=" + writing.pid() + " started=" + iso(started)
                    + " command=control-panel write profile.yaml";
            writeLine(rootLockOf(corpus), rootLine);
            writeLine(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), workingLine);
            List<Row> read;
            try (WindowForTests window = shown(corpus, working, new RecordingDockerControl())) {
                read = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 4);
            }

            claim("each folder's row is followed by its own holder's, and no row says both",
                    () -> assertThat(read).extracting(Row::id).containsExactly("corpus-root", "corpus-root-holder",
                            "working-directory", "working-directory-holder"));
            claim("the corpus root's holder reads Held by, KO, vespera run with its pid and time, its line whole the"
                            + " caption",
                    () -> assertThat(read.get(1)).extracting(Row::name, Row::mark, Row::markClass, Row::words,
                            Row::caption).containsExactly("Held by", "KO", "ko",
                            "vespera run (pid " + running.pid() + ") since " + hhmm(started), rootLine));
            claim("and the working directory's, another control panel's write, reads another control panel with its pid"
                            + " and time, its line whole the caption",
                    () -> assertThat(read.get(3)).extracting(Row::name, Row::mark, Row::markClass, Row::words,
                            Row::caption).containsExactly("Held by", "KO", "ko",
                            "another control panel (pid " + writing.pid() + ") since " + hhmm(started), workingLine));
        } finally {
            running.destroyForcibly().waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
            writing.destroyForcibly().waitFor(BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("The Preflight section says a holder in words")
    @DisplayName("A holder line that cannot be read is said held, by a process whose line cannot be read, the line its caption")
    void aHolderLineThatCannotBeReadIsSaidHeldWithTheLineAsItsCaption() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        String unreadable = "held, by whom it does not say";
        writeLine(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), unreadable);
        List<Row> read;
        try (WindowForTests window = shown(corpus, working, new RecordingDockerControl())) {
            read = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 3);
        }

        claim("the working directory's holder's row follows its folder's, the last",
                () -> assertThat(read).extracting(Row::id)
                        .containsExactly("corpus-root", "working-directory", "working-directory-holder"));
        claim("it reads Held by, KO, a process whose line cannot be read, the line whole its caption",
                () -> assertThat(read.get(2)).extracting(Row::name, Row::mark, Row::words, Row::caption)
                        .containsExactly("Held by", "KO", "a process whose line cannot be read", unreadable));
    }

    @Test
    @Story("The Preflight section says Docling's image where Run would use another")
    @DisplayName("Docling's image is a row only where the last check found Docling running an image other than the Services field's")
    void doclingsImageIsARowOnlyWhereTheCheckFoundItRunningAnotherImage() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(keeping(ANOTHER_IMAGE, List.of()));
        List<Row> another;
        List<Row> none;
        List<Row> anotherAgain;
        List<Row> same;
        try (WindowForTests window = shown(corpus, working, docker)) {
            another = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 3);
            docker.checkAnswering(keeping("", List.of()));
            none = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 2);
            docker.checkAnswering(keeping(ANOTHER_IMAGE, List.of()));
            anotherAgain = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 3);
            docker.checkAnswering(keeping(NAMED_IMAGE, List.of()));
            same = window.awaiting(ThePreflightSectionTest::rows, rows -> rows.size() == 2);
        }

        claim("a check keeping another image than the field's draws Docling's image's row last, KO, both images whole,"
                        + " and its caption",
                () -> assertThat(another.get(2)).extracting(Row::id, Row::name, Row::mark, Row::markClass, Row::words,
                        Row::caption, Row::inItsColumns).containsExactly("docling-image", "Docling's image", "KO", "ko",
                        "runs " + ANOTHER_IMAGE + "; Run would use " + NAMED_IMAGE, IMAGE_CAPTION, true));
        claim("a check keeping no image draws no such row",
                () -> assertThat(none).extracting(Row::id).containsExactly("corpus-root", "working-directory"));
        claim("another image kept again draws it again",
                () -> assertThat(anotherAgain).extracting(Row::id).last().isEqualTo("docling-image"));
        claim("and a check keeping the field's own image draws none",
                () -> assertThat(same).extracting(Row::id).containsExactly("corpus-root", "working-directory"));
    }

    @Test
    @Story("The Profile window offers the models the header's check found")
    @DisplayName("The Profile window's model choices offer the names the last check found Ollama listing, in its order")
    void theProfileWindowOffersTheModelsTheLastCheckFoundOllamaListing() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        List<String> listed = List.of("a-writer:7b", "an-embedder:latest");
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(keeping("", listed));
        List<List<String>> offered;
        try (WindowForTests window = shown(corpus, working, docker)) {
            window.awaiting(root -> WindowForTests.node(root, "check-ollama-mark", Labeled.class).getText(),
                    "OK"::equals);
            window.openProfile();
            offered = window.readProfile(root -> List.of(
                    List.copyOf(choice(root, ProfileKey.GENERATION_MODEL).getItems()),
                    List.copyOf(choice(root, ProfileKey.EMBEDDING_MODEL).getItems())));
        }

        claim("the generation model's choice and the embedding model's each offer the two names, in Ollama's order",
                () -> assertThat(offered).containsExactly(listed, listed));
    }

    // ---- the window and its rows

    private WindowForTests shown(Path corpus, Path working, RecordingDockerControl docker) throws Exception {
        return shown(folder.resolve(STATE), corpus, working, docker);
    }

    /** The window over {@code corpus} and {@code working}, its state's files and folder of corpus-root locks under {@code under}. */
    private static WindowForTests shown(Path under, Path corpus, Path working, RecordingDockerControl docker)
            throws Exception {
        ControlPanelState state = WindowForTests.aState(under, SleepingJar.runtime(), docker);
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", NAMED_IMAGE, "", "", "", ""));
        WindowForTests window = WindowForTests.shown(state);
        window.act(root -> WindowForTests.section(root, "preflight").setExpanded(true));
        return window;
    }

    /**
     * The corpus root's lock file, named as the jar names it, in the folder of corpus-root locks the state of
     * {@link #shown(Path, Path, RecordingDockerControl)} is given, {@code root-locks} under its folder.
     */
    private Path rootLockOf(Path corpus) throws java.io.IOException {
        return CorpusRootLockFile.in(Files.createDirectories(folder.resolve(STATE).resolve("root-locks")), corpus);
    }

    /** A check every row of which is OK, keeping {@code image} of Docling and {@code models} of Ollama. */
    private static DockerCheck keeping(String image, List<String> models) {
        return new DockerCheck(DockerCheck.Mark.OK,
                new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE,
                        RecordingDockerControl.DOCLING_HERE),
                new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE,
                        RecordingDockerControl.OLLAMA_HERE),
                DockerCheck.Mark.OK, DockerCheck.Mark.OK, image, models);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> choice(Parent root, ProfileKey key) {
        return WindowForTests.node(root, "profile-" + key.key(), ComboBox.class);
    }

    /** The grid's rows, by their row index: a row is a node {@code preflight-<row>} with its {@code -mark} and {@code -words}. */
    private static List<Row> rows(Parent root) {
        GridPane grid = WindowForTests.node(root, "preflight-list", GridPane.class);
        List<Node> names = new ArrayList<>();
        for (Node child : grid.getChildren()) {
            String id = child.getId();
            if (id != null && id.startsWith("preflight-") && !id.endsWith("-mark") && !id.endsWith("-words")) {
                names.add(child);
            }
        }
        names.sort(Comparator.comparingInt(ThePreflightSectionTest::rowOf));
        List<Row> rows = new ArrayList<>();
        for (Node name : names) {
            Node mark = WindowForTests.node(root, name.getId() + "-mark", Node.class);
            Node words = WindowForTests.node(root, name.getId() + "-words", Node.class);
            boolean inItsColumns = columnOf(name) == 0 && columnOf(mark) == 1 && columnOf(words) == 2
                    && rowOf(mark) == rowOf(name) && rowOf(words) == rowOf(name);
            rows.add(new Row(name.getId().substring("preflight-".length()), text(name), text(mark),
                    TheWindowChecksAndStartsNothingAsItOpensTest.markClass(mark), text(words),
                    WindowForTests.caption(words), inItsColumns));
        }
        return rows;
    }

    private static int rowOf(Node node) {
        return Objects.requireNonNullElse(GridPane.getRowIndex(node), 0);
    }

    private static int columnOf(Node node) {
        return Objects.requireNonNullElse(GridPane.getColumnIndex(node), 0);
    }

    private static String text(Node node) {
        return node instanceof TextInputControl input ? input.getText() : ((Labeled) node).getText();
    }

    private static String iso(OffsetDateTime at) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at);
    }

    /** {@code at} as the section says a start of today: its hour and minute at the system's zone. */
    private static String hhmm(OffsetDateTime at) {
        return at.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static void writeLine(Path lockFile, String line) throws java.io.IOException {
        Files.writeString(lockFile, line + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
