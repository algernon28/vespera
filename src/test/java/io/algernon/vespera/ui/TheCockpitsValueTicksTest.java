package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cockpit's value ticks (ADR-371 §3): shortened to {@code K}, {@code M}, {@code B} and {@code T} on an axis that is
 * not a size, and plain grouped numbers in its unit on an axis whose title ends with a size's unit in parentheses.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("789")
@Link(name = "ADR-371", url = Adr.A_SIZE_OF_MEMORY_IS_WRITTEN_IN_POWERS_OF_1024, type = "adr")
class TheCockpitsValueTicksTest {

    @Test
    @Story("The window's charts read at a glance")
    @DisplayName("A count's tick is shortened to K, M, B and T, and a size's tick is a plain number in its unit")
    void aCountTickIsShortenedAndASizeTickIsNot() {
        claim("on an axis titled Done (count), 2.5, 999, 1,500, 2,000,000, 999,950 and 3,000,000,000,000 read 2.5, 999,"
                        + " 1.5K, 2M, 1M and 3T: below 1,000 as it is, and 999,950 moved up to 1M rather than 1000K",
                () -> assertThat(List.of(2.5, 999.0, 1_500.0, 2_000_000.0, 999_950.0, 3e12).stream()
                        .map(tick -> ProgressCharts.tickText("Done (count)", tick)).toList())
                        .as("each tick of a count axis").containsExactly("2.5", "999", "1.5K", "2M", "1M", "3T"));
        claim("on axes titled Database size (B), (MB) and (KB), 51,700,000,000, 51,700 and 1.5 read 51,700,000,000,"
                        + " 51,700 and 1.5: a size's tick is never shortened, so B never reads as billions on a bytes axis",
                () -> assertThat(List.of(ProgressCharts.tickText("Database size (B)", 51_700_000_000.0),
                        ProgressCharts.tickText("Database size (MB)", 51_700),
                        ProgressCharts.tickText("Database size (KB)", 1.5)))
                        .as("each tick of a size axis").containsExactly("51,700,000,000", "51,700", "1.5"));
        claim("an analysis report's value in bytes, as the window's own tab shows it, is counted in 1,024 as the page"
                        + " counts it: 512, 2,050, 1,030,000, 1,048,575 and -2,050 bytes read 512 B, 2.00 KB, 1,005.86 KB,"
                        + " 1.00 MB and -2.00 KB",
                () -> assertThat(List.of(512L, 2_050L, 1_030_000L, 1_048_575L, -2_050L).stream()
                        .map(bytes -> AnalysisReportView.number(BigDecimal.valueOf(bytes), "bytes")).toList())
                        .as("each value in bytes on the analysis report's tab")
                        .containsExactly("512 B", "2.00 KB", "1,005.86 KB", "1.00 MB", "-2.00 KB"));
        claim("on the elapsed-time axis, 0, 45, 90, 600, 3,599, 5,400, 36,000 and 360,000 seconds read 0 s, 45 s, 1.5 min,"
                        + " 10 min, 1 h, 1.5 h, 10 h and 100 h: seconds, minutes and hours, never K, M, B or T",
                () -> assertThat(List.of(0.0, 45.0, 90.0, 600.0, 3_599.0, 5_400.0, 36_000.0, 360_000.0).stream()
                        .map(ProgressCharts::timeTick).toList())
                        .as("each tick of the elapsed-time axis")
                        .containsExactly("0 s", "45 s", "1.5 min", "10 min", "1 h", "1.5 h", "10 h", "100 h"));
        claim("and a value axis titled with seconds is read as time too: 5,400 on Duration (s) reads 1.5 h, never 5.4K",
                () -> assertThat(ProgressCharts.tickText("Duration (s)", 5_400)).as("a tick of a value axis in seconds")
                        .isEqualTo("1.5 h"));
    }
}
