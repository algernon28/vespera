package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * How stage 1 tells a video from its bytes, so that it can leave every video out of scope, whatever its
 * container (ADR-168).
 *
 * <p>Every rule is pinned from both sides. A video is recognised under a name that says something else,
 * because the bytes decide (ADR-094). And the files that share a video's container or its opening bytes
 * without being a video stay what they were before: a HEIF picture and an M4A recording in the same box
 * structure as an MP4, a CorelDRAW drawing in the same {@code RIFF} wrapper as an AVI, and a text file that
 * happens to open with the transport stream's sync byte, which is the letter {@code G}.
 *
 * <p>No database and no Spring context, matching {@code DetectedFormatTest}: the seam is one static method over a
 * real filesystem path.
 */
@Epic("Byte-level reduction")
@Feature("Format detection")
@Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
@Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
class VideoDetectionTest {

    /** The detected format a video is recorded as. */
    private static final DetectedFormat VIDEO_FORMAT = DetectedFormat.VIDEO;

    /** The box every ISO base media file opens with, at offset 4, after the box's own size. */
    private static final String FILE_TYPE_BOX = "ftyp";

    /** The size of an ISO base media box header: four bytes of size and four of type. */
    private static final int BOX_HEADER_LENGTH = 8;

    /**
     * Each file stage 1 is handed, under the name it carries, with the bytes it holds and what the bytes make it:
     * a video in every container that holds one, and the pictures, recordings and text that share a video's
     * container or its opening bytes staying what they were.
     */
    static Stream<Arguments> filesAndWhatTheirBytesMakeThem() throws IOException {
        return Stream.of(
                Arguments.of(
                        Named.of(
                                "an MP4 whose 24-byte ftyp box names the mp42 brand, as seven of the eight videos on the"
                                        + " measured archive did, is a video, although it is named as a PDF",
                                "Stacco_bianco_e_pressione_tasto_rosso.pdf"),
                        isoMedia("mp42", "isom", "mp42"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a recording whose major brand is M4A holds sound and no picture, and audio is not what"
                                        + " was decided about",
                                "meeting.m4a"),
                        isoMedia("M4A ", "M4A ", "mp42", "isom"),
                        DetectedFormat.UNRECOGNISED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filesAndWhatTheirBytesMakeThem")
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A video is told by its bytes in every container that holds one, and what only shares its container or opening bytes is not")
    void aVideoIsToldByItsBytes(String fileName, byte[] bytes, DetectedFormat expected, @TempDir Path dir)
            throws IOException {
        Path file = Files.write(dir.resolve(fileName), bytes);

        claim(
                "the bytes make the file " + expected + ", whatever it is called",
                () -> assertThat(formatOf(file)).isEqualTo(expected));
    }

    /** {@code text} as the bytes a text file holds. */
    private static byte[] text(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The name of the format stage 1 decides {@code file} is, as the text the ledger stores. */
    private static DetectedFormat formatOf(Path file) {
        return BrokenCheck.check(file).format();
    }

    /**
     * An ISO base media file: an {@code ftyp} box naming {@code majorBrand} and {@code compatibleBrands},
     * with a minor version of 0, then the boxes a movie goes on with. Big-endian, as every number in the
     * container is.
     */
    private static byte[] isoMedia(String majorBrand, String... compatibleBrands) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(ascii(majorBrand));
        body.write(new byte[4]); // minor version
        for (String brand : compatibleBrands) {
            body.write(ascii(brand));
        }
        byte[] fileType = box(BOX_HEADER_LENGTH + body.size(), FILE_TYPE_BOX, body.toByteArray());
        return concat(fileType, mediaData());
    }

    /** A box of {@code size} bytes as its header declares it, of {@code type}, carrying {@code body}. */
    private static byte[] box(int size, String type, byte[] body) {
        return ByteBuffer.allocate(BOX_HEADER_LENGTH + body.length)
                .putInt(size)
                .put(ascii(type))
                .put(body)
                .array();
    }

    /** An {@code mdat} box header, then binary bytes of the kind a coded picture is made of. */
    private static byte[] mediaData() {
        byte[] bytes = new byte[600];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 7 == 0 ? 0x00 : 0x80 + i % 0x70);
        }
        return concat(box(BOX_HEADER_LENGTH + bytes.length, "mdat", new byte[0]), bytes);
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
