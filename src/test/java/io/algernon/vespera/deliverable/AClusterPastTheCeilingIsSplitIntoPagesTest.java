package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster's page past the profile's byte ceiling is split at entries' ends into {@code index.html} and {@code
 * index-<k>.html} (ADR-303 §3 to §5): unset, at or under the ceiling, or for a cluster of no listed member or of one
 * entry, the page is the one ADR-286 writes, byte for byte; past it, the pages are filled greedily in membership order,
 * each within the ceiling but a page holding one entry, every entry on exactly one page with its id and its number,
 * the navigation above and below the list, the writing on the first page alone, the further pages moved into place
 * before the first, and a page an earlier drawing wrote past the last counted and kept.
 *
 * <p>Every drawing here lays out {@code <base>/archive}, which holds nothing, so every copy is {@code gone} and every
 * entry is a path and a reason: what this class claims is about the pages. The expected pages are built from the page
 * written whole, so a claim reads the bytes the writer itself wrote for each entry. Citations across pages are {@code
 * ACitationLinksToThePageItsEntryIsOnTest}'s; the links of a split tree, {@code TheTreeIsHtmlTest}'s; the report,
 * {@code TheClusterPageSizesReportTest}'s.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class AClusterPastTheCeilingIsSplitIntoPagesTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The cluster written over, placed first, of three documents. */
    private static final RecordedCluster DOORS = cluster(THE_SEED, 0, "Fire doors", 1, 3);

    /** Its folder, from the tree's root: the partition's place and slug, then the cluster's. */
    private static final String DOORS_FOLDER = "1-fire-safety/1-fire-doors";

    /** The writing over the first cluster, citing each of its three documents. */
    private static final SynthesisDoc DOORS_WRITING =
            writing("Fire doors and their inspection", "Doors [1], frames [2] and closers [3].", 10, 11, 12);

    /** The first cluster's three documents, none of them in the archive. */
    private static final List<ListedSurvivor> DOORS_MEMBERS = List.of(
            survivor(10, "reports/a.docx", "0".repeat(64), THE_SEED, SEED_PATH, 0, 0.9),
            survivor(11, "reports/b.docx", "1".repeat(64), THE_SEED, SEED_PATH, 0, 0.8),
            survivor(12, "reports/c.docx", "2".repeat(64), THE_SEED, SEED_PATH, 0, 0.7));

    /** The smallest ceiling there is: every page past it, so every cluster of two entries or more is split. */
    private static final ClusterPageCeiling ONE_BYTE = new ClusterPageCeiling.Bytes(1);

    private static final ClusterPageCeiling UNSET = new ClusterPageCeiling.Unset();

    /** What heads every page's list. */
    private static final String THE_LIST_HEADING = "<h2>The documents in this group</h2>\n";

    /** How long a long document's path is, in characters: an entry of near 5,000 bytes. */
    private static final int A_LONG_PATH = 4_900;

    /** A cluster's list entry, from its opening tag to the line break after its close. */
    private static final Pattern AN_ENTRY = Pattern.compile("(?s)<li id=\"document-(\\d+)\">.*?</li>\n");

    /** A page of a cluster folder, and the number its name gives it. */
    private static final Pattern A_PAGE_NAME = Pattern.compile("index(?:-([2-9]|[1-9]\\d+))?\\.html");

    /** What a page is called while it is written beside its place. */
    private static final String WHILE_IT_IS_WRITTEN = ".part";

    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("Pages take entries in order while they stay within the ceiling, so five long entries make pages of two, two and one")
    void pagesAreFilledGreedilyAndEachIsWithinTheCeiling(@TempDir Path base) throws IOException {
        List<ListedSurvivor> members = longMembers(A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH);
        String whole = read(folderOf(drawLong(base.resolve("whole"), members, UNSET), DOORS_FOLDER).resolve("index.html"));
        long ceiling = twoAndAHalfEntriesAbove(whole);

        List<Path> pages = pagesIn(folderOf(
                drawLong(base.resolve("split"), members, new ClusterPageCeiling.Bytes(ceiling)), DOORS_FOLDER));

        claim(
                "with a ceiling of the page's fixed bytes and two and a half entries, " + ceiling + " bytes, the first"
                        + " page takes entries 1 and 2, the third would pass it; the second takes 3 and 4; the third, 5",
                () -> assertThat(pages.stream().map(page -> numbersOn(read(page))).toList())
                        .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5)));
        for (Path page : pages) {
            claim(
                    page.getFileName() + " is within the ceiling of " + ceiling + " bytes",
                    () -> assertThat(Files.size(page)).isLessThanOrEqualTo(ceiling));
        }
    }

    /** The smallest ceiling at which {@code entrySizes} are planned as two pages and not three. */
    private static long smallestCeilingOfTwoPages(RecordedCluster unwritten, long[] entrySizes) {
        return smallestCeilingOfTwoPages(unwritten, entrySizes, DeliverableLanguage.ENGLISH);
    }

    /** As above, the cluster's page in {@code language}. */
    private static long smallestCeilingOfTwoPages(
            RecordedCluster unwritten, long[] entrySizes, DeliverableLanguage language) {
        return smallestCeilingOf(2, entrySizes,
                ceiling -> ClusterPage.plan(ceiling, unwritten, null, null, entrySizes, false, language));
    }

    /** The smallest ceiling at which {@code plan} makes {@code pages} pages or fewer of {@code entrySizes}. */
    private static long smallestCeilingOf(
            int pages, long[] entrySizes, LongFunction<Optional<ClusterPage.PageBreaks>> plan) {
        long low = 1;
        long high = Arrays.stream(entrySizes).sum() + 999_999;
        while (low < high) {
            long middle = (low + high) / 2;
            int planned = plan.apply(middle).map(ClusterPage.PageBreaks::pages).orElse(1);
            if (planned <= pages) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("Every entry is on exactly one page, in order, each list numbered on from the page before and each entry keeping its id")
    void everyEntryIsOnExactlyOnePageInOrderNumberedOnWithItsId(@TempDir Path base) throws IOException {
        List<ListedSurvivor> members = longMembers(A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH);
        String whole = read(folderOf(drawLong(base.resolve("whole"), members, UNSET), DOORS_FOLDER).resolve("index.html"));
        List<String> pages = pagesIn(folderOf(
                        drawLong(base.resolve("split"), members, new ClusterPageCeiling.Bytes(twoAndAHalfEntriesAbove(whole))),
                        DOORS_FOLDER))
                .stream()
                .map(AClusterPastTheCeilingIsSplitIntoPagesTest::read)
                .toList();
        List<String> entriesOnThePages = pages.stream().flatMap(page -> entriesOf(page).stream()).toList();

        claim(
                "the entries of the pages, read in page order, are the entries of the page written whole, each once, in"
                        + " its order and byte for byte as it was written there, its id document-n among them",
                () -> assertThat(entriesOnThePages).containsExactlyElementsOf(entriesOf(whole)));
        claim(
                "the first page's list opens with no start, as a page written whole does, and each further page's"
                        + " starts at its first entry's number, 3 and then 5",
                () -> assertThat(pages.stream().map(AClusterPastTheCeilingIsSplitIntoPagesTest::listOpening).toList())
                        .containsExactly("<ol>", "<ol start=\"3\">", "<ol start=\"5\">"));
    }

    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("A group's further pages are put in place before its first, and no page is left half written")
    void theFurtherPagesAreMovedIntoPlaceBeforeTheFirst(@TempDir Path base) throws IOException {
        List<String> movedIntoTheFolder = new ArrayList<>();
        ReplacingMove recording = new ReplacingMove(
                (from, to) -> {
                    if (to.getParent().endsWith(DOORS_FOLDER) && to.getFileName().toString().endsWith(".html")) {
                        movedIntoTheFolder.add(to.getFileName().toString());
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Path tree = ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(DOORS), Map.of(slotOf(DOORS), DOORS_WRITING),
                DOORS_MEMBERS, Map.of(), ONE_BYTE, DeliverableProgress.NONE, recording);

        claim(
                "the second and third pages are moved into place, in page order, and the first page last, so a reader"
                        + " who reaches it finds the pages it links",
                () -> assertThat(movedIntoTheFolder).containsExactly("index-2.html", "index-3.html", "index.html"));
        claim(
                "and no file is left in the tree under the name a page is written beside its place with",
                () -> assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(WHILE_IT_IS_WRITTEN)));
    }

    /** The first cluster over {@code members}, its writing citing the first and the last, drawn under {@code base}. */
    private static Path drawLong(Path base, List<ListedSurvivor> members, ClusterPageCeiling ceiling) {
        RecordedCluster doors = cluster(THE_SEED, 0, "Fire doors", 1, members.size());
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(doors), Map.of(slotOf(doors), longWriting(members)),
                members, Map.of(), SurvivorPictures.none(), ceiling, DeliverableProgress.NONE);
    }

    /** As {@link #drawLong(Path, List, ClusterPageCeiling)}, the deliverable in {@code language} (ADR-321). */
    private static Path drawLong(
            Path base, List<ListedSurvivor> members, ClusterPageCeiling ceiling, DeliverableLanguage language) {
        RecordedCluster doors = cluster(THE_SEED, 0, "Fire doors", 1, members.size());
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(base.resolve("archive")),
                ListedTree.of(List.of(doors), Map.of(slotOf(doors), longWriting(members)), members, Map.of(), false,
                        language),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                ceiling,
                DeliverableProgress.NONE);
    }

    /** The writing over the first cluster of {@code members}, citing the first and the last. */
    private static SynthesisDoc longWriting(List<ListedSurvivor> members) {
        long[] sent = members.stream().mapToLong(member -> member.occurrence().value()).toArray();
        return writing("Fire doors and their inspection", "Doors [1] and closers [" + members.size() + "].", sent);
    }

    /** Members of the first cluster whose paths are as long as {@code lengths}, in that order, scored highest first. */
    private static List<ListedSurvivor> longMembers(int... lengths) {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 0; at < lengths.length; at++) {
            String name = (100 + at) + ".docx";
            String path = "r/".repeat((lengths[at] - name.length()) / 2) + name;
            members.add(survivor(100 + at, path, String.valueOf(at).repeat(64), THE_SEED, SEED_PATH, 0, 0.9 - at * 0.1));
        }
        return members;
    }

    /**
     * A ceiling of {@code whole}'s bytes outside its entries, two of its shortest entries and half of one more: the
     * first page holds two entries and not three, and the navigation it gains, a few hundred bytes, is far within the
     * half entry.
     */
    private static long twoAndAHalfEntriesAbove(String whole) {
        List<String> entries = entriesOf(whole);
        long entryBytes = entries.stream().mapToLong(entry -> entry.getBytes(StandardCharsets.UTF_8).length).sum();
        long shortest = entries.stream().mapToLong(entry -> entry.getBytes(StandardCharsets.UTF_8).length).min().orElseThrow();
        long fixed = whole.getBytes(StandardCharsets.UTF_8).length - entryBytes;
        return fixed + 2 * shortest + shortest / 2;
    }

    /** Every page of a cluster folder, the first first and the others in the order of their numbers. */
    private static List<Path> pagesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> A_PAGE_NAME.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(AClusterPastTheCeilingIsSplitIntoPagesTest::pageNumber))
                    .toList();
        }
    }

    private static int pageNumber(Path page) {
        Matcher name = A_PAGE_NAME.matcher(page.getFileName().toString());
        if (!name.matches()) {
            throw new IllegalStateException(page + " is not a page");
        }
        return name.group(1) == null ? 1 : Integer.parseInt(name.group(1));
    }

    /** Every list entry of {@code page}, in order, each from its opening tag to the line break after its close. */
    private static List<String> entriesOf(String page) {
        return AN_ENTRY.matcher(page).results().map(entry -> entry.group()).toList();
    }

    /** The numbers of the entries {@code page} lists, in order. */
    private static List<Integer> numbersOn(String page) {
        return AN_ENTRY.matcher(page).results().map(entry -> Integer.parseInt(entry.group(1))).toList();
    }

    /** The tag a page's list opens with. */
    private static String listOpening(String page) {
        int at = page.indexOf(THE_LIST_HEADING) + THE_LIST_HEADING.length();
        return page.substring(at, page.indexOf('\n', at));
    }

    private static Path folderOf(Path tree, String folder) {
        return tree.resolve(folder);
    }

    private static String read(Path page) {
        try {
            return Files.readString(page, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long size(Path page) {
        try {
            return Files.size(page);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
