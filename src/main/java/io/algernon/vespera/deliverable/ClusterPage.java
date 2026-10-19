package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

/**
 * One cluster's page (ADR-103, ADR-104, ADR-109, ADR-133, ADR-174, ADR-274 §1.4, ADR-286, #187): the heading, the
 * writing rendered by {@link ProseHtml} with its citations as links, the disclosure where the writing rests on part of
 * the cluster, and then the cluster's complete membership.
 *
 * <p><b>The page is UTF-8 handed to a stream an entry at a time and built nowhere whole</b> (ADR-286 §2): after each
 * entry's {@code </li>} everything written is handed on and the caller is told how many bytes that is.
 *
 * <p>The membership list is numbered from the documents the call was recorded as carrying (ADR-133), so entry
 * {@code n} is the {@code n}th document the model was given and each {@code [n]} of the writing links to it.
 *
 * <p><b>A page past the profile's ceiling is written as several</b> (ADR-303): {@link #write} writes the page whole,
 * which is also the measurement the split is planned from ({@link #plan}), and {@link #writePage} writes one page of the
 * plan, the first with the writing and the others with the list alone. An entry is written the same on whichever page
 * it lands.
 */
final class ClusterPage {

    /** What the writing's block shows (ADR-275 section 2): fixed words, none of the cluster's. */
    private static final String WRITING_HOVER = "What the local model wrote over this group from the documents listed below it;"
            + " each number in brackets is a link to the document it cites.";

    /**
     * The heading a page opens its membership list with — rendered prose, so it says <em>group</em> (ADR-122).
     */
    private static final String MEMBERSHIP_HEADING = "The documents in this group";

    /**
     * How the page says the writing rests on part of its cluster (ADR-108, ADR-133): the count the
     * call sent and the count the cluster holds. Written only where those differ, so a page over the
     * whole cluster carries no claim it cannot support.
     *
     * <p><b>The first, not the highest-scoring</b> (ADR-133). A document the call could not carry is
     * dropped wherever it scored, so what was sent is in general not the top anything. What is true
     * either way, and what a reader can check against the list on this very page, is that the writing
     * was made from the entries this page numbers first.
     */
    private static final String SUBSET_DISCLOSURE = "Written from the first %d of the %d documents in this group.";

    /**
     * What a citation link points at, and what each membership entry carries as its own id: the page's
     * inside-the-page naming for membership entry {@code n} (ADR-109).
     */
    static final String ANCHOR_PREFIX = "document-";

    /** What ends a list, whether the page is whole or one of several. */
    private static final String LIST_CLOSING = "</ol>\n";

    /** The bytes the page's writer holds before it hands them to the stream; an entry's end hands on all of them. */
    private static final int BUFFER_BYTES = 1 << 16;

    private ClusterPage() {}

    /**
     * Writes the page to {@code out}, which it flushes and does not close, {@code folder} being the cluster's folder
     * its copies stand in, and tells {@code entryEnds} after each entry how many bytes it has handed to {@code out}
     * (ADR-286 §2, §7). The heading is the generated title where there is one, and the derived label
     * where there is not (ADR-106), so a cluster nothing was written over is still headed by something a
     * reader can match against the index.
     *
     * <p><b>The membership is complete, not the cited subset</b> (ADR-104), and every entry links to its copy inside
     * the tree where one stands, or names its path and says why none does and links nowhere (ADR-274 §2.6). No entry
     * links into the archive.
     *
     * <p><b>The disclosure counts what the arrangement recorded</b>, not what this writer was handed
     * (ADR-112): 6a states a cluster's size once, and the index cell and this sentence read that one
     * number.
     *
     * <p><b>The page is written either way</b>: a cluster keeps its slot so the order the operator approved and the
     * order on disk stay one order (ADR-112). Where no writing exists the sentence saying why stands under the
     * heading, or the plain line when no reason was given (ADR-174).
     *
     * <p>Tells {@code progress} of each membership entry that carries a document, and of nothing else:
     * the cluster page itself is reported by the caller.
     *
     * @param doc the writing, or {@code null} where nothing was written over the cluster
     * @param why why nothing was written, or {@code null} where no reason was handed over
     */
    static void write(
            OutputStream out,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            List<ListedSurvivor> members,
            Path folder,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress,
            LongConsumer entryEnds)
            throws IOException {
        emit(out, recorded, doc, why, numbered(doc, members), null, 1, folder, copies, pictures, progress, entryEnds);
    }

    /**
     * Writes page {@code page} of {@code breaks}, which flushes {@code out} and does not close it (ADR-303 §5): the
     * title, the navigation, on the first page the writing or the sentence saying why there is none, the list headed as
     * ever and numbered on from the page before, the navigation again. Tells nobody of an entry: the page written whole
     * has told each once (ADR-303 §3).
     */
    static void writePage(
            OutputStream out,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            List<Optional<ListedSurvivor>> numbered,
            PageBreaks breaks,
            int page,
            Path folder,
            Copies copies,
            EntryPictures pictures)
            throws IOException {
        emit(out, recorded, doc, why, numbered, breaks, page, folder, copies, pictures, DeliverableProgress.NONE,
                bytesHandedOn -> {});
    }

    /**
     * The page whole where {@code breaks} is {@code null}, else page {@code page} of it. The two differ only in what
     * stands around the list (ADR-303 §5): a whole page is the title, the writing, the list and the tail.
     */
    private static void emit(
            OutputStream out,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            List<Optional<ListedSurvivor>> numbered,
            PageBreaks breaks,
            int page,
            Path folder,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress,
            LongConsumer entryEnds)
            throws IOException {
        HandedOn handedOn = new HandedOn(out);
        OutputStream stream = new BufferedOutputStream(handedOn, BUFFER_BYTES);
        StringBuilder text = new StringBuilder(opening(recorded, doc));
        if (breaks != null) {
            text.append(navigation(page, breaks.pages()));
        }
        if (breaks == null || page == 1) {
            text.append(firstPageBody(recorded, doc, why, breaks == null ? number -> "" : breaks::nameOfPageHolding));
        }
        int first = breaks == null ? 0 : breaks.firstEntryOf(page);
        int after = breaks == null ? numbered.size() : breaks.firstEntryOf(page + 1);
        if (!numbered.isEmpty()) {
            text.append(listOpening(first));
            stream.write(text.toString().getBytes(StandardCharsets.UTF_8));
            text.setLength(0);
            writeMembership(stream, handedOn, numbered, first, after, folder, copies, pictures, progress, entryEnds);
            text.append(LIST_CLOSING);
        }
        if (breaks != null) {
            text.append(navigation(page, breaks.pages()));
        }
        text.append(Html.tail());
        stream.write(text.toString().getBytes(StandardCharsets.UTF_8));
        stream.flush();
    }

    /** The head and the {@code <h1>}, the title being the generated one or the derived label where nothing was written. */
    private static String opening(RecordedCluster recorded, SynthesisDoc doc) {
        String title = doc == null ? recorded.label().value() : doc.title();
        return Html.head(title) + "<h1>" + Html.escape(title) + "</h1>\n";
    }

    /**
     * What stands on a page whole, or on the first of several, between the {@code <h1>} and the list's heading: the
     * writing and, where it rests on part of the cluster, the disclosure, or the sentence saying why nothing was
     * written (ADR-174). {@code pageOf} is {@link ProseHtml#of}'s.
     */
    private static String firstPageBody(
            RecordedCluster recorded, SynthesisDoc doc, Unwritten why, IntFunction<String> pageOf) {
        StringBuilder body = new StringBuilder();
        if (doc == null) {
            body.append("<p><em>")
                    .append(Html.escape(why == null ? Deliverable.NOTHING_WAS_WRITTEN_OVER_IT : UnwrittenSentences.of(why)))
                    .append("</em></p>\n");
        } else {
            body.append("<div class=\"writing\" title=\"").append(Html.escape(WRITING_HOVER)).append("\">\n")
                    .append(ProseHtml.of(doc.prose(), pageOf)).append("\n</div>\n");
            int documentCount = recorded.cluster().documentCount();
            if (doc.documentsSent() < documentCount) {
                body.append("<p>")
                        .append(Html.escape(SUBSET_DISCLOSURE.formatted(doc.documentsSent(), documentCount)))
                        .append("</p>\n");
            }
        }
        return body.toString();
    }

    /** The heading and opening of the list, numbered on from the entry at {@code first}, counted from 0 (ADR-303 §5). */
    private static String listOpening(int first) {
        return "<h2>" + MEMBERSHIP_HEADING + "</h2>\n" + (first == 0 ? "<ol>\n" : "<ol start=\"" + (first + 1) + "\">\n");
    }

    /** The name of page {@code page}, counted from 1: {@code index.html} and then {@code index-<page>.html} (ADR-303 §4). */
    static String nameOfPage(int page) {
        return page == 1 ? Deliverable.CLUSTER_PAGE_NAME : Deliverable.FURTHER_PAGE_STEM + page + Deliverable.PAGE_EXTENSION;
    }

    /**
     * The line that names page {@code page} of {@code pages} and links the others (ADR-303 §5): the first, the
     * previous, the next and the last, each left out where it would point at the page itself or at no page.
     */
    private static String navigation(int page, int pages) {
        List<String> links = new ArrayList<>();
        if (page > 1) {
            links.add(link(1, "first"));
            links.add(link(page - 1, "previous"));
        }
        if (page < pages) {
            links.add(link(page + 1, "next"));
            links.add(link(pages, "last"));
        }
        return "<p class=\"pages\">Page " + page + " of " + pages + ": " + String.join(", ", links) + "</p>\n";
    }

    private static String link(int page, String word) {
        return "<a href=\"./" + nameOfPage(page) + "\">" + word + "</a>";
    }

    /**
     * Where a page whole of {@code entryCount} entries is split, given the exact size of each entry as the page written
     * whole told it (ADR-303 §3): greedily, in membership order, so that a page takes the next entry while it stays
     * within {@code ceiling} and each page holds at least one, a page holding one entry alone being allowed to pass it.
     *
     * <p><b>What depends on the plan is counted at an upper bound</b>, so that no page comes out larger than planned: a
     * page number, and the number of pages, at the digits of {@code entryCount + 1}; and every citation of the writing
     * as if it carried the longest page name.
     *
     * @param entrySizes the bytes of each entry, from its {@code <li>} to the line break after its close, which are the
     *     same on whichever page the entry lands
     * @return the breaks, or empty where the plan is one page: a cluster of fewer than two entries has no entry's end to
     *     split at
     */
    static Optional<PageBreaks> plan(
            long ceiling, RecordedCluster recorded, SynthesisDoc doc, Unwritten why, long[] entrySizes) {
        int entryCount = entrySizes.length;
        if (entryCount < 2) {
            return Optional.empty();
        }
        String widest = "9".repeat(Integer.toString(entryCount + 1).length());
        String longestName = "index-" + widest + ".html";
        String widestNavigation = "<p class=\"pages\">Page " + widest + " of " + widest + ": "
                + String.join(", ", Collections.nCopies(4, "<a href=\"./" + longestName + "\">previous</a>"))
                + "</p>\n";
        long aroundEveryList = utf8(opening(recorded, doc)) + 2 * utf8(widestNavigation)
                + utf8("<h2>" + MEMBERSHIP_HEADING + "</h2>\n") + utf8(LIST_CLOSING) + utf8(Html.tail());
        long firstPage = aroundEveryList + utf8("<ol>\n")
                + utf8(firstPageBody(recorded, doc, why, number -> longestName));
        long laterPage = aroundEveryList + utf8(listOpening(entryCount - 1));
        List<Integer> firsts = new ArrayList<>();
        firsts.add(0);
        long size = firstPage + entrySizes[0];
        for (int at = 1; at < entryCount; at++) {
            if (size + entrySizes[at] <= ceiling) {
                size += entrySizes[at];
            } else {
                firsts.add(at);
                size = laterPage + entrySizes[at];
            }
        }
        return firsts.size() < 2 ? Optional.empty() : Optional.of(new PageBreaks(firsts, entryCount));
    }

    /**
     * What the page written whole holds before its first entry, in bytes: everything up to and including the list's
     * opening, which {@code entryEnds} does not tell (ADR-303 §3).
     */
    static long bytesBeforeTheFirstEntry(RecordedCluster recorded, SynthesisDoc doc, Unwritten why) {
        return utf8(opening(recorded, doc)) + utf8(firstPageBody(recorded, doc, why, number -> ""))
                + utf8(listOpening(0));
    }

    private static long utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Where a cluster's entries fall on its pages (ADR-303 §3): the first entry of each page, counted from 0, and the
     * number of entries.
     */
    static final class PageBreaks {
        private final List<Integer> firsts;
        private final int entries;

        PageBreaks(List<Integer> firsts, int entries) {
            this.firsts = List.copyOf(firsts);
            this.entries = entries;
        }

        int pages() {
            return firsts.size();
        }

        /** The entry, counted from 0, that page {@code page} opens with; the entry count for the page after the last. */
        int firstEntryOf(int page) {
            return page > firsts.size() ? entries : firsts.get(page - 1);
        }

        /**
         * The name of the page holding entry {@code number}, counted from 1, and the empty text where that is the first
         * page, which a citation on it links by its anchor alone, or where no entry has the number (ADR-303 §6).
         */
        String nameOfPageHolding(int number) {
            if (number < 1 || number > entries) {
                return "";
            }
            int page = 1;
            while (page < firsts.size() && firsts.get(page) < number) {
                page++;
            }
            return page == 1 ? "" : nameOfPage(page);
        }
    }

    /** Counts the bytes handed to the stream beneath it, which it does not close. */
    private static final class HandedOn extends OutputStream {
        private final OutputStream beneath;
        private long bytes;

        HandedOn(OutputStream beneath) {
            this.beneath = beneath;
        }

        @Override
        public void write(int b) throws IOException {
            beneath.write(b);
            bytes++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            beneath.write(buffer, offset, length);
            bytes += length;
        }

        @Override
        public void flush() throws IOException {
            beneath.flush();
        }
    }

    /**
     * The cluster's whole membership in the order this page numbers it (ADR-133): the documents the
     * call carried first, in the order their citation ordinals give them, then every other survivor of
     * the cluster highest-scoring first.
     *
     * <p><b>This ordering is forced rather than preferred.</b> Entries are numbered {@code 1..M} down
     * the page and a citation {@code [n]} has to reach the n-th of them, so if the documents the call
     * carried were scattered through a globally score-ordered list, no sequential numbering of that
     * list could agree with the ordinals the model was given. Sent-first is the only ordering under
     * which one numbering serves the prompt, the check and the reader — which is ADR-109's own
     * requirement, now met by a record instead of by an argument.
     *
     * <p><b>Where nothing was dropped this changes nothing.</b> The call fills in score order, so
     * sent-first <em>is</em> score order whenever it carried the top {@code k}.
     *
     * <p><b>A cluster nothing was written over keeps the list it had</b>: every document,
     * highest-scoring first. There is no call, no record and no citation, so there is nothing for the
     * numbering to serve.
     *
     * <p><b>A recorded exemplar this cluster no longer holds keeps its number and says so.</b> It is an
     * empty entry rather than dropped, because a citation above points at that number and dropping it
     * would move every document beneath it up one — which is #236's defect, reintroduced by the writer
     * that was meant to close it. It is not thrown on: a throw from here escapes {@link
     * Deliverable#writeTo} and rolls back every fault row the invocation had already recorded
     * (ADR-111), which is a heavy price for a state the ledger cannot reach. The same entry catches one
     * document recorded under two ordinals, which the record's own {@code UNIQUE} already refuses.
     *
     * @param doc the writing, or {@code null} where nothing was written over the cluster
     */
    static List<Optional<ListedSurvivor>> numbered(SynthesisDoc doc, List<ListedSurvivor> members) {
        List<ListedSurvivor> inScoreOrder = new ArrayList<>(members);
        // Ties keep the order the survivors arrived in, which is the order the exemplars were drawn
        // in, so an unstable sort can never put two equally-scoring documents out of step with it.
        inScoreOrder.sort(Comparator.comparingDouble(ListedSurvivor::score).reversed());
        if (doc == null) {
            return inScoreOrder.stream().map(Optional::of).toList();
        }
        Map<OccurrenceId, ListedSurvivor> unlisted = new LinkedHashMap<>();
        for (ListedSurvivor member : inScoreOrder) {
            unlisted.put(member.occurrence(), member);
        }
        List<Optional<ListedSurvivor>> numbered = new ArrayList<>();
        for (OccurrenceId sent : doc.sent()) {
            numbered.add(Optional.ofNullable(unlisted.remove(sent)));
        }
        unlisted.values().forEach(member -> numbered.add(Optional.of(member)));
        return List.copyOf(numbered);
    }

    /**
     * {@code name}'s UTF-8 bytes, each outside RFC 3986's unreserved set, {@code A}-{@code Z}, {@code a}-{@code z},
     * {@code 0}-{@code 9}, {@code -}, {@code .}, {@code _} and {@code ~}, as {@code %} and two upper-case hexadecimal
     * digits (ADR-280 §5).
     */
    static String percentEncoded(String name) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = b & 0xFF;
            if ((unsigned >= 'A' && unsigned <= 'Z') || (unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
                    || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append("0123456789ABCDEF".charAt(unsigned >> 4))
                        .append("0123456789ABCDEF".charAt(unsigned & 0xF));
            }
        }
        return encoded.toString();
    }

    /**
     * The cluster's whole membership, numbered as {@link #numbered} ordered it (ADR-104, ADR-109, ADR-133): each
     * entry an {@code <li>} carrying the id a citation in the writing resolves to, and either a link to the document's
     * copy in the tree or, where none stands, the document's own root-relative path with the reason in italics and
     * no link at all (ADR-274 §2.6).
     *
     * @param first the entry the page opens with, counted from 0, and {@code after} the entry past its last
     * @param folder the cluster's folder, which this very page sits in (ADR-280 §0)
     */
    private static void writeMembership(
            OutputStream stream,
            HandedOn handedOn,
            List<Optional<ListedSurvivor>> entries,
            int first,
            int after,
            Path folder,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress,
            LongConsumer entryEnds)
            throws IOException {
        for (int at = first; at < after; at++) {
            StringBuilder page = new StringBuilder();
            page.append("<li id=\"").append(ANCHOR_PREFIX).append(at + 1).append("\">\n");
            if (entries.get(at).isEmpty()) {
                page.append("<em>")
                        .append(Html.escape(Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT))
                        .append("</em>\n</li>\n");
                stream.write(page.toString().getBytes(StandardCharsets.UTF_8));
                endEntry(stream, handedOn, entryEnds);
                continue;
            }
            ListedSurvivor member = entries.get(at).get();
            String path = Html.escape(member.path().value());
            CopyOutcome outcome = copies.outcomeOf(member);
            Optional<Path> copy = Optional.empty();
            if (outcome == CopyOutcome.COPIED) {
                page.append("<a href=\"./")
                        .append(Html.escape(percentEncoded(copies.nameOf(member))))
                        .append("\">")
                        .append(path)
                        .append("</a>\n");
                copy = Optional.of(folder.resolve(copies.nameOf(member)));
            } else {
                page.append(path).append(" <em>").append(Html.escape(outcome.sentence())).append("</em>\n");
            }
            stream.write(page.toString().getBytes(StandardCharsets.UTF_8));
            pictures.writeUnder(stream, member, copy);
            stream.write("</li>\n".getBytes(StandardCharsets.UTF_8));
            endEntry(stream, handedOn, entryEnds);
            progress.membershipEntryWritten();
        }
    }

    /** Hands on everything written, and then tells how much that is (ADR-286 §2). */
    private static void endEntry(OutputStream stream, HandedOn handedOn, LongConsumer entryEnds) throws IOException {
        stream.flush();
        entryEnds.accept(handedOn.bytes);
    }
}
