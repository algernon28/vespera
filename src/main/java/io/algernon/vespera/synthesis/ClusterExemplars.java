package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where the walk asks for one cluster's documents and the path of its seed, on the precedent of {@link SurvivorPictures}
 * (ADR-149). {@code pipeline} implements it as a lambda (ADR-190).
 */
@FunctionalInterface
public interface ClusterExemplars {

    /**
     * Asked lazily: once for each cluster the walk reaches that is not already written, in the order of
     * the walk, and never for a cluster after the walk stopped. Gives the same answer each time it is
     * asked about the same cluster, and keeps nothing between calls. An empty list of exemplars means
     * the cluster has no document this run can send.
     */
    ClusterMaterial of(RecordedCluster cluster);

    /**
     * One cluster's documents as the call carries them: the chunk each opens with, and the score that
     * decides the order they are sent in (ADR-226, moving ADR-222's rule 4).
     *
     * <p>A document nothing was ever chunked from contributes nothing and is left out rather than sent
     * empty. That is a fact about that one document rather than a fault of the cluster or a reason to
     * stop the run, and a drop here is exactly why the documents the call carries are recorded one by
     * one under the ordinals they were given (ADR-133): the sent set is not in general a prefix of the
     * members, so nothing downstream could work out which document a citation meant.
     *
     * <p><b>A member carrying no score stops instead</b>, which is {@code Arrangement.partitionsOf}'s
     * rule one stage along and for its reason: the order these are sent in <em>is</em> the score, so a
     * document with none cannot be placed among them. Standing a zero in for it would send it last as
     * though it had been measured and found least relevant, which is a claim nobody made.
     *
     * <p>For each member, in the order given: its score is asked for, then its opening chunk, then
     * {@link GenerationProgress#nothingChunkedFrom} where there is none, then {@link
     * GenerationProgress#occurrenceOpened}. The exemplars come back in member order.
     */
    static List<Exemplar> gathered(
            List<OccurrenceId> members,
            Function<OccurrenceId, Optional<Double>> scoreOf,
            OpeningChunk openingChunk,
            GenerationProgress progress) {
        List<Exemplar> exemplars = new ArrayList<>();
        for (OccurrenceId member : members) {
            Double score = scoreOf.apply(member).orElse(null);
            if (score == null) {
                throw new IllegalStateException("occurrence " + member.value()
                        + " is in a cluster being written over but carries no relevance score, so the"
                        + " documents of that cluster cannot be put in order");
            }
            Optional<OpeningText> opening = openingChunk.of(member);
            if (opening.isEmpty()) {
                progress.nothingChunkedFrom(member);
            }
            progress.occurrenceOpened();
            if (opening.isEmpty()) {
                continue;
            }
            exemplars.add(new Exemplar(member, opening.get().text(), opening.get().wordCount(), score));
        }
        return List.copyOf(exemplars);
    }
}
