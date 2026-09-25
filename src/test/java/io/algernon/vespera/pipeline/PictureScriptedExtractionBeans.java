package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A converter double for the one invocation test about a survivor's pictures (ADR-149): every document
 * converts to the same text, and two of them carry pictures in their responses.
 *
 * <p>The document named {@link #THE_DOCUMENT_WITH_A_DIAGRAM} carries {@link #THE_LETTERHEAD} and
 * {@link #THE_DIAGRAM}; the one named {@link #THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD} carries only
 * {@link #THE_LETTERHEAD}. So the letterhead's bytes recur across the documents the tree lists and the
 * diagram's do not, which is the one distinction the furniture rule draws. {@link #THE_SCREENSHOT}
 * carries a crop of itself, and {@link #THE_REPORT} answers as Docling answers for a PDF, with real
 * pictures that each say where on their page they sat. Every other file, the seed included, gets the
 * text and no picture at all.
 *
 * <p>The text is the same in every response on purpose. It is what every other invocation test in this
 * package converts to, so stages 3 and 4 treat these documents as they treat theirs, and the answer
 * that differs between the two corpus documents is the pictures and nothing else.
 *
 * <p>A sibling of {@link SeedScriptedExtractionBeans} rather than an edit to it, because that fixture is
 * shared by most of this package and a picture added to its default answer would reach every tree
 * those classes write. {@code @TestConfiguration} for the reason that class documents: left plain, it
 * would sit inside the application's component scan.
 */
@TestConfiguration
class PictureScriptedExtractionBeans {

    /** The corpus document whose response carries a picture no other document carries. */
    static final String THE_DOCUMENT_WITH_A_DIAGRAM = "carries-a-diagram.txt";

    /** The corpus document whose response carries only the picture both documents carry. */
    static final String THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD = "carries-only-the-letterhead.txt";

    /** The title every response carries, so a cluster is named after a document's own title (ADR-106). */
    static final String STUBBED_TITLE = "A Stubbed Document";

    /**
     * A picture's pixels. Not a real PNG: the tree compares bytes and writes them through, and never
     * decodes a picture, so a distinct byte string proves what a valid image would.
     */
    static final byte[] THE_DIAGRAM = "the pixels of a diagram only one document holds".getBytes(StandardCharsets.UTF_8);

    /** The pixels of the letterhead both corpus documents carry. */
    static final byte[] THE_LETTERHEAD = "the pixels of a letterhead every document holds".getBytes(StandardCharsets.UTF_8);

    /**
     * A standalone image file in the corpus (ADR-150 §4): stage 1 reads its PNG signature as an image,
     * and its conversion carries one picture of its own, as Docling's crop of an image file does.
     */
    static final String THE_SCREENSHOT = "a-screenshot.png";

    /** The picture the screenshot's conversion carries: a crop of the image file itself. */
    static final byte[] THE_SCREENSHOTS_CROP =
            "the pixels docling cropped out of a standalone image file".getBytes(StandardCharsets.UTF_8);

    /**
     * A corpus document whose response is shaped as Docling answers for a PDF (ADR-150): every picture
     * carries a real PNG and its first {@code prov} entry, a page number and a bounding box. Named
     * {@code .txt} so stage 1 detects it as text, as it does the other two: what makes it a PDF's answer
     * is the response, which is all stage 6b reads.
     */
    static final String THE_REPORT = "a-report-with-a-chart.txt";

    /** The difference hash (ADR-150 §3) of the report's header picture as cropped from page one. */
    private static final long THE_HEADERS_HASH = 0x0123456789abcdefL;

    /**
     * How many bits the header's crop from page two differs by: more than the 2 a near-copy allows, so
     * only its place can make it furniture, and within the 8 two pictures at one place may differ by.
     */
    private static final int BITS_BETWEEN_THE_HEADERS_CROPS = 5;

    /** The header picture's size in pixels, the same on both pages. */
    private static final int HEADER_WIDTH = 290;

    private static final int HEADER_HEIGHT = 70;

    /** The chart's size in pixels. */
    private static final int CHART_WIDTH = 400;

    private static final int CHART_HEIGHT = 240;

    /** The report's header picture, as Docling cropped it from page one. */
    static final byte[] THE_HEADER_ON_PAGE_ONE = aPictureHashing(THE_HEADERS_HASH, HEADER_WIDTH, HEADER_HEIGHT);

    /**
     * The same header, cropped from page two: different bytes, a hash {@value
     * #BITS_BETWEEN_THE_HEADERS_CROPS} bits away.
     */
    static final byte[] THE_HEADER_ON_PAGE_TWO = aPictureHashing(
            THE_HEADERS_HASH ^ ((1L << BITS_BETWEEN_THE_HEADERS_CROPS) - 1), HEADER_WIDTH, HEADER_HEIGHT);

    /** The report's one chart, on page one, whose hash differs from the header's in every bit. */
    static final byte[] THE_CHART = aPictureHashing(~THE_HEADERS_HASH, CHART_WIDTH, CHART_HEIGHT);

    /**
     * The header's bounding box on page one, then on page two, as {@code l}, {@code t}, {@code r} and
     * {@code b}: each edge moves by less than a point, well within the 3 the same-place rule allows. The
     * two boxes' edges share their whole-number parts, and those differ from the page numbers, so a
     * reader that took an edge for the page, or the page for an edge, would see both crops on one page,
     * and the rule, which needs two pages, would keep them.
     */
    private static final String THE_HEADERS_BOX_ON_PAGE_ONE =
            "{\"l\":72.3,\"t\":770.6,\"r\":180.2,\"b\":740.4,\"coord_origin\":\"BOTTOMLEFT\"}";

    private static final String THE_HEADERS_BOX_ON_PAGE_TWO =
            "{\"l\":72.8,\"t\":770.1,\"r\":180.9,\"b\":740.7,\"coord_origin\":\"BOTTOMLEFT\"}";

    /** The chart's bounding box on page one, in the body of the page. */
    private static final String THE_CHARTS_BOX =
            "{\"l\":90.0,\"t\":600.0,\"r\":500.0,\"b\":350.0,\"coord_origin\":\"BOTTOMLEFT\"}";

    /** The nine columns and eight rows of the grid the difference hash is read from (ADR-150 §3). */
    private static final int HASH_COLUMNS = 9;

    private static final int HASH_ROWS = 8;

    /** Text alone: the response every file but the two picture-bearing documents gets. */
    private static final String TEXT_ONLY = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"" + STUBBED_TITLE + "\",\"label\":\"title\"},"
            + "{\"text\":\"stubbed but real content\"}]}}}";

    @Bean
    DoclingExtractor doclingExtractor(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new PathScriptedExtractor()
                .cachingInto(jdbcTemplate)
                .answering(THE_DOCUMENT_WITH_A_DIAGRAM, response(withPictures(THE_LETTERHEAD, THE_DIAGRAM)))
                .answering(THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD, response(withPictures(THE_LETTERHEAD)))
                .answering(THE_SCREENSHOT, response(withPictures(THE_SCREENSHOTS_CROP)))
                .answering(THE_REPORT, response(thePdfShapedReport()))
                .otherwiseAnswering(response(TEXT_ONLY));
    }

    /**
     * The report's response, in the shape Docling gives a PDF's: the same text, and in reading order the
     * header cropped from page one, the chart on page one, and the header cropped from page two, each a
     * base64 PNG with its page and box in its first {@code prov} entry.
     */
    private static String thePdfShapedReport() {
        return "{\"document\":{\"json_content\":{"
                + "\"body\":{\"children\":[{\"$ref\":\"#/texts/0\"},{\"$ref\":\"#/texts/1\"},"
                + "{\"$ref\":\"#/pictures/0\"},{\"$ref\":\"#/pictures/1\"},{\"$ref\":\"#/pictures/2\"}]},"
                + "\"texts\":["
                + "{\"text\":\"" + STUBBED_TITLE + "\",\"label\":\"title\"},"
                + "{\"text\":\"stubbed but real content\",\"label\":\"text\"}],"
                + "\"pictures\":["
                + placedPicture(THE_HEADER_ON_PAGE_ONE, 1, THE_HEADERS_BOX_ON_PAGE_ONE) + ","
                + placedPicture(THE_CHART, 1, THE_CHARTS_BOX) + ","
                + placedPicture(THE_HEADER_ON_PAGE_TWO, 2, THE_HEADERS_BOX_ON_PAGE_TWO)
                + "]}}}";
    }

    /** One entry of a PDF response's {@code pictures} array: body layer, one position, embedded pixels. */
    private static String placedPicture(byte[] pixels, int page, String box) {
        return "{\"content_layer\":\"body\",\"captions\":[],\"children\":[],"
                + "\"prov\":[{\"page_no\":" + page + ",\"bbox\":" + box + ",\"charspan\":[0,0]}],"
                + "\"image\":{\"mimetype\":\"image/png\",\"dpi\":144,"
                + "\"size\":{\"width\":0.0,\"height\":0.0},"
                + "\"uri\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(pixels) + "\"}}";
    }

    /**
     * A real PNG, {@code width} by {@code height}, whose difference hash (ADR-150 §3) is exactly
     * {@code bits}. It is grey, drawn as the hash's own grid with each pixel given its cell's level, so
     * every cell's mean is that level. Each row starts at 100 and steps up 10 for a set bit and down 10
     * for a clear one, so it stays between 20 and 180.
     */
    private static byte[] aPictureHashing(long bits, int width, int height) {
        int[][] levels = new int[HASH_ROWS][HASH_COLUMNS];
        for (int row = 0; row < HASH_ROWS; row++) {
            int level = 100;
            levels[row][0] = level;
            for (int column = 0; column < HASH_COLUMNS - 1; column++) {
                boolean rising = ((bits >>> (Long.SIZE - 1 - (row * (HASH_COLUMNS - 1) + column))) & 1L) == 1L;
                level += rising ? 10 : -10;
                levels[row][column + 1] = level;
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int level = levels[y * HASH_ROWS / height][x * HASH_COLUMNS / width];
                image.setRGB(x, y, (level << 16) | (level << 8) | level);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * The same text as {@link #TEXT_ONLY}, read through a {@code body} tree as a real response is, with
     * each of {@code pictures} referenced from that tree in order, in Docling's {@code body} layer, and
     * carried as a base64 {@code data:} URI.
     */
    private static String withPictures(byte[]... pictures) {
        StringBuilder children = new StringBuilder("{\"$ref\":\"#/texts/0\"},{\"$ref\":\"#/texts/1\"}");
        StringBuilder entries = new StringBuilder();
        for (int at = 0; at < pictures.length; at++) {
            children.append(",{\"$ref\":\"#/pictures/").append(at).append("\"}");
            if (at > 0) {
                entries.append(',');
            }
            entries.append("{\"content_layer\":\"body\",\"captions\":[],\"children\":[],")
                    .append("\"image\":{\"mimetype\":\"image/png\",\"uri\":\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(pictures[at]))
                    .append("\"}}");
        }
        return "{\"document\":{\"json_content\":{"
                + "\"body\":{\"children\":[" + children + "]},"
                + "\"texts\":["
                + "{\"text\":\"" + STUBBED_TITLE + "\",\"label\":\"title\"},"
                + "{\"text\":\"stubbed but real content\",\"label\":\"text\"}],"
                + "\"pictures\":[" + entries + "]}}}";
    }

    private static DoclingResponse response(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, rawResponse);
    }

    /** Never reached over HTTP; the version map is what the extractor identity is composed from. */
    @Bean
    DoclingClient doclingClient() {
        return new DoclingClient("unused") {
            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
            }
        };
    }
}
