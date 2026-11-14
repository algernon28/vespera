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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
 * The texts of the Profile tab's rows (ADR-377 §4, §5): each key's title, short description and tooltip are data in
 * {@code profile-texts.json}, and every text a row shows, the cost line, its warnings and the line of the value in use
 * among them, is held to the record's rules: the work named in plain words and never by a stage, no time stated, a row
 * and a report named by their titles, and no text saying a failed file is read again unless it says only crashes and
 * time-outs are.
 *
 * <p>The words checked are the record's list, a stage's name as the jar spells it among them. Before a text is checked,
 * each key's name as {@code profile.yaml} spells it, each file name ending {@code .html}, {@code .yaml} or {@code .csv}
 * and each word holding a {@code /}, which is a path or a model's name, are taken out. The verb <i>run</i> of {@code
 * modelsRunOn}'s three texts, which ADR-384 §7 words, is the one exception to the word <i>run</i>: the record's rule
 * rewords <i>run</i> as a noun, the operator's start of Vespera.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("802")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProfileTextsTest {

    /** The data file, where the class path holds it beside {@code ProfileKey}. */
    private static final String THE_FILE = "/io/algernon/vespera/ui/profile-texts.json";

    /** The three texts each key's entry holds. */
    private static final List<String> THE_THREE = List.of("title", "short", "tooltip");

    /** The words no text says, beside the stages' names. */
    private static final List<String> NEVER_SAID = List.of("invocation", "run", "runs", "cluster", "embedding",
            "arrangement", "deliverable", "converter", "step", "makes again");

    /** The two words of {@code modelsRunOn}'s texts that are the verb of where the models run (ADR-384 §7). */
    private static final Set<String> THE_VERB = Set.of("run", "runs");

    /** The sources {@code effective-values.properties} names, and one it never names, for the line of the value in use. */
    private static final List<String> SOURCES = List.of("shipped", "local-file", "command-line", "environment",
            "code", "unknown");

    /** A text a tooltip names in double quotes, then the word after it. */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\" (\\w+)");

    /** A time taken: a number, then a unit of time, as a whole word. */
    private static final Pattern A_TIME = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:ms|s|sec|seconds?|min|minutes?|h|hours?)\\b|~\\s*\\d", Pattern.CASE_INSENSITIVE);

    /** The words of a text that speaks of trying a file again. */
    private static final List<String> TRYING_AGAIN = List.of("retry", "retried", "tried again", "try again",
            "read again the files");

    /** The keys whose tooltips name no report, so that the check of a tooltip naming one is not passed by none. */
    private static final List<ProfileKey> NAMING_NO_REPORT = List.of(ProfileKey.GENERATION_CONTEXT_WINDOW,
            ProfileKey.GENERATION_MODEL, ProfileKey.EMBEDDING_MODEL);

    @Test
    @Story("A setting's texts are kept in one file")
    @DisplayName("Every setting has a title, a short description and a tooltip, and the file lists no other")
    @SuppressWarnings("unchecked")
    void everyKeyHasATitleAShortDescriptionAndATooltipAndNoOtherKeyIsListed() throws IOException {
        Map<String, Object> file;
        try (InputStream in = ProfileKey.class.getResourceAsStream(THE_FILE)) {
            claim("the class path holds the file beside the class of the settings",
                    () -> assertThat(in).isNotNull());
            file = (Map<String, Object>) JsonReader.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<String> keys = Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();

        claim("the file's names are the settings' keys, each once, in the order of the settings",
                () -> assertThat(List.copyOf(file.keySet())).containsExactlyElementsOf(keys));
        for (String key : keys) {
            Map<String, Object> entry = (Map<String, Object>) file.get(key);
            claim(key + "'s entry holds a title, a short description and a tooltip, each text, none blank, and nothing"
                            + " else",
                    () -> assertThat(entry).containsOnlyKeys(THE_THREE).allSatisfy((name, text) -> assertThat(text)
                            .isInstanceOfSatisfying(String.class, words -> assertThat(words).isNotBlank())));
            String title = (String) entry.get("title");
            String shortDescription = (String) entry.get("short");
            claim(key + "'s title and short description are each on one line",
                    () -> assertThat(List.of(title, shortDescription)).noneMatch(text -> text.contains("\n")));
            claim(key + "'s short description is one sentence, ending in a stop, with no other sentence's end inside",
                    () -> assertThat(shortDescription).endsWith(".").doesNotContainPattern("[.!?] [A-Z]"));
        }
        claim("the seed folder's title, read by the class of the settings, is Seed folder",
                () -> assertThat(ProfileKey.SEED_FOLDER.title()).isEqualTo("Seed folder"));
    }

    @Test
    @Story("A setting's texts are kept in one file")
    @DisplayName("No tooltip names its own setting by the name the profile file spells, which stands beneath the title")
    void aTooltipDoesNotNameItsOwnKey() {
        for (ProfileKey key : ProfileKey.values()) {
            claim(key.key() + "'s tooltip does not hold " + key.key(),
                    () -> assertThat(key.tooltip()).doesNotContain(key.key()));
        }
    }

    @Test
    @Story("A setting's texts say the work in plain words")
    @DisplayName("No text of a setting's row names a stage, a run, a group by its internal word or a step")
    void noTextNamesAStageAnInvocationAClusterOrAnEmbedding() {
        List<String> forbidden = new ArrayList<>(NEVER_SAID);
        Arrays.stream(ProfileKey.Run.values()).map(ProfileKey.Run::stageName).forEach(forbidden::add);
        Map<String, String> texts = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            texts.putAll(rowTextsOf(key));
        }
        SOURCES.forEach(source -> texts.put("the line of a value in use from " + source,
                ProfileRow.inUseOf(new EffectiveValues.Value("qwen3:8b", source))));
        Reports.TITLES.forEach((name, title) -> texts.put("the title of the report " + name, title));

        claim("the texts checked are every setting's title, short description, tooltip, cost line and warnings, the"
                        + " line of a value in use for each source, and every report's title",
                () -> assertThat(texts).hasSizeGreaterThan(ProfileKey.values().length * THE_THREE.size()
                        + SOURCES.size() + Reports.ALL.size()));
        texts.forEach((what, text) -> {
            String checked = withoutNames(text).toLowerCase(Locale.ROOT);
            boolean theVerbIsAllowed = what.startsWith(ProfileKey.MODELS_RUN_ON.key() + "'s ")
                    && !what.contains("cost line") && !what.contains("warning");
            List<String> said = forbidden.stream()
                    .filter(word -> !(theVerbIsAllowed && THE_VERB.contains(word)))
                    .filter(word -> Pattern.compile("(?<![\\w-])" + Pattern.quote(word) + "(?![\\w-])")
                            .matcher(checked).find())
                    .toList();
            claim(what + " says none of the words that name the work by the program's own terms",
                    () -> assertThat(said).isEmpty());
        });
    }

    @Test
    @Story("A setting's texts name a row and a report by their titles")
    @DisplayName("A tooltip names another setting and a report by the titles the window shows, and only reports it links")
    void aTooltipNamesRowsAndReportsByTheirTitles() {
        Set<String> titles = Set.copyOf(Arrays.stream(ProfileKey.values()).map(ProfileKey::title).toList());
        for (ProfileKey key : ProfileKey.values()) {
            String tooltip = key.tooltip();
            claim(key.key() + "'s tooltip names no page by its file name",
                    () -> assertThat(tooltip).doesNotContainPattern("\\S+\\.html\\b"));
            List<String> rowsNamed = new ArrayList<>();
            List<String> reportsNamed = new ArrayList<>();
            Matcher quoted = QUOTED.matcher(tooltip);
            while (quoted.find()) {
                if (quoted.group(2).equals("row")) {
                    rowsNamed.add(quoted.group(1));
                } else if (quoted.group(2).equals("report")) {
                    reportsNamed.add(quoted.group(1));
                }
            }
            claim(key.key() + "'s tooltip names each row by a setting's title",
                    () -> assertThat(titles).containsAll(rowsNamed));
            List<String> linked = Stream.concat(Stream.of(key), Arrays.stream(ProfileKey.values())
                            .filter(other -> rowsNamed.contains(other.title())))
                    .flatMap(row -> Reports.OF_A_KEY.getOrDefault(row.key(), List.of()).stream())
                    .map(Reports.TITLES::get).toList();
            claim(key.key() + "'s tooltip names each report by its title, and only a report its own row links or a"
                            + " row it names links",
                    () -> assertThat(linked).containsAll(reportsNamed));
            if (NAMING_NO_REPORT.contains(key)) {
                claim(key.key() + "'s tooltip names no report, its row linking none",
                        () -> assertThat(reportsNamed).isEmpty());
            }
        }
        claim("the reports' titles are read from the class that lists them, so that a title is checked against one",
                () -> assertThat(Reports.TITLES.keySet()).containsExactlyElementsOf(Reports.ALL));
    }

    @Test
    @Story("A setting's texts state no time")
    @DisplayName("No text of a setting's row states how long anything takes")
    void noTextStatesATime() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            texts.putAll(rowTextsOf(key));
        }
        SOURCES.forEach(source -> texts.put("the line of a value in use from " + source,
                ProfileRow.inUseOf(new EffectiveValues.Value("qwen3:8b", source))));
        claim("a clock time, which takes no time, is not read as one",
                () -> assertThat(A_TIME.matcher("a time alone (10:15:30)").find()).isFalse());
        claim("a number with a unit of time is read as one",
                () -> assertThat(A_TIME.matcher("about 5 minutes").find()).isTrue());
        texts.forEach((what, text) -> claim(what + " states no time",
                () -> assertThat(A_TIME.matcher(text).find()).isFalse()));
    }

    @Test
    @Story("A setting's texts say which files a new try reads")
    @DisplayName("A text that speaks of trying a file again says only crashes and time-outs of the reader are tried again")
    void aTextThatSpeaksOfTryingAFileAgainSaysOnlyCrashesAndTimeOutsAre() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            texts.put(key.key() + "'s title", key.title());
            texts.put(key.key() + "'s short description", key.shortDescription());
            texts.put(key.key() + "'s tooltip", key.tooltip());
            texts.put(key.key() + "'s cost line", ProfileRow.costOf(key));
        }
        List<String> speaking = texts.entrySet().stream()
                .filter(text -> TRYING_AGAIN.stream().anyMatch(text.getValue().toLowerCase(Locale.ROOT)::contains))
                .map(Map.Entry::getKey).toList();

        claim("the try number's short description and tooltip are among the texts that speak of trying again, so that"
                        + " the check reads some",
                () -> assertThat(speaking).contains("extractionAttempt's short description", "extractionAttempt's tooltip"));
        for (String what : speaking) {
            String text = texts.get(what).toLowerCase(Locale.ROOT);
            claim(what + " says that the files whose reading crashed or took too long are the ones tried again",
                    () -> assertThat(text).contains("crash")
                            .containsAnyOf("took too long", "time-out", "timeout"));
        }
    }

    /** Every text of a key's row: its title, short description, tooltip, cost line and each warning. */
    private static Map<String, String> rowTextsOf(ProfileKey key) {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(key.key() + "'s title", key.title());
        texts.put(key.key() + "'s short description", key.shortDescription());
        texts.put(key.key() + "'s tooltip", key.tooltip());
        texts.put(key.key() + "'s cost line", ProfileRow.costOf(key));
        List<String> warnings = ProfileRow.warningsOf(key);
        for (int i = 0; i < warnings.size(); i++) {
            texts.put(key.key() + "'s warning " + (i + 1), warnings.get(i));
        }
        return texts;
    }

    /** A text with each key's name, each file name ending .html, .yaml or .csv and each word holding a / taken out. */
    private static String withoutNames(String text) {
        String out = text.replaceAll("\\S*/\\S*", " ").replaceAll("\\S+\\.(?:html|yaml|csv)\\b", " ");
        for (ProfileKey key : ProfileKey.values()) {
            out = out.replace(key.key(), " ");
        }
        return out;
    }
}
