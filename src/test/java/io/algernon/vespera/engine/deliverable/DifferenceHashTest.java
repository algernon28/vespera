package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
@Epic("The deliverable")
@Feature("The pictures a document carries")
@Issue("286")
@Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
class DifferenceHashTest {

    /** Every bit set: each cell brighter than the one to its left, in every row. */
    private static final long ALL_RISING = 0xffffffffffffffffL;

    /** Only the first bit of every row set: the second cell is brighter than the first, and nothing else rises. */
    private static final long RISING_AT_THE_FIRST_COMPARISON_ONLY = 0x8080808080808080L;

    /** The eight rows of the grid, each holding eight of the hash's bits. */
    private static final int ROWS = 8;

    /**
     * How far apart {@link #ALL_RISING} and {@link #RISING_AT_THE_FIRST_COMPARISON_ONLY} are: they share
     * the first bit of each of the {@value #ROWS} rows and differ in every other bit.
     */
    private static final int ALL_BUT_THE_FIRST_BIT_OF_EVERY_ROW = Long.SIZE - ROWS;

    /** The width and height most golden images share: wide enough for ten pixels per column. */
    private static final int WIDTH = 90;

    private static final int HEIGHT = 80;

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
                "every bit set against only the first bit of each row set is " + ALL_BUT_THE_FIRST_BIT_OF_EVERY_ROW
                        + " bits apart, the 64 bits less the " + ROWS + " the two share, measured either way"
                        + " round, and a hash is no bits apart from itself",
                () -> {
                    assertThat(rising.bitsApartFrom(stripes)).isEqualTo(ALL_BUT_THE_FIRST_BIT_OF_EVERY_ROW);
                    assertThat(stripes.bitsApartFrom(rising)).isEqualTo(ALL_BUT_THE_FIRST_BIT_OF_EVERY_ROW);
                    assertThat(rising.bitsApartFrom(rising)).isZero();
                });
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

    private static void paint(BufferedImage image, IntBinaryOperator colour) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, colour.applyAsInt(x, y));
            }
        }
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
