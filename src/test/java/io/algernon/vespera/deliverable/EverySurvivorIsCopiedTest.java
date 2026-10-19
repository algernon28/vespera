package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.columnByPath;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.manifestOf;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every survivor the tree lists is copied from the archive into its cluster folder, byte for byte, under its
 * original's own name (ADR-280), and kept only where its bytes hash to the key stage 2 recorded for it (ADR-274 §2).
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the working directory,
 * with two survivors of one cluster: occurrence 42, {@code reports/Q3 Report (final).PDF}, and occurrence 43, {@code
 * notes/risers.txt}, the second larger than the first.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class EverySurvivorIsCopiedTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The one cluster both survivors are in, written over from both. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    private static final String FIRST_PATH = "reports/Q3 Report (final).PDF";

    private static final byte[] FIRST_BYTES = "the first survivor".getBytes(StandardCharsets.UTF_8);

    /** The folder of the one cluster, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String THE_FOLDER = "1-fire-safety/1-fire-doors";

    /** The first survivor's copy: its original's own name, as written, in its cluster's folder. */
    private static final String FIRST_COPY = THE_FOLDER + "/Q3 Report (final).PDF";

    /** How the page links the first copy: beside it, its spaces and brackets percent-encoded. */
    private static final String FIRST_LINK = "href=\"./Q3%20Report%20%28final%29.PDF\"";

    private static final String SECOND_PATH = "notes/risers.txt";

    private static final byte[] SECOND_BYTES =
            "the second survivor, which holds more bytes than the first".getBytes(StandardCharsets.UTF_8);

    private static final String SECOND_COPY = THE_FOLDER + "/risers.txt";

    /** How the page links the second copy. */
    private static final String SECOND_LINK = "href=\"./risers.txt\"";

    /** The bytes stage 2 read where it read something other than what the archive holds now. */
    private static final byte[] WHAT_STAGE_2_READ = "the first survivor, as it was".getBytes(StandardCharsets.UTF_8);

    /** The manifest's header: its nine columns as before, then the copy and what became of it. */
    private static final List<String> THE_HEADER = List.of(
            "occurrence_id", "path", "content_hash", "winning_seed", "relevance_score", "seed_partition", "cluster",
            "partition_order", "cluster_order", "copy", "copy_outcome");

    /** What the drive is left with at least after any copy: one gibibyte. */
    private static final long ONE_GIBIBYTE = 1L << 30;

    /** The page both survivors are listed on, in their cluster's folder. */
    private static final String THE_PAGE = THE_FOLDER + "/index.html";

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("Each document is copied byte for byte into its group's folder under its own name, and its entry and its row lead to the copy")
    void copiesEverySurvivorByteForByteAndLinksToTheCopy(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);

        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "each survivor's copy is in its group's folder under its original's own name, and holds exactly the"
                        + " bytes of the file in the archive",
                () -> {
                    assertThat(tree.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES);
                    assertThat(tree.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES);
                });
        claim(
                "no part of a copy is left beside it",
                () -> assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part")));
        claim(
                "the group's page links each entry to its copy beside it, the name percent-encoded",
                () -> assertThat(Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8))
                        .contains(FIRST_LINK, SECOND_LINK));
        claim(
                "documents.csv keeps its nine columns and ends with the copy and what became of it",
                () -> assertThat(manifestOf(tree).getFirst()).containsExactlyElementsOf(THE_HEADER));
        claim(
                "and each row names its copy from the tree's root, in its group's folder, and says it was copied",
                () -> {
                    assertThat(columnByPath(tree, "copy"))
                            .containsExactly(Map.entry(FIRST_PATH, FIRST_COPY), Map.entry(SECOND_PATH, SECOND_COPY));
                    assertThat(columnByPath(tree, "copy_outcome"))
                            .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "copied"));
                });
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A document changed since it was read is not copied, says so, and the other is still copied")
    void aSurvivorChangedSinceItWasReadIsNotCopied(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);

        Path tree = writeOver(base, archive, sha256Of(WHAT_STAGE_2_READ));

        claim(
                "nothing stands at the changed document's copy, and no part of it is left",
                () -> {
                    assertThat(tree.resolve(FIRST_COPY)).doesNotExist();
                    assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part"));
                });
        claim(
                "its row names no copy and says it changed",
                () -> {
                    assertThat(columnByPath(tree, "copy")).containsEntry(FIRST_PATH, "");
                    assertThat(columnByPath(tree, "copy_outcome")).containsEntry(FIRST_PATH, "changed");
                });
        claim(
                "its entry links to no copy",
                () -> assertThat(Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8))
                        .doesNotContain(FIRST_LINK));
        claim(
                "and the other document is copied as ever: a bad copy is that document's, not the tree's",
                () -> assertThat(tree.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES));
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A document gone from the archive is listed as gone, and the other is still copied")
    void aSurvivorGoneFromTheArchiveIsListedAsGone(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Files.delete(archive.resolve(SECOND_PATH));

        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "the gone document's row names no copy and says it is gone, and the other's says it was copied",
                () -> {
                    assertThat(columnByPath(tree, "copy"))
                            .containsExactly(Map.entry(FIRST_PATH, FIRST_COPY), Map.entry(SECOND_PATH, ""));
                    assertThat(columnByPath(tree, "copy_outcome"))
                            .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "gone"));
                });
        claim("and the other's copy holds its bytes", () -> assertThat(tree.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES));
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A copy already in the tree is kept as it is, and the archive is not read for it again")
    void aCopyThatStandsIsKeptWithoutReadingTheArchive(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));
        FileTime firstWritten = Files.getLastModifiedTime(tree.resolve(FIRST_COPY));
        FileTime secondWritten = Files.getLastModifiedTime(tree.resolve(SECOND_COPY));
        Files.delete(archive.resolve(FIRST_PATH));
        Files.delete(archive.resolve(SECOND_PATH));

        writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "with both files gone from the archive, both rows still say copied: a copy that stands was put there"
                        + " only once its bytes were checked, so it is not checked again",
                () -> assertThat(columnByPath(tree, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "copied")));
        claim(
                "and both copies are the ones the first writing left, their bytes and their times untouched",
                () -> {
                    assertThat(tree.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES);
                    assertThat(tree.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES);
                    assertThat(List.of(
                                    Files.getLastModifiedTime(tree.resolve(FIRST_COPY)),
                                    Files.getLastModifiedTime(tree.resolve(SECOND_COPY))))
                            .containsExactly(firstWritten, secondWritten);
                });
    }

    @Test
    @Story("Copying never fills the drive the database is on")
    @DisplayName("A copy that would leave the drive with less than a gibibyte is not made, and a smaller one still is")
    void aCopyThatWouldLeaveLessThanAGibibyteIsNotMade(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        long freeForTheFirstOnly = ONE_GIBIBYTE + FIRST_BYTES.length;

        Path tree = ListedTree.writeWithFreeSpace(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(sha256Of(FIRST_BYTES)),
                () -> freeForTheFirstOnly);

        claim(
                "the precondition: the second document is the larger, so the room left for the first is too little"
                        + " for it",
                () -> assertThat(SECOND_BYTES.length).isGreaterThan(FIRST_BYTES.length));
        claim(
                "what the drive must keep free after a copy is one gibibyte",
                () -> assertThat(Deliverable.COPY_HEADROOM_BYTES).isEqualTo(ONE_GIBIBYTE));
        claim(
                "with a gibibyte and the first document's size free, the first is copied, leaving exactly a gibibyte,"
                        + " and the larger second is not, its row saying no-room",
                () -> assertThat(columnByPath(tree, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "no-room")));
        claim(
                "and nothing stands at the second's copy",
                () -> assertThat(tree.resolve(SECOND_COPY)).doesNotExist());
    }

    @Test
    @Story("The archive is only ever read")
    @DisplayName("Copying leaves every file of the archive, and its listing, exactly as they were")
    void theArchiveIsLeftAsItWas(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Map<String, String> before = everythingIn(archive);

        writeOver(base, archive, sha256Of(WHAT_STAGE_2_READ));

        claim(
                "every entry beneath the archive's root, folder or file, is there with the same bytes and the same"
                        + " time, and none was added, over a writing that copied one document and turned one down",
                () -> assertThat(everythingIn(archive)).containsExactlyEntriesOf(before));
    }

    @Test
    @Story("Every file of the tree has a name of bounded length")
    @DisplayName("A copy keeps its document's own name, cut to a hundred characters where it is longer, whatever the name holds")
    void namesEachCopyByItsOriginalsOwnName(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        String aNameWithNoLetterASlugKeeps = "a/Отчёт.txt";
        try {
            Files.createDirectories(archive.resolve(aNameWithNoLetterASlugKeeps).getParent());
            Files.write(archive.resolve(aNameWithNoLetterASlugKeeps), new byte[0]);
        } catch (InvalidPathException | IOException e) {
            abort("this file system, under this JVM's encoding of file names, will not create a Cyrillic name: "
                    + e.getMessage());
        }
        String theFolder = "1-fire-safety/1-four-names";
        Map<String, String> copyOf = new LinkedHashMap<>();
        copyOf.put(aNameWithNoLetterASlugKeeps, theFolder + "/Отчёт.txt");
        copyOf.put(
                "b/An extraordinarily long file name for an inspection report of the doors on every floor of the north"
                        + " tower.docx",
                theFolder + "/An extraordinarily long file name for an inspection report of the doors on every floor"
                        + " of the n.docx");
        copyOf.put("c/README", theFolder + "/README");
        copyOf.put("d/archive.tar.gz", theFolder + "/archive.tar.gz");
        List<ListedSurvivor> survivors = new ArrayList<>();
        long occurrence = 7;
        for (String path : copyOf.keySet()) {
            byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(archive.resolve(path).getParent());
            Files.write(archive.resolve(path), bytes);
            survivors.add(survivor(occurrence++, path, sha256Of(bytes), THE_SEED, SEED_PATH, 0, 0.5));
        }
        RecordedCluster four = cluster(THE_SEED, 0, "Four names", 1, survivors.size());

        Path tree = ListedTree.write(base.resolve("work"), archive, List.of(four), Map.of(), survivors, Map.of());

        claim(
                "each copy is in its group's folder under its original's own name: a Cyrillic name as written, a"
                        + " name of 110 characters cut to 100 with its extension kept, a name with no extension as it"
                        + " is, and a name with two extensions as written",
                () -> assertThat(columnByPath(tree, "copy")).containsExactlyEntriesOf(copyOf));
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A document whose original cannot be read as a file is not copied, says so, and the other is still copied")
    void aSurvivorThatCannotBeReadIsNotCopied(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Files.delete(archive.resolve(SECOND_PATH));
        Files.createDirectories(archive.resolve(SECOND_PATH));

        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "the precondition: a folder stands at the second document's path, so something is there and it is"
                        + " no file to read: the document is neither changed nor gone",
                () -> assertThat(archive.resolve(SECOND_PATH)).isDirectory());
        claim(
                "its row names no copy and says it could not be copied, and the other's says it was copied",
                () -> {
                    assertThat(columnByPath(tree, "copy"))
                            .containsExactly(Map.entry(FIRST_PATH, FIRST_COPY), Map.entry(SECOND_PATH, ""));
                    assertThat(columnByPath(tree, "copy_outcome"))
                            .containsExactly(Map.entry(FIRST_PATH, "copied"), Map.entry(SECOND_PATH, "not-copied"));
                });
        claim(
                "nothing stands at its copy's name, and no part of it is left",
                () -> {
                    assertThat(tree.resolve(SECOND_COPY)).doesNotExist();
                    assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part"));
                });
        claim(
                "its entry links to no copy and says, in italics, that it could not be copied",
                () -> assertThat(Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8))
                        .doesNotContain(SECOND_LINK)
                        .contains("<em>could not be copied</em>"));
        claim(
                "and the other document is copied as ever",
                () -> assertThat(tree.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES));
    }

    @Test
    @Story("Copying never fills the drive the database is on")
    @DisplayName("With no free space read, no copy is made and every document's row says no-room")
    void aDriveWithNoFreeSpaceReadTakesNoCopy(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);

        Path tree = ListedTree.writeWithFreeSpace(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(sha256Of(FIRST_BYTES)),
                () -> 0L);

        claim(
                "with 0 bytes free, which is what a free space that cannot be read counts as, both rows say no-room:"
                        + " a copy the drive may not hold is the one thing the bound is for",
                () -> assertThat(columnByPath(tree, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "no-room"), Map.entry(SECOND_PATH, "no-room")));
        claim(
                "and no copy stands in the group's folder, which holds its page alone",
                () -> assertThat(filesUnder(tree.resolve(THE_FOLDER))).containsExactly("index.html"));
    }

    /**
     * The shipped reading of the drive's free space, {@code Deliverable.usableBytesAt}, asked directly: no public
     * form of the writer can be given a working directory whose drive cannot be read and still write a tree there.
     */
    @Test
    @Story("Copying never fills the drive the database is on")
    @DisplayName("The tree's own reading of the drive's free space gives none where the free space cannot be read")
    void aFreeSpaceThatCannotBeReadCountsAsNone(@TempDir Path base) throws IOException {
        Path aFile = base.resolve("a-file");
        Files.writeString(aFile, "not a folder");
        Path beneathAFile = aFile.resolve("work");

        claim(
                "the precondition: the drive the test's own folder lies on is read as having room",
                () -> assertThat(Deliverable.usableBytesAt(base)).isPositive());
        claim(
                "and a working directory beneath a file, whose drive's free space cannot be read, is read as having"
                        + " 0 bytes free",
                () -> assertThat(Deliverable.usableBytesAt(beneathAFile)).isZero());
    }

    @Test
    @Story("Copying never fills the drive the database is on")
    @DisplayName("Before the first copy the tree counts what it is about to copy, a size it cannot read counting as none")
    void aSizeThatCannotBeReadCountsAsNoneInTheSurvey(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Files.delete(archive.resolve(FIRST_PATH));
        long theDrivesFreeBytes = 5 * ONE_GIBIBYTE;
        List<List<Long>> surveys = new ArrayList<>();
        DeliverableProgress survey = new DeliverableProgress() {
            @Override
            public void toCopySurvivors(long survivors, long bytes, long usableBytes) {
                surveys.add(List.of(survivors, bytes, usableBytes));
            }
        };

        Path tree = Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(sha256Of(FIRST_BYTES)), Map.of()),
                SurvivorPictures.none(),
                survey,
                ReplacingMove.ATOMIC,
                () -> theDrivesFreeBytes);

        claim(
                "the survey is made once, and counts both documents, neither of whose copies stood, the bytes of the"
                        + " one whose size could be read and none for the one gone, and the drive's free bytes",
                () -> assertThat(surveys)
                        .containsExactly(List.of(2L, (long) SECOND_BYTES.length, theDrivesFreeBytes)));
        claim(
                "and it decided nothing: each document met its own outcome at its own copy",
                () -> assertThat(columnByPath(tree, "copy_outcome"))
                        .containsExactly(Map.entry(FIRST_PATH, "gone"), Map.entry(SECOND_PATH, "copied")));
    }

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("A part of a copy a stopped writing left behind is written over, and the copy holds the document's bytes alone")
    void aPartLeftByAStoppedWritingIsWrittenOver(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        Path theFolder = base.resolve("work").resolve("deliverable").resolve(ListedTree.RUN_ID).resolve(THE_FOLDER);
        Files.createDirectories(theFolder);
        byte[] whatAStoppedWritingLeft =
                "a part longer than the document it was a part of, left when the invocation stopped"
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(theFolder.resolve("risers.txt.part"), whatAStoppedWritingLeft);

        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "the precondition: the part left behind is longer than the document, so a copy written into it"
                        + " without cutting it short would hold more than the document",
                () -> assertThat(whatAStoppedWritingLeft.length).isGreaterThan(SECOND_BYTES.length));
        claim(
                "the document is copied, and its copy holds exactly its bytes",
                () -> {
                    assertThat(columnByPath(tree, "copy_outcome")).containsEntry(SECOND_PATH, "copied");
                    assertThat(tree.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES);
                });
        claim(
                "and no part is left beside it",
                () -> assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part")));
    }

    /** The two survivors' files beneath {@code <base>/archive}, which is returned. */
    private static Path anArchive(Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.write(archive.resolve(FIRST_PATH), FIRST_BYTES);
        Files.write(archive.resolve(SECOND_PATH), SECOND_BYTES);
        return archive;
    }

    /** The tree written over {@code archive}, the first survivor's recorded key being {@code firstKey}. */
    private static Path writeOver(Path base, Path archive, String firstKey) {
        return ListedTree.write(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(firstKey), Map.of());
    }

    private static Map<ClusterSlot, SynthesisDoc> writtenFromBoth() {
        return Map.of(slotOf(THE_CLUSTER), writing("Fire doors", "Both [1] and [2].", 42, 43));
    }

    private static List<ListedSurvivor> bothSurvivors(String firstKey) {
        return List.of(
                survivor(42, FIRST_PATH, firstKey, THE_SEED, SEED_PATH, 0, 0.9),
                survivor(43, SECOND_PATH, sha256Of(SECOND_BYTES), THE_SEED, SEED_PATH, 0, 0.8));
    }

    /** Every entry beneath {@code root} by relative path: a folder as {@code folder}, a file as its digest and time. */
    private static Map<String, String> everythingIn(Path root) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path entry : walk.sorted().toList()) {
                String name = root.relativize(entry).toString().replace('\\', '/');
                entries.put(name, Files.isDirectory(entry)
                        ? "folder"
                        : sha256Of(Files.readAllBytes(entry)) + " " + Files.getLastModifiedTime(entry));
            }
        }
        return entries;
    }
}
