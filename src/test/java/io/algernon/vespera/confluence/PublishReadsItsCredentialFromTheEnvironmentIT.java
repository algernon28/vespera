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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The account's email and token are read from the environment alone, go as HTTP Basic authentication, and appear in no
 * line the publish prints and no file it writes; a value missing is named, never its value, and refused before
 * anything is sent (ADR-308 §3), the email and the parent by the names ADR-318 gives them. Against a stub; no test
 * reaches a real site.
 */
@Epic("Operation")
@Feature("The publish")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
@Link(name = "ADR-318", url = Adr.THE_PUBLISH_READS_THE_EMAIL_AND_THE_FOLDER_BY_THEIR_NEW_NAMES, type = "adr")
@Tag("confluence")
class PublishReadsItsCredentialFromTheEnvironmentIT {

    @TempDir
    Path folder;

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("Every request carries the email and token from the environment as Basic authentication")
    void everyRequestCarriesTheEnvironmentsEmailAndTokenAsBasic() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                (StubConfluence.USER + ":" + StubConfluence.TOKEN).getBytes(StandardCharsets.UTF_8));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome outcome = site.publisher(new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, folder, line -> {});

            claim("the publish went to its end", () -> assertThat(outcome.published()).as(outcome.closingLine()).isTrue());
            claim("and each request it sent carried the two as Basic authentication",
                    () -> assertThat(site.sent()).isNotEmpty()
                            .allSatisfy(request -> assertThat(request.header("Authorization")).isEqualTo(basic)));
        }
    }

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("No Spring property names the token or the email: the publisher's configured values are the site, the space and the parent")
    void noPropertyNamesTheTokenOrTheEmail() {
        List<String> properties = StubConfluence.thePublishersPlaceholders();

        claim("the application's publisher reads four properties, the site's address, the space's key and the parent"
                        + " by its folder id or its folder title, and no other",
                () -> assertThat(properties).containsExactly(
                        "${" + ConfluenceSite.BASE_URL_PROPERTY + ":}", "${" + ConfluenceSite.SPACE_KEY_PROPERTY + ":}",
                        "${" + StubConfluence.FOLDER_ID_PROPERTY + ":}",
                        "${" + StubConfluence.FOLDER_TITLE_PROPERTY + ":}"));
        claim("and none of them names a token, a user, an email or a parent",
                () -> assertThat(properties).noneMatch(value -> value.matches("(?i).*(token|user|email|parent).*")));
        claim("the constructor reading them is the one public constructor",
                () -> assertThat(Publisher.class.getConstructors()).hasSize(1)
                        .allSatisfy(constructor -> assertThat(Arrays.stream(constructor.getParameters())
                                .map(parameter -> parameter.getType().getName()).distinct().toList())
                                .containsExactly(String.class.getName())));
    }

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("A missing token or email is named by its variable and refused before anything is sent, and the templates are written in the working directory")
    void aMissingValueIsNamedAndRefusedBeforeSending() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (StubConfluence site = StubConfluence.start()) {
            Publisher.Outcome noToken = site.publisher(
                            Map.of(StubConfluence.EMAIL_VARIABLE, StubConfluence.USER)::get,
                            StubConfluence.BASE_URL, StubConfluence.PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, line -> {});
            Publisher.Outcome noEmail = site.publisher(
                            Map.of(ConfluenceSite.TOKEN_VARIABLE, StubConfluence.TOKEN)::get,
                            StubConfluence.BASE_URL, StubConfluence.PARENT_ID, "", new ArrayList<>())
                    .publish(rendering.confluence(), Rendering.RUN, workingDirectory, line -> {});

            claim("with no token the publish is refused, naming the token's variable",
                    () -> assertThat(List.of(noToken.published(),
                                    noToken.closingLine().contains(ConfluenceSite.TOKEN_VARIABLE)))
                            .containsExactly(false, true));
            claim("with no email it is refused, naming the email's variable and not the token's value",
                    () -> assertThat(List.of(noEmail.published(),
                                    noEmail.closingLine().contains(StubConfluence.EMAIL_VARIABLE),
                                    noEmail.closingLine().contains(StubConfluence.TOKEN)))
                            .containsExactly(false, true, false));
            claim("nothing was sent either time", () -> assertThat(site.sent()).isEmpty());
            claim("and the two templates were written in the working directory, naming each of the five variables by"
                            + " its current name, no name the email or the parent was read from before, and no value",
                    () -> assertThat(Publisher.TEMPLATES).allSatisfy(template -> {
                        String text = Files.readString(workingDirectory.resolve(template), StandardCharsets.UTF_8);
                        assertThat(text).contains("VESPERA_CONFLUENCE_BASE_URL", "VESPERA_CONFLUENCE_SPACE_KEY",
                                StubConfluence.FOLDER_ID_VARIABLE, StubConfluence.FOLDER_TITLE_VARIABLE,
                                StubConfluence.EMAIL_VARIABLE, ConfluenceSite.TOKEN_VARIABLE);
                        assertThat(text).doesNotContain(StubConfluence.EARLIER_VARIABLES.toArray(String[]::new));
                        assertThat(text).doesNotContain(StubConfluence.USER, StubConfluence.TOKEN,
                                StubConfluence.SITE_HOST);
                    }));
        }
    }

    @Test
    @Story("The token is the operator's environment's alone")
    @DisplayName("No line a publish prints and no file it writes holds the token, as it goes to its end or stops")
    void noLineOrFileThePublishWritesHoldsTheToken() throws IOException {
        Rendering rendering = Rendering.drawnIn(folder.resolve("tree"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        List<String> lines = new ArrayList<>();
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
            claim("no line holds the token or the email",
                    () -> assertThat(lines).isNotEmpty().noneMatch(line -> line.contains(StubConfluence.TOKEN)
                            || line.contains(StubConfluence.USER)));
            claim("and no file in the working directory holds either, the list of what was not carried among them",
                    () -> {
                        assertThat(workingDirectory.resolve(Publisher.NOT_CARRIED_FILE_NAME)).exists();
                        try (Stream<Path> files = Files.walk(folder)) {
                            assertThat(files.filter(Files::isRegularFile).toList()).allSatisfy(file -> assertThat(
                                    new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                                    .doesNotContain(StubConfluence.TOKEN, StubConfluence.USER));
                        }
                    });
        }
    }
}
