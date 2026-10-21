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
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reports the control panel links beside each profile key, {@code report-<key>} (ADR-302 §8, with ADR-306 §2's
 * row for {@code clusterPageByteCeiling}), are the reports the README's table of values names for that key, in the
 * README's order, and a key whose row names none has none.
 *
 * <p>The README's table is read as the operator reads it: the table headed {@code | The value | Read | Which reports |},
 * a row per key, its first cell the key and its third the reports, separated by commas, or {@code —} for none.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Link(name = "ADR-306", url = Adr.THE_CONTROL_PANELS_OWN_HOLDER_LINE_IS_HELD_BY_NOBODY, type = "adr")
class TheReportLinksAreTheReadmesTableTest {

    /** The operator's guide, at the root of the repository the tests run in. */
    private static final Path README = Path.of("README.md");

    /** The header row of the README's table of values. */
    private static final String TABLE_HEADER = "| The value | Read | Which reports |";

    /** The third cell of a row whose key no report informs. */
    private static final String NO_REPORT = "—";

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Each value is linked to the reports the operator's guide names for it, in the guide's order, and a value it names none for to none")
    void eachKeysReportLinksAreTheReportsTheReadmesTableNamesForIt() throws IOException {
        Map<String, List<String>> readme = reportsOfTheReadmesTable();

        claim("the guide's table has a row for every profile value",
                () -> assertThat(readme.keySet()).containsAll(
                        Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList()));
        claim("the control panel links each value to exactly the reports the guide's table names for it, in the"
                        + " guide's order, and a value the table names none for to none",
                () -> assertThat(new TreeMap<>(NextPages.REPORTS_OF_A_KEY)).isEqualTo(withReports(readme)));
    }

    /** Each key of the README's table and the report file names its third cell names, in their order. */
    private static Map<String, List<String>> reportsOfTheReadmesTable() throws IOException {
        List<String> lines = Files.readAllLines(README, StandardCharsets.UTF_8);
        int header = lines.indexOf(TABLE_HEADER);
        claim("the guide holds its table of values, headed by the value, what to read and which reports",
                () -> assertThat(header).isNotNegative());
        Map<String, List<String>> rows = new TreeMap<>();
        for (String line : lines.subList(header + 2, lines.size())) {
            if (!line.startsWith("|")) {
                break;
            }
            String[] cells = line.split("\\|");
            String key = unquoted(cells[1]);
            String reports = cells[3].strip();
            rows.put(key, reports.equals(NO_REPORT) ? List.of()
                    : Arrays.stream(reports.split(",")).map(TheReportLinksAreTheReadmesTableTest::unquoted).toList());
        }
        return rows;
    }

    private static Map<String, List<String>> withReports(Map<String, List<String>> rows) {
        Map<String, List<String>> named = new TreeMap<>();
        rows.forEach((key, reports) -> {
            if (!reports.isEmpty()) {
                named.put(key, reports);
            }
        });
        return named;
    }

    private static String unquoted(String cell) {
        String text = cell.strip();
        return text.startsWith("`") && text.endsWith("`") ? text.substring(1, text.length() - 1) : text;
    }
}
