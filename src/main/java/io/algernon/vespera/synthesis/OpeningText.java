package io.algernon.vespera.synthesis;

/**
 * The chunk a document opens with and its word count as recorded when it was cut (ADR-226).
 *
 * @param text the text the document opens with
 * @param wordCount how many words that text runs to
 */
public record OpeningText(String text, int wordCount) {}
