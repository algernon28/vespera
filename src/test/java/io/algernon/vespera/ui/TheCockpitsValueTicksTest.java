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
        claim("on the elapsed-time axis the unit is the largest of s, min and h in which the ticks' spacing is at least 1:"
                        + " ticks every 15 s read 0 s, 15 s, 30 s, 45 s; every 300 s, 0 min, 5 min, 10 min, 15 min; every"
                        + " 3,600 s, 2 h, 3 h; every 36,000 s, 100 h; and every 50 s, 7,250 s grouped by thousands",
                () -> assertThat(List.of(ticks(15, 0, 15, 30, 45), ticks(300, 0, 300, 600, 900),
                        ticks(3_600, 7_200, 10_800), ticks(36_000, 360_000), ticks(50, 7_250)))
                        .as("each axis's ticks, by spacing")
                        .containsExactly(List.of("0 s", "15 s", "30 s", "45 s"),
                                List.of("0 min", "5 min", "10 min", "15 min"), List.of("2 h", "3 h"), List.of("100 h"),
                                List.of("7,250 s")));
        claim("a step that started 2 h into the job and has run 5 min, its axis from 7,200 to 7,500 s ticked every 100 s,"
                        + " reads four distinct ticks, 120 min, 121.7 min, 123.3 min and 125 min, never 2 h twice",
                () -> assertThat(ticks(100, 7_200, 7_300, 7_400, 7_500)).as("the ticks of a late step's axis")
                        .containsExactly("120 min", "121.7 min", "123.3 min", "125 min").doesNotHaveDuplicates());
        claim("a value axis titled with seconds is read as time too: 5,400 on Duration (s), ticked every 1,800 s, reads"
                        + " 90 min, never 5.4K; and a title whose (s) is a plural marker, Cluster(s), is a count, 5,400 reading 5.4K",
                () -> assertThat(List.of(ProgressCharts.tickText("Duration (s)", 5_400, 1_800),
                        ProgressCharts.tickText("Cluster(s)", 5_400, 1_800)))
                        .as("a tick of a value axis in seconds, and of one whose (s) is a plural")
                        .containsExactly("90 min", "5.4K"));
    }

    /** The elapsed-time axis's labels of {@code seconds}, on an axis ticked every {@code spacing} seconds. */
    private static List<String> ticks(double spacing, double... seconds) {
        return java.util.Arrays.stream(seconds).mapToObj(each -> ProgressCharts.timeTick(each, spacing)).toList();
    }
}
