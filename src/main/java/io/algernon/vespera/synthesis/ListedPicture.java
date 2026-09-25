package io.algernon.vespera.synthesis;

/**
 * A survivor's picture, as far as {@link Deliverable} needs to know about one (ADR-149, #285): a
 * plain value, like {@link ListedSurvivor}, that names no {@code extraction} type so this module's
 * dependency stays {@code ledger} alone (ADR-110).
 *
 * <p>{@code pixels} are the decoded bytes Docling's response carried, never re-encoded and never
 * decoded here: {@link Deliverable} only hashes and writes them through. {@code inFurnitureLayer} is
 * Docling's own claim that the picture is page furniture (a header or a footer), one of the two ways
 * a picture is left out of the tree. {@code caption} is Docling's resolved caption text, or {@code
 * ""} where it gave none.
 *
 * @param mediaType the media type Docling's data URI declared for the picture
 * @param pixels the picture's decoded bytes
 * @param inFurnitureLayer whether Docling placed the picture in its own {@code furniture} content
 *     layer
 * @param caption the picture's caption text, or {@code ""} where Docling gave none
 */
public record ListedPicture(String mediaType, byte[] pixels, boolean inFurnitureLayer, String caption) {}
