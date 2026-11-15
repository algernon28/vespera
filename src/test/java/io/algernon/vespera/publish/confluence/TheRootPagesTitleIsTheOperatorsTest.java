package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * The title a publish sends the root page under (ADR-398 §2 to §4): the title the shell's {@code
 * VESPERA_CONFLUENCE_ROOT_TITLE} states, not blank, or else the profile's, folded as a title's text is; blank or stated
 * nowhere, {@code Vespera_{language}}; {@code {language}} replaced by the language's code wherever it stands, and, where
 * the title holds none, an underscore and the code appended; and a title past Confluence's 255 characters refused before
 * any request, never cut. And the two templates a publish writes set the variable to nothing (ADR-398 §8).
 *
 * <p>Each publish reads a rendering drawn by hand ({@link Rendering}) and is pointed at a request factory that refuses to
 * build any request, so no connection is opened: the publish's first line, which names the tree once the values and
 * the page list are checked and before any request (ADR-378 §7), is read, and the publish then stops at its first
 * request. That the title reaches the site, and retitles a tree in place, is {@code
 * PublishUpdatesOneTreeInPlaceIT}'s. Untagged: nothing is sent (ADR-320 §6).
 */
@Epic("Operation")
@Feature("The publish")
@Issue("837")
@Link(name = "ADR-398", url = Adr.THE_CONFLUENCE_ROOT_PAGES_TITLE_IS_A_PROFILE_VALUE, type = "adr")
@Link(name = "ADR-378", url = Adr.EACH_DELIVERABLE_LANGUAGE_IS_PUBLISHED_AS_A_TREE_OF_ITS_OWN, type = "adr")
class TheRootPagesTitleIsTheOperatorsTest {

    /** The variable the shell states the root page's title in (ADR-398 §2). */
    private static final String ROOT_TITLE_VARIABLE = "VESPERA_CONFLUENCE_ROOT_TITLE";

    /** The name of the component of what a publish reads of the profile that holds the root page's title (ADR-398 §1). */
    private static final String ROOT_TITLE_COMPONENT = "rootTitle";

    /** The variable by which the control panel tells the child it started it, and its one value (ADR-359 §3). */
    private static final String STARTED_BY_VARIABLE = "VESPERA_STARTED_BY";

    private static final String THE_CONTROL_PANEL = "control-panel";

    /** The most characters a Confluence title has, Confluence's own limit (ADR-310 §4). */
    private static final int LONGEST_TITLE = 255;

    /** What a title with no placeholder is given at its end in an English rendering: an underscore and the code. */
    private static final String ENGLISH_SUFFIX = "_" + Rendering.ENGLISH;

    /** The characters of a title with no placeholder that, its English suffix appended, come to 255 exactly. */
    private static final String AT_THE_LIMIT = "x".repeat(LONGEST_TITLE - ENGLISH_SUFFIX.length());

    /** One character more, which comes to 256 with the suffix. */
    private static final String PAST_THE_LIMIT = AT_THE_LIMIT + "x";

    /** The refusal of a title past the limit that the shell stated, typed on the command line (ADR-398 §4). */
    private static final String SHELLS_TITLE_TOO_LONG = "vespera publish sent nothing: VESPERA_CONFLUENCE_ROOT_TITLE"
            + " gives the root page a title of 256 characters, and Confluence takes at most 255. Shorten it, and publish"
            + " again.";

    /** The refusal of a title past the limit that the profile stated, typed on the command line (ADR-398 §4). */
    private static final String PROFILES_TITLE_TOO_LONG = "vespera publish sent nothing: confluenceRootTitle gives the"
            + " root page a title of 256 characters, and Confluence takes at most 255. Shorten it, and publish again.";

    /** The same, the control panel having started the publish (ADR-398 §4). */
    private static final String PROFILES_TITLE_TOO_LONG_FROM_THE_PANEL = "vespera publish sent nothing:"
            + " confluenceRootTitle gives the root page a title of 256 characters, and Confluence takes at most 255."
            + " Shorten it in the control panel's Profile window, and publish again.";

    /** What each template must hold: a line setting the variable to nothing, with or without a comment after it. */
    private static final Map<String, Pattern> EACH_TEMPLATES_LINE = Map.of(
            "confluence-env.sh", Pattern.compile("(?m)^export VESPERA_CONFLUENCE_ROOT_TITLE=''(\\s+#.*)?$"),
            "confluence-env.ps1", Pattern.compile("(?m)^\\$env:VESPERA_CONFLUENCE_ROOT_TITLE = ''(\\s+#.*)?$"));

    @TempDir
    Path folder;

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("Where nothing names a title, or the shell names a blank one, the root page is Vespera and the language's code, as before")
    void whereNothingNamesATitleTheRootPageIsVesperaAndTheCodeAsBefore() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        Rendering italian = Rendering.drawnIn(folder.resolve("it"), Rendering.ITALIAN);

        Published unnamed = published(english, Map.of(), ConfluenceValues.NONE);
        Published blank = published(english, Map.of(ROOT_TITLE_VARIABLE, " \t "), ConfluenceValues.NONE);
        Published unnamedInItalian = published(italian, Map.of(), ConfluenceValues.NONE);

        claim("with no title stated, the English tree is Vespera_en",
                () -> assertThat(unnamed.tree()).isEqualTo("Vespera_en"));
        claim("with a title of blanks and a tab stated, it is Vespera_en too: a blank title is none",
                () -> assertThat(blank.tree()).isEqualTo("Vespera_en"));
        claim("and with no title stated, the Italian tree is Vespera_it",
                () -> assertThat(unnamedInItalian.tree()).isEqualTo("Vespera_it"));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("The language placeholder in a title is replaced by the language's code, wherever it stands and however often")
    void theLanguagePlaceholderTakesTheCodeWhereverItStands() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));

        Published atTheEnd = published(english, Map.of(ROOT_TITLE_VARIABLE, "Hand-overs {language}"),
                ConfluenceValues.NONE);
        Published twice = published(english, Map.of(ROOT_TITLE_VARIABLE, "{language} notes, {language}"),
                ConfluenceValues.NONE);

        claim("Hand-overs {language} is Hand-overs en, nothing appended",
                () -> assertThat(atTheEnd.tree()).isEqualTo("Hand-overs en"));
        claim("and {language} notes, {language} is en notes, en: each placeholder takes the code",
                () -> assertThat(twice.tree()).isEqualTo("en notes, en"));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("A title with no language placeholder has an underscore and the language's code appended, once its blanks and control characters are folded")
    void withNoPlaceholderAnUnderscoreAndTheCodeAreAppendedToTheFoldedTitle() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        Rendering italian = Rendering.drawnIn(folder.resolve("it"), Rendering.ITALIAN);

        Published plain = published(english, Map.of(ROOT_TITLE_VARIABLE, "Hand-overs"), ConfluenceValues.NONE);
        Published plainInItalian = published(italian, Map.of(ROOT_TITLE_VARIABLE, "Hand-overs"), ConfluenceValues.NONE);
        Published spaced = published(english, Map.of(ROOT_TITLE_VARIABLE, "  Hand-overs\tfor\n the team  "),
                ConfluenceValues.NONE);

        claim("Hand-overs is Hand-overs_en in English", () -> assertThat(plain.tree()).isEqualTo("Hand-overs_en"));
        claim("and Hand-overs_it in Italian", () -> assertThat(plainInItalian.tree()).isEqualTo("Hand-overs_it"));
        claim("a title with blanks around it and a tab and a line break in it is folded to single spaces and stripped"
                        + " before the code is appended",
                () -> assertThat(spaced.tree()).isEqualTo("Hand-overs for the team_en"));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("A title the shell names comes before the profile's, a blank one being none, and the profile's comes before the default")
    void theShellsTitleComesBeforeTheProfilesAndABlankOneIsNone() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        ConfluenceValues profile = theProfileNaming(Map.of(ROOT_TITLE_COMPONENT, "From the profile"));

        Published profilesAlone = published(english, Map.of(), profile);
        Published both = published(english, Map.of(ROOT_TITLE_VARIABLE, "From the shell"), profile);
        Published shellBlank = published(english, Map.of(ROOT_TITLE_VARIABLE, "  "), profile);

        claim("the profile's title alone gives the tree its title",
                () -> assertThat(profilesAlone.tree()).isEqualTo("From the profile_en"));
        claim("the shell's title beside it comes first",
                () -> assertThat(both.tree()).isEqualTo("From the shell_en"));
        claim("and a blank title in the shell is none, so the profile's is read",
                () -> assertThat(shellBlank.tree()).isEqualTo("From the profile_en"));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("A title that comes to 256 characters is refused before anything is sent, naming where it was stated, and one that comes to 255 is not")
    void aTitlePastConfluencesLimitIsRefusedBeforeAnythingIsSentAndOneAtItIsNot() throws IOException {
        Rendering english = Rendering.drawnIn(folder.resolve("en"));
        ConfluenceValues profilePastTheLimit = theProfileNaming(Map.of(ROOT_TITLE_COMPONENT, PAST_THE_LIMIT));
        Map<String, String> everyValueAndTooLong = new LinkedHashMap<>();
        everyValueAndTooLong.put("baseUrl", StubConfluence.BASE_URL);
        everyValueAndTooLong.put("spaceKey", StubConfluence.SPACE_KEY);
        everyValueAndTooLong.put("folderId", StubConfluence.PARENT_ID);
        everyValueAndTooLong.put("email", StubConfluence.USER);
        everyValueAndTooLong.put(ROOT_TITLE_COMPONENT, PAST_THE_LIMIT);
        ConfluenceValues panelsProfile = theProfileNaming(everyValueAndTooLong);

        Published fromTheShell = published(english, Map.of(ROOT_TITLE_VARIABLE, PAST_THE_LIMIT), ConfluenceValues.NONE);
        Published fromTheProfile = published(english, Map.of(), profilePastTheLimit);
        Published fromThePanel = publishedByThePanel(english, panelsProfile);
        Published atTheLimit = published(english, Map.of(ROOT_TITLE_VARIABLE, AT_THE_LIMIT), ConfluenceValues.NONE);

        claim("typed on the command line with the shell's title past the limit, the publish is refused naming the"
                        + " variable and the title's 256 characters",
                () -> assertThat(fromTheShell.outcome().closingLine()).isEqualTo(SHELLS_TITLE_TOO_LONG));
        claim("typed on the command line with the profile's title past the limit, it is refused naming the key",
                () -> assertThat(fromTheProfile.outcome().closingLine()).isEqualTo(PROFILES_TITLE_TOO_LONG));
        claim("started by the control panel, it is refused naming the key and the Profile window",
                () -> assertThat(fromThePanel.outcome().closingLine()).isEqualTo(PROFILES_TITLE_TOO_LONG_FROM_THE_PANEL));
        claim("none of the three published, built a request or wrote a file in the working directory",
                () -> assertThat(List.of(fromTheShell, fromTheProfile, fromThePanel)).allSatisfy(refused -> {
                    assertThat(refused.outcome().published()).isFalse();
                    assertThat(refused.requested()).isEmpty();
                    assertThat(refused.written()).isEmpty();
                }));
        claim("a title that comes to 255 characters is not refused: it names the tree whole, uncut, and the publish"
                        + " goes on to its first request",
                () -> {
                    assertThat(atTheLimit.tree()).isEqualTo(AT_THE_LIMIT + ENGLISH_SUFFIX).hasSize(LONGEST_TITLE);
                    assertThat(atTheLimit.requested()).isNotEmpty();
                });
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("Each template a publish writes sets the root page's title variable to nothing")
    void eachTemplateSetsTheRootTitlesVariableToNothing() throws IOException {
        Map<String, String> shipped = new LinkedHashMap<>();
        for (String template : Publisher.TEMPLATES) {
            try (InputStream in = Publisher.class.getResourceAsStream(template)) {
                shipped.put(template, in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        claim("the templates are the shell's and PowerShell's",
                () -> assertThat(shipped.keySet()).containsExactlyInAnyOrderElementsOf(EACH_TEMPLATES_LINE.keySet()));
        claim("and each holds a line setting VESPERA_CONFLUENCE_ROOT_TITLE to nothing, so that an empty one gives the"
                        + " default title",
                () -> assertThat(shipped).allSatisfy((template, text) ->
                        assertThat(EACH_TEMPLATES_LINE.get(template).matcher(text).find())
                                .as("%s sets %s to ''", template, ROOT_TITLE_VARIABLE).isTrue()));
    }

    // ---- what the tests share

    /**
     * How one publish went: its outcome, its lines, the addresses of the requests it asked the factory to build, none of
     * which was sent, and the names of the files the working directory holds after it.
     */
    private record Published(Publisher.Outcome outcome, List<String> lines, List<URI> requested, List<String> written) {

        /** The title the publish's first line names the tree by, or what it said instead. */
        String tree() {
            if (lines.isEmpty()) {
                return "(the publish said no line; it ended: " + outcome.closingLine() + ")";
            }
            Matcher first = FIRST_LINE.matcher(lines.getFirst());
            return first.find() ? first.group(1) : "(its first line names no tree: " + lines.getFirst() + ")";
        }
    }

    /** The first line of a publish, which names the tree before any request (ADR-378 §7). */
    private static final Pattern FIRST_LINE = Pattern.compile("^vespera publish is sending the rendering of generation"
            + " run [0-9a-f]{12} to " + Pattern.quote(StubConfluence.SITE_HOST) + ", space "
            + Pattern.quote(StubConfluence.SPACE_KEY) + ", tree (.*): [0-9]+ pages? and [0-9]+ files? holding ");

    /**
     * A publish of {@code rendering} typed on the command line: the shell holding the account's email and token and
     * {@code shell}, the site, the space and the parent given as properties, {@code profile} as the working directory's
     * profile.
     */
    private Published published(Rendering rendering, Map<String, String> shell, ConfluenceValues profile)
            throws IOException {
        Map<String, String> environment = new HashMap<>(Map.of(StubConfluence.EMAIL_VARIABLE, StubConfluence.USER,
                ConfluenceSite.TOKEN_VARIABLE, StubConfluence.TOKEN));
        environment.putAll(shell);
        return publishedWith(rendering, environment, StubConfluence.BASE_URL, StubConfluence.SPACE_KEY,
                StubConfluence.PARENT_ID, profile);
    }

    /** A publish of {@code rendering} the control panel started: the token alone in its environment, no property set. */
    private Published publishedByThePanel(Rendering rendering, ConfluenceValues profile) throws IOException {
        return publishedWith(rendering, Map.of(STARTED_BY_VARIABLE, THE_CONTROL_PANEL, ConfluenceSite.TOKEN_VARIABLE,
                StubConfluence.TOKEN), "", "", "", profile);
    }

    private Published publishedWith(Rendering rendering, Map<String, String> environment, String baseUrl,
            String spaceKey, String parentId, ConfluenceValues profile) throws IOException {
        Path workingDirectory = Files.createTempDirectory(folder, "work");
        List<URI> requested = new ArrayList<>();
        ClientHttpRequestFactory buildsNone = (uri, method) -> {
            requested.add(uri);
            throw new IOException("this test sends nothing");
        };
        Publisher publisher = new Publisher(environment::get, buildsNone, duration -> { }, Clock.systemUTC(), baseUrl,
                spaceKey, parentId, "", directory -> profile);
        List<String> lines = new ArrayList<>();
        Publisher.Outcome outcome = publisher.publish(rendering.confluence(), rendering.run(), workingDirectory,
                lines::add);
        List<String> written;
        try (Stream<Path> files = Files.list(workingDirectory)) {
            written = files.map(file -> file.getFileName().toString()).toList();
        }
        return new Published(outcome, lines, requested, written);
    }

    /**
     * What the working directory's profile names, by the names of {@link ConfluenceValues}' components, each component
     * it does not name being {@code null}: built through the record's canonical constructor by those names, so that a
     * name the record has no component for stops the test, saying what the record holds.
     */
    private static ConfluenceValues theProfileNaming(Map<String, String> named) {
        RecordComponent[] components = ConfluenceValues.class.getRecordComponents();
        List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();
        List<String> missing = named.keySet().stream().filter(name -> !names.contains(name)).toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("what a publish reads of the profile holds no " + missing + ": it holds " + names);
        }
        Object[] values = new Object[components.length];
        named.forEach((name, value) -> values[names.indexOf(name)] = value);
        try {
            return ConfluenceValues.class.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(values);
        } catch (ReflectiveOperationException unbuilt) {
            throw new IllegalStateException("the profile's values could not be built from " + named, unbuilt);
        }
    }
}
