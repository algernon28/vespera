package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.FilenameStem;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One cluster's page (ADR-103, ADR-104, ADR-109, ADR-133, ADR-174, ADR-274 §1.4, #187): the heading, the writing
 * rendered by {@link ProseHtml} with its citations as links, the disclosure where the writing rests on part of the
 * cluster, and then the cluster's complete membership.
 *
 * <p>The membership list is numbered from the documents the call was recorded as carrying (ADR-133), so entry
 * {@code n} is the {@code n}th document the model was given and each {@code [n]} of the writing links to it.
 */
final class ClusterPage {

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

    private ClusterPage() {}

    /**
     * Writes {@code file}: the heading is the generated title where there is one, and the derived label
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
            Path file,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            List<ListedSurvivor> members,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        String title = doc == null ? recorded.label().value() : doc.title();
        StringBuilder page = new StringBuilder(Html.head(title));
        page.append("<h1>").append(Html.escape(title)).append("</h1>\n");
        if (doc == null) {
            page.append("<p><em>")
                    .append(Html.escape(why == null ? Deliverable.NOTHING_WAS_WRITTEN_OVER_IT : UnwrittenSentences.of(why)))
                    .append("</em></p>\n");
        } else {
            page.append("<div class=\"writing\">\n").append(ProseHtml.of(doc.prose())).append("\n</div>\n");
            int documentCount = recorded.cluster().documentCount();
            if (doc.documentsSent() < documentCount) {
                page.append("<p>")
                        .append(Html.escape(SUBSET_DISCLOSURE.formatted(doc.documentsSent(), documentCount)))
                        .append("</p>\n");
            }
        }
        List<Optional<ListedSurvivor>> numbered = numbered(doc, members);
        if (!numbered.isEmpty()) {
            page.append("<h2>").append(MEMBERSHIP_HEADING).append("</h2>\n<ol>\n");
            appendMembership(
                    page,
                    numbered,
                    file.getParent(),
                    FilenameStem.of(file.getFileName().toString()),
                    copies,
                    pictures,
                    progress);
            page.append("</ol>\n");
        }
        page.append(Html.tail());
        Files.writeString(file, page.toString(), StandardCharsets.UTF_8);
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
     * The cluster's whole membership, numbered as {@link #numbered} ordered it (ADR-104, ADR-109, ADR-133): each
     * entry an {@code <li>} carrying the id a citation in the writing resolves to, and either a link to the document's
     * copy in the tree or, where none stands, the document's own root-relative path with the reason in italics and
     * no link at all (ADR-274 §2.6).
     *
     * @param pageDirectory the directory this very page sits in
     * @param pictureDirectoryName the name a picture's own directory is given, beside this page (ADR-149 §3)
     */
    private static void appendMembership(
            StringBuilder page,
            List<Optional<ListedSurvivor>> entries,
            Path pageDirectory,
            String pictureDirectoryName,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        for (int at = 0; at < entries.size(); at++) {
            page.append("<li id=\"").append(ANCHOR_PREFIX).append(at + 1).append("\">\n");
            if (entries.get(at).isEmpty()) {
                page.append("<em>")
                        .append(Html.escape(Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT))
                        .append("</em>\n</li>\n");
                continue;
            }
            ListedSurvivor member = entries.get(at).get();
            String path = Html.escape(member.path().value());
            CopyOutcome outcome = copies.outcomeOf(member);
            if (outcome == CopyOutcome.COPIED) {
                page.append("<a href=\"../")
                        .append(Html.escape(Copies.pathOf(member)))
                        .append("\">")
                        .append(path)
                        .append("</a>\n");
            } else {
                page.append(path).append(" <em>").append(Html.escape(outcome.sentence())).append("</em>\n");
            }
            pictures.appendUnder(page, member, pageDirectory, pictureDirectoryName);
            page.append("</li>\n");
            progress.membershipEntryWritten();
        }
    }
}
