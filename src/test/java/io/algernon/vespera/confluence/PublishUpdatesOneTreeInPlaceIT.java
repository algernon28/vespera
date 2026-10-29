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

/**
 * The Confluence rendering is one tree under the parent, which every publish updates in place, whichever generation
 * run drew it (ADR-328): a page is matched by its place; a page the rendering no longer has is moved to the trash after
 * every page is sent, one holding a page the tree does not own, or one the site refuses to delete, being left and said;
 * a tree an earlier publish made with its run's marker in its titles is taken and retitled in place; and a title a page
 * outside the tree holds, a tree under another parent and a page list of the earlier format stop the publish before
 * anything is written. Against a stub; every text is synthetic.
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
            claim("its line says it was left because it holds a page the tree does not own",
                    () -> assertThat(lines.getLast()).isEqualTo("removal 1 of 1: " + Rendering.CLUSTER_TITLE
                            + ", left as it stands: it holds a page this tree does not own"));
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
        String label = "vespera-" + marker;
        StubConfluence.Page root = site.aPageAnEarlierPublishMade(StubConfluence.PARENT_ID, "Vespera " + marker, label,
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
