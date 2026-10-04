package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** ADR-106's lead-document rule, held here by ADR-190. */
public final class LeadDocument {

    private LeadDocument() {}

    /**
     * The member of {@code documents} in {@code cluster}'s partition and at {@code cluster}'s ordinal with
     * the highest score. A member with a null score is weighed as 0.0; of equal scores the first in
     * {@code documents}' order wins; none at all is an {@link IllegalStateException}.
     */
    public static OccurrenceId of(ArrangedCluster cluster, List<ClusteredDocument> documents) {
        return documents.stream()
                .filter(document -> document.winningSeed().equals(cluster.winningSeed()))
                .filter(document -> document.clusterOrdinal() == cluster.ordinal())
                .max(Comparator.comparingDouble(document -> document.score() == null ? 0.0 : document.score()))
                .map(ClusteredDocument::occurrence)
                .orElseThrow(() -> new IllegalStateException(
                        "cluster " + cluster.ordinal() + " was arranged with no members"));
    }

    /**
     * The lead, by {@link #of}, and {@code ClusterLabel.derivedFrom(title.of(lead).orElse(null),
     * pathOf.apply(lead), cluster.ordinal())}. Asks {@code title} once and {@code pathOf} once, both of
     * the lead alone, title first.
     */
    public static LabelledCluster labelled(
            ArrangedCluster cluster,
            List<ClusteredDocument> documents,
            DocumentTitle title,
            Function<OccurrenceId, OccurrencePath> pathOf) {
        OccurrenceId lead = of(cluster, documents);
        String doclingTitle = title.of(lead).orElse(null);
        return new LabelledCluster(lead, ClusterLabel.derivedFrom(doclingTitle, pathOf.apply(lead), cluster.ordinal()));
    }
}
