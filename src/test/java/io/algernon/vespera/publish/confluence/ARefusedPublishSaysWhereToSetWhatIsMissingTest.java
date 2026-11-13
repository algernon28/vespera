package io.algernon.vespera.publish.confluence;

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
 * control panel's Profile tab. Its two other refusals of what it read, a parent named twice and an address that is not
 * a Cloud site's, name the profile's keys and the Profile tab there too, and on the command line say what they said
 * before (ADR-372); those two are read through {@link ConfluenceSite#of}, the line it refuses with.
 *
 * <p>Each publish is refused before it reads the rendering or sends anything, so none is drawn, and the request
 * factory and the pause fail a test that reaches them. Untagged: nothing is sent (ADR-320 §6).
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-318", url = Adr.THE_PUBLISH_READS_THE_EMAIL_AND_THE_FOLDER_BY_THEIR_NEW_NAMES, type = "adr")
@Link(name = "ADR-372", url = Adr.THE_CONTROL_PANELS_PUBLISH_NAMES_THE_PROFILE_KEYS_FOR_A_PARENT_NAMED_TWICE_OR_A_WRONG_ADDRESS, type = "adr")
class ARefusedPublishSaysWhereToSetWhatIsMissingTest {

    /** The variable by which the control panel tells the child it started it, and its one value (ADR-359 §3). */
    private static final String STARTED_BY_VARIABLE = "VESPERA_STARTED_BY";

    private static final String THE_CONTROL_PANEL = "control-panel";

    private static final String SITE = "https://site.example";

    private static final String EMAIL = "operator@example.org";

    private static final String TOKEN = "TOKEN-0a9b8c7d-not-a-real-one";

    /** The generation run the rendering would be drawn for; no rendering is drawn, the publish refusing first. */
    private static final String RUN = "abcdef0123456789";

    /** An address that is not {@code https}, and so not a Cloud site's. */
    private static final String NOT_A_SITE = "http://site.example";

    private static final String SPACE = "DOCS";

    private static final String FOLDER_ID = "4242";

    private static final String FOLDER_TITLE = "Hand-overs";

    /** The refusal of a parent named twice, started by the control panel (ADR-372 §1). */
    private static final String PANEL_PARENT_NAMED_TWICE = "vespera publish sent nothing: the parent is named both by"
            + " confluenceFolderId and by confluenceFolderTitle. Clear one in the control panel's Profile tab, and"
            + " publish again.";

    /** The refusal of an address that is not a Cloud site's, started by the control panel (ADR-372 §1). */
    private static final String PANEL_NOT_A_SITE = "vespera publish sent nothing: confluenceBaseUrl is not the https"
            + " address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user, query or other"
            + " path. Correct it in the control panel's Profile tab, and publish again.";

    /** The refusal of a parent named twice, typed on the command line, as ADR-318 §3 has it (ADR-372 §2). */
    private static final String SHELL_PARENT_NAMED_TWICE = "vespera publish sent nothing: the parent is named both by"
            + " VESPERA_CONFLUENCE_FOLDER_ID and by VESPERA_CONFLUENCE_FOLDER_TITLE. Name it by one.";

    /** The refusal of an address that is not a Cloud site's, typed on the command line (ADR-372 §2). */
    private static final String SHELL_NOT_A_SITE = "vespera publish sent nothing: VESPERA_CONFLUENCE_BASE_URL is not the"
            + " https address of a Confluence Cloud site, https://<site> or https://<site>/wiki, with no user, query or"
            + " other path.";

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

    @Test
    @Story("A publish missing a value says where to set it")
    @DisplayName("Started by the control panel, a parent named by both its id and its title, and an address that is not https, are each refused naming the profile's keys and the Profile tab, and no variable of the shell")
    void fromTheControlPanelAParentNamedTwiceAndAnAddressNotASitesNameTheProfilesKeys() {
        Map<String, String> fromThePanel = Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN, STARTED_BY_VARIABLE,
                THE_CONTROL_PANEL);
        ConfluenceValues bothFolders = new ConfluenceValues(SITE, SPACE, FOLDER_ID, FOLDER_TITLE, EMAIL);
        ConfluenceValues notASite = new ConfluenceValues(NOT_A_SITE, SPACE, FOLDER_ID, null, EMAIL);

        ConfluenceSite.Read twice = ConfluenceSite.of(fromThePanel::get, "", "", "", "", bothFolders);
        ConfluenceSite.Read wrongAddress = ConfluenceSite.of(fromThePanel::get, "", "", "", "", notASite);

        claim("a profile naming the parent by both its folder id and its folder title is refused naming both keys and"
                        + " the Profile tab, and no variable of the shell",
                () -> assertThat(twice).isEqualTo(new ConfluenceSite.Read.Refused(PANEL_PARENT_NAMED_TWICE)));
        claim("a profile naming an http address is refused naming the address's key and the Profile tab, and no"
                        + " variable of the shell",
                () -> assertThat(wrongAddress).isEqualTo(new ConfluenceSite.Read.Refused(PANEL_NOT_A_SITE)));
    }

    @Test
    @Story("A publish missing a value says where to set it")
    @DisplayName("Typed on the command line, a parent named by both its id and its title, and an address that is not https, are each refused with the line naming the shell's variables, as before")
    void fromTheCommandLineAParentNamedTwiceAndAnAddressNotASitesAreRefusedAsBefore() {
        Map<String, String> fromTheShell = Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN, ConfluenceSite.EMAIL_VARIABLE,
                EMAIL);

        ConfluenceSite.Read twice = ConfluenceSite.of(fromTheShell::get, SITE, SPACE, FOLDER_ID, FOLDER_TITLE,
                ConfluenceValues.NONE);
        ConfluenceSite.Read wrongAddress = ConfluenceSite.of(fromTheShell::get, NOT_A_SITE, SPACE, FOLDER_ID, "",
                ConfluenceValues.NONE);

        claim("a shell naming the parent by both its folder id and its folder title is refused with the line naming"
                        + " both variables",
                () -> assertThat(twice).isEqualTo(new ConfluenceSite.Read.Refused(SHELL_PARENT_NAMED_TWICE)));
        claim("a shell naming an http address is refused with the line naming the address's variable",
                () -> assertThat(wrongAddress).isEqualTo(new ConfluenceSite.Read.Refused(SHELL_NOT_A_SITE)));
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
