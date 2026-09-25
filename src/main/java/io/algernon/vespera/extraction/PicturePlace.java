package io.algernon.vespera.extraction;

/**
 * Where on its page a picture sat, as Docling's first {@code prov} entry gave it (ADR-150 §5): the
 * page number and the four edges of its bounding box, in whatever coordinate origin that page's
 * entry used. Compared only within one document, where every entry shares one origin, so nothing
 * here converts between origins.
 *
 * <p>{@code synthesis}'s own {@code ListedPicturePlace} carries the same fields: neither module names
 * the other's type (ADR-110), so {@code pipeline} maps one onto the other, as it does for the picture
 * itself.
 *
 * @param page the 1-indexed page the picture was cropped from
 * @param left the bounding box's left edge, as the response gave it
 * @param top the bounding box's top edge, as the response gave it
 * @param right the bounding box's right edge, as the response gave it
 * @param bottom the bounding box's bottom edge, as the response gave it
 */
public record PicturePlace(int page, double left, double top, double right, double bottom) {}
