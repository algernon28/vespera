package io.algernon.vespera.engine.corpus;

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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /** A PDF with its trailer cut off, which is the one thing the structural check catches. */
    private static final String PDF_WITHOUT_ITS_TRAILER = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** The entry a Word document always carries, and the only thing separating one from any zip. */
    private static final String WORD_MAIN_PART = "word/document.xml";

    /**
     * Files whose name says one thing and whose content another, each with the format its content makes it and
     * whether it is damaged: the content decides both.
     */
    static Stream<Arguments> filesNamedAsSomethingElse() throws IOException {
        return Stream.of(
                Arguments.of(
                        Named.of(
                                "a truncated PDF saved under a .png name is still a PDF, gets the check a PDF gets,"
                                        + " and stopping before its end marker it is damaged and removed",
                                "holiday-photo.png"),
                        PDF_WITHOUT_ITS_TRAILER.getBytes(StandardCharsets.US_ASCII),
                        DetectedFormat.PDF,
                        true),
                Arguments.of(
                        Named.of(
                                "a real image saved under a .docx name is an image, never opened as an archive, and"
                                        + " matching a known image marker is itself the validity check",
                                "report.docx"),
                        signed(PNG_SIGNATURE, "image data follows"),
                        DetectedFormat.IMAGE,
                        false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filesNamedAsSomethingElse")
    @Story("The content decides, not the name")
    @DisplayName("A file is checked as what its content is, whatever its name says")
    void aFileIsCheckedAsWhatItsContentIs(String fileName, byte[] content, DetectedFormat format, boolean damaged,
            @TempDir Path dir) throws IOException {
        Path misnamed = Files.write(dir.resolve(fileName), content);

        claim(
                "the content makes the file " + format + ", whatever it is called on disk",
                () -> assertThat(BrokenCheck.check(misnamed).format()).isEqualTo(format));
        claim(
                damaged ? "and it is damaged, so it is removed" : "and it is intact, so it is kept",
                () -> assertThat(BrokenCheck.check(misnamed).broken()).isEqualTo(damaged));
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
