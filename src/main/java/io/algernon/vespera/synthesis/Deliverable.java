package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p><b>The archive is never touched.</b> No original is copied, and none is stat-ed (ADR-104): every
 * path here is written exactly as it was given, and nothing calls {@code Files.exists} against the
 * corpus root or anything beneath it.
 *
 * <p><b>Everything a reader of this tree sees says <em>group</em>; everything this class names says
 * <em>cluster</em></b> (ADR-122). {@link #NOTHING_WAS_WRITTEN_OVER_IT} is both halves on one line: a
 * bound name whose javadoc says cluster, holding a value that says group.
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
     * How the page says the writing rests on part of its cluster (ADR-108): the count the call sent
     * and the count the cluster holds. Written only where those differ, so a page over the whole
     * cluster carries no claim it cannot support.
     */
    private static final String SUBSET_DISCLOSURE = "Written from the %d highest-scoring of %d documents.";

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
                    .append(seedPath)
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
        String label = escapeCell(recorded.label().value());
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
                .append(label)
                .append("](")
                .append(link)
                .append(") | ")
                .append(documentCount)
                .append(" | ")
                .append(escapeCell(doc.doc().title()))
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
     * membership list is numbered in relevance-score order — the order the call drew its documents in
     * — so entry {@code n} is the {@code n}th document the model was given and a citation resolves by
     * construction. No raw marker survives, which also keeps a citation at the start of a line from
     * parsing as a Markdown reference-link definition.
     *
     * <p><b>The membership is complete, not the cited subset</b> (ADR-104), and every entry links to
     * the original in the archive as an absolute {@code file:} target composed from the recorded root
     * (ADR-104). Nothing is copied and nothing is stat-ed: a link that has gone dead because the
     * archive moved is the operator's to re-point, not this writer's to hide.
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
        page.append("# ").append(doc == null ? label : doc.title()).append("\n\n");
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
        if (!members.isEmpty()) {
            page.append('\n').append(MEMBERSHIP_HEADING).append("\n\n");
            appendMembership(page, members, corpusRoot);
        }
        Files.writeString(file, page.toString(), StandardCharsets.UTF_8);
    }

    /**
     * The cluster's whole membership, numbered in relevance-score order (ADR-104, ADR-109): every
     * survivor sits at the place its score gives it, each entry carrying the anchor a citation in the
     * prose above resolves to and a link to the original in the archive.
     *
     * <p>Highest score first, because that is the order ADR-108 draws a call's exemplars in and the
     * order the prompt numbers them: one numbering serves the prompt, the citation check and the
     * reader. Ties keep the order the survivors arrived in, which is the order the exemplars arrive
     * in, so an unstable sort can never put the page and the prose out of step.
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
     */
    private static void appendMembership(StringBuilder page, List<ListedSurvivor> members, String corpusRoot) {
        List<ListedSurvivor> inScoreOrder = new ArrayList<>(members);
        inScoreOrder.sort(Comparator.comparingDouble(ListedSurvivor::score).reversed());
        for (int at = 0; at < inScoreOrder.size(); at++) {
            int ordinal = at + 1;
            ListedSurvivor member = inScoreOrder.get(at);
            page.append(ordinal)
                    .append(". <a id=\"")
                    .append(anchorFor(ordinal))
                    .append("\"></a>[")
                    .append(escapeLinkText(member.path().value()))
                    .append("](")
                    .append(fileUrl(corpusRoot, member.path().value()))
                    .append(")\n\n");
        }
    }

    /**
     * {@code prose} with each {@code [n]} rewritten into a link to membership entry {@code n}
     * (ADR-109).
     *
     * <p><b>The range is not re-checked here.</b> ADR-109 puts the one check where the ordinals are
     * minted: every {@code [n]} a stored answer carries satisfied {@code 1 ≤ n ≤ k} before the row was
     * written, and this cluster's membership is at least as large as the {@code k} that call sent, so
     * each rewritten ordinal names an entry by construction. A second check at write time would measure
     * the same fact against a different bound, and a throw from here would escape {@link #writeTo} and
     * roll back every fault row the invocation had already recorded (ADR-111). What was checked is
     * rendered, not checked again.
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
     * {@code relativePath} as it sits beneath {@code corpusRoot}, as an absolute {@code file:} target
     * (ADR-104): the recorded root joined to the root-relative path the ledger holds.
     *
     * <p><b>Composed by escaping rather than by resolving a {@link Path}</b>, because the JDK's
     * Windows path parser refuses characters NTFS allows — a quote in a filename is legal on this
     * filesystem and a {@code Path.resolve} of one throws before any URI is built. Every character the
     * URI grammar reserves is escaped, spaces included, so a name never opens the link at its first
     * whitespace. Nothing is stat-ed to build it. Parentheses are escaped on top of the URI's own
     * quoting because a Markdown destination ends at the first unescaped {@code )}.
     */
    private static String fileUrl(String corpusRoot, String relativePath) {
        String root = corpusRoot.replace('\\', '/').replaceAll("^/+", "");
        String path = "/" + root + "/" + relativePath;
        try {
            return new URI("file", "", path, null)
                    .toASCIIString()
                    .replace("(", "%28")
                    .replace(")", "%29");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "could not compose a file: link for " + relativePath + " beneath " + corpusRoot, e);
        }
    }

    /** {@code text} as Markdown link text: the two characters that would close or nest the link escaped. */
    private static String escapeLinkText(String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
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

    /** A table cell rendered for the operator (ADR-122): the pipe that would break the row, escaped. */
    private static String escapeCell(String text) {
        return text.replace("|", "\\|");
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
