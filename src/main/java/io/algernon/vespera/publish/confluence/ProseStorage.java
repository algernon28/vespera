package io.algernon.vespera.publish.confluence;

import io.algernon.vespera.engine.deliverable.ClusterPage;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.Prose;
import io.algernon.vespera.engine.deliverable.ProseHtml;
import java.util.function.IntFunction;

/**
 * The writing's blocks ({@link Prose}) written in Confluence's storage format for the Confluence rendering (#620).
 *
 * <p>The blocks are the HTML tree's, so both renderings carry the same paragraphs, lists, headings and citations. Two
 * things differ, each so the body is well-formed XML: a line break is {@code <br/>}, and a citation is ADR-309 §1's
 * {@code ac:link} naming the title of the page its entry is on, its anchor {@code document-n} and no space, its text
 * {@code [n]} in CDATA, which digits and brackets cannot close.
 */
final class ProseStorage {

    private ProseStorage() {}

    /**
     * A topic's overview in storage format (ADR-415 §7), each citation {@code [n]} an {@code ac:link} to the page titled
     * {@code titleOf(n)}, with no anchor, and the plain text {@code [n]} where {@code titleOf} answers {@code null}: the
     * number is no cluster's place among the topic's.
     */
    static String ofAnOverview(String markdown, IntFunction<String> titleOf) {
        return Prose.emit(markdown, new Prose.Emitter() {
            @Override
            public String lineBreak() {
                return "<br/>";
            }

            @Override
            public String citation(String digits) {
                String title = digits.length() > ProseHtml.MOST_DIGITS_OF_AN_ENTRY
                        ? null
                        : titleOf.apply(Integer.parseInt(digits));
                if (title == null) {
                    return "[" + digits + "]";
                }
                return "<ac:link><ri:page ri:content-title=\"" + Html.escape(title)
                        + "\" /><ac:plain-text-link-body><![CDATA[[" + digits
                        + "]]]></ac:plain-text-link-body></ac:link>";
            }
        });
    }

    /**
     * {@code markdown} in storage format, each citation of entry {@code n} naming the page titled {@code titleOf(n)}; a
     * number no entry has is given the title of the page the citation is on (ADR-309 §1).
     */
    static String of(String markdown, IntFunction<String> titleOf) {
        return Prose.emit(markdown, new Prose.Emitter() {
            @Override
            public String lineBreak() {
                return "<br/>";
            }

            @Override
            public String citation(String digits) {
                int number = digits.length() > ProseHtml.MOST_DIGITS_OF_AN_ENTRY ? 0 : Integer.parseInt(digits);
                return "<ac:link ac:anchor=\"" + ClusterPage.ANCHOR_PREFIX + digits + "\"><ri:page ri:content-title=\""
                        + Html.escape(titleOf.apply(number)) + "\" /><ac:plain-text-link-body><![CDATA[[" + digits
                        + "]]]></ac:plain-text-link-body></ac:link>";
            }
        });
    }
}
