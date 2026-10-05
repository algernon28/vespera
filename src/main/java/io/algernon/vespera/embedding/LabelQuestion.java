package io.algernon.vespera.embedding;

import java.util.Optional;

/**
 * One question put to a {@link RelevanceLabeller}: what a person labelling is shown beside an entry
 * (ADR-088, ADR-197 §2).
 *
 * @param path the document, relative to the corpus root (ADR-051)
 * @param winningSeedPath the seed its score was taken against
 * @param opening the first of its text, or empty where it could not be read
 */
public record LabelQuestion(String path, String winningSeedPath, Optional<String> opening) {}
