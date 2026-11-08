package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cluster-page-sizes.html}, which every drawing writes in the working directory, whatever the ceiling (ADR-303
 * §8): the key and how it reads, how many groups, their one-page sizes' largest, 95th percentile and median by nearest
 * rank, how many pass each of four marks, the ten largest with their entries and pages, and, where the ceiling is set,
 * what it split; in numbers and fixed words, and nothing that names a cluster, a partition, a document or a path.
 *
 * <p>The expected numbers are read off the tree the same drawing wrote, a page's size being its file's: the report's
 * claim is that it states the pages the operator is handed. Each tree is drawn over an empty archive, so every copy is
 * {@code gone}, and a group's size is made by the length of its documents' paths.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class TheClusterPageSizesReportTest {

    /** The report's name, in the working directory beside the other reports. */
    private static final String THE_REPORT = "cluster-page-sizes.html";

    /** The tree's index, which is put in place before the report. */
    private static final String THE_INDEX = "index.html";

    /** What a file is called while it is written beside its place. */
    private static final String WHILE_IT_IS_WRITTEN = ".part";

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The largest group's documents: entries of near 900 bytes, enough to pass a million bytes. */
    private static final int LARGEST_CLUSTERS_DOCUMENTS = 1_300;

    /** The second largest's: enough to pass a hundred thousand bytes and not a million. */
    private static final int SECOND_CLUSTERS_DOCUMENTS = 150;

    /** How many small groups there are, of 1 to this many documents each, one group of each size. */
    private static final int SMALL_CLUSTERS = 19;

    /** Every group: the two large ones, the small ones, and one of no document. */
    private static final int EVERY_CLUSTER = 2 + SMALL_CLUSTERS + 1;

    /** How many of the largest the report ranks. */
    private static final int RANKED = 10;

    /** The four marks the report counts the groups past, in bytes: powers of ten, none of them a value to set. */
    private static final List<Long> THE_MARKS = List.of(100_000L, 1_000_000L, 10_000_000L, 100_000_000L);

    /** A ceiling that splits the largest group and no other. */
    private static final long A_CEILING = 200_000;

    /** The labels the fixture's groups carry, the title its largest is written under, and words of its paths. */
    private static final List<String> NAMES_OF_THE_FIXTURE = List.of(
            "Hydrant valves", "Sprinkler heads", "Alarm panel", "Empty riser", "Inspection of hydrant valves",
            "Fire Safety", "fire-safety", "hydrant", "sprinkler", "reports/", "r/r/", ".docx");

    /** A row of a table of the report that states one number under a label. */
    private static final Pattern A_LABELLED_ROW =
            Pattern.compile("<tr><th>([^<]*)</th><td class=\"count\">([^<]*)</td></tr>");

    /** A row of the table of the largest: its rank, its bytes, its entries and its pages. */
    private static final Pattern A_RANKED_ROW = Pattern.compile(
            "<tr><td class=\"count\">(\\d+)</td><td class=\"count\">(\\d+)</td><td class=\"count\">(\\d+)</td>"
                    + "<td class=\"count\">(\\d+)</td></tr>");

    /** A row as the tests here compare it. */
    private record Row(String label, String value) {}

    /** A group as the tree the drawing wrote shows it: its page written whole, its entries, and its pages. */
    private record Drawn(long onePageSize, long entries, long pages) {}

    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("With no ceiling, the report states the groups, the largest, 95th percentile and median sizes, the four marks and the ten largest, and names nothing")
    void theReportStatesTheSizesTheMarksAndTheTenLargestInNumbersAlone(@TempDir Path base) throws IOException {
        Path tree = drawTheFixture(base, new ClusterPageCeiling.Unset());
        List<Drawn> drawn = drawnIn(tree);
        String report = Files.readString(base.resolve("work").resolve(THE_REPORT), StandardCharsets.UTF_8);
        List<Long> ascending = drawn.stream().map(Drawn::onePageSize).sorted().toList();

        claim(
                "the tree shows " + EVERY_CLUSTER + " groups, so every number below is read off each of their pages",
                () -> assertThat(drawn).hasSize(EVERY_CLUSTER));
        claim(
                "the report states, in this order: the key as unset; " + EVERY_CLUSTER + " groups; the largest page"
                        + " written whole; the 95th percentile and the median, each the size at rank ceil(p x n / 100)"
                        + " of the sizes in ascending order; and how many groups are larger than each of the four marks",
                () -> assertThat(labelledRowsOf(report)).containsExactly(
                        new Row("clusterPageByteCeiling", "unset"),
                        new Row("Groups", String.valueOf(EVERY_CLUSTER)),
                        new Row("Largest, in bytes", String.valueOf(ascending.getLast())),
                        new Row("95th percentile, in bytes", String.valueOf(nearestRank(ascending, 95))),
                        new Row("Median, in bytes", String.valueOf(nearestRank(ascending, 50))),
                        new Row("Larger than 100000 bytes", String.valueOf(largerThan(ascending, THE_MARKS.get(0)))),
                        new Row("Larger than 1000000 bytes", String.valueOf(largerThan(ascending, THE_MARKS.get(1)))),
                        new Row("Larger than 10000000 bytes", String.valueOf(largerThan(ascending, THE_MARKS.get(2)))),
                        new Row("Larger than 100000000 bytes", String.valueOf(largerThan(ascending, THE_MARKS.get(3))))));
        claim(
                "the fixture puts one group past a million bytes and two past a hundred thousand, so the marks are"
                        + " counted over pages that pass them",
                () -> assertThat(List.of(largerThan(ascending, THE_MARKS.get(0)), largerThan(ascending, THE_MARKS.get(1))))
                        .containsExactly(2L, 1L));
        claim(
                "the ten largest are ranked largest first, each with its size, its entries and the one page it was"
                        + " written as",
                () -> assertThat(rankedRowsOf(report)).containsExactlyElementsOf(theLargest(drawn)));
        claim(
                "the report names no group, title, seed, document or path the tree holds",
                () -> assertThat(report).doesNotContain(NAMES_OF_THE_FIXTURE));
        namesNoPathOfTheDrawing(report, base, tree);
        claim(
                "and it is in place, beside the other reports, with no file left under the name it was written with",
                () -> assertThat(base.resolve("work").resolve(THE_REPORT + ".part")).doesNotExist());
    }

    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("With a ceiling set, the report states it, how many groups it split, how many pages were written and how many are larger than it")
    void aSetCeilingIsStatedWithWhatItSplit(@TempDir Path base) throws IOException {
        List<Drawn> whole = drawnIn(drawTheFixture(base.resolve("whole"), new ClusterPageCeiling.Unset()));
        Path tree = drawTheFixture(base.resolve("split"), new ClusterPageCeiling.Bytes(A_CEILING));
        List<Drawn> drawn = withThePagesOf(whole, drawnIn(tree));
        String report = Files.readString(base.resolve("split").resolve("work").resolve(THE_REPORT), StandardCharsets.UTF_8);
        List<Row> rowsWithNoCeiling = labelledRowsOf(
                Files.readString(base.resolve("whole").resolve("work").resolve(THE_REPORT), StandardCharsets.UTF_8));
        List<Row> sizeRowsWithNoCeiling = rowsWithNoCeiling.subList(1, rowsWithNoCeiling.size());
        long pagesWritten = everyPageOf(tree).size();
        long pagesLarger = everyPageOf(tree).stream().filter(page -> sizeOf(page) > A_CEILING).count();
        long largestClustersPages = drawn.stream().mapToLong(Drawn::pages).max().orElseThrow();

        claim(
                "a ceiling of " + A_CEILING + " bytes splits the largest group alone, into pages none of which is larger"
                        + " than it, every entry being far smaller",
                () -> assertThat(List.of(drawn.stream().filter(cluster -> cluster.pages() > 1).count(), pagesLarger))
                        .containsExactly(1L, 0L));
        claim(
                "the report states the ceiling, the sizes the groups' pages take written whole, the same as with no"
                        + " ceiling, and then the groups it split, the pages written in all and those larger than it",
                () -> {
                    List<Row> expected = new ArrayList<>();
                    expected.add(new Row("clusterPageByteCeiling", String.valueOf(A_CEILING)));
                    expected.addAll(sizeRowsWithNoCeiling);
                    expected.add(new Row("Groups split", "1"));
                    expected.add(new Row("Pages written", String.valueOf(pagesWritten)));
                    expected.add(new Row("Pages larger than the ceiling", String.valueOf(pagesLarger)));
                    assertThat(labelledRowsOf(report)).containsExactlyElementsOf(expected);
                });
        claim(
                "the largest ranks first with the " + largestClustersPages + " pages it was written as, and every other of"
                        + " the ten with one",
                () -> assertThat(rankedRowsOf(report)).containsExactlyElementsOf(theLargest(drawn)));
        claim(
                "and it still names nothing of the tree",
                () -> assertThat(report).doesNotContain(NAMES_OF_THE_FIXTURE));
        namesNoPathOfTheDrawing(report, base.resolve("split"), tree);
    }

    /**
     * The claim that {@code report} names no path of its drawing: not the tree, the folders it was drawn under, the
     * working directory or the run's folder, and no separator of a path in any text outside its tags.
     */
    private static void namesNoPathOfTheDrawing(String report, Path base, Path tree) {
        String text = report.replaceAll("(?s)<[^>]*>", "");
        claim(
                "the report names no path of the drawing: neither the tree, the folders it was drawn under, the working"
                        + " directory nor the run's folder, and its text outside the markup holds no path separator",
                () -> {
                    assertThat(report).doesNotContain(
                            tree.toString(), base.toString(), base.resolve("work").toString(),
                            tree.getFileName().toString());
                    assertThat(text).doesNotContain("/", "\\");
                });
    }

    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("A ceiling that is ignored is stated as ignored, and nothing is said of splitting")
    void anIgnoredCeilingIsStatedAsIgnored(@TempDir Path base) throws IOException {
        drawTheFixture(base, new ClusterPageCeiling.Ignored());
        String report = Files.readString(base.resolve("work").resolve(THE_REPORT), StandardCharsets.UTF_8);

        claim(
                "the key reads as ignored",
                () -> assertThat(labelledRowsOf(report)).first().isEqualTo(new Row("clusterPageByteCeiling", "ignored")));
        claim(
                "and with no ceiling read, no row says what one split",
                () -> assertThat(labelledRowsOf(report)).extracting(Row::label)
                        .doesNotContain("Groups split", "Pages written", "Pages larger than the ceiling"));
    }

    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("A tree of no group reports no group, none for each size, nothing past any mark and no row of the largest")
    void aTreeWithNoClusterStatesNone(@TempDir Path base) throws IOException {
        ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(), Map.of(), List.of(), Map.of(),
                SurvivorPictures.none(), new ClusterPageCeiling.Unset(), DeliverableProgress.NONE);
        String report = Files.readString(base.resolve("work").resolve(THE_REPORT), StandardCharsets.UTF_8);

        claim(
                "the report states 0 groups, none for the largest, the 95th percentile and the median, and 0 past each"
                        + " of the four marks",
                () -> assertThat(labelledRowsOf(report)).containsExactly(
                        new Row("clusterPageByteCeiling", "unset"),
                        new Row("Groups", "0"),
                        new Row("Largest, in bytes", "none"),
                        new Row("95th percentile, in bytes", "none"),
                        new Row("Median, in bytes", "none"),
                        new Row("Larger than 100000 bytes", "0"),
                        new Row("Larger than 1000000 bytes", "0"),
                        new Row("Larger than 10000000 bytes", "0"),
                        new Row("Larger than 100000000 bytes", "0")));
        claim(
                "and no row of the largest",
                () -> assertThat(rankedRowsOf(report)).isEmpty());
    }

    /**
     * The report is written beside its place and moved there by the drawing's own move, after the tree's index (ADR-303
     * §8, ADR-286 §3): a report written straight to its name could be read half written.
     */
    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("The report is written beside its place and moved there after the tree's index, and nothing is left beside it")
    void theReportIsMovedIntoPlaceAfterTheIndex(@TempDir Path base) throws IOException {
        Path work = base.resolve("work");
        List<String> movedInto = new ArrayList<>();
        ReplacingMove recording = new ReplacingMove(
                (from, to) -> {
                    if (to.equals(work.resolve(THE_REPORT)) || to.equals(treeUnder(base).resolve(THE_INDEX))) {
                        movedInto.add(from.getFileName() + " to " + to.getFileName());
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        drawASmallTree(base, recording);

        claim(
                "the tree's index is moved into place first, then the report from the name it was written under, its"
                        + " own name with .part after it",
                () -> assertThat(movedInto).containsExactly(
                        THE_INDEX + WHILE_IT_IS_WRITTEN + " to " + THE_INDEX,
                        THE_REPORT + WHILE_IT_IS_WRITTEN + " to " + THE_REPORT));
        claim(
                "the report stands in the working directory, and nothing is left under the name it was written under",
                () -> {
                    assertThat(work.resolve(THE_REPORT)).isRegularFile();
                    assertThat(work.resolve(THE_REPORT + WHILE_IT_IS_WRITTEN)).doesNotExist();
                });
    }

    /**
     * A report that cannot be put in place fails nothing, and its {@code .part} is removed (ADR-303 §8): the tree is
     * already in place, and the report is the operator's evidence, not part of it.
     */
    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("A report whose move is refused fails nothing: the tree is drawn, no report stands, and nothing is left beside its place")
    void aReportThatCannotBeMovedFailsNothingAndLeavesNoPart(@TempDir Path base) {
        Path work = base.resolve("work");
        ReplacingMove refusingTheReport = new ReplacingMove(
                (from, to) -> {
                    if (to.equals(work.resolve(THE_REPORT))) {
                        throw new IOException("the report's place refuses it");
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Throwable thrown = catchThrowable(() -> drawASmallTree(base, refusingTheReport));
        Path tree = treeUnder(base);

        claim(
                "the drawing throws nothing",
                () -> assertThat(thrown).isNull());
        claim(
                "the tree's index is in place",
                () -> assertThat(tree.resolve(THE_INDEX)).isRegularFile());
        claim(
                "no report stands in the working directory, and no file is left under the name it was written under",
                () -> {
                    assertThat(work.resolve(THE_REPORT)).doesNotExist();
                    assertThat(work.resolve(THE_REPORT + WHILE_IT_IS_WRITTEN)).doesNotExist();
                });
    }

    /**
     * A drawing that fails writes no report (ADR-303 §8): the report states the tree the operator is handed, and a
     * drawing that fails hands none.
     */
    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("A drawing that fails writes no report, nor anything beside its place")
    void aDrawingThatFailsWritesNoReport(@TempDir Path base) {
        Path work = base.resolve("work");
        ReplacingMove refusingTheIndex = new ReplacingMove(
                (from, to) -> {
                    if (to.equals(treeUnder(base).resolve(THE_INDEX))) {
                        throw new IOException("the index's place refuses it");
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Throwable thrown = catchThrowable(() -> drawASmallTree(base, refusingTheIndex));

        claim(
                "the drawing fails, its index refused",
                () -> assertThat(thrown).isInstanceOf(java.io.UncheckedIOException.class));
        claim(
                "and no report stands in the working directory, nor a file under the name it is written under",
                () -> {
                    assertThat(work.resolve(THE_REPORT)).doesNotExist();
                    assertThat(work.resolve(THE_REPORT + WHILE_IT_IS_WRITTEN)).doesNotExist();
                });
    }

    /** Where {@link #drawASmallTree} draws its tree under {@code base}. */
    private static Path treeUnder(Path base) {
        return DeliverableRoot.UNNAMED.treeOf(base.resolve("work"), ListedTree.provenance(base.resolve("archive")).runId());
    }

    /** One group of two short documents, written over, drawn under {@code base} with no ceiling, put in place by {@code moves}. */
    private static Path drawASmallTree(Path base, ReplacingMove moves) {
        RecordedCluster small = cluster(THE_SEED, 0, "Alarm panel", 1, 2);
        long[] next = {1_000};
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(small),
                Map.of(slotOf(small), writing("Alarm panel", "Panels [1] and bells [2].", 1_000, 1_001)),
                members(0, 2, false, next), Map.of(), new ClusterPageCeiling.Unset(), DeliverableProgress.NONE, moves);
    }

    /**
     * The fixture, drawn under {@code base}: a group of {@value #LARGEST_CLUSTERS_DOCUMENTS} long-pathed documents written
     * over, one of {@value #SECOND_CLUSTERS_DOCUMENTS} nothing was written over, {@value #SMALL_CLUSTERS} small groups of 1
     * to {@value #SMALL_CLUSTERS} documents, and one of none.
     */
    private static Path drawTheFixture(Path base, ClusterPageCeiling ceiling) {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        long[] next = {1_000};
        RecordedCluster largest = cluster(THE_SEED, 0, "Hydrant valves", 1, LARGEST_CLUSTERS_DOCUMENTS);
        arrangement.add(largest);
        survivors.addAll(members(0, LARGEST_CLUSTERS_DOCUMENTS, true, next));
        arrangement.add(cluster(THE_SEED, 1, "Sprinkler heads", 2, SECOND_CLUSTERS_DOCUMENTS));
        survivors.addAll(members(1, SECOND_CLUSTERS_DOCUMENTS, true, next));
        for (int small = 1; small <= SMALL_CLUSTERS; small++) {
            arrangement.add(cluster(THE_SEED, 1 + small, "Alarm panel " + small, 2 + small, small));
            survivors.addAll(members(1 + small, small, false, next));
        }
        arrangement.add(cluster(THE_SEED, 2 + SMALL_CLUSTERS, "Empty riser", 3 + SMALL_CLUSTERS, 0));
        Map<ClusterSlot, SynthesisDoc> written =
                Map.of(slotOf(largest), writing("Inspection of hydrant valves", "Valves [1] and seals [2].", 1_000, 1_001));
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), arrangement, written, survivors, Map.of(),
                SurvivorPictures.none(), ceiling, DeliverableProgress.NONE);
    }

    /** {@code count} survivors of the cluster of {@code ordinal}, their paths near 800 characters where {@code long}. */
    private static List<ListedSurvivor> members(int ordinal, int count, boolean longPaths, long[] next) {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            long occurrence = next[0]++;
            String path = (longPaths ? "r/".repeat(395) : "reports/") + occurrence + ".docx";
            members.add(survivor(occurrence, path, "0".repeat(64), THE_SEED, SEED_PATH, ordinal, 0.9 - at * 1e-4));
        }
        return members;
    }

    /** Each cluster folder of {@code tree}, in the order the tree places them, as its pages show it. */
    private static List<Drawn> drawnIn(Path tree) throws IOException {
        List<Drawn> drawn = new ArrayList<>();
        for (Path folder : clusterFoldersOf(tree)) {
            List<Path> pages = pagesOf(folder);
            long entries = pages.stream()
                    .mapToLong(page -> Pattern.compile("<li id=\"document-").matcher(textOf(page)).results().count())
                    .sum();
            long onePageSize = pages.size() == 1 ? sizeOf(pages.getFirst()) : pages.stream().mapToLong(page -> sizeOf(page)).sum();
            drawn.add(new Drawn(onePageSize, entries, pages.size()));
        }
        return drawn;
    }

    /**
     * The groups of a drawing with a ceiling as the report states them: each one-page size and its entries read off
     * {@code whole}, the same fixture drawn with no ceiling, and the pages off the drawing itself, folder by folder.
     */
    private static List<Drawn> withThePagesOf(List<Drawn> whole, List<Drawn> split) {
        List<Drawn> stated = new ArrayList<>();
        for (int at = 0; at < whole.size(); at++) {
            stated.add(new Drawn(whole.get(at).onePageSize(), whole.get(at).entries(), split.get(at).pages()));
        }
        return stated;
    }

    /** The cluster folders of {@code tree}, a partition's folders in their order and the partitions in theirs. */
    private static List<Path> clusterFoldersOf(Path tree) throws IOException {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> partitions = Files.list(tree)) {
            // The Confluence rendering's folder is not a seed partition's (ADR-312 §1), its plugin's (ADR-333 §1).
            for (Path partition : partitions.filter(Files::isDirectory)
                    .filter(folder -> !folder.getFileName().toString().equals("confluence"))
                    .sorted()
                    .toList()) {
                try (Stream<Path> inside = Files.list(partition)) {
                    folders.addAll(inside.filter(Files::isDirectory).sorted().toList());
                }
            }
        }
        return folders;
    }

    /** The pages of one cluster folder. */
    private static List<Path> pagesOf(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().matches("index(-[1-9]\\d*)?\\.html")).sorted().toList();
        }
    }

    /** Every page of every cluster folder of {@code tree}. */
    private static List<Path> everyPageOf(Path tree) throws IOException {
        List<Path> pages = new ArrayList<>();
        for (Path folder : clusterFoldersOf(tree)) {
            pages.addAll(pagesOf(folder));
        }
        return pages;
    }

    /** The ten largest of {@code drawn}, largest first, ties in the tree's order, as the report ranks them. */
    private static List<List<Long>> theLargest(List<Drawn> drawn) {
        List<Drawn> ranked = new ArrayList<>(drawn);
        ranked.sort(Comparator.comparingLong(Drawn::onePageSize).reversed());
        List<List<Long>> rows = new ArrayList<>();
        for (int rank = 1; rank <= Math.min(RANKED, ranked.size()); rank++) {
            Drawn cluster = ranked.get(rank - 1);
            rows.add(List.of((long) rank, cluster.onePageSize(), cluster.entries(), cluster.pages()));
        }
        return rows;
    }

    /** The value at rank ceil(p x n / 100) of {@code ascending}, counted from 1. */
    private static long nearestRank(List<Long> ascending, int percentile) {
        int rank = (int) Math.ceil(percentile * ascending.size() / 100.0);
        return ascending.get(rank - 1);
    }

    private static long largerThan(List<Long> sizes, long mark) {
        return sizes.stream().filter(size -> size > mark).count();
    }

    private static List<Row> labelledRowsOf(String report) {
        return A_LABELLED_ROW.matcher(report).results().map(row -> new Row(row.group(1), row.group(2))).toList();
    }

    private static List<List<Long>> rankedRowsOf(String report) {
        return A_RANKED_ROW.matcher(report).results()
                .map(row -> List.of(
                        Long.parseLong(row.group(1)), Long.parseLong(row.group(2)), Long.parseLong(row.group(3)),
                        Long.parseLong(row.group(4))))
                .toList();
    }

    private static String textOf(Path page) {
        try {
            return Files.readString(page, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long sizeOf(Path page) {
        try {
            return Files.size(page);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
