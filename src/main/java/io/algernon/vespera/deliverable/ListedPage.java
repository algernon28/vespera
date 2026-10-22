package io.algernon.vespera.deliverable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * One page of the Confluence rendering, as the page list lists it (ADR-316 §2).
 *
 * @param key the page's stable key, {@code <generation run>:<place>} (ADR-316 §3)
 * @param parent the parent's key, {@code null} for the root page
 * @param title ADR-310's title
 * @param body the storage body's path from {@code confluence/}
 * @param bodySha256 the body's bytes' SHA-256, lower-case hexadecimal
 * @param files the files the page carries
 */
@JsonPropertyOrder({"key", "parent", "title", "body", "bodySha256", "files"})
@JsonInclude(JsonInclude.Include.ALWAYS)
record ListedPage(String key, String parent, String title, String body, String bodySha256, List<PageFile> files) {}
