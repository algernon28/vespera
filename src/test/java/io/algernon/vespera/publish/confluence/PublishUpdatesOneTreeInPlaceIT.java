package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
 * <p>It also holds, since this is the one class publishing to the stub, what ADR-308 §2 and §3 decide of every publish:
 * each request goes to the configured {@code https} host alone, an {@code http} address is refused and a link to
 * another host is not followed, before anything is sent there; and the environment's email and token go as Basic
 * authentication on every request and in no line or file the publish writes.
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

    /** What a removal's line says of a page left because something stands under it (ADR-328 §5). */
    private static final String LEFT_HOLDING =
            ", left as it stands: something stands under it, which its removal could take with it";

    /** What a removal's line says of a page the site no longer holds (ADR-328 §5). */
    private static final String ALREADY_GONE = ", already gone";

    /** The status a site answers a page, a label or a listing it does not hold with, and a failure of its own. */
    private static final int NOT_FOUND = 404;

    /** Pages of the tree an earlier publish of a larger rendering left, which this rendering no longer has. */
    private static final String FORMER_PARTITION_TITLE = "Former seeds (2)";

    private static final String FORMER_CLUSTER_TITLE = "Former payroll (2.1)";

    /** A page someone put in the space, which no publish made. */
    private static final String A_PERSONS_PAGE = "Notes on overtime";

    /** The line a publish stopped by a title a page outside the tree holds begins with (ADR-320 §3). */
    private static final String STOPPED_BY_TITLE = "vespera publish stopped: a page titled ";

    /** An address of the site that is http, not https, which no request may be sent to. */
    private static final String AN_HTTP_ADDRESS = "http://" + StubConfluence.SITE_HOST;

    /** A host other than the site's, which a link the site answers with may name. */
    private static final String ANOTHER_HOST = "elsewhere.example";

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
            claim("and no file in the working directory holds any of them, the list of what was not carried among them",
                    () -> {
                        assertThat(workingDirectory.resolve(Publisher.NOT_CARRIED_FILE_NAME)).exists();
                        try (Stream<Path> files = Files.walk(folder)) {
                            assertThat(files.filter(Files::isRegularFile).toList()).allSatisfy(file -> assertThat(
                                    new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                                    .doesNotContain(secrets.toArray(String[]::new)));
                        }
                    });
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
