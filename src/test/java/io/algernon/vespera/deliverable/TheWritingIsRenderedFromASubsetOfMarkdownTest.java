package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The writing's Markdown, converted to HTML by Vespera's own renderer from a fixed subset, after the whole text is
 * escaped (ADR-274 §1.3).
 *
 * <p>Every claim compares the whole output with the whole of what §1.3 says it is, so a renderer that adds, drops or
 * reorders anything fails it. The last test reads every output of this class for a tag or an attribute the renderer
 * does not write.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
class TheWritingIsRenderedFromASubsetOfMarkdownTest {

    /** The tags the renderer writes, and no other may appear in what it renders. */
    private static final Set<String> THE_RENDERERS_TAGS =
            Set.of("p", "br", "h3", "h4", "h5", "ul", "ol", "li", "strong", "em", "code", "a");

    /** A tag in rendered output: its name, then whatever stands before its closing bracket. */
    private static final Pattern A_TAG = Pattern.compile("<\\s*/?\\s*([A-Za-z][A-Za-z0-9]*)([^>]*)>");

    /** The one attribute the renderer writes: a citation's link to its entry. */
    private static final Pattern A_CITATIONS_HREF = Pattern.compile(" href=\"#document-\\d+\"");

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

    /** Every input of this class, for the claim that nothing renders a tag or an attribute the renderer does not write. */
    private static final List<String> EVERY_INPUT = List.of(
            PARAGRAPHS, HEADINGS, BULLETS, NUMBERED, MIXED_LISTS, EMPHASIS, CODE, CITATIONS_NESTED, UNPAIRED,
            OUTSIDE_THE_SUBSET, HOSTILE);

    @Test
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("Blocks become paragraphs, and every line break inside one is kept")
    void rendersParagraphsAndTheirLineBreaks() {
        claim(
                "two lines of one block are one paragraph with a break between them, and a blank line, even one of"
                        + " spaces, starts the next",
                () -> assertThat(ProseHtml.of(PARAGRAPHS))
                        .isEqualTo("<p>One.<br>Two.</p>\n<p>Three.</p>\n<p>Four.</p>"));
    }

    @Test
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("One to three hashes and a space make a heading beneath the page's own, and nothing else does")
    void rendersThreeLevelsOfHeading() {
        claim(
                "#, ## and ### become h3, h4 and h5, and four hashes or a hash with no space stay text",
                () -> assertThat(ProseHtml.of(HEADINGS))
                        .isEqualTo("<h3>One</h3>\n<h4>Two</h4>\n<h5>Three</h5>\n<p>#### Four<br>#No space</p>"));
    }

    @Test
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("Lines opening with a dash, a star or a number make lists")
    void rendersBulletedAndNumberedLists() {
        claim(
                "dash and star items are one bulleted list, in order",
                () -> assertThat(ProseHtml.of(BULLETS))
                        .isEqualTo("<ul>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n</ul>"));
        claim(
                "numbered items are a numbered list counting from one, the written numbers not kept",
                () -> assertThat(ProseHtml.of(NUMBERED))
                        .isEqualTo("<ol>\n<li>a</li>\n<li>b</li>\n<li>c</li>\n</ol>"));
        claim(
                "a paragraph ends where a list begins, a plain line continues the item above it, a nested item is"
                        + " shown flat in its place, and an item of the other kind starts a new list",
                () -> assertThat(ProseHtml.of(MIXED_LISTS))
                        .isEqualTo("<p>Intro:</p>\n<ul>\n<li>a<br>more of a</li>\n<li>a nested</li>\n<li>b</li>\n</ul>\n"
                                + "<ol>\n<li>c</li>\n</ol>"));
    }

    @Test
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("Double stars make bold, a single star or underscore italic, and the two nest")
    void rendersBoldAndItalic() {
        claim(
                "**bold** is strong, *one* and _two_ are emphasis, and a star pair inside bold is emphasis inside it",
                () -> assertThat(ProseHtml.of(EMPHASIS))
                        .isEqualTo("<p><strong>bold</strong> and <em>one</em> and <em>two</em> and"
                                + " <strong>a <em>b</em> c</strong></p>"));
    }

    @Test
    @Story("The writing's Markdown is read as a reader expects")
    @DisplayName("Backticks make code, inside which nothing is read as emphasis and a citation still links")
    void rendersInlineCode() {
        claim(
                "the text between the backticks is code, its stars left as written and its citation a link",
                () -> assertThat(ProseHtml.of(CODE))
                        .isEqualTo("<p>use <code>x **y** <a href=\"#document-1\">[1]</a></code> here</p>"));
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

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("A delimiter with no partner on its line is left as it was written")
    void leavesAnUnpairedDelimiterAsWritten() {
        claim(
                "an unterminated double star, a lone star and underscore, underscores and stars inside words, an odd"
                        + " backtick and a bold split over two lines all stay as written, the citation still a link",
                () -> assertThat(ProseHtml.of(UNPAIRED))
                        .isEqualTo("<p>**open and never closed <a href=\"#document-1\">[1]</a><br>a * b and c _ d<br>"
                                + "snake_case_name, 2*3*4 and an odd `tick<br>**a<br>b**</p>"));
    }

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("A table, a link, an image, a quote, a rule, a fenced block and a deep heading are shown as text")
    void showsWhatIsOutsideTheSubsetAsText() {
        claim(
                "every one of them reads as its characters, escaped, in one paragraph",
                () -> assertThat(ProseHtml.of(OUTSIDE_THE_SUBSET))
                        .isEqualTo("<p>| a | b |<br>|---|---|<br>[x](http://example.com)<br>![a](b.png)<br>&gt; quote<br>"
                                + "---<br>```<br>code<br>```<br>#### deep</p>"));
    }

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("A script, a tag breaking out of an attribute and a script link come out as inert text")
    void rendersHostileInputInert() {
        claim(
                "each is escaped text: the script, the broken-out image, both script links, a tag inside bold and a"
                        + " tag inside code; only the citation is a link, and to its entry",
                () -> assertThat(ProseHtml.of(HOSTILE))
                        .isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt;<br>"
                                + "&quot;&gt;&lt;img src=x onerror=alert(1)&gt;<br>"
                                + "[x](javascript:alert(1))<br>"
                                + "<a href=\"#document-1\">[1]</a>(javascript:alert(1))<br>"
                                + "<strong>&lt;b onclick=&quot;x&quot;&gt;it&#39;s&lt;/b&gt;</strong><br>"
                                + "<code>&lt;/code&gt;&lt;script&gt;</code></p>"));
    }

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("No input renders a tag or an attribute the renderer does not write")
    void writesNoTagOrAttributeOfItsInput() {
        List<String> foreign = new ArrayList<>();
        for (String input : EVERY_INPUT) {
            Matcher tag = A_TAG.matcher(ProseHtml.of(input));
            while (tag.find()) {
                String name = tag.group(1);
                String attributes = tag.group(2);
                boolean allowed = THE_RENDERERS_TAGS.contains(name)
                        && (attributes.isEmpty()
                                || ("a".equals(name) && A_CITATIONS_HREF.matcher(attributes).matches()));
                if (!allowed) {
                    foreign.add(tag.group());
                }
            }
        }

        claim(
                "over all " + EVERY_INPUT.size() + " inputs of this class, every tag is one of the renderer's "
                        + THE_RENDERERS_TAGS.size() + " and the only attribute is a citation's link to its entry",
                () -> assertThat(foreign).isEmpty());
    }
}
