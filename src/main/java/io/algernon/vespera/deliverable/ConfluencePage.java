package io.algernon.vespera.deliverable;

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
import java.util.function.IntFunction;

/**
 * One page of a cluster in the Confluence rendering, as a storage-format body (#620): the writing rendered by {@link
 * ProseStorage}, the disclosure, and the cluster's membership, the same entries in the same order as the HTML page of
 * the same number (ADR-303), so both renderings carry the same entries and citations (ADR-301 §4).
 *
 * <p>The entries are {@link ConfluenceEntries}', written once as the HTML page written whole wrote its own, and each
 * page copies its run of them.
 *
 * <p>The page's title is Confluence's to show, so the body opens with no heading of its own; the title is given in the
 * page list (ADR-316).
 */
final class ConfluencePage {

    private ConfluencePage() {}

    /**
     * Writes page {@code page} of a cluster to {@code out}, which it flushes and does not close, its entries copied from
     * {@code entries}, and answers the files the page carries: each of its entries' copy that stands and pictures' files,
     * in entry order.
     *
     * @param entryCount how many entries the cluster's membership has
     * @param breaks where the HTML pages split the cluster, or {@code null} for one page
     * @param titleOf the title of page {@code k} of the cluster, counted from 1
     */
    static List<PageFile> write(
            OutputStream out,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            int entryCount,
            ConfluenceEntries entries,
            ClusterPage.PageBreaks breaks,
            int page,
            IntFunction<String> titleOf,
            boolean writingFinished)
            throws IOException {
        OutputStream stream = new BufferedOutputStream(out, 1 << 16);
        int pages = breaks == null ? 1 : breaks.pages();
        int first = breaks == null ? 0 : breaks.firstEntryOf(page);
        int after = breaks == null ? entryCount : breaks.firstEntryOf(page + 1);
        StringBuilder text = new StringBuilder();
        if (pages > 1) {
            text.append(navigation(page, pages, titleOf));
        }
        if (page == 1) {
            text.append(firstPageBody(recorded, doc, why, writingFinished, number -> {
                int holding = breaks == null || number < 1 || number > entryCount ? page : pageHolding(breaks, number);
                return titleOf.apply(holding);
            }));
        }
        List<PageFile> files = new ArrayList<>();
        if (entryCount > 0) {
            text.append("<h2>").append(ClusterPage.MEMBERSHIP_HEADING).append("</h2>\n")
                    .append(first == 0 ? "<ol>\n" : "<ol start=\"" + (first + 1) + "\">\n");
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
            text.append(navigation(page, pages, titleOf));
        }
        write(stream, text);
        stream.flush();
        return List.copyOf(files);
    }

    /** The page of {@code breaks}, counted from 1, that holds entry {@code number}, counted from 1. */
    private static int pageHolding(ClusterPage.PageBreaks breaks, int number) {
        int page = 1;
        while (page < breaks.pages() && breaks.firstEntryOf(page + 1) < number) {
            page++;
        }
        return page;
    }

    /** The writing and its disclosure, or the sentence saying why nothing was written (ADR-174), as the HTML page has them. */
    private static String firstPageBody(
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            boolean writingFinished,
            IntFunction<String> titleOf) {
        StringBuilder body = new StringBuilder();
        if (doc == null) {
            body.append("<p><em>")
                    .append(Html.escape(why == null ? Deliverable.NOTHING_WAS_WRITTEN_OVER_IT : UnwrittenSentences.of(why)))
                    .append("</em></p>\n");
            UnwrittenSentences.standing(why, writingFinished)
                    .ifPresent(standing -> body.append("<p><em>").append(Html.escape(standing)).append("</em></p>\n"));
            return body.toString();
        }
        String writing = ProseStorage.of(doc.prose(), titleOf);
        if (!writing.isEmpty()) {
            body.append(writing).append('\n');
        }
        int documentCount = recorded.cluster().documentCount();
        if (doc.documentsSent() < documentCount) {
            body.append("<p>")
                    .append(Html.escape(ClusterPage.SUBSET_DISCLOSURE.formatted(doc.documentsSent(), documentCount)))
                    .append("</p>\n");
        }
        return body.toString();
    }

    /** The line that names page {@code page} of {@code pages} and links the others by their titles (ADR-303 §5). */
    private static String navigation(int page, int pages, IntFunction<String> titleOf) {
        List<String> links = new ArrayList<>();
        if (page > 1) {
            links.add(ConfluencePages.link(titleOf.apply(1), "first"));
            links.add(ConfluencePages.link(titleOf.apply(page - 1), "previous"));
        }
        if (page < pages) {
            links.add(ConfluencePages.link(titleOf.apply(page + 1), "next"));
            links.add(ConfluencePages.link(titleOf.apply(pages), "last"));
        }
        return "<p>Page " + page + " of " + pages + ": " + String.join(", ", links) + "</p>\n";
    }

    private static void write(OutputStream stream, StringBuilder text) throws IOException {
        stream.write(text.toString().getBytes(StandardCharsets.UTF_8));
        text.setLength(0);
    }
}
