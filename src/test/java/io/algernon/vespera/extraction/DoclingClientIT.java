package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.TestcontainersConfiguration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link DoclingClient} against the real {@code docling-serve} sidecar (ADR-071): one synchronous
 * call per document, and a response this module can actually read back.
 *
 * <p>Every other test of this ticket's behaviour — the cache hit that issues no call, the changed
 * extractor identity that issues a new one, the client-side timeout — runs against a stub, because
 * none of them needs Docling to be real. This one does, and it is the only one: it is the test that
 * would catch a request shape Docling rejects, a field name that does not match the wire, or an enum
 * value Docling emits that {@link ConversionStatus} does not carry — all of which a stub built from
 * the same beliefs as the code would happily confirm.
 *
 * <p>An integration test, so {@code *IT} and failsafe rather than surefire (ADR-052's conventions):
 * it needs a Docker daemon, and {@link TestcontainersConfiguration} starts the sidecar for it.
 *
 * <p>Both fixtures are built inside the test rather than committed as binaries (ADR-063): they are
 * genuine files — a real single-page PDF with a real text-showing content stream, and a real
 * minimal-but-valid OOXML package — because Docling has to parse them, but nothing about either is
 * worth keeping in git when the bytes that produce them read as code.
 *
 * <p>Two formats rather than one, and these two: {@code .pdf} goes through Docling's paginated
 * pipeline and {@code .docx} through its simple one (ADR-070's confidence finding rests on exactly
 * that split), so a single format would leave the other pipeline's response shape unread.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("The Docling client")
@Issue("46")
@Link(name = "ADR-010", url = Adr.EXTRACTION_VIA_DOCLING, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
@Link(name = "ADR-063", url = Adr.FIXTURES_ARE_GENERATED_IN_TEST, type = "adr")
class DoclingClientIT {

    /**
     * The one word the PDF fixture's only line of text is written with. Distinctive and single, so
     * finding it in the response proves the document's own content came back — a phrase could be
     * broken up by however the converter joins text cells, one word cannot.
     */
    private static final String PDF_MARKER_WORD = "Chiaroscuro";

    /** The same, for the {@code .docx} fixture, and deliberately a different word from the PDF's. */
    private static final String DOCX_MARKER_WORD = "Palimpsest";

    /** The one distinctive word the extension-less prose fixture is written with. */
    private static final String PROSE_MARKER_WORD = "Recrudescence";

    /** The distinctive word the older Word fixture carries, saved from a {@code .docx} as a {@code .doc}. */
    private static final String DOC_MARKER_WORD = "Sesquipedalian";

    /** The distinctive word the older PowerPoint fixture carries, on its one slide. */
    private static final String PPT_MARKER_WORD = "Quiddity";

    /** How a picture's pixels begin when they are written into the answer as a PNG (ADR-150). */
    private static final String EMBEDDED_PNG_PREFIX = "data:image/png;base64,";

    /** The chart image's width in pixels, drawn at 288 points wide on the page. */
    private static final int CHART_WIDTH = 240;

    /** The chart image's height in pixels, drawn at 192 points high on the page. */
    private static final int CHART_HEIGHT = 160;

    /** The chart's four bars: left edge, height, and red, green and blue, each bar 30 pixels wide. */
    private static final int[][] CHART_BARS = {
        {40, 60, 200, 30, 30}, {90, 110, 30, 120, 200}, {140, 40, 40, 160, 60}, {190, 130, 230, 160, 20}
    };

    @Autowired
    private DoclingClient client;

    @Autowired
    private GenericContainer<?> doclingServeContainer;

    /**
     * ADR-147: the sidecar carries LibreOffice, which docling-serve calls to turn a {@code .doc} into a
     * {@code .docx} before converting it. On the stock image this very file came back {@code failure}
     * with an uncategorised error, and every older Word document in the archive with it.
     *
     * <p>The fixture is made in the test, as ADR-063 asks, by the one thing on hand that writes the
     * format: the sidecar's own LibreOffice, saving a generated {@code .docx} as a {@code .doc}.
     */
    @Test
    @Story("One call converts one document")
    @DisplayName("An older Word document converts, and carries its own text")
    @Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
    void convertsAnOlderWordDocument(@TempDir Path dir) throws Exception {
        Path doc = savedByLibreOfficeAs(aDocx(dir.resolve("older.docx"), DOC_MARKER_WORD), "doc", dir);

        DoclingResponse response = client.convert(doc, DetectedFormat.OLE_COMPOUND, DetectedSubtype.LEGACY_WORD);

        claim(
                "the service reports the conversion succeeded, where the image without LibreOffice refused it",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "and the body carries the document's own word, so its text came through the conversion",
                () -> assertThat(response.rawResponse()).contains(DOC_MARKER_WORD));
    }

    /** The same for an older PowerPoint file, which docling-serve turns into a {@code .pptx} first. */
    @Test
    @Story("One call converts one document")
    @DisplayName("An older PowerPoint presentation converts, and carries its own text")
    @Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
    void convertsAnOlderPowerPointPresentation(@TempDir Path dir) throws Exception {
        Path ppt = savedByLibreOfficeAs(anOdp(dir.resolve("older.odp"), PPT_MARKER_WORD), "ppt", dir);

        DoclingResponse response =
                client.convert(ppt, DetectedFormat.OLE_COMPOUND, DetectedSubtype.LEGACY_PRESENTATION);

        claim(
                "the service reports the conversion succeeded",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "and the body carries the slide's own word",
                () -> assertThat(response.rawResponse()).contains(PPT_MARKER_WORD));
    }

    @Test
    @Story("One call converts one document")
    @DisplayName("A real PDF converted by the running document service comes back readable, content and all")
    void convertsARealPdf(@TempDir Path dir) throws IOException {
        DoclingResponse response = client.convert(aRealPdf(dir.resolve("one-page.pdf")), DetectedFormat.PDF, null);

        claim(
                "the service reports the conversion succeeded, which is the only status a well-formed"
                        + " one-page document should ever produce",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "a successful conversion reports no errors at all; anything listed here would be a"
                        + " failure the client had quietly accepted",
                () -> assertThat(response.errors()).isEmpty());
        claim(
                "the response body carries the one distinctive word the fixture document was written"
                        + " with, so what came back is this document's content and not an empty shell",
                () -> assertThat(response.rawResponse()).contains(PDF_MARKER_WORD));
        claim(
                "the reported processing time is above zero, so the field was read off the response"
                        + " rather than left at the default a missing field would leave",
                () -> assertThat(response.processingTimeSeconds()).isGreaterThan(0.0));
        claim(
                "a page-based document comes back with a measured overall quality score, since quality"
                        + " is derived per page and this document has a page",
                () -> assertThat(response.confidence().meanScore()).isNotNull());
    }

    @Test
    @Story("One call converts one document")
    @DisplayName("A real Word document converted by the running document service comes back readable too")
    void convertsARealDocx(@TempDir Path dir) throws IOException {
        DoclingResponse response = client.convert(aRealDocx(dir.resolve("one-paragraph.docx")), DetectedFormat.WORDPROCESSING, null);

        claim(
                "the service reports the conversion succeeded, for the second of the two document"
                        + " formats it converts by a different internal route",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "a successful conversion reports no errors at all; anything listed here would be a"
                        + " failure the client had quietly accepted",
                () -> assertThat(response.errors()).isEmpty());
        claim(
                "the response body carries the one distinctive word the fixture document was written"
                        + " with, so what came back is this document's content and not an empty shell",
                () -> assertThat(response.rawResponse()).contains(DOCX_MARKER_WORD));
        claim(
                "a Word document is converted by the route that has no pages to measure, so every"
                        + " quality score comes back absent and both grades come back unspecified —"
                        + " absent meaning not measured, never meaning poor",
                () -> assertThat(response.confidence())
                        .isEqualTo(new ConfidenceScores(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                QualityGrade.UNSPECIFIED,
                                QualityGrade.UNSPECIFIED)));
    }

    @Test
    @Story("The sidecar says what it is built from")
    @DisplayName("The running document service reports the component versions the extractor identity is built from")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void reportsTheComponentsItIsBuiltFrom() {
        Map<String, String> version = client.version();

        claim(
                "the running service names the wrapper that serves the endpoint and the library that"
                        + " actually converts documents as separate entries, because they move separately and"
                        + " either one changes what a conversion produces",
                () -> assertThat(version).containsKeys("docling-serve", "docling"));
        claim(
                "and every version it reports is an actual value rather than a blank, since a blank one"
                        + " folded into an extractor identity would claim two different builds were the same",
                () -> assertThat(version.values()).allSatisfy(reported -> assertThat(reported)
                        .isNotBlank()));
    }

    @Test
    @Story("The conversion pins what it asks for")
    @DisplayName("The running document service accepts the OCR engine this client names, rather than refusing it")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void acceptsThePinnedOcrEngine(@TempDir Path dir) throws IOException {
        DoclingResponse response = client.convert(aRealPdf(dir.resolve("pinned-engine.pdf")), DetectedFormat.PDF, null);

        claim(
                "naming the OCR engine is a request the service actually honours, and this is the only"
                        + " test that can say so: a stub built from the same beliefs as the code would accept"
                        + " an engine the real service refuses, and the refusal arrives as a status code whose"
                        + " reason appears only in the service's own log",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "and the document still converts to its own content under that engine, so pinning it"
                        + " bought identity rather than costing extraction",
                () -> assertThat(response.rawResponse()).contains(PDF_MARKER_WORD));
    }

    /**
     * ADR-150: a PDF's pictures come back with their pixels, because the client asks for them. Under
     * the sidecar's default picture mode the same file comes back with the picture located on the page
     * and no pixels at all, which is what every PDF in the cache carried before this decision.
     *
     * <p>The fixture is a one-page PDF drawing a small bar chart as an image, generated here (ADR-063).
     * It was measured against the pinned image on 2026-09-25: one picture, with pixels under
     * {@code embedded} and without them under {@code placeholder}.
     */
    @Test
    @Story("The conversion pins what it asks for")
    @DisplayName("A picture in a PDF comes back with its pixels, as an image the reader can decode")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void returnsAPicturesPixelsFromAPdf(@TempDir Path dir) throws IOException {
        DoclingResponse response = client.convert(aPdfWithAChart(dir.resolve("chart.pdf")), DetectedFormat.PDF, null);
        JsonNode pictures = JsonMapper.builder().build()
                .readTree(response.rawResponse())
                .path("document")
                .path("json_content")
                .path("pictures");

        claim(
                "the service reports the conversion succeeded",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "it found the chart as a picture on the page",
                () -> assertThat(pictures.isArray() && !pictures.isEmpty()).isTrue());
        String uri = pictures.path(0).path("image").path("uri").asString("");
        claim(
                "and the picture carries its pixels inside the answer, as a PNG written into the address"
                        + " itself, rather than a location on the page with nothing to show",
                () -> assertThat(uri).startsWith(EMBEDDED_PNG_PREFIX));
        claim(
                "and those pixels decode to an image with a width and a height, so what is cached is a"
                        + " picture a reader can be shown",
                () -> {
                    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(
                            Base64.getDecoder().decode(uri.substring(EMBEDDED_PNG_PREFIX.length()))));
                    assertThat(decoded).isNotNull();
                    assertThat(decoded.getWidth()).isPositive();
                    assertThat(decoded.getHeight()).isPositive();
                });
    }

    @Test
    @Story("One call converts one document")
    @DisplayName("Prose in a file with no extension converts, because the name sent is the format's")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void convertsProseThatCarriesNoExtensionOnDisk(@TempDir Path dir) throws IOException {
        Path noExtension = Files.writeString(
                dir.resolve("notes"), "A paragraph of ordinary prose, mentioning " + PROSE_MARKER_WORD + ".\n");

        DoclingResponse response = client.convert(noExtension, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "the whole design rests on this and only a real sidecar can contradict it: an upload"
                        + " whose name carries no extension the converter knows does not fall back to plain"
                        + " text, it resolves to no format at all and fails -- so the name derived from the"
                        + " detected format is what makes a file like this convertible",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "and what came back is this file's content, not an empty document that happened not to"
                        + " error",
                () -> assertThat(response.rawResponse()).contains(PROSE_MARKER_WORD));
        claim(
                "and it carries a quality snapshot with nothing measured in it -- text is converted by"
                        + " the simple pipeline and confidence is derived per page, so the scores come back"
                        + " null and the grades unspecified, which reads as not measured and never as poor",
                () -> {
                    assertThat(response.confidence().meanScore()).isNull();
                    assertThat(response.confidence().lowScore()).isNull();
                    assertThat(response.confidence().meanGrade()).isEqualTo(QualityGrade.UNSPECIFIED);
                });
    }

    /**
     * A real single-page PDF: catalog, page tree, one page, one Type 1 base font, and a content
     * stream that actually shows text, with a cross-reference table whose offsets are computed from
     * the bytes written. Nothing here is decorative — a PDF missing any of it is one the converter's
     * backend rejects rather than parses, which would make this test pass for the wrong reason.
     */
    private static Path aRealPdf(Path file) throws IOException {
        String textStream = "BT /F1 24 Tf 72 700 Td (" + PDF_MARKER_WORD + ") Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]"
                        + " /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + textStream.length() + " >>\nstream\n" + textStream + "endstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int object = 0; object < objects.size(); object++) {
            offsets.add(pdf.length());
            pdf.append(object + 1).append(" 0 obj\n").append(objects.get(object)).append("\nendobj\n");
        }
        int startOfCrossReferenceTable = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append('\n');
        pdf.append("0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append("%010d 00000 n \n".formatted(offset)));
        pdf.append("trailer\n<< /Size ")
                .append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n")
                .append(startOfCrossReferenceTable)
                .append("\n%%EOF\n");

        // US-ASCII, so a character offset counted above is also a byte offset, which is what the
        // cross-reference table means. A non-ASCII marker word would silently invalidate every offset.
        Files.writeString(file, pdf, StandardCharsets.US_ASCII);
        return file;
    }

    /**
     * A one-page PDF showing {@link #PDF_MARKER_WORD} and, below it, a bar chart drawn as an image: a
     * {@value #CHART_WIDTH}×{@value #CHART_HEIGHT} RGB image XObject, Flate-compressed, of four coloured
     * bars on two black axes over white. The same page and the same image as the fixture measured on
     * 2026-09-25 (ADR-150's research record), which the pinned image reads as one picture. The
     * cross-reference offsets are byte offsets into what is written, so the binary stream is counted in
     * bytes, not characters.
     */
    private static Path aPdfWithAChart(Path file) throws IOException {
        byte[] raw = new byte[CHART_WIDTH * CHART_HEIGHT * 3];
        for (int y = 0; y < CHART_HEIGHT; y++) {
            for (int x = 0; x < CHART_WIDTH; x++) {
                int[] rgb = chartPixel(x, y);
                int at = (y * CHART_WIDTH + x) * 3;
                raw[at] = (byte) rgb[0];
                raw[at + 1] = (byte) rgb[1];
                raw[at + 2] = (byte) rgb[2];
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        byte[] image = compressed.toByteArray();

        String content = "BT /F1 24 Tf 72 700 Td (" + PDF_MARKER_WORD + ") Tj ET\nq 288 0 0 192 72 400 cm /Im1 Do Q\n";
        List<byte[]> objects = List.of(
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >>"
                        + " /XObject << /Im1 6 0 R >> >> /Contents 5 0 R >>"),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                ascii("<< /Length " + content.length() + " >>\nstream\n" + content + "endstream"),
                concat(
                        ascii("<< /Type /XObject /Subtype /Image /Width " + CHART_WIDTH + " /Height " + CHART_HEIGHT
                                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                                + image.length + " >>\nstream\n"),
                        image,
                        ascii("\nendstream")));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes(ascii("%PDF-1.7\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int object = 0; object < objects.size(); object++) {
            offsets.add(pdf.size());
            pdf.writeBytes(ascii((object + 1) + " 0 obj\n"));
            pdf.writeBytes(objects.get(object));
            pdf.writeBytes(ascii("\nendobj\n"));
        }
        int startOfCrossReferenceTable = pdf.size();
        StringBuilder trailer = new StringBuilder("xref\n0 ").append(objects.size() + 1).append('\n');
        trailer.append("0000000000 65535 f \n");
        offsets.forEach(offset -> trailer.append("%010d 00000 n \n".formatted(offset)));
        trailer.append("trailer\n<< /Size ")
                .append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n")
                .append(startOfCrossReferenceTable)
                .append("\n%%EOF\n");
        pdf.writeBytes(ascii(trailer.toString()));
        Files.write(file, pdf.toByteArray());
        return file;
    }

    /** One pixel of the chart, {@code y} counted from the top row: two axes, four bars, white elsewhere. */
    private static int[] chartPixel(int x, int y) {
        int baseline = CHART_HEIGHT - 20;
        if (x == 20 || y == baseline) {
            return new int[] {0, 0, 0};
        }
        for (int[] bar : CHART_BARS) {
            if (x >= bar[0] && x < bar[0] + 30 && y >= baseline - bar[1] && y < baseline) {
                return new int[] {bar[2], bar[3], bar[4]};
            }
        }
        return new int[] {255, 255, 255};
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            joined.writeBytes(part);
        }
        return joined.toByteArray();
    }

    /**
     * A real {@code .docx}: an OOXML package with the three parts a reader has to find — the content
     * types map, the package relationship naming the main document, and the document itself with one
     * paragraph. Built with {@link ZipOutputStream} the same way {@code BrokenCheckTest} builds its
     * valid container, since a {@code .docx} is a zip and the JDK already writes those.
     */
    private static Path aRealDocx(Path file) throws IOException {
        return aDocx(file, DOCX_MARKER_WORD);
    }

    /** {@link #aRealDocx}'s package, its one paragraph reading {@code word}. */
    private static Path aDocx(Path file, String word) throws IOException {
        try (ZipOutputStream docx = new ZipOutputStream(Files.newOutputStream(file))) {
            write(
                    docx,
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels"\
                     ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml"\
                     ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            write(
                    docx,
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1"\
                     Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"\
                     Target="word/document.xml"/>
                    </Relationships>
                    """);
            write(
                    docx,
                    "word/_rels/document.xml.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                    """);
            write(
                    docx,
                    "word/document.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document\
                     xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """
                            .formatted(word));
        }
        return file;
    }

    /**
     * A real OpenDocument presentation with one slide reading {@code word}: the three parts LibreOffice
     * needs, the uncompressed {@code mimetype} entry first as the format requires.
     */
    private static Path anOdp(Path file, String word) throws IOException {
        try (ZipOutputStream odp = new ZipOutputStream(Files.newOutputStream(file))) {
            byte[] mimetype = "application/vnd.oasis.opendocument.presentation".getBytes(StandardCharsets.US_ASCII);
            CRC32 checksum = new CRC32();
            checksum.update(mimetype);
            ZipEntry stored = new ZipEntry("mimetype");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(mimetype.length);
            stored.setCrc(checksum.getValue());
            odp.putNextEntry(stored);
            odp.write(mimetype);
            odp.closeEntry();
            write(
                    odp,
                    "META-INF/manifest.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
                      <manifest:file-entry manifest:full-path="/"\
                     manifest:media-type="application/vnd.oasis.opendocument.presentation"/>
                      <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                    </manifest:manifest>
                    """);
            write(
                    odp,
                    "content.xml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <office:document-content\
                     xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"\
                     xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"\
                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"\
                     xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"\
                     office:version="1.2">
                      <office:body><office:presentation><draw:page draw:name="page1">
                        <draw:frame svg:x="2cm" svg:y="2cm" svg:width="20cm" svg:height="3cm">
                          <draw:text-box><text:p>%s</text:p></draw:text-box>
                        </draw:frame>
                      </draw:page></office:presentation></office:body>
                    </office:document-content>
                    """
                            .formatted(word));
        }
        return file;
    }

    /**
     * {@code source} saved as {@code extension} by the sidecar's own LibreOffice, and copied back into
     * {@code dir}: the one writer of the older Office formats this suite has, and the reason these two
     * fixtures can be generated rather than committed (ADR-063).
     */
    private Path savedByLibreOfficeAs(Path source, String extension, Path dir) throws Exception {
        String inside = "/tmp/fixture-" + source.getFileName();
        doclingServeContainer.copyFileToContainer(MountableFile.forHostPath(source), inside);
        Container.ExecResult saved = doclingServeContainer.execInContainer(
                "soffice",
                "-env:UserInstallation=file:///tmp/fixture-profile",
                "--headless",
                "--convert-to",
                extension,
                "--outdir",
                "/tmp/fixture-out",
                inside);
        if (saved.getExitCode() != 0) {
            throw new IllegalStateException("LibreOffice could not save the fixture as ." + extension + ": " + saved);
        }
        String stem = "fixture-" + source.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path copied = dir.resolve(stem + "." + extension);
        doclingServeContainer.copyFileFromContainer("/tmp/fixture-out/" + copied.getFileName(), copied.toString());
        return copied;
    }

    private static void write(ZipOutputStream docx, String part, String content) throws IOException {
        docx.putNextEntry(new ZipEntry(part));
        docx.write(content.getBytes(StandardCharsets.UTF_8));
        docx.closeEntry();
    }
}
