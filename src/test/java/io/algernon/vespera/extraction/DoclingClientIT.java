package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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

    @Autowired
    private DoclingClient client;

    @Test
    @Story("One call converts one document")
    @DisplayName("A real PDF converted by the running document service comes back readable, content and all")
    void convertsARealPdf(@TempDir Path dir) throws IOException {
        DoclingResponse response = client.convert(aRealPdf(dir.resolve("one-page.pdf")));

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
        DoclingResponse response = client.convert(aRealDocx(dir.resolve("one-paragraph.docx")));

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
     * A real {@code .docx}: an OOXML package with the three parts a reader has to find — the content
     * types map, the package relationship naming the main document, and the document itself with one
     * paragraph. Built with {@link ZipOutputStream} the same way {@code BrokenCheckTest} builds its
     * valid container, since a {@code .docx} is a zip and the JDK already writes those.
     */
    private static Path aRealDocx(Path file) throws IOException {
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
                            .formatted(DOCX_MARKER_WORD));
        }
        return file;
    }

    private static void write(ZipOutputStream docx, String part, String content) throws IOException {
        docx.putNextEntry(new ZipEntry(part));
        docx.write(content.getBytes(StandardCharsets.UTF_8));
        docx.closeEntry();
    }
}
