package io.algernon.vespera.engine.synthesis;

/**
 * One passage a topic is named from: a stored seed chunk's text and its word count as the embedding step cached it
 * (ADR-403 section 3).
 */
public record NamingPassage(String text, int wordCount) {}
