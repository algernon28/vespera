package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/** A cluster's lead document and the label derived from it, found together. */
public record LabelledCluster(OccurrenceId leadDocument, ClusterLabel label) {}
