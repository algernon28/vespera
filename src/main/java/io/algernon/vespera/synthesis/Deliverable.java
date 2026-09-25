package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Writes the tree the operator is actually handed (ADR-103, ADR-104, ADR-109, ADR-111, ADR-112,
 * #186, #187): a directory of Markdown named for the run that produced it, opened by an index that is
 * mechanical rather than generated, a file per cluster, and a manifest beside them.
 *
 * <p><b>Plain values only.</b> This class learns no step, no stage and no {@code Profile} (ADR-110):
 * everything it needs arrives as {@link DeliverableProvenance}, {@link RecordedCluster}, {@link
 * RecordedSynthesisDoc} and {@link ListedSurvivor}, which is why {@code pipeline} is the only module
 * that gathers them.
 *
 * <p><b>The cluster files are a rendering of what 6b kept.</b> A stored answer's raw {@code [n]}
 * markers are rewritten into links to that cluster's numbered membership, and the membership is
 * composed here from the survivors rather than read out of the answer (ADR-109) — so a reader follows
 * a claim to an entry, and that entry to the original in the archive. Nothing the model wrote is
 * altered except the markers' rendering: the row still holds exactly what came back.
 *
 * <p><b>The numbering that makes a citation land is read, not derived</b> (ADR-133). Which documents
 * one call carried is recorded under the ordinals the model was given, and {@link #numbered} lays the
 * membership out from that record: the documents the call carried first, in citation order, then
 * every other survivor of the cluster highest-scoring first. Deriving score order a second time here
 * agreed with the model only up to the first document the call had to drop.
 *
 * <p><b>The archive is never touched.</b> No original is copied, and none is stat-ed (ADR-104): every
 * path here is written exactly as it was given, and nothing calls {@code Files.exists} against the
 * corpus root or anything beneath it.
 *
 * <p><b>Everything a reader of this tree sees says <em>group</em>; everything this class names says
 * <em>cluster</em></b> (ADR-122). {@link #NOTHING_WAS_WRITTEN_OVER_IT} and {@link
 * #THE_CLUSTER_NO_LONGER_HOLDS_IT} are both halves on one line apiece: a bound name whose javadoc
 * says cluster, holding a value that says group.
 *
 * <p><b>The order is rendered, never re-derived.</b> {@code arrangement} arrives already in the order
 * the operator approved (ADR-112), and neither level here is sorted again — a partition's position
 * comes from its clusters' own {@code partitionOrder}, and a cluster's from its own {@code
 * clusterOrder}.
 */
public final class Deliverable {

    /** Where every tree this stage writes lives, beneath the working directory (ADR-103). */
    public static final String DIRECTORY_NAME = "deliverable";

    /** The mechanical listing at the root of every tree. */
    public static final String INDEX_FILE_NAME = "index.md";

    /** The manifest at the root of every tree (ADR-104, ADR-112). */
    public static final String MANIFEST_FILE_NAME = "documents.csv";

    /**
     * What {@code index.md} says in place of a title for a cluster nothing was written over
     * (ADR-111). A bound name of ours, naming a cluster in its own javadoc; a value rendered for the
     * operator, naming a group (ADR-122) — the two are answered by different rules, and sitting on one
     * line of source joins them not at all.
     */
    static final String NOTHING_WAS_WRITTEN_OVER_IT = "*nothing was written over this group*";

    /**
     * What stands at the number of a document the writing was made from and the cluster no longer
     * holds (ADR-133). Rendered prose, so it says <em>group</em> where the name holding it says
     * cluster (ADR-122).
     *
     * <p>The entry is kept rather than dropped because a citation above points at its number:
     * dropping it would move every document beneath it up one and leave those citations naming the
     * wrong documents, which is the defect this numbering exists to close.
     */
    static final String THE_CLUSTER_NO_LONGER_HOLDS_IT =
            "*a document the writing was made from, which this group no longer holds*";

    /** The header the index opens every partition's table with — rendered prose, not a column name. */
    private static final String INDEX_TABLE_HEADER = "| Group | Documents | Written up as |";

    /**
     * The heading a cluster file opens its membership list with — rendered prose, so it says
     * <em>group</em> (ADR-122).
     */
    private static final String MEMBERSHIP_HEADING = "## The documents in this group";

    /**
     * A citation as the model wrote it: a bracketed ordinal into the exemplars that call sent
     * (ADR-109). Rewritten at write time into a link to the membership entry carrying the same
     * ordinal, so no raw marker survives into the file.
     */
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)\\]");

    /**
     * How the page says the writing rests on part of its cluster (ADR-108, ADR-133): the count the
     * call sent and the count the cluster holds. Written only where those differ, so a page over the
     * whole cluster carries no claim it cannot support.
     *
     * <p><b>The first, not the highest-scoring</b> (ADR-133). A document the call could not carry is
     * dropped wherever it scored, so what was sent is in general not the top anything — where a drop
     * happened the old sentence named a set including a document the prose was not written from,
     * which is this ticket's defect stated in words instead of a link. What is true either way, and
     * what a reader can check against the list on this very page, is that the writing was made from
     * the entries this page numbers first.
     */
    private static final String SUBSET_DISCLOSURE = "Written from the first %d of the %d documents in this group.";

    /**
     * What a citation link points at, and what each membership entry carries as its own anchor: the
     * cluster file's inside-the-page naming for membership entry {@code n} (ADR-109). A bound name of
     * ours, naming an entry of a cluster's membership list.
     */
    private static final String ANCHOR_PREFIX = "document-";

    /**
     * Every column the manifest carries, in order (ADR-104, ADR-112). A machine-read header, not
     * prose, so it names columns as the ledger names them rather than in the reader's plain words
     * (ADR-122).
     */
    private static final String MANIFEST_HEADER = "occurrence_id,path,content_hash,winning_seed,"
            + "relevance_score,seed_partition,cluster,partition_order,cluster_order";

    private Deliverable() {}

    /**
     * Writes the whole tree and returns where it landed.
     *
     * @param workingDirectory the directory the database is in — the one thing that moves anything
     *     this tool writes (ADR-103)
     * @param provenance what produced the tree, stated so {@code index.md} needs no database beside it
     * @param arrangement every cluster of the arrangement, in stored order — a hole where a cluster
     *     was never written over included, since a faulted cluster keeps its slot (ADR-111, ADR-112)
     * @param written every synthesis doc a call actually produced
     * @param survivors every survivor the arrangement arranged, for the manifest (ADR-104)
     * @return {@code <workingDirectory>/deliverable/<runId>}
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors) {
        Path tree = workingDirectory.resolve(DIRECTORY_NAME).resolve(provenance.runId());
        try {
            Files.createDirectories(tree);
            writeIndexAndClusterFiles(tree, provenance, arrangement, written, survivors);
            writeManifest(tree, arrangement, survivors);
            return tree;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the deliverable tree at " + tree, e);
        }
    }

    private static void writeIndexAndClusterFiles(
            Path tree,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors)
            throws IOException {
        Map<ClusterKey, RecordedSynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (RecordedSynthesisDoc doc : written) {
            writtenByCluster.put(new ClusterKey(doc.winningSeed(), doc.clusterOrdinal()), doc);
        }

        Map<OccurrenceId, String> seedPathByPartition = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            seedPathByPartition.putIfAbsent(survivor.winningSeed(), survivor.seedPath());
        }

        Map<ClusterKey, List<ListedSurvivor>> membersByCluster = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            membersByCluster
                    .computeIfAbsent(
                            new ClusterKey(survivor.winningSeed(), survivor.clusterOrdinal()),
                            key -> new ArrayList<>())
                    .add(survivor);
        }

        Map<OccurrenceId, List<RecordedCluster>> byPartition = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            byPartition
                    .computeIfAbsent(recorded.cluster().winningSeed(), key -> new ArrayList<>())
                    .add(recorded);
        }

        int partitionWidth = String.valueOf(byPartition.size()).length();

        StringBuilder index = new StringBuilder();
        openIndexWith(index, provenance);

        for (Map.Entry<OccurrenceId, List<RecordedCluster>> partition : byPartition.entrySet()) {
            List<RecordedCluster> clusters = partition.getValue();
            int clusterWidth = String.valueOf(clusters.size()).length();
            String seedPath = seedPathByPartition.get(partition.getKey());
            if (seedPath == null) {
                throw new IllegalArgumentException("no survivor names the seed of partition "
                        + partition.getKey().value() + "; a partition directory cannot be named without it");
            }
            int partitionOrdinal = clusters.getFirst().cluster().partitionOrder();
            String partitionDirName = pad(partitionOrdinal, partitionWidth) + "-" + slug(stemOf(seedPath));
            Path partitionDir = tree.resolve(partitionDirName);
            Files.createDirectories(partitionDir);

            index.append("\n## ")
                    .append(inAHeading(seedPath))
                    .append("\n\n")
                    .append(INDEX_TABLE_HEADER)
                    .append('\n')
                    .append("|---|---|---|\n");

            for (RecordedCluster recorded : clusters) {
                writeClusterEntry(
                        index,
                        partitionDir,
                        partitionDirName,
                        clusterWidth,
                        recorded,
                        writtenByCluster,
                        membersByCluster.getOrDefault(ClusterKey.of(recorded), List.of()),
                        provenance.corpusRoot());
            }
        }

        Files.writeString(tree.resolve(INDEX_FILE_NAME), index.toString(), StandardCharsets.UTF_8);
    }

    private static void writeClusterEntry(
            StringBuilder index,
            Path partitionDir,
            String partitionDirName,
            int clusterWidth,
            RecordedCluster recorded,
            Map<ClusterKey, RecordedSynthesisDoc> writtenByCluster,
            List<ListedSurvivor> members,
            String corpusRoot)
            throws IOException {
        String label = inACell(recorded.label().value());
        int documentCount = recorded.cluster().documentCount();
        RecordedSynthesisDoc doc = writtenByCluster.get(ClusterKey.of(recorded));
        String clusterFileName =
                pad(recorded.cluster().clusterOrder(), clusterWidth) + "-" + slug(recorded.label().value()) + ".md";
        if (doc == null) {
            index.append("| ")
                    .append(label)
                    .append(" | ")
                    .append(documentCount)
                    .append(" | ")
                    .append(NOTHING_WAS_WRITTEN_OVER_IT)
                    .append(" |\n");
            writeClusterFile(
                    partitionDir.resolve(clusterFileName),
                    recorded.label().value(),
                    null,
                    documentCount,
                    members,
                    corpusRoot);
            return;
        }
        String link = partitionDirName + "/" + clusterFileName;
        index.append("| [")
                .append(asLinkText(recorded.label().value()))
                .append("](")
                .append(link)
                .append(") | ")
                .append(documentCount)
                .append(" | ")
                .append(inACell(doc.doc().title()))
                .append(" |\n");
        writeClusterFile(
                partitionDir.resolve(clusterFileName),
                recorded.label().value(),
                doc.doc(),
                documentCount,
                members,
                corpusRoot);
    }

    /**
     * The one file every cluster's slot gets (ADR-103, ADR-104, ADR-109, #187): the heading, the
     * writing with its citations resolved, the disclosure where the writing rests on part of the
     * cluster, and then the cluster's complete membership.
     *
     * <p><b>The heading is the generated title where there is one, and the derived label where there
     * is not</b> (ADR-106): the label is the name that always exists, so a cluster nothing was written
     * over is still headed by something a reader can match against the index.
     *
     * <p><b>Each {@code [n]} becomes a link to membership entry {@code n}</b> (ADR-109), and the
     * membership list is numbered from the documents the call was recorded as carrying (ADR-133) —
     * those first, in the order their ordinals were minted, then the rest highest-scoring first — so
     * entry {@code n} is the {@code n}th document the model was given. No raw marker survives, which
     * also keeps a citation at the start of a line from parsing as a Markdown reference-link
     * definition.
     *
     * <p><b>The membership is complete, not the cited subset</b> (ADR-104), and every entry links to
     * the original in the archive by a path relative to this very file (ADR-135): the two directories
     * — this file's own and the recorded corpus root — are relativized, and the occurrence's
     * root-relative path is appended to the result as text. Where no relative path exists between them
     * — the archive on another Windows volume — the entry states the document's root-relative path
     * and links nowhere, because a {@code file:} destination is measured to be a link in only two of
     * seven renderer configurations, and in two of the five failures the reader is shown the literal
     * Markdown source rather than a page (ADR-135), and that failure is this writer's own to avoid.
     * Nothing is copied and nothing is stat-ed: a link that has
     * gone dead because the archive moved is the operator's to re-point, not this writer's to hide.
     *
     * <p><b>The disclosure counts what the arrangement recorded</b>, not what this writer was handed
     * (ADR-112): 6a states a cluster's size once, and the index cell and this sentence read that one
     * number. Re-deriving it from the membership list would be a second place the arrangement is
     * stated, and the two would part company the moment a survivor failed to reach the list — in the
     * one sentence that exists to tell a reader how much of the cluster the writing rests on.
     *
     * <p><b>The index links to a page only where writing exists</b>, but the page itself is written
     * either way: a cluster keeps its slot in the directory listing so the order the operator approved
     * and the order on disk stay one order (ADR-112).
     */
    private static void writeClusterFile(
            Path file,
            String label,
            SynthesisDoc doc,
            int documentCount,
            List<ListedSurvivor> members,
            String corpusRoot)
            throws IOException {
        StringBuilder page = new StringBuilder();
        page.append("# ").append(inAHeading(doc == null ? label : doc.title())).append("\n\n");
        if (doc == null) {
            page.append(NOTHING_WAS_WRITTEN_OVER_IT).append('\n');
        } else {
            page.append(withCitationLinks(doc.prose())).append('\n');
            if (doc.documentsSent() < documentCount) {
                page.append('\n')
                        .append(SUBSET_DISCLOSURE.formatted(doc.documentsSent(), documentCount))
                        .append('\n');
            }
        }
        List<MembershipEntry> numbered = numbered(doc, members);
        if (!numbered.isEmpty()) {
            page.append('\n').append(MEMBERSHIP_HEADING).append("\n\n");
            appendMembership(page, numbered, file.getParent(), corpusRoot);
        }
        Files.writeString(file, page.toString(), StandardCharsets.UTF_8);
    }

    /**
     * The cluster's whole membership in the order this page numbers it (ADR-133): the documents the
     * call carried first, in the order their citation ordinals give them, then every other survivor
     * of the cluster highest-scoring first.
     *
     * <p><b>This ordering is forced rather than preferred.</b> Entries are numbered {@code 1..M} down
     * the page and a citation {@code [n]} has to reach the n-th of them, so if the documents the call
     * carried were scattered through a globally score-ordered list, no sequential numbering of that
     * list could agree with the ordinals the model was given. Sent-first is the only ordering under
     * which one numbering serves the prompt, the check and the reader — which is ADR-109's own
     * requirement, now met by a record instead of by an argument.
     *
     * <p><b>Where nothing was dropped this changes nothing.</b> The call fills in score order, so
     * sent-first <em>is</em> score order whenever it carried the top {@code k} — which is the
     * ordinary case. This differs from numbering in score order only on the clusters where numbering
     * in score order was wrong.
     *
     * <p><b>A cluster nothing was written over keeps the list it had</b>: every document,
     * highest-scoring first. There is no call, no record and no citation, so there is nothing for the
     * numbering to serve.
     *
     * <p><b>A recorded exemplar this cluster no longer holds keeps its number and says so.</b> The
     * entry is written as {@link #THE_CLUSTER_NO_LONGER_HOLDS_IT} rather than dropped, because a
     * citation above points at that number and dropping it would move every document beneath it up
     * one — which is this ticket's defect, reintroduced by the writer that was meant to close it. It
     * is not thrown on: a throw from here escapes {@link #writeTo} and rolls back every fault row the
     * invocation had already recorded (ADR-111), which is a heavy price for a state the ledger cannot
     * reach — {@code document_cluster}'s rows are written once per scoring run and the approval fixes
     * which run that is — and the honest entry costs nothing. The same entry catches one document
     * recorded under two ordinals, which the record's own {@code UNIQUE} already refuses.
     */
    private static List<MembershipEntry> numbered(SynthesisDoc doc, List<ListedSurvivor> members) {
        List<ListedSurvivor> inScoreOrder = new ArrayList<>(members);
        // Ties keep the order the survivors arrived in, which is the order the exemplars were drawn
        // in, so an unstable sort can never put two equally-scoring documents out of step with it.
        inScoreOrder.sort(Comparator.comparingDouble(ListedSurvivor::score).reversed());
        if (doc == null) {
            return inScoreOrder.stream().map(MembershipEntry::new).toList();
        }
        Map<OccurrenceId, ListedSurvivor> unlisted = new LinkedHashMap<>();
        for (ListedSurvivor member : inScoreOrder) {
            unlisted.put(member.occurrence(), member);
        }
        List<MembershipEntry> numbered = new ArrayList<>();
        for (OccurrenceId sent : doc.sent()) {
            numbered.add(new MembershipEntry(unlisted.remove(sent)));
        }
        unlisted.values().forEach(member -> numbered.add(new MembershipEntry(member)));
        return List.copyOf(numbered);
    }

    /**
     * One line of a cluster file's membership list.
     *
     * <p>Its document is absent in one case only, and it is an entry rather than a broken record: a
     * document the writing was made from that this cluster no longer holds. The line keeps its number
     * because a citation above points at it.
     *
     * @param document the survivor at this number, or {@code null} where the cluster no longer holds
     *     the document the call was written from
     */
    private record MembershipEntry(ListedSurvivor document) {}

    /**
     * The cluster's whole membership, numbered as {@link #numbered} ordered it (ADR-104, ADR-109,
     * ADR-133): each entry carrying the anchor a citation in the prose above resolves to, and either a
     * link to the original in the archive or, where no relative route to it exists, the document's own
     * root-relative path stated with no link at all (ADR-135).
     *
     * <p>The order is rendered rather than decided here — which documents the call carried is a
     * recorded fact and score is only what orders the rest, so deriving either at this point would be
     * the second derivation this record exists to remove.
     *
     * <p><b>The anchor is written explicitly rather than left to a renderer's heading slugs.</b> A
     * citation must resolve against this entry by construction, and an implicit anchor whose name a
     * viewer derives from the entry's text is that viewer's business — two viewers would be free to
     * disagree, and a citation would then resolve in one of them and not the other.
     *
     * <p><b>The anchor sits inside the list item, never on a line of its own.</b> An {@code <a>} tag
     * alone on a line is a paragraph rather than an HTML block, and a list item can interrupt a
     * paragraph only when it is numbered {@code 1}: entry 1 would open a list and every entry after it
     * would be swallowed into the paragraph above as lazy continuation. The membership would still
     * hold every survivor in score order as bytes, and would render as one item and a wall of text.
     *
     * @param pageDirectory the directory this very cluster file sits in, which a relative destination is
     *     composed against (ADR-135)
     */
    private static void appendMembership(
            StringBuilder page, List<MembershipEntry> entries, Path pageDirectory, String corpusRoot) {
        Optional<Path> corpusRootDirectory = asDirectory(corpusRoot);
        for (int at = 0; at < entries.size(); at++) {
            int ordinal = at + 1;
            ListedSurvivor member = entries.get(at).document();
            page.append(ordinal).append(". <a id=\"").append(anchorFor(ordinal)).append("\"></a>");
            if (member == null) {
                page.append(THE_CLUSTER_NO_LONGER_HOLDS_IT).append("\n\n");
                continue;
            }
            String escapedPath = escapeLinkText(member.path().value());
            Optional<String> destination = corpusRootDirectory
                    .filter(root -> hasARelativeRoute(pageDirectory, root))
                    .map(root -> relativeDestination(pageDirectory, root, member.path().value()));
            if (destination.isPresent()) {
                page.append('[').append(escapedPath).append("](").append(destination.get()).append(")\n\n");
            } else {
                page.append(escapedPath).append("\n\n");
            }
        }
    }

    /**
     * {@code prose} with each {@code [n]} rewritten into a link to membership entry {@code n}
     * (ADR-109).
     *
     * <p><b>The range is not re-checked here.</b> ADR-109 puts the one check where the ordinals are
     * minted: every {@code [n]} a stored answer carries satisfied {@code 1 ≤ n ≤ k} before the row was
     * written. What makes the rewritten ordinal name the right entry is not that check but the record
     * beneath it — {@link #numbered} puts the {@code k} documents the call was recorded as carrying at
     * entries {@code 1..k}, in the order their ordinals were minted (ADR-133), so entry {@code n} is
     * the document the model wrote {@code [n]} about. Before that record the claim here was that the
     * membership is at least as large as {@code k} and so each ordinal names an entry "by
     * construction": the size half was true, and the mapping half was not, wherever a member was
     * dropped between the membership and the call (#236). A second range check at write time would
     * measure the same fact against a different bound, and a throw from here would escape {@link
     * #writeTo} and roll back every fault row the invocation had already recorded (ADR-111). What was
     * checked is rendered, not checked again.
     */
    private static String withCitationLinks(String prose) {
        return CITATION.matcher(prose)
                .replaceAll(match -> "[" + match.group(1) + "](#" + ANCHOR_PREFIX + match.group(1) + ")");
    }

    /** The inside-the-page name a citation and the membership entry it points at share (ADR-109). */
    private static String anchorFor(int ordinal) {
        return ANCHOR_PREFIX + ordinal;
    }

    /**
     * {@code corpusRoot} as a {@link Path}, or empty where this machine's parser refuses it (ADR-135):
     * a recorded root is a directory this tool composed or canonicalised on some machine, but not
     * necessarily this one, and a root carrying a character this machine's path parser refuses — a
     * quote or a NUL, for instance — is one this writer declines to link through rather than fail the
     * whole invocation over (ADR-111). A UNC share or a root written for a different platform parses
     * fine here and is refused earlier, by {@link #hasARelativeRoute}.
     */
    private static Optional<Path> asDirectory(String corpusRoot) {
        try {
            return Optional.of(Path.of(corpusRoot));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a path from {@code pageDirectory} to {@code corpusRootDirectory} can be composed at all
     * (ADR-135): both absolute, and rooted the same — a Windows drive letter or a UNC prefix a relative
     * path can never cross. A predicate over the two directories rather than a caught exception, because
     * {@link Path#relativize} throws {@link IllegalArgumentException} for the cases this refuses, and a
     * throw escaping {@link #writeTo} would roll back every fault row the invocation had already
     * recorded (ADR-111).
     */
    private static boolean hasARelativeRoute(Path pageDirectory, Path corpusRootDirectory) {
        return pageDirectory.isAbsolute()
                && corpusRootDirectory.isAbsolute()
                && Objects.equals(pageDirectory.getRoot(), corpusRootDirectory.getRoot());
    }

    /**
     * {@code relativePath} as it sits beneath {@code corpusRootDirectory}, as a destination relative to
     * {@code pageDirectory} (ADR-135): the two directories relativized, and the occurrence's
     * root-relative path appended to the result as text.
     *
     * <p><b>Never composed by resolving {@code relativePath} itself against a {@link Path}</b>, because
     * the JDK's Windows path parser refuses characters NTFS allows — a quote in a filename is legal on
     * this filesystem and a {@code Path.resolve} of one throws before any URI is built. Only the two
     * directories, which this tool composed or canonicalised, ever go through {@link Path}; the
     * document's own name is appended as text. Every character the URI grammar makes illegal in a
     * path is escaped, spaces included, so a name never opens the link at its first whitespace.
     * Nothing is stat-ed to build it. Parentheses are escaped on top of the URI's own quoting
     * because a Markdown destination ends at the first unescaped {@code )}. The ampersand joins
     * them on the same ground: the URI grammar permits it in a path, so it survives the URI's own
     * quoting untouched, but a Markdown destination decodes a named entity reference inside it, so
     * a name such as {@code &copy;} would resolve to a document the archive does not hold
     * (ADR-137). Only the named form needs it — the numeric form, {@code &#169;}, is already
     * defused because {@code #} becomes {@code %23} for an unrelated reason.
     */
    private static String relativeDestination(Path pageDirectory, Path corpusRootDirectory, String relativePath) {
        String relativeDirectory =
                pageDirectory.relativize(corpusRootDirectory).toString().replace('\\', '/');
        String rawPath = relativeDirectory.isEmpty() ? relativePath : relativeDirectory + "/" + relativePath;
        try {
            return new URI(null, null, rawPath, null)
                    .toASCIIString()
                    .replace("(", "%28")
                    .replace(")", "%29")
                    .replace("&", "%26");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "could not compose a relative link for " + relativePath + " beneath " + corpusRootDirectory, e);
        }
    }

    /**
     * {@code text} as Markdown link text: the backslash, the angle bracket and the ampersand
     * shared with every Markdown surrounding (ADR-136), the two characters that would close or nest
     * the link, and the backtick that would open a code span (ADR-148), all escaped.
     *
     * <p><b>A membership entry, not a table cell</b>, which is why this is not {@link #asLinkText}
     * (#246). Here the text is a path, the surrounding structure is a numbered list, and a pipe is an
     * ordinary character. The backslash is escaped first here too, but not on {@link #inACell}'s
     * ground: this method inserts {@code \[}, {@code \]} and {@code \`}, which that one now inserts
     * as well, so the hazard has the same shape -- what it lacks here is an input. It is applied to
     * {@code member.path().value()}, an {@code OccurrencePath} that is separator-normalised
     * to {@code /} and cannot hold a backslash on NTFS (ADR-051) — nothing here should be read as
     * claiming a path on this filesystem can carry one. The rule is kept anyway as cheap defence
     * against an input that source rules out: a rule that escapes brackets without escaping the escape
     * character first is the wrong shape to leave lying about for the next reader to copy. Five
     * rules exist in this class because there are five surroundings — a cell, a list, a heading in
     * {@link #inAHeading}, the CSV in {@link #quoted}, and a link destination in
     * {@link #relativeDestination} — and a value is only ever dangerous with respect to the one it
     * lands in. What a surrounding answers to decides the form its rule takes, never whether it is
     * one (ADR-137): the three Markdown text positions answer to a reader and escape with a
     * backslash, the destination answers to a resolver and escapes by percent-encoding, and the CSV
     * answers to a parser and escapes by doubling a quote. ADR-136's character class lands in all
     * five even so — {@code \<} and {@code \&} in the cell, the list and the heading; {@code %3C}
     * and {@code %26} in the destination; neither in the CSV. ADR-148's backtick lands the same
     * way: {@code \`} in the cell, the list and the heading; {@code %60} in the destination, written
     * by the URI quoting; nothing in the CSV.
     */
    private static String escapeLinkText(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<").replace("&", "\\&").replace("[", "\\[")
                .replace("]", "\\]").replace("`", "\\`");
    }

    private static void openIndexWith(StringBuilder index, DeliverableProvenance provenance) {
        index.append("# Deliverable ")
                .append(provenance.runId())
                .append("\n\n")
                .append("- Run: ")
                .append(provenance.runId())
                .append('\n')
                .append("- Walk: ")
                .append(provenance.walk())
                .append('\n')
                .append("- Archive root: ")
                .append(provenance.corpusRoot())
                .append('\n');
        for (NamedValue value : provenance.values()) {
            index.append("- ").append(value.key()).append(": ").append(value.value()).append('\n');
        }
        index.append("\nThe path column of documents.csv names each document beneath the archive"
                + " root recorded above, and the links inside a group's page resolve against it"
                + " too. The seed_partition column is not one of those: it names a seed beneath the seed"
                + " folder, which is a root of its own. Nothing here is a copy of the archive, so if the"
                + " archive moves, everything resolved against that archive root dies until this tree is"
                + " re-pointed at where it went.\n");
    }

    private static void writeManifest(Path tree, List<RecordedCluster> arrangement, List<ListedSurvivor> survivors)
            throws IOException {
        Map<ClusterKey, ArrangedCluster> orderByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            orderByCluster.put(ClusterKey.of(recorded), recorded.cluster());
        }

        StringBuilder csv = new StringBuilder(MANIFEST_HEADER).append('\n');
        for (ListedSurvivor survivor : survivors) {
            ArrangedCluster cluster =
                    orderByCluster.get(new ClusterKey(survivor.winningSeed(), survivor.clusterOrdinal()));
            if (cluster == null) {
                // ADR-105 and #175 §6: the arrangement is total over the survivors it was built from,
                // so a survivor with no cluster row is a broken invariant, not a document the
                // arrangement happens to be silent about. A 0,0 pair here would be two plausible
                // numbers in a file built to be loaded straight into a table (ADR-104) -- the one
                // shape of wrong this manifest exists to prevent.
                throw new IllegalArgumentException("survivor " + survivor.occurrence().value()
                        + " names cluster " + survivor.clusterOrdinal() + " of seed "
                        + survivor.winningSeed().value() + ", which the arrangement does not carry");
            }
            csv.append(survivor.occurrence().value())
                    .append(',')
                    .append(quoted(survivor.path().value()))
                    .append(',')
                    .append(survivor.contentHash())
                    .append(',')
                    .append(survivor.winningSeed().value())
                    .append(',')
                    .append(survivor.score())
                    .append(',')
                    .append(quoted(survivor.seedPath()))
                    .append(',')
                    .append(survivor.clusterOrdinal())
                    .append(',')
                    .append(cluster.partitionOrder())
                    .append(',')
                    .append(cluster.clusterOrder())
                    .append('\n');
        }
        Files.writeString(tree.resolve(MANIFEST_FILE_NAME), csv.toString(), StandardCharsets.UTF_8);
    }

    /**
     * {@code field} as one RFC 4180 CSV field: unchanged where it carries none of the three characters
     * that would make it ambiguous, and otherwise wrapped in {@code "} with any embedded {@code "}
     * doubled. Applied to the two path columns, because an NTFS filename may legally carry a comma, a
     * quote or a newline, and this manifest exists so a consumer can load it into a table without
     * parsing anything first (ADR-104) — one such document would otherwise shift every column after it
     * for that row. Quoted only where needed rather than unconditionally, so an ordinary path — the
     * overwhelming majority of them — reads exactly as it does in the archive.
     */
    private static String quoted(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }

    /** {@code ordinal}, zero-padded to {@code width} digits (ADR-112). */
    private static String pad(int ordinal, int width) {
        return String.format("%0" + width + "d", ordinal);
    }

    /**
     * A filename-safe rendering of {@code text}: lower-cased, every run of characters that is not a
     * letter or digit collapsed to one hyphen, and no leading or trailing hyphen.
     */
    private static String slug(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String hyphenated = lower.replaceAll("[^a-z0-9]+", "-");
        return hyphenated.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    /** {@code path}'s filename without its folders or its extension (mirrors {@code ClusterLabel}'s). */
    private static String stemOf(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int extension = filename.lastIndexOf('.');
        return extension < 0 ? filename : filename.substring(0, extension);
    }

    /**
     * {@code text} as an ATX heading carries it: folded to one line, then the backslash and the
     * five characters a renderer would read as markup escaped (ADR-136, ADR-138, ADR-148).
     *
     * <p><b>A surrounding of its own, and so a rule of its own</b> -- the fourth to be counted, of the
     * five {@link #escapeLinkText} lists (ADR-136, ADR-137) -- which is ADR-134's own rule applied
     * where its premise holds rather than an exception to it. A heading has no pipe to guard --
     * not structural on a line that begins with {@code #} -- but its two brackets are guarded here,
     * because a link and an image both form in an ATX heading in every renderer measured (ADR-138),
     * and its backtick is guarded here, unconditionally, because a pair forms a code span in every
     * renderer measured (ADR-148). {@link #inACell} is still not
     * borrowed for the pipe: it would write a backslash before a character a heading has no hazard
     * from. A value is only ever dangerous with respect to the structure it lands in, and that is the
     * whole reason these rules are separate.
     *
     * <p><b>This is the position that had no escaping at all</b>: folding was its entire treatment,
     * so a title reading {@code <draft>} reached the renderer untouched and was deleted outright by
     * GitHub's sanitiser, leaving the word gone with nothing to say it had been (ADR-136).
     *
     * <p><b>The backslash goes first</b>, for {@link #inACell}'s reason: this rule inserts its own
     * escape character, so a literal one already present must be escaped before it can merge with
     * what is added after it (ADR-134).
     */
    private static String inAHeading(String text) {
        return onOneLine(text).replace("\\", "\\\\").replace("<", "\\<").replace("&", "\\&")
                .replace("[", "\\[").replace("]", "\\]").replace("`", "\\`");
    }

    /**
     * One cell of the index, made safe to sit in a Markdown table (ADR-122, ADR-138, #246).
     *
     * <p>Every value this guards is text this project did not write. A cluster's label is a document's
     * own title as Docling read it, falling back to its filename stem (ADR-106), and a cluster's title
     * is what the model answered. A partition's heading is a path out of the archive and is guarded by
     * {@link #inAHeading} rather than here. Neither of the two was composed to sit in a table, and two
     * characters end one:
     *
     * <ul>
     *   <li>A {@code |} closes the cell where it stands and shifts every value after it one column
     *       along, so a row says something different from what it was given.
     *   <li>A line break ends the <em>table</em>. Markdown stops a table at the first line that is not
     *       a row, so everything below it — every remaining cluster of every remaining partition —
     *       renders as prose. Nothing fails; the page is simply missing most of what it lists.
     * </ul>
     *
     * <p><b>Both are handled everywhere rather than where a value looks risky</b>, because a rule
     * applied per value is one more place for a page to silently become a different page.
     *
     * <p><b>A {@code <} or a {@code &} is the hazard every Markdown surrounding shares (ADR-136)</b>, and a
     * cell is no exception. Written through, a tag goes live where a renderer honours HTML, is
     * deleted outright by GitHub's sanitiser with nothing left to say a word was removed, and is
     * invisible but present in {@code marked} and {@code commonmark}. A bare {@code &} is worse
     * than harmless in one direction only -- a run such as {@code &copy;} is decoded to
     * {@code ©}, so the page says something other than what the archive holds. The escape
     * inserted here is the backslash escape {@code \<} and {@code \&}, not the HTML entities
     * {@code &lt;} and {@code &amp;}: those are a different operation, and writing them would
     * damage the rendered page and the plain-text reader alike.
     *
     * <p><b>A {@code [} or a {@code ]} composes a link or an image the value never asked this tool
     * to build (ADR-138)</b>, because a GFM table cell parses its content as inlines exactly as an
     * ATX heading does: unescaped, {@code [click me](evil.md) report} is a live link in the cell and
     * {@code ![shot](https://host/x.png)} fetches an image through the reader's browser, in a tree
     * ADR-103 says resolves every link with no network. The escape inserted is the same backslash
     * escape as the rest of this method's set, {@code \[} and {@code \]}.
     *
     * <p><b>A backtick is structural in a table cell exactly as it is in a heading (ADR-148)</b>: two
     * of them around any text form a code span in every renderer measured, and a lone one is a literal
     * backtick under the specification; only in link text does one renderer, {@code marked}, refuse
     * to form the link around it, a divergence ADR-148 §3 gives no weight. The escape
     * is unconditional, on the same ground {@link #inAHeading} gives, and it costs nothing where a
     * value carries no backtick.
     *
     * <p><b>The backslash goes first, on a ground that is not about the data (ADR-134).</b> This
     * method inserts escape characters of its own -- {@code \<}, {@code \&}, {@code \[}, {@code \]},
     * {@code \|} and {@code \`} -- so a literal backslash already present must be escaped before it can merge
     * with what is added after it; a rule that adds an escape character without first escaping one
     * already present is not a function of its input in the way it claims to be. A label reading
     * {@code Retrofits \| Phase 2} would otherwise become {@code Retrofits \\| Phase 2} — an escaped
     * backslash followed by a live pipe, which is #246's defect reintroduced by the rule written to
     * prevent it. This holds whatever the label turns out to contain.
     *
     * <p>What the data can carry corroborates rather than carries the decision: NTFS forbids a
     * backslash in a filename, so the stem fallback cannot carry one, but the primary source is the
     * document's own Docling title — arbitrary text lifted out of a title block, where a backslash is
     * an ordinary printable character — and a cluster's title is unbounded text the generation model
     * wrote under no filesystem constraint at all.
     */
    private static String inACell(String text) {
        return onOneLine(text).replace("\\", "\\\\").replace("<", "\\<").replace("&", "\\&")
                .replace("[", "\\[").replace("]", "\\]").replace("|", "\\|").replace("`", "\\`");
    }

    /**
     * {@code text} as a single line, which is what a table row and a heading need of it (#246).
     *
     * <p>A table row ends at a line break and so does a heading, so a value carrying one does not
     * merely look wrong — it ends the thing it was written into and turns what follows into prose.
     * The break is folded into a space rather than escaped, because Markdown has no escape for it in
     * either place, and a title that wrapped in the document it came from means one line here.
     */
    private static String onOneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    /**
     * The words a link in the index is made of (#246) -- a call site rather than a rule of its
     * own (ADR-138).
     *
     * <p>A link in a cell was never a sixth surrounding in ADR-137's enumeration: it is a table
     * cell that happens to carry a link, and everything it needs beyond what a cell needs is now
     * what {@link #inACell} needs. That method escapes the {@code [} and {@code ]} that would
     * otherwise close the link the index composes out of a cluster's label, so this method adds
     * nothing on top of what {@link #inACell} already returns.
     *
     * <p><b>Escaping the bracket a second time here was tried and measured wrong (ADR-138).</b>
     * With {@link #inACell} escaping a bracket and this method escaping it again, a literal
     * {@code [} already turned into {@code \[} was turned into {@code \\[} -- an escaped
     * backslash followed by a live bracket, the composition hazard ADR-134 exists to prevent,
     * reintroduced by the rule written to close it. Six of seven renderer configurations then
     * failed to form the index's only link at all.
     */
    private static String asLinkText(String text) {
        return inACell(text);
    }

    /**
     * A cluster's identity, used to line the arrangement up against what was actually written.
     *
     * @param winningSeed the seed whose partition the cluster sits in
     * @param ordinal the cluster's identity within that partition
     */
    private record ClusterKey(OccurrenceId winningSeed, int ordinal) {

        static ClusterKey of(RecordedCluster recorded) {
            return new ClusterKey(recorded.cluster().winningSeed(), recorded.cluster().ordinal());
        }
    }
}
