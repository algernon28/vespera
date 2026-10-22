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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A publish of a rendering published before updates its pages in place, found by their key: a page whose body's digest
 * its property holds is not sent again, a changed one is sent with the next version, a file standing at its size with
 * its digest in its comment is skipped, and nothing is deleted (ADR-308 §8, ADR-316 §4). Against a stub.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-316", url = Adr.THE_PAGE_LIST_IS_PAGES_JSON, type = "adr")
class RepublishUpdatesInPlaceTest {

    @TempDir
    Path folder;

    @Test
    @Story("A publish updates the run's pages in place")
    @DisplayName("A second publish of the same rendering creates nothing, sends no body and no file, and says each is unchanged")
    void aSecondPublishOfTheSameRenderingSendsNothingAgain() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome first = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            List<Integer> versions = site.pages().stream().map(page -> page.version).toList();
            site.forgetTheRequests();
            Publisher.Outcome second = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the first publish created the three pages and sent their three files",
                    () -> assertThat(first.closingLine()).contains("3 pages (3 created, 0 updated, 0 unchanged)",
                            "3 files (3 added, 0 replaced, 0 unchanged)"));
            claim("the second went to its end, saying every page and file is unchanged",
                    () -> assertThat(second.closingLine()).contains("3 pages (0 created, 0 updated, 3 unchanged)",
                            "3 files (0 added, 0 replaced, 3 unchanged)"));
            claim("it sent nothing but reads: no request of it was a POST, a PUT or a DELETE",
                    () -> assertThat(site.sent()).isNotEmpty().allSatisfy(request ->
                            assertThat(request.method()).isEqualTo("GET")));
            claim("and the site holds the same three pages at the same versions",
                    () -> assertThat(site.pages().stream().map(page -> page.version).toList()).isEqualTo(versions));
        }
    }

    @Test
    @Story("A publish updates the run's pages in place")
    @DisplayName("A body drawn again is sent to its page with the next version, and only that page's")
    void aChangedBodyIsSentWithTheNextVersion() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            StubConfluence.Page cluster = page(site, Rendering.CLUSTER_TITLE);
            int before = cluster.version;
            rendering.withBody(Rendering.CLUSTER, "<p>What the group holds, written again.</p>");
            site.forgetTheRequests();
            Publisher.Outcome again = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            List<StubConfluence.Request> bodies = site.sent().stream()
                    .filter(request -> request.method().equals("PUT") && !request.path().contains("/properties"))
                    .toList();

            claim("the publish updated one page and kept two",
                    () -> assertThat(again.closingLine()).contains("3 pages (0 created, 1 updated, 2 unchanged)"));
            claim("one body was sent, to the cluster's page, as the version after the one it stood at",
                    () -> assertThat(bodies).singleElement().satisfies(request -> {
                        assertThat(request.path()).endsWith("/pages/" + cluster.id);
                        assertThat(new String(request.body(), StandardCharsets.UTF_8))
                                .contains("\"number\":" + (before + 1));
                    }));
            claim("the page holds the new body, and its property the new body's digest",
                    () -> assertThat(List.of(cluster.body, cluster.property.get("bodySha256"))).containsExactly(
                            "<p>What the group holds, written again.</p>",
                            Publisher.sha256(rendering.confluence().resolve("001/001/page.xml"))));
        }
    }

    @Test
    @Story("A publish updates the run's pages in place")
    @DisplayName("A copy drawn again is sent through its file's update endpoint, and the others are skipped")
    void aChangedFileIsReplacedThroughItsUpdateEndpoint() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            StubConfluence.File copy = page(site, Rendering.CLUSTER_TITLE).files.get(Rendering.COPY);
            rendering.withCopy("%PDF-1.4 synthetic, drawn again");
            site.forgetTheRequests();
            Publisher.Outcome again = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("one file was replaced and two kept",
                    () -> assertThat(again.closingLine()).contains("3 files (0 added, 1 replaced, 2 unchanged)"));
            claim("through the update endpoint of the file of that name",
                    () -> assertThat(site.sent()).filteredOn(request -> request.method().equals("POST"))
                            .singleElement().satisfies(request -> assertThat(request.path())
                                    .endsWith("/child/attachment/" + copy.id + "/data")));
            claim("which now holds the new bytes",
                    () -> assertThat(new String(copy.bytes, StandardCharsets.US_ASCII))
                            .isEqualTo("%PDF-1.4 synthetic, drawn again"));
        }
    }

    @Test
    @Story("A publish updates the run's pages in place")
    @DisplayName("A page of the run the page list no longer holds is left as it stands and counted, and nothing is deleted")
    void aPageTheListNoLongerHoldsIsLeftAndCounted() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            rendering.withoutTheCluster();
            Publisher.Outcome again = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end and counted the page it left",
                    () -> assertThat(again.closingLine())
                            .contains("1 page of this run the page list no longer holds was left as it stands"));
            claim("which still stands on the site, with its files",
                    () -> assertThat(page(site, Rendering.CLUSTER_TITLE).files).containsOnlyKeys(Rendering.COPY,
                            Rendering.PICTURE));
            claim("and no request of either publish was a DELETE",
                    () -> assertThat(site.sent()).noneMatch(request -> request.method().equals("DELETE")));
        }
    }

    @Test
    @Story("A publish updates the run's pages in place")
    @DisplayName("A page titled as a page of the rendering that no publish made stops the publish and is left as it stands")
    void aPageOfTheSameTitleNoPublishMadeIsLeft() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.aPageNoPublishMade(Rendering.PARTITION_TITLE);
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish stopped, naming the title",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains(
                            Rendering.PARTITION_TITLE))).containsExactly(false, true));
            claim("and the page no publish made was sent nothing",
                    () -> assertThat(page(site, Rendering.PARTITION_TITLE))
                            .satisfies(page -> assertThat(List.of(page.version, page.body, page.labels.size()))
                                    .containsExactly(1, "", 0)));
        }
    }

    private static StubConfluence.Page page(StubConfluence site, String title) {
        return site.pages().stream().filter(page -> page.title.equals(title)).findFirst().orElseThrow();
    }
}
