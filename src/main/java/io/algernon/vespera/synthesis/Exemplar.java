package io.algernon.vespera.synthesis;

/**
 * One document as a call carries it (ADR-108): the chunk it opens with, and how close it sits to the
 * seed its group belongs to.
 *
 * <p>The opening chunk rather than the whole document, and rather than a summary of it. A document
 * cut by {@code HybridChunker} opens on a heading, so what arrives in the call is titled rather than
 * starting mid-paragraph — and it costs a query against chunks already cut rather than a second
 * pass over the text.
 *
 * <p>The score is here because it decides the order the documents are sent in, which is the only
 * relevance signal this system has: the same one that decided each of them survived at all.
 *
 * @param leadingChunk the text the document opens with
 * @param score how close it sits to its seed
 */
public record Exemplar(String leadingChunk, double score) {}
