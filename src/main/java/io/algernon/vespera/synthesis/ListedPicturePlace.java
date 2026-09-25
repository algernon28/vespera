package io.algernon.vespera.synthesis;

/**
 * Where on its page a survivor's picture sat, as far as {@link Deliverable} needs to know about one
 * (ADR-150 §5): a plain value naming no {@code extraction} type, mirroring that module's own {@code
 * PicturePlace} field for field (ADR-110) -- {@code pipeline} maps one onto the other.
 *
 * <p>The edges are compared only within one document, where Docling gives them in one coordinate
 * origin, so nothing here converts between origins.
 *
 * @param page the 1-indexed page the picture was cropped from
 * @param left the bounding box's left edge, as the response gave it
 * @param top the bounding box's top edge, as the response gave it
 * @param right the bounding box's right edge, as the response gave it
 * @param bottom the bounding box's bottom edge, as the response gave it
 */
public record ListedPicturePlace(int page, double left, double top, double right, double bottom) {

    /**
     * Whether {@code other} sits on a different page from this place and each of the four bounding-box
     * edges is within {@code points} of the other's (ADR-150 §3(d)): the same-place furniture rule's
     * whole geometric test, so {@link Deliverable} compares two places without naming an edge itself.
     */
    boolean withinPointsOf(ListedPicturePlace other, double points) {
        return page != other.page
                && Math.abs(left - other.left) <= points
                && Math.abs(top - other.top) <= points
                && Math.abs(right - other.right) <= points
                && Math.abs(bottom - other.bottom) <= points;
    }
}
