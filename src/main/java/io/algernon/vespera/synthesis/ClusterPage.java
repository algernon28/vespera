package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
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
 * One cluster's page (ADR-103, ADR-104, ADR-109, ADR-133, ADR-174, #187): the heading, the writing with
 * its citations resolved, the disclosure where the writing rests on part of the cluster, and then the
 * cluster's complete membership.
 *
 * <p><b>Each {@code [n]} becomes a link to membership entry {@code n}</b> (ADR-109), and the membership
 * list is numbered from the documents the call was recorded as carrying (ADR-133), so entry {@code n} is
 * the {@code n}th document the model was given. No raw marker survives, which also keeps a citation at
 * the start of a line from parsing as a Markdown reference-link definition.
 */
final class ClusterPage {

    /**
     * The heading a cluster file opens its membership list with — rendered prose, so it says
     * <em>group</em> (ADR-122).
     */
    private static final String MEMBERSHIP_HEADING = "## The documents in this group";

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
     * What a citation link points at, and what each membership entry carries as its own anchor: the
     * cluster file's inside-the-page naming for membership entry {@code n} (ADR-109). A bound name of
     * ours, naming an entry of a cluster's membership list.
     */
    private static final String ANCHOR_PREFIX = "document-";

    private ClusterPage() {}

    /**
     * Writes {@code file}: the heading is the generated title where there is one, and the derived label
     * where there is not (ADR-106), so a cluster nothing was written over is still headed by something a
     * reader can match against the index.
     *
     * <p><b>The membership is complete, not the cited subset</b> (ADR-104), and every entry links to the
     * original in the archive by a path relative to this very file (ADR-135), or, where no relative path
     * exists, states the path and links nowhere. Nothing is copied and nothing is stat-ed.
     *
     * <p><b>The disclosure counts what the arrangement recorded</b>, not what this writer was handed
     * (ADR-112): 6a states a cluster's size once, and the index cell and this sentence read that one
     * number. Re-deriving it from the membership list would be a second place the arrangement is stated,
     * and the two would part company the moment a survivor failed to reach the list.
     *
     * <p><b>The page is written either way</b>: a cluster keeps its slot in the directory listing so the
     * order the operator approved and the order on disk stay one order (ADR-112). Where no writing exists
     * the sentence saying why stands under the heading, or the plain line when no reason was given
     * (ADR-174).
     *
     * <p>Tells {@code progress} of each membership entry that carries a document, and of nothing else:
     * the cluster file itself is reported by the caller.
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
            String corpusRoot,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        StringBuilder page = new StringBuilder();
        page.append("# ")
                .append(MarkdownSurroundings.ATX_HEADING.escape(doc == null ? recorded.label().value() : doc.title()))
                .append("\n\n");
        if (doc == null) {
            page.append(why == null ? Deliverable.NOTHING_WAS_WRITTEN_OVER_IT : why.sentence())
                    .append('\n');
        } else {
            page.append(withCitationLinks(doc.prose())).append('\n');
            int documentCount = recorded.cluster().documentCount();
            if (doc.documentsSent() < documentCount) {
                page.append('\n')
                        .append(SUBSET_DISCLOSURE.formatted(doc.documentsSent(), documentCount))
                        .append('\n');
            }
        }
        List<Optional<ListedSurvivor>> numbered = numbered(doc, members);
        if (!numbered.isEmpty()) {
            page.append('\n').append(MEMBERSHIP_HEADING).append("\n\n");
            appendMembership(
                    page,
                    numbered,
                    file.getParent(),
                    corpusRoot,
                    FilenameStem.of(file.getFileName().toString()),
                    pictures,
                    progress);
        }
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
     * {@code prose} with each {@code [n]} rewritten into a link to membership entry {@code n}
     * (ADR-109).
     *
     * <p><b>The range is not re-checked here.</b> ADR-109 puts the one check where the ordinals are
     * minted: every {@code [n]} a stored answer carries satisfied {@code 1 ≤ n ≤ k} before the row was
     * written. What makes the rewritten ordinal name the right entry is not that check but the record
     * beneath it — {@link #numbered} puts the {@code k} documents the call was recorded as carrying at
     * entries {@code 1..k}, in the order their ordinals were minted (ADR-133). A second range check at
     * write time would measure the same fact against a different bound, and a throw from here would
     * escape {@link Deliverable#writeTo} and roll back every fault row the invocation had already
     * recorded (ADR-111). What was checked is rendered, not checked again.
     */
    static String withCitationLinks(String prose) {
        return Citation.AS_WRITTEN
                .matcher(prose)
                .replaceAll(match -> "[" + match.group(1) + "](#" + ANCHOR_PREFIX + match.group(1) + ")");
    }

    /**
     * The cluster's whole membership, numbered as {@link #numbered} ordered it (ADR-104, ADR-109,
     * ADR-133): each entry carrying the anchor a citation in the prose above resolves to, and either a
     * link to the original in the archive or, where no relative route to it exists, the document's own
     * root-relative path stated with no link at all (ADR-135).
     *
     * <p><b>The anchor is written explicitly rather than left to a renderer's heading slugs.</b> A
     * citation must resolve against this entry by construction, and an implicit anchor whose name a
     * viewer derives from the entry's text is that viewer's business — two viewers would be free to
     * disagree, and a citation would then resolve in one of them and not the other.
     *
     * <p><b>The anchor sits inside the list item, never on a line of its own.</b> An {@code <a>} tag
     * alone on a line is a paragraph rather than an HTML block, and a list item can interrupt a
     * paragraph only when it is numbered {@code 1}: entry 1 would open a list and every entry after it
     * would be swallowed into the paragraph above as lazy continuation.
     *
     * @param pageDirectory the directory this very cluster file sits in, which a relative destination is
     *     composed against (ADR-135)
     * @param pictureDirectoryName the name a picture's own directory is given, beside this cluster file
     *     (ADR-149 §3)
     */
    private static void appendMembership(
            StringBuilder page,
            List<Optional<ListedSurvivor>> entries,
            Path pageDirectory,
            String corpusRoot,
            String pictureDirectoryName,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        for (int at = 0; at < entries.size(); at++) {
            int ordinal = at + 1;
            page.append(ordinal).append(". <a id=\"").append(ANCHOR_PREFIX).append(ordinal).append("\"></a>");
            if (entries.get(at).isEmpty()) {
                page.append(Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT).append("\n\n");
                continue;
            }
            ListedSurvivor member = entries.get(at).get();
            String escapedPath = MarkdownSurroundings.MEMBERSHIP_ENTRY.escape(member.path().value());
            Optional<String> destination = ArchiveLink.from(pageDirectory, corpusRoot, member.path().value());
            if (destination.isPresent()) {
                page.append('[').append(escapedPath).append("](").append(destination.get()).append(")\n\n");
            } else {
                page.append(escapedPath).append("\n\n");
            }
            pictures.appendUnder(page, member, ordinal, pageDirectory, pictureDirectoryName);
            progress.membershipEntryWritten();
        }
    }
}
