package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrencePath;

/**
 * What stage 6a calls a cluster (ADR-106): the title of the cluster's highest-scoring document.
 *
 * <p>Derived rather than written, and derived before anything is generated, which is the whole point
 * of it — the gate ADR-022 puts between arrangement and generation exists to review a derivation, and
 * a reviewer checking a generated name would be reviewing the thing the gate exists to authorise.
 * The name a model writes is a cluster <em>title</em> and belongs to 6b.
 *
 * <p>The document count is not part of this value. It is stored beside the label and rendered with
 * it, because a number re-derived where it is read is a second place the arrangement is stated
 * (ADR-112).
 *
 * @param value the label's text
 */
public record ClusterLabel(String value) {

    public ClusterLabel {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a cluster label is never blank");
        }
    }

    /**
     * The label for a cluster whose highest-scoring document is at {@code leadDocument}.
     *
     * @param doclingTitle the lead document's Docling-labelled title
     * @param leadDocument the lead document's root-relative path (ADR-051)
     * @param clusterOrdinal the cluster's ordinal within its seed partition
     */
    public static ClusterLabel derivedFrom(String doclingTitle, OccurrencePath leadDocument, int clusterOrdinal) {
        if (doclingTitle != null && !doclingTitle.isBlank()) {
            return new ClusterLabel(doclingTitle);
        }
        String stem = filenameStemOf(leadDocument);
        if (!stem.isBlank()) {
            return new ClusterLabel(stem);
        }
        return new ClusterLabel(ORDINAL_ONLY.formatted(clusterOrdinal));
    }

    /**
     * The last tier, reached when the lead document has neither a title nor a filename that says
     * anything. The ordinal is spelled out rather than rendered bare, because a label reading
     * {@code 3} beside labels reading like documents is one a reader cannot place.
     */
    private static final String ORDINAL_ONLY = "Cluster %d";

    /**
     * The lead document's filename without its folders or its extension.
     *
     * <p>A stored path is separator-normalised to {@code /} already (ADR-051), so the last segment is
     * the filename whatever the walk found it on.
     *
     * <p>A name that is <em>only</em> an extension yields a blank stem rather than itself, which is
     * what makes the third tier reachable at all. Left the other way the chain would have had a tier
     * nothing could arrive at — the defect ADR-096 was written about one stage earlier.
     */
    private static String filenameStemOf(OccurrencePath path) {
        String filename = path.value().substring(path.value().lastIndexOf('/') + 1);
        int extension = filename.lastIndexOf('.');
        return extension < 0 ? filename : filename.substring(0, extension);
    }
}
