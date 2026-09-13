package io.algernon.vespera.extraction;

import java.util.Optional;

/**
 * A document's own title, as Docling labelled it (ADR-106).
 *
 * <p>Read here because it lives inside the converter's response and this module is the only one that
 * parses those, and handed out as a plain string — which is what lets the terminal stages name a
 * cluster after a document without naming this module (ADR-110).
 *
 * <p><b>It reads the labelled item, never the first line.</b> {@code HybridChunker} already treats
 * {@code title} and {@code section_header} alike, because for chunking any heading will do as leading
 * context. Here they are not alike: a section header names a part of a document, and a cluster named
 * after one would be named after that part.
 *
 * <p>Absent is the common case rather than an error — a plain {@code .txt} file and a scan that OCR'd
 * into undifferentiated body text carry no title item at all — so this returns an empty optional and
 * the caller supplies its own answer.
 */
public final class DocumentTitle {

    /** Docling's own label for a document title, distinct from {@code section_header}. */
    private static final String TITLE_LABEL = "title";

    private DocumentTitle() {}

    /**
     * The title {@code rawDoclingResponse} carries, or empty where it carries none.
     *
     * <p>The first of two, because document order is the only order available and a second title is an
     * appendix or a bound-in second document rather than a better name for the first.
     */
    public static Optional<String> of(String rawDoclingResponse) {
        return DoclingDocumentTexts.parse(rawDoclingResponse).stream()
                .filter(item -> TITLE_LABEL.equals(item.label()))
                .map(DocumentText::text)
                .map(String::trim)
                .filter(title -> !title.isEmpty())
                .findFirst();
    }
}
