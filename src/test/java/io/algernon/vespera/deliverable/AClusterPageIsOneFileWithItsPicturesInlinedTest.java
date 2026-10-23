package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster's page is one file (ADR-286): a picture, and an image survivor's copy that is a PNG or a JPEG, are shown
 * by a base64 {@code data:} URI on an {@code <img>}'s {@code src}, and no picture file is written (§1, §4, §6); the page
 * is written to a stream an entry at a time, the bytes handed on counted at each entry's end (§2); it is written
 * through {@code index.html.part} and moved into place (§3); and the files an earlier drawing left in a cluster folder
 * are counted and none deleted (§4).
 *
 * <p>Each test that draws a tree lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the
 * working directory, the tree drawn at the working directory's {@code deliverable/} folder. The pictures and images
 * are real PNGs, JPEGs and GIFs, written by the JDK, so the bytes carry the signature of their kind. The rules of
 * furniture and the budget are {@code DeliverablePicturesTest}'s; every page's sources and links, {@code
 * TheTreeIsHtmlTest}'s; the names a copy yields to, {@code CopyNamesTest}'s; the closing line, {@code
 * pipeline.TheClosingLineSaysWhatAnEarlierDrawingLeftTest}'s.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("560")
@Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
class AClusterPageIsOneFileWithItsPicturesInlinedTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The cluster every drawing here arranges. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    /** That cluster's folder, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String THE_FOLDER = "1-fire-safety/1-fire-doors";

    /** The page, in its folder. */
    private static final String THE_PAGE = THE_FOLDER + "/index.html";

    /** What a page is written under in its folder before it is moved over its place. */
    private static final String THE_PAGES_PART = "index.html.part";

    /** The title the cluster is written over under. */
    private static final String THE_WRITING_TITLE = "Fire doors and their inspection";

    /** A title the redrawing is handed, so a page written again differs from the first. */
    private static final String ANOTHER_TITLE = "Fire doors, inspected again";

    /** Two pictures the first test's document carries, and none other. */
    private static final int TWO_PICTURES = 2;

    /** The files the redrawing finds left in the cluster folder: an old picture and a copy under an old name. */
    private static final long TWO_FILES = 2;

    /** Nothing left behind, in a tree drawn for the first time. */
    private static final long NO_FILE = 0;

    /** The base64 alphabet of RFC 4648 §4, padded to a multiple of four characters, with no line break. */
    private static final Pattern A_PADDED_STANDARD_PAYLOAD = Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?");

    /** Every image on a page, as its source and its alt text. */
    private static final Pattern AN_IMAGE = Pattern.compile("<img src=\"([^\"]*)\" alt=\"([^\"]*)\">");

    /** Where one membership entry begins: its item, which carries its anchor. */
    private static final Pattern AN_ENTRY = Pattern.compile("(?m)^<li id=\"document-\\d+\">");

    @Test
    @Story("A group's page is one file, its pictures in it")
    @DisplayName("A PNG and a JPEG picture are shown as base64 sources on their images, and no picture file is written")
    void aPictureIsShownByABase64DataUriOnItsImageAndNoPictureFileIsWritten(@TempDir Path base) throws IOException {
        byte[] diagram = ListedTree.anImage("png", 7, 5);
        byte[] photograph = ListedTree.anImage("jpg", 9, 4);
        Path archive = anArchive(base, Map.of("reports/retrofit.docx", bytes("a report holding two pictures")));

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                Map.of(),
                List.of(aSurvivor(42, "reports/retrofit.docx", bytes("a report holding two pictures"))),
                Map.of(),
                survivor -> List.of(
                        new ListedPicture("image/png", diagram, false, ""),
                        new ListedPicture("image/jpeg", photograph, false, "")));
        String page = Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8);
        List<String> sources = sourcesOn(page);

        claim(
                "the entry shows the PNG and then the JPEG, each in a paragraph of its own, its source the media type"
                        + " it declares and its own bytes in base64, its alt text empty as none was given",
                () -> assertThat(page)
                        .contains("<p><img src=\"data:image/png;base64," + base64(diagram) + "\" alt=\"\"></p>\n"
                                + "<p><img src=\"data:image/jpeg;base64," + base64(photograph) + "\" alt=\"\"></p>\n"
                                + "</li>\n"));
        claim(
                "each of the " + TWO_PICTURES + " payloads is in the standard alphabet, padded to a multiple of four"
                        + " characters, with no line break or space in it",
                () -> assertThat(sources)
                        .hasSize(TWO_PICTURES)
                        .allSatisfy(source -> assertThat(source.substring(source.indexOf(',') + 1))
                                .matches(A_PADDED_STANDARD_PAYLOAD)));
        claim(
                "and the group's folder holds its page and the document's copy, and no picture file",
                () -> assertThat(filesUnder(tree.resolve(THE_FOLDER)))
                        .containsExactlyInAnyOrder("index.html", "retrofit.docx"));
    }

    @Test
    @Story("A group's page is one file, its pictures in it")
    @DisplayName("A picture whose bytes are not of the kind it declares is shown nowhere and counted as not shown")
    void aPictureWhoseBytesAreNotOfTheKindItDeclaresIsCountedAsNotShown(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/retrofit.docx", bytes("a report")));

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                Map.of(),
                List.of(aSurvivor(42, "reports/retrofit.docx", bytes("a report"))),
                Map.of(),
                survivor -> List.of(
                        new ListedPicture("image/png", bytes("bytes that are no PNG"), false, ""),
                        new ListedPicture("image/jpeg", ListedTree.anImage("png", 3, 3), false, "")));
        String page = Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8);

        claim(
                "neither picture is shown: the first begins with no PNG signature, and the second, declared a JPEG,"
                        + " is a PNG, so a page never declares one kind for bytes of another",
                () -> assertThat(page).doesNotContain("<img"));
        claim(
                "and both are counted on the line saying what is not shown, so the page does not pass them off as"
                        + " absent",
                () -> assertThat(page).contains("<p><em>2 more pictures from this document are not shown.</em></p>\n"));
    }

    @Test
    @Story("A group's page is written an entry at a time")
    @DisplayName("The page is handed to its stream an entry at a time, and the bytes handed on are told at each entry's end")
    void thePageIsWrittenAnEntryAtATimeAndTellsTheBytesWrittenAtEachEntrysEnd(@TempDir Path base) throws IOException {
        ListedSurvivor first = aSurvivor(10, "reports/Brücke über den Fluss.docx", bytes("the first"));
        ListedSurvivor second = aSurvivor(11, "reports/Übersicht – Türen.pdf", bytes("the second"));
        List<ListedSurvivor> members = List.of(first, second);
        Path archive = anArchive(base, Map.of(
                first.path().value(), bytes("the first"), second.path().value(), bytes("the second")));
        Path tree = base.resolve("work").resolve("deliverable").resolve(ListedTree.RUN_ID).toAbsolutePath();
        Path folder = Files.createDirectories(tree.resolve(THE_FOLDER));
        Copies copies = new Copies(
                tree, archive.toAbsolutePath().toString(), ReplacingMove.ATOMIC, () -> Long.MAX_VALUE, DeliverableProgress.NONE);
        copies.copyAll(folder, members);
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        boolean[] secondPass = {false};
        List<OccurrenceId> askedFor = new ArrayList<>();
        List<Long> handedOnWhenAsked = new ArrayList<>();
        Map<OccurrenceId, byte[]> pictures = Map.of(
                first.occurrence(), ListedTree.anImage("png", 6, 6), second.occurrence(), ListedTree.anImage("png", 8, 3));
        EntryPictures entryPictures = ListedArrangement.picturesAmong(
                members,
                survivor -> {
                    if (secondPass[0]) {
                        askedFor.add(survivor.occurrence());
                        handedOnWhenAsked.add((long) page.size());
                    }
                    return List.of(new ListedPicture("image/png", pictures.get(survivor.occurrence()), false, ""));
                },
                DeliverableProgress.NONE);
        secondPass[0] = true;
        List<Long> told = new ArrayList<>();
        List<Long> handedOnWhenTold = new ArrayList<>();
        List<Boolean> endedWithAnEntry = new ArrayList<>();

        ClusterPage.write(
                page,
                THE_CLUSTER,
                new SynthesisDoc("Brandschutztüren – Prüfung", "Both [1] and [2].", List.of(first.occurrence(), second.occurrence())),
                null,
                members,
                folder,
                copies,
                entryPictures,
                DeliverableProgress.NONE,
                bytesSoFar -> {
                    told.add(bytesSoFar);
                    handedOnWhenTold.add((long) page.size());
                    endedWithAnEntry.add(new String(page.toByteArray(), StandardCharsets.UTF_8).endsWith("</li>\n"));
                },
                ConfluenceEntries.NONE,
                false,
                DeliverableLanguage.ENGLISH);
        String written = page.toString(StandardCharsets.UTF_8);
        List<Long> entryEnds = new ArrayList<>();
        for (int at = written.indexOf("</li>\n"); at >= 0; at = written.indexOf("</li>\n", at + 1)) {
            entryEnds.add((long) written.substring(0, at + "</li>\n".length()).getBytes(StandardCharsets.UTF_8).length);
        }

        claim(
                "each of the two entries' ends is told once, in order, as the count of UTF-8 bytes of the page up to"
                        + " and including that entry's end, the title and the paths carrying characters of two and"
                        + " three bytes, so a count of characters would fall short",
                () -> assertThat(told).hasSize(2).containsExactlyElementsOf(entryEnds));
        claim(
                "and when each is told, every byte counted has already been handed to the stream, which then ends"
                        + " with that entry's end",
                () -> {
                    assertThat(handedOnWhenTold).containsExactlyElementsOf(told);
                    assertThat(endedWithAnEntry).containsExactly(true, true);
                });
        claim(
                "the first entry's pictures are asked for before its end is handed on, and the second entry's only"
                        + " once the first entry, its picture inlined, has been handed on whole and before the second's"
                        + " end is: the page is written as it is made, never built first and written after",
                () -> {
                    assertThat(askedFor).containsExactly(first.occurrence(), second.occurrence());
                    assertThat(handedOnWhenAsked.get(0)).isLessThan(told.get(0));
                    assertThat(handedOnWhenAsked.get(1)).isGreaterThanOrEqualTo(told.get(0)).isLessThan(told.get(1));
                });
    }

    @Test
    @Story("A group's page is written beside its place and moved over it")
    @DisplayName("A first drawing whose group page cannot be moved into place leaves no page in the group's folder")
    void aFirstDrawingWhosePageCannotBeMovedLeavesNoPageInItsFolder(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", bytes("the first survivor")));
        Path tree = base.resolve("work").resolve("deliverable").resolve(ListedTree.RUN_ID);

        Throwable thrown = catchThrowable(() -> drawWith(base, archive, THE_WRITING_TITLE, refusingEveryClusterPage()));

        claim(
                "the drawing fails, as it does when the tree's index cannot be moved",
                () -> assertThat(thrown).isInstanceOf(UncheckedIOException.class));
        claim(
                "no page stands at index.html in the group's folder: a page is put there only by its move, whole",
                () -> assertThat(tree.resolve(THE_PAGE)).doesNotExist());
        claim(
                "and the tree's index, moved only after every page, is not put in place either",
                () -> assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).doesNotExist());
    }

    @Test
    @Story("A group's page is written beside its place and moved over it")
    @DisplayName("A redrawing whose group page cannot be moved into place leaves the earlier page whole")
    void aRedrawingWhosePageCannotBeMovedLeavesTheEarlierPageWhole(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", bytes("the first survivor")));
        Path tree = drawWith(base, archive, THE_WRITING_TITLE, ReplacingMove.ATOMIC);
        byte[] theEarlierPage = Files.readAllBytes(tree.resolve(THE_PAGE));

        claim(
                "a finished drawing leaves no part of any page behind, and its page carries its title",
                () -> {
                    assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part"));
                    assertThat(new String(theEarlierPage, StandardCharsets.UTF_8)).contains(THE_WRITING_TITLE);
                });

        Throwable thrown = catchThrowable(() -> drawWith(base, archive, ANOTHER_TITLE, refusingEveryClusterPage()));

        claim(
                "the redrawing, handed another title, fails at its page's move",
                () -> assertThat(thrown).isInstanceOf(UncheckedIOException.class));
        claim(
                "and the group's page is the earlier drawing's, byte for byte: the new page was written beside it"
                        + " and never over it",
                () -> assertThat(tree.resolve(THE_PAGE)).hasBinaryContent(theEarlierPage));
    }

    @Test
    @Story("Nothing beneath the folder is deleted")
    @DisplayName("The tree counts the files an earlier drawing left in a group's folder, names none and deletes none")
    void theWriterCountsTheFilesAnEarlierDrawingLeftInAClusterFolderAndDeletesNone(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", bytes("the first survivor")));
        List<Long> reported = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void filesLeftBehind(long files) {
                reported.add(files);
            }
        };
        Path tree = drawWith(base, archive, THE_WRITING_TITLE, ReplacingMove.ATOMIC, hearing);
        List<Long> afterTheFirst = List.copyOf(reported);
        reported.clear();
        Path anEarlierPicture = Files.write(tree.resolve(THE_FOLDER).resolve("0123456789abcdef.png"), ListedTree.anImage("png", 2, 2));
        Path aCopyUnderAnEarlierName =
                Files.write(tree.resolve(THE_FOLDER).resolve("q3~42.pdf"), bytes("the first survivor"));
        Path aFolderInside = Files.createDirectories(tree.resolve(THE_FOLDER).resolve("stray"));

        drawWith(base, archive, THE_WRITING_TITLE, ReplacingMove.ATOMIC, hearing);

        claim(
                "the first drawing, over an empty tree, tells once that " + NO_FILE + " files were left",
                () -> assertThat(afterTheFirst).containsExactly(NO_FILE));
        claim(
                "the redrawing tells once of " + TWO_FILES + " files: a picture an earlier drawing wrote and a copy"
                        + " under a name this drawing did not give; its page and its copy are not counted, nor is a"
                        + " folder inside the group's folder",
                () -> assertThat(reported).containsExactly(TWO_FILES));
        claim(
                "and every one of them is where it was: nothing in the group's folder is deleted",
                () -> {
                    assertThat(anEarlierPicture).isRegularFile();
                    assertThat(aCopyUnderAnEarlierName).hasBinaryContent(bytes("the first survivor"));
                    assertThat(aFolderInside).isDirectory();
                });
    }

    @Test
    @Story("A group's page is one file, its pictures in it")
    @DisplayName("A PNG or JPEG image file is shown on its entry from its copy, whatever its name, and any other file is only linked")
    void aPngOrJpegImageIsShownInlineFromItsCopyAndAnyOtherImageIsOnlyLinked(@TempDir Path base) throws IOException {
        byte[] plan = ListedTree.anImage("png", 5, 7);
        byte[] site = ListedTree.anImage("jpg", 6, 2);
        byte[] sketch = ListedTree.anImage("gif", 4, 4);
        byte[] renamed = ListedTree.anImage("png", 3, 2);
        byte[] notAPng = bytes("a text file whose name ends in .png");
        Path archive = anArchive(base, Map.of(
                "scans/plan.png", plan,
                "photos/site.jpg", site,
                "drawings/sketch.gif", sketch,
                "scans/plan-renamed.dat", renamed,
                "scans/not-a-png.png", notAPng));

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                Map.of(),
                List.of(
                        aSurvivor(20, "scans/plan.png", plan),
                        aSurvivor(21, "photos/site.jpg", site),
                        aSurvivor(22, "drawings/sketch.gif", sketch),
                        aSurvivor(23, "scans/lost.png", ListedTree.anImage("png", 3, 3)),
                        aSurvivor(24, "scans/plan-renamed.dat", renamed),
                        aSurvivor(25, "scans/not-a-png.png", notAPng)),
                Map.of());
        String page = Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8);

        claim(
                "the PNG image's entry links its copy and then shows that copy, its bytes as a base64 PNG source,"
                        + " with empty alt text, the link above it naming the document",
                () -> assertThat(theEntryNaming(page, "scans/plan.png"))
                        .contains("<a href=\"./plan.png\">scans/plan.png</a>\n"
                                + "<p><img src=\"data:image/png;base64," + base64(plan) + "\" alt=\"\"></p>\n"
                                + "</li>\n"));
        claim(
                "and the JPEG image's entry likewise, as a JPEG source",
                () -> assertThat(theEntryNaming(page, "photos/site.jpg"))
                        .contains("<a href=\"./site.jpg\">photos/site.jpg</a>\n"
                                + "<p><img src=\"data:image/jpeg;base64," + base64(site) + "\" alt=\"\"></p>\n"
                                + "</li>\n"));
        claim(
                "the GIF image's entry links its copy and shows no image, its kind being neither of the two",
                () -> assertThat(theEntryNaming(page, "drawings/sketch.gif"))
                        .contains("<a href=\"./sketch.gif\">drawings/sketch.gif</a>")
                        .doesNotContain("<img"));
        claim(
                "and a PNG whose original is gone, so that no copy stands, shows no image either",
                () -> assertThat(theEntryNaming(page, "scans/lost.png")).doesNotContain("<img"));
        claim(
                "a PNG whose name does not say so is shown all the same, its copy's first bytes being a PNG's:"
                        + " the bytes decide, not the name",
                () -> assertThat(theEntryNaming(page, "scans/plan-renamed.dat"))
                        .contains("<a href=\"./plan-renamed.dat\">scans/plan-renamed.dat</a>\n"
                                + "<p><img src=\"data:image/png;base64," + base64(renamed) + "\" alt=\"\"></p>\n"
                                + "</li>\n"));
        claim(
                "and a file named as a PNG whose bytes are not one is only linked, and shows no image",
                () -> assertThat(theEntryNaming(page, "scans/not-a-png.png"))
                        .contains("<a href=\"./not-a-png.png\">scans/not-a-png.png</a>")
                        .doesNotContain("<img"));
        claim(
                "so the page shows exactly three images, the PNG's, the JPEG's and the renamed PNG's, each its"
                        + " copy's bytes",
                () -> assertThat(sourcesOn(page))
                        .containsExactly(
                                "data:image/png;base64," + base64(plan),
                                "data:image/jpeg;base64," + base64(site),
                                "data:image/png;base64," + base64(renamed)));
    }

    /**
     * ADR-286 §6: a copy that cannot be read is linked only, as a copy of any other kind is. A folder stands at the
     * copy's place, so opening it or reading its first bytes fails with an {@link IOException} on every platform the
     * suite runs on; no file permission is relied on, which the suite's user may override.
     */
    @Test
    @Story("A group's page is one file, its pictures in it")
    @DisplayName("A document whose copy cannot be read shows no image, and its entry is still written")
    void aCopyThatCannotBeReadShowsNoImageAndFailsNothing(@TempDir Path base) throws IOException {
        ListedSurvivor unreadable = aSurvivor(30, "scans/unreadable.png", ListedTree.anImage("png", 2, 2));
        Path aFolderAtTheCopysPlace = Files.createDirectories(base.resolve("unreadable.png"));
        EntryPictures pictures =
                ListedArrangement.picturesAmong(List.of(unreadable), SurvivorPictures.none(), DeliverableProgress.NONE);
        ByteArrayOutputStream page = new ByteArrayOutputStream();

        Throwable thrown = catchThrowable(
                () -> pictures.writeUnder(page, unreadable, java.util.Optional.of(aFolderAtTheCopysPlace)));

        claim(
                "nothing is thrown: a copy that cannot be read fails neither its entry nor the drawing",
                () -> assertThat(thrown).isNull());
        claim(
                "and nothing is written under the entry, not even the start of an image, since the copy's first bytes"
                        + " are read before any tag is begun",
                () -> assertThat(page.toString(StandardCharsets.UTF_8)).isEmpty());
    }

    /** A move that refuses every cluster page's, as a file system refusing it would, and makes every other. */
    private static ReplacingMove refusingEveryClusterPage() {
        return new ReplacingMove(
                (part, target) -> {
                    if (target.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME)
                            && part.getFileName().toString().equals(THE_PAGES_PART)
                            && !target.getParent().getFileName().toString().equals(ListedTree.RUN_ID)) {
                        throw new IOException("a group's page was refused its place");
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});
    }

    private static Path drawWith(Path base, Path archive, String title, ReplacingMove moves) {
        return drawWith(base, archive, title, moves, DeliverableProgress.NONE);
    }

    /** The one-survivor tree of {@link #THE_CLUSTER}, written over under {@code title}, moved by {@code moves}. */
    private static Path drawWith(
            Path base, Path archive, String title, ReplacingMove moves, DeliverableProgress progress) {
        Map<ClusterSlot, SynthesisDoc> written = Map.of(slotOf(THE_CLUSTER), writing(title, "It says [1].", 42));
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        written,
                        List.of(aSurvivor(42, "reports/q3.pdf", bytes("the first survivor"))),
                        Map.of()),
                SurvivorPictures.none(),
                progress,
                moves);
    }

    /** The one membership entry of {@code page} naming {@code document}, from its item to the next. */
    private static String theEntryNaming(String page, String document) {
        Matcher entry = AN_ENTRY.matcher(page);
        List<Integer> starts = new ArrayList<>();
        while (entry.find()) {
            starts.add(entry.start());
        }
        starts.add(page.length());
        for (int at = 0; at < starts.size() - 1; at++) {
            String body = page.substring(starts.get(at), starts.get(at + 1));
            if (body.contains(document)) {
                return body;
            }
        }
        throw new IllegalStateException("no entry on the page names " + document);
    }

    /** Every image source on {@code page}, in the order the page gives them. */
    private static List<String> sourcesOn(String page) {
        Matcher image = AN_IMAGE.matcher(page);
        List<String> found = new ArrayList<>();
        while (image.find()) {
            found.add(image.group(1));
        }
        return List.copyOf(found);
    }

    /** {@code <base>/archive}, holding each path's bytes. */
    private static Path anArchive(Path base, Map<String, byte[]> files) throws IOException {
        Path archive = base.resolve("archive");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path original = archive.resolve(file.getKey());
            Files.createDirectories(original.getParent());
            Files.write(original, file.getValue());
        }
        return archive;
    }

    /** A survivor of {@link #THE_CLUSTER}, its recorded key that of {@code content}, scored by its occurrence. */
    private static ListedSurvivor aSurvivor(long occurrence, String path, byte[] content) {
        return survivor(occurrence, path, sha256Of(content), THE_SEED, SEED_PATH, 0, 1.0 - occurrence / 1000.0);
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
