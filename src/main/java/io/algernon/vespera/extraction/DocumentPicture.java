package io.algernon.vespera.extraction;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * One picture a Docling response carries, with its pixels (ADR-149, #285): the survivor's own
 * evidence that a chart, a diagram or a screenshot said something its extracted text does not.
 *
 * <p>Read here because it lives inside the converter's response and this module is the only one that
 * parses those (ADR-041, ADR-106's precedent for a title), and handed out as a plain value that names
 * no {@code synthesis} type -- {@code pipeline} maps each one onto {@code synthesis}'s own {@code
 * ListedPicture} (ADR-110).
 *
 * <p><b>Only a picture the response itself carries pixels for.</b> {@code image.uri} is read only
 * where it is a base64 {@code data:} URI; an {@code http:} or {@code file:} address is skipped rather
 * than followed, because nothing here fetches anything, and a picture with no {@code image} at all --
 * the ordinary case for Docling's PDF pipeline, which locates a picture with a bounding box and never
 * crops it -- is skipped the same way (ADR-149 §2).
 *
 * <p><b>Reading order is ADR-145's walk</b>, read again here for pictures rather than text items: the
 * {@code body} tree first, then {@code furniture}, a picture inside a group read where the group sits.
 * A picture the walk never reaches -- Docling emits some that no reference names -- follows, in the
 * order the converter's own {@code pictures} array gives them. A response with no {@code body} lists
 * every picture in that array order (ADR-149 §8). {@code DoclingDocumentTexts}'s own reading, and the
 * text it produces, are untouched: this is a second, independent walk of the same tree.
 *
 * @param mediaType the media type the picture's data URI declares
 * @param pixels the picture's decoded bytes
 * @param inFurnitureLayer whether Docling placed the picture in its own {@code furniture} content
 *     layer -- its own page-header and page-footer furniture
 * @param caption the picture's resolved caption texts, joined by a single space and trimmed, or
 *     {@code ""} where Docling gave none
 * @param place where on its page the picture sat (ADR-150 §5), read from its first {@code prov}
 *     entry, or empty where there is no such entry or it carries no bounding box
 */
public record DocumentPicture(
        String mediaType, byte[] pixels, boolean inFurnitureLayer, String caption, Optional<PicturePlace> place) {

    /**
     * A picture with no known place (ADR-150 §5): every caller from before this component existed, and
     * every test fixture that has no reason to state one.
     */
    public DocumentPicture(String mediaType, byte[] pixels, boolean inFurnitureLayer, String caption) {
        this(mediaType, pixels, inFurnitureLayer, caption, Optional.empty());
    }

    /** The prefix a data URI carrying base64-encoded bytes starts with. */
    private static final String DATA_URI_PREFIX = "data:";

    /** What separates a data URI's declared media type from its base64 payload. */
    private static final String BASE64_MARKER = ";base64,";

    /** Docling's own name for a picture's page-header/page-footer content layer. */
    private static final String FURNITURE_LAYER = "furniture";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Every picture {@code rawDoclingResponse} carries pixels for, in the reading order of ADR-149 §8,
     * or empty where the response carries no document, no pictures, or none with pixels.
     */
    public static List<DocumentPicture> allOf(String rawDoclingResponse) {
        JsonNode content = MAPPER.readTree(rawDoclingResponse).path("document").path("json_content");
        JsonNode pictures = content.path("pictures");
        if (!pictures.isArray() || pictures.isEmpty()) {
            return List.of();
        }
        List<DocumentPicture> result = new ArrayList<>();
        for (int index : readingOrder(content, pictures.size())) {
            documentPictureAt(pictures.path(index), content).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * The index of every picture in {@code pictures}, in reading order (ADR-149 §8): the walk's own
     * order first, then any index the walk never reached, in the converter's own array order. A
     * response with no {@code body} tree gives the array order alone, as {@code DoclingDocumentTexts}
     * falls back for text.
     */
    private static List<Integer> readingOrder(JsonNode content, int pictureCount) {
        List<Integer> order = new ArrayList<>();
        if (content.path("body").path("children").isArray()) {
            Set<String> read = new HashSet<>();
            collectPictureRefs(content, content.path("body"), order, read);
            collectPictureRefs(content, content.path("furniture"), order, read);
            for (int index = 0; index < pictureCount; index++) {
                if (!read.contains("#/pictures/" + index)) {
                    order.add(index);
                }
            }
        } else {
            for (int index = 0; index < pictureCount; index++) {
                order.add(index);
            }
        }
        return order;
    }

    private static void collectPictureRefs(JsonNode content, JsonNode node, List<Integer> order, Set<String> read) {
        for (JsonNode child : node.path("children")) {
            String ref = child.path("$ref").asString("");
            // A node reachable twice is read the first time only, exactly as the text walk reads it.
            if (!read.add(ref)) {
                continue;
            }
            JsonNode item = resolve(content, ref);
            if (item.isMissingNode()) {
                continue;
            }
            if (ref.startsWith("#/pictures/")) {
                order.add(Integer.parseInt(ref.substring("#/pictures/".length())));
            }
            if (!ref.startsWith("#/tables/")) {
                collectPictureRefs(content, item, order, read);
            }
        }
    }

    /** {@code #/pictures/3} is the fourth entry of {@code content.pictures}; anything else resolves to nothing. */
    private static JsonNode resolve(JsonNode content, String ref) {
        String[] parts = ref.split("/");
        if (parts.length != 3 || !"#".equals(parts[0]) || !parts[2].chars().allMatch(Character::isDigit)) {
            return MissingNode.getInstance();
        }
        return content.path(parts[1]).path(Integer.parseInt(parts[2]));
    }

    /** {@code picture}, or empty where it carries no data URI. */
    private static Optional<DocumentPicture> documentPictureAt(JsonNode picture, JsonNode content) {
        String uri = picture.path("image").path("uri").asString("");
        if (!uri.startsWith(DATA_URI_PREFIX) || !uri.contains(BASE64_MARKER)) {
            return Optional.empty();
        }
        int markerAt = uri.indexOf(BASE64_MARKER);
        String mediaType = uri.substring(DATA_URI_PREFIX.length(), markerAt);
        byte[] pixels;
        try {
            pixels = Base64.getDecoder().decode(uri.substring(markerAt + BASE64_MARKER.length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        boolean inFurnitureLayer = FURNITURE_LAYER.equals(picture.path("content_layer").asString(""));
        String caption = captionOf(picture, content);
        Optional<PicturePlace> place = placeOf(picture);
        return Optional.of(new DocumentPicture(mediaType, pixels, inFurnitureLayer, caption, place));
    }

    /**
     * {@code picture}'s place on its page (ADR-150 §5), read from its first {@code prov} entry, or
     * empty where there is no such entry or it carries no bounding box. The edges are read exactly as
     * the response gives them, with no conversion between coordinate origins: the same-place rule
     * compares them only within one document, where every entry shares one origin.
     */
    private static Optional<PicturePlace> placeOf(JsonNode picture) {
        JsonNode prov = picture.path("prov").path(0);
        if (prov.isMissingNode() || prov.isNull() || !prov.isObject()) {
            return Optional.empty();
        }
        JsonNode bbox = prov.path("bbox");
        if (bbox.isMissingNode() || bbox.isNull() || !bbox.isObject()) {
            return Optional.empty();
        }
        JsonNode pageNo = prov.path("page_no");
        JsonNode left = bbox.path("l");
        JsonNode top = bbox.path("t");
        JsonNode right = bbox.path("r");
        JsonNode bottom = bbox.path("b");
        if (!pageNo.isNumber() || !left.isNumber() || !top.isNumber() || !right.isNumber() || !bottom.isNumber()) {
            return Optional.empty();
        }
        return Optional.of(
                new PicturePlace(pageNo.asInt(), left.asDouble(), top.asDouble(), right.asDouble(), bottom.asDouble()));
    }

    /** {@code picture}'s captions, each resolved to its text item and joined by a single space. */
    private static String captionOf(JsonNode picture, JsonNode content) {
        List<String> texts = new ArrayList<>();
        for (JsonNode captionRef : picture.path("captions")) {
            String ref = captionRef.path("$ref").asString("");
            JsonNode item = resolve(content, ref);
            String text = item.path("text").asString("");
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return texts.stream().collect(Collectors.joining(" ")).trim();
    }
}
