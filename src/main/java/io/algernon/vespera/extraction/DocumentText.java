package io.algernon.vespera.extraction;

/**
 * One structural text item read off a Docling response's {@code document.json_content.texts}
 * (ADR-029): the unit {@link HybridChunker} chunks over, since it is Docling's own structural
 * segmentation rather than an arbitrary slice of the document's text.
 *
 * @param text the item's text
 * @param label Docling's structural label for it (e.g. {@code "title"}, {@code "section_header"},
 *     {@code "paragraph"}, {@code "list_item"}, {@code "page_header"}, {@code "page_footer"})
 */
record DocumentText(String text, String label) {}
