package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pictures a Docling response carries (ADR-149, #285): each one whose pixels the response holds,
 * with its media type, whether Docling placed it in its page furniture, and its caption, in the
 * document's reading order.
 *
 * <p>Read here because it lives inside the converter's response and this module is the only one that
 * parses those (ADR-106's precedent for a title). It is handed out as plain values, which
 * {@code pipeline} maps onto {@code synthesis}'s own, so the terminal stage never names this module.
 *
 * <p>The fixtures are hand-written in the shape the measured cache holds: a {@code body} tree whose
 * children reference {@code texts}, {@code groups} and {@code pictures}, a {@code furniture} tree
 * beside it, and pictures whose {@code image.uri} is a base64 {@code data:} URI. Pixels are short byte
 * strings rather than real images, because nothing here decodes one.
 */
@Epic("Extraction")
@Feature("A document's pictures")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
class DocumentPictureTest {

    private static final String PNG = "image/png";

    private static final String JPEG = "image/jpeg";

    private static final byte[] FIRST_IN_READING_ORDER = bytes("the picture the body reaches first");

    private static final byte[] INSIDE_A_GROUP = bytes("the picture the body reaches through a group");

    private static final byte[] A_PHOTOGRAPH = bytes("a jpeg the body reaches last");

    private static final byte[] IN_THE_PAGE_FURNITURE = bytes("a page-header logo");

    private static final byte[] REACHED_BY_NOTHING = bytes("a picture no tree references");

    /** The caption's two text items, and what they read as once joined. */
    private static final String CAPTION_FIRST_PART = "Fig. 1";

    private static final String CAPTION_SECOND_PART = "The settlement flow";

    private static final String THE_JOINED_CAPTION = "Fig. 1 The settlement flow";

    /** How many of the fixture's seven pictures carry pixels in the response itself. */
    private static final int FIVE_WITH_PIXELS = 5;

    /**
     * Seven pictures. The body reaches picture 1, then picture 0 through a group, then picture 4 (no
     * image at all), picture 5 (an image only at a remote address) and picture 6 (a JPEG). The furniture
     * tree reaches picture 2. Nothing reaches picture 3.
     */
    private static final String A_DOCUMENT_WITH_PICTURES = """
            {"document":{"json_content":{
              "body":{"children":[
                {"$ref":"#/texts/0"},{"$ref":"#/pictures/1"},{"$ref":"#/groups/0"},
                {"$ref":"#/pictures/4"},{"$ref":"#/pictures/5"},{"$ref":"#/pictures/6"}]},
              "furniture":{"children":[{"$ref":"#/pictures/2"}]},
              "groups":[{"children":[{"$ref":"#/pictures/0"}]}],
              "texts":[
                {"text":"The flow is shown below.","label":"text"},
                {"text":"%s","label":"caption"},
                {"text":"%s","label":"caption"}],
              "pictures":[
                {"content_layer":"body","captions":[],"children":[],"image":{"mimetype":"image/png","uri":"%s"}},
                {"content_layer":"body","captions":[{"$ref":"#/texts/1"},{"$ref":"#/texts/2"}],"children":[],
                  "image":{"mimetype":"image/png","uri":"%s"}},
                {"content_layer":"furniture","captions":[],"children":[],"image":{"mimetype":"image/png","uri":"%s"}},
                {"content_layer":"body","captions":[],"children":[],"image":{"mimetype":"image/png","uri":"%s"}},
                {"content_layer":"body","captions":[],"children":[],
                  "prov":[{"page_no":1,"bbox":{"l":1,"t":2,"r":3,"b":4}}]},
                {"content_layer":"body","captions":[],"children":[],
                  "image":{"mimetype":"image/png","uri":"https://example.com/remote.png"}},
                {"content_layer":"body","captions":[],"children":[],"image":{"mimetype":"image/jpeg","uri":"%s"}}]}}}
            """.formatted(
                    CAPTION_FIRST_PART,
                    CAPTION_SECOND_PART,
                    dataUri(PNG, INSIDE_A_GROUP),
                    dataUri(PNG, FIRST_IN_READING_ORDER),
                    dataUri(PNG, IN_THE_PAGE_FURNITURE),
                    dataUri(PNG, REACHED_BY_NOTHING),
                    dataUri(JPEG, A_PHOTOGRAPH));

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("Every picture whose pixels the response carries is read, in the order the document gives them")
    void readsEveryPictureWithPixelsInReadingOrder() {
        List<DocumentPicture> pictures = DocumentPicture.allOf(A_DOCUMENT_WITH_PICTURES);

        claim(
                FIVE_WITH_PIXELS + " of the seven pictures come back: the two with no pixels in the response"
                        + " are left out, one because the converter located it and never cropped it, the other"
                        + " because its image lives at a remote address, which nothing here follows",
                () -> assertThat(pictures).hasSize(FIVE_WITH_PIXELS));
        claim(
                "and they come back in the document's reading order: the body tree first, a picture inside a"
                        + " group where the group sits, then the page furniture, then the one picture no tree"
                        + " reaches, last",
                () -> assertThat(pictures)
                        .extracting(picture -> new String(picture.pixels(), StandardCharsets.UTF_8))
                        .containsExactly(
                                text(FIRST_IN_READING_ORDER),
                                text(INSIDE_A_GROUP),
                                text(A_PHOTOGRAPH),
                                text(IN_THE_PAGE_FURNITURE),
                                text(REACHED_BY_NOTHING)));
        claim(
                "and each carries the media type its data URI declares, the JPEG included",
                () -> assertThat(pictures)
                        .extracting(DocumentPicture::mediaType)
                        .containsExactly(PNG, PNG, JPEG, PNG, PNG));
    }

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("A picture whose data does not decode, or which points at a file, is skipped and the rest still come back")
    void skipsAPictureThatDoesNotDecodeOrPointsAtAFile() {
        String response = """
                {"document":{"json_content":{
                  "body":{"children":[
                    {"$ref":"#/pictures/0"},{"$ref":"#/pictures/1"},{"$ref":"#/pictures/2"},{"$ref":"#/pictures/3"}]},
                  "pictures":[
                    {"content_layer":"body","captions":[],"children":[],"image":{"mimetype":"image/png","uri":"%s"}},
                    {"content_layer":"body","captions":[],"children":[],
                      "image":{"mimetype":"image/png","uri":"data:image/png;base64,@@ not base64 @@"}},
                    {"content_layer":"body","captions":[],"children":[],
                      "image":{"mimetype":"image/png","uri":"file:///C:/Windows/win.ini"}},
                    {"content_layer":"body","captions":[],"children":[],"image":{"mimetype":"image/png","uri":"%s"}}]}}}
                """.formatted(dataUri(PNG, FIRST_IN_READING_ORDER), dataUri(PNG, INSIDE_A_GROUP));

        claim(
                "the picture whose data URI carries text that is not base64 is skipped rather than failing"
                        + " the whole document, the one pointing at a file is skipped rather than read, and"
                        + " the two good pictures on either side of them still come back, in order",
                () -> assertThat(DocumentPicture.allOf(response))
                        .extracting(picture -> new String(picture.pixels(), StandardCharsets.UTF_8))
                        .containsExactly(text(FIRST_IN_READING_ORDER), text(INSIDE_A_GROUP)));
    }

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("A picture's caption is the converter's caption text, and a picture without one has none")
    void readsTheCaptionTheConverterGave() {
        List<DocumentPicture> pictures = DocumentPicture.allOf(A_DOCUMENT_WITH_PICTURES);

        claim(
                "the picture with a caption made of two text items carries both, joined by one space",
                () -> assertThat(pictures.getFirst().caption()).isEqualTo(THE_JOINED_CAPTION));
        claim(
                "and every picture without a caption carries an empty one, not a name made up for it",
                () -> assertThat(pictures.subList(1, pictures.size()))
                        .extracting(DocumentPicture::caption)
                        .containsOnly(""));
    }

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("A picture the converter placed in the page header or footer says so")
    void readsWhetherThePictureIsInThePageFurniture() {
        List<DocumentPicture> pictures = DocumentPicture.allOf(A_DOCUMENT_WITH_PICTURES);

        claim(
                "only the picture the converter placed in its furniture layer is marked as page furniture",
                () -> assertThat(pictures)
                        .extracting(DocumentPicture::inFurnitureLayer)
                        .containsExactly(false, false, false, true, false));
    }

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("A response with no body tree lists its pictures in the order the converter listed them")
    void readsThePictureListWhereThereIsNoBodyTree() {
        String response = """
                {"document":{"json_content":{"pictures":[
                  {"content_layer":"body","captions":[],"image":{"mimetype":"image/png","uri":"%s"}},
                  {"content_layer":"body","captions":[],"image":{"mimetype":"image/png","uri":"%s"}}]}}}
                """.formatted(dataUri(PNG, REACHED_BY_NOTHING), dataUri(PNG, INSIDE_A_GROUP));

        claim(
                "with no reading order to follow, the pictures come back in the order the converter listed"
                        + " them",
                () -> assertThat(DocumentPicture.allOf(response))
                        .extracting(picture -> new String(picture.pixels(), StandardCharsets.UTF_8))
                        .containsExactly(text(REACHED_BY_NOTHING), text(INSIDE_A_GROUP)));
    }

    @Test
    @Story("A document's pictures are read in its reading order")
    @DisplayName("A response carrying no document, or a document with no pictures, has no pictures")
    void hasNoPicturesWhereTheResponseCarriesNone() {
        claim(
                "a failed conversion, whose response carries no document at all, has no pictures rather than"
                        + " failing the reader",
                () -> assertThat(DocumentPicture.allOf("{\"status\":\"failure\",\"document\":{\"json_content\":null}}"))
                        .isEmpty());
        claim(
                "and a document with text and no pictures has none",
                () -> assertThat(DocumentPicture.allOf(
                                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"Only words.\"}]}}}"))
                        .isEmpty());
    }

    /**
     * ADR-150 §5: a picture Docling cropped from a page says where on the page it sat, which the
     * same-place rule compares within one document. Read from the first {@code prov} entry as the
     * response gives it, with no conversion between coordinate origins.
     */
    @Test
    @Story("A picture says where on its page it sat")
    @DisplayName("A picture cropped from a page carries its page and its box, and one with no position carries none")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void readsWhereOnItsPageAPictureSat() {
        String response = """
                {"document":{"json_content":{"pictures":[
                  {"content_layer":"body","captions":[],
                    "prov":[{"page_no":2,"bbox":{"l":60.5,"t":795.5,"r":205.0,"b":761.0,"coord_origin":"BOTTOMLEFT"}},
                            {"page_no":3,"bbox":{"l":1,"t":2,"r":3,"b":4,"coord_origin":"BOTTOMLEFT"}}],
                    "image":{"mimetype":"image/png","uri":"%s"}},
                  {"content_layer":"body","captions":[],"image":{"mimetype":"image/png","uri":"%s"}},
                  {"content_layer":"body","captions":[],"prov":[{"page_no":1}],
                    "image":{"mimetype":"image/png","uri":"%s"}}]}}}
                """.formatted(
                        dataUri(PNG, FIRST_IN_READING_ORDER), dataUri(PNG, INSIDE_A_GROUP), dataUri(PNG, A_PHOTOGRAPH));

        List<DocumentPicture> pictures = DocumentPicture.allOf(response);

        claim(
                "the cropped picture carries the page number and the four edges of its first position,"
                        + " exactly as the converter gave them",
                () -> assertThat(pictures.get(0).place())
                        .contains(new PicturePlace(2, 60.5, 795.5, 205.0, 761.0)));
        claim(
                "a picture with no position at all, as an office document's usually has, carries none",
                () -> assertThat(pictures.get(1).place()).isEmpty());
        claim(
                "and neither does one whose position names a page but no box, since a place without edges"
                        + " cannot be compared with another",
                () -> assertThat(pictures.get(2).place()).isEmpty());
    }

    private static String dataUri(String mediaType, byte[] pixels) {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(pixels);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] pixels) {
        return new String(pixels, StandardCharsets.UTF_8);
    }
}
