package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's pages drawn to one compact scale (ADR-335 §5): the style names a scale of spaces, type sizes,
 * corners, a control height and a sidebar width on {@code :root}, the same in both schemes, and every margin, padding,
 * gap, type size and corner of every rule is a step of it.
 *
 * <p>No test renders a page (ADR-297): what is held is the style each page carries, read as text, rule by rule. How dense
 * a page then looks rests on these rules and on the browser.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-335", url = Adr.THE_CONTROL_PANEL_SHOWS_THE_PROGRESS_PAGE_AND_ITS_PAGES_ARE_DRAWN_TO_ONE_COMPACT_SCALE, type = "adr")
class TheCompactScaleScreenTest {

    /** The home page, the invocation page and the folder page, each of which carries the style (ADR-340 §2, §3). */
    private static final List<String> EVERY_PAGE = List.of("/", "/invocation", "/folders?for=vespera.corpus-root");

    /** The scale, by property, in the order the record gives it. */
    private static final Map<String, String> THE_SCALE = theScale();

    /** The families of property the scale is made of: no property of these is named but the scale's own. */
    private static final Pattern A_PROPERTY_OF_THE_SCALES_FAMILIES = Pattern.compile("--(space|text|radius)(-[a-z0-9]+)?");

    /** The dark scheme's block of colours. */
    private static final Pattern THE_DARK_BLOCK = Pattern.compile(
            "@media\\s*\\(\\s*prefers-color-scheme\\s*:\\s*dark\\s*\\)\\s*\\{\\s*:root\\s*\\{([^}]*)\\}\\s*\\}");

    /** A rule with no rule inside it: its selectors, then its declarations. */
    private static final Pattern A_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    /** The properties that set a space around or between boxes. */
    private static final Pattern A_SPACE = Pattern.compile(
            "(margin|padding)(-(top|right|bottom|left|inline|block)(-(start|end))?)?|gap|row-gap|column-gap");

    /** A step of the spaces, or what is no length. */
    private static final Pattern A_STEP_OF_SPACE = Pattern.compile("0|auto|var\\(--space-[1-6]\\)");

    /** A step of the type sizes. */
    private static final Pattern A_STEP_OF_TYPE = Pattern.compile("var\\(--text-(xs|sm|md|lg|xl)\\)");

    /** A length written out, in any unit a size of type could be given in. */
    private static final Pattern A_LENGTH_WRITTEN_OUT = Pattern.compile("(?<![\\w-])\\d*\\.?\\d+(px|rem|em|pt|%)");

    /** A step of the corners, or none. */
    private static final Pattern A_STEP_OF_CORNER = Pattern.compile("0|var\\(--radius(-sm|-lg|-pill)?\\)");

    @TempDir
    Path folder;

    /** The style of the launch page, which {@link #everyPageCarriesTheOneStyle} holds every page to. */
    private String style;

    /** Every rule of the style, a narrow window's and the dark scheme's among them, in order. */
    private List<Rule> rules;

    @BeforeEach
    void readTheStyle() throws IOException {
        try (ScreenServer screens = aServer()) {
            style = styleOf(screens.browser().page());
        }
        rules = rulesOf(style);
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Every page carries one style, the same text on each, so that every page is drawn to the same scale")
    void everyPageCarriesTheOneStyle() throws IOException {
        Map<String, Integer> styles = new LinkedHashMap<>();
        Map<String, Boolean> theSame = new LinkedHashMap<>();
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            for (String path : EVERY_PAGE) {
                Page page = browser.go(path).page();
                styles.put(path, page.document().select("style").size());
                theSame.put(path, styleOf(page).equals(style));
            }
        }

        claim("the home page, the invocation page and the folder page each carry one style",
                () -> assertThat(styles.values()).hasSize(EVERY_PAGE.size()).containsOnly(1));
        claim("and it is the home page's, word for word",
                () -> assertThat(theSame.values()).containsOnly(true));
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("The style names one scale of spaces, type sizes, corners, a control height and a sidebar width, the same in the light and the dark")
    void theStyleNamesOneScaleTheSameInBothSchemes() {
        Map<String, String> root = rootOutsideTheDark();
        Matcher dark = THE_DARK_BLOCK.matcher(style);
        String darkBlock = dark.find() ? dark.group(1) : "";
        Set<String> ofTheFamilies = new TreeSet<>();
        Matcher named = Pattern.compile("(" + A_PROPERTY_OF_THE_SCALES_FAMILIES.pattern() + ")\\s*:").matcher(style);
        while (named.find()) {
            ofTheFamilies.add(named.group(1));
        }
        Set<String> scaleNamedInTheDark = new TreeSet<>();
        for (String property : THE_SCALE.keySet()) {
            if (Pattern.compile(Pattern.quote(property) + "\\s*:").matcher(darkBlock).find()) {
                scaleNamedInTheDark.add(property);
            }
        }

        claim("the light scheme's root names each of the scale's seventeen steps, each at the value the record gives it",
                () -> assertThat(root).containsAllEntriesOf(THE_SCALE));
        claim("no other space, type size or corner is named, so that the scale is the only one",
                () -> assertThat(ofTheFamilies).isEqualTo(new TreeSet<>(THE_SCALE.keySet().stream()
                        .filter(property -> A_PROPERTY_OF_THE_SCALES_FAMILIES.matcher(property).matches()).toList())));
        claim("the dark scheme's block was found, so the next claim is about something",
                () -> assertThat(darkBlock).contains("--bg"));
        claim("and it names no step of the scale, which is the same in both schemes",
                () -> assertThat(scaleNamedInTheDark).isEmpty());
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Every margin, padding and gap of every rule is a step of the scale, a narrow window's rules among them")
    void everySpaceIsAStepOfTheScale() {
        List<String> spaces = new ArrayList<>();
        List<String> offTheScale = new ArrayList<>();
        for (Rule rule : rules) {
            rule.declarations().forEach(declaration -> {
                if (A_SPACE.matcher(declaration.property()).matches()) {
                    spaces.add(rule.selectors() + " " + declaration);
                    if (!Arrays.stream(declaration.value().split("\\s+"))
                            .allMatch(part -> A_STEP_OF_SPACE.matcher(part).matches())) {
                        offTheScale.add(rule.selectors() + " " + declaration);
                    }
                }
            });
        }

        claim("the style's spaces were read, so the next claim is about something",
                () -> assertThat(spaces).isNotEmpty());
        claim("and each is made of nothing but 0, auto and the six steps of space",
                () -> assertThat(offTheScale).isEmpty());
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Every type size of every rule is a step of the scale: the body at the middle one, the page's title at the largest and a card's heading the next")
    void everyTypeSizeIsAStepOfTheScale() {
        List<String> offTheScale = new ArrayList<>();
        for (Rule rule : rules) {
            rule.declarations().forEach(declaration -> {
                boolean off = switch (declaration.property()) {
                    case "font-size" -> !A_STEP_OF_TYPE.matcher(declaration.value()).matches();
                    case "font" -> !declaration.value().equals("inherit")
                            && (!A_STEP_OF_TYPE.matcher(declaration.value()).find()
                                    || A_LENGTH_WRITTEN_OUT.matcher(declaration.value()).find());
                    default -> false;
                };
                if (off) {
                    offTheScale.add(rule.selectors() + " " + declaration);
                }
            });
        }

        claim("no font size, and no font, of any rule is written out: each is a step of the type sizes, or inherited",
                () -> assertThat(offTheScale).isEmpty());
        claim("the body's type is the middle step, 13 pixels, its lines 1.4 times as high",
                () -> assertThat(lastOutsideAMediaBlock("body", "font")).startsWith("var(--text-md)/1.4 system-ui"));
        claim("a page's title is the largest step, and a card's heading the one below it",
                () -> assertThat(List.of(lastOutsideAMediaBlock("h1", "font-size"),
                                lastOutsideAMediaBlock("h2", "font-size")))
                        .containsExactly("var(--text-xl)", "var(--text-lg)"));
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Every corner of every rule is a radius of the scale")
    void everyCornerIsARadiusOfTheScale() {
        List<String> corners = new ArrayList<>();
        List<String> offTheScale = new ArrayList<>();
        for (Rule rule : rules) {
            rule.declarations().forEach(declaration -> {
                if (declaration.property().matches("border(-[a-z]+)*-radius")) {
                    corners.add(rule.selectors() + " " + declaration);
                    if (!Arrays.stream(declaration.value().split("\\s+"))
                            .allMatch(part -> A_STEP_OF_CORNER.matcher(part).matches())) {
                        offTheScale.add(rule.selectors() + " " + declaration);
                    }
                }
            });
        }

        claim("the style's corners were read, so the next claim is about something",
                () -> assertThat(corners).isNotEmpty());
        claim("and each is one of the four radii of the scale",
                () -> assertThat(offTheScale).isEmpty());
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("A text field, a number field, a choice and a button are drawn by one rule, one height and one shape, so that they line up side by side")
    void everyFieldChoiceAndButtonIsOneHeightAndShape() {
        List<String> theFour = List.of("input[type=text]", "input[type=number]", "select", "button");
        List<Rule> naming = rules.stream().filter(rule -> !rule.insideAMediaBlock())
                .filter(rule -> rule.selectorList().containsAll(theFour)).toList();

        claim("one rule outside a narrow window's names all four controls",
                () -> assertThat(naming).hasSize(1));
        claim("and sets them the control height, the middle radius and the page's own type",
                () -> assertThat(List.of(naming.getFirst().value("height"), naming.getFirst().value("border-radius"),
                                naming.getFirst().value("font")))
                        .containsExactly("var(--control-height)", "var(--radius)", "inherit"));
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Cards are padded one step of the scale, and the tiles stand a step closer")
    void cardsAndTilesStandOneStepApart() {
        claim("a card's padding and the gap of the tiles are the steps the record gives",
                () -> assertThat(List.of(lastOutsideAMediaBlock(".card", "padding"),
                                lastOutsideAMediaBlock("#tiles", "gap")))
                        .containsExactly("var(--space-4)", "var(--space-3)"));
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("The browser's own parts of a page, its scroll bars, lists and arrows, follow the light or dark scheme and the accent colour")
    void theBrowsersOwnPartsFollowTheSchemeAndTheAccent() {
        Map<String, String> root = rootOutsideTheDark();

        claim("the root says the page comes in both schemes, and gives the browser's own parts the accent colour",
                () -> assertThat(List.of(String.valueOf(root.get("color-scheme")), String.valueOf(root.get("accent-color"))))
                        .containsExactly("light dark", "var(--accent)"));
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("The progress page's frame is as wide as the page's column and four fifths of the window high")
    void theProgressFrameIsAsWideAsTheColumnAndMostOfTheWindowHigh() {
        claim("the frame's rule sets its width to the whole column and its height to 80 of each 100 of the window's",
                () -> assertThat(List.of(lastOutsideAMediaBlock("#progress-frame", "width"),
                                lastOutsideAMediaBlock("#progress-frame", "height")))
                        .containsExactly("100%", "80vh"));
    }

    private ScreenServer aServer() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }

    private static Map<String, String> theScale() {
        Map<String, String> scale = new LinkedHashMap<>();
        scale.put("--space-1", "2px");
        scale.put("--space-2", "4px");
        scale.put("--space-3", "8px");
        scale.put("--space-4", "12px");
        scale.put("--space-5", "16px");
        scale.put("--space-6", "24px");
        scale.put("--text-xs", "11px");
        scale.put("--text-sm", "12px");
        scale.put("--text-md", "13px");
        scale.put("--text-lg", "15px");
        scale.put("--text-xl", "18px");
        scale.put("--radius-sm", "4px");
        scale.put("--radius", "6px");
        scale.put("--radius-lg", "8px");
        scale.put("--radius-pill", "999px");
        scale.put("--control-height", "28px");
        scale.put("--sidebar-width", "176px");
        return scale;
    }

    private static String styleOf(Page page) {
        return String.join("\n", page.document().select("style").stream().map(Element::data).toList());
    }

    /** Every declaration of every {@code :root} rule outside the dark scheme's block, the last of a property winning. */
    private Map<String, String> rootOutsideTheDark() {
        Map<String, String> root = new LinkedHashMap<>();
        rules.stream().filter(rule -> !rule.insideTheDark() && rule.selectorList().contains(":root"))
                .forEach(rule -> rule.declarations().forEach(each -> root.put(each.property(), each.value())));
        return root;
    }

    /**
     * The value the last rule outside every {@code @media} block that names {@code selector} among its selectors gives
     * {@code property}, or an empty text where none gives it one.
     */
    private String lastOutsideAMediaBlock(String selector, String property) {
        String value = "";
        for (Rule rule : rules) {
            if (!rule.insideAMediaBlock() && rule.selectorList().contains(selector) && !rule.value(property).isEmpty()) {
                value = rule.value(property);
            }
        }
        return value;
    }

    /**
     * Every rule of {@code style} with no rule inside it, in order, each knowing whether a {@code @media} block holds
     * it: the style nests nothing but a rule inside a {@code @media}, so a rule opened while a brace is still open is
     * inside one.
     */
    private static List<Rule> rulesOf(String style) {
        List<Rule> found = new ArrayList<>();
        List<int[]> darkBlocks = THE_DARK_BLOCK.matcher(style).results()
                .map(dark -> new int[] {dark.start(), dark.end()}).toList();
        Matcher rule = A_RULE.matcher(style);
        while (rule.find()) {
            int start = rule.start();
            int end = rule.end();
            boolean inMedia = depthAt(style, start) > 0;
            boolean inTheDark = darkBlocks.stream().anyMatch(dark -> dark[0] <= start && end <= dark[1]);
            List<Declaration> declarations = new ArrayList<>();
            for (String part : rule.group(2).split(";")) {
                int colon = part.indexOf(':');
                if (colon > 0) {
                    declarations.add(new Declaration(part.substring(0, colon).strip(),
                            part.substring(colon + 1).strip().replaceAll("\\s+", " ")));
                }
            }
            found.add(new Rule(rule.group(1).strip(), inMedia, inTheDark, declarations));
        }
        return found;
    }

    /** How many braces of {@code text} are open at {@code index}. */
    private static int depthAt(String text, int index) {
        int depth = 0;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '{') {
                depth++;
            } else if (text.charAt(i) == '}') {
                depth--;
            }
        }
        return depth;
    }

    /** One declaration of a rule. */
    private record Declaration(String property, String value) {

        @Override
        public String toString() {
            return property + ": " + value;
        }
    }

    /** One rule: its selectors as written, whether a {@code @media} block holds it, and its declarations. */
    private record Rule(String selectors, boolean insideAMediaBlock, boolean insideTheDark,
            List<Declaration> declarations) {

        List<String> selectorList() {
            return Arrays.stream(selectors.split(",")).map(String::strip).toList();
        }

        /** The value the rule's last declaration of {@code property} gives, or an empty text. */
        String value(String property) {
            String value = "";
            for (Declaration declaration : declarations) {
                if (declaration.property().equals(property)) {
                    value = declaration.value();
                }
            }
            return value;
        }
    }
}
