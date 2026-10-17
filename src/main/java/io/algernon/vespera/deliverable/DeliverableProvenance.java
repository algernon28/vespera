package io.algernon.vespera.deliverable;

import java.util.List;

/**
 * What produced the deliverable tree, stated so {@code index.html} can be read years later with no
 * database beside it (ADR-103): the run in full, the reading of the archive it rests on, the corpus
 * root, and every profile value the run consumed.
 *
 * @param runId the 6b run's whole id, which is also the tree's own directory name
 * @param walk the walk the run read, which is what says which file occurrences the copies were made from
 * @param corpusRoot the corpus root, recorded once: where each survivor's original was read from to
 *     make its copy (ADR-274 section 2); no entry in the tree links there
 * @param values every value {@code Profile} carries, named and stated as the operator wrote them
 */
public record DeliverableProvenance(String runId, long walk, String corpusRoot, List<NamedValue> values) {}
