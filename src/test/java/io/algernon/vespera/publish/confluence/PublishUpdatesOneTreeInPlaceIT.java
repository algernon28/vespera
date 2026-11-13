package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Confluence rendering is one tree under the parent, which every publish updates in place, whichever generation
 * run drew it (ADR-328): a page is matched by its place; a page the rendering no longer has is moved to the trash after
 * every page is sent, deepest first, one with anything under it, or one the site refuses to delete, being left and said;
 * a tree an earlier publish made with its run's marker in its titles is taken and retitled in place, a taking stopped
 * half way ending on the next publish; and a title a page outside the tree holds, for a page created, retitled or
 * taken, a parent the site will not say is a folder or a page or will not list, a tree under another parent and a page
 * list of the earlier format stop the publish before anything is written. Against a stub; every text is synthetic.
 *
 * <p><b>One tree for each deliverable language</b> (ADR-378): an English and an Italian rendering are two trees under
 * the one parent, each titled, labelled and keyed by its language's code, and a publish of one writes to no page of the
 * other; a tree an earlier publish made is left by a publish in the other language and taken by the one its root page's
 * heading names, one whose heading names neither, or two of one language, stopping the publish; and a language's tree
 * standing under another parent stops its own publish alone.
 *
 * <p>It also holds, since this is the one class publishing to the stub, what ADR-308 §2 and §3 decide of every publish:
 * each request goes to the configured {@code https} host alone, an {@code http} address is refused and a link to
 * another host is not followed, before anything is sent there; and the environment's email and token go as Basic
 * authentication on every request and in no line or file the publish writes. And what ADR-383 §1 and §2 decide of a
 * parent's title that no page or folder of the space holds, or several: the line and the exit code a delivery stops
 * with, as the control panel started it and as typed on the command line.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("667")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
@Link(name = "ADR-320", url = Adr.A_PUBLISH_SENDS_EACH_PAGE_WHOLE_AND_SAYS_HOW_FAR_IT_HAS_GOT, type = "adr")
@Link(name = "ADR-378", url = Adr.EACH_DELIVERABLE_LANGUAGE_IS_PUBLISHED_AS_A_TREE_OF_ITS_OWN, type = "adr")
@Tag("confluence")
class PublishUpdatesOneTreeInPlaceIT {

    /** The title the later run's group at place 1.1 is drawn with, in the English tree (ADR-378 §1). */
    private static final String THE_LATER_GROUPS_TITLE = "Overtime and pay (1.1 · en)";

    /** The root page's title of a tree of ADR-328's form, which an earlier publish made with no language. */
    private static final String THE_EARLIER_ROOT_TITLE = "Vespera";

    /** That tree's label and its pages' keys, its places alone (ADR-328 §3, §4). */
    private static final String THE_EARLIER_LABEL = "vespera";

    /** Its seed partition's and its group's titles, their places alone in their suffixes (ADR-328 §1). */
    private static final String THE_EARLIER_PARTITION_TITLE = "Seeds (1)";

    private static final String THE_EARLIER_CLUSTER_TITLE = "Payroll and leave (1.1)";

    /** A root page's body as an Italian drawing opens it, its heading's Italian words (ADR-328 §2, ADR-321 §4). */
    private static final String AN_ITALIAN_ROOT_BODY = "<h1>Base di conoscenza</h1>\n<p>An earlier body.</p>";

    /** The same Italian heading in bold, its two words kept apart by a no-break space, as an editor may save it. */
    private static final String AN_ITALIAN_ROOT_BODY_IN_MARKUP =
            "<h1><strong>Base di&nbsp;conoscenza</strong></h1>\n<p>An earlier body.</p>";

    /** A root page's body as an English drawing opens it (ADR-328 §2). */
    private static final String AN_ENGLISH_ROOT_BODY = "<h1>Deliverable</h1>\n<p>An earlier body.</p>";

    /** The key the earlier tree's group's page holds, its place alone (ADR-328 §3). */
    private static final String THE_GROUPS_EARLIER_KEY = "1.1";

    /** Two pages someone copied with the group's page's label and key: one under another parent, one under the tree. */
    private static final String A_PAGE_OF_ONE_KEY_OUTSIDE = "Payroll and leave, a copy elsewhere (1.1)";

    private static final String A_LATER_PAGE_OF_ONE_KEY = "Payroll and leave, a later copy (1.1)";

    /** The list of refused files an earlier version wrote in the working directory, which no publish writes now. */
    private static final String THE_LIST_AN_EARLIER_VERSION_WROTE = "confluence-not-carried.csv";

    private static final String AN_EARLIER_LIST = "key,name,size\r\n\"1.1\",\"an earlier copy.pdf\",7\r\n";

    /** What a publish says of that list, removed or not (ADR-378 §10). */
    private static final String THE_EARLIER_LIST_IS_REMOVED = "confluence-not-carried.csv, which an earlier version of"
            + " vespera publish wrote, is removed from the working directory";

    private static final String THE_EARLIER_LIST_COULD_NOT_BE_REMOVED = "confluence-not-carried.csv, which an earlier"
            + " version of vespera publish wrote, could not be removed from the working directory (";

    /** The statuses a site refuses an account with, and fails a request of its own with. */
    private static final int FORBIDDEN = 403;

    private static final int SERVER_ERROR = 500;

    /** A root page's body whose heading someone took out, so it names no language. */
    private static final String A_ROOT_BODY_WITH_NO_HEADING = "<p>An earlier body.</p>";

    /** What the English publish says of an earlier tree drawn in Italian, which it leaves (ADR-378 §7). */
    private static final String LEFT_FOR_AN_ITALIAN_PUBLISH = " an earlier publish made was drawn in Italian, and is"
            + " left as it stands for a publish in Italian to take";

    /** What the Italian publish says of an earlier tree drawn in English, which it leaves (ADR-378 §7). */
    private static final String LEFT_FOR_AN_ENGLISH_PUBLISH = " an earlier publish made was drawn in English, and is"
            + " left as it stands for a publish in English to take";

    /** A generation run an earlier publish sent under ADR-310, its marker in every title, and its marker. */
    private static final String EARLIER_RUN = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private static final String EARLIER_MARKER = "fedcba987654";

    /** A second such run, for a parent holding two trees of an earlier publish. */
    private static final String ANOTHER_EARLIER_RUN = "0f1e2d3c4b5a69780f1e2d3c4b5a69780f1e2d3c4b5a69780f1e2d3c4b5a6978";

    private static final String ANOTHER_EARLIER_MARKER = "0f1e2d3c4b5a";

    /** What a removal's line says of a page left because something stands under it (ADR-328 §5). */
    private static final String LEFT_HOLDING =
            ", left as it stands: something stands under it, which its removal could take with it";

    /** What a removal's line says of a page the site no longer holds (ADR-328 §5). */
    private static final String ALREADY_GONE = ", already gone";

    /** The status a site answers a page, a label or a listing it does not hold with, and a failure of its own. */
    private static final int NOT_FOUND = 404;

    /** Pages of the English tree an earlier publish of a larger rendering left, which this rendering no longer has. */
    private static final String FORMER_PARTITION_TITLE = "Former seeds (2 · en)";

    private static final String FORMER_CLUSTER_TITLE = "Former payroll (2.1 · en)";

    private static final String FORMER_PARTITION_KEY = "en:2";

    private static final String FORMER_CLUSTER_KEY = "en:2.1";

    /** A page someone put in the space, which no publish made. */
    private static final String A_PERSONS_PAGE = "Notes on overtime";

    /** The line a publish stopped by a title a page outside the tree holds begins with (ADR-320 §3). */
    private static final String STOPPED_BY_TITLE = "vespera publish stopped: a page titled ";

    /** An address of the site that is http, not https, which no request may be sent to. */
    private static final String AN_HTTP_ADDRESS = "http://" + StubConfluence.SITE_HOST;

    /** A host other than the site's, which a link the site answers with may name. */
    private static final String ANOTHER_HOST = "elsewhere.example";

    /** A parent's title no page or folder of the stub's space holds. */
    private static final String TITLE_OF_NO_PAGE = "Nowhere";

    /** How far a publish stopped at its parent had got: nothing sent (ADR-320 §4). */
    private static final String NOTHING_SENT = " It had created 0 pages, updated 0 and sent 0 files. Publish again to go"
            + " on from there.";

    /** A publish's closing lines where the parent's title names no page or folder, and two, on the command line (ADR-318 §3). */
    private static final String ON_THE_COMMAND_LINE_NO_PAGE = "vespera publish stopped: no page or folder of the space "
            + StubConfluence.SPACE_KEY + " is titled " + TITLE_OF_NO_PAGE + ", and the parent must be exactly one; name"
            + " it by VESPERA_CONFLUENCE_FOLDER_ID." + NOTHING_SENT;

    private static final String ON_THE_COMMAND_LINE_TWO = "vespera publish stopped: 2 pages or folders of the space "
            + StubConfluence.SPACE_KEY + " is titled " + StubConfluence.PARENT_TITLE + ", and the parent must be exactly"
            + " one; name it by VESPERA_CONFLUENCE_FOLDER_ID." + NOTHING_SENT;

    /** The same two, the control panel having started the publish (ADR-383 §1, as ADR-388 §6 words them). */
    private static final String FROM_THE_CONTROL_PANEL_NO_PAGE = "vespera publish stopped: no page or folder of the"
            + " space " + StubConfluence.SPACE_KEY + " is titled " + TITLE_OF_NO_PAGE + ", and the parent must be"
            + " exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in"
            + " the control panel's Profile window." + NOTHING_SENT;

    private static final String FROM_THE_CONTROL_PANEL_TWO = "vespera publish stopped: 2 pages or folders of the space "
            + StubConfluence.SPACE_KEY + " are titled " + StubConfluence.PARENT_TITLE + ", and the parent must be"
            + " exactly one; set another title in confluenceFolderTitle, or the parent's id in confluenceFolderId, in"
            + " the control panel's Profile window." + NOTHING_SENT;

    /** The exit codes a publish the control panel started ends with, the parent's title naming none, and several (ADR-383 §1). */
    private static final int NO_PAGE_SO_TITLED = 3;

    private static final int SEVERAL_SO_TITLED = 4;

    /** The exit code of any other stop (ADR-333 §6). */
    private static final int STOPPED = 1;

    @TempDir
    Path folder;

    @Test
    @Story("A publish updates one tree in place")
    @DisplayName("A later run's rendering creates no page, keeps every page's address, and puts the group now at a place on that place's page")
    void aLaterRunsRenderingUpdatesTheSameTreeInPlace() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            List<String> ids = site.pages().stream().map(page -> page.id).toList();
            rendering.asTheLaterRunWithTheClusterTitled(THE_LATER_GROUPS_TITLE);
            site.forgetTheRequests();
            Publisher.Outcome later = publish(site, rendering, Rendering.LATER_RUN, new ArrayList<>());

            claim("the later run's publish went to its end, creating no page: the root page unchanged, the seed"
                            + " partition's and the group's updated",
                    () -> assertThat(List.of(later.published(), later.closingLine().contains(
                            "3 pages (0 created, 2 updated, 1 unchanged)"))).containsExactly(true, true));
            claim("the site holds the same three pages, at the same addresses, the place 1.1 now titled by the group"
                            + " drawn there",
                    () -> assertThat(site.pages()).extracting(page -> page.id + " " + page.title).containsExactly(
                            ids.get(0) + " " + Rendering.ROOT_TITLE,
                            ids.get(1) + " " + Rendering.PARTITION_TITLE,
                            ids.get(2) + " " + THE_LATER_GROUPS_TITLE));
            claim("every page is labelled vespera alone and keyed by its place, with nothing of either run in them",
                    () -> assertThat(site.pages()).extracting(page -> page.labels + " " + page.property.get("key"))
                            .containsExactly(
                                    "[" + Rendering.LABEL + "] " + Rendering.ROOT,
                                    "[" + Rendering.LABEL + "] " + Rendering.PARTITION,
                                    "[" + Rendering.LABEL + "] " + Rendering.CLUSTER));
            claim("and nothing was moved to the trash", () -> assertThat(site.trash()).isEmpty());
        }
    }

    @Test
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A page the rendering no longer has is moved to the trash, its children asked first, after every page is sent, and said so")
    void aPageTheRenderingNoLongerHasIsMovedToTheTrash() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            StubConfluence.Page cluster = page(site, Rendering.CLUSTER_TITLE);
            rendering.withBody(Rendering.PARTITION, "<p>The seed partition, with no group now.</p>").withoutTheCluster();
            site.forgetTheRequests();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);
            List<StubConfluence.Request> sent = site.sent();
            int removal = indexOf(sent, "DELETE", "/wiki/api/v2/pages/" + cluster.id);
            String justBefore = removal > 0
                    ? sent.get(removal - 1).method() + " " + sent.get(removal - 1).path()
                    : "no request, the page's DELETE being " + (removal < 0 ? "absent" : "the first");

            claim("the publish went to its end", () -> assertThat(again.published()).isTrue());
            claim("the group's page, and it alone, is in the space's trash",
                    () -> assertThat(site.trash()).extracting(page -> page.id + " " + page.title)
                            .containsExactly(cluster.id + " " + Rendering.CLUSTER_TITLE));
            claim("by one DELETE of the page, with no purge, so a space admin can restore it",
                    () -> assertThat(sent).filteredOn(request -> request.method().equals("DELETE")).singleElement()
                            .satisfies(request -> {
                                assertThat(request.path()).isEqualTo("/wiki/api/v2/pages/" + cluster.id);
                                assertThat(request.query("purge")).isNull();
                            }));
            claim("the request just before it asked for the page's children",
                    () -> assertThat(justBefore).isEqualTo(
                            "GET /wiki/api/v2/pages/" + cluster.id + "/direct-children"));
            claim("the site took the seed partition's new body first, and only then the removal",
                    () -> assertThat(site.writes()).extracting(StubConfluence.Write::toString).containsExactly(
                            new StubConfluence.Write(StubConfluence.Kind.BODY, Rendering.PARTITION_TITLE, null).toString(),
                            new StubConfluence.Write(StubConfluence.Kind.TRASHED, Rendering.CLUSTER_TITLE, null)
                                    .toString()));
            claim("after the last page's line, a line counts the removals and a line says what became of the page",
                    () -> assertThat(lines.subList(lines.size() - 2, lines.size())).containsExactly(
                            "1 page of the tree the rendering no longer has is moved to the trash",
                            "removal 1 of 1: " + Rendering.CLUSTER_TITLE + ", moved to the trash"));
            claim("and the closing line counts it removed, and no page left",
                    () -> assertThat(again.closingLine())
                            .contains("; 1 page the rendering no longer has was removed")
                            .doesNotContain("left as"));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("Two trees an earlier publish made in English under the parent stop an English publish before anything is written, naming both, and an Italian publish under them makes its own tree")
    void twoTreesWithTheirMarkerUnderTheParentStopThePublishBeforeAnyWrite() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        Rendering italian = Rendering.drawnIn(folder.resolve("it"), Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> earlier = new ArrayList<>(anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER));
            earlier.addAll(anEarlierTree(site, tree, ANOTHER_EARLIER_RUN, ANOTHER_EARLIER_MARKER));
            List<String> before = describedById(site, earlier);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());
            List<StubConfluence.Request> sentByTheEnglishPublish = site.sent();
            site.forgetTheRequests();
            List<String> italianLines = new ArrayList<>();
            Publisher.Outcome italianOutcome = publish(site, italian, italian.run(), italianLines);

            claim("the English publish stopped, saying two trees an earlier publish made in English stand under the"
                            + " parent, naming both their root pages",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().startsWith(
                                    "vespera publish stopped: the parent holds 2 trees an earlier publish made in"
                                            + " English, "),
                            outcome.closingLine().contains("Vespera " + EARLIER_MARKER),
                            outcome.closingLine().contains("Vespera " + ANOTHER_EARLIER_MARKER)))
                            .containsExactly(false, true, true, true));
            claim("and it sent nothing but reads", () -> assertThat(sentByTheEnglishPublish).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("the Italian publish under the same parent went to its end, creating its own three pages",
                    () -> assertThat(List.of(italianOutcome.published(), italianOutcome.closingLine().contains(
                            "tree Vespera_it: 3 pages (3 created, 0 updated, 0 unchanged)")))
                            .containsExactly(true, true));
            claim("saying of each English tree that it is left for an English publish to take",
                    () -> assertThat(italianLines).contains(
                            "the tree Vespera " + EARLIER_MARKER + LEFT_FOR_AN_ENGLISH_PUBLISH,
                            "the tree Vespera " + ANOTHER_EARLIER_MARKER + LEFT_FOR_AN_ENGLISH_PUBLISH));
            claim("and the six pages of the two English trees stand as they stood, titled, labelled and keyed as an"
                            + " earlier publish left them",
                    () -> assertThat(describedById(site, earlier)).isEqualTo(before));
        }
    }

    @Test
    @Story("Each deliverable language is a tree of its own")
    @DisplayName("An English and an Italian publish make two trees under one parent, and an English publish that removes a page writes to no Italian page")
    void twoLanguagesAreTwoTreesUnderOneParentAndAPublishOfOneWritesToNoPageOfTheOther() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        Rendering italian = Rendering.drawnIn(folder.resolve("it"), Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> englishLines = new ArrayList<>();
            Publisher.Outcome first = publish(site, english, english.run(), englishLines);
            Publisher.Outcome second = publish(site, italian, italian.run(), new ArrayList<>());
            List<String> afterBoth = site.pages().stream().map(PublishUpdatesOneTreeInPlaceIT::described).toList();
            List<String> italianIds = site.pages().stream().filter(page -> page.labels.contains(italian.label()))
                    .map(page -> page.id).toList();
            List<String> italianBefore = describedById(site, italianIds);
            english.withoutTheCluster();
            site.forgetTheRequests();
            Publisher.Outcome third = publish(site, english, english.run(), new ArrayList<>());

            claim("the English totals line names the generation run, the site, the space and the tree it sends to",
                    () -> assertThat(englishLines.getFirst()).startsWith("vespera publish is sending the rendering of"
                            + " generation run " + Rendering.MARKER + " to " + StubConfluence.SITE_HOST + ", space "
                            + StubConfluence.SPACE_KEY + ", tree Vespera_en: 3 pages and 3 files holding "));
            claim("each of the two publishes went to its end creating three pages, its closing line naming its tree",
                    () -> assertThat(List.of(first.closingLine(), second.closingLine())).containsExactly(
                            closingLineOf(Rendering.MARKER, "Vespera_en") + "3 pages (3 created, 0 updated, 0"
                                    + " unchanged) and 3 files (3 added, 0 replaced, 0 unchanged)",
                            closingLineOf(Rendering.ITALIAN_MARKER, "Vespera_it") + "3 pages (3 created, 0 updated, 0"
                                    + " unchanged) and 3 files (3 added, 0 replaced, 0 unchanged)"));
            claim("the site then held two trees, each page titled with its language's code, labelled vespera and the"
                            + " code, and keyed by the code and its place",
                    () -> assertThat(afterBoth).containsExactly(
                            "Vespera_en [vespera-en] en:root",
                            "Seeds (1 · en) [vespera-en] en:1",
                            "Payroll and leave (1.1 · en) [vespera-en] en:1.1",
                            "Vespera_it [vespera-it] it:root",
                            "Seeds (1 · it) [vespera-it] it:1",
                            "Payroll and leave (1.1 · it) [vespera-it] it:1.1"));
            claim("both root pages stand directly under the one parent named",
                    () -> assertThat(List.of(page(site, "Vespera_en").parentId, page(site, "Vespera_it").parentId))
                            .containsExactly(StubConfluence.PARENT_ID, StubConfluence.PARENT_ID));
            claim("the English publish that no longer has its group moved the English group's page alone to the trash",
                    () -> assertThat(List.of(third.published(), site.trash().stream().map(page -> page.title).toList()))
                            .containsExactly(true, List.of(english.clusterTitle())));
            claim("and it sent no request but a read naming any Italian page, each of which stands as it stood",
                    () -> assertThat(List.of(writesNaming(site, italianIds), describedById(site, italianIds)))
                            .containsExactly(List.of(), italianBefore));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("The tree an earlier publish made with no language is left by an English publish and taken by an Italian one, its root page's heading being Italian")
    void theTreeOfTheEarlierFormIsLeftByTheOtherLanguageAndTakenByTheLanguageItsHeadingNames() throws IOException {
        Path italianTree = folder.resolve("it");
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        Rendering italian = Rendering.drawnIn(italianTree, Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> earlier = aTreeOfTheEarlierForm(site, italianTree, AN_ITALIAN_ROOT_BODY);
            List<String> before = describedById(site, earlier);
            List<String> englishLines = new ArrayList<>();
            Publisher.Outcome englishOutcome = publish(site, english, english.run(), englishLines);
            List<String> afterTheEnglishPublish = describedById(site, earlier);
            List<String> englishIds = site.pages().stream().filter(page -> page.labels.contains(english.label()))
                    .map(page -> page.id).toList();
            site.forgetTheRequests();
            List<String> italianLines = new ArrayList<>();
            Publisher.Outcome italianOutcome = publish(site, italian, italian.run(), italianLines);

            claim("the English publish went to its end creating a tree of its own, and said why it left the earlier one",
                    () -> assertThat(List.of(englishOutcome.published(),
                            englishOutcome.closingLine().contains("tree Vespera_en: 3 pages (3 created, 0 updated, 0"
                                    + " unchanged)"),
                            englishLines.contains("the tree " + THE_EARLIER_ROOT_TITLE + LEFT_FOR_AN_ITALIAN_PUBLISH)))
                            .containsExactly(true, true, true));
            claim("the earlier tree's three pages stood after it as they stood before it",
                    () -> assertThat(afterTheEnglishPublish).isEqualTo(before));
            claim("the Italian publish went to its end creating no page, the earlier tree's three updated",
                    () -> assertThat(List.of(italianOutcome.published(), italianOutcome.closingLine().contains(
                            "tree Vespera_it: 3 pages (0 created, 3 updated, 0 unchanged)")))
                            .containsExactly(true, true));
            claim("and said before the first page that it took the earlier tree as the Italian one",
                    () -> assertThat(italianLines).contains("the tree " + THE_EARLIER_ROOT_TITLE + " an earlier"
                            + " publish made is taken as the tree Vespera_it: its 3 pages are retitled and updated in"
                            + " place"));
            claim("the earlier tree's pages keep their addresses, retitled with the Italian code, labelled vespera-it"
                            + " alone and keyed by the code and their place",
                    () -> assertThat(describedById(site, earlier)).containsExactly(
                            earlier.get(0) + " Vespera_it [vespera-it] it:root",
                            earlier.get(1) + " Seeds (1 · it) [vespera-it] it:1",
                            earlier.get(2) + " Payroll and leave (1.1 · it) [vespera-it] it:1.1"));
            claim("their files standing at their size and digest, none was sent again",
                    () -> assertThat(site.writes()).extracting(StubConfluence.Write::kind)
                            .doesNotContain(StubConfluence.Kind.FILE));
            claim("and the Italian publish sent no request but a read naming any page of the English tree",
                    () -> assertThat(writesNaming(site, englishIds)).isEmpty());
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A tree an earlier publish made whose root page's heading names neither language stops the publish before anything is written")
    void anEarlierTreeWhoseHeadingNamesNeitherLanguageStopsThePublishBeforeAnyWrite() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            aTreeOfTheEarlierForm(site, tree, A_ROOT_BODY_WITH_NO_HEADING);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, saying the earlier tree's language cannot be told from its heading",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().startsWith(
                            "vespera publish stopped: the parent holds the tree " + THE_EARLIER_ROOT_TITLE + " an"
                                    + " earlier publish made, and its root page's heading is neither Deliverable nor"
                                    + " Base di conoscenza, so the language it was drawn in cannot be told")))
                            .containsExactly(false, true));
            claim("and it sent nothing but reads, so no tree was made beside it",
                    () -> assertThat(site.sent()).isNotEmpty()
                            .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
        }
    }

    @Test
    @Story("Each deliverable language is a tree of its own")
    @DisplayName("An English tree standing under another parent stops the English publish naming its root page, and an Italian publish to that parent makes its own tree there")
    void theTreeOfItsLanguageUnderAnotherParentStopsThePublishNamingItsRootPage() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        Rendering italian = Rendering.drawnIn(folder.resolve("it"), Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, english, english.run(), new ArrayList<>());
            site.forgetTheRequests();
            Publisher.Outcome elsewhere = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL,
                            StubConfluence.OTHER_PARENT_ID, "", new ArrayList<>())
                    .publish(english.confluence(), english.run(), folder, line -> {});
            List<StubConfluence.Request> sentByTheEnglishPublish = site.sent();
            Publisher.Outcome italianOutcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL,
                            StubConfluence.OTHER_PARENT_ID, "", new ArrayList<>())
                    .publish(italian.confluence(), italian.run(), folder, line -> {});

            claim("the English publish stopped, naming the parent its tree stands under and its root page",
                    () -> assertThat(List.of(elsewhere.published(), elsewhere.closingLine().startsWith(
                            "vespera publish stopped: the tree Vespera_en it updates stands under "
                                    + StubConfluence.PARENT_ID + ", not under the parent named. Name that parent, or"
                                    + " move the page Vespera_en under the one named")))
                            .containsExactly(false, true));
            claim("and it sent nothing but reads", () -> assertThat(sentByTheEnglishPublish).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("the Italian publish to that other parent went to its end, its root page standing directly under it",
                    () -> assertThat(List.of(italianOutcome.published(), page(site, "Vespera_it").parentId))
                            .containsExactly(true, StubConfluence.OTHER_PARENT_ID));
        }
    }

    @Test
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A page the site answers 404 to when it is removed is said to be gone already, and counted removed")
    void aRemovalAnswered404IsSaidToBeGoneAlready() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            site.refuseToRemove(Rendering.CLUSTER_TITLE, NOT_FOUND);
            rendering.withoutTheCluster();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);

            claim("its line says the page was gone already",
                    () -> assertThat(lines.getLast()).isEqualTo("removal 1 of 1: " + Rendering.CLUSTER_TITLE
                            + ALREADY_GONE));
            claim("and the publish went to its end, counting it removed and none left",
                    () -> assertThat(List.of(again.published(),
                            again.closingLine().contains("; 1 page the rendering no longer has was removed"),
                            again.closingLine().contains("left as"))).containsExactly(true, true, false));
        }
    }

    @Test
    @Story("A publish updates one tree in place")
    @DisplayName("A parent whose children the site answers 404 to listing stops the publish before anything is written, and is not taken as holding no tree")
    void aParentsListingAnswered404StopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.answerTheListingOfTheChildrenOf(StubConfluence.PARENT_ID, NOT_FOUND);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, saying what the site answered",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().startsWith(
                            "vespera publish stopped: the site answered HTTP " + NOT_FOUND))).containsExactly(false, true));
            claim("it sent nothing but reads, and so made no tree", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
        }
    }

    @Test
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A seed partition's page and its group's page the rendering no longer has, someone's page under the group's, are neither deleted, and each line says why")
    void aSeedPartitionWhoseClusterHoldsAPersonsPageIsLeftWithIt() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            StubConfluence.Page partition = site.aPageOfTheTree(page(site, Rendering.ROOT_TITLE).id,
                    FORMER_PARTITION_TITLE, FORMER_PARTITION_KEY);
            StubConfluence.Page cluster = site.aPageOfTheTree(partition.id, FORMER_CLUSTER_TITLE, FORMER_CLUSTER_KEY);
            site.aPageNoPublishMadeUnder(cluster.id, A_PERSONS_PAGE);
            site.forgetTheRequests();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);

            claim("no DELETE was sent, of the group's page, of the seed partition's page or of any",
                    () -> assertThat(site.sent()).filteredOn(request -> request.method().equals("DELETE"))
                            .extracting(StubConfluence.Request::path).isEmpty());
            claim("the seed partition's page, the group's page and the page under it stand, and nothing is in the trash",
                    () -> assertThat(List.of(site.pages().stream().map(page -> page.title).toList()
                            .containsAll(List.of(FORMER_PARTITION_TITLE, FORMER_CLUSTER_TITLE, A_PERSONS_PAGE)),
                            site.trash().isEmpty())).containsExactly(true, true));
            claim("the lines count two removals, the group's page first, and say each was left because something"
                            + " stands under it",
                    () -> assertThat(lines.subList(lines.size() - 3, lines.size())).containsExactly(
                            "2 pages of the tree the rendering no longer has are moved to the trash",
                            "removal 1 of 2: " + FORMER_CLUSTER_TITLE + LEFT_HOLDING,
                            "removal 2 of 2: " + FORMER_PARTITION_TITLE + LEFT_HOLDING));
            claim("and the publish went to its end, counting both left and none removed",
                    () -> assertThat(List.of(again.published(),
                            again.closingLine().contains("; 2 pages the rendering no longer has were left as they stand"),
                            again.closingLine().contains("removed"))).containsExactly(true, true, false));
        }
    }

    @Test
    @Story("A title a page outside the tree holds is refused")
    @DisplayName("A later run retitling a page of the tree to a title a page outside it holds stops the publish in one line before anything is written")
    void aRetitleToATitleAPageOutsideTheTreeHoldsStopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            site.aPageNoPublishMadeUnder(StubConfluence.OTHER_PARENT_ID, THE_LATER_GROUPS_TITLE);
            rendering.asTheLaterRunWithTheClusterTitled(THE_LATER_GROUPS_TITLE);
            site.forgetTheRequests();
            Publisher.Outcome later = publish(site, rendering, Rendering.LATER_RUN, new ArrayList<>());

            claim("the publish stopped in one line naming the title",
                    () -> assertThat(List.of(later.published(),
                            later.closingLine().startsWith(STOPPED_BY_TITLE + THE_LATER_GROUPS_TITLE
                                    + " stands in the space"),
                            later.closingLine().contains("\n"))).containsExactly(false, true, false));
            claim("it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("and the tree's page of that place still holds its title, the other page its own",
                    () -> assertThat(site.pages()).extracting(page -> page.title)
                            .contains(Rendering.CLUSTER_TITLE, THE_LATER_GROUPS_TITLE));
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A page no publish made, titled as a later page of the rendering, stops the publish before anything is written")
    void aPageOfALaterTitleNoPublishMadeStopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.aPageNoPublishMade(Rendering.CLUSTER_TITLE);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, naming the title, and at no page",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(Rendering.CLUSTER_TITLE),
                                    outcome.closingLine().contains("It stopped at page")))
                            .containsExactly(false, true, false));
            claim("every request it sent was a read, and the site holds only the page no publish made",
                    () -> assertThat(List.of(site.sent().stream().allMatch(request -> request.method().equals("GET")),
                                    site.pages().size())).containsExactly(true, 1));
            claim("and that page was left as it stood: its first version, no body and no label",
                    () -> assertThat(page(site, Rendering.CLUSTER_TITLE)).satisfies(page -> assertThat(
                            List.of(page.version, page.body, page.labels.size())).containsExactly(1, "", 0)));
        }
    }

    @Test
    @Story("A publish sends to the operator's site alone")
    @DisplayName("Every request a publish sends is addressed to the configured https host")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void everyRequestIsAddressedToTheConfiguredHttpsHost() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish went to its end, so the requests below are the whole of what it sent",
                    () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("each of them was addressed to https and the configured host, and to no other",
                    () -> assertThat(site.sent()).isNotEmpty().allSatisfy(request -> {
                        assertThat(request.addressed().getScheme()).isEqualTo("https");
                        assertThat(request.addressed().getHost()).isEqualTo(StubConfluence.SITE_HOST);
                    }));
        }
    }

    @Test
    @Story("A publish sends to the operator's site alone")
    @DisplayName("An address that is http and not https is refused before anything is sent")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void anAddressThatIsNotTheSitesHttpsOriginIsRefusedBeforeSending() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), AN_HTTP_ADDRESS,
                            StubConfluence.PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish is refused with the line naming the variable",
                    () -> assertThat(List.of(outcome.published(),
                                    outcome.closingLine().contains("VESPERA_CONFLUENCE_BASE_URL")))
                            .containsExactly(false, true));
            claim("and nothing was sent", () -> assertThat(site.sent()).isEmpty());
        }
    }

    @Test
    @Story("A publish sends to the operator's site alone")
    @DisplayName("A link the site answers with to another host is not followed, and the publish stops")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void aLinkToAnotherHostIsNotFollowed() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.nextLinkOfTheLabelSearch("https://" + ANOTHER_HOST + "/wiki/rest/api/content/search?cursor=2");
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, saying the link names another host",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine()
                                    .contains("a link to another host"))).containsExactly(false, true));
            claim("and no request was addressed to it",
                    () -> assertThat(site.sent()).isNotEmpty().noneMatch(request ->
                            ANOTHER_HOST.equals(request.addressed().getHost())));
        }
    }

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("Every request carries the email and token from the environment as Basic authentication")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void everyRequestCarriesTheEnvironmentsEmailAndTokenAsBasic() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        String basic = "Basic " + basicOfTheAccount();
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish went to its end", () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("and each request it sent carried the two as Basic authentication",
                    () -> assertThat(site.sent()).isNotEmpty()
                            .allSatisfy(request -> assertThat(request.header("Authorization")).isEqualTo(basic)));
        }
    }

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("No line a publish prints and no file it writes holds the token, as it goes to its end or stops")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void noLineOrFileThePublishWritesHoldsTheToken() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        List<String> lines = new ArrayList<>();
        List<String> secrets = List.of(StubConfluence.TOKEN, StubConfluence.USER, basicOfTheAccount());
        try (StubConfluence site = StubConfluence.start()) {
            site.refuseAsTooLarge(Rendering.COPY);
            Publisher.Outcome published = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, lines::add);
            lines.add(published.closingLine());
            site.conflictOn(Rendering.CLUSTER_TITLE, 2);
            rendering.withBody(Rendering.CLUSTER, "<p>Written again.</p>");
            Publisher.Outcome stopped = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, lines::add);
            lines.add(stopped.closingLine());

            claim("the first publish went to its end and the second stopped, so both kinds of line were printed",
                    () -> assertThat(List.of(published.published(), stopped.published())).containsExactly(true, false));
            claim("no line holds the token, the email or the Basic value encoding the two",
                    () -> assertThat(lines).isNotEmpty()
                            .noneMatch(line -> secrets.stream().anyMatch(line::contains)));
            claim("and no file under the test's folder, the rendering and the working directory, holds any of them",
                    () -> {
                        try (Stream<Path> files = Files.walk(folder)) {
                            assertThat(files.filter(Files::isRegularFile).toList()).allSatisfy(file -> assertThat(
                                    new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                                    .doesNotContain(secrets.toArray(String[]::new)));
                        }
                    });
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A copy the site refuses as too large is said and counted with its bytes, no file lists it, and the list an earlier version wrote is removed, a line saying so")
    void aFileTheSiteRefusesAsTooLargeIsSaidAndCountedAndListedInNoFile() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path earlierList = workingDirectory.resolve(THE_LIST_AN_EARLIER_VERSION_WROTE);
        Files.writeString(earlierList, AN_EARLIER_LIST, StandardCharsets.UTF_8);
        long copyBytes = Files.size(folder.resolve("tree/001/001/" + Rendering.COPY));
        List<String> lines = new ArrayList<>();
        try (StubConfluence site = StubConfluence.start()) {
            site.refuseAsTooLarge(Rendering.COPY);
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, lines::add);

            claim("the publish went to its end, the copy's line saying the site refused it as too large",
                    () -> assertThat(List.of(outcome.published(), lines.stream().anyMatch(line -> line.endsWith(
                            Rendering.COPY + ", " + copyBytes + " bytes, refused by the site as too large"))))
                            .containsExactly(true, true));
            claim("the closing line counts it and its bytes, and ends there, naming no file",
                    () -> assertThat(outcome.closingLine())
                            .endsWith("; 1 copy was refused by the site as too large and is not on its page, "
                                    + copyBytes + " bytes")
                            .doesNotContain(THE_LIST_AN_EARLIER_VERSION_WROTE, "listed in"));
            claim("the line right after the totals says the earlier list is removed",
                    () -> assertThat(lines.get(1)).isEqualTo(THE_EARLIER_LIST_IS_REMOVED));
            claim("and the working directory is left empty: the earlier list gone, and no list of this publish's",
                    () -> {
                        try (Stream<Path> files = Files.list(workingDirectory)) {
                            assertThat(files.map(file -> file.getFileName().toString()).toList()).isEmpty();
                        }
                    });
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("An earlier list the publish cannot remove is said on a line, left as it stands, and stops nothing")
    void anEarlierListThatCannotBeRemovedIsSaidAndStopsNothing() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path aFolderOfThatName = Files.createDirectories(workingDirectory.resolve(THE_LIST_AN_EARLIER_VERSION_WROTE));
        Files.writeString(aFolderOfThatName.resolve("held.txt"), AN_EARLIER_LIST, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, lines::add);

            claim("the line right after the totals says it could not be removed, and why, and that it is left",
                    () -> assertThat(lines.get(1)).startsWith(THE_EARLIER_LIST_COULD_NOT_BE_REMOVED)
                            .endsWith("), and is left as it stands"));
            claim("the folder of that name stands with what it holds",
                    () -> assertThat(aFolderOfThatName.resolve("held.txt")).hasContent(AN_EARLIER_LIST));
            claim("and the publish went to its end, every page created",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(
                            "3 pages (3 created, 0 updated, 0 unchanged)"))).containsExactly(true, true));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A tree an earlier version published with its run's characters, headed in English, is taken by an English publish as Vespera_en, its places read without their padding")
    void aTreeOfTheMarkersFormHeadedInEnglishIsTakenAsVesperaEnItsPlacesUnpadded() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> earlier = anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER);
            List<String> lines = new ArrayList<>();
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, lines);

            claim("the publish went to its end creating no page, the earlier tree's three updated",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(
                            "tree Vespera_en: 3 pages (0 created, 3 updated, 0 unchanged)")))
                            .containsExactly(true, true));
            claim("and said before the first page that it took the earlier tree as the English one",
                    () -> assertThat(lines).contains("the tree Vespera " + EARLIER_MARKER + " an earlier publish made"
                            + " is taken as the tree Vespera_en: its 3 pages are retitled and updated in place"));
            claim("its pages keep their addresses, retitled, labelled vespera-en alone, the label with the run's"
                            + " characters taken off, and keyed by the code and their place read without its padding",
                    () -> assertThat(describedById(site, earlier)).containsExactly(
                            earlier.get(0) + " Vespera_en [vespera-en] en:root",
                            earlier.get(1) + " Seeds (1 · en) [vespera-en] en:1",
                            earlier.get(2) + " Payroll and leave (1.1 · en) [vespera-en] en:1.1"));
            claim("their files standing at their size and digest, none was sent again",
                    () -> assertThat(site.writes()).extracting(StubConfluence.Write::kind)
                            .doesNotContain(StubConfluence.Kind.FILE));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A taking the site stopped at a seed partition's page and then at the root page is finished by the next publish in its language, the root page keyed last")
    void aTakingStoppedPartWayIsFinishedByTheNextPublishInItsLanguageTheRootPageKeyedLast() throws IOException {
        Path tree = folder.resolve("it");
        Rendering italian = Rendering.drawnIn(tree, Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> earlier = aTreeOfTheEarlierForm(site, tree, AN_ITALIAN_ROOT_BODY);
            site.refuseToTakeALabelOff(earlier.get(1), FORBIDDEN);
            Publisher.Outcome first = publish(site, italian, italian.run(), new ArrayList<>());
            Object rootKeyAfterTheFirst = site.pages().getFirst().property.get("key");
            site.refuseToTakeALabelOff(earlier.get(0), FORBIDDEN);
            Publisher.Outcome second = publish(site, italian, italian.run(), new ArrayList<>());
            Object rootKeyAfterTheSecond = site.pages().getFirst().property.get("key");
            Publisher.Outcome third = publish(site, italian, italian.run(), new ArrayList<>());

            claim("the first two publishes stopped at the label the site would not take off, the third went to its end"
                            + " creating no page",
                    () -> assertThat(List.of(first.published(), second.published(), third.published(),
                            third.closingLine().contains("tree Vespera_it: 3 pages (0 created, 3 updated")))
                            .containsExactly(false, false, true, true));
            claim("after each stop the root page still held its earlier key, its own being the taking's last write",
                    () -> assertThat(List.of(rootKeyAfterTheFirst, rootKeyAfterTheSecond))
                            .containsExactly("root", "root"));
            claim("and once taken, every page keeps its address, labelled vespera-it alone and keyed by the code and"
                            + " its place",
                    () -> assertThat(describedById(site, earlier)).containsExactly(
                            earlier.get(0) + " Vespera_it [vespera-it] it:root",
                            earlier.get(1) + " Seeds (1 · it) [vespera-it] it:1",
                            earlier.get(2) + " Payroll and leave (1.1 · it) [vespera-it] it:1.1"));
            claim("and the site holds no other page", () -> assertThat(site.pages()).hasSize(earlier.size()));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("An earlier root page whose body the site answers 404 or 500 to stops the publish before anything is written")
    void aRootPagesBodyTheSiteWillNotGiveStopsThePublishBeforeAnyWrite() throws IOException {
        for (int status : List.of(NOT_FOUND, SERVER_ERROR)) {
            Path tree = folder.resolve("tree-" + status);
            Rendering rendering = Rendering.drawnIn(tree);
            try (StubConfluence site = StubConfluence.start()) {
                List<String> earlier = aTreeOfTheEarlierForm(site, tree, AN_ITALIAN_ROOT_BODY);
                site.answerTheBodyReadOf(earlier.getFirst(), status);
                Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

                claim("answered " + status + ", the publish stopped saying what the site answered",
                        () -> assertThat(List.of(outcome.published(), outcome.closingLine().startsWith(
                                "vespera publish stopped: the site answered HTTP " + status)))
                                .containsExactly(false, true));
                claim("answered " + status + ", it sent nothing but reads, so no tree was made and none taken",
                        () -> assertThat(site.sent()).isNotEmpty()
                                .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            }
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A root page's heading in bold with a no-break space between its words is read as Italian, and its tree taken by an Italian publish")
    void aHeadingInMarkupWithANoBreakSpaceIsReadAsItsLanguage() throws IOException {
        Path tree = folder.resolve("it");
        Rendering italian = Rendering.drawnIn(tree, Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            aTreeOfTheEarlierForm(site, tree, AN_ITALIAN_ROOT_BODY_IN_MARKUP);
            List<String> lines = new ArrayList<>();
            Publisher.Outcome outcome = publish(site, italian, italian.run(), lines);

            claim("the publish went to its end, taking the earlier tree as the Italian one",
                    () -> assertThat(List.of(outcome.published(), lines.contains("the tree " + THE_EARLIER_ROOT_TITLE
                            + " an earlier publish made is taken as the tree Vespera_it: its 3 pages are retitled and"
                            + " updated in place"))).containsExactly(true, true));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("Of two earlier pages of one key under the root page the earlier made is taken, and the later and one outside the tree are left as they stand")
    void ofTwoEarlierPagesOfOneKeyUnderTheRootTheLowerIdIsTakenAndAPageOutsideTheTreeIsNot() throws IOException {
        Path tree = folder.resolve("it");
        Rendering italian = Rendering.drawnIn(tree, Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            StubConfluence.Page outside = site.aPageAnEarlierPublishMade(StubConfluence.OTHER_PARENT_ID,
                    A_PAGE_OF_ONE_KEY_OUTSIDE, THE_EARLIER_LABEL, THE_GROUPS_EARLIER_KEY, Map.of());
            List<String> earlier = aTreeOfTheEarlierForm(site, tree, AN_ITALIAN_ROOT_BODY);
            StubConfluence.Page later = site.aPageAnEarlierPublishMade(earlier.get(1), A_LATER_PAGE_OF_ONE_KEY,
                    THE_EARLIER_LABEL, THE_GROUPS_EARLIER_KEY, Map.of());
            List<String> strays = List.of(outside.id, later.id);
            List<String> before = describedById(site, strays);
            site.forgetTheRequests();
            Publisher.Outcome outcome = publish(site, italian, italian.run(), new ArrayList<>());

            claim("the publish went to its end, the earlier tree's three pages updated and none created",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(
                            "tree Vespera_it: 3 pages (0 created, 3 updated, 0 unchanged)")))
                            .containsExactly(true, true));
            claim("the group's page the tree made first, the lower id under the root page, is the one taken",
                    () -> assertThat(describedById(site, List.of(earlier.get(2)))).containsExactly(
                            earlier.get(2) + " Payroll and leave (1.1 · it) [vespera-it] it:1.1"));
            claim("the page of that key made later under the root page and the one under another parent stand as they"
                            + " stood, titled, labelled and keyed as before",
                    () -> assertThat(describedById(site, strays)).isEqualTo(before));
            claim("and the publish sent no request but a read naming either",
                    () -> assertThat(writesNaming(site, strays)).isEmpty());
        }
    }

    @Test
    @Story("Each deliverable language is a tree of its own")
    @DisplayName("The language's own root page that lost its label is found by its title, so no earlier tree is looked for or said to be left, and it is labelled again")
    void itsOwnRootPageThatLostItsLabelIsFoundByItsTitleAndNoEarlierTreeIsLookedFor() throws IOException {
        Path tree = folder.resolve("it");
        Rendering italian = Rendering.drawnIn(tree, Rendering.ITALIAN);
        try (StubConfluence site = StubConfluence.start()) {
            aTreeOfTheEarlierForm(site, tree, AN_ENGLISH_ROOT_BODY);
            publish(site, italian, italian.run(), new ArrayList<>());
            StubConfluence.Page root = page(site, italian.rootTitle());
            root.labels.remove(italian.label());
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, italian, italian.run(), lines);

            claim("the publish went to its end creating no page",
                    () -> assertThat(List.of(again.published(), again.closingLine().contains(
                            "tree Vespera_it: 3 pages (0 created, 0 updated, 3 unchanged)")))
                            .containsExactly(true, true));
            claim("no line speaks of a tree an earlier publish made, left or taken",
                    () -> assertThat(lines).noneMatch(line -> line.contains("an earlier publish made")));
            claim("and the root page carries its language's label again, keyed as it was",
                    () -> assertThat(described(page(site, italian.rootTitle())))
                            .isEqualTo("Vespera_it [vespera-it] it:root"));
        }
    }

    @Test
    @Story("A parent's title naming no page or several asks for another")
    @Link(name = "ADR-383", url = Adr.A_PARENT_TITLE_NAMING_NO_PAGE_OR_SEVERAL_ASKS_FOR_ANOTHER_IN_A_WARNING, type = "adr")
    @Link(name = "ADR-388", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
    @DisplayName("Started by the control panel, a parent's title the profile names that no page holds, or two, stops the publish before any write with an exit code of its own each, naming the profile's keys and the profile window")
    void fromTheControlPanelATitleNamingNoPageOrTwoStopsWithItsOwnCodeNamingTheProfilesKeys() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Plugin.Delivery none = fromTheControlPanel(site, TITLE_OF_NO_PAGE)
                    .deliver(rendering.confluence(), Rendering.RUN, folder, line -> { });
            site.aPageNoPublishMade(StubConfluence.PARENT_TITLE);
            Plugin.Delivery two = fromTheControlPanel(site, StubConfluence.PARENT_TITLE)
                    .deliver(rendering.confluence(), Rendering.RUN, folder, line -> { });

            claim("a title no page holds stopped the publish with exit code " + NO_PAGE_SO_TITLED + ", its line naming"
                            + " confluenceFolderTitle, confluenceFolderId and the profile window, and no variable",
                    () -> assertThat(none).isEqualTo(
                            new Plugin.Delivery(false, FROM_THE_CONTROL_PANEL_NO_PAGE, NO_PAGE_SO_TITLED)));
            claim("a title a folder and a page both hold stopped it with exit code " + SEVERAL_SO_TITLED + ", its line"
                            + " saying so",
                    () -> assertThat(two).isEqualTo(
                            new Plugin.Delivery(false, FROM_THE_CONTROL_PANEL_TWO, SEVERAL_SO_TITLED)));
            claim("and neither sent anything but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
        }
    }

    @Test
    @Story("A parent's title naming no page or several asks for another")
    @Link(name = "ADR-383", url = Adr.A_PARENT_TITLE_NAMING_NO_PAGE_OR_SEVERAL_ASKS_FOR_ANOTHER_IN_A_WARNING, type = "adr")
    @DisplayName("Typed on the command line, a parent's title that no page holds, or two, stops the publish as before: exit code 1 and the line naming the folder id's variable")
    void fromTheCommandLineATitleNamingNoPageOrTwoStopsAsBefore() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Plugin.Delivery none = new ConfluencePlugin(site.publisher(StubConfluence.theAccount(),
                    StubConfluence.BASE_URL, "", TITLE_OF_NO_PAGE, new ArrayList<>()))
                    .deliver(rendering.confluence(), Rendering.RUN, folder, line -> { });
            site.aPageNoPublishMade(StubConfluence.PARENT_TITLE);
            Plugin.Delivery two = new ConfluencePlugin(site.publisher(StubConfluence.theAccount(),
                    StubConfluence.BASE_URL, "", StubConfluence.PARENT_TITLE, new ArrayList<>()))
                    .deliver(rendering.confluence(), Rendering.RUN, folder, line -> { });

            claim("each stopped with exit code " + STOPPED + " and the line it stopped with before, word for word",
                    () -> assertThat(List.of(none, two)).containsExactly(
                            new Plugin.Delivery(false, ON_THE_COMMAND_LINE_NO_PAGE, STOPPED),
                            new Plugin.Delivery(false, ON_THE_COMMAND_LINE_TWO, STOPPED)));
        }
    }

    /**
     * Confluence's plugin as a publish the control panel started has it: the environment holding the token and {@code
     * VESPERA_STARTED_BY=control-panel} alone, no property set, and the profile naming the site, the space, the parent
     * by {@code title} and the email (ADR-359 §2, §3).
     */
    private static ConfluencePlugin fromTheControlPanel(StubConfluence site, String title) {
        Map<String, String> environment = Map.of(ConfluenceSite.TOKEN_VARIABLE, StubConfluence.TOKEN,
                ConfluenceSite.STARTED_BY_VARIABLE, ConfluenceSite.THE_CONTROL_PANEL);
        ConfluenceValues profile = new ConfluenceValues(StubConfluence.BASE_URL, StubConfluence.SPACE_KEY, null, title,
                StubConfluence.USER);
        return new ConfluencePlugin(new Publisher(environment::get, site.requests(), new ArrayList<Duration>()::add,
                Clock.systemUTC(), "", "", "", "", workingDirectory -> profile));
    }

    private Publisher.Outcome publish(StubConfluence site, Rendering rendering, String run, List<String> lines) {
        return site.publisher(new ArrayList<>()).publish(rendering.confluence(), run, folder, lines::add);
    }

    /** The closing line of a publish that went to its end, to the colon after the tree it names (ADR-378 §7). */
    private static String closingLineOf(String marker, String tree) {
        return "vespera publish sent the rendering of generation run " + marker + " to " + StubConfluence.SITE_HOST
                + ", space " + StubConfluence.SPACE_KEY + ", tree " + tree + ": ";
    }

    /** A page of the site as a test compares it: its title, its labels in order and its property's key. */
    private static String described(StubConfluence.Page page) {
        return page.title + " " + new TreeSet<>(page.labels) + " " + (page.property == null ? null : page.property.get("key"));
    }

    /** The pages of {@code ids}, in that order, each with its id first and then as {@link #described} has it. */
    private static List<String> describedById(StubConfluence site, List<String> ids) {
        return ids.stream().map(id -> site.pages().stream().filter(page -> page.id.equals(id)).findFirst()
                .map(page -> page.id + " " + described(page)).orElse(id + " not in the space")).toList();
    }

    /** Every request but a read that names one of {@code ids} as a segment of its path, as its method and path. */
    private static List<String> writesNaming(StubConfluence site, List<String> ids) {
        return site.sent().stream()
                .filter(request -> !request.method().equals("GET"))
                .filter(request -> Arrays.stream(request.path().split("/")).anyMatch(ids::contains))
                .map(request -> request.method() + " " + request.path())
                .toList();
    }

    /**
     * Puts under the parent the tree an earlier publish made under ADR-328, which named no language: its root page
     * {@code Vespera}, its body {@code rootBody}, and its seed partition's and group's pages titled by their place
     * alone, all labelled {@code vespera} and keyed by their place, carrying the files the rendering in {@code tree}
     * lists at their digests; gives the pages' ids, root first.
     */
    private static List<String> aTreeOfTheEarlierForm(StubConfluence site, Path tree, String rootBody)
            throws IOException {
        StubConfluence.Page root = site.aPageAnEarlierPublishMade(StubConfluence.PARENT_ID, THE_EARLIER_ROOT_TITLE,
                THE_EARLIER_LABEL, "root",
                Map.of(Rendering.LISTING, Files.readAllBytes(tree.resolve(Rendering.LISTING))));
        root.body = rootBody;
        StubConfluence.Page partition = site.aPageAnEarlierPublishMade(root.id, THE_EARLIER_PARTITION_TITLE,
                THE_EARLIER_LABEL, "1", Map.of());
        StubConfluence.Page cluster = site.aPageAnEarlierPublishMade(partition.id, THE_EARLIER_CLUSTER_TITLE,
                THE_EARLIER_LABEL, "1.1", Map.of(
                        Rendering.COPY, Files.readAllBytes(tree.resolve("001/001/" + Rendering.COPY)),
                        Rendering.PICTURE, Files.readAllBytes(tree.resolve("confluence/001/001/" + Rendering.PICTURE))));
        return List.of(root.id, partition.id, cluster.id);
    }

    /**
     * Puts under the parent the tree an earlier publish made of {@code run} under ADR-310 and ADR-316: its three pages
     * titled with {@code marker}, labelled {@code vespera-<marker>}, keyed {@code <run>:<place>}, its root page's body
     * opening with the English heading and the run's id as such a drawing opened it (ADR-315 §2), and carrying the
     * files the rendering in {@code tree} lists at their digests; gives the pages' ids, root first.
     */
    private static List<String> anEarlierTree(StubConfluence site, Path tree, String run, String marker)
            throws IOException {
        return anEarlierTreeUnder(site, StubConfluence.PARENT_ID, tree, run, marker);
    }

    /**
     * {@link #anEarlierTree}, under the page or folder {@code parentId}, its places padded to two digits as ADR-310
     * padded a place of a rendering of ten or more, {@code 01} and {@code 01.01}.
     */
    private static List<String> anEarlierTreeUnder(StubConfluence site, String parentId, Path tree, String run,
            String marker) throws IOException {
        String label = "vespera-" + marker;
        StubConfluence.Page root = site.aPageAnEarlierPublishMade(parentId, "Vespera " + marker, label,
                run + ":root", Map.of(Rendering.LISTING, Files.readAllBytes(tree.resolve(Rendering.LISTING))));
        root.body = "<h1>Deliverable " + run + "</h1>\n<p>An earlier body.</p>";
        StubConfluence.Page partition = site.aPageAnEarlierPublishMade(root.id, "Seeds (01 · " + marker + ")", label,
                run + ":01", Map.of());
        StubConfluence.Page cluster = site.aPageAnEarlierPublishMade(partition.id,
                "Payroll and leave (01.01 · " + marker + ")", label, run + ":01.01", Map.of(
                        Rendering.COPY, Files.readAllBytes(tree.resolve("001/001/" + Rendering.COPY)),
                        Rendering.PICTURE, Files.readAllBytes(tree.resolve("confluence/001/001/" + Rendering.PICTURE))));
        return List.of(root.id, partition.id, cluster.id);
    }

    /** The account's email and token as Basic authentication encodes them, with no {@code Basic} before them. */
    private static String basicOfTheAccount() {
        return Base64.getEncoder().encodeToString(
                (StubConfluence.USER + ":" + StubConfluence.TOKEN).getBytes(StandardCharsets.UTF_8));
    }

    private static int indexOf(List<StubConfluence.Request> sent, String method, String path) {
        for (int at = 0; at < sent.size(); at++) {
            if (sent.get(at).method().equals(method) && sent.get(at).path().equals(path)) {
                return at;
            }
        }
        return -1;
    }

    private static StubConfluence.Page page(StubConfluence site, String title) {
        return site.pages().stream().filter(page -> page.title.equals(title)).findFirst().orElseThrow();
    }
}
