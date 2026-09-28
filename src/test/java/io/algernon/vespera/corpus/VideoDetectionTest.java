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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How stage 1 tells a video from its bytes, so that it can leave every video out of scope, whatever its
 * container (ADR-170).
 *
 * <p>Every rule is pinned from both sides. A video is recognised under a name that says something else,
 * because the bytes decide (ADR-094). And the files that share a video's container or its opening bytes
 * without being a video stay what they were before: a HEIF picture and an M4A recording in the same box
 * structure as an MP4, a CorelDRAW drawing in the same {@code RIFF} wrapper as an AVI, and a text file that
 * happens to open with the transport stream's sync byte, which is the letter {@code G}.
 *
 * <p>The new value is named by its string, {@value #VIDEO_FORMAT}, so this compiles before it exists. No
 * database and no Spring context, matching {@code DetectedFormatTest}: the seam is one static method over a
 * real filesystem path.
 */
@Epic("Byte-level reduction")
@Feature("Format detection")
@Link(name = "ADR-170", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
@Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
class VideoDetectionTest {

    /** The name of the detected format a video is recorded as, written as text until it exists. */
    private static final String VIDEO_FORMAT = "VIDEO";

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

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("An MP4, a QuickTime movie and a 3GP clip are videos, whatever they are called")
    void anIsoBaseMediaFileIsAVideoWhateverItIsCalled(@TempDir Path dir) throws IOException {
        Path mp42 = Files.write(dir.resolve("Stacco_bianco_e_pressione_tasto_rosso.pdf"), isoMedia("mp42", "isom", "mp42"));
        Path isom = Files.write(dir.resolve("PAX_1.0.36.docx"), isoMedia("isom", "isom", "iso2", "avc1", "mp41"));
        Path quickTime = Files.write(dir.resolve("demo.txt"), isoMedia("qt  ", "qt  "));
        Path thirdGeneration = Files.write(dir.resolve("clip.png"), isoMedia("3gp4", "3gp4", "isom"));
        Path iTunesVideo = Files.write(dir.resolve("episode.m4a"), isoMedia("M4V ", "M4V ", "M4A ", "mp42", "isom"));

        claim(
                "an MP4 whose " + (SMALLEST_FILE_TYPE_BOX + 8) + "-byte " + FILE_TYPE_BOX + " box names the"
                        + " mp42 brand, as seven of the eight videos on the measured archive did, is a video,"
                        + " although it is named as a PDF",
                () -> assertThat(formatOf(mp42)).isEqualTo(VIDEO_FORMAT));
        claim(
                "and so are one under the general isom brand, a QuickTime movie and a 3GP clip",
                () -> assertThat(List.of(formatOf(isom), formatOf(quickTime), formatOf(thirdGeneration)))
                        .containsOnly(VIDEO_FORMAT));
        claim(
                "an iTunes video is a video although it lists the audio brand M4A among its compatible brands:"
                        + " only the major brand can say a file holds nothing but sound",
                () -> assertThat(formatOf(iTunesVideo)).isEqualTo(VIDEO_FORMAT));
        claim(
                "recognising a video removes nothing by itself: an intact video is not damaged",
                () -> assertThat(BrokenCheck.check(mp42).broken()).isFalse());
        claim(
                "and the name is not consulted, so no finer label is taken from it",
                () -> assertThat(BrokenCheck.check(mp42).subtype()).isEmpty());
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A HEIC or AVIF picture, a camera's raw file and an M4A recording share the MP4's container and are not videos")
    void aStillImageOrAnAudioFileInTheSameContainerIsNotAVideo(@TempDir Path dir) throws IOException {
        Path heic = Files.write(dir.resolve("IMG_0001.heic"), isoMedia("heic", "mif1", "heic"));
        Path avif = Files.write(dir.resolve("photo.avif"), isoMedia("avif", "avif", "mif1", "miaf"));
        Path imageSequence = Files.write(dir.resolve("burst.heics"), isoMedia("iso8", "iso8", "msf1", "hevc"));
        Path cameraRaw = Files.write(dir.resolve("IMG_0002.cr3"), isoMedia("crx ", "crx ", "isom"));
        Path recording = Files.write(dir.resolve("meeting.m4a"), isoMedia("M4A ", "M4A ", "mp42", "isom"));

        claim(
                "a HEIC and an AVIF picture stay what they are today, of no known kind: they are pictures in"
                        + " the same box structure as an MP4, and the rule is about videos",
                () -> assertThat(List.of(BrokenCheck.check(heic).format(), BrokenCheck.check(avif).format()))
                        .containsOnly(DetectedFormat.UNRECOGNISED));
        claim(
                "a HEIF image sequence is one too, although only a compatible brand says so: every HEIF file"
                        + " must list one of its structural brands there, whatever its major brand",
                () -> assertThat(BrokenCheck.check(imageSequence).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
        claim(
                "and so is a Canon raw photograph, which is ISO base media too",
                () -> assertThat(BrokenCheck.check(cameraRaw).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
        claim(
                "a recording whose major brand is M4A holds sound and no picture, and audio is not what was"
                        + " decided about",
                () -> assertThat(BrokenCheck.check(recording).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("The word ftyp at offset 4 is not enough: the box it names must be able to hold a brand")
    void anFtypBoxTheBytesCannotHoldIsNotAVideo(@TempDir Path dir) throws IOException {
        Path prose = Files.writeString(
                dir.resolve("notes.txt"), "abcdftypmp42 is how an MP4 opens, written out as words for a reader.");
        Path tooSmall = Files.write(
                dir.resolve("stub.mp4"),
                concat(box(BOX_HEADER_LENGTH, FILE_TYPE_BOX, new byte[0]), mediaData()));

        claim(
                "a text file with " + FILE_TYPE_BOX + " at offset 4 is text: its first four bytes, read as a"
                        + " box size, are far larger than the prefix, so they cannot open a video",
                () -> assertThat(BrokenCheck.check(prose).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "and a binary whose " + FILE_TYPE_BOX + " box declares only " + BOX_HEADER_LENGTH + " bytes,"
                        + " too few for a major brand, stays of no known kind",
                () -> assertThat(BrokenCheck.check(tooSmall).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("An older QuickTime movie with no ftyp box is a video, and text whose fifth letter starts 'wide' is not")
    void aQuickTimeFileWithNoFtypIsAVideoAndTextThatLooksLikeOneIsNot(@TempDir Path dir) throws IOException {
        Path wide = Files.write(dir.resolve("old.mov"), concat(box(BOX_HEADER_LENGTH, "wide", new byte[0]), mediaData()));
        Path movie = Files.write(dir.resolve("old movie.bin"), box(108, "moov", zeros(100)));
        Path preview = Files.write(dir.resolve("preview.dat"), box(20, "pnot", zeros(64)));
        Path padded = Files.write(dir.resolve("padded.mov"), concat(box(BOX_HEADER_LENGTH, "free", new byte[0]), mediaData()));
        Path wideProse = Files.writeString(dir.resolve("range.txt"), "The wide range of terminals is listed below.");
        Path freeProse = Files.writeString(dir.resolve("support.txt"), "Get free support from the help desk.");

        claim(
                "a file opening with a wide, a moov or a pnot atom, as QuickTime wrote them before the ftyp box"
                        + " existed, is a video",
                () -> assertThat(List.of(formatOf(wide), formatOf(movie), formatOf(preview)))
                        .containsOnly(VIDEO_FORMAT));
        claim(
                "one opening with a free atom stays of no known kind: any writer of this container may lead with"
                        + " padding, a HEIF picture's among them",
                () -> assertThat(BrokenCheck.check(padded).format()).isEqualTo(DetectedFormat.UNRECOGNISED));
        claim(
                "and text with wide or free as its fifth to eighth letters is text: the rule asks for bytes that"
                        + " do not read as text",
                () -> assertThat(List.of(BrokenCheck.check(wideProse).format(), BrokenCheck.check(freeProse).format()))
                        .containsOnly(DetectedFormat.PLAIN_TEXT));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A Matroska and a WebM file are videos, told by the EBML magic they open with")
    void matroskaAndWebmAreVideos(@TempDir Path dir) throws IOException {
        Path matroska = Files.write(dir.resolve("recording.txt"), ebml("matroska"));
        Path webm = Files.write(dir.resolve("capture.pdf"), ebml("webm"));

        claim(
                "both open with the EBML magic 1A 45 DF A3, and both are videos, whatever they are named",
                () -> assertThat(List.of(formatOf(matroska), formatOf(webm))).containsOnly(VIDEO_FORMAT));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("An AVI is a video, and a CorelDRAW drawing or a WAVE recording in the same RIFF wrapper is not")
    void anAviIsAVideoAndNoOtherRiffIs(@TempDir Path dir) throws IOException {
        Path avi = Files.write(dir.resolve("clip.doc"), riff("AVI "));
        Path drawing = Files.write(dir.resolve("logo.cdr"), riff("CDRC"));
        Path recording = Files.write(dir.resolve("beep.wav"), riff("WAVE"));
        Path webp = Files.write(dir.resolve("banner.webp"), riff("WEBP"));

        claim(
                "a RIFF file whose form type is AVI is a video",
                () -> assertThat(formatOf(avi)).isEqualTo(VIDEO_FORMAT));
        claim(
                "a CorelDRAW drawing, 23 of which sit on the measured archive, and a WAVE recording stay of no"
                        + " known kind: the RIFF wrapper says nothing, the form type after it does",
                () -> assertThat(List.of(BrokenCheck.check(drawing).format(), BrokenCheck.check(recording).format()))
                        .containsOnly(DetectedFormat.UNRECOGNISED));
        claim(
                "and a WEBP stays an image",
                () -> assertThat(BrokenCheck.check(webp).format()).isEqualTo(DetectedFormat.IMAGE));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A Windows Media file is a video, told by its header object's GUID")
    void anAsfFileIsAVideo(@TempDir Path dir) throws IOException {
        Path asf = Files.write(dir.resolve("presentation.wmv.txt"), concat(ASF_HEADER_GUID, littleEndianLong(730), zeros(200)));

        claim(
                "a file opening with the ASF header object's 16-byte GUID is a video",
                () -> assertThat(formatOf(asf)).isEqualTo(VIDEO_FORMAT));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A Flash video is a video, told by FLV and its version byte")
    void anFlvFileIsAVideo(@TempDir Path dir) throws IOException {
        Path flv = Files.write(
                dir.resolve("tutorial.pdf"),
                concat(new byte[] {'F', 'L', 'V', 0x01, 0x05, 0x00, 0x00, 0x00, 0x09}, zeros(200)));

        claim(
                "a file opening with FLV and the version byte 01 is a video",
                () -> assertThat(formatOf(flv)).isEqualTo(VIDEO_FORMAT));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("An MPEG program stream and an MPEG video elementary stream are videos")
    void mpegProgramAndElementaryStreamsAreVideos(@TempDir Path dir) throws IOException {
        Path programStream = Files.write(
                dir.resolve("VTS_01_1.dat"),
                concat(new byte[] {0x00, 0x00, 0x01, (byte) 0xBA, 0x44, 0x00, 0x04, 0x00, 0x04, 0x01}, zeros(200)));
        Path elementaryStream = Files.write(
                dir.resolve("track.bin"),
                concat(new byte[] {0x00, 0x00, 0x01, (byte) 0xB3, 0x14, 0x00, (byte) 0xF0, 0x13}, zeros(200)));

        claim(
                "a file opening with a pack header, 00 00 01 BA, or a sequence header, 00 00 01 B3, is a video",
                () -> assertThat(List.of(formatOf(programStream), formatOf(elementaryStream)))
                        .containsOnly(VIDEO_FORMAT));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A transport stream is a video only with three syncs a packet apart and bytes that are not text")
    void aTransportStreamIsAVideoOnlyWithThreeSyncsAndNoText(@TempDir Path dir) throws IOException {
        Path transport = Files.write(dir.resolve("broadcast.txt"), transportStream(TRANSPORT_PACKET_LENGTH, 0, 3));
        Path timestamped = Files.write(
                dir.resolve("00001.pdf"), transportStream(TIMESTAMPED_PACKET_LENGTH, TIMESTAMP_LENGTH, 3));
        Path prose = Files.writeString(dir.resolve("glossary.txt"), textWithGAt(0, TRANSPORT_PACKET_LENGTH, 2 * TRANSPORT_PACKET_LENGTH));
        Path gif = Files.write(dir.resolve("spinner.gif"), gifWithGAt(TRANSPORT_PACKET_LENGTH, 2 * TRANSPORT_PACKET_LENGTH));
        byte[] twoSyncs = transportStream(TRANSPORT_PACKET_LENGTH, 0, 3);
        twoSyncs[2 * TRANSPORT_PACKET_LENGTH] = 0x00;
        Path missingThirdSync = Files.write(dir.resolve("two.bin"), twoSyncs);
        Path tooShort = Files.write(
                dir.resolve("short.bin"), Arrays.copyOf(transportStream(TRANSPORT_PACKET_LENGTH, 0, 3), 300));

        claim(
                "a file with the sync byte 47 at offsets 0, 188 and 376 is a transport stream, and a video",
                () -> assertThat(formatOf(transport)).isEqualTo(VIDEO_FORMAT));
        claim(
                "and so is a Blu-ray stream, whose 192-byte packets carry the sync byte after a 4-byte"
                        + " timestamp, at offsets 4, 196 and 388",
                () -> assertThat(formatOf(timestamped)).isEqualTo(VIDEO_FORMAT));
        claim(
                "text with the letter G at those three offsets is text: 47 is G, and the rule asks for bytes"
                        + " that do not read as text",
                () -> assertThat(BrokenCheck.check(prose).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
        claim(
                "a GIF, which opens with G, stays an image even with 47 at 188 and 376",
                () -> assertThat(BrokenCheck.check(gif).format()).isEqualTo(DetectedFormat.IMAGE));
        claim(
                "a binary with only two of the three syncs, or too short to carry the third, stays of no known"
                        + " kind",
                () -> assertThat(List.of(BrokenCheck.check(missingThirdSync).format(), BrokenCheck.check(tooShort).format()))
                        .containsOnly(DetectedFormat.UNRECOGNISED));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("An Ogg file is a video when it carries Theora, and not when it carries only sound")
    void anOggFileIsAVideoOnlyWhenItCarriesTheora(@TempDir Path dir) throws IOException {
        Path theora = Files.write(
                dir.resolve("lecture.txt"), oggFirstPage(concat(new byte[] {(byte) 0x80}, ascii("theora"), new byte[] {3, 2, 1})));
        Path vorbis = Files.write(
                dir.resolve("song.ogg"), oggFirstPage(concat(new byte[] {0x01}, ascii("vorbis"), zeros(23))));
        Path opus = Files.write(
                dir.resolve("voice.opus"), oggFirstPage(concat(ascii("OpusHead"), new byte[] {1, 2, 0x38, 0x01})));

        claim(
                "an Ogg file whose prefix holds the Theora identification header, 80 then theora, is a video",
                () -> assertThat(formatOf(theora)).isEqualTo(VIDEO_FORMAT));
        claim(
                "one carrying Vorbis or Opus holds only sound, and stays of no known kind",
                () -> assertThat(List.of(BrokenCheck.check(vorbis).format(), BrokenCheck.check(opus).format()))
                        .containsOnly(DetectedFormat.UNRECOGNISED));
    }

    @Test
    @Story("Videos are out of scope, whatever holds them")
    @DisplayName("A RealMedia and an MXF file are videos, and text that opens like RealMedia is not")
    void realMediaAndMxfAreVideosAndTextOpeningLikeRealMediaIsNot(@TempDir Path dir) throws IOException {
        Path realMedia = Files.write(
                dir.resolve("webinar.doc"),
                concat(ascii(".RMF"), new byte[] {0x00, 0x00, 0x00, 0x12, 0x00, 0x01}, zeros(200)));
        Path mxf = Files.write(
                dir.resolve("camera.bin"),
                concat(MXF_HEADER_PARTITION_KEY, new byte[] {0x04, 0x00, (byte) 0x83, 0x00, 0x00, 0x58}, zeros(200)));
        Path prose = Files.writeString(dir.resolve("formats.txt"), ".RMF files are what RealPlayer saved.");

        claim(
                "a file opening with .RMF and a header size whose top two bytes are zero is a RealMedia video",
                () -> assertThat(formatOf(realMedia)).isEqualTo(VIDEO_FORMAT));
        claim(
                "a file opening with the fourteen fixed bytes of an MXF header partition key is a video",
                () -> assertThat(formatOf(mxf)).isEqualTo(VIDEO_FORMAT));
        claim(
                "and text opening with the four letters .RMF is text",
                () -> assertThat(BrokenCheck.check(prose).format()).isEqualTo(DetectedFormat.PLAIN_TEXT));
    }

    /** The name of the format stage 1 decides {@code file} is, as the text the ledger stores. */
    private static String formatOf(Path file) {
        return BrokenCheck.check(file).format().name();
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
