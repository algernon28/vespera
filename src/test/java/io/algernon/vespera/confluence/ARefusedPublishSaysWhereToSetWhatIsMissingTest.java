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
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * What a publish missing a value says, and what it writes (ADR-359 §4). Typed on the command line it says what it said
 * before, naming each variable missing and pointing at the templates it writes into the working directory, a value the
 * profile names not being missing (ADR-308 §3, ADR-318 §3). Started by the control panel, which tells its child so by
 * {@code VESPERA_STARTED_BY=control-panel}, it writes no template, and names each value missing by its key on the
 * control panel's Profile tab.
 *
 * <p>Each publish is refused before it reads the rendering or sends anything, so none is drawn, and the request
 * factory and the pause fail a test that reaches them. Untagged: nothing is sent (ADR-320 §6).
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-318", url = Adr.THE_PUBLISH_READS_THE_EMAIL_AND_THE_FOLDER_BY_THEIR_NEW_NAMES, type = "adr")
class ARefusedPublishSaysWhereToSetWhatIsMissingTest {

    /** The variable by which the control panel tells the child it started it, and its one value (ADR-359 §3). */
    private static final String STARTED_BY_VARIABLE = "VESPERA_STARTED_BY";

    private static final String THE_CONTROL_PANEL = "control-panel";

    private static final String SITE = "https://site.example";

    private static final String EMAIL = "operator@example.org";

    private static final String TOKEN = "TOKEN-0a9b8c7d-not-a-real-one";

    /** The generation run the rendering would be drawn for; no rendering is drawn, the publish refusing first. */
    private static final String RUN = "abcdef0123456789";

    @Test
    @Story("A publish missing a value says where to set it")
    @DisplayName("Started by the control panel, a publish missing values names their keys on the Profile tab, names no variable and no template, and writes nothing in the working directory")
    void fromTheControlPanelTheRefusalNamesTheProfileTabsKeysAndWritesNoTemplate(@TempDir Path folder)
            throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ConfluenceValues siteAndFolder = new ConfluenceValues(SITE, null, null, "Hand-overs", null);
        Map<String, String> withTheToken = Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN, STARTED_BY_VARIABLE,
                THE_CONTROL_PANEL);
        Map<String, String> withNoToken = new HashMap<>(Map.of(STARTED_BY_VARIABLE, THE_CONTROL_PANEL,
                ConfluenceSite.EMAIL_VARIABLE, EMAIL));

        Publisher.Outcome noSpaceNoEmail = publisher(withTheToken, "", "", "", "", siteAndFolder)
                .publish(folder.resolve("no-rendering"), RUN, workingDirectory, line -> {});
        Publisher.Outcome noToken = publisher(withNoToken, "", "DOCS", "", "", siteAndFolder)
                .publish(folder.resolve("no-rendering"), RUN, workingDirectory, line -> {});

        claim("both publishes are refused",
                () -> assertThat(List.of(noSpaceNoEmail.published(), noToken.published())).containsExactly(false, false));
        claim("the first names the space key and the email by their keys on the Profile tab, and no value the profile"
                        + " named",
                () -> {
                    assertThat(noSpaceNoEmail.closingLine()).contains("confluenceSpaceKey", "confluenceEmail",
                            "Profile tab");
                    assertThat(noSpaceNoEmail.closingLine()).doesNotContain("confluenceBaseUrl", "confluenceFolder");
                });
        claim("the second says the API token Publish asks for is missing",
                () -> assertThat(noToken.closingLine()).contains("API token"));
        claim("neither names a variable of the shell or a template",
                () -> assertThat(List.of(noSpaceNoEmail.closingLine(), noToken.closingLine())).allSatisfy(line ->
                        assertThat(line).doesNotContain("VESPERA_CONFLUENCE_", "confluence-env")));
        claim("and the working directory holds no file: no template was written",
                () -> {
                    try (Stream<Path> files = Files.list(workingDirectory)) {
                        assertThat(files.toList()).isEmpty();
                    }
                });
    }

    /**
     * A publisher reading {@code environment}, the four properties as given and {@code profile} as the working
     * directory's profile, whose requests and waits fail the test: a refused publish sends nothing and waits for nothing.
     */
    private static Publisher publisher(Map<String, String> environment, String baseUrl, String spaceKey, String parentId,
            String parentTitle, ConfluenceValues profile) {
        ClientHttpRequestFactory sendsNothing = (uri, method) -> {
            throw new AssertionError("a refused publish sends nothing, and this one built a request to " + uri);
        };
        return new Publisher(environment::get, sendsNothing, duration -> {
            throw new AssertionError("a refused publish waits for nothing");
        }, Clock.systemUTC(), baseUrl, spaceKey, parentId, parentTitle, workingDirectory -> profile);
    }
}
