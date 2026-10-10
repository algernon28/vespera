package io.algernon.vespera.synthesis;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The manifest, {@code documents.csv}: one row per survivor, in the order given (ADR-104, ADR-112), and
 * the RFC 4180 quoting its two path columns need (ADR-136 §5).
 *
 * <p>It is written as its rows come, a page of survivors at a time, and nothing here holds the file whole
 * (ADR-223 section 7).
 *
 * <p>The CSV is the surrounding that answers to a parser, so its rule is a doubled quote and not a row of
 * {@link MarkdownSurroundings} (ADR-137 §4).
 */
final class ManifestCsv {

    /**
     * Every column the manifest carries, in order (ADR-104, ADR-112). A machine-read header, not
     * prose, so it names columns as the ledger names them rather than in the reader's plain words
     * (ADR-122).
     */
    private static final String HEADER = "occurrence_id,path,content_hash,winning_seed,"
            + "relevance_score,seed_partition,cluster,partition_order,cluster_order";

    private ManifestCsv() {}

    /** The whole file, with no row reported. */
    static void write(Appendable csv, ArrangedSurvivors source) throws IOException {
        write(csv, source, DeliverableProgress.NONE);
    }

    /**
     * The whole file: the header, then a row per survivor of {@code source}, each reported to {@code progress}
     * once it is written.
     *
     * @throws IllegalArgumentException where a survivor names a cluster the arrangement does not carry
     */
    static void write(Appendable csv, ArrangedSurvivors source, DeliverableProgress progress) throws IOException {
        csv.append(HEADER).append('\n');
        try {
            source.eachPageOfSurvivors(page -> {
                for (ListedSurvivor survivor : page) {
                    try {
                        append(csv, survivor, source);
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

    private static void append(Appendable csv, ListedSurvivor survivor, ArrangedSurvivors source) throws IOException {
        ArrangedCluster cluster = source.placeOf(ClusterSlot.of(survivor))
                .orElseThrow(() ->
                        // ADR-105 and #175 §6: the arrangement is total over the survivors it was built from,
                        // so a survivor with no cluster row is a broken invariant, not a document the
                        // arrangement happens to be silent about. A 0,0 pair here would be two plausible
                        // numbers in a file built to be loaded straight into a table (ADR-104) -- the one
                        // shape of wrong this manifest exists to prevent.
                        new IllegalArgumentException("survivor " + survivor.occurrence().value()
                                + " names cluster " + survivor.clusterOrdinal() + " of seed "
                                + survivor.winningSeed().value() + ", which the arrangement does not carry"));
        csv.append(String.valueOf(survivor.occurrence().value()))
                .append(',')
                .append(quoted(survivor.path().value()))
                .append(',')
                .append(survivor.contentHash())
                .append(',')
                .append(String.valueOf(survivor.winningSeed().value()))
                .append(',')
                .append(String.valueOf(survivor.score()))
                .append(',')
                .append(quoted(survivor.seedPath()))
                .append(',')
                .append(String.valueOf(survivor.clusterOrdinal()))
                .append(',')
                .append(String.valueOf(cluster.partitionOrder()))
                .append(',')
                .append(String.valueOf(cluster.clusterOrder()))
                .append('\n');
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
