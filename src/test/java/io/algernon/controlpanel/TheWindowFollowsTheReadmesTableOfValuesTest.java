package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each profile value's hint and its reports, wherever the window shows the value, are the README's table of values'
 * row for it (ADR-331 §8 and ADR-306 §3, which ADR-339 and ADR-341 §6 keep): the hint is the row's second cell, its code
 * quotes taken out, its first letter small and no stop at its end; the reports are the third cell's, in its order,
 * and a key whose row names none has none.
 *
 * <p>The README's table is read as the operator reads it: the table headed {@code | The value | Read | Which reports |},
 * a row per key.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class TheWindowFollowsTheReadmesTableOfValuesTest {

    /** The operator's guide, at the root of the repository the tests run in. */
    private static final Path README = Path.of("README.md");

    /** The header row of the README's table of values. */
    private static final String TABLE_HEADER = "| The value | Read | Which reports |";

    /** The third cell of a row whose key no report informs. */
    private static final String NO_REPORT = "—";

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Each value's hint is what the operator's guide says it is read from")
    void eachKeysHintIsItsRowsWords() throws IOException {
        Map<String, String> hints = new TreeMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            hints.put(key.key(), plain(key.hint()));
        }
        Map<String, String> readme = new TreeMap<>();
        rows().forEach((key, cells) -> readme.put(key, plain(cells[2].replace("`", ""))));

        claim("each profile value's hint is its own row's second cell, so a table shifted by one fails",
                () -> assertThat(hints).isEqualTo(readme));
    }

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Each value is linked to the reports the operator's guide names for it, in the guide's order, and a value it names none for to none")
    void eachKeysReportsAreTheReportsItsRowNames() throws IOException {
        Map<String, List<String>> readme = new TreeMap<>();
        rows().forEach((key, cells) -> {
            String reports = cells[3].strip();
            if (!reports.equals(NO_REPORT)) {
                readme.put(key, Arrays.stream(reports.split(","))
                        .map(TheWindowFollowsTheReadmesTableOfValuesTest::unquoted).toList());
            }
        });

        claim("the control panel links each value to exactly the reports the guide's table names for it, in the"
                        + " guide's order, and a value the table names none for to none",
                () -> assertThat(new TreeMap<>(Reports.OF_A_KEY)).isEqualTo(readme));
    }

    /** Each row of the README's table of values, by its key, split into its cells. */
    private static Map<String, String[]> rows() throws IOException {
        List<String> lines = Files.readAllLines(README, StandardCharsets.UTF_8);
        int header = lines.indexOf(TABLE_HEADER);
        claim("the guide holds its table of values, headed by the value, what to read and which reports",
                () -> assertThat(header).isNotNegative());
        Map<String, String[]> rows = new TreeMap<>();
        for (String line : lines.subList(header + 2, lines.size())) {
            if (!line.startsWith("|")) {
                break;
            }
            String[] cells = line.split("\\|");
            rows.put(unquoted(cells[1]), cells);
        }
        return rows;
    }

    /** A hint as the guide's cell gives it: no stop at its end, and its first letter small. */
    private static String plain(String hint) {
        String stripped = hint.strip();
        String withoutAStop = stripped.endsWith(".") ? stripped.substring(0, stripped.length() - 1) : stripped;
        return withoutAStop.isEmpty() ? withoutAStop
                : withoutAStop.substring(0, 1).toLowerCase(Locale.ROOT) + withoutAStop.substring(1);
    }

    private static String unquoted(String cell) {
        String text = cell.strip();
        return text.startsWith("`") && text.endsWith("`") ? text.substring(1, text.length() - 1) : text;
    }
}
