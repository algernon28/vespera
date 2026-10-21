package io.algernon.vespera.deliverable;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One file a page of the Confluence rendering carries, as the page list lists it (ADR-316 §2): its name on the page,
 * its path from {@code confluence/} with {@code /} between parts, its kind, its bytes and their SHA-256 in lower-case
 * hexadecimal.
 *
 * @param kind {@value #COPY}, {@value #PICTURE} or {@value #LISTING}
 */
@JsonPropertyOrder({"name", "path", "kind", "size", "sha256"})
record PageFile(String name, String path, String kind, long size, String sha256) {

    /** A survivor's copy, lying in the HTML tree (ADR-313). */
    static final String COPY = "copy";

    /** A picture's file, lying beside its page's body (ADR-314). */
    static final String PICTURE = "picture";

    /** {@code documents.csv}, on the root page (ADR-315 §3). */
    static final String LISTING = "listing";
}
