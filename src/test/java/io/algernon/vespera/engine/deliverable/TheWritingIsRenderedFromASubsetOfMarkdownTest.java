package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The writing's Markdown, converted to HTML by Vespera's own renderer from a fixed subset, after the whole text is
 * escaped (ADR-274 §1.3).
 *
 * <p>Every claim compares the whole output with the whole of what §1.3 says it is, so a renderer that adds, drops or
 * reorders anything, a tag or an attribute of its input among them, fails it. The Confluence rendering's emitter moved
 * into {@code confluence} with the rendering (ADR-333 §1), and {@code confluence.TheStorageEmitterWritesTheSameBlocksTest}
 * holds it to this class's inputs.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
public class TheWritingIsRenderedFromASubsetOfMarkdownTest {

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

    /**
     * Every input of this class, for the claims of {@code confluence.TheStorageEmitterWritesTheSameBlocksTest} that the
     * storage emitter writes what the HTML renderer does, which moved into {@code confluence} with the emitter
     * (ADR-333 §1).
     */
    public static final List<String> EVERY_INPUT = List.of(
            PARAGRAPHS, HEADINGS, BULLETS, NUMBERED, MIXED_LISTS, EMPHASIS, CODE, CITATIONS_NESTED, UNPAIRED,
            OUTSIDE_THE_SUBSET, HOSTILE);

    /** Each input nothing of which may be read as markup but what the subset names, and the whole of its output. */
    static Stream<Arguments> whatIsNotMarkup() {
        return Stream.of(
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
}
