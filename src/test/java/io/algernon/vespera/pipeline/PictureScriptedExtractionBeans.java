package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A converter double for the one invocation test about a survivor's pictures (ADR-149): every document
 * converts to the same text, and two of them carry pictures in their responses.
 *
 * <p>The document named {@link #THE_DOCUMENT_WITH_A_DIAGRAM} carries {@link #THE_LETTERHEAD} and
 * {@link #THE_DIAGRAM}; the one named {@link #THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD} carries only
 * {@link #THE_LETTERHEAD}. So the letterhead's bytes recur across the documents the tree lists and the
 * diagram's do not, which is the one distinction the furniture rule draws. Every other file, the seed
 * included, gets the text and no picture at all.
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
                .otherwiseAnswering(response(TEXT_ONLY));
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
