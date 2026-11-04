package io.algernon.vespera.website;

import io.algernon.vespera.deliverable.ClusterPage;
import io.algernon.vespera.deliverable.FixedWords;
import io.algernon.vespera.deliverable.Html;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * One page of the website rendering: the HTML tree's cluster page, split where it is split (ADR-303) and drawn with the
 * website's head, its navigation and its search (ADR-333 §3). What stands between the heading and the list is {@link
 * ClusterPage}'s, the writing through {@code ProseHtml}, the disclosure or the sentence saying why there is none, and
 * the page-of line, so that both renderings say what they say once; the entries are {@link WebsiteEntries}', copied
 * from the file they were written to.
 *
 * <p>A page of the website is the HTML page and the head, which is a few hundred bytes longer: it follows the HTML
 * tree's ceiling in its entries and not to the byte (ADR-303).
 */
final class WebsitePage {

    private WebsitePage() {}

    /**
     * Everything of a page before its content: the head, with the theme and the two scripts at the website's root,
     * {@code up} being the way from the page up to it, and the header, with the link back to the contents and the
     * search's two elements, which {@code website.js} binds to (ADR-333 §3, §9).
     */
    static String opening(String title, String up, DeliverableLanguage language) {
        String search = Html.escape(FixedWords.search(language));
        return "<!DOCTYPE html>\n<html lang=\"" + language.code() + "\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>" + Html.escape(title)
                + "</title>\n<link rel=\"stylesheet\" href=\"" + up + "website.css\">\n<script src=\"" + up
                + "search-index.js\" defer></script>\n<script src=\"" + up + "website.js\" defer></script>\n</head>\n"
                + "<body>\n<header class=\"site-header\">\n<nav>\n<a href=\"" + up + "index.html\">"
                + Html.escape(FixedWords.contents(language)) + "</a>\n<input type=\"search\" id=\"search\" placeholder=\""
                + search + "\" aria-label=\"" + search + "\" data-root=\"" + up + "\" data-none=\""
                + Html.escape(FixedWords.searchFoundNothing(language))
                + "\" autocomplete=\"off\">\n</nav>\n<ol id=\"search-results\"></ol>\n</header>\n<main>\n";
    }

    /** Everything of a page after its content. */
    static String closing() {
        return "</main>\n</body>\n</html>\n";
    }

    /**
     * Writes page {@code page} of a cluster to {@code out}, which it flushes and does not close, its entries copied from
     * {@code entries}, and answers the files the page carries: each of its entries' copy that stands, in entry order.
     *
     * @param entryCount how many entries the cluster's membership has
     * @param breaks where the HTML pages split the cluster, or {@code null} for one page
     */
    static List<ListedFile> write(
            OutputStream out,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            int entryCount,
            WebsiteEntries entries,
            ClusterPage.PageBreaks breaks,
            int page,
            boolean writingFinished,
            String up,
            DeliverableLanguage language)
            throws IOException {
        OutputStream stream = new BufferedOutputStream(out, 1 << 16);
        int pages = breaks == null ? 1 : breaks.pages();
        int first = breaks == null ? 0 : breaks.firstEntryOf(page);
        int after = breaks == null ? entryCount : breaks.firstEntryOf(page + 1);
        String title = doc == null ? recorded.label().value() : doc.title();
        StringBuilder text = new StringBuilder(opening(title, up, language))
                .append("<h1>").append(Html.escape(title)).append("</h1>\n");
        if (pages > 1) {
            text.append(ClusterPage.navigation(page, pages, language));
        }
        if (page == 1) {
            text.append(ClusterPage.firstPageBody(
                    recorded, doc, why, writingFinished,
                    breaks == null ? number -> "" : breaks::nameOfPageHolding, language));
        }
        List<ListedFile> files = new ArrayList<>();
        if (entryCount > 0) {
            text.append(ClusterPage.listOpening(first, language));
            write(stream, text);
            try (FileChannel channel = FileChannel.open(entries.file(), StandardOpenOption.READ)) {
                WritableByteChannel sink = Channels.newChannel(stream);
                long to = entries.endOf(after - 1);
                for (long at = entries.startOf(first); at < to; ) {
                    at += channel.transferTo(at, to - at, sink);
                }
            }
            for (int at = first; at < after; at++) {
                files.addAll(entries.filesOf(at));
            }
            text.append("</ol>\n");
        }
        if (pages > 1) {
            text.append(ClusterPage.navigation(page, pages, language));
        }
        text.append(closing());
        write(stream, text);
        stream.flush();
        return List.copyOf(files);
    }

    private static void write(OutputStream stream, StringBuilder text) throws IOException {
        stream.write(text.toString().getBytes(StandardCharsets.UTF_8));
        text.setLength(0);
    }
}
