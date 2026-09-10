package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrencePath;

/**
 * A person's recorded answer about one document (ADR-088): relevant to this seed set, or not.
 *
 * <p>Not a verdict — it removes nothing and no stage writes it — and not a measurement, because no
 * re-run can produce it a second time.
 *
 * @param path the document judged, named the way ADR-051 names one: relative to the corpus root.
 *     That is the identity that survives the re-walk census performs every invocation, where an
 *     occurrence id does not (ADR-097)
 * @param seedSet the seed folder it was judged against, which is what an operator means by a seed
 *     set and what stays the same across the re-walks census performs every invocation
 * @param relevant the answer itself
 * @param runId the run that put the question, kept as context and never as part of the identity
 * @param scoreShown what the person had in front of them when they answered
 * @param embedderIdentity what produced that score, so a later reader can tell whether two answers
 *     were given against numbers on the same scale
 */
public record RelevanceLabel(
        OccurrencePath path,
        String seedSet,
        boolean relevant,
        String runId,
        double scoreShown,
        String embedderIdentity) {}
