package io.algernon.vespera.synthesis;

import java.util.List;

/**
 * What produced the deliverable tree, stated so {@code index.md} can be read years later with no
 * database beside it (ADR-103): the run in full, the reading of the archive it rests on, the archive
 * itself, and every profile value the run consumed.
 *
 * @param runId the 6b run's whole id, which is also the tree's own directory name
 * @param walk the walk the run read, which is what says when the links in the tree were true
 * @param corpusRoot the archive's own root, recorded once so a moved archive is re-pointed by
 *     replacing one line rather than every link in the tree
 * @param values every value {@code Profile} carries, named and stated as the operator wrote them
 */
public record DeliverableProvenance(String runId, long walk, String corpusRoot, List<NamedValue> values) {}
