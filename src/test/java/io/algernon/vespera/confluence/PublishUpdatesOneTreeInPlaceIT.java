package io.algernon.vespera.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Confluence rendering is one tree under the parent, which every publish updates in place, whichever generation
 * run drew it (ADR-328): a page is matched by its place; a page the rendering no longer has is moved to the trash after
 * every page is sent, deepest first, one with anything under it, or one the site refuses to delete, being left and said;
 * a tree an earlier publish made with its run's marker in its titles is taken and retitled in place, a taking stopped
 * half way ending on the next publish; and a title a page outside the tree holds, for a page created, retitled or
 * taken, a parent the site will not say is a folder or a page or will not list, a tree under another parent and a page
 * list of the earlier format stop the publish before anything is written. Against a stub; every text is synthetic.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("667")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
@Link(name = "ADR-320", url = Adr.A_PUBLISH_SENDS_EACH_PAGE_WHOLE_AND_SAYS_HOW_FAR_IT_HAS_GOT, type = "adr")
@Tag("confluence")
class PublishUpdatesOneTreeInPlaceIT {

    /** The title the later run's group at place 1.1 is drawn with. */
    private static final String THE_LATER_GROUPS_TITLE = "Overtime and pay (1.1)";

    /** A generation run an earlier publish sent under ADR-310, its marker in every title, and its marker. */
    private static final String EARLIER_RUN = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    private static final String EARLIER_MARKER = "fedcba987654";

    /** A second such run, for a parent holding two trees of an earlier publish. */
    private static final String ANOTHER_EARLIER_RUN = "0f1e2d3c4b5a69780f1e2d3c4b5a69780f1e2d3c4b5a69780f1e2d3c4b5a6978";

    private static final String ANOTHER_EARLIER_MARKER = "0f1e2d3c4b5a";

    /** The message the stub refuses a removal with, and the status: an account that may not delete pages. */
    private static final int FORBIDDEN = 403;

    private static final String NOT_PERMITTED = "You are not permitted to delete this page";

    /** The three pages of the rendering. */
    private static final int THREE_PAGES = 3;

    /** What a removal's line says of a page left because something stands under it (ADR-328 §5). */
    private static final String LEFT_HOLDING =
            ", left as it stands: something stands under it, which its removal could take with it";

    /** What a removal's line says of a page the site no longer holds (ADR-328 §5). */
    private static final String ALREADY_GONE = ", already gone";

    /** What a removal's line says of a page moved to the trash (ADR-328 §5). */
    private static final String MOVED = ", moved to the trash";

    /** The status a site answers a page, a label or a listing it does not hold with, and a failure of its own. */
    private static final int NOT_FOUND = 404;

    private static final int SERVER_ERROR = 500;

    /** Pages of the tree an earlier publish of a larger rendering left, which this rendering no longer has. */
    private static final String FORMER_PARTITION_TITLE = "Former seeds (2)";

    private static final String FORMER_CLUSTER_TITLE = "Former payroll (2.1)";

    /** A page someone put in the space, which no publish made. */
    private static final String A_PERSONS_PAGE = "Notes on overtime";

    /** The line a publish stopped by a title a page outside the tree holds begins with (ADR-320 §3). */
    private static final String STOPPED_BY_TITLE = "vespera publish stopped: a page titled ";

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
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A removal the site refuses leaves the page standing, is said on its line and counted, and the publish still goes to its end")
    void aRemovalTheSiteRefusesIsSaidAndThePageStands() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            site.refuseToRemove(Rendering.CLUSTER_TITLE, FORBIDDEN);
            rendering.withoutTheCluster();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);

            claim("the publish went to its end", () -> assertThat(again.published()).isTrue());
            claim("the group's page stands, and nothing is in the trash",
                    () -> assertThat(List.of(site.pages().stream().anyMatch(page -> page.title.equals(
                            Rendering.CLUSTER_TITLE)), site.trash().isEmpty())).containsExactly(true, true));
            claim("its line says it was left, and what the site answered: 403 and its message",
                    () -> assertThat(lines.getLast()).isEqualTo("removal 1 of 1: " + Rendering.CLUSTER_TITLE
                            + ", left as it stands: the site answered HTTP " + FORBIDDEN + " (" + NOT_PERMITTED + ")"));
            claim("and the closing line counts it left, and none removed",
                    () -> assertThat(again.closingLine())
                            .contains("; 1 page the rendering no longer has was left as it stands")
                            .doesNotContain("was removed"));
        }
    }

    @Test
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A page the rendering no longer has, holding a page someone put under it, is not deleted, and its line says why")
    void aPageHoldingAPageTheTreeDoesNotOwnIsLeftAsItStands() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            StubConfluence.Page notes = site.aPageNoPublishMadeUnder(page(site, Rendering.CLUSTER_TITLE).id,
                    "Notes on payroll");
            rendering.withoutTheCluster();
            site.forgetTheRequests();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);

            claim("no DELETE was sent", () -> assertThat(site.sent()).noneMatch(request -> request.method().equals(
                    "DELETE")));
            claim("the group's page and the page under it stand",
                    () -> assertThat(site.pages()).extracting(page -> page.title)
                            .contains(Rendering.CLUSTER_TITLE, notes.title));
            claim("its line says it was left because something stands under it",
                    () -> assertThat(lines.getLast()).isEqualTo("removal 1 of 1: " + Rendering.CLUSTER_TITLE
                            + LEFT_HOLDING));
            claim("and the publish went to its end, counting it left",
                    () -> assertThat(List.of(again.published(), again.closingLine().contains(
                            "; 1 page the rendering no longer has was left as it stands"))).containsExactly(true, true));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A tree an earlier publish made with its run's characters in every title is taken, its pages retitled in place and their files not sent again")
    void aTreeAnEarlierPublishMadeWithItsMarkerIsTakenAndRetitled() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> ids = anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER);
            List<String> lines = new ArrayList<>();
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, lines);

            claim("the publish went to its end, creating no page and updating the three, every file unchanged",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().contains("3 pages (0 created, 3 updated, 0 unchanged)"),
                            outcome.closingLine().contains("3 files (0 added, 0 replaced, 3 unchanged)")))
                            .containsExactly(true, true, true));
            claim("the site holds the same three pages at the same addresses, now titled without the run's characters",
                    () -> assertThat(site.pages()).extracting(page -> page.id + " " + page.title).containsExactly(
                            ids.get(0) + " " + Rendering.ROOT_TITLE,
                            ids.get(1) + " " + Rendering.PARTITION_TITLE,
                            ids.get(2) + " " + Rendering.CLUSTER_TITLE));
            claim("each now labelled vespera alone, its earlier label taken off, and keyed by its place",
                    () -> assertThat(site.pages()).extracting(page -> page.labels + " " + page.property.get("key"))
                            .containsExactly(
                                    "[" + Rendering.LABEL + "] " + Rendering.ROOT,
                                    "[" + Rendering.LABEL + "] " + Rendering.PARTITION,
                                    "[" + Rendering.LABEL + "] " + Rendering.CLUSTER));
            claim("no page was created and none moved to the trash",
                    () -> assertThat(site.writes()).noneMatch(write -> write.kind() == StubConfluence.Kind.CREATED
                            || write.kind() == StubConfluence.Kind.TRASHED));
            claim("the parent's children were asked for, to find the earlier tree's root page",
                    () -> assertThat(site.sent()).anyMatch(request -> request.method().equals("GET")
                            && request.path().equals("/wiki/api/v2/folders/" + StubConfluence.PARENT_ID
                                    + "/direct-children")));
            claim("and the line after the totals says the earlier tree is taken, naming its root page's title",
                    () -> assertThat(lines.get(1)).isEqualTo("the tree Vespera " + EARLIER_MARKER + " an earlier"
                            + " publish made is taken as this tree: its " + THREE_PAGES + " pages are retitled and"
                            + " updated in place"));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("Two trees an earlier publish made under the parent stop the publish before anything is written, naming both")
    void twoTreesWithTheirMarkerUnderTheParentStopThePublishBeforeAnyWrite() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER);
            anEarlierTree(site, tree, ANOTHER_EARLIER_RUN, ANOTHER_EARLIER_MARKER);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, naming both trees' root pages",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().startsWith("vespera publish stopped: "),
                            outcome.closingLine().contains("Vespera " + EARLIER_MARKER),
                            outcome.closingLine().contains("Vespera " + ANOTHER_EARLIER_MARKER)))
                            .containsExactly(false, true, true, true));
            claim("and it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
        }
    }

    @Test
    @Story("A title a page outside the tree holds is refused")
    @DisplayName("A page titled Vespera that is not the tree's stops the publish in one line before anything is written")
    void aPageTitledVesperaTheTreeDoesNotOwnStopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.aPageNoPublishMade(Rendering.ROOT_TITLE);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped in one line naming the title",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().startsWith("vespera publish stopped: a page titled "
                                    + Rendering.ROOT_TITLE + " stands in the space"),
                            outcome.closingLine().contains("\n"))).containsExactly(false, true, false));
            claim("it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("and the page was left as it stood: its first version, no body and no label",
                    () -> assertThat(page(site, Rendering.ROOT_TITLE)).satisfies(page -> assertThat(
                            List.of(page.version, page.body, page.labels.size())).containsExactly(1, "", 0)));
        }
    }

    @Test
    @Story("A publish updates one tree in place")
    @DisplayName("A tree standing under another parent than the one named stops the publish before anything is written, saying where it stands")
    void theTreeStandingUnderAnotherParentStopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            site.forgetTheRequests();
            Publisher.Outcome elsewhere = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL,
                            StubConfluence.OTHER_PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish stopped, naming the parent the tree stands under",
                    () -> assertThat(List.of(elsewhere.published(), elsewhere.closingLine().contains(
                            "stands under " + StubConfluence.PARENT_ID + ", not under the parent named")))
                            .containsExactly(false, true));
            claim("it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("and the tree still stands under the parent it was published under, its three pages alone",
                    () -> assertThat(site.pages()).hasSize(THREE_PAGES).first().satisfies(root -> assertThat(
                            root.parentId).isEqualTo(StubConfluence.PARENT_ID)));
        }
    }

    @Test
    @Story("A publish updates one tree in place")
    @DisplayName("A page list drawn in the earlier format, its keys holding the run, is refused before anything is sent")
    void aPageListOfFormatOneIsRefusedBeforeAnythingIsSent() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree")).inFormat(1);
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish sent nothing, saying the list is of the earlier format and to draw the deliverable again",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().startsWith("vespera publish sent nothing: "),
                            outcome.closingLine().contains("it is of format 1, and this publish reads format "
                                    + Rendering.FORMAT),
                            outcome.closingLine().contains("Run vespera run to draw the deliverable again")))
                            .containsExactly(false, true, true, true));
            claim("and the site was sent no request", () -> assertThat(site.sent()).isEmpty());
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
                    FORMER_PARTITION_TITLE, "2");
            StubConfluence.Page cluster = site.aPageOfTheTree(partition.id, FORMER_CLUSTER_TITLE, "2.1");
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
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("Several pages the rendering no longer has are moved to the trash deepest first, and within one depth in the order of their numbers")
    void severalPagesAreRemovedDeepestFirstAndInTheOrderOfTheirNumbers() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            String root = page(site, Rendering.ROOT_TITLE).id;
            StubConfluence.Page two = site.aPageOfTheTree(root, "Second seeds (2)", "2");
            site.aPageOfTheTree(two.id, "Tenth group (2.10)", "2.10");
            site.aPageOfTheTree(page(site, Rendering.CLUSTER_TITLE).id, "Payroll and leave (1.1 page 2)", "1.1.2");
            site.aPageOfTheTree(two.id, "Ninth group (2.9)", "2.9");
            site.aPageOfTheTree(page(site, Rendering.PARTITION_TITLE).id, "Second group (1.2)", "1.2");
            List<String> inOrder = List.of("Payroll and leave (1.1 page 2)", "Second group (1.2)", "Ninth group (2.9)",
                    "Tenth group (2.10)", "Second seeds (2)");
            site.forgetTheRequests();
            List<String> lines = new ArrayList<>();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, lines);

            claim("the five pages went to the trash, the page of three numbers first, then those of two in the order of"
                            + " their numbers, 2.9 before 2.10, then the page of one",
                    () -> assertThat(site.trash()).extracting(page -> page.title).containsExactlyElementsOf(inOrder));
            claim("by five DELETEs, one a page, in that order, and nothing else written",
                    () -> assertThat(site.writes()).extracting(write -> write.kind() + " " + write.page())
                            .containsExactlyElementsOf(inOrder.stream().map(title -> "TRASHED " + title).toList()));
            claim("each removal's line says so, numbered in that order",
                    () -> assertThat(lines.subList(lines.size() - inOrder.size(), lines.size())).containsExactly(
                            "removal 1 of 5: " + inOrder.get(0) + MOVED,
                            "removal 2 of 5: " + inOrder.get(1) + MOVED,
                            "removal 3 of 5: " + inOrder.get(2) + MOVED,
                            "removal 4 of 5: " + inOrder.get(3) + MOVED,
                            "removal 5 of 5: " + inOrder.get(4) + MOVED));
            claim("and the closing line counts five removed",
                    () -> assertThat(again.closingLine()).contains("; 5 pages the rendering no longer has were removed"));
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
    @Story("A publish removes what the rendering no longer has")
    @DisplayName("A publish stopped during the removals says which removal it stopped at, and the page removed before it stays in the trash")
    void aStopDuringTheRemovalsSaysWhichRemovalItStoppedAt() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            String root = page(site, Rendering.ROOT_TITLE).id;
            StubConfluence.Page second = site.aPageOfTheTree(root, "Second seeds (2)", "2");
            StubConfluence.Page third = site.aPageOfTheTree(root, "Third seeds (3)", "3");
            site.answerTheListingOfTheChildrenOf(third.id, SERVER_ERROR);
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped, saying it stopped at the second of two removals, on that page, and to publish"
                            + " again",
                    () -> assertThat(List.of(again.published(),
                            again.closingLine().startsWith("vespera publish stopped: "),
                            again.closingLine().endsWith(". It stopped at removal 2 of 2, " + third.title + ". It had"
                                    + " created 0 pages, updated 0 and sent 0 files. Publish again to go on from there.")))
                            .containsExactly(false, true, true));
            claim("the page removed before the stop is in the trash, and the page it stopped at stands",
                    () -> assertThat(List.of(site.trash().stream().map(page -> page.title).toList(),
                            site.pages().stream().map(page -> page.title).filter(third.title::equals).toList()))
                            .containsExactly(List.of(second.title), List.of(third.title)));
        }
    }

    @Test
    @Story("A publish updates one tree in place")
    @DisplayName("Of two pages of the tree holding one place, the one made first is updated and the other is neither updated nor removed")
    void ofTwoPagesHoldingOneKeyTheLowerIdIsTheTreesAndTheOtherIsUntouched() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, Rendering.RUN, new ArrayList<>());
            StubConfluence.Page first = page(site, Rendering.CLUSTER_TITLE);
            int firstsVersion = first.version;
            StubConfluence.Page copy = site.aPageOfTheTree(StubConfluence.OTHER_PARENT_ID,
                    "Copy of " + Rendering.CLUSTER_TITLE, Rendering.CLUSTER);
            String copysState = stateOf(copy);
            site.listTheLabelsSearchNewestFirst();
            rendering.withBody(Rendering.CLUSTER, "<p>What the group holds now.</p>");
            site.forgetTheRequests();
            Publisher.Outcome again = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish went to its end, updating one page and removing none",
                    () -> assertThat(List.of(again.published(),
                            again.closingLine().contains("3 pages (0 created, 1 updated, 2 unchanged)"),
                            again.closingLine().contains("removed"))).containsExactly(true, true, false));
            claim("the one write was the new body, to the page titled as the group's",
                    () -> assertThat(site.writes()).singleElement().isEqualTo(
                            new StubConfluence.Write(StubConfluence.Kind.BODY, Rendering.CLUSTER_TITLE, null)));
            claim("that page is the one made first, the lower id, though the label's search found it last, now at its"
                            + " next version",
                    () -> assertThat(page(site, Rendering.CLUSTER_TITLE)).satisfies(page -> assertThat(
                            List.of(page.id, "" + page.version)).containsExactly(first.id, "" + (firstsVersion + 1))));
            claim("and the other page holding that place is as it was: title, version, body, labels and property",
                    () -> assertThat(site.pages()).filteredOn(page -> page.id.equals(copy.id)).singleElement()
                            .satisfies(page -> assertThat(stateOf(page)).isEqualTo(copysState)));
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
    @Story("A title a page outside the tree holds is refused")
    @DisplayName("A page titled Vespera outside the tree stops the taking of a tree an earlier publish made, in one line before anything is written")
    void aPageTitledVesperaStopsATakingBeforeAnyWrite() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER);
            site.aPageNoPublishMadeUnder(StubConfluence.OTHER_PARENT_ID, Rendering.ROOT_TITLE);
            Publisher.Outcome outcome = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the publish stopped in one line naming the title",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().startsWith(STOPPED_BY_TITLE + Rendering.ROOT_TITLE
                                    + " stands in the space"),
                            outcome.closingLine().contains("\n"))).containsExactly(false, true, false));
            claim("it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("and the earlier tree is as it was: its run's characters in its label and its keys",
                    () -> assertThat(site.pages()).filteredOn(page -> !page.title.equals(Rendering.ROOT_TITLE))
                            .extracting(page -> page.labels + " " + page.property.get("key")).containsExactly(
                                    "[vespera-" + EARLIER_MARKER + "] " + EARLIER_RUN + ":root",
                                    "[vespera-" + EARLIER_MARKER + "] " + EARLIER_RUN + ":1",
                                    "[vespera-" + EARLIER_MARKER + "] " + EARLIER_RUN + ":1.1"));
        }
    }

    @ParameterizedTest(name = "the page of the place {0}")
    @ValueSource(strings = {Rendering.PARTITION, Rendering.ROOT})
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A taking stopped before a page's earlier label was taken off takes it off on the next publish")
    void aTakingStoppedBeforeAnEarlierLabelWasTakenOffTakesItOffOnTheNextPublish(String key) throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            List<String> ids = anEarlierTree(site, tree, EARLIER_RUN, EARLIER_MARKER);
            site.refuseToTakeALabelOff(ids.get(key.equals(Rendering.ROOT) ? 0 : 1), SERVER_ERROR);
            Publisher.Outcome stopped = publish(site, rendering, Rendering.RUN, new ArrayList<>());
            Publisher.Outcome resumed = publish(site, rendering, Rendering.RUN, new ArrayList<>());

            claim("the first publish stopped while it took the tree, and the next went to its end",
                    () -> assertThat(List.of(stopped.published(), resumed.published())).containsExactly(false, true));
            claim("the same three pages stand, each labelled vespera alone, no earlier label left, and keyed by its"
                            + " place",
                    () -> assertThat(site.pages()).extracting(page -> page.id + " " + page.labels + " "
                            + page.property.get("key")).containsExactly(
                                    ids.get(0) + " [" + Rendering.LABEL + "] " + Rendering.ROOT,
                                    ids.get(1) + " [" + Rendering.LABEL + "] " + Rendering.PARTITION,
                                    ids.get(2) + " [" + Rendering.LABEL + "] " + Rendering.CLUSTER));
        }
    }

    @Test
    @Story("A tree an earlier publish made is taken as this tree")
    @DisplayName("A parent that is a page has its children listed as a page's, and the tree an earlier publish made under it is taken")
    void aParentThatIsAPageHasItsChildrenListedAsAPagesAndItsTreeTaken() throws IOException {
        Path tree = folder.resolve("tree");
        Rendering rendering = Rendering.drawnIn(tree);
        try (StubConfluence site = StubConfluence.start()) {
            StubConfluence.Page handbook = site.aPageNoPublishMadeUnder(StubConfluence.PARENT_ID, "Handbook");
            List<String> ids = anEarlierTreeUnder(site, handbook.id, tree, EARLIER_RUN, EARLIER_MARKER);
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL,
                    handbook.id, "", new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end, creating no page and updating the three",
                    () -> assertThat(List.of(outcome.published(),
                            outcome.closingLine().contains("3 pages (0 created, 3 updated, 0 unchanged)")))
                            .containsExactly(true, true));
            claim("the page's children were listed as a page's, and never as a folder's",
                    () -> assertThat(site.sent()).filteredOn(request -> request.path().endsWith("/direct-children")
                            && request.path().contains("/" + handbook.id + "/")).extracting(StubConfluence.Request::path)
                            .containsOnly("/wiki/api/v2/pages/" + handbook.id + "/direct-children"));
            claim("and the tree under it was taken, its pages at their addresses keyed by their place",
                    () -> assertThat(site.pages()).filteredOn(page -> ids.contains(page.id))
                            .extracting(page -> page.id + " " + page.property.get("key")).containsExactly(
                                    ids.get(0) + " " + Rendering.ROOT,
                                    ids.get(1) + " " + Rendering.PARTITION,
                                    ids.get(2) + " " + Rendering.CLUSTER));
        }
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {FORBIDDEN, SERVER_ERROR})
    @Story("A publish updates one tree in place")
    @DisplayName("A parent the site will not say is a folder or a page stops the publish before anything is written, and is not taken for a page")
    void aParentTheSiteWillNotSayIsAFolderStopsThePublishBeforeAnyWrite(int status) throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.answerEveryFolderReadWith(status);
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL, "",
                    StubConfluence.PARENT_TITLE, new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN,
                    folder, line -> {});

            claim("the publish stopped, saying what the site answered",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().startsWith(
                            "vespera publish stopped: the site answered HTTP " + status))).containsExactly(false, true));
            claim("it sent nothing but reads", () -> assertThat(site.sent()).isNotEmpty()
                    .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET")));
            claim("and never listed the parent's children as a page's",
                    () -> assertThat(site.sent()).noneMatch(request -> request.path().equals(
                            "/wiki/api/v2/pages/" + StubConfluence.PARENT_ID + "/direct-children")));
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

    /** A page's title, version, body, labels and property, as one text to compare before and after. */
    private static String stateOf(StubConfluence.Page page) {
        return page.title + " | " + page.version + " | " + page.body + " | " + new java.util.TreeSet<>(page.labels)
                + " | " + page.propertyVersion + " " + page.property;
    }

    private Publisher.Outcome publish(StubConfluence site, Rendering rendering, String run, List<String> lines) {
        return site.publisher(new ArrayList<>()).publish(rendering.confluence(), run, folder, lines::add);
    }

    /**
     * Puts under the parent the tree an earlier publish made of {@code run} under ADR-310 and ADR-316: its three pages
     * titled with {@code marker}, labelled {@code vespera-<marker>}, keyed {@code <run>:<place>}, and carrying the files
     * the rendering in {@code tree} lists at their digests; gives the pages' ids, root first.
     */
    private static List<String> anEarlierTree(StubConfluence site, Path tree, String run, String marker)
            throws IOException {
        return anEarlierTreeUnder(site, StubConfluence.PARENT_ID, tree, run, marker);
    }

    /** {@link #anEarlierTree}, under the page or folder {@code parentId}. */
    private static List<String> anEarlierTreeUnder(StubConfluence site, String parentId, Path tree, String run,
            String marker) throws IOException {
        String label = "vespera-" + marker;
        StubConfluence.Page root = site.aPageAnEarlierPublishMade(parentId, "Vespera " + marker, label,
                run + ":root", Map.of(Rendering.LISTING, Files.readAllBytes(tree.resolve(Rendering.LISTING))));
        StubConfluence.Page partition = site.aPageAnEarlierPublishMade(root.id, "Seeds (1 · " + marker + ")", label,
                run + ":1", Map.of());
        StubConfluence.Page cluster = site.aPageAnEarlierPublishMade(partition.id,
                "Payroll and leave (1.1 · " + marker + ")", label, run + ":1.1", Map.of(
                        Rendering.COPY, Files.readAllBytes(tree.resolve("001/001/" + Rendering.COPY)),
                        Rendering.PICTURE, Files.readAllBytes(tree.resolve("confluence/001/001/" + Rendering.PICTURE))));
        return List.of(root.id, partition.id, cluster.id);
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
