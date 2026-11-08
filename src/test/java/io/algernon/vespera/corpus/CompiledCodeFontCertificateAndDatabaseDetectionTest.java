package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.corpus.DetectedFormat.COMPILED_CODE;
import static io.algernon.vespera.corpus.DetectedFormat.ZIP_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How stage 1 tells compiled code, a font, a certificate or a detached signature, and a database from their
 * bytes, so that it can leave each out of scope (ADR-252).
 *
 * <p>Every rule is pinned from both sides. A file of one of the four kinds is recognised under a name that says
 * something else, because the bytes decide (ADR-094). And what shares its opening bytes or its container
 * without being one stays what it was: text that opens with the same letters, an Office or OpenDocument
 * package in the same zip container as a Java archive, a signed envelope that carries the document it signs.
 * An email is the one kind told by its name, within the container or the text its bytes fixed, as an older
 * spreadsheet is (ADR-146). The last test pins what the record leaves alone: a project plan, a drawing, a
 * binary file with no signature, and text in an older single-byte encoding are not left out by it.
 *
 * <p>Every file here is a few bytes built in this class. No database and no Spring context, matching
 * {@code VideoDetectionTest}.
 */
@Epic("Byte-level reduction")
@Feature("Format detection")
@Link(name = "ADR-252", url = Adr.COMPILED_CODE_FONTS_CERTIFICATES_AND_DATABASES_ARE_OUT_OF_SCOPE, type = "adr")
@Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
class CompiledCodeFontCertificateAndDatabaseDetectionTest {

    @Test
    @Story("Compiled code is out of scope")
    @DisplayName("A Java or Android archive is compiled code, and an Office, OpenDocument or EPUB package in the same zip container is not")
    void aCodeArchiveIsCompiledCodeAndADocumentInAZipIsNot(@TempDir Path dir) throws IOException {
        Path javaArchive = zip(dir.resolve("guida.docx"), "META-INF/MANIFEST.MF", "io/example/Main.class");
        Path androidLibrary = zip(dir.resolve("sdk.aar"), "AndroidManifest.xml", "classes.jar", "R.txt");
        Path word = zip(dir.resolve("lib.jar"), "[Content_Types].xml", "word/document.xml");
        Path signedWord =
                zip(dir.resolve("signed.docx"), "[Content_Types].xml", "META-INF/MANIFEST.MF", "word/document.xml");
        Path workbook = zip(dir.resolve("tabella.jar"), "[Content_Types].xml", "META-INF/MANIFEST.MF", "xl/workbook.xml");
        Path presentation = zip(dir.resolve("slides.pptx"), "[Content_Types].xml", "ppt/presentation.xml");
        Path presentationWithAManifest =
                zip(dir.resolve("deck.pptx"), "[Content_Types].xml", "META-INF/MANIFEST.MF", "ppt/presentation.xml");
        Path openDocument =
                zip(dir.resolve("lettera.odt"), "mimetype", "META-INF/manifest.xml", "META-INF/MANIFEST.MF", "content.xml");
        Path book = zip(dir.resolve("manuale.epub"), "mimetype", "META-INF/container.xml", "OEBPS/content.opf");
        Path plainArchive = zip(dir.resolve("consegna.zip"), "README.txt", "docs/specifica.pdf");

        claim(
                "a zip container holding META-INF/MANIFEST.MF is a Java archive and compiled code, although it"
                        + " is named as a Word file",
                () -> assertThat(formatOf(javaArchive)).isEqualTo(COMPILED_CODE));
        claim(
                "and one holding AndroidManifest.xml is an Android archive and compiled code",
                () -> assertThat(formatOf(androidLibrary)).isEqualTo(COMPILED_CODE));
        claim(
                "a Word file is a Word file whatever it is called, and whether or not it also holds a manifest:"
                        + " the part that makes it one is looked for first",
                () -> assertThat(List.of(BrokenCheck.check(word).format(), BrokenCheck.check(signedWord).format()))
                        .containsOnly(DetectedFormat.WORDPROCESSING));
        claim(
                "a workbook holding a manifest is still a spreadsheet",
                () -> assertThat(BrokenCheck.check(workbook).format()).isEqualTo(DetectedFormat.SPREADSHEET));
        claim(
                "a presentation stays an archive of another kind, which the converter reads, and it stays one"
                        + " where it also holds a manifest: a package that lists its content types is an Office"
                        + " package, not a Java archive",
                () -> assertThat(List.of(formatOf(presentation), formatOf(presentationWithAManifest)))
                        .containsOnly(ZIP_CONTAINER));
        claim(
                "an OpenDocument file and an EPUB book stay archives of another kind, the first even where it"
                        + " holds a manifest of the Java name: a package that opens with its mimetype is a document",
                () -> assertThat(List.of(formatOf(openDocument), formatOf(book))).containsOnly(ZIP_CONTAINER));
        claim(
                "and a plain zip archive stays an archive of another kind: what it holds may be documents",
                () -> assertThat(formatOf(plainArchive)).isEqualTo(ZIP_CONTAINER));
        Path documentsZippedWithJar =
                zip(dir.resolve("consegna-doc.zip"), "META-INF/MANIFEST.MF", "docs/specifica.pdf", "docs/verbale.docx");
        Path androidProjectFolder = zip(dir.resolve("progetto.zip"), "AndroidManifest.xml", "docs/manuale.pdf");
        claim(
                "an archive of documents that holds a manifest, because it was made with the jar tool or is rooted"
                        + " at an Android project, is taken as compiled code: the manifest is all the directory says,"
                        + " and the record accepts it",
                () -> assertThat(List.of(formatOf(documentsZippedWithJar), formatOf(androidProjectFolder)))
                        .containsOnly(COMPILED_CODE));
    }

    private static DetectedFormat formatOf(Path file) {
        return BrokenCheck.check(file).format();
    }

    /** A zip container holding an entry of each of {@code names}, each a few bytes long. */
    private static Path zip(Path file, String... names) throws IOException {
        try (OutputStream out = Files.newOutputStream(file);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(ascii("built by a test"));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
