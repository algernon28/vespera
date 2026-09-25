package io.algernon.vespera.synthesis;

import java.util.Optional;

/**
 * A survivor's picture, as far as {@link Deliverable} needs to know about one (ADR-149, ADR-150,
 * #285): a plain value, like {@link ListedSurvivor}, that names no {@code extraction} type so this
 * module's dependency stays {@code ledger} alone (ADR-110).
 *
 * <p>{@code pixels} are the decoded bytes Docling's response carried, never re-encoded here:
 * {@link Deliverable} hashes and writes them through, and also decodes them to compute a difference
 * hash where they will decode (ADR-150 §3, §5). {@code inFurnitureLayer} is Docling's own claim that
 * the picture is page furniture (a header or a footer), one of the ways a picture is left out of the
 * tree. {@code caption} is Docling's resolved caption text, or {@code ""} where it gave none.
 * {@code place} is where on its page the picture sat, or empty where Docling gave no bounding box.
 *
 * @param mediaType the media type Docling's data URI declared for the picture
 * @param pixels the picture's decoded bytes
 * @param inFurnitureLayer whether Docling placed the picture in its own {@code furniture} content
 *     layer
 * @param caption the picture's caption text, or {@code ""} where Docling gave none
 * @param place where on its page the picture sat (ADR-150 §5), or empty where it has none
 */
public record ListedPicture(
        String mediaType, byte[] pixels, boolean inFurnitureLayer, String caption, Optional<ListedPicturePlace> place) {

    /** A picture with no known place: every caller from before this component existed. */
    public ListedPicture(String mediaType, byte[] pixels, boolean inFurnitureLayer, String caption) {
        this(mediaType, pixels, inFurnitureLayer, caption, Optional.empty());
    }
}
