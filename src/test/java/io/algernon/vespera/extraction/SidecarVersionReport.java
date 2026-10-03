package io.algernon.vespera.extraction;

import java.util.Map;

/**
 * The version report a stubbed {@link DoclingClient} answers with, in the shape the real sidecar's
 * {@code GET /version} has: component name to version, plus the name of the image the sidecar was built
 * as (ADR-179 §2).
 *
 * <p>One place for it, because every stub that stands in for the sidecar has to report the image, and
 * the image it has to report is the one {@code vespera.docling.image} names: a stub reporting no image,
 * or another one, is a sidecar the extractor identity refuses to be composed for (ADR-179 §3), and
 * every test behind it would stop at stage 2 for a reason it is not about.
 */
public final class SidecarVersionReport {

    /**
     * The entry the Docling image adds to {@code /version}, naming the image it was built as. Prefixed
     * so that no docling component can ever report under the same key.
     */
    public static final String IMAGE_ENTRY = "vespera-image";

    private SidecarVersionReport() {}

    /** A report from a sidecar built as {@code image}, on the versions the stubs have always reported. */
    public static Map<String, String> runningImage(String image) {
        return Map.of("docling-serve", "1.32.0", "docling", "2.124.0", IMAGE_ENTRY, image);
    }

    /**
     * A report from a sidecar that does not say which image it is: the shape every image built before
     * ADR-179 answers with, and the stock docling-serve image too.
     */
    public static Map<String, String> namingNoImage() {
        return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
    }
}
