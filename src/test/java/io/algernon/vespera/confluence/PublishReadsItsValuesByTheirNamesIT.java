package io.algernon.vespera.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The publish reads the account's email from {@code VESPERA_CONFLUENCE_EMAIL} and the parent from {@code
 * VESPERA_CONFLUENCE_FOLDER_ID} or {@code VESPERA_CONFLUENCE_FOLDER_TITLE}; the names ADR-308 gave the three are read
 * nowhere, and every refusal names the new ones (ADR-318). Against a stub; no test reaches a real site.
 */
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-318", url = Adr.THE_PUBLISH_READS_THE_EMAIL_AND_THE_FOLDER_BY_THEIR_NEW_NAMES, type = "adr")
@Tag("confluence")
class PublishReadsItsValuesByTheirNamesIT {

    /** A title no page or folder of the stub's space carries. */
    private static final String NO_SUCH_TITLE = "Nothing is titled so";

    /** What the earlier variables are set to, so that a value read from one would show. */
    private static final String EARLIER_ID = "9009";

    private static final String EARLIER_TITLE = "Set by an earlier name";

    @TempDir
    Path folder;

    @Test
    @Story("The publish reads its values by their names")
    @DisplayName("An environment holding the email under the variable's earlier name alone is refused, naming the email's current variable, and nothing is sent")
    void anEnvironmentHoldingOnlyTheEarlierEmailVariableIsRefusedNamingTheNewOne() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(
                            Map.of(StubConfluence.EARLIER_VARIABLES.getFirst(), StubConfluence.USER,
                                    ConfluenceSite.TOKEN_VARIABLE, StubConfluence.TOKEN)::get,
                            StubConfluence.BASE_URL, StubConfluence.PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish is refused, naming the email's current variable",
                    () -> assertThat(List.of(outcome.published(),
                                    outcome.closingLine().contains(StubConfluence.EMAIL_VARIABLE)))
                            .as(outcome.closingLine()).containsExactly(false, true));
            claim("its line names no earlier variable", () -> assertThat(outcome.closingLine())
                    .doesNotContain(StubConfluence.EARLIER_VARIABLES.toArray(String[]::new)));
            claim("and nothing was sent", () -> assertThat(site.sent()).isEmpty());
        }
    }

    @Test
    @Story("The publish reads its values by their names")
    @DisplayName("The application's publisher takes the parent from the folder id and folder title variables, and nothing from their earlier names")
    void theParentIsReadFromTheFolderVariablesAndNotFromTheEarlierOnes() {
        Map<String, Object> site = Map.of(
                "VESPERA_CONFLUENCE_BASE_URL", StubConfluence.BASE_URL,
                "VESPERA_CONFLUENCE_SPACE_KEY", StubConfluence.SPACE_KEY);
        Map<String, Object> current = new HashMap<>(site);
        current.put(StubConfluence.FOLDER_ID_VARIABLE, StubConfluence.PARENT_ID);
        current.put(StubConfluence.FOLDER_TITLE_VARIABLE, StubConfluence.PARENT_TITLE);
        Map<String, Object> earlier = new HashMap<>(site);
        earlier.put(StubConfluence.EARLIER_VARIABLES.get(1), EARLIER_ID);
        earlier.put(StubConfluence.EARLIER_VARIABLES.get(2), EARLIER_TITLE);
        Map<String, Object> both = new HashMap<>(current);
        both.putAll(earlier);

        claim("from the current variables the publisher's four properties are the site, the space, the folder's id"
                        + " and its title",
                () -> assertThat(resolvedFrom(current)).containsExactly(StubConfluence.BASE_URL,
                        StubConfluence.SPACE_KEY, StubConfluence.PARENT_ID, StubConfluence.PARENT_TITLE));
        claim("from the earlier variables alone the parent's two properties are empty",
                () -> assertThat(resolvedFrom(earlier)).containsExactly(StubConfluence.BASE_URL,
                        StubConfluence.SPACE_KEY, "", ""));
        claim("and with both set, the current variables' values are read and the earlier ones' are not",
                () -> assertThat(resolvedFrom(both)).containsExactly(StubConfluence.BASE_URL,
                        StubConfluence.SPACE_KEY, StubConfluence.PARENT_ID, StubConfluence.PARENT_TITLE));
    }

    @Test
    @Story("The publish names what is missing by its current name")
    @DisplayName("A parent named neither by its id nor by its title is refused, naming the folder id and folder title variables")
    void aParentNamedByNeitherIsRefusedNamingTheFolderVariables() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL, "", "",
                            new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claimRefusedNamingTheFolderVariables(outcome);
            claim("and nothing was sent", () -> assertThat(site.sent()).isEmpty());
        }
    }

    @Test
    @Story("The publish names what is missing by its current name")
    @DisplayName("A parent named both by its id and by its title is refused, naming the folder id and folder title variables")
    void aParentNamedByBothIsRefusedNamingTheFolderVariables() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL,
                            StubConfluence.PARENT_ID, StubConfluence.PARENT_TITLE, new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claimRefusedNamingTheFolderVariables(outcome);
            claim("and nothing was sent", () -> assertThat(site.sent()).isEmpty());
        }
    }

    @Test
    @Story("The publish names what is missing by its current name")
    @DisplayName("A title naming no page or folder of the space stops the publish, which says to name the parent by the folder id variable")
    void aTitleNamingNoPageOrFolderIsRefusedPointingAtTheFolderId() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(StubConfluence.theAccount(), StubConfluence.BASE_URL, "",
                            NO_SUCH_TITLE, new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish stopped, its line naming the folder id variable",
                    () -> assertThat(List.of(outcome.published(),
                                    outcome.closingLine().contains(StubConfluence.FOLDER_ID_VARIABLE)))
                            .as(outcome.closingLine()).containsExactly(false, true));
            claim("and its line names no earlier variable", () -> assertThat(outcome.closingLine())
                    .doesNotContain(StubConfluence.EARLIER_VARIABLES.toArray(String[]::new)));
        }
    }

    /** The refusal of a parent named by neither or by both: both folder variables named, no earlier one. */
    private static void claimRefusedNamingTheFolderVariables(Publisher.Outcome outcome) {
        claim("the publish is refused, naming the folder id and the folder title variables",
                () -> assertThat(List.of(outcome.published(),
                                outcome.closingLine().contains(StubConfluence.FOLDER_ID_VARIABLE),
                                outcome.closingLine().contains(StubConfluence.FOLDER_TITLE_VARIABLE)))
                        .as(outcome.closingLine()).containsExactly(false, true, true));
        claim("its line names no earlier variable", () -> assertThat(outcome.closingLine())
                .doesNotContain(StubConfluence.EARLIER_VARIABLES.toArray(String[]::new)));
    }

    /**
     * The application's publisher's placeholders resolved as Spring Boot resolves them, over an environment holding
     * {@code variables} and nothing else, in the constructor's order.
     */
    private static List<String> resolvedFrom(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        ConfigurationPropertySources.attach(environment);
        return StubConfluence.thePublishersPlaceholders().stream()
                .map(environment::resolveRequiredPlaceholders)
                .toList();
    }
}
