package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntBinaryOperator;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The difference hash ADR-150 §3 defines, against the golden values its research record computed with
 * an independent implementation (research §6c). Every rule that reads the hash rests on it being the
 * same number wherever it is computed, so each row below pins one step of the definition: the direction
 * of a comparison, the luminance weights, compositing over white, reading a palette image through its
 * palette, the integer assignment of a pixel to a column, and the smallest image that has a hash.
 *
 * <p>The images are built here and encoded as PNG, which is lossless, so the pixels the hash reads are
 * exactly the ones written (ADR-063).
 */
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("286")
@Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
class DifferenceHashTest {

    /** Every bit set: each cell brighter than the one to its left, in every row. */
    private static final long ALL_RISING = 0xffffffffffffffffL;

    /** No bit set. */
    private static final long NONE_RISING = 0L;

    /** The width and height most golden images share: wide enough for ten pixels per column. */
    private static final int WIDTH = 90;

    private static final int HEIGHT = 80;

    /** Opaque black, and fully transparent black, as ARGB. */
    private static final int OPAQUE_BLACK = 0xff000000;

    private static final int TRANSPARENT_BLACK = 0x00000000;

    /** The PNG colour type of an image stored as indices into a palette. */
    private static final int PNG_PALETTE = 3;

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("A picture brightening left to right sets every bit, and one darkening sets none")
    void readsTheDirectionOfEveryComparison() {
        claim(
                "a grey ramp from dark on the left to light on the right sets all 64 bits, because every"
                        + " cell is brighter than the cell to its left",
                () -> assertThat(bitsOf(grey(WIDTH, HEIGHT, (x, y) -> x * 255 / (WIDTH - 1)))).isEqualTo(ALL_RISING));
        claim(
                "and the same ramp reversed sets none, so a cell equal to or darker than its left neighbour"
                        + " is a zero",
                () -> assertThat(bitsOf(grey(WIDTH, HEIGHT, (x, y) -> 255 - x * 255 / (WIDTH - 1))))
                        .isEqualTo(NONE_RISING));
        claim(
                "ten-pixel stripes starting black alternate the bits within every row, the first bit read"
                        + " being the most significant",
                () -> assertThat(bitsOf(rgb(WIDTH, HEIGHT, (x, y) -> (x / 10) % 2 == 0 ? 0x000000 : 0xffffff)))
                        .isEqualTo(0xaaaaaaaaaaaaaaaaL));
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("Colour is weighed by the stated luminance, and transparency is read over white")
    void weighsColourAndCompositesOverWhite() {
        claim(
                "pure red beside pure green rises at the boundary, because green weighs 587 against red's"
                        + " 299",
                () -> assertThat(bitsOf(rgb(WIDTH, HEIGHT, (x, y) -> x < WIDTH / 2 ? 0xff0000 : 0x00ff00)))
                        .isEqualTo(0x1818181818181818L));
        claim(
                "and pure red beside pure blue does not, because blue weighs only 114",
                () -> assertThat(bitsOf(rgb(WIDTH, HEIGHT, (x, y) -> x < WIDTH / 2 ? 0xff0000 : 0x0000ff)))
                        .isEqualTo(NONE_RISING));
        claim(
                "a transparent right half reads as white, not as the black its colour channels hold, so it"
                        + " rises at the boundary as black beside white does",
                () -> assertThat(bitsOf(argb(WIDTH, HEIGHT, (x, y) -> x < WIDTH / 2 ? OPAQUE_BLACK : TRANSPARENT_BLACK)))
                        .isEqualTo(0x1818181818181818L));
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("A palette picture is read through its palette, as the same picture in full colour is")
    void readsAPaletteImageThroughItsPalette() {
        byte[] redGreen = paletteHalves(new int[] {0xffff0000, 0xff00ff00});

        claim(
                "the red-beside-green picture stored as a palette PNG is encoded with colour type 3, so the"
                        + " fixture is the palette case and not a full-colour one",
                () -> assertThat(colourTypeOf(redGreen)).isEqualTo(PNG_PALETTE));
        claim(
                "and it hashes to the full-colour picture's 0x1818181818181818: the hash reads each pixel's"
                        + " palette colour, not its palette index as if the index were red",
                () -> assertThat(DifferenceHash.of(redGreen).orElseThrow().bits()).isEqualTo(0x1818181818181818L));
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("A palette picture's transparency is read over white, as a full-colour picture's is")
    void compositesAPaletteImagesTransparencyOverWhite() {
        byte[] blackTransparent = paletteHalves(new int[] {OPAQUE_BLACK, TRANSPARENT_BLACK});

        claim(
                "the opaque-black-beside-transparent picture stored as a palette PNG is encoded with colour"
                        + " type 3 and carries a transparency chunk",
                () -> {
                    assertThat(colourTypeOf(blackTransparent)).isEqualTo(PNG_PALETTE);
                    assertThat(new String(blackTransparent, StandardCharsets.ISO_8859_1)).contains("tRNS");
                });
        claim(
                "and it hashes to 0x1818181818181818 as the full-colour picture does, not to 0: the"
                        + " transparent half reads as white through the palette's alpha",
                () -> assertThat(DifferenceHash.of(blackTransparent).orElseThrow().bits())
                        .isEqualTo(0x1818181818181818L));
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("A pixel belongs to the column its position divides into, in whole numbers")
    void assignsAPixelToItsColumnByIntegerDivision() {
        claim(
                "on a 100-pixel-wide picture a white column at x = 11 falls in the first of the nine columns"
                        + " (11 times 9 is 99, and 99 over 100 is 0), so no comparison sees it rise",
                () -> assertThat(bitsOf(rgb(100, 50, (x, y) -> x == 11 ? 0xffffff : 0x000000))).isEqualTo(NONE_RISING));
        claim(
                "while a white column at x = 12 falls in the second (108 over 100 is 1), so the first bit of"
                        + " every row is set",
                () -> assertThat(bitsOf(rgb(100, 50, (x, y) -> x == 12 ? 0xffffff : 0x000000)))
                        .isEqualTo(0x8080808080808080L));
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("A picture smaller than the grid, or one that cannot be read as an image, has no hash")
    void hasNoHashBelowTheGridOrForBytesThatAreNoImage() {
        claim(
                "nine by eight pixels is the smallest picture with a hash, one pixel per cell",
                () -> assertThat(bitsOf(grey(9, 8, (x, y) -> x * 30))).isEqualTo(ALL_RISING));
        claim(
                "eight by eight has none, because nine columns cannot each hold a pixel",
                () -> assertThat(DifferenceHash.of(png(grey(8, 8, (x, y) -> x * 30)))).isEmpty());
        claim(
                "and bytes that are no image at all have none either, rather than failing: a picture whose"
                        + " pixels cannot be read is still judged by the rules that need no hash",
                () -> assertThat(DifferenceHash.of("not an image".getBytes(StandardCharsets.UTF_8))).isEmpty());
    }

    @Test
    @Story("A picture's difference hash is the same wherever it is computed")
    @DisplayName("The hash carries the picture's size, and two hashes are as far apart as the bits they differ in")
    void carriesTheSizeAndCountsTheBitsApart() {
        DifferenceHash rising = DifferenceHash.of(png(grey(WIDTH, HEIGHT, (x, y) -> x * 255 / (WIDTH - 1)))).orElseThrow();
        DifferenceHash stripes =
                DifferenceHash.of(png(rgb(100, 50, (x, y) -> x == 12 ? 0xffffff : 0x000000))).orElseThrow();

        claim(
                "the hash states the width and height of the picture it was computed from, which the"
                        + " near-copy rule compares alongside it",
                () -> {
                    assertThat(rising.width()).isEqualTo(WIDTH);
                    assertThat(rising.height()).isEqualTo(HEIGHT);
                });
        claim(
                "every bit set against the first bit of each of the eight rows set is 56 bits apart, and a"
                        + " hash is no bits apart from itself",
                () -> {
                    assertThat(rising.bitsApartFrom(stripes)).isEqualTo(56);
                    assertThat(stripes.bitsApartFrom(rising)).isEqualTo(56);
                    assertThat(rising.bitsApartFrom(rising)).isZero();
                });
    }

    private static long bitsOf(BufferedImage image) {
        return DifferenceHash.of(png(image)).orElseThrow().bits();
    }

    private static BufferedImage grey(int width, int height, IntBinaryOperator level) {
        return rgb(width, height, (x, y) -> {
            int v = level.applyAsInt(x, y);
            return (v << 16) | (v << 8) | v;
        });
    }

    private static BufferedImage rgb(int width, int height, IntBinaryOperator colour) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        paint(image, colour);
        return image;
    }

    private static BufferedImage argb(int width, int height, IntBinaryOperator colour) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        paint(image, colour);
        return image;
    }

    private static void paint(BufferedImage image, IntBinaryOperator colour) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, colour.applyAsInt(x, y));
            }
        }
    }

    /**
     * A {@value #WIDTH} by {@value #HEIGHT} palette PNG whose left half is the first of two ARGB palette
     * entries and whose right half is the second. An entry with alpha below 255 makes the encoder write a
     * transparency chunk.
     */
    private static byte[] paletteHalves(int[] argbPalette) {
        byte[] r = new byte[argbPalette.length];
        byte[] g = new byte[argbPalette.length];
        byte[] b = new byte[argbPalette.length];
        byte[] a = new byte[argbPalette.length];
        for (int i = 0; i < argbPalette.length; i++) {
            a[i] = (byte) (argbPalette[i] >>> 24);
            r[i] = (byte) (argbPalette[i] >>> 16);
            g[i] = (byte) (argbPalette[i] >>> 8);
            b[i] = (byte) argbPalette[i];
        }
        BufferedImage image = new BufferedImage(
                WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, argbPalette.length, r, g, b, a));
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.getRaster().setSample(x, y, 0, x < WIDTH / 2 ? 0 : 1);
            }
        }
        return png(image);
    }

    /** The colour type byte of a PNG's header chunk: 8 signature bytes, 8 chunk-header bytes, then 9 more. */
    private static int colourTypeOf(byte[] png) {
        return png[25];
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
