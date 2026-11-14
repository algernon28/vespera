package io.algernon.vespera.ui;

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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each profile key's title, short description and tooltip are data in {@code profile-texts.json}, held to the rules of
 * ADR-377 §4: one entry for each key in {@code ProfileKey}'s order, a short description of one sentence, a tooltip that
 * does not name its own key and names a row or a report only by its title, and no text, the cost lines, warnings and
 * lines of the value in use among them, that names the work by a stage or a word of this project's own, states a
 * time, or says that every file whose reading failed is read again (ADR-183).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("802")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProfileTextsTest {

    /** Where the texts lie on the class path, beside {@code ProfileKey}. */
    private static final String THE_TEXTS = "/io/algernon/vespera/ui/profile-texts.json";

    /** The three texts each key's entry holds. */
    private static final List<String> THE_THREE_TEXTS = List.of("title", "short", "tooltip");

    /** The title of the seed folder's row, which ADR-377 §4's table fixes. */
    private static final String THE_SEED_FOLDERS_TITLE = "Seed folder";

    /** The sources a value in use may be given, the five §4 names and two it does not. */
    private static final List<String> SOURCES =
            List.of("shipped", "local-file", "command-line", "environment", "code", "unknown", "nowhere");

    /** The words no text says, a stage's name aside, as whole words in any case. */
    private static final List<String> WORDS_NO_TEXT_SAYS = List.of("invocation", "run", "runs", "cluster", "embedding",
            "arrangement", "deliverable", "converter", "step", "makes again");

    /** The keys whose tooltips name no report, because their rows link none. */
    private static final List<ProfileKey> ROWS_THAT_LINK_NO_REPORT =
            List.of(ProfileKey.GENERATION_CONTEXT_WINDOW, ProfileKey.GENERATION_MODEL, ProfileKey.EMBEDDING_MODEL);

    /** A number followed by a unit of time, as a whole word. */
    private static final Pattern A_TIME = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:ms|s|sec|seconds?|min|minutes?|h|hours?)\\b", Pattern.CASE_INSENSITIVE);

    /** A clock time, which is no time taken, taken out before {@link #A_TIME} is looked for. */
    private static final Pattern A_CLOCK_TIME = Pattern.compile("\\d{1,2}:\\d{2}(?::\\d{2})?");

    /** An estimate: a tilde before a number. */
    private static final Pattern AN_ESTIMATE = Pattern.compile("~\\s*\\d");

    /** What a text says of trying a file again. */
    private static final Pattern TRYING_AGAIN =
            Pattern.compile("retry|retried|tried again|try again|read again the files", Pattern.CASE_INSENSITIVE);

    /** What such a text must also say: that only the reader's crashes and time-outs are tried again. */
    private static final Pattern A_TIME_OUT = Pattern.compile("took too long|time-out|timeout", Pattern.CASE_INSENSITIVE);

    /** A double-quoted text followed by a word, as a tooltip names a row or a report. */
    private static final Pattern QUOTED_THEN = Pattern.compile("\"([^\"]+)\"\\s+(row|report)\\b");

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("Every key has a title, a one-sentence description and a tooltip, in the order of the keys, and no other key is listed")
    void everyKeyHasATitleAShortDescriptionAndATooltipAndNoOtherKeyIsListed() {
        @SuppressWarnings("unchecked")
        Map<String, Object> texts = (Map<String, Object>) JsonReader.read(theFile());

        claim("the file names exactly the keys profile.yaml holds, in the order the keys are listed",
                () -> assertThat(texts.keySet())
                        .containsExactlyElementsOf(Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList()));
        claim("each entry holds " + THE_THREE_TEXTS + ", none blank, and nothing else",
                () -> assertThat(texts.values()).allSatisfy(entry -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> three = (Map<String, Object>) entry;
                    assertThat(three).containsOnlyKeys(THE_THREE_TEXTS)
                            .allSatisfy((name, text) -> assertThat((String) text).isNotBlank());
                }));
        claim("each key's title and short description are on one line each",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> {
                    assertThat(key.title()).as("%s's title", key.key()).doesNotContain("\n");
                    assertThat(key.shortDescription()).as("%s's short description", key.key()).doesNotContain("\n");
                }));
        claim("each short description is one sentence ending in a stop: it ends in one, and no stop inside it is"
                        + " followed by a new sentence's capital",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(key.shortDescription())
                        .as("%s's short description", key.key())
                        .endsWith(".")
                        .doesNotContainPattern("[.!?]\\s+[A-Z]")));
        claim("and the seed folder's row is titled \"" + THE_SEED_FOLDERS_TITLE + "\"",
                () -> assertThat(ProfileKey.SEED_FOLDER.title()).isEqualTo(THE_SEED_FOLDERS_TITLE));
    }

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("No tooltip names its own key, which stands beneath the row's title")
    void aTooltipDoesNotNameItsOwnKey() {
        claim("no key's tooltip holds the key as profile.yaml spells it",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(key.tooltip())
                        .as("%s's tooltip", key.key())
                        .doesNotContain(key.key())));
    }

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("No text of a row names a step of the work by its internal name or says invocation, run, cluster or embedding")
    void noTextNamesAStageAnInvocationAClusterOrAnEmbedding() {
        List<String> words = new ArrayList<>(WORDS_NO_TEXT_SAYS);
        Arrays.stream(ProfileKey.Run.values()).map(ProfileKey.Run::stageName).forEach(words::add);
        List<String> texts = new ArrayList<>(everyTextOfARow());
        texts.addAll(Reports.TITLES.values());

        claim("every title, short description, tooltip, cost line, warning, line of the value in use and report title,"
                        + " each key's name, each file name and each word holding a slash taken out, holds none of "
                        + words + " as a whole word in any case",
                () -> assertThat(texts.stream().map(TheProfileTextsTest::withoutNames)).allSatisfy(text ->
                        assertThat(words).noneSatisfy(word -> assertThat(text)
                                .containsPattern(Pattern.compile("(?<![\\w-])" + Pattern.quote(word) + "(?![\\w-])",
                                        Pattern.CASE_INSENSITIVE)))));
    }

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("A tooltip names another row and a report only by its title, and only a report its row or a row it names links")
    void aTooltipNamesRowsAndReportsByTheirTitles() {
        Map<String, ProfileKey> byTitle = new java.util.HashMap<>();
        Arrays.stream(ProfileKey.values()).forEach(key -> byTitle.put(key.title(), key));
        Map<String, String> reportByTitle = new java.util.HashMap<>();
        Reports.TITLES.forEach((name, title) -> reportByTitle.put(title, name));

        claim("no tooltip holds a word ending .html",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(key.tooltip())
                        .as("%s's tooltip", key.key())
                        .doesNotContainPattern("\\S\\.html\\b")));
        claim("each double-quoted text a tooltip follows with \"row\" is a key's title",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(quotedBefore(key.tooltip(), "row"))
                        .as("the rows %s's tooltip names", key.key())
                        .allSatisfy(title -> assertThat(byTitle).containsKey(title))));
        claim("each double-quoted text a tooltip follows with \"report\" is the title of a report its own row links, or"
                        + " a row it names links",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> {
                    Set<String> linked = new LinkedHashSet<>(Reports.OF_A_KEY.getOrDefault(key.key(), List.of()));
                    quotedBefore(key.tooltip(), "row").stream().map(byTitle::get).filter(java.util.Objects::nonNull)
                            .forEach(row -> linked.addAll(Reports.OF_A_KEY.getOrDefault(row.key(), List.of())));
                    assertThat(quotedBefore(key.tooltip(), "report"))
                            .as("the reports %s's tooltip names", key.key())
                            .allSatisfy(title -> assertThat(linked).contains(reportByTitle.get(title)));
                }));
        claim("and the tooltips of " + ROWS_THAT_LINK_NO_REPORT.stream().map(ProfileKey::key).toList()
                        + ", whose rows link no report, name none, so that the check above cannot pass for a row that"
                        + " links none",
                () -> assertThat(ROWS_THAT_LINK_NO_REPORT).allSatisfy(key -> assertThat(
                                quotedBefore(key.tooltip(), "report"))
                        .as("the reports %s's tooltip names", key.key())
                        .isEmpty()));
    }

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("No text of a row states how long anything takes")
    void noTextStatesATime() {
        claim("no title, short description, tooltip, cost line, warning or line of the value in use holds a number"
                        + " followed by a unit of time, or a tilde before a number; a clock time is no time taken",
                () -> assertThat(everyTextOfARow()).allSatisfy(text -> {
                    String noClock = A_CLOCK_TIME.matcher(text).replaceAll(" ");
                    assertThat(noClock).doesNotContainPattern(A_TIME).doesNotContainPattern(AN_ESTIMATE);
                }));
    }

    @Test
    @Story("Each value's row explains it in plain words")
    @DisplayName("A text that speaks of trying a file again says only crashes and time-outs are tried again")
    void aTextThatSpeaksOfTryingAFileAgainSaysOnlyCrashesAndTimeOutsAre() {
        List<String> speakingOfIt = Arrays.stream(ProfileKey.values())
                .flatMap(key -> Stream.of(key.title(), key.shortDescription(), key.tooltip(), ProfileRow.costOf(key)))
                .filter(text -> TRYING_AGAIN.matcher(text).find())
                .toList();

        claim("the short description and the tooltip of the try number for unread files are among the texts that"
                        + " speak of trying a file again, so that a check of nothing cannot pass",
                () -> assertThat(speakingOfIt).contains(ProfileKey.EXTRACTION_ATTEMPT.shortDescription(),
                        ProfileKey.EXTRACTION_ATTEMPT.tooltip()));
        claim("and each of them says that a crash, and a reading that took too long, are what is tried again",
                () -> assertThat(speakingOfIt).allSatisfy(text -> assertThat(text.toLowerCase(Locale.ROOT))
                        .contains("crash")
                        .containsPattern(A_TIME_OUT)));
    }

    /** Every title, short description, tooltip, cost line, warning, and line of the value in use for each source. */
    private static List<String> everyTextOfARow() {
        List<String> texts = new ArrayList<>();
        for (ProfileKey key : ProfileKey.values()) {
            texts.addAll(List.of(key.title(), key.shortDescription(), key.tooltip(), ProfileRow.costOf(key)));
            texts.addAll(ProfileRow.warningsOf(key));
        }
        SOURCES.forEach(source -> texts.add(ProfileRow.inUseOf(new EffectiveValues.Value("qwen3:8b", source))));
        return texts;
    }

    /** {@code text} with each key's name, each file name ending .html, .yaml or .csv and each word holding a slash taken out. */
    private static String withoutNames(String text) {
        String without = text;
        for (ProfileKey key : ProfileKey.values()) {
            without = without.replace(key.key(), " ");
        }
        return without.replaceAll("\\S+\\.(?:html|yaml|csv)\\b", " ").replaceAll("\\S*/\\S*", " ");
    }

    /** The double-quoted texts {@code tooltip} follows with {@code word}. */
    private static List<String> quotedBefore(String tooltip, String word) {
        List<String> quoted = new ArrayList<>();
        Matcher matcher = QUOTED_THEN.matcher(tooltip);
        while (matcher.find()) {
            if (matcher.group(2).equals(word)) {
                quoted.add(matcher.group(1));
            }
        }
        return quoted;
    }

    private static String theFile() {
        try (InputStream in = TheProfileTextsTest.class.getResourceAsStream(THE_TEXTS)) {
            if (in == null) {
                throw new IllegalStateException("the class path holds no " + THE_TEXTS);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
