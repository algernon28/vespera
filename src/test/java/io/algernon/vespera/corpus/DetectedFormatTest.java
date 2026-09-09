package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What stage 1 decides a file is, and which structural check follows from that (ADR-094), plus what
 * the decision is worth once made (ADR-095).
 *
 * <p>Most fixtures here are deliberately misnamed: the content is one format and the filename says
 * another, because the substance of ADR-094 is that the bytes settle the format on their own.
 * {@code BrokenCheckTest} pins each per-format structural check against a matching name; this class
 * pins that the check is reached by the bytes rather than by the name.
 *
 * <p>The rest pin ADR-094's one narrow admission of the filename: within a class the bytes have
 * already fixed — OLE compound files and plain text, the two classes whose members share a
 * signature or have none — the extension chooses a subtype. Three limits are pinned individually,
 * because they are the whole of what keeps this from being the name-as-evidence design ADR-094
 * rejected: the name never crosses a class boundary, a byte-level signal beats it where one exists,
 * and no subtype reaches the {@code broken} verdict.
 *
 * <p>No database and no Spring context, matching {@code BrokenCheckTest}: the seam is one static
 * method over a real filesystem path.
 */
@Epic("Byte-level reduction")
@Feature("Format detection")
@Issue("87")
@Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
@Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
class DetectedFormatTest {

    /** The eight bytes every PNG begins with, and the whole of what makes a file a PNG here. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** The four bytes a little-endian TIFF begins with: {@code II}, then 42 as a 16-bit word. */
    private static final byte[] LITTLE_ENDIAN_TIFF_SIGNATURE = {'I', 'I', 0x2A, 0x00};

    /**
     * The eight bytes every OLE compound file begins with, required at offset 0 by the format's
     * specification. Shared by legacy Word, Excel and PowerPoint documents and by Thumbs.db alike,
     * which is why this one class holds both real documents and material that is not a document.
     */
    private static final byte[] OLE_COMPOUND_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    /**
     * The four bytes a Windows shortcut begins with: the length of its own header, 76, written
     * smallest byte first. A binary that matches no format this rule knows and does not read as
     * words.
     */
    private static final byte[] WINDOWS_SHORTCUT_SIGNATURE = {0x4C, 0x00, 0x00, 0x00};

    /** A complete, well-formed PDF: the header, one trivial object, and the trailer. */
    private static final String WHOLE_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n%%EOF";

    /** The same PDF with its trailer cut off, which is the one thing the structural check catches. */
    private static final String PDF_WITHOUT_ITS_TRAILER = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** The entry a Word document always carries, and the only thing separating one from any zip. */
    private static final String WORD_MAIN_PART = "word/document.xml";

    /** The equivalent entry in a spreadsheet, which is a zip but is not a Word document. */
    private static final String SPREADSHEET_MAIN_PART = "xl/workbook.xml";

    @Test
    @Story("The content decides, not the name")
    @DisplayName("A PDF saved under a .png name is recognised as a PDF and checked as one")
    void aPdfNamedAsAnImageIsCheckedAsAPdf(@TempDir Path dir) throws IOException {
        Path misnamed = Files.write(dir.resolve("holiday-photo.png"), WHOLE_PDF.getBytes(StandardCharsets.US_ASCII));

        claim(
                "a file whose content begins as a PDF is a PDF, whatever it is called on disk",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.PDF));
        claim(
                "this PDF carries both its opening marker and its end marker, so it is intact and is kept",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isFalse());
    }

    @Test
    @Story("The content decides, not the name")
    @DisplayName("A truncated PDF saved under a .png name is caught as damaged, where before it was not checked at all")
    void aTruncatedPdfNamedAsAnImageIsBroken(@TempDir Path dir) throws IOException {
        Path misnamed =
                Files.write(dir.resolve("holiday-photo.png"), PDF_WITHOUT_ITS_TRAILER.getBytes(StandardCharsets.US_ASCII));

        claim(
                "a truncated PDF is still recognisably a PDF, so it gets the check a PDF gets",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.PDF));
        claim(
                "a PDF that stops before its end marker was cut short, so it is damaged and removed",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isTrue());
    }

    @Test
    @Story("The content decides, not the name")
    @DisplayName("A real image saved under a .docx name is recognised as an image, not opened as an archive")
    void anImageNamedAsAWordDocumentIsAnImage(@TempDir Path dir) throws IOException {
        Path misnamed = Files.write(dir.resolve("report.docx"), signed(PNG_SIGNATURE, "image data follows"));

        claim(
                "an image is identified by the bytes it opens with, never by what it is called",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.IMAGE));
        claim(
                "matching a known image marker is itself the validity check, so a recognised image is"
                        + " never reported damaged",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isFalse());
    }

    @Test
    @Story("The content decides, not the name")
    @DisplayName("Plain text saved under a .pdf name is recognised as text and passes")
    void textNamedAsAPdfIsText(@TempDir Path dir) throws IOException {
        Path misnamed = Files.writeString(dir.resolve("invoice.pdf"), "Dear Sir,\n\nplease find enclosed.\n");

        claim(
                "content that matches no known format marker and reads cleanly as words is text",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "text has no internal structure to validate, so it is never reported damaged",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isFalse());
    }

    @Test
    @Story("Archives are told apart by looking inside them")
    @DisplayName("A Word document saved under a .txt name is recognised by the part it carries inside")
    void aWordDocumentNamedAsTextIsRecognisedByItsInnerPart(@TempDir Path dir) throws IOException {
        Path misnamed = zipHolding(dir.resolve("notes.txt"), WORD_MAIN_PART);

        claim(
                "an archive holding " + WORD_MAIN_PART + " is a word processing document; that entry is what"
                        + " separates one from every other archive, since all of them open the same way",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.WORDPROCESSING));
        claim(
                "the archive opens cleanly, so it is kept — whatever the part inside it turns out to contain",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isFalse());
    }

    @Test
    @Story("Archives are told apart by looking inside them")
    @DisplayName("A spreadsheet is an archive but not a word processing document, and is still kept")
    void aSpreadsheetIsAnArchiveThatIsNotAWordDocument(@TempDir Path dir) throws IOException {
        Path spreadsheet = zipHolding(dir.resolve("figures.xlsx"), SPREADSHEET_MAIN_PART);

        claim(
                "an archive without " + WORD_MAIN_PART + " is not a word processing document",
                () -> assertThat(BrokenCheck.check(spreadsheet).format()).isEqualTo(DetectedFormat.ZIP_CONTAINER));
        claim(
                "spreadsheets, presentations and open-document files are all archives that the conversion"
                        + " step reads perfectly well, so being an archive of another kind removes nothing",
                () -> assertThat(BrokenCheck.check(spreadsheet).broken()).isFalse());
        claim(
                "every archive can be told apart by an entry inside it, so the filename is never consulted"
                        + " on this branch and no finer label is taken from it",
                () -> assertThat(BrokenCheck.check(spreadsheet).subtype()).isEmpty());
    }

    @Test
    @Story("Archives are told apart by looking inside them")
    @DisplayName("An archive cut off part-way through is damaged, whatever it is named")
    void aTruncatedArchiveIsBroken(@TempDir Path dir) throws IOException {
        byte[] whole = Files.readAllBytes(zipHolding(dir.resolve("whole.docx"), WORD_MAIN_PART));
        Path truncated = Files.write(dir.resolve("half-a-file.bin"), Arrays.copyOf(whole, whole.length / 2));

        claim(
                "an archive whose index was cut off cannot be opened, so it is damaged and removed",
                () -> assertThat(BrokenCheck.check(truncated).broken()).isTrue());
    }

    @Test
    @Story("The set of recognised images is what the conversion step can read")
    @DisplayName("A TIFF image is recognised, because the conversion step converts TIFF images")
    void aTiffIsRecognisedAsAnImage(@TempDir Path dir) throws IOException {
        Path tiff = Files.write(dir.resolve("scan-0001.tif"), signed(LITTLE_ENDIAN_TIFF_SIGNATURE, "scan data"));

        claim(
                "TIFF is one of the image formats the conversion step handles, so leaving it out would put"
                        + " real documents in the branch kept for files that are not documents at all",
                () -> assertThat(BrokenCheck.check(tiff).format()).isEqualTo(DetectedFormat.IMAGE));
    }

    @Test
    @Story("One marker, both documents and clutter behind it")
    @DisplayName("A legacy Word document and a Windows thumbnail cache open with the same marker, and are separated by their names")
    void theSharedOfficeContainerIsSeparatedByItsName(@TempDir Path dir) throws IOException {
        Path legacyWord = Files.write(dir.resolve("minutes.doc"), signed(OLE_COMPOUND_SIGNATURE, "document streams"));
        Path thumbnailCache = Files.write(dir.resolve("Thumbs.db"), signed(OLE_COMPOUND_SIGNATURE, "cached thumbnails"));

        claim(
                "an old Word document and a thumbnail cache open with exactly the same eight bytes, so the"
                        + " content alone puts both in one container class and can say nothing further",
                () -> assertThat(BrokenCheck.check(legacyWord).format())
                        .isEqualTo(BrokenCheck.check(thumbnailCache).format())
                        .isEqualTo(DetectedFormat.OLE_COMPOUND));
        claim(
                "inside that one class, and only inside it, the name is allowed to say which member this is",
                () -> assertThat(BrokenCheck.check(legacyWord).subtype()).contains(DetectedSubtype.LEGACY_WORD));
        claim(
                "a name that identifies no member of the class leaves the file in the class unlabelled,"
                        + " which is the honest answer and the one a thumbnail cache earns",
                () -> assertThat(BrokenCheck.check(thumbnailCache).subtype()).isEmpty());
        claim(
                "both files are intact and open perfectly well, so neither is damaged and neither is"
                        + " removed — being clutter rather than a document is a different question, and one"
                        + " nothing answers until a real archive has been measured",
                () -> assertThat(BrokenCheck.check(legacyWord).broken())
                        .isEqualTo(BrokenCheck.check(thumbnailCache).broken())
                        .isFalse());
    }

    @Test
    @Story("One marker, both documents and clutter behind it")
    @DisplayName("An old Word document under an unfamiliar name keeps its container but loses its finer label, and is still kept")
    void aLegacyWordDocumentUnderAnUnknownNameLosesOnlyItsLabel(@TempDir Path dir) throws IOException {
        Path renamed = Files.write(dir.resolve("archive-0042.dat"), signed(OLE_COMPOUND_SIGNATURE, "document streams"));

        claim(
                "the content still says which container this is, and no name can take that away",
                () -> assertThat(BrokenCheck.check(renamed).format()).isEqualTo(DetectedFormat.OLE_COMPOUND));
        claim(
                "an unfamiliar name simply adds nothing, so the finer label is absent rather than guessed",
                () -> assertThat(BrokenCheck.check(renamed).subtype()).isEmpty());
        claim(
                "the worst a misleading name can do is cost a finer label; it can never make a file damaged"
                        + " and can never lose it",
                () -> assertThat(BrokenCheck.check(renamed).broken()).isFalse());
    }

    @Test
    @Story("The name may narrow, but it may never cross a boundary the content has drawn")
    @DisplayName("A PDF named as an old Word document stays a PDF and takes no label from that name")
    void aNameCannotCarryALabelAcrossIntoAnotherClass(@TempDir Path dir) throws IOException {
        Path misnamed = Files.write(dir.resolve("minutes.doc"), WHOLE_PDF.getBytes(StandardCharsets.US_ASCII));

        claim(
                "the content is a PDF, so the file is a PDF, and the name does not get to reopen that",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.PDF));
        claim(
                "a name may only choose between members of the class the content already fixed; here it"
                        + " names a member of a different class, so it is ignored entirely",
                () -> assertThat(BrokenCheck.check(misnamed).subtype()).isEmpty());
    }

    @Test
    @Story("The name may narrow, but it may never cross a boundary the content has drawn")
    @DisplayName("Markdown is recognised from its name, because nothing in the content of any text file distinguishes it")
    void markdownIsNarrowedByItsName(@TempDir Path dir) throws IOException {
        Path markdown = Files.writeString(dir.resolve("release-notes.md"), "# Release notes\n\n- first change\n");

        claim(
                "every text file reads the same way as every other, so the content can only say that this"
                        + " is text",
                () -> assertThat(BrokenCheck.check(markdown).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "the conversion step reads Markdown structurally rather than as flat words, so which kind of"
                        + " text this is genuinely matters downstream, and the name is the only thing left"
                        + " that says",
                () -> assertThat(BrokenCheck.check(markdown).subtype()).contains(DetectedSubtype.MARKDOWN));
    }

    @Test
    @Story("The name may narrow, but it may never cross a boundary the content has drawn")
    @DisplayName("A web page saved under a .txt name is recognised as a web page, because its opening line says so")
    void aWebPageIsRecognisedFromItsOpeningLineRatherThanItsName(@TempDir Path dir) throws IOException {
        Path misnamed = Files.writeString(dir.resolve("saved-page.txt"), "<!DOCTYPE html>\n<title>Notes</title>\n");

        claim(
                "a web page is still text as far as the content test goes: it reads cleanly as words",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "a web page is the one kind of text that announces itself in its opening bytes, and where"
                        + " the content can answer, the name is not asked",
                () -> assertThat(BrokenCheck.check(misnamed).subtype()).contains(DetectedSubtype.HTML));
    }

    @Test
    @Story("The name may narrow, but it may never cross a boundary the content has drawn")
    @DisplayName("Text with no extension is kept as text with no finer label, rather than guessed at")
    void textWithNoExtensionKeepsNoFinerLabel(@TempDir Path dir) throws IOException {
        Path unnamed = Files.writeString(dir.resolve("README"), "Notes on the contents of this folder.\n");

        claim(
                "content that reads cleanly as words is text, whether or not anything names it",
                () -> assertThat(BrokenCheck.check(unnamed).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "with no name to narrow it and no content marker to read, no finer label is claimed; the"
                        + " file is kept and travels onward as ordinary text",
                () -> assertThat(BrokenCheck.check(unnamed).subtype()).isEmpty());
        claim(
                "nothing is removed for carrying no extension",
                () -> assertThat(BrokenCheck.check(unnamed).broken()).isFalse());
    }

    @Test
    @Story("Content that is not a document of any known kind")
    @DisplayName("A Windows shortcut is recognised as no known format, and is still not removed")
    void anUnrecognisedBinaryIsRecordedAndNotRemoved(@TempDir Path dir) throws IOException {
        Path shortcut = Files.write(dir.resolve("Report - Shortcut.lnk"), signed(WINDOWS_SHORTCUT_SIGNATURE, "target"));

        claim(
                "content matching no known format marker, that does not read as words either, is recorded"
                        + " as being of no known format",
                () -> assertThat(BrokenCheck.check(shortcut).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
        claim(
                "a shortcut is intact and perfectly valid — it is simply not a document — so calling it"
                        + " damaged would be the wrong word, and nothing is removed on this ground until a"
                        + " real archive has been measured to say how much of it there is",
                () -> assertThat(BrokenCheck.check(shortcut).broken()).isFalse());
    }

    @Test
    @Story("Content that is not a document of any known kind")
    @DisplayName("A Windows folder settings file reads as text and passes, which is the honest limit of this rule")
    void aTextualSystemFileStillPasses(@TempDir Path dir) throws IOException {
        Path folderSettings = Files.writeString(dir.resolve("desktop.ini"), "[.ShellClassInfo]\nIconResource=x.dll,0\n");

        claim(
                "this file genuinely is text and reads as text, so the rule reports it as text; recognising"
                        + " content is not the same as recognising usefulness, and this rule claims only the"
                        + " first",
                () -> assertThat(BrokenCheck.check(folderSettings).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "it lands with no finer label, in the same place an ordinary written note lands, so a count"
                        + " of text files cannot tell the two apart — which is what any later rule about"
                        + " clutter has to be read against",
                () -> assertThat(BrokenCheck.check(folderSettings).subtype()).isEmpty());
    }

    @Test
    @Story("Content that is not a document of any known kind")
    @DisplayName("An empty file is removed, and is recorded as one nothing was ever read from")
    void anEmptyFileIsBrokenAndRecordedAsUnread(@TempDir Path dir) throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.pdf"));

        claim(
                "an empty file is stopped before anything opens it, so no content was ever examined; that is"
                        + " a different fact from content that was examined and matched nothing, and the two"
                        + " are kept apart because a count of the second is what a later decision rests on",
                () -> assertThat(BrokenCheck.check(empty).format()).isEqualTo(DetectedFormat.FLOOR_STOPPED));
        claim(
                "an empty file carries nothing to extract, so it is removed",
                () -> assertThat(BrokenCheck.check(empty).broken()).isTrue());
    }

    /** A file beginning with {@code signature} and continuing with {@code trailingText}. */
    private static byte[] signed(byte[] signature, String trailingText) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(signature);
        bytes.write(trailingText.getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    /** A well-formed archive whose single entry is named {@code entryName}. */
    private static Path zipHolding(Path path, String entryName) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<part/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        return path;
    }
}
