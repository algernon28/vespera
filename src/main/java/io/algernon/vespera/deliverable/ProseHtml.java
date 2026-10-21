package io.algernon.vespera.deliverable;

import java.util.function.IntFunction;

/**
 * The writing's blocks ({@link Prose}) written as HTML for the HTML tree (ADR-274 §1.3).
 *
 * <p>The only tags in the output are {@code p br h3 h4 h5 ul ol li strong em code a}, and the only attribute is a
 * citation's {@code href}, {@code "#document-n"} made of digits the text matched, or, where the entry is on another
 * page of its cluster, {@code "./index-k.html#document-n"} with the page's name from {@link ClusterPage} (ADR-303 §6).
 */
final class ProseHtml {

    /** The most digits an entry's number has, which an {@code int} holds. */
    static final int MOST_DIGITS_OF_AN_ENTRY = 9;

    private ProseHtml() {}

    /** {@code markdown} as HTML, one element to a line, or the empty text where it holds nothing. */
    static String of(String markdown) {
        return of(markdown, number -> "");
    }

    /**
     * {@code markdown} as HTML, each citation of entry {@code n} linking {@code #document-n} where {@code pageOf} answers
     * {@code ""} for it, the entry being on this page, and {@code ./<page>#document-n} where it answers that page's name
     * (ADR-303 §6). The escaping comes first, as ever.
     */
    static String of(String markdown, IntFunction<String> pageOf) {
        return Prose.emit(markdown, new Prose.Emitter() {
            @Override
            public String lineBreak() {
                return "<br>";
            }

            @Override
            public String citation(String digits) {
                return "<a href=\"" + pageOfCitation(digits, pageOf) + "#" + ClusterPage.ANCHOR_PREFIX + digits + "\">["
                        + digits + "]</a>";
            }
        });
    }

    /** The {@code ./page} a citation's link names before its anchor, or nothing where its entry is on this page. */
    private static String pageOfCitation(String digits, IntFunction<String> pageOf) {
        // A number too long for an int is no entry's, and links its anchor as it always did.
        String page = digits.length() > MOST_DIGITS_OF_AN_ENTRY ? "" : pageOf.apply(Integer.parseInt(digits));
        return page.isEmpty() ? "" : "./" + Html.escape(page);
    }
}
