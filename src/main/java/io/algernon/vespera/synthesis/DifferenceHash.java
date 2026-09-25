package io.algernon.vespera.synthesis;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * A picture's difference hash (ADR-150 §3): a 64-bit number computed from its decoded pixels, defined
 * exactly so that it is the same number wherever it is computed. Used by {@link Deliverable}'s
 * near-copy and same-place furniture rules, which compare a picture's hash against another's.
 *
 * <p>The steps, in order:
 *
 * <ol>
 *   <li>Decode the image, reading the sample values the file stores with no colour-space conversion:
 *       a grey image's sample is its R, G and B alike. Composite any transparency over white, each
 *       channel as {@code (c · a + 255 · (255 − a)) / 255}. Each pixel's luminance is
 *       {@code (299 R + 587 G + 114 B) / 1000}. All of it is integer arithmetic.
 *   <li>Divide the image into a grid of 9 columns and 8 rows. Pixel {@code (x, y)} belongs to column
 *       {@code x · 9 / width} and row {@code y · 8 / height}, both in integer division. Each cell's
 *       value is the integer mean of its pixels' luminance, the sum divided by the count.
 *   <li>For each row, and for each column {@code c} from 0 to 7, the bit is 1 when cell {@code c + 1}
 *       is greater than cell {@code c}. The 64 bits are read row by row, the first bit being the most
 *       significant.
 * </ol>
 *
 * <p>An image narrower than 9 pixels or shorter than 8 has no difference hash, and neither has one the
 * reader cannot decode: {@link #of} is empty for both, rather than throwing, because a picture whose
 * bytes do not decode is still judged by the furniture rules that need no hash.
 *
 * <p><b>{@link java.awt.image.BufferedImage#getRGB} is not used to read a sample</b>, because the JDK
 * gamma-shifts a grey image's samples on the way to sRGB, which would move a value the format never
 * held opinions about (research §6c). Samples are read through the image's own {@link
 * java.awt.image.Raster} instead, which returns exactly the numbers the file stores.
 *
 * <p>Package-private: only {@code synthesis} computes this hash (ADR-150 §5), which is where the
 * furniture rule that reads it lives.
 *
 * @param bits the 64 comparison bits, the first read the most significant
 * @param width the picture's pixel width, which the near-copy rule compares alongside the hash
 * @param height the picture's pixel height, compared the same way
 */
record DifferenceHash(long bits, int width, int height) {

    /** The grid's column count (ADR-150 §3): 9 columns compared pairwise give the 64 bits' width, 8. */
    private static final int COLUMNS = 9;

    /** The grid's row count. */
    private static final int ROWS = 8;

    /** {@code pixels} decoded and hashed, or empty where it cannot be read as an image at least 9 by 8. */
    static Optional<DifferenceHash> of(byte[] pixels) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(pixels));
        } catch (IOException e) {
            return Optional.empty();
        }
        if (image == null || image.getWidth() < COLUMNS || image.getHeight() < ROWS) {
            return Optional.empty();
        }
        int width = image.getWidth();
        int height = image.getHeight();
        long[] sums = new long[COLUMNS * ROWS];
        int[] counts = new int[COLUMNS * ROWS];
        java.awt.image.Raster raster = image.getRaster();
        java.awt.image.ColorModel model = image.getColorModel();
        int[] samples = new int[model.getNumComponents()];
        int sampleShift = Math.max(0, model.getComponentSize(0) - 8);
        for (int y = 0; y < height; y++) {
            int row = y * ROWS / height;
            for (int x = 0; x < width; x++) {
                int column = x * COLUMNS / width;
                raster.getPixel(x, y, samples);
                int luminance = luminanceOf(model, samples, sampleShift);
                int cell = row * COLUMNS + column;
                sums[cell] += luminance;
                counts[cell]++;
            }
        }
        int[] cells = new int[COLUMNS * ROWS];
        for (int cell = 0; cell < cells.length; cell++) {
            cells[cell] = counts[cell] == 0 ? 0 : (int) (sums[cell] / counts[cell]);
        }
        long bits = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS - 1; column++) {
                bits <<= 1;
                if (cells[row * COLUMNS + column + 1] > cells[row * COLUMNS + column]) {
                    bits |= 1L;
                }
            }
        }
        return Optional.of(new DifferenceHash(bits, width, height));
    }

    /**
     * {@code samples}, as {@code model} declares them, composited over white and weighed into one
     * luminance value (ADR-150 §3): a grey sample is read as R, G and B alike; a colour sample is read
     * as its three channels; either is composited over white first where the model carries alpha, all
     * in integer arithmetic.
     *
     * <p>Where {@code model} is an {@link java.awt.image.IndexColorModel}, {@code samples[0]} is the
     * palette index, not a colour value: the raster holds one component, the index, and the palette
     * gives the actual R, G, B and alpha for it. Low-bit-depth greyscale PNGs decode to an
     * {@code IndexColorModel} too, so the same lookup covers them.
     *
     * <p>Where a sample is wider than 8 bits (a 16-bit PNG channel), only the high byte is kept,
     * {@code sample >> 8}, scaling it to 0–255 (ADR-150 §3). That is how PIL reads a 16-bit RGB
     * image; it is not how PIL reads a 16-bit greyscale one, which it clamps, so the probe's Python is
     * no reference for that case.
     */
    private static int luminanceOf(java.awt.image.ColorModel model, int[] samples, int sampleShift) {
        int r;
        int g;
        int b;
        int a = 255;
        if (model instanceof java.awt.image.IndexColorModel icm) {
            int index = samples[0];
            r = icm.getRed(index);
            g = icm.getGreen(index);
            b = icm.getBlue(index);
            if (icm.hasAlpha()) {
                a = icm.getAlpha(index);
            }
        } else {
            int numColourComponents = model.getNumColorComponents();
            if (numColourComponents == 1) {
                r = samples[0] >> sampleShift;
                g = r;
                b = r;
            } else {
                r = samples[0] >> sampleShift;
                g = samples[1] >> sampleShift;
                b = samples[2] >> sampleShift;
            }
            if (model.hasAlpha()) {
                a = samples[numColourComponents] >> sampleShift;
            }
        }
        if (a != 255) {
            r = compositeOverWhite(r, a);
            g = compositeOverWhite(g, a);
            b = compositeOverWhite(b, a);
        }
        return (299 * r + 587 * g + 114 * b) / 1000;
    }

    /** {@code c} composited over white with alpha {@code a}, {@code (c · a + 255 · (255 − a)) / 255}. */
    private static int compositeOverWhite(int c, int a) {
        return (c * a + 255 * (255 - a)) / 255;
    }

    /** How many of this hash's 64 bits differ from {@code other}'s, used by the near-copy and same-place rules. */
    int bitsApartFrom(DifferenceHash other) {
        return Long.bitCount(bits ^ other.bits);
    }
}
