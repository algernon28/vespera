package io.algernon.vespera.engine.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The publish's four plain values, the site's address, the space's key, the parent by its id or its title, and the
 * account's email, are five text keys of the profile, the last of its record, and no key holds the API token
 * (ADR-359 §1).
 */
@Epic("Operation")
@Feature("Profile")
@Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class TheConfluenceValuesAreProfileKeysTest {

    /** The five keys, in the order the record declares them: the site, the space, the parent two ways, the email. */
    private static final List<String> THE_FIVE = ProfileKeys.CONFLUENCE_KEYS;

    /** The values {@link #THE_FIVE_ANSWERED} writes, each one no other key carries. */
    private static final List<String> THE_FIVE_VALUES = List.of("https://wiki.example.com", "DOCS", "123456789",
            "Hand-overs", "operator@example.org");

    /** A file answering the five, each with a value no other key carries. */
    private static final String THE_FIVE_ANSWERED = """
            confluenceBaseUrl:
              value: "https://wiki.example.com"
              provenance: "the team's site"
            confluenceSpaceKey:
              value: "DOCS"
              provenance: "the space the team writes in"
            confluenceFolderId:
              value: "123456789"
              provenance: "the folder the team keeps hand-overs in"
            confluenceFolderTitle:
              value: "Hand-overs"
              provenance: "the same folder, by its title"
            confluenceEmail:
              value: "operator@example.org"
              provenance: "the account the pages are published as"
            """;

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("The profile's last five values are the site's address, the space's key, the folder by its id and by its title, and the account's email, each a text")
    void theLastFiveKeysAreTheConfluenceValuesEachText() {
        List<RecordComponent> components = Arrays.asList(Profile.class.getRecordComponents());
        List<RecordComponent> last = components.subList(components.size() - THE_FIVE.size(), components.size());

        claim("the record's last five keys are the site's address, the space's key, the folder by its id, the folder by"
                        + " its title and the account's email, in that order",
                () -> assertThat(last.stream().map(RecordComponent::getName).toList())
                        .containsExactlyElementsOf(THE_FIVE));
        claim("and each holds a text, as a model's name or an approval does",
                () -> assertThat(last).allSatisfy(component -> assertThat(component.getType())
                        .as(component.getName()).isEqualTo(TextValue.class)));
    }

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("No value of the profile holds a token, a secret or a password")
    void noKeyHoldsAToken() {
        claim("no key of the profile is named for a token, a secret, a password or a credential: the API token is never"
                        + " written into profile.yaml",
                () -> assertThat(Arrays.stream(Profile.class.getRecordComponents()).map(RecordComponent::getName))
                        .noneMatch(name -> name.matches("(?i).*(token|secret|password|credential).*")));
    }

    @Test
    @Story("The operator names where a publish sends in the profile")
    @DisplayName("A profile answering the five loads each answer on the key that carried it")
    void aFileAnsweringThemLoadsEachOnItsKey(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), THE_FIVE_ANSWERED);

        Profile loaded = new ProfileStore(workingDirectory).load();

        claim("each of the five is read as the file wrote it, on its own key",
                () -> assertThat(loaded.keysAsWritten()).containsAllEntriesOf(Map.of(
                        "confluenceBaseUrl", "https://wiki.example.com",
                        "confluenceSpaceKey", "DOCS",
                        "confluenceFolderId", "123456789",
                        "confluenceFolderTitle", "Hand-overs",
                        "confluenceEmail", "operator@example.org")));
    }

    /**
     * The deliverable's index states every key but the five (ADR-359 §7, amending ADR-186 §1): they say where the tree
     * is sent, not how it was made. {@code keysAsWritten} keeps them for every other reader.
     */
    @Test
    @Story("The deliverable's index says how it was made, not where it was sent")
    @DisplayName("The keys the deliverable's index states are every key of the profile but the five values of a publish, none of whose values is among them")
    @Link(name = "ADR-186", url = Adr.THE_INDEX_STATES_EVERY_PROFILE_KEY_READ_OFF_THE_RECORD, type = "adr")
    void theIndexStatesEveryKeyButTheFive(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), THE_FIVE_ANSWERED);
        Profile loaded = new ProfileStore(workingDirectory).load();

        Map<String, String> stated = loaded.keysTheIndexStates();

        claim("the index states every key of the record but the five, in the record's order",
                () -> assertThat(List.copyOf(stated.keySet()))
                        .containsExactlyElementsOf(ProfileKeys.everyKeyTheIndexStates())
                        .doesNotContainAnyElementsOf(THE_FIVE));
        claim("each as written, as every key of the profile is written",
                () -> assertThat(loaded.keysAsWritten()).containsAllEntriesOf(stated));
        claim("and none of the values the five hold is among what it states",
                () -> assertThat(stated.values()).doesNotContainAnyElementsOf(THE_FIVE_VALUES));
        claim("while every reader of keysAsWritten still finds the five",
                () -> assertThat(loaded.keysAsWritten()).containsKeys(THE_FIVE.toArray(String[]::new)));
    }
}
