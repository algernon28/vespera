package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * A person's recorded answer about one document (ADR-088): relevant to this seed set, or not.
 *
 * <p>Not a verdict — it removes nothing and no stage writes it — and not a measurement, because no
 * re-run can produce it a second time.
 *
 * @param occurrenceId the document judged
 * @param seedSet the seed folder it was judged against, which is what an operator means by a seed
 *     set and what stays the same across the re-walks census performs every invocation
 * @param relevant the answer itself
 * @param runId the run that put the question, kept as context and never as part of the identity
 * @param scoreShown what the person had in front of them when they answered
 * @param embedderIdentity what produced that score, so a later reader can tell whether two answers
 *     were given against numbers on the same scale
 */
public record RelevanceLabel(
        OccurrenceId occurrenceId,
        String seedSet,
        boolean relevant,
        String runId,
        double scoreShown,
        String embedderIdentity) {}
