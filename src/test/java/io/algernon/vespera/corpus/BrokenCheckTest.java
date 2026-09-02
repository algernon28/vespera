package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link BrokenCheck} against real files, built rather than described: a corrupt container is the
 * point, so the fixture is the corruption itself (ADR-068).
 *
 * <p>No database, no Spring context — {@link BrokenCheck} is a plain method over a filesystem path,
 * so the seam is the method itself, matching {@code WalkTest}'s direct-against-a-real-filesystem
 * style rather than a Batch step's.
 */
@Epic("Byte-level reduction")
@Feature("Broken check")
@Issue("36")
@Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
class BrokenCheckTest {

    @Test
    @Story("The cross-format floor")
    @DisplayName("An empty file is broken, whatever its extension")
    void anEmptyFileIsBrokenWhateverItsExtension(@TempDir Path dir) throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.docx"));

        claim(
                "an empty file carries no content to extract, so it is broken regardless of format",
                () -> assertThat(BrokenCheck.check(empty).broken()).isTrue());
        claim(
                "the reason names emptiness, not a format-specific failure",
                () -> assertThat(BrokenCheck.check(empty).reason()).contains("empty"));
    }

    @Test
    @Story("The cross-format floor")
    @DisplayName("A file that vanished before the check runs is broken, not a thrown exception")
    void aFileThatCannotBeReadIsBroken(@TempDir Path dir) throws IOException {
        Path vanished = dir.resolve("gone.txt");
        Files.writeString(vanished, "here for a moment");
        Files.delete(vanished);

        claim(
                "a file the walk recorded but that is no longer openable is broken, reported rather than"
                        + " thrown, so one occurrence never aborts a whole stage 1 run",
                () -> assertThat(BrokenCheck.check(vanished).broken()).isTrue());
    }

    @Test
    @Story("Text needs no structural check")
    @DisplayName("Any non-empty .txt file passes, since plain text has no container to validate")
    void nonEmptyTextFilePasses(@TempDir Path dir) throws IOException {
        Path text = Files.writeString(dir.resolve("notes.txt"), "plain text, nothing to validate");

        claim(
                "a non-empty .txt file is never broken: there is no structural check beyond the floor",
                () -> assertThat(BrokenCheck.check(text).broken()).isFalse());
    }

    @Test
    @Story("The .docx structural check")
    @DisplayName("A .docx with a valid zip container passes")
    void validDocxPasses(@TempDir Path dir) throws IOException {
        Path docx = validDocx(dir.resolve("valid.docx"));

        claim(
                "a well-formed zip container is not broken by this stage",
                () -> assertThat(BrokenCheck.check(docx).broken()).isFalse());
    }

    @Test
    @Story("The .docx structural check")
    @DisplayName("A .docx truncated mid-container is broken")
    void truncatedDocxIsBroken(@TempDir Path dir) throws IOException {
        Path wholeDocx = validDocx(dir.resolve("whole.docx"));
        byte[] wholeBytes = Files.readAllBytes(wholeDocx);
        Path truncated = dir.resolve("truncated.docx");
        Files.write(truncated, java.util.Arrays.copyOf(wholeBytes, wholeBytes.length / 2));

        claim(
                "a zip whose central directory was cut off is unreadable as a container, so it is broken",
                () -> assertThat(BrokenCheck.check(truncated).broken()).isTrue());
        claim(
                "the reason names the zip container, not a guess at the document inside it",
                () -> assertThat(BrokenCheck.check(truncated).reason()).contains("zip"));
    }

    @Test
    @Story("The .docx structural check")
    @DisplayName("A .docx with a valid container but garbage content inside is NOT caught here")
    void docxWithValidContainerButGarbageContentIsNotCaughtHere(@TempDir Path dir) throws IOException {
        // A valid zip holding an entry named like the real part, but not real OOXML -- this stage
        // checks the container, not what is inside it (ADR-068's deliberate boundary with
        // extraction-failed).
        Path docx = dir.resolve("garbage-content.docx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("not valid OOXML at all".getBytes());
            zip.closeEntry();
        }

        claim(
                "a valid container is not broken here, however malformed the document inside it is --"
                        + " that surfaces at extraction instead",
                () -> assertThat(BrokenCheck.check(docx).broken()).isFalse());
    }

    @Test
    @Story("The .pdf structural check")
    @DisplayName("A .pdf with a header and trailer passes")
    void validPdfPasses(@TempDir Path dir) throws IOException {
        Path pdf = Files.write(dir.resolve("valid.pdf"), "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n%%EOF".getBytes());

        claim(
                "a pdf carrying both the header and the trailer is not broken",
                () -> assertThat(BrokenCheck.check(pdf).broken()).isFalse());
    }

    @Test
    @Story("The .pdf structural check")
    @DisplayName("A .pdf missing the %PDF- header is broken")
    void pdfMissingHeaderIsBroken(@TempDir Path dir) throws IOException {
        Path pdf = Files.write(dir.resolve("no-header.pdf"), "not a pdf at all, just some bytes\n%%EOF".getBytes());

        claim(
                "no %PDF- header means the file is not recognisable as a pdf at all",
                () -> assertThat(BrokenCheck.check(pdf).broken()).isTrue());
        claim(
                "the reason names the missing header",
                () -> assertThat(BrokenCheck.check(pdf).reason()).contains("header"));
    }

    @Test
    @Story("The .pdf structural check")
    @DisplayName("A .pdf missing the %%EOF trailer is broken")
    void pdfMissingTrailerIsBroken(@TempDir Path dir) throws IOException {
        Path pdf = Files.write(dir.resolve("no-trailer.pdf"), "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n".getBytes());

        claim(
                "a truncated pdf that never reaches %%EOF is broken",
                () -> assertThat(BrokenCheck.check(pdf).broken()).isTrue());
        claim(
                "the reason names the missing trailer",
                () -> assertThat(BrokenCheck.check(pdf).reason()).contains("trailer"));
    }

    @Test
    @Story("The image structural check")
    @DisplayName("A .png with a valid signature passes")
    void validPngPasses(@TempDir Path dir) throws IOException {
        byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        Path png = dir.resolve("valid.png");
        Files.write(png, pngSignature, StandardOpenOption.CREATE);
        Files.write(png, "fake but signed image data".getBytes(), StandardOpenOption.APPEND);

        claim(
                "a recognised PNG signature is not broken",
                () -> assertThat(BrokenCheck.check(png).broken()).isFalse());
    }

    @Test
    @Story("The image structural check")
    @DisplayName("An image file with an unrecognised signature is broken")
    void imageWithUnrecognisedSignatureIsBroken(@TempDir Path dir) throws IOException {
        Path png = Files.write(dir.resolve("not-really.png"), "this is not an image at all".getBytes());

        claim(
                "bytes that match no known image signature are broken",
                () -> assertThat(BrokenCheck.check(png).broken()).isTrue());
        claim(
                "the reason names the signature as the failed check",
                () -> assertThat(BrokenCheck.check(png).reason()).contains("signature"));
    }

    private static Path validDocx(Path path) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return path;
    }
}
