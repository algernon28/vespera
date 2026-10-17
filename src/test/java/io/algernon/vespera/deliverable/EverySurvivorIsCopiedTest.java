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

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
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
 * Every survivor the tree lists is copied from the archive into {@code documents/}, byte for byte, and kept only where
 * its bytes hash to the key stage 2 recorded for it (ADR-273 §2).
 *
 * <p>Each test lays out {@code <base>/archive} as the corpus root and {@code <base>/work} as the working directory,
 * with two survivors of one cluster: occurrence 42, {@code reports/Q3 Report (final).PDF}, and occurrence 43, {@code
 * notes/risers.txt}, the second larger than the first.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-273", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class EverySurvivorIsCopiedTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The one cluster both survivors are in, written over from both. */
    private static final RecordedCluster THE_CLUSTER = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    private static final String FIRST_PATH = "reports/Q3 Report (final).PDF";

    private static final byte[] FIRST_BYTES = "the first survivor".getBytes(StandardCharsets.UTF_8);

    /** The first survivor's copy: its occurrence, its stem as a slug, and its extension lower-cased. */
    private static final String FIRST_COPY = "documents/42-q3-report-final.pdf";

    private static final String SECOND_PATH = "notes/risers.txt";

    private static final byte[] SECOND_BYTES =
            "the second survivor, which holds more bytes than the first".getBytes(StandardCharsets.UTF_8);

    private static final String SECOND_COPY = "documents/43-risers.txt";

    /** The bytes stage 2 read where it read something other than what the archive holds now. */
    private static final byte[] WHAT_STAGE_2_READ = "the first survivor, as it was".getBytes(StandardCharsets.UTF_8);

    /** The manifest's header: its nine columns as before, then the copy and what became of it. */
    private static final List<String> THE_HEADER = List.of(
            "occurrence_id", "path", "content_hash", "winning_seed", "relevance_score", "seed_partition", "cluster",
            "partition_order", "cluster_order", "copy", "copy_outcome");

    /** What the drive is left with at least after any copy: one gibibyte. */
    private static final long ONE_GIBIBYTE = 1L << 30;

    /** The page both survivors are listed on. */
    private static final String THE_PAGE = "1-fire-safety/1-fire-doors.html";

    @Test
    @Story("Every document listed is copied into the tree")
    @DisplayName("Each document is copied byte for byte under a name of its own, and its entry and its row lead to the copy")
    void copiesEverySurvivorByteForByteAndLinksToTheCopy(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);

        Path tree = writeOver(base, archive, sha256Of(FIRST_BYTES));

        claim(
                "each survivor's copy is under documents/, named by its occurrence and its stem, and holds exactly the"
                        + " bytes of the file in the archive",
                () -> {
                    assertThat(tree.resolve(FIRST_COPY)).hasBinaryContent(FIRST_BYTES);
                    assertThat(tree.resolve(SECOND_COPY)).hasBinaryContent(SECOND_BYTES);
                });
        claim(
                "no part of a copy is left beside it",
                () -> assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".part")));
        claim(
                "the group's page links each entry to its copy, relatively, from the partition's folder",
                () -> assertThat(Files.readString(tree.resolve(THE_PAGE), StandardCharsets.UTF_8))
                        .contains("href=\"../" + FIRST_COPY + "\"", "href=\"../" + SECOND_COPY + "\""));
        claim(
                "documents.csv keeps its nine columns and ends with the copy and what became of it",
                () -> assertThat(manifestOf(tree).getFirst()).containsExactlyElementsOf(THE_HEADER));
        claim(
                "and each row names its copy from the tree's root and says it was copied",
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
                        .doesNotContain("href=\"../" + FIRST_COPY + "\""));
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
    @DisplayName("A copy is named by its occurrence and at most forty characters of its name, whatever the name holds")
    void namesEachCopyByItsOccurrenceAndAStemOfFortyCharactersAtMost(@TempDir Path base) throws IOException {
        Path archive = base.resolve("archive");
        Map<String, String> copyOf = new LinkedHashMap<>();
        copyOf.put("a/Отчёт.txt", "documents/7.txt");
        copyOf.put(
                "b/An extraordinarily long file name for an inspection report of the doors.docx",
                "documents/8-an-extraordinarily-long-file-name-for-an.docx");
        copyOf.put("c/README", "documents/9-readme");
        copyOf.put("d/archive.tar.gz", "documents/10-archive-tar.gz");
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
                "a name with no letter a slug keeps is its occurrence alone, a long one is cut to forty characters of"
                        + " slug, one with no extension has none, and only the last extension is kept as one",
                () -> assertThat(columnByPath(tree, "copy")).containsExactlyEntriesOf(copyOf));
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
