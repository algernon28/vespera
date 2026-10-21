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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a publish sends in what order, and how it answers the site's refusals (ADR-316 §4, ADR-313 §3; #633): every
 * page is created before any body, one {@code 409} is answered and a second stops it, a copy refused as too large is
 * skipped, counted and listed, a paced request is sent again after its wait, and a file is sent with {@code
 * X-Atlassian-Token: no-check} and its digest as its comment. Against a stub.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-316", url = Adr.THE_PAGE_LIST_IS_PAGES_JSON, type = "adr")
@Link(name = "ADR-313", url = Adr.A_CLUSTERS_CONFLUENCE_PAGE_CARRIES_ITS_COPIES_AS_FILES, type = "adr")
class PublishOrderAndRefusalsTest {

    @TempDir
    Path folder;

    @Test
    @Story("A publish sends the rendering whole")
    @DisplayName("A publish creates every page, parents first, before it sends any body, and labels and keys each")
    void aPublishCreatesEveryPageBeforeAnyBody() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            List<StubConfluence.Request> sent = site.sent();
            List<Integer> creates = IntStream.range(0, sent.size())
                    .filter(at -> sent.get(at).method().equals("POST") && sent.get(at).path().equals("/wiki/api/v2/pages"))
                    .boxed().toList();
            int firstBody = IntStream.range(0, sent.size())
                    .filter(at -> sent.get(at).method().equals("PUT") && !sent.get(at).path().contains("/properties"))
                    .findFirst().orElse(-1);
            List<StubConfluence.Page> pages = site.pages();

            claim("three pages were created, and the last create came before the first body was sent",
                    () -> assertThat(List.of(creates.size(), creates.getLast() < firstBody, firstBody > 0))
                            .containsExactly(3, true, true));
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
    @Story("A publish sends the rendering whole")
    @DisplayName("The parent named by its title is found as the one page or folder of the space so titled")
    void theParentNamedByItsTitleIsFound() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL, "",
                    StubConfluence.PARENT_TITLE, new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end, the root page under the folder so titled",
                    () -> assertThat(List.of(outcome.published(), site.pages().getFirst().parentId))
                            .containsExactly(true, StubConfluence.PARENT_ID));
        }
    }

    @Test
    @Story("A publish answers the site's refusals")
    @DisplayName("One 409 on a body is answered by reading the page's version again and sending once more; a second stops the publish")
    void aSecond409StopsThePublish() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.conflictOn(Rendering.CLUSTER_TITLE, 1);
            Publisher.Outcome once = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            rendering.withBody(Rendering.CLUSTER, "<p>Drawn again.</p>");
            site.conflictOn(Rendering.CLUSTER_TITLE, 2);
            List<String> lines = new ArrayList<>();
            Publisher.Outcome twice = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, lines::add);

            claim("after one 409 the body was sent again and the publish went to its end",
                    () -> assertThat(once.published()).as(once.closingLine()).isTrue());
            claim("after two it stopped, saying the page changed while it was sent, and sent no file after it",
                    () -> assertThat(List.of(twice.published(), twice.closingLine().contains(Rendering.CLUSTER),
                                    twice.closingLine().startsWith("vespera publish stopped:"), lines.isEmpty()))
                            .containsExactly(false, true, true, true));
        }
    }

    @Test
    @Story("A publish answers the site's refusals")
    @DisplayName("A copy the site refuses as too large is skipped, counted on the closing line and listed in confluence-not-carried.csv, and the rest are sent")
    void aCopyTheSiteRefusesAsTooLargeIsSkippedCountedAndListed() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (StubConfluence site = StubConfluence.start()) {
            site.refuseAsTooLarge(Rendering.COPY);
            List<String> lines = new ArrayList<>();
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, lines::add);
            long size = Files.size(folder.resolve("tree/001/001/" + Rendering.COPY));
            StubConfluence.Page cluster = site.pages().getLast();

            claim("the publish went to its end, the page published without the copy and with its picture",
                    () -> assertThat(List.of(outcome.published(), cluster.files.keySet()))
                            .containsExactly(true, java.util.Set.of(Rendering.PICTURE)));
            claim("its closing line counts the copy and its bytes, naming no copy",
                    () -> assertThat(outcome.closingLine())
                            .contains("; 1 copy was refused by the site as too large and is not on its page, " + size
                                    + " bytes, listed in " + Publisher.NOT_CARRIED_FILE_NAME)
                            .doesNotContain(Rendering.COPY));
            claim("the page's own line says one file was refused",
                    () -> assertThat(lines).contains(Rendering.CLUSTER_TITLE
                            + ": created; files: 1 sent, 0 unchanged, 1 refused by the site as too large"));
            claim("and confluence-not-carried.csv lists it by the page's key, its name and its size",
                    () -> assertThat(Files.readString(workingDirectory.resolve(Publisher.NOT_CARRIED_FILE_NAME),
                                    StandardCharsets.UTF_8))
                            .isEqualTo("key,name,size\r\n\"" + Rendering.CLUSTER + "\",\"" + Rendering.COPY + "\","
                                    + size + "\r\n"));
        }
    }

    @Test
    @Story("A publish answers the site's refusals")
    @DisplayName("A publish that skips no file writes no confluence-not-carried.csv and removes none an earlier one wrote")
    void aPublishSkippingNoneLeavesTheListAsItStands() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path earlier = Files.writeString(workingDirectory.resolve(Publisher.NOT_CARRIED_FILE_NAME), "earlier\r\n");
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, line -> {});

            claim("the publish went to its end with no clause for a refused file",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains("too large")))
                            .containsExactly(true, false));
            claim("and the earlier list stands as it was",
                    () -> assertThat(Files.readString(earlier)).isEqualTo("earlier\r\n"));
        }
    }

    @Test
    @Story("A publish answers the site's refusals")
    @DisplayName("A request the site paces with 429 is sent again after the Retry-After it gives")
    void aPacedRequestIsSentAgainAfterItsWait() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.pace(2, "7");
            List<Duration> waits = new ArrayList<>();
            Publisher.Outcome outcome =
                    site.publisher(waits).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end", () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("after waiting the 7 seconds the site asked for, twice",
                    () -> assertThat(waits).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(7)));
            claim("and the request paced was the first, sent three times",
                    () -> assertThat(site.sent().subList(0, 3)).extracting(StubConfluence.Request::path)
                            .containsOnly("/wiki/api/v2/spaces"));
        }
    }

    @Test
    @Story("A publish sends the rendering whole")
    @DisplayName("Each file is sent with X-Atlassian-Token: no-check, under its name, with its digest as its comment")
    void eachFileIsSentWithNoCheckAndItsDigestAsItsComment() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.publisher(new ArrayList<>()).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});
            List<StubConfluence.Request> files = site.sent().stream()
                    .filter(request -> request.path().endsWith("/child/attachment")).toList();

            claim("three files were sent, each with X-Atlassian-Token: no-check",
                    () -> assertThat(files).hasSize(3).allSatisfy(request ->
                            assertThat(request.header("X-Atlassian-Token")).isEqualTo("no-check")));
            claim("each under its name, its comment the digest of its bytes",
                    () -> assertThat(files).allSatisfy(request -> {
                        StubConfluence.Part file = StubConfluence.parts(request).get("file");
                        String comment = new String(StubConfluence.parts(request).get("comment").bytes(),
                                StandardCharsets.UTF_8);
                        assertThat(comment).isEqualTo(java.util.HexFormat.of().formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(file.bytes())));
                        assertThat(file.filename()).isIn(Rendering.LISTING, Rendering.COPY, Rendering.PICTURE);
                    }));
        }
    }
}
