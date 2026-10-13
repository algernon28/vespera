package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.corpus.DetectedFormat.CERTIFICATE;
import static io.algernon.vespera.corpus.DetectedFormat.COMPILED_CODE;
import static io.algernon.vespera.corpus.DetectedFormat.DATABASE;
import static io.algernon.vespera.corpus.DetectedFormat.FONT;
import static io.algernon.vespera.corpus.DetectedFormat.PLAIN_TEXT;
import static io.algernon.vespera.corpus.DetectedFormat.UNRECOGNISED;
import static io.algernon.vespera.corpus.DetectedFormat.ZIP_CONTAINER;
import static io.algernon.vespera.corpus.DetectedSubtype.OUTLOOK_MESSAGE;
import static io.algernon.vespera.corpus.DetectedSubtype.RFC822_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    /** The reason an email's out-of-scope verdict carries, word for word, whichever of the two it is. */
    private static final String EMAIL_REASON = "an email, and emails are out of scope";

    /** Where an MZ header keeps the offset of the PE header, little-endian. */
    private static final int PE_HEADER_OFFSET_FIELD = 60;

    /** How many leading bytes stage 1 reads to decide what a file is. */
    private static final int DETECTION_PREFIX = 512;

    /** The version an sfnt font with TrueType outlines opens with, which a Jet database opens with too. */
    private static final byte[] TRUETYPE_VERSION = {0x00, 0x01, 0x00, 0x00};

    /** The object identifier of CMS signed data, as its whole tag, length and value. */
    private static final byte[] SIGNED_DATA_TYPE = {
        0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x02
    };

    /** The object identifier of CMS data, the type of what a signature is over, the same way. */
    private static final byte[] DATA_TYPE = {
        0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x01
    };

    /** A signed data's version and its one digest algorithm, SHA-256, which precede what it signs. */
    private static final byte[] VERSION_AND_DIGEST_ALGORITHMS = {
        0x02, 0x01, 0x01, 0x31, 0x0F, 0x30, 0x0D, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02,
        0x01, 0x05, 0x00
    };

    /** The eight bytes every OLE compound file opens with. */
    private static final byte[] OLE_COMPOUND_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    @Test
    @Story("Compiled code is out of scope")
    @DisplayName("A Windows program or library is compiled code where its PE header lies in the bytes read, whatever it is called")
    void aWindowsProgramIsCompiledCodeOnlyWithItsPeHeader(@TempDir Path dir) throws IOException {
        Path program = Files.write(dir.resolve("manuale.pdf"), portableExecutable(0x80));
        Path dosOnly = Files.write(dir.resolve("old.exe"), portableExecutable(0));
        Path headerOutOfReach = Files.write(dir.resolve("big-stub.dll"), portableExecutable(2 * DETECTION_PREFIX));
        Path prose = Files.writeString(
                dir.resolve("notes.txt"), "MZ is how a Windows program opens, written out as words for a reader.");

        claim(
                "a file opening with MZ whose header names a PE header at byte 128 is compiled code, although it"
                        + " is named as a PDF",
                () -> assertThat(formatOf(program)).isEqualTo(COMPILED_CODE));
        claim(
                "recognising it removes nothing by itself: it is not damaged, and no finer label is taken from"
                        + " its name",
                () -> {
                    assertThat(BrokenCheck.check(program).broken()).isFalse();
                    assertThat(BrokenCheck.check(program).subtype()).isEmpty();
                });
        claim(
                "MZ alone is two letters: a binary file opening with them and naming no PE header stays of no"
                        + " known kind",
                () -> assertThat(formatOf(dosOnly)).isEqualTo(UNRECOGNISED));
        claim(
                "and so does one whose PE header lies past the " + DETECTION_PREFIX + " bytes read: the rule"
                        + " reads no second time",
                () -> assertThat(formatOf(headerOutOfReach)).isEqualTo(UNRECOGNISED));
        byte[] offsetOfAllOnes = portableExecutable(0);
        java.util.Arrays.fill(offsetOfAllOnes, PE_HEADER_OFFSET_FIELD, PE_HEADER_OFFSET_FIELD + 4, (byte) 0xFF);
        Path headerNowhere = Files.write(dir.resolve("odd.exe"), offsetOfAllOnes);
        Path headerAcrossTheEnd = Files.write(dir.resolve("edge.dll"), portableExecutable(DETECTION_PREFIX - 3));
        Path tooShortToNameAHeader =
                Files.write(dir.resolve("stub.com"), java.util.Arrays.copyOf(portableExecutable(0), PE_HEADER_OFFSET_FIELD - 20));
        claim(
                "a header offset of FF FF FF FF names no place in the file: of no known kind, and nothing is read"
                        + " at it",
                () -> assertThat(formatOf(headerNowhere)).isEqualTo(UNRECOGNISED));
        claim(
                "a PE header that begins at byte " + (DETECTION_PREFIX - 3) + " has three of its four bytes in the"
                        + " bytes read and one past them: it does not lie wholly inside, so of no known kind",
                () -> assertThat(formatOf(headerAcrossTheEnd)).isEqualTo(UNRECOGNISED));
        claim(
                "and a binary file opening with MZ that ends before byte " + PE_HEADER_OFFSET_FIELD + " has no"
                        + " field to name a header: of no known kind",
                () -> assertThat(formatOf(tooShortToNameAHeader)).isEqualTo(UNRECOGNISED));
        claim(
                "a text file opening with the letters MZ is text",
                () -> assertThat(formatOf(prose)).isEqualTo(PLAIN_TEXT));
    }

    @Test
    @Story("Compiled code is out of scope")
    @DisplayName("An ELF file, a Mach-O file, a Java class and a static library are compiled code")
    void anObjectFileAClassAndAStaticLibraryAreCompiledCode(@TempDir Path dir) throws IOException {
        Path object = Files.write(dir.resolve("main.o"), elf(1, 1));
        Path firmware = Files.write(dir.resolve("image.axf"), elf(1, 1));
        Path sharedLibrary = Files.write(dir.resolve("report.docx"), elf(2, 1));
        Path bigEndian = Files.write(dir.resolve("a.out"), elf(1, 2));
        Path noSuchClass = Files.write(dir.resolve("odd.o"), elf(0, 1));
        List<Path> machO = List.of(
                Files.write(dir.resolve("tool32"), machO(0xFE, 0xED, 0xFA, 0xCE)),
                Files.write(dir.resolve("tool64"), machO(0xFE, 0xED, 0xFA, 0xCF)),
                Files.write(dir.resolve("tool32.le"), machO(0xCE, 0xFA, 0xED, 0xFE)),
                Files.write(dir.resolve("tool64.le"), machO(0xCF, 0xFA, 0xED, 0xFE)));
        Path javaClass = Files.write(
                dir.resolve("Main.txt"),
                concat(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 0x41, 0, 0x10},
                        binary(120)));
        Path staticLibrary = Files.write(
                dir.resolve("CRYPTO.LIB"),
                concat(ascii("!<arch>\n"), archiveMember("/", 8), new byte[] {0, 0, 0, 1, 0, 0, 0, 0x60}, binary(200)));
        Path archiveOfText = Files.write(
                dir.resolve("notes.a"),
                concat(ascii("!<arch>\n"), archiveMember("readme.txt/", 12), ascii("plain words\n")));

        claim(
                "an ELF file is compiled code, whether it is an object file, firmware for a device or a shared"
                        + " library named as a Word file, in either width and either byte order",
                () -> assertThat(List.of(
                                formatOf(object), formatOf(firmware), formatOf(sharedLibrary), formatOf(bigEndian)))
                        .containsOnly(COMPILED_CODE));
        claim(
                "the four ELF letters over a class byte no ELF file carries are not enough: of no known kind",
                () -> assertThat(formatOf(noSuchClass)).isEqualTo(UNRECOGNISED));
        claim(
                "a Mach-O file is compiled code under each of its four magic numbers",
                () -> assertThat(machO.stream()
                                .map(CompiledCodeFontCertificateAndDatabaseDetectionTest::formatOf)
                                .toList())
                        .containsOnly(COMPILED_CODE));
        claim(
                "a Java class file is compiled code, although it is named as text",
                () -> assertThat(formatOf(javaClass)).isEqualTo(COMPILED_CODE));
        claim(
                "a static library, an archive of object files, is compiled code",
                () -> assertThat(formatOf(staticLibrary)).isEqualTo(COMPILED_CODE));
        claim(
                "an archive of the same format whose bytes read are all text is text, as it is today: what"
                        + " decodes as text is text, whatever it opens with",
                () -> assertThat(formatOf(archiveOfText)).isEqualTo(PLAIN_TEXT));
    }

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

    @Test
    @Story("Fonts are out of scope")
    @DisplayName("A TrueType, OpenType, collection or WOFF font is a font, and text or bytes that only open like one are not")
    void aFontIsAFontOnlyWhereItsHeaderAgreesWithItself(@TempDir Path dir) throws IOException {
        Path trueType = Files.write(dir.resolve("Roboto.pdf"), sfnt(TRUETYPE_VERSION, 4, true));
        Path nineteenTables = Files.write(dir.resolve("Arial.ttf"), sfnt(TRUETYPE_VERSION, 19, true));
        Path openType = Files.write(dir.resolve("Source.otf"), sfnt(ascii("OTTO"), 9, true));
        Path collection = Files.write(
                dir.resolve("fonts.ttc"),
                concat(ascii("ttcf"), new byte[] {0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0x14, 0, 0, 1, 0}, binary(100)));
        Path webFont = Files.write(
                dir.resolve("site.woff"), concat(ascii("wOFF"), TRUETYPE_VERSION, new byte[] {0, 0, 4, 0}, binary(100)));
        Path webFont2 = Files.write(
                dir.resolve("site.woff2"), concat(ascii("wOF2"), ascii("OTTO"), new byte[] {0, 0, 4, 0}, binary(100)));
        Path headerDisagrees = Files.write(dir.resolve("table.dat"), sfnt(TRUETYPE_VERSION, 4, false));
        Path october = Files.writeString(
                dir.resolve("verbale.txt"), "OTTOBRE 2020: verbale della riunione di avanzamento del progetto.");
        Path aboutWebFonts = Files.writeString(dir.resolve("woff.md"), "wOFF is the wrapper a web font travels in.");
        Path wrapperOfSomethingElse =
                Files.write(dir.resolve("odd.woff"), concat(ascii("wOFF"), ascii("abcd"), binary(100)));

        claim(
                "a TrueType font is a font, although it is named as a PDF, and so is one of nineteen tables: the"
                        + " three numbers its header derives from the count of its tables are the ones that count"
                        + " gives",
                () -> assertThat(List.of(formatOf(trueType), formatOf(nineteenTables))).containsOnly(FONT));
        claim(
                "an OpenType font, a font collection and a web font in either WOFF version are fonts",
                () -> assertThat(List.of(
                                formatOf(openType), formatOf(collection), formatOf(webFont), formatOf(webFont2)))
                        .containsOnly(FONT));
        claim(
                "recognising a font removes nothing by itself, and its name adds no finer label",
                () -> {
                    assertThat(BrokenCheck.check(trueType).broken()).isFalse();
                    assertThat(BrokenCheck.check(trueType).subtype()).isEmpty();
                });
        claim(
                "four bytes a TrueType font opens with are not enough: where the header's three derived numbers"
                        + " are not those its table count gives, the file stays of no known kind",
                () -> assertThat(formatOf(headerDisagrees)).isEqualTo(UNRECOGNISED));
        claim(
                "minutes dated OTTOBRE are text, although an OpenType font opens with OTTO, and so is a note"
                        + " opening with the letters wOFF",
                () -> assertThat(List.of(formatOf(october), formatOf(aboutWebFonts))).containsOnly(PLAIN_TEXT));
        claim(
                "and a binary file opening with wOFF that wraps neither kind of font is of no known kind",
                () -> assertThat(formatOf(wrapperOfSomethingElse)).isEqualTo(UNRECOGNISED));
        Path octoberInAnOlderEncoding = Files.write(
                dir.resolve("verbale-ottobre.txt"),
                "OTTOBRE 2020: la società ha deciso che il collaudo è rinviato.".getBytes(StandardCharsets.ISO_8859_1));
        claim(
                "minutes dated OTTOBRE in an older single-byte encoding do not decode as text, and are still not a"
                        + " font: the letters after OTTO are no table count its header agrees with, so of no known"
                        + " kind, as today",
                () -> assertThat(formatOf(octoberInAnOlderEncoding)).isEqualTo(UNRECOGNISED));
    }

    @Test
    @Story("Databases are out of scope")
    @DisplayName("An Access database of either engine and an SQLite database are databases, and neither is taken for a font")
    void anAccessOrSqliteDatabaseIsADatabase(@TempDir Path dir) throws IOException {
        Path jet = Files.write(
                dir.resolve("anagrafica.doc"),
                concat(TRUETYPE_VERSION, ascii("Standard Jet DB"), new byte[] {0, 1, 0, 0, 0}, binary(200)));
        Path ace = Files.write(
                dir.resolve("terminali.accdb"),
                concat(TRUETYPE_VERSION, ascii("Standard ACE DB"), new byte[] {0, 2, 0, 0, 0}, binary(200)));
        Path sqlite = Files.write(
                dir.resolve("cache.txt"),
                concat(ascii("SQLite format 3"), new byte[] {0, 0x10, 0x00, 1, 1, 0, 0x40, 0x20, 0x20}, binary(200)));
        Path aboutSqlite = Files.writeString(
                dir.resolve("sqlite.md"), "SQLite format 3 is the name its database file opens with, as a reader sees it.");

        claim(
                "an Access database of the older Jet engine is a database, although it is named as a Word file and"
                        + " opens with the four bytes a TrueType font opens with",
                () -> assertThat(formatOf(jet)).isEqualTo(DATABASE));
        claim(
                "and so are one of the newer engine and an SQLite database named as text",
                () -> assertThat(List.of(formatOf(ace), formatOf(sqlite))).containsOnly(DATABASE));
        claim(
                "recognising a database removes nothing by itself, and its name adds no finer label",
                () -> {
                    assertThat(BrokenCheck.check(sqlite).broken()).isFalse();
                    assertThat(BrokenCheck.check(sqlite).subtype()).isEmpty();
                });
        claim(
                "a note that opens with the words an SQLite database opens with is text: the database's sixteenth"
                        + " byte is zero, and text holds none",
                () -> assertThat(formatOf(aboutSqlite)).isEqualTo(PLAIN_TEXT));
    }

    @Test
    @Story("Certificates and detached signatures are out of scope")
    @DisplayName("A certificate and a signature that carries no content are recognised, and a signed envelope that carries its document is not")
    void aCertificateAndADetachedSignatureAreRecognisedAndASignedEnvelopeIsNot(@TempDir Path dir) throws IOException {
        Path certificate = Files.write(
                dir.resolve("server.txt"),
                concat(
                        new byte[] {0x30, (byte) 0x82, 0x01, 0x0A, 0x30, (byte) 0x82, 0x00, (byte) 0xF2},
                        new byte[] {(byte) 0xA0, 0x03, 0x02, 0x01, 0x02, 0x02, 0x08},
                        binary(255)));
        Path detached = Files.write(
                dir.resolve("contratto.pdf.P7S"),
                concat(
                        new byte[] {0x30, (byte) 0x82, 0x01, 0x2C},
                        SIGNED_DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x82, 0x01, 0x1D, 0x30, (byte) 0x82, 0x01, 0x19},
                        VERSION_AND_DIGEST_ALGORITHMS,
                        new byte[] {0x30, 0x0B},
                        DATA_TYPE,
                        binary(248)));
        Path detachedOfUnstatedLength = Files.write(
                dir.resolve("firma.p7s"),
                concat(
                        new byte[] {0x30, (byte) 0x80},
                        SIGNED_DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x80, 0x30, (byte) 0x80},
                        VERSION_AND_DIGEST_ALGORITHMS,
                        new byte[] {0x30, (byte) 0x80},
                        DATA_TYPE,
                        new byte[] {0, 0},
                        binary(248)));
        Path envelope = Files.write(
                dir.resolve("contratto.pdf.p7m"),
                concat(
                        new byte[] {0x30, (byte) 0x82, 0x02, 0x00},
                        SIGNED_DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x82, 0x01, (byte) 0xF1, 0x30, (byte) 0x82, 0x01, (byte) 0xED},
                        VERSION_AND_DIGEST_ALGORITHMS,
                        new byte[] {0x30, (byte) 0x82, 0x01, 0x40},
                        DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x82, 0x01, 0x31, 0x04, (byte) 0x82, 0x01, 0x2D},
                        ascii("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n"),
                        binary(260)));
        Path privateKey = Files.write(
                dir.resolve("server.key"),
                concat(new byte[] {0x30, (byte) 0x82, 0x01, 0x0A, 0x02, 0x01, 0x00, 0x02, (byte) 0x81, (byte) 0x81}, binary(256)));
        Path inWords = Files.writeString(
                dir.resolve("server.crt"),
                "-----BEGIN CERTIFICATE-----\nMIIBszCCARygAwIBAgIJAKdC0n0hQmQxMA0GCSqGSIb3DQEBCwUAMBExDzANBgNV\n"
                        + "-----END CERTIFICATE-----\n");

        claim(
                "a certificate in its binary encoding is recognised, although it is named as text",
                () -> assertThat(formatOf(certificate)).isEqualTo(CERTIFICATE));
        claim(
                "a signature that carries no content is recognised, whether the lengths of its parts are stated"
                        + " or left open",
                () -> assertThat(List.of(formatOf(detached), formatOf(detachedOfUnstatedLength)))
                        .containsOnly(CERTIFICATE));
        claim(
                "recognising either removes nothing by itself, and a name adds no finer label",
                () -> {
                    assertThat(BrokenCheck.check(detached).broken()).isFalse();
                    assertThat(BrokenCheck.check(detached).subtype()).isEmpty();
                });
        claim(
                "a signed envelope carries the document it signs, here a PDF, so it is not a detached signature"
                        + " and stays of no known kind",
                () -> assertThat(formatOf(envelope)).isEqualTo(UNRECOGNISED));
        Path envelopeOfUnstatedLength = Files.write(
                dir.resolve("verbale.pdf.p7m"),
                concat(
                        new byte[] {0x30, (byte) 0x80},
                        SIGNED_DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x80, 0x30, (byte) 0x80},
                        VERSION_AND_DIGEST_ALGORITHMS,
                        new byte[] {0x30, (byte) 0x80},
                        DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x80, 0x24, (byte) 0x80, 0x04, (byte) 0x82, 0x01, 0x00},
                        ascii("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n"),
                        binary(260)));
        Path dataTypeInASet = Files.write(
                dir.resolve("ricevuta.p7s"),
                concat(
                        new byte[] {0x30, (byte) 0x82, 0x01, 0x2C},
                        SIGNED_DATA_TYPE,
                        new byte[] {(byte) 0xA0, (byte) 0x82, 0x01, 0x1D, 0x30, (byte) 0x82, 0x01, 0x19},
                        VERSION_AND_DIGEST_ALGORITHMS,
                        new byte[] {0x31, 0x0B},
                        DATA_TYPE,
                        binary(248)));
        claim(
                "and so does an envelope the lengths of whose parts are left open: its content of type data is"
                        + " followed by what it carries, not by the two zero bytes that end an empty one",
                () -> assertThat(formatOf(envelopeOfUnstatedLength)).isEqualTo(UNRECOGNISED));
        claim(
                "signed data that names the type data inside a set, and not as a sequence with nothing after it,"
                        + " shows no empty content: of no known kind",
                () -> assertThat(formatOf(dataTypeInASet)).isEqualTo(UNRECOGNISED));
        claim(
                "a private key shares a certificate's first two bytes and is not one: of no known kind",
                () -> assertThat(formatOf(privateKey)).isEqualTo(UNRECOGNISED));
        claim(
                "and a certificate written out as letters between BEGIN and END lines is text, as it is today",
                () -> assertThat(formatOf(inWords)).isEqualTo(PLAIN_TEXT));
    }

    @Test
    @Story("Emails are out of scope")
    @DisplayName("An email is told by its name within what its bytes are: an older Office container named .msg, or text named .eml")
    void anEmailIsToldByItsNameWithinWhatItsBytesAre(@TempDir Path dir) throws IOException {
        String message = "From: a@example.org\nTo: b@example.org\nSubject: collaudo\n\nIl collaudo e' rinviato.\n";
        Path outlook = Files.write(dir.resolve("RE offerta.msg"), concat(OLE_COMPOUND_SIGNATURE, binary(600)));
        Path outlookUpperCase = Files.write(dir.resolve("FW VERBALE.MSG"), concat(OLE_COMPOUND_SIGNATURE, binary(600)));
        Path word = Files.write(dir.resolve("offerta.doc"), concat(OLE_COMPOUND_SIGNATURE, binary(600)));
        Path textNamedAsOutlook = Files.writeString(dir.resolve("nota.msg"), "Una nota scritta a mano, salvata con un nome d'altri.");
        Path internetMessage = Files.writeString(dir.resolve("collaudo.eml"), message);
        Path sameWordsNamedAsText = Files.writeString(dir.resolve("collaudo.txt"), message);
        Path webPageNamedAsMessage = Files.writeString(dir.resolve("pagina.eml"), "<!DOCTYPE html><html><body>pagina</body></html>");
        Path binaryNamedAsMessage =
                Files.write(dir.resolve("rotto.eml"), concat(new byte[] {0x13, 0x57, (byte) 0x9B, (byte) 0xDF}, binary(600)));
        Path pdfNamedAsMessage =
                Files.writeString(dir.resolve("specifica.eml"), "%PDF-1.7\n1 0 obj\n<< >>\nendobj\n%%EOF\n");

        claim(
                "an older Office container named .msg is an Outlook message, in either case of the letters: the"
                        + " bytes fix the container, and the name says which member of it this is",
                () -> assertThat(List.of(labelOf(outlook), labelOf(outlookUpperCase)))
                        .containsOnly("OLE_COMPOUND/" + OUTLOOK_MESSAGE));
        claim(
                "text named .eml is an internet message, and the same words named .txt are text with no finer"
                        + " label: a saved email is told from a note by its name alone",
                () -> assertThat(List.of(labelOf(internetMessage), labelOf(sameWordsNamedAsText)))
                        .containsExactly(PLAIN_TEXT + "/" + RFC822_MESSAGE, PLAIN_TEXT + "/"));
        claim(
                "both are left out with the one reason an email carries",
                () -> assertThat(List.of(leftOutReasonOf(outlook), leftOutReasonOf(outlookUpperCase), leftOutReasonOf(internetMessage)))
                        .containsOnly(EMAIL_REASON));
        claim(
                "an older Office container named .doc is an older Word file and is not left out: the name .msg is"
                        + " what narrows, not the container",
                () -> {
                    assertThat(labelOf(word)).isEqualTo("OLE_COMPOUND/LEGACY_WORD");
                    assertThat(leftOutReasonOf(word)).isEmpty();
                });
        claim(
                "a name narrows only within what the bytes fixed: text named .msg is text, a web page named .eml"
                        + " is a web page, a binary file named .eml is of no known kind and a PDF named .eml is a PDF",
                () -> assertThat(List.of(
                                labelOf(textNamedAsOutlook),
                                labelOf(webPageNamedAsMessage),
                                labelOf(binaryNamedAsMessage),
                                labelOf(pdfNamedAsMessage)))
                        .containsExactly(PLAIN_TEXT + "/", PLAIN_TEXT + "/HTML", UNRECOGNISED + "/", "PDF/"));
        claim(
                "and none of those four, nor the email's words named .txt, is left out for its kind",
                () -> assertThat(List.of(
                                leftOutReasonOf(textNamedAsOutlook),
                                leftOutReasonOf(webPageNamedAsMessage),
                                leftOutReasonOf(binaryNamedAsMessage),
                                leftOutReasonOf(pdfNamedAsMessage),
                                leftOutReasonOf(sameWordsNamedAsText)))
                        .containsOnly(""));
    }

    @Test
    @Story("What is not a document is told by a signature, never by having none")
    @DisplayName("A PDF, a project plan, a drawing, a binary file with no signature and text in an older encoding are not left out")
    void whatCarriesNoSignatureOfTheFourKindsIsNotLeftOut(@TempDir Path dir) throws IOException {
        Path pdf = Files.writeString(dir.resolve("specifica.pdf"), "%PDF-1.7\n1 0 obj\n<< >>\nendobj\n%%EOF\n");
        Path message = Files.write(dir.resolve("Thumbs.db"), concat(OLE_COMPOUND_SIGNATURE, binary(600)));
        Path plan = Files.write(dir.resolve("piano.mpp"), concat(OLE_COMPOUND_SIGNATURE, binary(600)));
        Path drawing = Files.write(
                dir.resolve("schema.CDR"),
                concat(ascii("RIFF"), new byte[] {(byte) 0xD0, 0, 0, 0}, ascii("CDRC"), binary(200)));
        Path deviceFile = Files.write(
                dir.resolve("FIRMWARE.P3A"), concat(new byte[] {0x13, 0x57, (byte) 0x9B, (byte) 0xDF}, binary(600)));
        Path olderEncoding = Files.write(
                dir.resolve("verbale.txt"),
                "Verbale della riunione: la società ha deciso che il collaudo è rinviato perché manca il terminale."
                        .getBytes(StandardCharsets.ISO_8859_1));
        List<Path> all = List.of(pdf, message, plan, drawing, deviceFile, olderEncoding);

        claim(
                "a PDF is a PDF",
                () -> assertThat(BrokenCheck.check(pdf).format()).isEqualTo(DetectedFormat.PDF));
        claim(
                "a thumbnail cache and a project plan are older Office containers with no finer label, as they"
                        + " are today",
                () -> {
                    assertThat(List.of(BrokenCheck.check(message).format(), BrokenCheck.check(plan).format()))
                            .containsOnly(DetectedFormat.OLE_COMPOUND);
                    assertThat(BrokenCheck.check(message).subtype()).isEmpty();
                });
        claim(
                "a drawing, a binary file with no signature and text in an older single-byte encoding are of no"
                        + " known kind, as they are today: having no signature is not a signature",
                () -> assertThat(List.of(formatOf(drawing), formatOf(deviceFile), formatOf(olderEncoding)))
                        .containsOnly(UNRECOGNISED));
        claim(
                "and none of the six is left out for its kind",
                () -> assertThat(all.stream()
                                .map(BrokenCheck::check)
                                .filter(found -> OutOfScope.reasonFor(found.format(), found.subtype())
                                        .isPresent())
                                .map(found -> found.format().name())
                                .toList())
                        .isEmpty());
    }

    private static DetectedFormat formatOf(Path file) {
        return BrokenCheck.check(file).format();
    }

    /** The detected format and the finer label the name added, as {@code FORMAT/SUBTYPE}, the label empty where none. */
    private static String labelOf(Path file) {
        BrokenCheck.Result found = BrokenCheck.check(file);
        return found.format().name() + "/" + found.subtype().map(Enum::name).orElse("");
    }

    /** The reason {@code file} is left out for its kind, or the empty string where it is not. */
    private static String leftOutReasonOf(Path file) {
        BrokenCheck.Result found = BrokenCheck.check(file);
        return OutOfScope.reasonFor(found.format(), found.subtype()).orElse("");
    }

    /**
     * A file opening with {@code MZ} whose header names {@code headerOffset} as where its PE header lies, with
     * the four bytes {@code PE 00 00} written there. Zero names the file's own first bytes, where no PE header
     * is.
     */
    private static byte[] portableExecutable(int headerOffset) {
        byte[] file = new byte[headerOffset + 256];
        file[0] = 'M';
        file[1] = 'Z';
        file[2] = (byte) 0x90;
        ByteBuffer.wrap(file, PE_HEADER_OFFSET_FIELD, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(headerOffset);
        if (headerOffset > 0) {
            file[headerOffset] = 'P';
            file[headerOffset + 1] = 'E';
            file[headerOffset + 4] = 0x4C; // the machine, an i386
            file[headerOffset + 5] = 0x01;
        }
        return file;
    }

    /** An ELF header of {@code fileClass} (1 is 32-bit, 2 is 64-bit) and {@code byteOrder} (1 little, 2 big). */
    private static byte[] elf(int fileClass, int byteOrder) {
        return concat(
                new byte[] {0x7F, 'E', 'L', 'F', (byte) fileClass, (byte) byteOrder, 1, 0},
                new byte[8], // padding
                new byte[] {1, 0, 0x28, 0, 1, 0, 0, 0}, // a relocatable file for an ARM, version 1
                binary(200));
    }

    private static byte[] machO(int... magic) {
        byte[] opening = new byte[magic.length];
        for (int i = 0; i < magic.length; i++) {
            opening[i] = (byte) magic[i];
        }
        return concat(opening, new byte[] {0, 0, 0, 0x0C, 0, 0, 0, 0, 0, 0, 0, 2}, binary(200));
    }

    /** The 60-byte header an archive member of {@code name} and {@code size} bytes carries, all of it text. */
    private static byte[] archiveMember(String name, int size) {
        return ascii(String.format("%-16s%-12s%-6s%-6s%-8s%-10d`\n", name, "0", "0", "0", "100644", size));
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

    /**
     * An sfnt font header of {@code version} naming {@code tables} tables, then a record for each. Where
     * {@code consistent}, the header's search range, entry selector and range shift are the ones the table
     * count gives; where not, they are zero.
     */
    private static byte[] sfnt(byte[] version, int tables, boolean consistent) {
        int entrySelector = 31 - Integer.numberOfLeadingZeros(tables);
        int searchRange = 16 << entrySelector;
        byte[] derived = consistent
                ? concat(bigEndianShort(searchRange), bigEndianShort(entrySelector), bigEndianShort(tables * 16 - searchRange))
                : new byte[6];
        ByteArrayOutputStream records = new ByteArrayOutputStream();
        for (int table = 0; table < tables; table++) {
            records.writeBytes(ascii("cmap"));
            records.writeBytes(new byte[] {0x1A, 0x2B, 0x3C, 0x4D, 0, 0, 1, 0, 0, 0, 0, 0x40});
        }
        return concat(version, bigEndianShort(tables), derived, records.toByteArray(), binary(100));
    }

    private static byte[] bigEndianShort(int value) {
        return new byte[] {(byte) (value >>> 8), (byte) value};
    }

    /** {@code length} bytes no text is made of: a zero every seventh byte, and bytes above ASCII between. */
    private static byte[] binary(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i % 7 == 0 ? 0x00 : 0x80 + i % 0x70);
        }
        return bytes;
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
}
