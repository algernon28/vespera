package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.TopicKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The manifest, {@code documents.csv}: one row per survivor, in the order given (ADR-104, ADR-112), and
 * the RFC 4180 quoting its two path columns need (ADR-136 §5).
 *
 * <p>It is written as its rows come, a page of survivors at a time, and nothing here holds the file whole
 * (ADR-223 section 7). The places of a page's clusters are asked for together, once for the page (ADR-241).
 *
 * <p>The CSV is the surrounding that answers to a parser, so its rule is a doubled quote and not the HTML
 * escaping rule (ADR-137 §4). It is opened in a spreadsheet too, so a path that one would run
 * as a formula is written behind a single quote (ADR-235). Its last two columns, {@code copy} and {@code
 * copy_outcome}, say what became of each survivor's copy (ADR-274 §1.4). The outcome is a word and needs no quoting;
 * the copy is the original's own file name, commas and quotes kept (ADR-280 §1.2), and is quoted like the path
 * columns.
 */
final class ManifestCsv {

    /**
     * Every column the manifest carries, in order (ADR-104, ADR-112). A machine-read header, not
     * prose, so it names columns as the ledger names them rather than in the reader's plain words
     * (ADR-122).
     */
    private static final String HEADER = "occurrence_id,path,content_hash,winning_seed,"
            + "relevance_score,seed_partition,cluster,partition_order,cluster_order,copy,copy_outcome";

    private ManifestCsv() {}

    /**
     * The whole file: the header, then a row per survivor of {@code source}, each reported to {@code progress}
     * once it is written.
     *
     * @throws IllegalArgumentException where a survivor names a cluster the arrangement does not carry
     */
    static void write(Appendable csv, ArrangedSurvivors source, DeliverableProgress progress, Copies copies)
            throws IOException {
        csv.append(HEADER).append('\n');
        Map<SubtopicKey, Integer> sequence = sequenceOffsets(source);
        try {
            source.eachPageOfSurvivorsForTheManifest(page -> {
                Set<ClusterSlot> clusters = new LinkedHashSet<>();
                page.forEach(survivor -> clusters.add(survivor.slot()));
                Map<ClusterSlot, ArrangedCluster> places = clusters.isEmpty() ? Map.of() : source.placesOf(clusters);
                for (ListedSurvivor survivor : page) {
                    try {
                        append(csv, survivor, places, sequence, copies.outcomeOf(survivor), copies);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    progress.manifestRowWritten();
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * For each subtopic of a crowded topic, how many of the topic's clusters lie before it in the topic's one
     * sequence (ADR-409 section 10). A row's place in that sequence, the number its cluster's folder carries, is
     * this offset plus the cluster's order within its subtopic, so that the listing tells the topic's clusters apart
     * as the tree does. One number is held for each subtopic and a topic is read at a time; a topic with no
     * subtopics has none here and its rows keep the places the arrangement stored.
     */
    private static Map<SubtopicKey, Integer> sequenceOffsets(ArrangedSurvivors source) {
        Map<SubtopicKey, Integer> offsets = new java.util.HashMap<>();
        for (ListedTopic topic : source.topics()) {
            List<RecordedCluster> sequence = Deliverable.inOneSequence(source.clustersOf(topic));
            for (int place = 0; place < sequence.size(); place++) {
                ArrangedCluster cluster = sequence.get(place).cluster();
                if (cluster.subtopic().isPresent()) {
                    offsets.putIfAbsent(new SubtopicKey(cluster.topic(), cluster.subtopic().getAsInt()), place);
                }
            }
        }
        return offsets;
    }

    private record SubtopicKey(TopicKey topic, int subtopic) {}

    /** The survivor's place in its topic's one sequence where its topic is crowded, otherwise {@code stored}. */
    private static int placeInSequence(
            Map<SubtopicKey, Integer> offsets, ListedSurvivor survivor, ArrangedCluster cluster, int stored) {
        if (survivor.subtopic().isEmpty()) {
            return stored;
        }
        Integer offset = offsets.get(new SubtopicKey(survivor.topic(), survivor.subtopic().getAsInt()));
        return offset == null ? stored : offset + cluster.clusterOrder();
    }

    private static void append(
            Appendable csv, ListedSurvivor survivor, Map<ClusterSlot, ArrangedCluster> places,
            Map<SubtopicKey, Integer> sequence, CopyOutcome outcome, Copies copies)
            throws IOException {
        ArrangedCluster cluster = Optional.ofNullable(places.get(survivor.slot()))
                .orElseThrow(() ->
                        // ADR-105 and #175 §6: the arrangement is total over the survivors it was built from,
                        // so a survivor with no cluster row is a broken invariant, not a document the
                        // arrangement happens to be silent about. A 0,0 pair here would be two plausible
                        // numbers in a file built to be loaded straight into a table (ADR-104) -- the one
                        // shape of wrong this manifest exists to prevent.
                        new IllegalArgumentException("survivor " + survivor.occurrence().value()
                                + " names cluster " + survivor.clusterOrdinal() + " of topic "
                                + survivor.topic().seedPath() + " chunk " + survivor.topic().chunkIndex()
                                + ", which the arrangement does not carry"));
        csv.append(String.valueOf(survivor.occurrence().value()))
                .append(',')
                .append(cell(survivor.path().value()))
                .append(',')
                .append(survivor.contentHash())
                .append(',')
                // A member with no score on record has no winning seed on record (ADR-226): its cell is empty.
                .append(survivor.winningSeed().map(seed -> String.valueOf(seed.value())).orElse(""))
                .append(',')
                .append(String.valueOf(survivor.score()))
                .append(',')
                .append(cell(survivor.topicName()))
                .append(',')
                .append(String.valueOf(placeInSequence(sequence, survivor, cluster, survivor.clusterOrdinal())))
                .append(',')
                .append(String.valueOf(cluster.topicOrder()))
                .append(',')
                .append(String.valueOf(placeInSequence(sequence, survivor, cluster, cluster.clusterOrder())))
                .append(',')
                .append(outcome == CopyOutcome.COPIED ? cell(copies.pathOf(survivor)) : "")
                .append(',')
                .append(outcome.word())
                .append('\n');
    }

    /**
     * {@code field} as one cell of a path column: {@link #quoted} after {@link #shownAsText}.
     */
    static String cell(String field) {
        return quoted(shownAsText(field));
    }

    /**
     * {@code field} with a single quote before it where it begins with a character a spreadsheet reads as
     * the start of a formula, {@code =}, {@code +}, {@code -} or {@code @}, or with a tab or a carriage
     * return, which some read past to one; otherwise unchanged (ADR-235). A spreadsheet opening the listing
     * then shows the path as text and runs nothing; a parser reading the file gets the quote as the path's
     * first character, which is the price ADR-235 accepts.
     */
    static String shownAsText(String field) {
        if (!field.isEmpty() && "=+-@\t\r".indexOf(field.charAt(0)) >= 0) {
            return "'" + field;
        }
        return field;
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
    static String quoted(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
