package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.columnByPath;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster's folder in the tree (ADR-280): it holds the cluster's page, {@code index.html}, the pictures the page
 * shows, and the copy of each of the cluster's survivors under its original's own name, and {@code documents/} is no
 * longer written. A copy at its name is trusted unread, the listing names it in its folder, an entry links it by its
 * name percent-encoded, the survey counts the copies past Windows' limit, and the writer reports what an earlier
 * drawing left at the top of the tree and in each partition folder.
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the working directory,
 * the tree drawn at the working directory's {@code deliverable/} folder. How each name is worked out is {@code
 * CopyNamesTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class TheClusterFolderHoldsItsPageAndItsCopiesTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The cluster most fixtures here arrange, written over from both its survivors. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    /** That cluster's folder, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String THE_FOLDER = "1-fire-safety/1-fire-doors";

    /** A second cluster of the same partition, for the listing's paths. */
    private static final RecordedCluster THE_OTHER_CLUSTER = cluster(THE_SEED, 1, "Smoke dampers", 2, 1);

    /** And its folder. */
    private static final String THE_OTHER_FOLDER = "1-fire-safety/2-smoke-dampers";

    /** What a path may be at most for every program on Windows to open it: one less than the 260 of its limit. */
    private static final int WINDOWS_LONGEST_PATH = 259;

    /** How long the path of the tree's cluster folder is made in the test of long paths, its separator included. */
    private static final int A_DEEP_FOLDER = 200;

    /**
     * The listing's columns: the occurrence, its path, its hash, its winning seed, its score, the seed's path, its
     * cluster, the partition's and the cluster's order, its copy and the copy's outcome.
     */
    private static final int EVERY_COLUMN = 11;

    /** A copy that stands at a path longer than {@link #WINDOWS_LONGEST_PATH}. */
    private static final long ONE_COPY = 1;

    @Test
    @Story("A group's folder holds its page and its documents")
    @DisplayName("A group's folder holds its page and a copy of each of its documents under the document's own name, and no documents folder is written")
    void aClustersFolderHoldsItsPageAndItsCopiesAndNoDocumentsFolderIsWritten(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of(
                "reports/Q3 Report (final).PDF", "the first survivor",
                "notes/risers.txt", "the second survivor"));

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                writtenFrom(THE_CLUSTER, 42, 43),
                List.of(
                        aSurvivor(42, "reports/Q3 Report (final).PDF", "the first survivor", 0),
                        aSurvivor(43, "notes/risers.txt", "the second survivor", 0)),
                Map.of());

        claim(
                "the tree holds the index, the listing and the marker at its top, and the group's folder holds"
                        + " its page, index.html, and each document's copy under the document's own name, its spaces,"
                        + " brackets and capitals as written, and nothing else",
                () -> assertThat(filesUnder(tree))
                        .containsExactlyInAnyOrder(
                                "index.html",
                                "documents.csv",
                                "vespera-deliverable.txt",
                                THE_FOLDER + "/index.html",
                                THE_FOLDER + "/Q3 Report (final).PDF",
                                THE_FOLDER + "/risers.txt"));
        claim(
                "each copy holds exactly the bytes of its document in the archive",
                () -> {
                    assertThat(tree.resolve(THE_FOLDER).resolve("Q3 Report (final).PDF"))
                            .hasBinaryContent(bytes("the first survivor"));
                    assertThat(tree.resolve(THE_FOLDER).resolve("risers.txt"))
                            .hasBinaryContent(bytes("the second survivor"));
                });
        claim(
                "no documents folder is written",
                () -> assertThat(tree.resolve("documents")).doesNotExist());
        claim(
                "and the index links the group to the page in its folder",
                () -> assertThat(Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8))
                        .contains("<a href=\"" + THE_FOLDER + "/index.html\">Fire doors</a>"));
    }

    @Test
    @Story("A group's folder holds its page and its documents")
    @DisplayName("Each row of the listing names its document's copy in its group's folder, and two groups may each hold a document of one name")
    void eachRowOfTheListingNamesItsCopyInItsClustersFolder(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of(
                "a/minutes.docx", "the first minutes",
                "b/minutes.docx", "the second minutes",
                "c/MINUTES.DOCX", "the third minutes"));
        RecordedCluster first = cluster(THE_SEED, 0, "Fire doors", 1, 2);

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(first, THE_OTHER_CLUSTER),
                Map.of(),
                List.of(
                        aSurvivor(11, "a/minutes.docx", "the first minutes", 0),
                        aSurvivor(12, "b/minutes.docx", "the second minutes", 1),
                        aSurvivor(13, "c/MINUTES.DOCX", "the third minutes", 0)),
                Map.of());

        claim(
                "each row's copy is its group's folder and the copy's name: the two groups each keep a"
                        + " minutes.docx, names meeting only within one folder, and the third, meeting the first in"
                        + " its folder without regard to case, takes its number before its extension",
                () -> assertThat(columnByPath(tree, "copy"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                "a/minutes.docx", THE_FOLDER + "/minutes.docx",
                                "b/minutes.docx", THE_OTHER_FOLDER + "/minutes.docx",
                                "c/MINUTES.DOCX", THE_FOLDER + "/MINUTES~13.DOCX")));
        claim(
                "and each names a copy that stands, holding its own document's bytes",
                () -> {
                    assertThat(tree.resolve(THE_FOLDER).resolve("minutes.docx")).hasBinaryContent(bytes("the first minutes"));
                    assertThat(tree.resolve(THE_OTHER_FOLDER).resolve("minutes.docx"))
                            .hasBinaryContent(bytes("the second minutes"));
                    assertThat(tree.resolve(THE_FOLDER).resolve("MINUTES~13.DOCX"))
                            .hasBinaryContent(bytes("the third minutes"));
                });
    }

    @Test
    @Story("A group's folder holds its page and its documents")
    @DisplayName("A copy whose name holds a comma is quoted in the listing as a path is, so its row keeps every column")
    void theCopyColumnIsQuotedLikeThePathColumns(@TempDir Path base) throws IOException {
        String aNameWithAComma = "reports/b, c.pdf";
        Path archive = anArchive(base, Map.of(aNameWithAComma, "a report"));

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                Map.of(),
                List.of(aSurvivor(42, aNameWithAComma, "a report", 0)),
                Map.of());
        List<String> lines = Files.readAllLines(tree.resolve("documents.csv"), StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<List<String>> rows = ListedTree.manifestOf(tree);

        claim(
                "the listing holds its header and the one document's row, and the row names its copy in the group's"
                        + " folder between double quotes, as its path is, so the comma in the copy's name is read as"
                        + " part of it",
                () -> {
                    assertThat(lines).hasSize(2);
                    assertThat(lines.getLast())
                            .startsWith("42,\"" + aNameWithAComma + "\",")
                            .endsWith(",\"" + THE_FOLDER + "/b, c.pdf\",copied");
                });
        claim(
                "and read as a CSV the row has the header's " + EVERY_COLUMN + " fields, its copy whole",
                () -> {
                    assertThat(rows.getFirst()).hasSize(EVERY_COLUMN);
                    assertThat(rows.getLast()).hasSize(EVERY_COLUMN);
                    assertThat(columnByPath(tree, "copy"))
                            .containsExactlyEntriesOf(Map.of(aNameWithAComma, THE_FOLDER + "/b, c.pdf"));
                });
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("An entry links to its copy with every character a link cannot carry as it is encoded, and its text is the document's path")
    void anEntryLinksToItsCopyWithEveryByteOutsideTheUnreservedSetEncoded(@TempDir Path base) throws IOException {
        String aNameALinkCannotCarry = "reports/50% #1 draft (v2).pdf";
        String anAccentedName = "notes/résumé.txt";
        Path archive = anArchive(base, Map.of(aNameALinkCannotCarry, "a draft"));
        boolean theAccentedOriginalStands = true;
        try {
            Files.createDirectories(archive.resolve("notes"));
            Files.write(archive.resolve(anAccentedName), bytes("a summary"));
        } catch (InvalidPathException | IOException e) {
            theAccentedOriginalStands = false;
        }

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                writtenFrom(THE_CLUSTER, 42, 43),
                List.of(aSurvivor(42, aNameALinkCannotCarry, "a draft", 0), aSurvivor(43, anAccentedName, "a summary", 0)),
                Map.of());
        Path page = tree.resolve(THE_FOLDER).resolve("index.html");
        String text = Files.readString(page, StandardCharsets.UTF_8);
        String destination = "./50%25%20%231%20draft%20%28v2%29.pdf";

        claim(
                "the entry links to the copy beside the page, its percent sign, spaces, hash and brackets each"
                        + " encoded as a percent sign and two capital hexadecimal digits, and its text is the"
                        + " document's path beneath the archive root, as it was written",
                () -> assertThat(text).contains("<a href=\"" + destination + "\">" + aNameALinkCannotCarry + "</a>"));
        claim(
                "and the link, followed from the page's folder as a browser decodes it, lands on the copy, which"
                        + " holds the document's bytes",
                () -> assertThat(page.getParent().resolve(URI.create(destination).getPath()).normalize())
                        .isEqualTo(tree.resolve(THE_FOLDER).resolve("50% #1 draft (v2).pdf"))
                        .hasBinaryContent(bytes("a draft")));
        assumeTrue(
                theAccentedOriginalStands,
                "this file system, under this JVM's encoding of file names, will not create an accented name");
        claim(
                "and where an accented document stands, each byte of its accented letters' UTF-8 is encoded",
                () -> assertThat(text).contains("<a href=\"./r%C3%A9sum%C3%A9.txt\">" + anAccentedName + "</a>"));
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A numbered copy already in the tree is kept as it is, and the archive is not read for it again")
    void aSuffixedCopyThatStandsIsKeptWithoutReadingTheArchive(@TempDir Path base) throws IOException {
        Path archive = anArchive(base, Map.of("a/Report.pdf", "the first report", "b/REPORT.PDF", "the second report"));
        List<ListedSurvivor> both = List.of(
                aSurvivor(42, "a/Report.pdf", "the first report", 0), aSurvivor(43, "b/REPORT.PDF", "the second report", 0));
        Path tree = ListedTree.write(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFrom(THE_CLUSTER, 42, 43), both, Map.of());
        Path plain = tree.resolve(THE_FOLDER).resolve("Report.pdf");
        Path numbered = tree.resolve(THE_FOLDER).resolve("REPORT~43.PDF");
        FileTime plainWritten = Files.getLastModifiedTime(plain);
        FileTime numberedWritten = Files.getLastModifiedTime(numbered);
        Files.delete(archive.resolve("a/Report.pdf"));
        Files.delete(archive.resolve("b/REPORT.PDF"));

        ListedTree.write(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFrom(THE_CLUSTER, 42, 43), both, Map.of());

        claim(
                "with both documents gone from the archive, both rows still say copied, the second naming its"
                        + " numbered copy: the names were worked out again from the group's documents, and a copy"
                        + " standing at its name was put there only once its bytes were checked",
                () -> {
                    assertThat(columnByPath(tree, "copy_outcome"))
                            .containsExactlyInAnyOrderEntriesOf(Map.of("a/Report.pdf", "copied", "b/REPORT.PDF", "copied"));
                    assertThat(columnByPath(tree, "copy")).containsEntry("b/REPORT.PDF", THE_FOLDER + "/REPORT~43.PDF");
                });
        claim(
                "and both copies are the ones the first drawing left, their bytes and their times untouched",
                () -> {
                    assertThat(plain).hasBinaryContent(bytes("the first report"));
                    assertThat(numbered).hasBinaryContent(bytes("the second report"));
                    assertThat(List.of(Files.getLastModifiedTime(plain), Files.getLastModifiedTime(numbered)))
                            .containsExactly(plainWritten, numberedWritten);
                });
    }

    @Test
    @Story("A document keeps its own name in its group's folder")
    @DisplayName("A document named as a picture its group's page shows takes its number, and both stand side by side")
    void anOriginalNamedAsAPictureOfItsClusterYieldsToThePicture(@TempDir Path base) throws IOException {
        byte[] diagram = bytes("an architecture diagram");
        String thePicturesName = sha256Of(diagram).substring(0, 16) + ".png";
        String theNumberedName = thePicturesName.replace(".png", "~43.png");
        String aScanNamedAsThePicture = "scans/" + thePicturesName;
        Path archive = anArchive(base, Map.of(
                "reports/retrofit.docx", "a report holding a diagram",
                aScanNamedAsThePicture, "a scan that happens to share the picture's name"));
        SurvivorPictures theReportsDiagram = survivor -> survivor.occurrence().value() == 42
                ? List.of(new ListedPicture("image/png", diagram, false, ""))
                : List.of();

        Path tree = ListedTree.write(
                base.resolve("work"),
                archive,
                List.of(THE_CLUSTER),
                writtenFrom(THE_CLUSTER, 42, 43),
                List.of(
                        aSurvivor(42, "reports/retrofit.docx", "a report holding a diagram", 0),
                        aSurvivor(43, aScanNamedAsThePicture, "a scan that happens to share the picture's name", 0)),
                Map.of(),
                theReportsDiagram);
        String page = Files.readString(tree.resolve(THE_FOLDER).resolve("index.html"), StandardCharsets.UTF_8);

        claim(
                "the picture stands in the group's folder under its own name, holding its pixels, and the page"
                        + " shows it by that name alone, the page being in the same folder",
                () -> {
                    assertThat(tree.resolve(THE_FOLDER).resolve(thePicturesName)).hasBinaryContent(diagram);
                    assertThat(page).contains("<img src=\"" + thePicturesName + "\" alt=\"\">");
                });
        claim(
                "and the document of that name stands beside it with its number before the extension, holding"
                        + " its own bytes, its entry linking there",
                () -> {
                    assertThat(tree.resolve(THE_FOLDER).resolve(theNumberedName))
                            .hasBinaryContent(bytes("a scan that happens to share the picture's name"));
                    assertThat(page).contains("<a href=\"./" + theNumberedName + "\">" + aScanNamedAsThePicture + "</a>");
                });
    }

    @Test
    @Story("Every file of the tree has a name of bounded length")
    @DisplayName("After the last copy the tree counts the copies whose path is too long for some Windows programs to open")
    void theSurveyAfterTheLastCopyCountsTheCopiesPastTheWindowsLimit(@TempDir Path base) throws IOException {
        String aLongName = "l".repeat(96) + ".txt";
        int aShallowFolder = (base.toAbsolutePath() + "/work/deliverable/" + ListedTree.RUN_ID + "/" + THE_FOLDER + "/")
                .length();
        int padding = A_DEEP_FOLDER - aShallowFolder - 1;
        if (padding < 1) {
            abort("the temporary folder's own path is too long for a folder of " + A_DEEP_FOLDER + " characters to be"
                    + " made beneath it");
        }
        Path work = base.resolve("p".repeat(padding)).resolve("work");
        Path archive = anArchive(base, Map.of("long/" + aLongName, "a long name", "notes/risers.txt", "a short name"));
        List<Long> counted = new ArrayList<>();
        DeliverableProgress counting = new DeliverableProgress() {
            @Override
            public void copiesAtALongPath(long copies) {
                counted.add(copies);
            }
        };

        Path tree = Deliverable.writeTo(
                work,
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        writtenFrom(THE_CLUSTER, 42, 43),
                        List.of(
                                aSurvivor(42, "long/" + aLongName, "a long name", 0),
                                aSurvivor(43, "notes/risers.txt", "a short name", 0)),
                        Map.of()),
                SurvivorPictures.none(),
                counting);
        Path theLongCopy = tree.resolve(THE_FOLDER).resolve(aLongName).toAbsolutePath();
        Path theShortCopy = tree.resolve(THE_FOLDER).resolve("risers.txt").toAbsolutePath();

        claim(
                "the precondition: both copies stand, one at a path longer than " + WINDOWS_LONGEST_PATH
                        + " characters and one within it",
                () -> {
                    assertThat(theLongCopy).hasBinaryContent(bytes("a long name"));
                    assertThat(theShortCopy).hasBinaryContent(bytes("a short name"));
                    assertThat(theLongCopy.toString().length()).isGreaterThan(WINDOWS_LONGEST_PATH);
                    assertThat(theShortCopy.toString().length()).isLessThanOrEqualTo(WINDOWS_LONGEST_PATH);
                });
        claim(
                "the tree counts, once, the " + ONE_COPY + " copy whose path is longer than " + WINDOWS_LONGEST_PATH
                        + " characters, and names neither",
                () -> assertThat(counted).containsExactly(ONE_COPY));
    }

    @Test
    @Story("Nothing beneath the folder is deleted")
    @DisplayName("The tree reports the documents folder and counts the other folders an earlier drawing left, and deletes none of them")
    void theWriterReportsTheDocumentsFolderAndCountsTheOtherFoldersAnEarlierDrawingLeft(@TempDir Path base)
            throws IOException {
        Path archive = anArchive(base, Map.of("reports/q3.pdf", "the first survivor", "notes/risers.txt", "the second"));
        List<String> reported = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                reported.add("documents folder " + documentsFolder + ", other folders " + otherFolders);
            }
        };
        Path tree = drawWith(base, archive, hearing);
        List<String> afterTheFirst = List.copyOf(reported);
        reported.clear();
        Path anEarlierLayoutsCopy = Files.createDirectories(tree.resolve("documents")).resolve("42-q3.pdf");
        Files.writeString(anEarlierLayoutsCopy, "an earlier layout's copy", StandardCharsets.UTF_8);
        Path anEarlierPartition = Files.createDirectories(tree.resolve("2-an-earlier-partition"));
        Path anEarlierCluster = Files.createDirectories(tree.resolve("1-fire-safety").resolve("9-an-earlier-group"));
        Path anEarlierPage = Files.writeString(
                tree.resolve("1-fire-safety").resolve("1-fire-doors.html"), "an earlier layout's page", StandardCharsets.UTF_8);
        Path aFolderInsideAWrittenOne = Files.createDirectories(tree.resolve(THE_FOLDER).resolve("stray"));
        Path aFileAtTheTop = Files.writeString(tree.resolve("notes.txt"), "the operator's notes", StandardCharsets.UTF_8);

        drawWith(base, archive, hearing);

        claim(
                "the first drawing, over an empty tree, reports once that nothing was left behind",
                () -> assertThat(afterTheFirst).containsExactly("documents folder false, other folders 0"));
        claim(
                "the second reports once the documents folder, and counts 2 other folders: the partition folder"
                        + " at the top and the group's folder in the partition, which this drawing did not write;"
                        + " a file is not counted, and a folder inside a group's folder this drawing wrote is not"
                        + " looked at",
                () -> assertThat(reported).containsExactly("documents folder true, other folders 2"));
        claim(
                "and every one of them is where it was: nothing beneath the tree is deleted",
                () -> {
                    assertThat(anEarlierLayoutsCopy).hasContent("an earlier layout's copy");
                    assertThat(anEarlierPartition).isDirectory();
                    assertThat(anEarlierCluster).isDirectory();
                    assertThat(anEarlierPage).hasContent("an earlier layout's page");
                    assertThat(aFolderInsideAWrittenOne).isDirectory();
                    assertThat(aFileAtTheTop).hasContent("the operator's notes");
                });
    }

    /** The two-survivor tree of {@link #THE_CLUSTER} drawn over {@code archive}, {@code progress} hearing it. */
    private static Path drawWith(Path base, Path archive, DeliverableProgress progress) {
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(THE_CLUSTER),
                        writtenFrom(THE_CLUSTER, 42, 43),
                        List.of(
                                aSurvivor(42, "reports/q3.pdf", "the first survivor", 0),
                                aSurvivor(43, "notes/risers.txt", "the second", 0)),
                        Map.of()),
                SurvivorPictures.none(),
                progress);
    }

    /** {@code <base>/archive}, holding each path's text as its bytes, which is returned. */
    private static Path anArchive(Path base, Map<String, String> files) throws IOException {
        Path archive = base.resolve("archive");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path original = archive.resolve(file.getKey());
            Files.createDirectories(original.getParent());
            Files.write(original, bytes(file.getValue()));
        }
        return archive;
    }

    /** A survivor of the cluster of {@code ordinal}, its recorded key that of {@code text}'s bytes. */
    private static ListedSurvivor aSurvivor(long occurrence, String path, String text, int ordinal) {
        return survivor(occurrence, path, sha256Of(bytes(text)), THE_SEED, SEED_PATH, ordinal, 1.0 - occurrence / 1000.0);
    }

    /** The writing over {@code recorded}, its call having sent {@code sent} in citation order. */
    private static Map<ClusterSlot, SynthesisDoc> writtenFrom(RecordedCluster recorded, long... sent) {
        return Map.of(slotOf(recorded), writing(recorded.label().value(), "Both [1] and [2].", sent));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
