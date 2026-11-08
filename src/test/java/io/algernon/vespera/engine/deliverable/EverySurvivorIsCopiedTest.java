package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.columnByPath;
import static io.algernon.vespera.engine.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.engine.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
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

    /** The bytes stage 2 read where it read something other than what the archive holds now. */
    private static final byte[] WHAT_STAGE_2_READ = "the first survivor, as it was".getBytes(StandardCharsets.UTF_8);

    /** What the drive is left with at least after any copy: one gibibyte. */
    private static final long ONE_GIBIBYTE = 1L << 30;

    /** The page both survivors are listed on, in their cluster's folder. */
    private static final String THE_PAGE = THE_FOLDER + "/index.html";

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
    @Story("Copying never fills the drive the database is on")
    @DisplayName("A copy that would leave the drive with less than a gibibyte is not made, and a smaller one still is")
    void aCopyThatWouldLeaveLessThanAGibibyteIsNotMade(@TempDir Path base) throws IOException {
        Path archive = anArchive(base);
        long freeForTheFirstOnly = ONE_GIBIBYTE + FIRST_BYTES.length;

        Path tree = ListedTree.writeWithFreeSpace(
                base.resolve("work"), archive, List.of(THE_CLUSTER), writtenFromBoth(), bothSurvivors(sha256Of(FIRST_BYTES)),
                () -> freeForTheFirstOnly);

        assumeTrue(
                SECOND_BYTES.length > FIRST_BYTES.length,
                "the second document is the larger, so the room left for the first is too little for it");
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
