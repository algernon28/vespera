package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One document as a call carries it (ADR-108): the chunk it opens with, and how close it sits to the
 * seed its cluster belongs to.
 *
 * <p>The opening chunk rather than the whole document, and rather than a summary of it. A document
 * cut by {@code HybridChunker} opens on a heading, so what arrives in the call is titled rather than
 * starting mid-paragraph — and it costs a query against chunks already cut rather than a second
 * pass over the text.
 *
 * <p>The score is here because it decides the order the documents are sent in, which is the only
 * relevance signal this system has: the same one that decided each of them survived at all.
 *
 * <p>The word count travels beside the text rather than being counted from it, and it is the count
 * stored against that very chunk when it was cut. This project has no tokenizer and the serving
 * runtime is what counts tokens (ADR-091), so what a call can afford is reckoned in words — and a
 * count taken from a different cutting of the document would budget for text nobody is sending.
 *
 * <p>Which document it is travels with it because the ordinal it is sent under has to be written
 * down against it (ADR-133): a citation is an ordinal into the documents one call sent, and nothing
 * downstream could recover which document that was from the text alone.
 *
 * @param occurrence which document this is, carried so what the call sent can be recorded under the
 *     number the model was given for it (ADR-133)
 * @param leadingChunk the text the document opens with
 * @param wordCount how many words that text runs to, as recorded when it was cut
 * @param score how close it sits to its seed
 */
public record Exemplar(OccurrenceId occurrence, String leadingChunk, int wordCount, double score) {}
