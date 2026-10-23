package io.algernon.vespera.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * A publish sends each page whole before it creates the next, its files before its body, and says how far it has got
 * (ADR-320): a line of totals before any request, a line as each page starts, a line for each file, the page's own
 * line once its body is sent, and on a stop the page it stopped at. Against a stub; every text is synthetic.
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-320", url = Adr.A_PUBLISH_SENDS_EACH_PAGE_WHOLE_AND_SAYS_HOW_FAR_IT_HAS_GOT, type = "adr")
@Link(name = "ADR-316", url = Adr.THE_PAGE_LIST_IS_PAGES_JSON, type = "adr")
@Link(name = "ADR-313", url = Adr.A_CLUSTERS_CONFLUENCE_PAGE_CARRIES_ITS_COPIES_AS_FILES, type = "adr")
@Tag("confluence")
class PublishSendsEachPageWholeIT {

    private static final Pattern NAMED_FILE = Pattern.compile("ri:filename=\"([^\"]*)\"");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The rendering's pages in the page list's order, by title. */
    private static final List<String> ORDER =
            List.of(Rendering.ROOT_TITLE, Rendering.PARTITION_TITLE, Rendering.CLUSTER_TITLE);

    /** The files each page carries, by its title, in the page list's order. */
    private static final Map<String, List<String>> FILES = Map.of(
            Rendering.ROOT_TITLE, List.of(Rendering.LISTING),
            Rendering.PARTITION_TITLE, List.of(),
            Rendering.CLUSTER_TITLE, List.of(Rendering.COPY, Rendering.PICTURE));

    @TempDir
    Path folder;

    /** A line the publish printed, and how many requests the site had been sent when it printed it. */
    private record Seen(String line, int requestsBefore) {}

    @Test
    @Story("A publish says how far it has got")
    @DisplayName("The first line gives the pages, the files and their bytes the page list names, before any request is sent")
    void theTotalsComeBeforeAnyRequest() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            List<Seen> seen = new ArrayList<>();
            publish(site, rendering, seen);

            claim("the first line is the totals: three pages and three files holding their bytes",
                    () -> assertThat(seen.getFirst().line()).isEqualTo(totals()));
            claim("and it was printed before the site was sent any request",
                    () -> assertThat(seen.getFirst().requestsBefore()).isZero());
        }
    }

    @Test
    @Story("A publish says how far it has got")
    @DisplayName("Each page is announced by its place and its files, each file is said as it is sent, and the page's line closes it")
    void eachPageIsAnnouncedThenItsFilesThenItsClosingLine() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            List<Seen> seen = new ArrayList<>();
            publish(site, rendering, seen);

            claim("the lines are the totals, then for each page its start, its files and its closing line",
                    () -> assertThat(seen).extracting(Seen::line).containsExactly(
                            totals(),
                            "page 1 of 3: " + Rendering.ROOT_TITLE + ", 1 file holding " + listing() + " bytes",
                            "file 1 of 3: " + Rendering.LISTING + ", " + listing() + " bytes, sent",
                            Rendering.ROOT_TITLE + ": created; files: 1 sent, 0 unchanged",
                            "page 2 of 3: " + Rendering.PARTITION_TITLE + ", no files",
                            Rendering.PARTITION_TITLE + ": created",
                            "page 3 of 3: " + Rendering.CLUSTER_TITLE + ", 2 files holding " + (copy() + picture())
                                    + " bytes",
                            "file 2 of 3: " + Rendering.COPY + ", " + copy() + " bytes, sent",
                            "file 3 of 3: " + Rendering.PICTURE + ", " + picture() + " bytes, sent",
                            Rendering.CLUSTER_TITLE + ": created; files: 2 sent, 0 unchanged"));
        }
    }

    @Test
    @Story("A publish says how far it has got")
    @DisplayName("Everything sent about a page is sent after the line that starts it and before the line that closes it")
    void everythingAboutAPageIsSentBetweenItsTwoLines() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            List<Seen> seen = new ArrayList<>();
            publish(site, rendering, seen);
            List<StubConfluence.Request> sent = site.sent();
            List<String> outside = new ArrayList<>();
            List<String> unsent = new ArrayList<>();
            List<String> unannounced = new ArrayList<>();
            for (int place = 0; place < ORDER.size(); place++) {
                String title = ORDER.get(place);
                String id = page(site, title).id;
                String start = "page " + (place + 1) + " of " + ORDER.size() + ": " + title;
                int from = seen.stream().filter(line -> line.line().startsWith(start)).findFirst()
                        .map(Seen::requestsBefore).orElse(-1);
                int to = seen.stream().filter(line -> line.line().startsWith(title + ":")).findFirst()
                        .map(Seen::requestsBefore).orElse(-1);
                if (from < 0 || to < 0) {
                    unannounced.add(title);
                    continue;
                }
                List<Integer> about = IntStream.range(0, sent.size())
                        .filter(at -> concerns(sent.get(at), title, id)).boxed().toList();
                if (about.isEmpty()) {
                    unsent.add(title);
                }
                about.stream().filter(at -> at < from || at >= to)
                        .forEach(at -> outside.add(title + " <- " + sent.get(at).method() + " " + sent.get(at).path()));
            }

            claim("each page has a line that starts it and a line that closes it",
                    () -> assertThat(unannounced).isEmpty());
            claim("each page was sent requests: its create, its label, its property, its files and its body",
                    () -> assertThat(unsent).isEmpty());
            claim("and none of them before the line that starts the page, or after the line that closes it",
                    () -> assertThat(outside).isEmpty());
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A page is created, its files sent and then its body, before the next page is created; parents first")
    void aPageIsFilledBeforeTheNextIsCreated() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, new ArrayList<>());
            List<StubConfluence.Write> writes = site.writes();
            List<String> early = new ArrayList<>();
            for (int at = 0; at < writes.size(); at++) {
                StubConfluence.Write write = writes.get(at);
                if (write.kind() != StubConfluence.Kind.CREATED) {
                    continue;
                }
                List<StubConfluence.Write> before = writes.subList(0, at);
                for (String earlier : ORDER.subList(0, ORDER.indexOf(write.page()))) {
                    if (before.stream().noneMatch(w -> w.kind() == StubConfluence.Kind.BODY && w.page().equals(earlier))) {
                        early.add(write.page() + " created before the body of " + earlier);
                    }
                    for (String file : FILES.get(earlier)) {
                        if (!before.contains(new StubConfluence.Write(StubConfluence.Kind.FILE, earlier, file))) {
                            early.add(write.page() + " created before " + file + " of " + earlier);
                        }
                    }
                }
            }
            List<StubConfluence.Page> pages = site.pages();

            claim("the site took each page's create, then its files in the list's order, then its body, a page at a time",
                    () -> assertThat(writes).extracting(StubConfluence.Write::toString).containsExactly(
                            write(StubConfluence.Kind.CREATED, Rendering.ROOT_TITLE, null),
                            write(StubConfluence.Kind.FILE, Rendering.ROOT_TITLE, Rendering.LISTING),
                            write(StubConfluence.Kind.BODY, Rendering.ROOT_TITLE, null),
                            write(StubConfluence.Kind.CREATED, Rendering.PARTITION_TITLE, null),
                            write(StubConfluence.Kind.BODY, Rendering.PARTITION_TITLE, null),
                            write(StubConfluence.Kind.CREATED, Rendering.CLUSTER_TITLE, null),
                            write(StubConfluence.Kind.FILE, Rendering.CLUSTER_TITLE, Rendering.COPY),
                            write(StubConfluence.Kind.FILE, Rendering.CLUSTER_TITLE, Rendering.PICTURE),
                            write(StubConfluence.Kind.BODY, Rendering.CLUSTER_TITLE, null)));
            claim("no page was created while a page before it in the list still lacked its body or a file",
                    () -> assertThat(early).isEmpty());
            claim("parents first: the root page under the parent named, each other under its parent's page",
                    () -> assertThat(pages).extracting(page -> page.title + " < " + page.parentId).containsExactly(
                            Rendering.ROOT_TITLE + " < " + StubConfluence.PARENT_ID,
                            Rendering.PARTITION_TITLE + " < " + pages.get(0).id,
                            Rendering.CLUSTER_TITLE + " < " + pages.get(1).id));
            claim("each is labelled with the run's marker, and its property holds its key and its body's digest",
                    () -> assertThat(pages).allSatisfy(page -> {
                        assertThat(page.labels).containsExactly("vespera-" + Rendering.MARKER);
                        assertThat(page.property).containsOnlyKeys("key", "bodySha256");
                        assertThat((String) page.property.get("key")).startsWith(Rendering.RUN + ":");
                    }));
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("No body is sent naming a file its page does not hold at that moment")
    void noBodyIsSentNamingAFileItsPageDoesNotHold() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, new ArrayList<>());
            List<StubConfluence.BodyTaken> bodies = site.bodiesTaken();

            claim("the site took one body for each page, in the list's order",
                    () -> assertThat(bodies).extracting(StubConfluence.BodyTaken::page).containsExactlyElementsOf(ORDER));
            claim("the bodies name files, so the claim below is about something: the root page its listing, the"
                            + " cluster's page its copy and its picture",
                    () -> assertThat(List.of(namedIn(bodies.get(0).body()), namedIn(bodies.get(2).body())))
                            .containsExactly(Set.of(Rendering.LISTING), Set.of(Rendering.COPY, Rendering.PICTURE)));
            claim("every file a body names stood on its page when the site took the body",
                    () -> assertThat(bodies).allSatisfy(body ->
                            assertThat(body.filesHeld()).as(body.page()).containsAll(namedIn(body.body()))));
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A copy the site refuses as too large is said so on its line, and its page's body is still sent naming it")
    void aCopyRefusedAsTooLargeIsSaidSoAndItsBodyStillGoes() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.refuseAsTooLarge(Rendering.COPY);
            List<Seen> seen = new ArrayList<>();
            Publisher.Outcome outcome = publish(site, rendering, seen);
            StubConfluence.BodyTaken cluster = site.bodiesTaken().getLast();

            claim("the publish went to its end", () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("the copy's line says the site refused it, and the picture's that it was sent",
                    () -> assertThat(seen).extracting(Seen::line).contains(
                            "file 2 of 3: " + Rendering.COPY + ", " + copy() + " bytes, refused by the site as too large",
                            "file 3 of 3: " + Rendering.PICTURE + ", " + picture() + " bytes, sent"));
            claim("the cluster's body was taken after the picture, naming the copy the site holds not (ADR-313 §3)",
                    () -> assertThat(List.of(cluster.page(), cluster.filesHeld(), namedIn(cluster.body())))
                            .containsExactly(Rendering.CLUSTER_TITLE, Set.of(Rendering.PICTURE),
                                    Set.of(Rendering.COPY, Rendering.PICTURE)));
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A publish stopped at a page leaves the pages before it whole and none after it, and the next publish finishes it")
    void aPublishStoppedHalfwayIsFinishedByTheNext() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.conflictOn(Rendering.PARTITION_TITLE, 2);
            Publisher.Outcome stopped = publish(site, rendering, new ArrayList<>());
            List<String> left = asTheyStand(site);
            site.forgetTheRequests();
            Publisher.Outcome again = publish(site, rendering, new ArrayList<>());

            claim("the first publish stopped at the seed partition's page, saying so, and what it had done",
                    () -> assertThat(stopped.closingLine()).isEqualTo("vespera publish stopped: the page "
                            + Rendering.PARTITION + " was changed on the site while it was sent, twice. It stopped at"
                            + " page 2 of 3, " + Rendering.PARTITION_TITLE + ". It had created 2 pages, updated 0 and"
                            + " sent 1 file. Publish again to go on from there."));
            claim("it left the root page whole, the seed partition's page created with no body, and no cluster's page",
                    () -> assertThat(left).containsExactly(
                            Rendering.ROOT_TITLE + " | true | [" + Rendering.LISTING + "]",
                            Rendering.PARTITION_TITLE + " | false | []"));
            claim("the next publish went to its end, updating the page it stopped at and creating the one after",
                    () -> assertThat(again.closingLine()).contains("3 pages (1 created, 1 updated, 1 unchanged)",
                            "3 files (2 added, 0 replaced, 1 unchanged)"));
            claim("and the site holds every page with its body and its files",
                    () -> assertThat(asTheyStand(site)).containsExactly(
                            Rendering.ROOT_TITLE + " | true | [" + Rendering.LISTING + "]",
                            Rendering.PARTITION_TITLE + " | true | []",
                            Rendering.CLUSTER_TITLE + " | true | [" + Rendering.COPY + ", " + Rendering.PICTURE + "]"));
        }
    }

    @Test
    @Story("A publish says how far it has got")
    @DisplayName("A second publish of the same rendering says each page and each file unchanged, and only reads")
    void aSecondPublishSaysEachPageAndFileUnchanged() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            publish(site, rendering, new ArrayList<>());
            site.forgetTheRequests();
            List<Seen> seen = new ArrayList<>();
            publish(site, rendering, seen);

            claim("the lines say each page and each file unchanged",
                    () -> assertThat(seen).extracting(Seen::line).containsExactly(
                            totals(),
                            "page 1 of 3: " + Rendering.ROOT_TITLE + ", 1 file holding " + listing() + " bytes",
                            "file 1 of 3: " + Rendering.LISTING + ", " + listing() + " bytes, unchanged",
                            Rendering.ROOT_TITLE + ": unchanged; files: 0 sent, 1 unchanged",
                            "page 2 of 3: " + Rendering.PARTITION_TITLE + ", no files",
                            Rendering.PARTITION_TITLE + ": unchanged",
                            "page 3 of 3: " + Rendering.CLUSTER_TITLE + ", 2 files holding " + (copy() + picture())
                                    + " bytes",
                            "file 2 of 3: " + Rendering.COPY + ", " + copy() + " bytes, unchanged",
                            "file 3 of 3: " + Rendering.PICTURE + ", " + picture() + " bytes, unchanged",
                            Rendering.CLUSTER_TITLE + ": unchanged; files: 0 sent, 2 unchanged"));
            claim("and the site took no write", () -> assertThat(site.writes()).isEmpty());
        }
    }

    @Test
    @Story("A publish sends each page whole")
    @DisplayName("A page no publish made, titled as a later page of the rendering, stops the publish before anything is written")
    void aPageOfALaterTitleNoPublishMadeStopsThePublishBeforeAnyWrite() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.aPageNoPublishMade(Rendering.CLUSTER_TITLE);
            List<Seen> seen = new ArrayList<>();
            Publisher.Outcome outcome = publish(site, rendering, seen);

            claim("the publish stopped, naming the title, and at no page",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(Rendering.CLUSTER_TITLE),
                                    outcome.closingLine().contains("It stopped at page")))
                            .containsExactly(false, true, false));
            claim("it printed the totals and no page's line",
                    () -> assertThat(seen).extracting(Seen::line).containsExactly(totals()));
            claim("every request it sent was a read, and the site holds only the page no publish made",
                    () -> assertThat(List.of(site.sent().stream().allMatch(request -> request.method().equals("GET")),
                                    site.pages().size())).containsExactly(true, 1));
        }
    }

    @Test
    @Story("A publish says how far it has got")
    @DisplayName("A publish refused before it sends prints no totals and no page's line")
    void aPublishRefusedBeforeSendingPrintsNoTotals() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            List<String> lines = new ArrayList<>();
            Publisher.Outcome outcome = site.publisher(name -> null, StubConfluence.BASE_URL, StubConfluence.PARENT_ID,
                    "", new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, lines::add);

            claim("the publish sent nothing and printed nothing but its refusal",
                    () -> assertThat(List.of(outcome.published(), site.sent().isEmpty(), lines.isEmpty()))
                            .containsExactly(false, true, true));
        }
    }

    private Publisher.Outcome publish(StubConfluence site, Rendering rendering, List<Seen> seen) {
        return site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder,
                line -> seen.add(new Seen(line, site.sent().size())));
    }

    private String totals() throws IOException {
        return "vespera publish is sending the rendering of generation run " + Rendering.MARKER + " to "
                + StubConfluence.SITE_HOST + ", space " + StubConfluence.SPACE_KEY + ": 3 pages and 3 files holding "
                + (listing() + copy() + picture()) + " bytes";
    }

    private long listing() throws IOException {
        return Files.size(folder.resolve("tree/" + Rendering.LISTING));
    }

    private long copy() throws IOException {
        return Files.size(folder.resolve("tree/001/001/" + Rendering.COPY));
    }

    private long picture() throws IOException {
        return Files.size(folder.resolve("tree/confluence/001/001/" + Rendering.PICTURE));
    }

    /** Each page the site holds as {@code title | whether it holds a body | its files' names}, taken now. */
    private static List<String> asTheyStand(StubConfluence site) {
        return site.pages().stream().map(page -> page.title + " | " + !page.body.isEmpty() + " | " + page.files.keySet())
                .toList();
    }

    private static String write(StubConfluence.Kind kind, String page, String file) {
        return new StubConfluence.Write(kind, page, file).toString();
    }

    private static Set<String> namedIn(String body) {
        Set<String> named = new java.util.HashSet<>();
        Matcher matcher = NAMED_FILE.matcher(body);
        while (matcher.find()) {
            named.add(matcher.group(1));
        }
        return named;
    }

    /** Whether {@code request} creates the page titled {@code title} or names its id. */
    private static boolean concerns(StubConfluence.Request request, String title, String id) {
        String path = request.path();
        if (request.method().equals("POST") && path.equals("/wiki/api/v2/pages")) {
            return JSON.readTree(request.body()).path("title").asString().equals(title);
        }
        return path.endsWith("/pages/" + id) || path.contains("/pages/" + id + "/")
                || path.contains("/content/" + id + "/");
    }

    private static StubConfluence.Page page(StubConfluence site, String title) {
        return site.pages().stream().filter(page -> page.title.equals(title)).findFirst().orElseThrow();
    }
}
