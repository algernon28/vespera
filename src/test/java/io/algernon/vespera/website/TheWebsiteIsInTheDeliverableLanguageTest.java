package io.algernon.vespera.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.website.WebsiteFixture.pagesUnder;
import static io.algernon.vespera.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.AnItalianTreeIsDrawnInItalianTest;
import io.algernon.vespera.deliverable.FixedWords;
import io.algernon.vespera.deliverable.ItalianWordingOnRecord;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every fixed word of the website rendering is drawn in the deliverable language, from the table the HTML tree and
 * the Confluence rendering draw from, {@code FixedWords} (ADR-321, ADR-333 §3): an Italian tree's website pages say
 * {@code lang="it"}, head a group's documents with the Italian heading, which names a <em>gruppo</em>, and carry no
 * English word of the table.
 *
 * <p>Drawn over {@code deliverable}'s Italian fixture with the website rendering's plugin, its written group split a
 * page an entry by a ceiling of one byte, so the website's pages split where the HTML tree's do (ADR-303).
 *
 * <p><b>No English word of the table</b> is read two ways: the phrases {@code AnItalianTreeIsDrawnInItalianTest}
 * holds the HTML tree to, on the whole page; and each English word {@code FixedWords} gives that differs from its
 * Italian, cut at each {@code %d} of a format, every piece of four letters or more, in the page's text and in the
 * attributes a reader is shown, {@code title}, {@code alt}, {@code placeholder} and {@code aria-label}. So a fixed
 * word the website adds to the table, its search's among them, is held in Italian as soon as it is added.
 */
@Epic("The deliverable")
@Feature("The website rendering")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
class TheWebsiteIsInTheDeliverableLanguageTest {

    /** The attributes whose values a reader is shown. */
    private static final Pattern A_SHOWN_ATTRIBUTE =
            Pattern.compile("\\b(?:title|alt|placeholder|aria-label)=\"([^\"]*)\"");

    private static final Pattern A_TAG = Pattern.compile("<[^>]*>");

    private static final Pattern A_SCRIPT_OR_STYLE =
            Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("Every page of an Italian tree's website rendering is in Italian, names a group gruppo and carries no English word of the table")
    void everyWebsitePageIsInItalian(@TempDir Path base) throws IOException {
        Path tree = AnItalianTreeIsDrawnInItalianTest.draw(base, WebsiteFixture.THE_WEBSITE_PLUGIN);
        Path website = tree.resolve(WebsiteFixture.WEBSITE);
        List<String> htmlTreePages = pagesUnder(tree).stream()
                .filter(name -> !name.startsWith("website/") && !name.startsWith("confluence/"))
                .toList();
        Map<String, String> pages = new LinkedHashMap<>();
        for (String name : pagesUnder(website)) {
            pages.put(name, asRead(textOf(website.resolve(name))));
        }
        List<String> clusterPages = pages.keySet().stream().filter(name -> !name.equals("index.html")).toList();

        claim(
                "the website's pages are the HTML tree's, split where they are: the index, the written group's "
                        + AnItalianTreeIsDrawnInItalianTest.FIVE_ENTRIES + " pages and the unwritten group's one",
                () -> assertThat(pages.keySet())
                        .containsExactlyElementsOf(htmlTreePages)
                        .hasSize(1 + AnItalianTreeIsDrawnInItalianTest.FIVE_ENTRIES + 1));
        claim(
                "each opens as an HTML page in Italian",
                () -> assertThat(pages).allSatisfy((name, page) -> assertThat(page)
                        .as(name)
                        .startsWith("<!DOCTYPE html>")
                        .contains("<html lang=\"" + ItalianWordingOnRecord.LANG + "\"")));
        claim(
                "each group's page heads its documents in Italian, naming a gruppo",
                () -> assertThat(clusterPages).allSatisfy(name -> assertThat(pages.get(name))
                        .as(name)
                        .contains(ItalianWordingOnRecord.MEMBERSHIP_HEADING)
                        .contains("gruppo")));
        claim(
                "the written group's first page says in Italian which of its pages it is",
                () -> assertThat(pages.get("1-sicurezza/1-porte/index.html"))
                        .contains(ItalianWordingOnRecord.PAGE_OF.formatted(1, AnItalianTreeIsDrawnInItalianTest.FIVE_ENTRIES)));
        claim(
                "and the entry whose original is gone from the archive says why in Italian",
                () -> assertThat(pages.get("1-sicurezza/1-porte/index-" + AnItalianTreeIsDrawnInItalianTest.FIVE_ENTRIES
                                + ".html"))
                        .contains(ItalianWordingOnRecord.GONE));
        claim(
                "no page carries an English phrase the HTML tree's Italian pages are held free of",
                () -> assertThat(pages).allSatisfy((name, page) -> assertThat(page)
                        .as(name)
                        .doesNotContain(AnItalianTreeIsDrawnInItalianTest.ENGLISH_FIXED_WORDS)));

        List<String> englishWords = new ArrayList<>(englishWordsOfTheTable());
        claim(
                "the table was read: its English words that differ from the Italian are many, the membership heading"
                        + " among them",
                () -> assertThat(englishWords).hasSizeGreaterThan(10).contains("The documents in this group"));
        // The website's search is a fixed word of its own, whatever the table calls it: in Italian, never "Search".
        englishWords.add("Search");
        for (Map.Entry<String, String> page : pages.entrySet()) {
            String shown = shownTextOf(page.getValue());
            claim(
                    page.getKey() + " shows a reader none of the table's English words, in its text or its shown"
                            + " attributes",
                    () -> assertThat(englishWords).allSatisfy(word -> assertThat(standsIn(word, shown))
                            .as("the English \"%s\" on %s", word, page.getKey())
                            .isFalse()));
        }
    }

    /**
     * Each English value of {@code FixedWords}' methods taking a language alone, where it differs from the Italian,
     * cut at each placeholder of a format, every piece of four letters or more, stripped.
     */
    private static List<String> englishWordsOfTheTable() {
        TreeSet<String> words = new TreeSet<>();
        for (Method method : FixedWords.class.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[] {DeliverableLanguage.class})) {
                continue;
            }
            List<String> english = valuesOf(method, DeliverableLanguage.ENGLISH);
            List<String> italian = valuesOf(method, DeliverableLanguage.ITALIAN);
            for (String value : english) {
                if (italian.contains(value)) {
                    continue;
                }
                for (String piece : value.split("%[0-9$]*[dsf]")) {
                    String stripped = piece.strip();
                    if (stripped.codePoints().filter(Character::isLetter).count() >= 4) {
                        words.add(stripped);
                    }
                }
            }
        }
        return new ArrayList<>(words);
    }

    private static List<String> valuesOf(Method method, DeliverableLanguage language) {
        try {
            Object value = method.invoke(null, language);
            if (value instanceof String text) {
                return List.of(text);
            }
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FixedWords." + method.getName() + " could not be read", e);
        }
    }

    /** What {@code page} shows a reader: its text outside scripts and styles, and its shown attributes' values. */
    private static String shownTextOf(String page) {
        String withoutCode = A_SCRIPT_OR_STYLE.matcher(page).replaceAll(" ");
        StringBuilder shown = new StringBuilder(A_TAG.matcher(withoutCode).replaceAll(" "));
        Matcher attribute = A_SHOWN_ATTRIBUTE.matcher(withoutCode);
        while (attribute.find()) {
            shown.append(' ').append(attribute.group(1));
        }
        return shown.toString();
    }

    /** Whether {@code word} stands in {@code text}, not as part of a longer word. */
    private static boolean standsIn(String word, String text) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }

    /** {@code html} as a reader reads it: the five entities read back. */
    private static String asRead(String html) {
        return html.replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
