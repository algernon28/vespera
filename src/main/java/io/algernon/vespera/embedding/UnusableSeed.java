package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One seed document that produced no text, and why (ADR-083).
 *
 * <p>Not a verdict, and the distinction is the decision rather than a naming preference: a verdict
 * removes a candidate from the survivor set, and a seed is not a candidate. What this records is a fact
 * about the seed folder for whoever has to go and fix it.
 *
 * @param occurrenceId which seed occurrence it was — an operator needs the file, not a count
 * @param reason why it produced no text, at stage 2's tier-1 bar exactly (ADR-070) and no stricter
 */
public record UnusableSeed(OccurrenceId occurrenceId, String reason) {}
