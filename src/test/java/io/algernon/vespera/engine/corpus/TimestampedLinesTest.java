package io.algernon.vespera.engine.corpus;

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
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How many of a text file's lines begin with a timestamp, read the way ADR-171 §2 reads them: the whole
 * file up to 128 KB and otherwise its first and last 64 KB, decoded as its byte-order mark says.
 */
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("370")
@Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
class TimestampedLinesTest {

    /** The share an operator set on the archive ADR-171 surveyed. */
    private static final double FLOOR = 0.9;

    /** The fewest non-blank lines a share is taken over. */
    private static final int TEN = 10;

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Ten timestamped lines are enough to make a log, and nine are not")
    void tenLinesAreEnoughAndNineAreNot(@TempDir Path directory) throws IOException {
        Path ten = directory.resolve("ten");
        Path nine = directory.resolve("nine");
        Files.writeString(ten, timestamped(TEN));
        Files.writeString(nine, timestamped(TEN - 1));

        TimestampedLines.Count tenCounted = TimestampedLines.count(ten);
        TimestampedLines.Count nineCounted = TimestampedLines.count(nine);

        claim(
                "a file of exactly ten non-blank lines, all timestamped, is a log at a floor of 90%",
                () -> {
                    assertThat(tenCounted).isEqualTo(new TimestampedLines.Count(TEN, TEN));
                    assertThat(tenCounted.isLog(FLOOR)).isTrue();
                });
        claim(
                "and one of nine such lines is not, however high its share: nine lines is too few to call a"
                        + " share a share",
                () -> assertThat(nineCounted.isLog(FLOOR)).isFalse());
    }

    /** {@code lines} lines, each beginning with a timestamp of its own. */
    private static String timestamped(int lines) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            text.append(String.format(Locale.ROOT, "2023-02-08 %02d:%02d:%02d entry\n",
                    line / 3600 % 24, line / 60 % 60, line % 60));
        }
        return text.toString();
    }
}
