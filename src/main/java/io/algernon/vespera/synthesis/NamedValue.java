package io.algernon.vespera.synthesis;

/**
 * One profile value the run consumed, carried as a plain key-value pair so {@link Deliverable} can
 * state it in {@code index.md} without knowing {@code Profile} exists (ADR-110).
 *
 * @param key the key exactly as the profile names it
 * @param value what it held when the run read it
 */
public record NamedValue(String key, String value) {}
