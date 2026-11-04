package io.algernon.vespera.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.website.WebsiteFixture.DAMPERS_FOLDER;
import static io.algernon.vespera.website.WebsiteFixture.DAMPER_SCAN_BYTES;
import static io.algernon.vespera.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.website.WebsiteFixture.DOOR_SURVEY_BYTES;
import static io.algernon.vespera.website.WebsiteFixture.FILES_JSON;
import static io.algernon.vespera.website.WebsiteFixture.INSPECTION_LOG_BYTES;
import static io.algernon.vespera.website.WebsiteFixture.SEARCH_INDEX;
import static io.algernon.vespera.website.WebsiteFixture.filesUnder;
import static io.algernon.vespera.website.WebsiteFixture.listedIn;
import static io.algernon.vespera.website.WebsiteFixture.pagesUnder;
import static io.algernon.vespera.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.website.WebsiteFixture.sha256;
import static io.algernon.vespera.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.CopyOutcome;
import io.algernon.vespera.deliverable.ListedTree;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

    private static final List<String> ASSETS = List.of("search-index.js", "website.css", "website.js");

    private static final Pattern A_TAG = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    private static final Pattern A_SOURCE_OR_LINK = Pattern.compile("\\b(src|href)=\"([^\"]*)\"");

    private static final Pattern AN_INLINED_PICTURE = Pattern.compile(
            "data:(image/png|image/jpeg);base64,((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?)");

    private static final Pattern A_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    /** A {@code url(...)} of a stylesheet, its value with any quotes around it. */
    private static final Pattern A_URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]*)['\"]?\\s*\\)");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** What a script that reaches the network or loads code would name. */
    private static final List<String> WHAT_FETCHES = List.of(
            "fetch(", "XMLHttpRequest", "import(", "importScripts", "WebSocket", "EventSource", "sendBeacon",
            "http:", "https:");

    /** What writes markup, which the search's script never does: it writes what it finds as text (ADR-333 §3). */
    private static final List<String> WHAT_WRITES_MARKUP =
            List.of("insertAdjacentHTML", "outerHTML", "document.write", "eval(", "new Function");

    @Test
    @Story("The website is self-contained")
    @DisplayName("The rendering carries the HTML tree's pages, the script and theme shipped in the jar, and a list of every file of the website, the copies only listed")
    void theRenderingCarriesItsPagesItsAssetsAndItsList(@TempDir Path base) throws IOException {
        Path tree = WebsiteFixture.drawn(base);
        Path rendering = renderingOf(tree);
        Map<String, WebsiteFixture.Listed> listed = listedIn(rendering);
        List<String> htmlTreePages = pagesUnder(tree).stream()
                .filter(name -> !name.startsWith("website/") && !name.startsWith("confluence/"))
                .toList();

        claim(
                "the rendering's pages are the HTML tree's, each at its place under website/: the index and a page for"
                        + " each group",
                () -> assertThat(pagesUnder(rendering))
                        .containsExactlyElementsOf(htmlTreePages)
                        .containsExactly(DOORS_FOLDER + "/index.html", DAMPERS_FOLDER + "/index.html", "index.html"));
        claim(
                "beside them at its root stand the search's index, the script, the theme, the list and the state of the"
                        + " drawing",
                () -> assertThat(filesUnder(rendering).keySet())
                        .contains(SEARCH_INDEX, "website.js", "website.css", FILES_JSON, Plugin.DRAWING_STATE_FILE_NAME));
        claim(
                "the drawing finished",
                () -> assertThat(textOf(rendering.resolve(Plugin.DRAWING_STATE_FILE_NAME)).strip()).isEqualTo("drawn"));
        claim(
                "the script and the theme are the jar's own, byte for byte as the website module ships them",
                () -> {
                    assertThat(rendering.resolve("website.js")).hasBinaryContent(shipped("website.js"));
                    assertThat(rendering.resolve("website.css")).hasBinaryContent(shipped("website.css"));
                });
        claim(
                "no copy is written into the rendering: its copies are only listed, standing in the HTML tree",
                () -> assertThat(filesUnder(rendering).keySet())
                        .noneMatch(name -> name.endsWith(".docx") || name.endsWith(".png")));

        Map<String, String> kinds = new TreeMap<>();
        listed.values().forEach(entry -> kinds.put(entry.path(), entry.kind()));
        Map<String, String> expected = new TreeMap<>();
        pagesUnder(rendering).forEach(page -> expected.put(page, "page"));
        ASSETS.forEach(asset -> expected.put(asset, "asset"));
        expected.put("documents.csv", "listing");
        expected.put(DOORS_FOLDER + "/door survey.docx", "copy");
        expected.put(DOORS_FOLDER + "/inspection-log.docx", "copy");
        expected.put(DAMPERS_FOLDER + "/damper #2.png", "copy");
        claim(
                "files.json lists every file of the website and nothing else, each by its kind: each page, the three"
                        + " assets, documents.csv as a listing and each copy that stands, and neither itself nor"
                        + " drawing.txt",
                () -> assertThat(kinds).containsExactlyEntriesOf(expected));
        claim(
                "the copy of the document gone from the archive is not listed",
                () -> assertThat(kinds.keySet()).noneMatch(path -> path.contains("gone-from-the-archive")));

        Map<String, byte[]> copies = Map.of(
                DOORS_FOLDER + "/door survey.docx", DOOR_SURVEY_BYTES,
                DOORS_FOLDER + "/inspection-log.docx", INSPECTION_LOG_BYTES,
                DAMPERS_FOLDER + "/damper #2.png", DAMPER_SCAN_BYTES);
        for (Map.Entry<String, byte[]> copy : copies.entrySet()) {
            WebsiteFixture.Listed entry = listed.get(copy.getKey());
            claim(
                    "the copy " + copy.getKey() + " is listed by its place in the HTML tree, its size and the key stage"
                            + " 2 recorded",
                    () -> {
                        assertThat(rendering.resolve(entry.source()).normalize()).isEqualTo(tree.resolve(copy.getKey()));
                        assertThat(entry.size()).isEqualTo(copy.getValue().length);
                        assertThat(entry.sha256()).isEqualTo(ListedTree.sha256Of(copy.getValue()));
                    });
        }
        for (WebsiteFixture.Listed entry : listed.values()) {
            Path source = rendering.resolve(entry.source()).normalize();
            claim(
                    entry.path() + "'s source stands inside the tree, at the size and digest its entry names",
                    () -> {
                        assertThat(source.startsWith(tree)).isTrue();
                        assertThat(source).isRegularFile();
                        assertThat(Files.size(source)).isEqualTo(entry.size());
                        assertThat(sha256(source)).isEqualTo(entry.sha256());
                    });
        }
    }

    /**
     * ADR-286 §5's test, read in the website: every {@code src} and {@code href} of every page of a published website
     * lands on that page, on a file inside the website, or is a picture inlined on an image's source; a link with a
     * path and a fragment lands on a page whose ids hold it.
     */
    @Test
    @Story("The website is self-contained")
    @DisplayName("Every source and link of every page of the published website names no scheme or host and lands inside the website")
    void everySourceAndLinkLandsInsideTheWebsite(@TempDir Path base) throws IOException {
        Path tree = WebsiteFixture.drawn(base.resolve("drawn"));
        Path workingDirectory = Files.createDirectories(base.resolve("working directory"));
        Plugin.Delivery delivery = WebsiteFixture.published(tree, workingDirectory);
        Path website = workingDirectory.resolve(WebsiteFixture.WEBSITE);
        List<String> pages = pagesUnder(website);

        claim(
                "the publish delivered, and the website holds the rendering's pages",
                () -> {
                    assertThat(delivery.delivered()).as(delivery.closingLine()).isTrue();
                    assertThat(pages).containsExactly(
                            DOORS_FOLDER + "/index.html", DAMPERS_FOLDER + "/index.html", "index.html");
                });

        Set<String> kindsSeen = new TreeSet<>();
        for (String name : pages) {
            Path page = website.resolve(name);
            String text = textOf(page);
            List<Path> scripts = new ArrayList<>();
            List<Path> stylesheets = new ArrayList<>();
            Matcher tag = A_TAG.matcher(text);
            while (tag.find()) {
                String element = tag.group(1).toLowerCase();
                Matcher attribute = A_SOURCE_OR_LINK.matcher(tag.group(2));
                while (attribute.find()) {
                    String which = element + " " + attribute.group(1);
                    String value = attribute.group(2).replace("&amp;", "&");
                    if (value.startsWith("#")) {
                        kindsSeen.add("a fragment");
                        claim(which + "=\"" + value + "\" on " + name + " names an id on the same page",
                                () -> assertThat(text).contains("id=\"" + value.substring(1) + "\""));
                    } else if (value.startsWith("data:")) {
                        kindsSeen.add("an inlined picture");
                        Matcher inlined = AN_INLINED_PICTURE.matcher(value);
                        claim("the data source on " + name + " is an image's, a PNG or a JPEG, its payload padded",
                                () -> {
                                    assertThat(which).isEqualTo("img src");
                                    assertThat(inlined.matches()).isTrue();
                                });
                    } else {
                        kindsSeen.add("a file in the website");
                        URI destination = URI.create(value);
                        Path landed = page.getParent().resolve(destination.getPath()).normalize();
                        claim(
                                which + "=\"" + value + "\" on " + name + " names no scheme and no host, is not rooted,"
                                        + " and, followed from the page's folder, is a file inside the website",
                                () -> {
                                    assertThat(A_SCHEME.matcher(value).find()).isFalse();
                                    assertThat(value).doesNotStartWith("/");
                                    assertThat(landed.startsWith(website.normalize())).isTrue();
                                    assertThat(landed).isRegularFile();
                                });
                        if (destination.getRawFragment() != null) {
                            claim(which + "=\"" + value + "\" on " + name + " lands on a page holding its fragment's id",
                                    () -> assertThat(textOf(landed)).contains("id=\"" + destination.getFragment() + "\""));
                        }
                        if (which.equals("script src")) {
                            scripts.add(landed);
                        } else if (which.equals("link href")) {
                            stylesheets.add(landed);
                        }
                    }
                }
            }
            claim(
                    name + " loads the search's index and the script from the website's root, and its theme",
                    () -> {
                        assertThat(scripts).contains(website.resolve(SEARCH_INDEX), website.resolve("website.js"));
                        assertThat(stylesheets).contains(website.resolve("website.css"));
                    });
            claim(
                    name + " holds the search's query and the place what it finds is written",
                    () -> assertThat(text).contains("id=\"search\"", "id=\"search-results\""));
        }
        claim(
                "a file in the website, a fragment and an inlined picture were each met, so each kind was read",
                () -> assertThat(kindsSeen).contains("a file in the website", "a fragment", "an inlined picture"));

        String doors = textOf(website.resolve(DOORS_FOLDER + "/index.html"));
        claim(
                "the doors' page links each copy beside it, which the publish wrote byte for byte",
                () -> {
                    assertThat(website.resolve(DOORS_FOLDER + "/door survey.docx")).hasBinaryContent(DOOR_SURVEY_BYTES);
                    assertThat(website.resolve(DOORS_FOLDER + "/inspection-log.docx"))
                            .hasBinaryContent(INSPECTION_LOG_BYTES);
                    assertThat(linksOf(doors).stream()
                                    .map(value -> website.resolve(DOORS_FOLDER).resolve(URI.create(value).getPath())
                                            .normalize()))
                            .contains(website.resolve(DOORS_FOLDER + "/door survey.docx"),
                                    website.resolve(DOORS_FOLDER + "/inspection-log.docx"));
                });
        claim(
                "and the entry whose copy did not stand says why, as the HTML tree's page does",
                () -> assertThat(doors).contains(CopyOutcome.GONE.sentence(DeliverableLanguage.ENGLISH)));
        String dampers = textOf(website.resolve(DAMPERS_FOLDER + "/index.html"));
        claim(
                "the dampers' page shows its image document's copy inline, its bytes the copy's",
                () -> {
                    Matcher inlined = AN_INLINED_PICTURE.matcher(dampers);
                    assertThat(inlined.find()).isTrue();
                    byte[] decoded = Base64.getDecoder().decode(inlined.group(2));
                    assertThat(Arrays.copyOf(decoded, PNG_SIGNATURE.length)).isEqualTo(PNG_SIGNATURE);
                    assertThat(decoded).isEqualTo(DAMPER_SCAN_BYTES);
                });
    }

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

    /** Every {@code href} of {@code page} naming a file, its entities read back, fragments and pictures left out. */
    private static List<String> linksOf(String page) {
        List<String> links = new ArrayList<>();
        Matcher href = Pattern.compile("\\bhref=\"([^\"#][^\"]*)\"").matcher(page);
        while (href.find()) {
            links.add(href.group(1).replace("&amp;", "&"));
        }
        return links;
    }

    private static byte[] shipped(String name) throws IOException {
        try (InputStream in = WebsitePlugin.class.getResourceAsStream(SHIPPED + name)) {
            assertThat(in).as("the resource " + SHIPPED + name).isNotNull();
            return in.readAllBytes();
        }
    }
}
