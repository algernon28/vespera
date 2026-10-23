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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A publish sends to the configured site's {@code https} host alone, and refuses any other address before it sends
 * anything (ADR-308 §2). Against a stub; no test reaches a real site.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Tag("confluence")
class PublishSendsToOneHostIT {

    @TempDir
    Path folder;

    @Test
    @Story("A publish sends to the operator's site alone")
    @DisplayName("Every request a publish sends is addressed to the configured https host")
    void everyRequestIsAddressedToTheConfiguredHttpsHost() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end, so the requests below are the whole of what it sent",
                    () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("each of them was addressed to https and the configured host, and to no other",
                    () -> assertThat(site.sent()).isNotEmpty().allSatisfy(request -> {
                        assertThat(request.addressed().getScheme()).isEqualTo("https");
                        assertThat(request.addressed().getHost()).isEqualTo(StubConfluence.SITE_HOST);
                    }));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "http://site.example",
        "https://operator@site.example",
        "https://site.example/wiki/spaces/KT",
        "https://site.example?next=elsewhere.example",
        "site.example"
    })
    @Story("A publish sends to the operator's site alone")
    @DisplayName("An address that is not https, or names a user, a query or another path, is refused before anything is sent")
    void anAddressThatIsNotTheSitesHttpsOriginIsRefusedBeforeSending(String address) throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site
                    .publisher(StubConfluence.theAccount(), address, StubConfluence.PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish is refused with the line naming the variable",
                    () -> assertThat(List.of(outcome.published(),
                                    outcome.closingLine().contains("VESPERA_CONFLUENCE_BASE_URL")))
                            .containsExactly(false, true));
            claim("and nothing was sent",
                    () -> assertThat(site.sent()).isEmpty());
        }
    }

    @Test
    @Story("A publish sends to the operator's site alone")
    @DisplayName("A link the site answers with to another host is not followed, and the publish stops")
    void aLinkToAnotherHostIsNotFollowed() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            site.nextLinkOfTheLabelSearch("https://elsewhere.example/wiki/rest/api/content/search?cursor=2");
            List<Duration> waits = new ArrayList<>();
            Publisher.Outcome outcome =
                    site.publisher(waits).publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish stopped, saying the link names another host",
                    () -> assertThat(List.of(outcome.published(), outcome.closingLine().contains("another host")))
                            .containsExactly(false, true));
            claim("and no request was addressed to it",
                    () -> assertThat(site.sent()).noneMatch(request ->
                            "elsewhere.example".equals(request.addressed().getHost())));
        }
    }
}
