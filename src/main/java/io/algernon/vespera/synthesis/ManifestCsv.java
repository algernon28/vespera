package io.algernon.vespera.synthesis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The manifest, {@code documents.csv}: one row per survivor, in the order given (ADR-104, ADR-112), and
 * the RFC 4180 quoting its two path columns need (ADR-136 §5).
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

    /**
     * The whole file: the header, then a row per survivor.
     *
     * @throws IllegalArgumentException where a survivor names a cluster {@code arrangement} does not
     *     carry
     */
    static String contents(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        Map<ClusterSlot, ArrangedCluster> orderByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            orderByCluster.put(ClusterSlot.of(recorded), recorded.cluster());
        }

        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (ListedSurvivor survivor : survivors) {
            ArrangedCluster cluster = orderByCluster.get(ClusterSlot.of(survivor));
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
        return csv.toString();
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
