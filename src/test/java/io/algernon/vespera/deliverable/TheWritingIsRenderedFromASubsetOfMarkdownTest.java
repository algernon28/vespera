package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The writing's Markdown, converted to HTML by Vespera's own renderer from a fixed subset, after the whole text is
 * escaped (ADR-274 §1.3).
 *
 * <p>Every claim compares the whole output with the whole of what §1.3 says it is, so a renderer that adds, drops or
 * reorders anything, a tag or an attribute of its input among them, fails it.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
class TheWritingIsRenderedFromASubsetOfMarkdownTest {

    /** The tags the storage emitter writes: the HTML renderer's but its link, and ADR-309's citation. */
    private static final Set<String> THE_STORAGE_TAGS = Set.of(
            "p", "br", "h3", "h4", "h5", "ul", "ol", "li", "strong", "em", "code", "ac:link", "ri:page",
            "ac:plain-text-link-body");

    /** A tag in storage format, its name possibly prefixed. */
    private static final Pattern A_STORAGE_TAG = Pattern.compile("<\\s*/?\\s*([A-Za-z][A-Za-z0-9:-]*)([^>]*)>");

    /** A citation as the HTML renderer writes it, its number as group 1. */
    private static final Pattern AN_HTML_CITATION = Pattern.compile("<a href=\"#document-(\\d+)\">\\[\\1\\]</a>");

    /** A citation as the storage emitter writes it, its number as group 1. */
    private static final Pattern A_STORAGE_CITATION = Pattern.compile(
            "<ac:link ac:anchor=\"document-(\\d+)\"><ri:page ri:content-title=\"[^\"]*\" /><ac:plain-text-link-body>"
                    + "<!\\[CDATA\\[\\[\\1\\]\\]\\]></ac:plain-text-link-body></ac:link>");

    private static final String PARAGRAPHS = "One.\nTwo.\n\nThree.\n   \nFour.";

    private static final String HEADINGS = "# One\n## Two\n### Three\n#### Four\n#No space";

    private static final String BULLETS = "- a\n- b\n* c";

    private static final String NUMBERED = "1. a\n2. b\n7. c";

    private static final String MIXED_LISTS = "Intro:\n- a\n  more of a\n  - a nested\n- b\n1. c";

    private static final String EMPHASIS = "**bold** and *one* and _two_ and **a *b* c**";

    private static final String CODE = "use `x **y** [1]` here";

    private static final String CITATIONS_NESTED = "**see [1]**\n\n*see [2]*\n\n- item [3]\n\n## On [4]";

    private static final String UNPAIRED = "**open and never closed [1]\na * b and c _ d\nsnake_case_name, 2*3*4 and an odd `tick\n**a\nb**";

    private static final String OUTSIDE_THE_SUBSET =
            "| a | b |\n|---|---|\n[x](http://example.com)\n![a](b.png)\n> quote\n---\n```\ncode\n```\n#### deep";

    private static final String HOSTILE = "<script>alert(1)</script>\n\"><img src=x onerror=alert(1)>\n"
            + "[x](javascript:alert(1))\n[1](javascript:alert(1))\n**<b onclick=\"x\">it's</b>**\n`</code><script>`";

    /** Every input of this class, for the claims that the storage emitter writes what the HTML renderer does. */
    private static final List<String> EVERY_INPUT = List.of(
            PARAGRAPHS, HEADINGS, BULLETS, NUMBERED, MIXED_LISTS, EMPHASIS, CODE, CITATIONS_NESTED, UNPAIRED,
            OUTSIDE_THE_SUBSET, HOSTILE);

    /** Each construct of the subset, its input and the whole of what §1.3 says it renders as. */
    static Stream<Arguments> theSubset() {
        return Stream.of(
                Arguments.of(
                        Named.of("Blocks become paragraphs, and every line break inside one is kept", PARAGRAPHS),
                        "two lines of one block are one paragraph with a break between them, and a blank line, even one"
                                + " of spaces, starts the next",
                        "<p>One.<br>Two.</p>\n<p>Three.</p>\n<p>Four.</p>"),
                Arguments.of(
                        Named.of("One to three hashes and a space make a heading beneath the page's own", HEADINGS),
                        "#, ## and ### become h3, h4 and h5, and four hashes or a hash with no space stay text",
                        "<h3>One</h3>\n<h4>Two</h4>\n<h5>Three</h5>\n<p>#### Four<br>#No space</p>"),
                Arguments.of(
                        Named.of("Lines opening with a dash or a star make one bulleted list", BULLETS),
                        "dash and star items are one bulleted list, in order",
                        "<ul>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n</ul>"),
                Arguments.of(
                        Named.of("Lines opening with a number make a numbered list", NUMBERED),
                        "numbered items are a numbered list counting from one, the written numbers not kept",
                        "<ol>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n</ol>"),
                Arguments.of(
                        Named.of("A list ends a paragraph, takes a continuing line and shows a nested item flat",
                                MIXED_LISTS),
                        "a paragraph ends where a list begins, a plain line continues the item above it, a nested item"
                                + " is shown flat in its place, and an item of the other kind starts a new list",
                        "<p>Intro:</p>\n<ul>\n<li>a<br>more of a</li>\n<li>a nested</li>\n<li>b</li>\n</ul>\n"
                                + "<ol>\n<li>c</li>\n</ol>"),
                Arguments.of(
                        Named.of("Double stars make bold, a single star or underscore italic, and the two nest",
                                EMPHASIS),
                        "**bold** is strong, *one* and _two_ are emphasis, and a star pair inside bold is emphasis"
                                + " inside it",
                        "<p><strong>bold</strong> and <em>one</em> and <em>two</em> and"
                                + " <strong>a <em>b</em> c</strong></p>"),
                Arguments.of(
                        Named.of("Backticks make code, inside which nothing is read as emphasis and a citation still"
                                + " links", CODE),
                        "the text between the backticks is code, its stars left as written and its citation a link",
                        "<p>use <code>x **y** <a href=\"#document-1\">[1]</a></code> here</p>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("theSubset")
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("Each construct of the subset is rendered as a reader expects it")
    void rendersEachConstructOfTheSubset(String input, String whatItRendersAs, String expected) {
        claim(whatItRendersAs, () -> assertThat(ProseHtml.of(input)).isEqualTo(expected));
    }

    @Test
    @Story("Every citation leads to its document")
    @DisplayName("A citation is a link wherever it stands: in bold, in italics, in a list item and in a heading")
    void linksACitationWhereverItStands() {
        claim(
                "each of the four citations is a link to the entry it names, inside the markup around it",
                () -> assertThat(ProseHtml.of(CITATIONS_NESTED))
                        .isEqualTo("<p><strong>see <a href=\"#document-1\">[1]</a></strong></p>\n"
                                + "<p><em>see <a href=\"#document-2\">[2]</a></em></p>\n"
                                + "<ul>\n<li>item <a href=\"#document-3\">[3]</a></li>\n</ul>\n"
                                + "<h4>On <a href=\"#document-4\">[4]</a></h4>"));
    }

    /** Each input nothing of which may be read as markup but what the subset names, and the whole of its output. */
    static Stream<Arguments> whatIsNotMarkup() {
        return Stream.of(
                Arguments.of(
                        Named.of("A delimiter with no partner on its line is left as it was written", UNPAIRED),
                        "an unterminated double star, a lone star and underscore, underscores and stars inside words, an"
                                + " odd backtick and a bold split over two lines all stay as written, the citation"
                                + " still a link",
                        "<p>**open and never closed <a href=\"#document-1\">[1]</a><br>a * b and c _ d<br>"
                                + "snake_case_name, 2*3*4 and an odd `tick<br>**a<br>b**</p>"),
                Arguments.of(
                        Named.of("A table, a link, an image, a quote, a rule, a fenced block and a deep heading are"
                                + " shown as text", OUTSIDE_THE_SUBSET),
                        "every one of them reads as its characters, escaped, in one paragraph",
                        "<p>| a | b |<br>|---|---|<br>[x](http://example.com)<br>![a](b.png)<br>&gt; quote<br>"
                                + "---<br>```<br>code<br>```<br>#### deep</p>"),
                Arguments.of(
                        Named.of("A script, a tag breaking out of an attribute and a script link come out as inert"
                                + " text", HOSTILE),
                        "each is escaped text: the script, the broken-out image, both script links, a tag inside bold"
                                + " and a tag inside code; only the citation is a link, and to its entry",
                        "<p>&lt;script&gt;alert(1)&lt;/script&gt;<br>"
                                + "&quot;&gt;&lt;img src=x onerror=alert(1)&gt;<br>"
                                + "[x](javascript:alert(1))<br>"
                                + "<a href=\"#document-1\">[1]</a>(javascript:alert(1))<br>"
                                + "<strong>&lt;b onclick=&quot;x&quot;&gt;it&#39;s&lt;/b&gt;</strong><br>"
                                + "<code>&lt;/code&gt;&lt;script&gt;</code></p>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatIsNotMarkup")
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("What the subset does not name is shown as the text it is, and nothing renders a tag of its input")
    void showsWhatIsNotMarkupAsText(String input, String whatItRendersAs, String expected) {
        claim(whatItRendersAs, () -> assertThat(ProseHtml.of(input)).isEqualTo(expected));
    }

    @Test
    @Story("Both renderings carry the same writing")
    @DisplayName("The storage emitter writes the same blocks as the HTML one, its line break self-closed and its citations anchor links")
    @Issue("620")
    @Link(name = "ADR-309", url = Adr.A_CITATION_ON_A_CONFLUENCE_PAGE_IS_AN_ANCHOR_LINK, type = "adr")
    void theStorageEmitterWritesTheSameBlocks() {
        List<String> differing = new ArrayList<>();
        for (String input : EVERY_INPUT) {
            String html = AN_HTML_CITATION.matcher(ProseHtml.of(input)).replaceAll("{$1}").replace("<br>", "<br/>");
            String storage = A_STORAGE_CITATION.matcher(ProseStorage.of(input, number -> "T")).replaceAll("{$1}");
            if (!html.equals(storage)) {
                differing.add(input);
            }
        }

        claim(
                "over all " + EVERY_INPUT.size() + " inputs, the two emitters' outputs are one text once each citation"
                        + " is read as its number and the HTML break is self-closed",
                () -> assertThat(differing).isEmpty());
        claim(
                "a citation in storage format is an ac:link to its anchor, naming its page's title, escaped, and its"
                        + " text the number in CDATA",
                () -> assertThat(ProseStorage.of("See [2].", number -> "Doors & frames"))
                        .isEqualTo("<p>See <ac:link ac:anchor=\"document-2\"><ri:page ri:content-title=\"Doors &amp;"
                                + " frames\" /><ac:plain-text-link-body><![CDATA[[2]]]></ac:plain-text-link-body>"
                                + "</ac:link>.</p>"));
    }

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("No input renders, in storage format, a tag the emitter does not write, and every output is well-formed XML")
    @Issue("620")
    void theStorageEmitterWritesNoTagOfItsInputAndWellFormedXml() throws Exception {
        List<String> foreign = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        for (String input : EVERY_INPUT) {
            String storage = ProseStorage.of(input, number -> "A <title> & \"more\"");
            Matcher tag = A_STORAGE_TAG.matcher(storage);
            while (tag.find()) {
                if (!THE_STORAGE_TAGS.contains(tag.group(1))) {
                    foreign.add(tag.group());
                }
            }
            try {
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                        ("<body xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + storage + "</body>")
                                .getBytes(StandardCharsets.UTF_8)));
            } catch (Exception refused) {
                malformed.add(input);
            }
        }

        claim(
                "over all " + EVERY_INPUT.size() + " inputs, every tag is one the storage emitter writes",
                () -> assertThat(foreign).isEmpty());
        claim(
                "and every output parses as XML once ac and ri are declared",
                () -> assertThat(malformed).isEmpty());
    }
}
