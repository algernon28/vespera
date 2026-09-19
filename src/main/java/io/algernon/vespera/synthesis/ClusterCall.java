package io.algernon.vespera.synthesis;

import java.util.List;

/**
 * One cluster's worth of call (ADR-108, ADR-110): what it is called, what it sits under, and the
 * documents it is written from.
 *
 * <p>Handed over whole rather than gathered here. Everything in it is read where the passes are
 * assembled — this module may reach neither the table the clusters live in nor the one the chunks do —
 * which is what leaves the call itself testable with no database under it.
 *
 * @param label what stage 6a named the cluster, which is what the call tells the model it is reading
 * @param seedPath the seed document whose partition the cluster sits in, named so the model knows what
 *     the cluster is a cluster of
 * @param exemplars the documents to write from, in any order: the call sends them in the order the
 *     scores give them, rather than the order they arrive in
 */
public record ClusterCall(String label, String seedPath, List<Exemplar> exemplars) {}
