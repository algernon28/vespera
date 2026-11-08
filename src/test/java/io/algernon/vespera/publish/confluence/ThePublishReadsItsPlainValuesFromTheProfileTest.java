package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a publish reads its five values from (ADR-359 §2): the site's address, the space's key and the parent from the
 * properties the shell sets, the email from {@code VESPERA_CONFLUENCE_EMAIL}, each, where the shell sets none, from the
 * profile; the parent taken whole from one side; and the token from {@code VESPERA_CONFLUENCE_TOKEN} alone. Read
 * through {@link ConfluenceSite#of}, which sends nothing.
 *
 * <p>Untagged: it sends nothing, as {@code ConfluencePluginTest} sends nothing (ADR-320 §6).
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-318", url = Adr.THE_PUBLISH_READS_THE_EMAIL_AND_THE_FOLDER_BY_THEIR_NEW_NAMES, type = "adr")
class ThePublishReadsItsPlainValuesFromTheProfileTest {

    /** A token, synthetic, which the environment alone holds. */
    private static final String TOKEN = "TOKEN-f9e8d7c6-not-a-real-one";

    /** What the profile names: a site, a space, the folder by its id and an email, none of them the shell's. */
    private static final String PROFILES_HOST = "profile-site.example";

    private static final String PROFILES_SPACE = "PROF";

    private static final String PROFILES_FOLDER_ID = "8001";

    private static final String PROFILES_EMAIL = "profile@example.org";

    private static final ConfluenceValues THE_PROFILES = new ConfluenceValues("https://" + PROFILES_HOST, PROFILES_SPACE,
            PROFILES_FOLDER_ID, null, PROFILES_EMAIL);

    /** What the shell sets: another site, space and email, and the folder by its title. */
    private static final String SHELLS_HOST = "shell-site.example";

    private static final String SHELLS_SPACE = "SHELL";

    private static final String SHELLS_FOLDER_TITLE = "Shell folder";

    private static final String SHELLS_EMAIL = "shell@example.org";

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("With only the token set in the environment, the site, the space, the folder and the email are the profile's")
    void theProfileAloneNamesTheSiteTheSpaceTheParentAndTheEmail() {
        ConfluenceSite.Read read = ConfluenceSite.of(Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN)::get, "", "", "", "",
                THE_PROFILES);

        claim("the publish is given a site, the profile naming every value the shell does not",
                () -> assertThat(read).isInstanceOf(ConfluenceSite.Read.Given.class));
        ConfluenceSite site = ((ConfluenceSite.Read.Given) read).site();
        claim("its site, space and folder are the profile's, the folder by its id",
                () -> assertThat(List.of(site.site().getHost(), site.spaceKey(), String.valueOf(site.parentId()),
                        String.valueOf(site.parentTitle())))
                        .containsExactly(PROFILES_HOST, PROFILES_SPACE, PROFILES_FOLDER_ID, "null"));
        claim("and it sends as the profile's email, with the environment's token",
                () -> assertThat(site.authorization()).isEqualTo(basic(PROFILES_EMAIL, TOKEN)));
    }

    @Test
    @Story("A value the shell sets comes before the profile's")
    @DisplayName("A site, a space, a folder title and an email the shell sets come before the profile's, and the profile's folder id is not read beside the shell's title")
    void aValueTheShellSetsComesBeforeTheProfiles() {
        Map<String, String> environment = Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN, ConfluenceSite.EMAIL_VARIABLE,
                SHELLS_EMAIL);

        ConfluenceSite.Read read = ConfluenceSite.of(environment::get, "https://" + SHELLS_HOST, SHELLS_SPACE, "",
                SHELLS_FOLDER_TITLE, THE_PROFILES);

        claim("the publish is given a site, the folder not taken for one named twice",
                () -> assertThat(read).as(read.toString()).isInstanceOf(ConfluenceSite.Read.Given.class));
        ConfluenceSite site = ((ConfluenceSite.Read.Given) read).site();
        claim("its site and space are the shell's",
                () -> assertThat(List.of(site.site().getHost(), site.spaceKey()))
                        .containsExactly(SHELLS_HOST, SHELLS_SPACE));
        claim("its folder is the shell's title, the profile's id not read beside it",
                () -> assertThat(List.of(String.valueOf(site.parentTitle()), String.valueOf(site.parentId())))
                        .containsExactly(SHELLS_FOLDER_TITLE, "null"));
        claim("and it sends as the shell's email",
                () -> assertThat(site.authorization()).isEqualTo(basic(SHELLS_EMAIL, TOKEN)));
    }

    @Test
    @Story("A value the shell sets comes before the profile's")
    @DisplayName("A value the shell sets blank is none, and the profile's is read in its place")
    void aBlankValueOfTheShellIsNoneAndTheProfilesIsRead() {
        Map<String, String> environment = Map.of(ConfluenceSite.TOKEN_VARIABLE, TOKEN, ConfluenceSite.EMAIL_VARIABLE,
                "  ");

        ConfluenceSite.Read read = ConfluenceSite.of(environment::get, "  ", " ", "", "\t", THE_PROFILES);

        claim("the publish is given a site", () -> assertThat(read).isInstanceOf(ConfluenceSite.Read.Given.class));
        ConfluenceSite site = ((ConfluenceSite.Read.Given) read).site();
        claim("whose site, space, folder and email are the profile's, each blank of the shell's read as none",
                () -> {
                    assertThat(List.of(site.site().getHost(), site.spaceKey(), String.valueOf(site.parentId())))
                            .containsExactly(PROFILES_HOST, PROFILES_SPACE, PROFILES_FOLDER_ID);
                    assertThat(site.authorization()).isEqualTo(basic(PROFILES_EMAIL, TOKEN));
                });
    }

    @Test
    @Story("The token is the environment's alone")
    @DisplayName("The profile's values hold no token, and a profile naming every other value with no token in the environment is refused naming the token's variable alone")
    void theTokenComesFromTheEnvironmentAlone() {
        ConfluenceSite.Read read = ConfluenceSite.of(Map.<String, String>of()::get, "", "", "", "", THE_PROFILES);

        claim("what a publish reads of the profile is the site, the space, the folder by its id and by its title, and"
                        + " the email, and no token",
                () -> assertThat(Arrays.stream(ConfluenceValues.class.getRecordComponents())
                                .map(RecordComponent::getName).toList())
                        .containsExactly("baseUrl", "spaceKey", "folderId", "folderTitle", "email"));
        claim("with no token in the environment the publish is refused, however much the profile names",
                () -> assertThat(read).isInstanceOf(ConfluenceSite.Read.Refused.class));
        String line = ((ConfluenceSite.Read.Refused) read).line();
        claim("and the refusal names the token's variable, and no variable of a value the profile named",
                () -> {
                    assertThat(line).contains(ConfluenceSite.TOKEN_VARIABLE);
                    assertThat(line).doesNotContain("VESPERA_CONFLUENCE_BASE_URL", "VESPERA_CONFLUENCE_SPACE_KEY",
                            "VESPERA_CONFLUENCE_FOLDER_ID", ConfluenceSite.EMAIL_VARIABLE);
                });
    }

    private static String basic(String email, String token) {
        return "Basic " + Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
    }
}
