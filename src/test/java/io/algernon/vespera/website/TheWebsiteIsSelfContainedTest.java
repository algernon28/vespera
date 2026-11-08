package io.algernon.vespera.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.website.WebsiteFixture.SEARCH_INDEX;
import static io.algernon.vespera.website.WebsiteFixture.pagesUnder;
import static io.algernon.vespera.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The website rendering is self-contained (ADR-333 §3): its script and its theme are ours, shipped inside the jar and
 * drawn into it, with no CDN; every {@code src} and {@code href} is relative and, once a publish has refreshed the
 * website, lands inside it; no stylesheet names a host; nothing is fetched, the search reading what {@code
 * search-index.js} set; and {@code files.json} lists every file of the website, the copies only listed, their keys
 * stage 2's (ADR-316, ADR-313 §1), a copy that did not stand not listed and its entry saying why.
 *
 * <p><b>Where the script and the theme are shipped.</b> {@code website.js} and {@code website.css} are resources of the
 * {@code website} module, at {@code io/algernon/vespera/website/} on the class path, as {@code confluence}'s own
 * scripts are; the rendering carries each byte for byte. {@code src/test/website/website.test.mjs} reads the script
 * at that place in the source tree, so what {@code node --test} holds is what the rendering ships.
 *
 * <p><b>What a page carries for its search.</b> Each page loads {@code search-index.js} and {@code website.js} by a
 * {@code <script src>} and {@code website.css} by an {@code href}, each relative and landing on the file at the
 * website's root, and holds the two elements {@code website.js} binds to: the query, {@code id="search"}, and the
 * place what it finds is written, {@code id="search-results"}. {@code website.test.mjs} drives the script through
 * those two ids.
 */
@Epic("The deliverable")
@Feature("The website rendering")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
class TheWebsiteIsSelfContainedTest {

    /** Where the website module ships its script and its theme, on the class path. */
    private static final String SHIPPED = "/io/algernon/vespera/website/";

    private static final Pattern A_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    /** A {@code url(...)} of a stylesheet, its value with any quotes around it. */
    private static final Pattern A_URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]*)['\"]?\\s*\\)");

    /** What a script that reaches the network or loads code would name. */
    private static final List<String> WHAT_FETCHES = List.of(
            "fetch(", "XMLHttpRequest", "import(", "importScripts", "WebSocket", "EventSource", "sendBeacon",
            "http:", "https:");

    /** What writes markup, which the search's script never does: it writes what it finds as text (ADR-333 §3). */
    private static final List<String> WHAT_WRITES_MARKUP =
            List.of("insertAdjacentHTML", "outerHTML", "document.write", "eval(", "new Function");

    @Test
    @Story("The website is self-contained")
    @DisplayName("Nothing of the website is fetched or names a host: the theme imports nothing, and the script reaches no network and writes no markup")
    void nothingIsFetchedAndNothingNamesAHost(@TempDir Path base) throws IOException {
        Path rendering = renderingOf(WebsiteFixture.drawn(base));
        String css = textOf(rendering.resolve("website.css"));
        String script = textOf(rendering.resolve("website.js"));
        List<String> urls = new ArrayList<>();
        Matcher url = A_URL.matcher(css);
        while (url.find()) {
            urls.add(url.group(1).strip());
        }
        for (String page : pagesUnder(rendering)) {
            Matcher inPage = A_URL.matcher(textOf(rendering.resolve(page)));
            while (inPage.find()) {
                urls.add(inPage.group(1).strip());
            }
        }

        claim(
                "the theme imports no stylesheet",
                () -> assertThat(css).doesNotContain("@import"));
        claim(
                "no url( of the theme or of a page names a scheme, a host or a rooted path, but a picture's data",
                () -> assertThat(urls).allSatisfy(value -> {
                    if (!value.startsWith("data:")) {
                        assertThat(A_SCHEME.matcher(value).find()).as(value).isFalse();
                        assertThat(value).doesNotStartWith("/");
                    }
                }));
        claim(
                "the theme is light and dark by the reader's preferred colour scheme",
                () -> assertThat(css).contains("prefers-color-scheme"));
        claim(
                "the script fetches nothing and loads no code: what it searches is what search-index.js set",
                () -> assertThat(script).doesNotContain(WHAT_FETCHES));
        claim(
                "and it writes no markup: what a search finds is written as text",
                () -> assertThat(script).doesNotContain(WHAT_WRITES_MARKUP));
        claim(
                "the search's index fetches nothing either",
                () -> assertThat(textOf(rendering.resolve(SEARCH_INDEX))).doesNotContain(WHAT_FETCHES.subList(0, 7)));
    }

    private static byte[] shipped(String name) throws IOException {
        try (InputStream in = WebsitePlugin.class.getResourceAsStream(SHIPPED + name)) {
            assertThat(in).as("the resource " + SHIPPED + name).isNotNull();
            return in.readAllBytes();
        }
    }
}
