package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every HTML page Vespera writes is drawn dark, whatever the system's setting and the control panel's theme (ADR-380):
 * for each of the four kinds of page, the stylesheet it carries or links names no rule of the reader's colour scheme,
 * has one {@code :root} declaring {@code color-scheme: dark} (§1) and every colour as a property of it, exactly the
 * ones §3 names for that page (§2), and each text colour is lighter than each background and stands at WCAG AA against
 * it, each mark of a chart at 3 : 1 (§4).
 *
 * <p>Each stylesheet is read from what the page is written through: an analysis report's page as {@code ReportPage}
 * renders it, a page of the HTML tree as {@code Html} opens and closes it, {@code report.html} as the jar's template
 * draws it, and the website rendering's stylesheet as a page of it links it from {@code WebsitePage}. The three
 * writers are package-private to their modules, so they are called by reflection, as the jar calls them.
 *
 * <p>What it cannot see: the pixels a browser draws, a text a rule places on a background §3 does not pair it with,
 * and whether a rule draws through a property at all where it declares no colour (ADR-380, Known limits).
 */
@Epic("Operation")
@Feature("The pages Vespera writes")
@Issue("794")
@Link(name = "ADR-380", url = Adr.EVERY_HTML_PAGE_VESPERA_WRITES_IS_DRAWN_DARK, type = "adr")
class EveryHtmlPageVesperaWritesIsDrawnDarkTest {

    /** WCAG AA's least contrast for text of a normal size. */
    private static final double AA = 4.5;

    /** WCAG 2.1's least contrast for a graphical object a reader needs, a line or a bar of a chart. */
    private static final double A_MARK = 3.0;

    /** The title every page here is drawn under; it plays no part in the stylesheet. */
    private static final String A_TITLE = "A title";

    private static final Pattern A_STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);

    private static final Pattern A_STYLESHEET_LINK = Pattern.compile("<link rel=\"stylesheet\" href=\"([^\"]+)\">");

    private static final Pattern A_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** A rule's selector and its declarations; a rule inside an {@code @media} is read as a rule, the media's brace being no part of it. */
    private static final Pattern A_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    private static final Pattern A_COLOUR_PROPERTY = Pattern.compile("^--([a-z0-9-]+)$");

    private static final Pattern SIX_HEX_DIGITS = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /** A colour written in place: a hexadecimal value, {@code rgb()} or {@code hsl()}. */
    private static final Pattern A_COLOUR_IN_PLACE = Pattern.compile("#[0-9a-fA-F]{3,8}\\b|\\brgba?\\(|\\bhsla?\\(");

    private static final Pattern A_PROPERTY_DRAWN = Pattern.compile("^var\\(--([a-z0-9-]+)\\)$");

    /**
     * One kind of page: what it is called in the report, how its stylesheet is read, and the properties ADR-380 §3
     * names for it, as text, background, mark and not measured.
     */
    private record Page(
            String name,
            Stylesheet stylesheet,
            List<String> text,
            List<String> backgrounds,
            List<String> marks,
            List<String> notMeasured) {

        @Override
        public String toString() {
            return name;
        }

        List<String> every() {
            List<String> every = new ArrayList<>(text);
            every.addAll(backgrounds);
            every.addAll(marks);
            every.addAll(notMeasured);
            return every;
        }
    }

    @FunctionalInterface
    private interface Stylesheet {
        String read() throws Exception;
    }

    static Stream<Arguments> pages() {
        List<String> reportsText = List.of("ink", "dim", "accent");
        List<String> reportsBackgrounds = List.of("ground", "surface");
        return Stream.of(
                Arguments.of(new Page("an analysis report's page", () -> styleOf(analysisReportPage()),
                        reportsText, reportsBackgrounds, List.of(), List.of("rule"))),
                Arguments.of(new Page("a page of the deliverable's HTML tree", () -> styleOf(treePage()),
                        reportsText, reportsBackgrounds, List.of(), List.of("rule"))),
                Arguments.of(new Page("the invocation's report page, report.html", () -> styleOf(reportPage()),
                        List.of("text", "muted"), List.of("page", "surface"),
                        List.of("series-1", "series-2", "series-3", "series-4", "series-5", "finished", "gate", "failed",
                                "running"),
                        List.of("grid", "axis"))),
                Arguments.of(new Page("a page of the website", EveryHtmlPageVesperaWritesIsDrawnDarkTest::websiteStylesheet,
                        List.of("text", "muted", "accent"), List.of("background", "panel"), List.of(), List.of("line"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pages")
    @Story("Every page Vespera writes is drawn dark")
    @DisplayName("The page's stylesheet is dark alone, every colour it draws is one of its root's, and each text and chart colour reads clearly against each background")
    void theStylesheetIsDarkAloneAndEveryColourStandsAtWcagAa(Page page) throws Exception {
        String css = A_COMMENT.matcher(page.stylesheet().read()).replaceAll("");
        List<Map.Entry<String, String>> rules = rulesOf(css);
        List<Map<String, String>> roots = rules.stream()
                .filter(rule -> rule.getKey().strip().equals(":root"))
                .map(rule -> declarationsOf(rule.getValue()))
                .toList();
        Map<String, String> root = roots.isEmpty() ? Map.of() : roots.getFirst();
        List<String> schemes = rules.stream()
                .map(rule -> declarationsOf(rule.getValue()).get("color-scheme"))
                .filter(scheme -> scheme != null)
                .toList();
        Map<String, String> colours = new LinkedHashMap<>();
        root.forEach((name, value) -> {
            Matcher property = A_COLOUR_PROPERTY.matcher(name);
            if (property.matches()) {
                colours.put(property.group(1), value);
            }
        });
        List<String> writtenInPlace = new ArrayList<>();
        List<String> colourNotOfText = new ArrayList<>();
        for (Map.Entry<String, String> rule : rules) {
            if (rule.getKey().strip().equals(":root")) {
                continue;
            }
            if (A_COLOUR_IN_PLACE.matcher(rule.getValue()).find()) {
                writtenInPlace.add(rule.getKey().strip());
            }
            String colour = declarationsOf(rule.getValue()).get("color");
            if (colour != null) {
                Matcher drawn = A_PROPERTY_DRAWN.matcher(colour);
                if (!drawn.matches() || !page.text().contains(drawn.group(1))) {
                    colourNotOfText.add(rule.getKey().strip() + " { color: " + colour + " }");
                }
            }
        }
        List<String> notSixHexDigits = colours.entrySet().stream()
                .filter(colour -> !SIX_HEX_DIGITS.matcher(colour.getValue()).matches())
                .map(colour -> "--" + colour.getKey() + ": " + colour.getValue())
                .toList();
        List<String> notLighter = new ArrayList<>();
        List<String> belowTheirFloor = new ArrayList<>();
        if (notSixHexDigits.isEmpty() && colours.keySet().containsAll(page.every())) {
            for (String background : page.backgrounds()) {
                for (String text : page.text()) {
                    if (luminance(colours.get(text)) <= luminance(colours.get(background))) {
                        notLighter.add("--" + text + " on --" + background);
                    }
                    below(colours, text, background, AA).ifPresent(belowTheirFloor::add);
                }
                for (String mark : page.marks()) {
                    below(colours, mark, background, A_MARK).ifPresent(belowTheirFloor::add);
                }
            }
        }

        claim("the stylesheet names no rule of the reader's colour scheme, neither a media query of it nor a pair of"
                        + " light and dark colours",
                () -> assertThat(css).doesNotContain("prefers-color-scheme").doesNotContain("light-dark("));
        claim("it has one root rule, and the only colour scheme it declares is dark, on that root",
                () -> {
                    assertThat(roots).as("the root rules").hasSize(1);
                    assertThat(schemes).as("every colour scheme declared").containsExactly("dark");
                    assertThat(root.get("color-scheme")).as("the root's colour scheme").isEqualTo("dark");
                });
        claim("every colour it draws is a property of its root, written as six hexadecimal digits, and the root's colour"
                        + " properties are exactly the page's text, backgrounds, chart marks and dividing lines",
                () -> {
                    assertThat(writtenInPlace).as("rules writing a colour of their own").isEmpty();
                    assertThat(notSixHexDigits).as("properties not written as six hexadecimal digits").isEmpty();
                    assertThat(colours.keySet()).as("the root's colour properties")
                            .containsExactlyInAnyOrderElementsOf(page.every());
                });
        claim("every text colour a rule declares is one of the page's text colours",
                () -> assertThat(colourNotOfText).as("text colours drawn otherwise").isEmpty());
        claim("each text colour is lighter than each background: light text on a dark ground",
                () -> assertThat(notLighter).as("text no lighter than its background").isEmpty());
        claim("each text colour reads at 4.5 : 1 or more against each background, and each line or bar of a chart at"
                        + " 3 : 1 or more",
                () -> assertThat(belowTheirFloor).as("pairs below their floor").isEmpty());
    }

    // ---- the pages, as they are written

    private static String analysisReportPage() throws ReflectiveOperationException {
        Method render = method("io.algernon.vespera.engine.pipeline.ReportPage", "render", String.class, String.class);
        return (String) render.invoke(null, A_TITLE, "");
    }

    private static String treePage() throws ReflectiveOperationException {
        Method head = method(
                "io.algernon.vespera.engine.deliverable.Html", "head", String.class, DeliverableLanguage.class);
        Method tail = method("io.algernon.vespera.engine.deliverable.Html", "tail");
        return (String) head.invoke(null, A_TITLE, DeliverableLanguage.ENGLISH) + tail.invoke(null);
    }

    /** {@code report.html} as the jar's own template draws it over a report that holds nothing yet. */
    private static String reportPage() throws ReflectiveOperationException {
        Class<?> reportHtml = Class.forName("io.algernon.vespera.engine.pipeline.ReportHtml");
        Constructor<?> jarsOwn = reportHtml.getDeclaredConstructor(String.class);
        jarsOwn.setAccessible(true);
        Method draw = reportHtml.getDeclaredMethod("draw", String.class);
        draw.setAccessible(true);
        return (String) draw.invoke(jarsOwn.newInstance(""), "{}");
    }

    /** The stylesheet a page of the website links, read from the jar beside the class that writes the page. */
    private static String websiteStylesheet() throws ReflectiveOperationException, IOException {
        Class<?> websitePage = Class.forName("io.algernon.vespera.publish.website.WebsitePage");
        Method opening = websitePage.getDeclaredMethod("opening", String.class, String.class, DeliverableLanguage.class);
        opening.setAccessible(true);
        String head = (String) opening.invoke(null, A_TITLE, "", DeliverableLanguage.ENGLISH);
        Matcher link = A_STYLESHEET_LINK.matcher(head);
        if (!link.find()) {
            throw new IllegalStateException("a page of the website links no stylesheet: " + head);
        }
        try (InputStream in = websitePage.getResourceAsStream(link.group(1))) {
            if (in == null) {
                throw new IllegalStateException("the jar holds no " + link.group(1) + " beside the website's pages");
            }
            return new String(in.readAllBytes(), UTF_8) + styleOf(head);
        }
    }

    private static Method method(String type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = Class.forName(type).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    // ---- reading a stylesheet

    /** Every inline stylesheet of {@code page}, joined. */
    private static String styleOf(String page) {
        return String.join("\n", stylesOf(page));
    }

    private static List<String> stylesOf(String page) {
        List<String> styles = new ArrayList<>();
        Matcher style = A_STYLE.matcher(page);
        while (style.find()) {
            styles.add(style.group(1));
        }
        return styles;
    }

    private static List<Map.Entry<String, String>> rulesOf(String css) {
        List<Map.Entry<String, String>> rules = new ArrayList<>();
        Matcher rule = A_RULE.matcher(css);
        while (rule.find()) {
            rules.add(Map.entry(rule.group(1), rule.group(2)));
        }
        return rules;
    }

    private static Map<String, String> declarationsOf(String block) {
        Map<String, String> declarations = new LinkedHashMap<>();
        for (String declaration : block.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                declarations.put(declaration.substring(0, colon).strip(), declaration.substring(colon + 1).strip());
            }
        }
        return declarations;
    }

    // ---- WCAG's contrast

    private static Optional<String> below(Map<String, String> colours, String one, String other, double floor) {
        double ratio = contrast(colours.get(one), colours.get(other));
        return ratio >= floor
                ? Optional.empty()
                : Optional.of("--%s on --%s at %.2f : 1".formatted(one, other, ratio));
    }

    /** WCAG 2's contrast ratio of two colours written {@code #rrggbb}. */
    private static double contrast(String one, String other) {
        double a = luminance(one);
        double b = luminance(other);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** WCAG 2's relative luminance of a colour written {@code #rrggbb}. */
    private static double luminance(String colour) {
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(colour.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            channels[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }
}
