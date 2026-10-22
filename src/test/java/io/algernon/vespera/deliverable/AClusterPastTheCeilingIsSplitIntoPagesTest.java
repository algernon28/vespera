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
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
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

    /** The cluster nothing could be sent for, placed second, of two documents. */
    private static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, "Smoke dampers", 2, 2);

    private static final String DAMPERS_FOLDER = "1-fire-safety/2-smoke-dampers";

    /** The writing over the first cluster, citing each of its three documents. */
    private static final SynthesisDoc DOORS_WRITING =
            writing("Fire doors and their inspection", "Doors [1], frames [2] and closers [3].", 10, 11, 12);

    /** The first cluster's three documents, none of them in the archive. */
    private static final List<ListedSurvivor> DOORS_MEMBERS = List.of(
            survivor(10, "reports/a.docx", "0".repeat(64), THE_SEED, SEED_PATH, 0, 0.9),
            survivor(11, "reports/b.docx", "1".repeat(64), THE_SEED, SEED_PATH, 0, 0.8),
            survivor(12, "reports/c.docx", "2".repeat(64), THE_SEED, SEED_PATH, 0, 0.7));

    /** The second cluster's two documents. */
    private static final List<ListedSurvivor> DAMPERS_MEMBERS = List.of(
            survivor(20, "reports/d.docx", "3".repeat(64), THE_SEED, SEED_PATH, 1, 0.6),
            survivor(21, "reports/e.docx", "4".repeat(64), THE_SEED, SEED_PATH, 1, 0.5));

    /**
     * The first cluster's page as it was written before a page could be split, byte for byte: what every drawing with
     * the ceiling unset still writes.
     */
    private static final String THE_PAGE_AS_IT_WAS_WRITTEN = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <title>Fire doors and their inspection</title>
            <style>
            body { font-family: sans-serif; margin: 2em; max-width: 900px; }
            table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }
            td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }
            td.count { text-align: right; }
            code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: #eee; }
            blockquote { color: #444; font-style: italic; margin: 0.4em 0 0 1em; }
            li { margin-bottom: 0.6em; }
            img { max-width: 100%; }
            </style>
            </head>
            <body>
            <h1>Fire doors and their inspection</h1>
            <div class="writing" title="What the local model wrote over this group from the documents listed below it; each number in brackets is a link to the document it cites.">
            <p>Doors <a href="#document-1">[1]</a>, frames <a href="#document-2">[2]</a> and closers <a href="#document-3">[3]</a>.</p>
            </div>
            <h2>The documents in this group</h2>
            <ol>
            <li id="document-1">
            reports/a.docx <em>no longer in the archive, so it was not copied</em>
            </li>
            <li id="document-2">
            reports/b.docx <em>no longer in the archive, so it was not copied</em>
            </li>
            <li id="document-3">
            reports/c.docx <em>no longer in the archive, so it was not copied</em>
            </li>
            </ol>
            </body>
            </html>
            """;

    /** The smallest ceiling there is: every page past it, so every cluster of two entries or more is split. */
    private static final ClusterPageCeiling ONE_BYTE = new ClusterPageCeiling.Bytes(1);

    private static final ClusterPageCeiling UNSET = new ClusterPageCeiling.Unset();

    /** The navigation of each page of a cluster of three pages, and of two, as each page carries it twice. */
    private static final String PAGE_1_OF_3 = "<p class=\"pages\">Page 1 of 3: <a href=\"./index-2.html\">next</a>,"
            + " <a href=\"./index-3.html\">last</a></p>\n";

    private static final String PAGE_2_OF_3 = "<p class=\"pages\">Page 2 of 3: <a href=\"./index.html\">first</a>,"
            + " <a href=\"./index.html\">previous</a>, <a href=\"./index-3.html\">next</a>,"
            + " <a href=\"./index-3.html\">last</a></p>\n";

    private static final String PAGE_3_OF_3 = "<p class=\"pages\">Page 3 of 3: <a href=\"./index.html\">first</a>,"
            + " <a href=\"./index-2.html\">previous</a></p>\n";

    private static final String PAGE_1_OF_2 = "<p class=\"pages\">Page 1 of 2: <a href=\"./index-2.html\">next</a>,"
            + " <a href=\"./index-2.html\">last</a></p>\n";

    private static final String PAGE_2_OF_2 = "<p class=\"pages\">Page 2 of 2: <a href=\"./index.html\">first</a>,"
            + " <a href=\"./index.html\">previous</a></p>\n";

    /** What heads every page's list. */
    private static final String THE_LIST_HEADING = "<h2>The documents in this group</h2>\n";

    /** Everything a page holds after its list's end and the navigation below it. */
    private static final String THE_TAIL = "</body>\n</html>\n";

    /** How long a long document's path is, in characters: an entry of near 5,000 bytes. */
    private static final int A_LONG_PATH = 4_900;

    /** How long a very long document's path is: one entry larger than the ceiling the long entries are split at. */
    private static final int A_VERY_LONG_PATH = 20_000;

    /** A cluster's list entry, from its opening tag to the line break after its close. */
    private static final Pattern AN_ENTRY = Pattern.compile("(?s)<li id=\"document-(\\d+)\">.*?</li>\n");

    /** A page of a cluster folder, and the number its name gives it. */
    private static final Pattern A_PAGE_NAME = Pattern.compile("index(?:-([2-9]|[1-9]\\d+))?\\.html");

    /** What a page is called while it is written beside its place. */
    private static final String WHILE_IT_IS_WRITTEN = ".part";

    @Test
    @Story("A group's page is split only past the ceiling")
    @DisplayName("With no ceiling set, a group's page is one file, byte for byte as it was written before pages could be split")
    void anUnsetCeilingWritesThePageAsItWasWritten(@TempDir Path base) throws IOException {
        Path unset = folderOf(drawDoors(base.resolve("unset"), UNSET, DeliverableProgress.NONE), DOORS_FOLDER);
        Path byTheFormWithNoCeiling = ListedTree.write(
                        base.resolve("without").resolve("work"),
                        base.resolve("without").resolve("archive"),
                        List.of(DOORS),
                        Map.of(slotOf(DOORS), DOORS_WRITING),
                        DOORS_MEMBERS,
                        Map.of())
                .resolve(DOORS_FOLDER);

        claim(
                "the group's folder holds one page, index.html, and no further page",
                () -> assertThat(pagesIn(unset)).containsExactly(unset.resolve("index.html")));
        claim(
                "and that page is, byte for byte, the page written before any page could be split: no navigation and"
                        + " no word of pages",
                () -> assertThat(read(unset.resolve("index.html"))).isEqualTo(THE_PAGE_AS_IT_WAS_WRITTEN));
        claim(
                "the writer's forms that take no ceiling write that same page",
                () -> assertThat(read(byTheFormWithNoCeiling.resolve("index.html"))).isEqualTo(THE_PAGE_AS_IT_WAS_WRITTEN));
    }

    @Test
    @Story("A group's page is split only past the ceiling")
    @DisplayName("A ceiling equal to the page's size leaves one page, byte for byte the same, and one byte less splits it")
    void aCeilingAtTheOnePageSizeWritesOnePageAndOneByteLessSplitsIt(@TempDir Path base) throws IOException {
        long onePageSize = THE_PAGE_AS_IT_WAS_WRITTEN.getBytes(StandardCharsets.UTF_8).length;

        Path atTheSize = folderOf(
                drawDoors(base.resolve("at"), new ClusterPageCeiling.Bytes(onePageSize), DeliverableProgress.NONE),
                DOORS_FOLDER);
        Path oneByteLess = folderOf(
                drawDoors(base.resolve("under"), new ClusterPageCeiling.Bytes(onePageSize - 1), DeliverableProgress.NONE),
                DOORS_FOLDER);

        claim(
                "a ceiling of exactly the " + onePageSize + " bytes the page takes written whole is not passed, so the"
                        + " group keeps one page, byte for byte the page with no ceiling",
                () -> {
                    assertThat(pagesIn(atTheSize)).containsExactly(atTheSize.resolve("index.html"));
                    assertThat(read(atTheSize.resolve("index.html"))).isEqualTo(THE_PAGE_AS_IT_WAS_WRITTEN);
                });
        claim(
                "a ceiling one byte smaller is passed, so the group is written as more than one page",
                () -> assertThat(pagesIn(oneByteLess)).hasSizeGreaterThan(1));
    }

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

    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("An entry larger than the ceiling on its own has a page of its own, and every other page stays within the ceiling")
    void anEntryLargerThanTheCeilingHasAPageOfItsOwn(@TempDir Path base) throws IOException {
        List<ListedSurvivor> members = longMembers(A_LONG_PATH, A_VERY_LONG_PATH, A_LONG_PATH, A_LONG_PATH);
        String whole = read(folderOf(drawLong(base.resolve("whole"), members, UNSET), DOORS_FOLDER).resolve("index.html"));
        long ceiling = twoAndAHalfEntriesAbove(whole);

        List<Path> pages = pagesIn(folderOf(
                drawLong(base.resolve("split"), members, new ClusterPageCeiling.Bytes(ceiling)), DOORS_FOLDER));

        claim(
                "entry 1 is alone on the first page, since entry 2 alone passes the ceiling; entry 2 is alone on the"
                        + " second; 3 and 4 share the third",
                () -> assertThat(pages.stream().map(page -> numbersOn(read(page))).toList())
                        .containsExactly(List.of(1), List.of(2), List.of(3, 4)));
        for (Path page : pages) {
            claim(
                    page.getFileName() + " is within the ceiling of " + ceiling + " bytes, or holds one entry alone",
                    () -> assertThat(Files.size(page) <= ceiling || numbersOn(read(page)).size() == 1).isTrue());
        }
        claim(
                "and the one page past the ceiling is the second, holding the entry larger than it",
                () -> assertThat(pages.stream().filter(page -> size(page) > ceiling).map(Path::getFileName).map(Path::toString))
                        .containsExactly("index-2.html"));
    }

    @Test
    @Story("A group's page is split only past the ceiling")
    @DisplayName("A group of no listed document, or of one, keeps one page however small the ceiling, even where its"
            + " writing was made from documents it no longer holds")
    void aClusterWithNoListedMemberOrOneEntryIsNeverSplit(@TempDir Path base) throws IOException {
        RecordedCluster one = cluster(THE_SEED, 0, "Fire doors", 1, 1);
        RecordedCluster none = cluster(THE_SEED, 1, "Smoke dampers", 2, 0);
        // Written over from two documents it no longer holds: two entries saying so, and no listed member.
        RecordedCluster writtenOverNone = cluster(THE_SEED, 2, "Sprinklers", 3, 0);
        Map<ClusterSlot, SynthesisDoc> writings =
                Map.of(slotOf(writtenOverNone), writing("Sprinkler heads", "Heads [1] and valves [2].", 30, 31));
        List<RecordedCluster> clusters = List.of(one, none, writtenOverNone);
        List<ListedSurvivor> theOne = List.of(DOORS_MEMBERS.getFirst());
        Path unset = ListedTree.writeWithCeiling(
                base.resolve("unset").resolve("work"), base.resolve("unset").resolve("archive"), clusters,
                writings, theOne, Map.of(), SurvivorPictures.none(), UNSET, DeliverableProgress.NONE);
        Path split = ListedTree.writeWithCeiling(
                base.resolve("split").resolve("work"), base.resolve("split").resolve("archive"), clusters,
                writings, theOne, Map.of(), SurvivorPictures.none(), ONE_BYTE, DeliverableProgress.NONE);

        for (String folder : List.of(DOORS_FOLDER, DAMPERS_FOLDER, "1-fire-safety/3-sprinklers")) {
            claim(
                    "with a ceiling of one byte, " + folder + " still holds one page, byte for byte the page with no"
                            + " ceiling: a page with no entry, or one, has no entry's end to split at",
                    () -> {
                        assertThat(pagesIn(split.resolve(folder))).containsExactly(split.resolve(folder).resolve("index.html"));
                        assertThat(read(split.resolve(folder).resolve("index.html")))
                                .isEqualTo(read(unset.resolve(folder).resolve("index.html")));
                    });
        }
    }

    @Test
    @Story("A group's page is split only past the ceiling")
    @DisplayName("A later page is counted with the list's heading once, so it holds what fits as the first page does")
    void aLaterPageCountsTheListsHeadingOnce() {
        RecordedCluster unwritten = cluster(THE_SEED, 0, "Fire doors", 1, 3);
        long small = 100;
        long tooLargeForAnyPage = 1_000_000;
        // Two small entries after a large one: whether they share the second page decides two pages or three. Two small
        // entries before a large one: whether they share the first page decides it. Both plans count the same heading,
        // navigation and tail, so the two smallest ceilings differ by what the first page holds and a later one does not.
        long laterThreshold = smallestCeilingOfTwoPages(unwritten, new long[] {tooLargeForAnyPage, small, small});
        long firstThreshold = smallestCeilingOfTwoPages(unwritten, new long[] {small, small, tooLargeForAnyPage});
        long firstPageOnly = utf8("<ol>\n") + utf8("<p><em>" + Deliverable.NOTHING_WAS_WRITTEN_OVER_IT + "</em></p>\n");
        long laterPageOnly = utf8("<ol start=\"9\">\n");

        claim(
                "the smallest ceiling at which two entries share the first page exceeds the one at which they share a"
                        + " later page by the first page's own list opening and sentence, less a later page's numbered"
                        + " list opening, and by nothing counted twice",
                () -> assertThat(firstThreshold - laterThreshold).isEqualTo(firstPageOnly - laterPageOnly));
    }

    /**
     * An Italian page's navigation is longer than an English one's (ADR-321 §4), so the plan counts it at its own upper
     * bound: the smallest ceiling at which two small entries share a later page is greater in Italian than in English by
     * exactly the longer navigation, counted twice, and the longer list heading. A plan counting the English words would
     * leave the two thresholds the heading's byte apart.
     */
    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("An Italian group's split is planned with the Italian navigation and list heading, longer than the English")
    @Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
    void anItalianPlanCountsItsNavigationInItalianWords() {
        RecordedCluster unwritten = cluster(THE_SEED, 0, "Fire doors", 1, 3);
        long[] laterPageDecides = {1_000_000, 100, 100};
        long english = smallestCeilingOfTwoPages(unwritten, laterPageDecides, DeliverableLanguage.ENGLISH);
        long italian = smallestCeilingOfTwoPages(unwritten, laterPageDecides, DeliverableLanguage.ITALIAN);
        long longerNavigation = utf8(ItalianWordingOnRecord.PAGE_OF) - utf8(ENGLISH_PAGE_OF)
                + 4 * (utf8(longestOf(ItalianWordingOnRecord.FIRST, ItalianWordingOnRecord.PREVIOUS,
                                ItalianWordingOnRecord.NEXT, ItalianWordingOnRecord.LAST))
                        - utf8(longestOf("first", "previous", "next", "last")));
        long longerHeading = utf8(ItalianWordingOnRecord.MEMBERSHIP_HEADING) - utf8("The documents in this group");

        claim(
                "the Italian threshold exceeds the English by the navigation above and below the list, each longer by "
                        + longerNavigation + " bytes at its widest, and the list heading, longer by " + longerHeading,
                () -> assertThat(italian - english).isEqualTo(2 * longerNavigation + longerHeading));
    }

    /**
     * Where the plan is tight, the pages an Italian group is written as stay within the ceiling (ADR-303 §3, ADR-321 §4):
     * the ceiling is the smallest at which the Italian plan puts five long entries on three pages.
     */
    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("At the tightest ceiling its plan allows, every page of an Italian group holding more than one entry is within the ceiling")
    @Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
    void anItalianGroupsPagesAreWithinATightCeiling(@TempDir Path base) throws IOException {
        List<ListedSurvivor> members = longMembers(A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH, A_LONG_PATH);
        Path wholeTree = drawLong(base.resolve("whole"), members, UNSET, DeliverableLanguage.ITALIAN);
        String whole = read(folderOf(wholeTree, DOORS_FOLDER).resolve("index.html"));
        long[] entrySizes = entriesOf(whole).stream().mapToLong(AClusterPastTheCeilingIsSplitIntoPagesTest::utf8).toArray();
        RecordedCluster doors = cluster(THE_SEED, 0, "Fire doors", 1, members.size());
        long tight = smallestCeilingOf(3, entrySizes,
                ceiling -> ClusterPage.plan(ceiling, doors, longWriting(members), null, entrySizes, false,
                        DeliverableLanguage.ITALIAN));

        List<Path> pages = pagesIn(folderOf(
                drawLong(base.resolve("split"), members, new ClusterPageCeiling.Bytes(tight), DeliverableLanguage.ITALIAN),
                DOORS_FOLDER));

        claim(
                "the group is written as the three pages its plan made: the first, carrying the writing, holds entry 1"
                        + " alone, and the two after it two entries each, filled to the ceiling",
                () -> assertThat(pages.stream().map(page -> numbersOn(read(page))).toList())
                        .containsExactly(List.of(1), List.of(2, 3), List.of(4, 5)));
        for (Path page : pages.subList(1, pages.size())) {
            claim(
                    page.getFileName() + ", holding two entries, is within the ceiling of " + tight + " bytes, its"
                            + " Italian navigation counted",
                    () -> assertThat(Files.size(page)).isLessThanOrEqualTo(tight));
        }
    }

    /** The English navigation's opening, as ADR-303 §5 has it, which the Italian is measured against. */
    private static final String ENGLISH_PAGE_OF = "Page %d of %d: ";

    private static String longestOf(String... words) {
        return Arrays.stream(words).max(Comparator.comparingInt(String::length)).orElseThrow();
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

    private static long utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
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
    @DisplayName("Each page carries the group's title, the page navigation above and below its list, and only the first carries the writing or the reason nothing was written")
    void eachPageCarriesTheTitleTheNavigationAboveAndBelowTheListAndTheWritingOnlyOnTheFirst(@TempDir Path base)
            throws IOException {
        Path unset = drawBoth(base.resolve("unset"), UNSET, DeliverableProgress.NONE);
        Path split = drawBoth(base.resolve("split"), ONE_BYTE, DeliverableProgress.NONE);
        String doors = read(unset.resolve(DOORS_FOLDER).resolve("index.html"));
        String dampers = read(unset.resolve(DAMPERS_FOLDER).resolve("index.html"));
        List<String> doorsEntries = entriesOf(doors);
        List<String> dampersEntries = entriesOf(dampers);
        String doorsWritingWithItsCitationsOnTheirPages = standingOnTheFirstPage(doors)
                .replace("href=\"#document-2\"", "href=\"./index-2.html#document-2\"")
                .replace("href=\"#document-3\"", "href=\"./index-3.html#document-3\"");

        claim(
                "the written group's three pages are, in full: its head and title, the navigation, on the first page"
                        + " alone the writing, its citations of the second and third entries naming the page each is on,"
                        + " then the list's heading, its list numbered on, its one entry, the navigation again and the tail",
                () -> assertThat(pagesIn(split.resolve(DOORS_FOLDER)).stream().map(AClusterPastTheCeilingIsSplitIntoPagesTest::read))
                        .containsExactly(
                                throughTheTitle(doors) + PAGE_1_OF_3 + doorsWritingWithItsCitationsOnTheirPages
                                        + THE_LIST_HEADING + "<ol>\n" + doorsEntries.get(0) + "</ol>\n" + PAGE_1_OF_3
                                        + THE_TAIL,
                                throughTheTitle(doors) + PAGE_2_OF_3 + THE_LIST_HEADING + "<ol start=\"2\">\n"
                                        + doorsEntries.get(1) + "</ol>\n" + PAGE_2_OF_3 + THE_TAIL,
                                throughTheTitle(doors) + PAGE_3_OF_3 + THE_LIST_HEADING + "<ol start=\"3\">\n"
                                        + doorsEntries.get(2) + "</ol>\n" + PAGE_3_OF_3 + THE_TAIL));
        claim(
                "and the group nothing was written over has two pages, the sentence saying why on the first alone",
                () -> assertThat(pagesIn(split.resolve(DAMPERS_FOLDER)).stream().map(AClusterPastTheCeilingIsSplitIntoPagesTest::read))
                        .containsExactly(
                                throughTheTitle(dampers) + PAGE_1_OF_2 + standingOnTheFirstPage(dampers)
                                        + THE_LIST_HEADING + "<ol>\n" + dampersEntries.get(0) + "</ol>\n" + PAGE_1_OF_2
                                        + THE_TAIL,
                                throughTheTitle(dampers) + PAGE_2_OF_2 + THE_LIST_HEADING + "<ol start=\"2\">\n"
                                        + dampersEntries.get(1) + "</ol>\n" + PAGE_2_OF_2 + THE_TAIL));
        claim(
                "the sentence saying why is the one that group's page written whole carries",
                () -> assertThat(standingOnTheFirstPage(dampers)).startsWith("<p><em>").endsWith("</em></p>\n"));
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

    @Test
    @Story("What an earlier drawing left is counted and kept")
    @DisplayName("A page an earlier drawing wrote past the last one this drawing writes is counted as left behind, and none is deleted")
    void aPageAnEarlierDrawingWrotePastTheLastIsCountedAndKept(@TempDir Path base) throws IOException {
        List<Long> told = new ArrayList<>();
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void filesLeftBehind(long files) {
                told.add(files);
            }
        };
        Path folder = folderOf(drawDoors(base, ONE_BYTE, hearing), DOORS_FOLDER);
        Path aSeventhPage = Files.writeString(folder.resolve("index-7.html"), "a page an earlier drawing wrote");

        drawDoors(base, ONE_BYTE, hearing);
        drawDoors(base, UNSET, hearing);
        List<Path> afterTheDrawingWithNoCeiling = pagesIn(folder);
        String theFirstPageDrawnWhole = read(folder.resolve("index.html"));
        drawDoors(base, ONE_BYTE, hearing);

        claim(
                "the first drawing, of three pages, tells of no file left; with a seventh page put beside them, the next"
                        + " drawing of three tells of 1; drawn whole, of one page, it tells of 3, the second, the third"
                        + " and the seventh; and of three pages again, of 1",
                () -> assertThat(told).containsExactly(0L, 1L, 3L, 1L));
        claim(
                "drawn whole, the group's folder still held every page an earlier drawing wrote, the first written"
                        + " whole again",
                () -> {
                    assertThat(afterTheDrawingWithNoCeiling).extracting(page -> page.getFileName().toString())
                            .containsExactly("index.html", "index-2.html", "index-3.html", "index-7.html");
                    assertThat(theFirstPageDrawnWhole).isEqualTo(THE_PAGE_AS_IT_WAS_WRITTEN);
                });
        claim(
                "and the seventh page is where it was put, as it was written: nothing is deleted",
                () -> assertThat(aSeventhPage).hasContent("a page an earlier drawing wrote"));
    }

    @Test
    @Story("A group past the ceiling is written as several pages")
    @DisplayName("The progress hears of every entry once and every group once, however many pages each group is written as")
    void eachEntryAndEachClusterIsToldOnceWhetherOrNotItIsSplit(@TempDir Path base) {
        long[] entriesAnnounced = {-1};
        long[] entriesTold = {0};
        long[] clustersAnnounced = {-1};
        long[] clustersTold = {0};
        DeliverableProgress counting = new DeliverableProgress() {
            @Override
            public void toWriteMembershipEntries(long entries) {
                entriesAnnounced[0] = entries;
            }

            @Override
            public void membershipEntryWritten() {
                entriesTold[0]++;
            }

            @Override
            public void toDrawClusters(long clusters) {
                clustersAnnounced[0] = clusters;
            }

            @Override
            public void clusterDrawn() {
                clustersTold[0]++;
            }
        };
        long everyEntry = DOORS_MEMBERS.size() + DAMPERS_MEMBERS.size();
        long everyCluster = 2;

        drawBoth(base, ONE_BYTE, counting);

        claim(
                "the " + everyEntry + " entries of the two groups, split into five pages, are announced as "
                        + everyEntry + " and each told once, not again for the pages they are written on",
                () -> assertThat(List.of(entriesAnnounced[0], entriesTold[0])).containsExactly(everyEntry, everyEntry));
        claim(
                "and the " + everyCluster + " groups are announced as " + everyCluster + " and each told once",
                () -> assertThat(List.of(clustersAnnounced[0], clustersTold[0])).containsExactly(everyCluster, everyCluster));
    }

    /** The first cluster alone, written over, drawn over an empty archive under {@code base}. */
    private static Path drawDoors(Path base, ClusterPageCeiling ceiling, DeliverableProgress progress) {
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(DOORS), Map.of(slotOf(DOORS), DOORS_WRITING),
                DOORS_MEMBERS, Map.of(), SurvivorPictures.none(), ceiling, progress);
    }

    /** Both clusters, the second with nothing it could send, drawn over an empty archive under {@code base}. */
    private static Path drawBoth(Path base, ClusterPageCeiling ceiling, DeliverableProgress progress) {
        List<ListedSurvivor> survivors = new ArrayList<>(DOORS_MEMBERS);
        survivors.addAll(DAMPERS_MEMBERS);
        Map<ClusterSlot, Unwritten> unwritten = Map.of(slotOf(DAMPERS), Unwritten.NO_SENDABLE_DOCUMENT);
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(DOORS, DAMPERS),
                Map.of(slotOf(DOORS), DOORS_WRITING), survivors, unwritten, SurvivorPictures.none(), ceiling, progress);
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

    /** A page written whole, from its first byte to the line break after its heading. */
    private static String throughTheTitle(String whole) {
        return whole.substring(0, whole.indexOf("</h1>\n") + "</h1>\n".length());
    }

    /** What a page written whole holds between its heading and its list's heading: the writing, or why there is none. */
    private static String standingOnTheFirstPage(String whole) {
        return whole.substring(whole.indexOf("</h1>\n") + "</h1>\n".length(), whole.indexOf(THE_LIST_HEADING));
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
