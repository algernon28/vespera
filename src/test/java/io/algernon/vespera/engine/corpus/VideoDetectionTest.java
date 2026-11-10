package io.algernon.vespera.engine.corpus;

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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
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
     * The smallest {@code ftyp} box that can say anything: its header, a major brand and a minor version.
     * Each compatible brand adds four bytes.
     */
    private static final int SMALLEST_FILE_TYPE_BOX = 16;

    /** How far apart the sync bytes of an MPEG transport stream sit: one packet. */
    private static final int TRANSPORT_PACKET_LENGTH = 188;

    /** A BDAV transport stream's packet: a 4-byte timestamp, then an ordinary 188-byte packet. */
    private static final int TIMESTAMPED_PACKET_LENGTH = 192;

    /** Where a BDAV packet's sync byte sits, after its timestamp. */
    private static final int TIMESTAMP_LENGTH = 4;

    /** The byte every transport stream packet starts with, which is also the letter {@code G}. */
    private static final byte TRANSPORT_SYNC_BYTE = 0x47;

    /** The 16 bytes of the GUID an ASF file's header object is identified by, at offset 0. */
    private static final byte[] ASF_HEADER_GUID = {
        0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11,
        (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C
    };

    /** The fourteen fixed bytes of an MXF header partition pack's key (SMPTE ST 377-1). */
    private static final byte[] MXF_HEADER_PARTITION_KEY = {
        0x06, 0x0E, 0x2B, 0x34, 0x02, 0x05, 0x01, 0x01, 0x0D, 0x01, 0x02, 0x01, 0x01, 0x02
    };

    /** The four bytes of the EBML magic every Matroska and WebM file opens with. */
    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /** The six bytes a GIF opens with, the image signature the transport stream's sync byte shares a letter with. */
    private static final byte[] GIF89A_SIGNATURE = "GIF89a".getBytes(StandardCharsets.US_ASCII);

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
                                "an MP4 under the general isom brand is a video",
                                "PAX_1.0.36.docx"),
                        isoMedia("isom", "isom", "iso2", "avc1", "mp41"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a QuickTime movie is a video",
                                "demo.txt"),
                        isoMedia("qt  ", "qt  "),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a 3GP clip is a video",
                                "clip.png"),
                        isoMedia("3gp4", "3gp4", "isom"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "an iTunes video is a video although it lists the audio brand M4A among its compatible"
                                        + " brands: only the major brand can say a file holds nothing but sound",
                                "episode.m4a"),
                        isoMedia("M4V ", "M4V ", "M4A ", "mp42", "isom"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "an MP4 whose 24-byte ftyp box is followed by bytes spelling jpeg, a still-image brand,"
                                        + " is still a video: the brands are read to the end of the box its size declares, and no"
                                        + " further",
                                "after.mp4"),
                        concat(Arrays.copyOf(isoMedia("mp42", "isom", "mp42"), SMALLEST_FILE_TYPE_BOX + 8), box(12, "free", ascii("jpeg")), mediaData()),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a HEIC picture stays of no known kind: it is a picture in the same box structure as an"
                                        + " MP4, and the rule is about videos",
                                "IMG_0001.heic"),
                        isoMedia("heic", "mif1", "heic"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "an AVIF picture stays of no known kind too",
                                "photo.avif"),
                        isoMedia("avif", "avif", "mif1", "miaf"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a HEIF image sequence is one too, although only a compatible brand says so: every HEIF"
                                        + " file must list one of its structural brands there, whatever its major brand",
                                "burst.heics"),
                        isoMedia("iso8", "iso8", "msf1", "hevc"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a Canon raw photograph, which is ISO base media too, stays of no known kind",
                                "IMG_0002.cr3"),
                        isoMedia("crx ", "crx ", "isom"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a recording whose major brand is M4A holds sound and no picture, and audio is not what"
                                        + " was decided about",
                                "meeting.m4a"),
                        isoMedia("M4A ", "M4A ", "mp42", "isom"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a text file with ftyp at offset 4 is text: its first four bytes, read as a box size, are"
                                        + " far larger than the prefix, so they cannot open a video",
                                "notes.txt"),
                        text("abcdftypmp42 is how an MP4 opens, written out as words for a reader."),
                        DetectedFormat.PLAIN_TEXT),
                Arguments.of(
                        Named.of(
                                "a binary whose ftyp box declares only 8 bytes, too few for a major brand, stays of no"
                                        + " known kind",
                                "stub.mp4"),
                        concat(box(BOX_HEADER_LENGTH, FILE_TYPE_BOX, new byte[0]), mediaData()),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a file opening with a wide atom, as QuickTime wrote them before the ftyp box existed, is"
                                        + " a video",
                                "old.mov"),
                        concat(box(BOX_HEADER_LENGTH, "wide", new byte[0]), mediaData()),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and so is one opening with a moov atom",
                                "old movie.bin"),
                        box(108, "moov", zeros(100)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and one opening with an mdat atom",
                                "raw.mov"),
                        mediaData(),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and one opening with a pnot atom",
                                "preview.dat"),
                        box(20, "pnot", zeros(64)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "one opening with a free atom stays of no known kind: any writer of this container may"
                                        + " lead with padding, a HEIF picture's among them",
                                "padded.mov"),
                        concat(box(BOX_HEADER_LENGTH, "free", new byte[0]), mediaData()),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "text with wide as its fifth to eighth letters is text: the rule asks for bytes that do"
                                        + " not read as text",
                                "range.txt"),
                        text("The wide range of terminals is listed below."),
                        DetectedFormat.PLAIN_TEXT),
                Arguments.of(
                        Named.of(
                                "and so is text with free there",
                                "support.txt"),
                        text("Get free support from the help desk."),
                        DetectedFormat.PLAIN_TEXT),
                Arguments.of(
                        Named.of(
                                "a Matroska file opens with the EBML magic 1A 45 DF A3 and is a video, whatever it is"
                                        + " named",
                                "recording.txt"),
                        ebml("matroska"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and so is a WebM file",
                                "capture.pdf"),
                        ebml("webm"),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a RIFF file whose form type is AVI is a video",
                                "clip.doc"),
                        riff("AVI "),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a CorelDRAW drawing, 23 of which sit on the measured archive, stays of no known kind:"
                                        + " the RIFF wrapper says nothing, the form type after it does",
                                "logo.cdr"),
                        riff("CDRC"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "and so does a WAVE recording",
                                "beep.wav"),
                        riff("WAVE"),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "and a WEBP stays an image",
                                "banner.webp"),
                        riff("WEBP"),
                        DetectedFormat.IMAGE),
                Arguments.of(
                        Named.of(
                                "a file opening with the ASF header object's 16-byte GUID is a video",
                                "presentation.wmv.txt"),
                        concat(ASF_HEADER_GUID, littleEndianLong(730), zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a file opening with FLV and the version byte 01 is a video",
                                "tutorial.pdf"),
                        concat(new byte[] {'F', 'L', 'V', 0x01, 0x05, 0x00, 0x00, 0x00, 0x09}, zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "text opening with the three letters FLV is text: the version byte is what makes it a"
                                        + " video",
                                "formats.txt"),
                        text("FLV files are what Flash players streamed."),
                        DetectedFormat.PLAIN_TEXT),
                Arguments.of(
                        Named.of(
                                "a file opening with an MPEG pack header, 00 00 01 BA, is a video",
                                "VTS_01_1.dat"),
                        concat(new byte[] {0x00, 0x00, 0x01, (byte) 0xBA, 0x44, 0x00, 0x04, 0x00, 0x04, 0x01}, zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and so is one opening with a sequence header, 00 00 01 B3",
                                "track.bin"),
                        concat(new byte[] {0x00, 0x00, 0x01, (byte) 0xB3, 0x14, 0x00, (byte) 0xF0, 0x13}, zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a Windows icon, which opens 00 00 01 00, shares the first three bytes and stays of no"
                                        + " known kind: the fourth byte is what names an MPEG header",
                                "favicon.ico"),
                        concat(new byte[] {0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x10, 0x10, 0x00, 0x00, 0x01, 0x00, 0x20, 0x00}, zeros(200)),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a file with the sync byte 47 at offsets 0, 188 and 376 is a transport stream, and a"
                                        + " video",
                                "broadcast.txt"),
                        transportStream(TRANSPORT_PACKET_LENGTH, 0, 3),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "and so is a Blu-ray stream, whose 192-byte packets carry the sync byte after a 4-byte"
                                        + " timestamp, at offsets 4, 196 and 388",
                                "00001.pdf"),
                        transportStream(TIMESTAMPED_PACKET_LENGTH, TIMESTAMP_LENGTH, 3),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "text with the letter G at those three offsets is text: 47 is G, and the rule asks for"
                                        + " bytes that do not read as text",
                                "glossary.txt"),
                        text(textWithGAt(0, TRANSPORT_PACKET_LENGTH, 2 * TRANSPORT_PACKET_LENGTH)),
                        DetectedFormat.PLAIN_TEXT),
                Arguments.of(
                        Named.of(
                                "a GIF, which opens with G, stays an image even with 47 at 188 and 376",
                                "spinner.gif"),
                        gifWithGAt(TRANSPORT_PACKET_LENGTH, 2 * TRANSPORT_PACKET_LENGTH),
                        DetectedFormat.IMAGE),
                Arguments.of(
                        Named.of(
                                "a binary with only two of the three syncs stays of no known kind",
                                "two.bin"),
                        withoutTheThirdSync(transportStream(TRANSPORT_PACKET_LENGTH, 0, 3)),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "and so does one too short to carry the third",
                                "short.bin"),
                        Arrays.copyOf(transportStream(TRANSPORT_PACKET_LENGTH, 0, 3), 300),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "an Ogg file whose prefix holds the Theora identification header, 80 then theora, is a"
                                        + " video",
                                "lecture.txt"),
                        oggFirstPage(concat(new byte[] {(byte) 0x80}, ascii("theora"), new byte[] {3, 2, 1})),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "an Ogg file carrying Vorbis holds only sound, and stays of no known kind",
                                "song.ogg"),
                        oggFirstPage(concat(new byte[] {0x01}, ascii("vorbis"), zeros(23))),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "and so does one carrying Opus",
                                "voice.opus"),
                        oggFirstPage(concat(ascii("OpusHead"), new byte[] {1, 2, 0x38, 0x01})),
                        DetectedFormat.UNRECOGNISED),
                Arguments.of(
                        Named.of(
                                "a file opening with .RMF and a header size whose top two bytes are zero is a RealMedia"
                                        + " video",
                                "webinar.doc"),
                        concat(ascii(".RMF"), new byte[] {0x00, 0x00, 0x00, 0x12, 0x00, 0x01}, zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "a file opening with the fourteen fixed bytes of an MXF header partition key is a video",
                                "camera.bin"),
                        concat(MXF_HEADER_PARTITION_KEY, new byte[] {0x04, 0x00, (byte) 0x83, 0x00, 0x00, 0x58}, zeros(200)),
                        VIDEO_FORMAT),
                Arguments.of(
                        Named.of(
                                "text opening with the four letters .RMF is text",
                                "formats.txt"),
                        text(".RMF files are what RealPlayer saved."),
                        DetectedFormat.PLAIN_TEXT));
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

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("Recognising a video removes nothing by itself, and takes no finer label from its name")
    void recognisingAVideoRemovesNothingByItself(@TempDir Path dir) throws IOException {
        Path mp42 = Files.write(dir.resolve("Stacco_bianco_e_pressione_tasto_rosso.pdf"), isoMedia("mp42", "isom", "mp42"));

        claim(
                "an intact video is not damaged, so recognising one removes nothing by itself",
                () -> assertThat(BrokenCheck.check(mp42).broken()).isFalse());
        claim(
                "and the name is not consulted, so no finer label is taken from it",
                () -> assertThat(BrokenCheck.check(mp42).subtype()).isEmpty());
    }

    /** {@code text} as the bytes a text file holds. */
    private static byte[] text(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A transport stream with its third sync byte overwritten, so only two remain. */
    private static byte[] withoutTheThirdSync(byte[] stream) {
        stream[2 * TRANSPORT_PACKET_LENGTH] = 0x00;
        return stream;
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

    /**
     * The EBML header every Matroska and WebM file opens with, naming {@code docType}, then the start of the
     * segment that holds the tracks.
     */
    private static byte[] ebml(String docType) {
        byte[] name = docType.getBytes(StandardCharsets.US_ASCII);
        byte[] header = concat(
                new byte[] {0x42, (byte) 0x86, (byte) 0x81, 0x01}, // EBML version 1
                new byte[] {0x42, (byte) 0xF7, (byte) 0x81, 0x01}, // read version 1
                new byte[] {0x42, (byte) 0xF2, (byte) 0x81, 0x04}, // longest ID, 4 bytes
                new byte[] {0x42, (byte) 0xF3, (byte) 0x81, 0x08}, // longest size, 8 bytes
                new byte[] {0x42, (byte) 0x82, (byte) (0x80 | name.length)},
                name, // the document type
                new byte[] {0x42, (byte) 0x87, (byte) 0x81, 0x02}, // its version
                new byte[] {0x42, (byte) 0x85, (byte) 0x81, 0x02}); // and its read version
        return concat(
                EBML_MAGIC,
                new byte[] {(byte) (0x80 | header.length)},
                header,
                new byte[] {0x18, 0x53, (byte) 0x80, 0x67, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00},
                zeros(200));
    }

    /**
     * A {@code RIFF} file of {@code formType}: the wrapper, its little-endian length, the form type at
     * offset 8, and a first chunk of zeros. The form type alone is what separates one kind from another.
     */
    private static byte[] riff(String formType) {
        byte[] chunk = concat(ascii("LIST"), littleEndianInt(200), zeros(200));
        return concat(ascii("RIFF"), littleEndianInt(4 + chunk.length), ascii(formType), chunk);
    }

    /**
     * {@code packets} transport stream packets of {@code packetLength} bytes, each with the sync byte at
     * {@code syncOffset}, a header after it that names the programme table, and stuffing to the end.
     */
    private static byte[] transportStream(int packetLength, int syncOffset, int packets) {
        byte[] stream = new byte[packetLength * packets];
        Arrays.fill(stream, (byte) 0xFF);
        for (int packet = 0; packet < packets; packet++) {
            int at = packet * packetLength;
            Arrays.fill(stream, at, at + syncOffset, (byte) 0x00); // the timestamp, where there is one
            stream[at + syncOffset] = TRANSPORT_SYNC_BYTE;
            stream[at + syncOffset + 1] = 0x40; // payload starts here, programme table
            stream[at + syncOffset + 2] = 0x00;
            stream[at + syncOffset + 3] = 0x10;
        }
        return stream;
    }

    /** 600 bytes of ordinary prose, with the letter {@code G} at each of {@code offsets}. */
    private static String textWithGAt(int... offsets) {
        StringBuilder text = new StringBuilder();
        while (text.length() < 600) {
            text.append("glossary entries are listed in order of use. ");
        }
        text.setLength(600);
        for (int offset : offsets) {
            text.setCharAt(offset, 'G');
        }
        return text.toString();
    }

    /** A GIF's signature and screen descriptor, then zeros, with {@code 47} at each of {@code offsets}. */
    private static byte[] gifWithGAt(int... offsets) {
        byte[] gif = concat(GIF89A_SIGNATURE, new byte[] {0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00}, zeros(600));
        for (int offset : offsets) {
            gif[offset] = TRANSPORT_SYNC_BYTE;
        }
        return gif;
    }

    /**
     * The first page of an Ogg stream, marked as its beginning, carrying {@code packet} as its one segment.
     * The checksum is left at zero: stage 1 reads a signature, it does not verify a page.
     */
    private static byte[] oggFirstPage(byte[] packet) {
        byte[] header = ByteBuffer.allocate(27)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(ascii("OggS"))
                .put((byte) 0x00) // version
                .put((byte) 0x02) // beginning of stream
                .putLong(0L) // granule position
                .putInt(1) // stream serial number
                .putInt(0) // page sequence number
                .putInt(0) // checksum
                .put((byte) 1) // one segment
                .array();
        return concat(header, new byte[] {(byte) packet.length}, packet, zeros(100));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] zeros(int length) {
        return new byte[length];
    }

    private static byte[] littleEndianInt(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static byte[] littleEndianLong(long value) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            joined.writeBytes(part);
        }
        return joined.toByteArray();
    }
}
