package io.algernon.vespera.extraction;

import java.util.List;

/**
 * One Docling conversion response, deserialised from {@code /v1/convert/file} (ADR-070, ADR-071):
 * the three independent signals a response carries — {@code status}, {@code errors[]},
 * {@code confidence} — plus {@code processing_time}, and the whole response body verbatim.
 *
 * <p>{@code rawResponse} is what makes this "the full response payload, not just extracted text"
 * (ADR-070's consequence, repeated in ADR-071): the document's exported content (requested as the
 * JSON/{@code DoclingDocument} export, per ADR-071's call-shape decision) lives inside it, so a later
 * pass — metrics, degeneracy tiers, chunking — reads it back from the cache without a second Docling
 * call. This module does not itself parse that far; deciding what to extract from it is the
 * derived-metrics and chunking tickets' job.
 *
 * @param status Docling's top-level verdict on the call itself
 * @param errors the categorised failures the call reported, empty on a clean {@code success}
 * @param processingTimeSeconds how long Docling itself spent, as reported in the response
 * @param confidence the quality snapshot, or {@code null} when Docling never reported one (a
 *     response without a {@code confidence} object at all — distinct from a present object whose
 *     scores are all null, which {@link ConfidenceScores} itself carries)
 * @param rawResponse the response body exactly as received, so nothing Docling sent is lost to a
 *     narrower Java shape
 */
public record DoclingResponse(
        ConversionStatus status,
        List<DoclingError> errors,
        double processingTimeSeconds,
        ConfidenceScores confidence,
        String rawResponse) {}
