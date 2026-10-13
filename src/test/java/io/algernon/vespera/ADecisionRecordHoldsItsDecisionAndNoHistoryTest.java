package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A decision record holds a decision, the context it answers and the reasons for it, and nothing about how it
 * came to be written or what the operator's machine holds (ADR-249).
 *
 * <p>Two kinds of text are refused. Whether a {@code vespera run} has been made is a fact of one working
 * directory on one day, and no record or line of the index may state it. And from ADR-237 on, a record carries
 * no history of its making: its Status gives the date it was accepted and nothing else, and no line names a
 * review, a session, a pull request, a commit it was written against, an answer it was given, or a test's first
 * failure, and neither does its line of the index. Records before ADR-237 are read for the first kind only.
 *
 * <p>It reads text, so it sees the words it looks for and no paraphrase of them.
 */
@Epic("Architecture")
@Feature("Decision records")
@Link(name = "ADR-249", url = Adr.A_RECORD_HOLDS_ITS_DECISION_AND_NO_HISTORY, type = "adr")
class ADecisionRecordHoldsItsDecisionAndNoHistoryTest {

    private static final Path DECISION_RECORDS = Path.of("docs", "adr");

    /** The first record held to carry no history of its making. */
    private static final int FIRST_WITHOUT_HISTORY = 237;

    private static final Pattern A_RUN_MADE_OR_NOT = Pattern.compile(
            "(?i)(\\bruns?\\b[^.|]{0,60}\\b(has|have|had|was|were)\\s+(not\\s+)?(yet\\s+)?been\\s+made"
                    + "|no\\s+`?vespera run`?\\s+(has|had|was)"
                    + "|no\\s+run\\s+(has|had|was)\\s+(yet\\s+)?(been\\s+)?made)");

    private static final Pattern STATUS = Pattern.compile("^- \\*\\*Status\\*\\*: (.*)$", Pattern.MULTILINE);

    private static final Pattern ACCEPTED_ON_A_DATE = Pattern.compile("accepted on \\d{4}-\\d{2}-\\d{2}\\.");

    private static final Pattern HISTORY = Pattern.compile(
            "(?i)(\\breview(?!\\s+list)(?!List)\\w*\\b|\\bsession\\b|/pull/\\d+|\\bfailed first\\b|\\bfailed on\\b"
                    + "|operator's (answer|word|call)|operator answered|\\./mvnw|\\bregister\\b|`[0-9a-f]{7}`)");

    private static final Pattern INDEX_ROW = Pattern.compile("^\\| \\[ADR-(\\d+)\\]");

    @Test
    @Story("A record holds its decision")
    @DisplayName("No decision record, and no line of their index, says whether a run has been made")
    void noRecordSaysWhetherARunHasBeenMade() throws IOException {
        List<String> said = new ArrayList<>();
        for (Path record : records()) {
            Files.readAllLines(record).stream()
                    .filter(line -> A_RUN_MADE_OR_NOT.matcher(line).find())
                    .forEach(line -> said.add(record.getFileName() + ": " + line));
        }
        claim(
                "no record and no line of the index says whether a run has been made",
                () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("A record holds its decision")
    @DisplayName("A decision record from ADR-237 on, and its line of the index, gives the date it was accepted and tells nothing of its making")
    void aRecordTellsNothingOfItsMaking() throws IOException {
        List<Path> recent = records().stream()
                .filter(record -> numberOf(record) >= FIRST_WITHOUT_HISTORY)
                .toList();
        List<String> statuses = new ArrayList<>();
        List<String> history = new ArrayList<>();
        for (Path record : recent) {
            String text = Files.readString(record);
            STATUS.matcher(text).results()
                    .map(status -> status.group(1))
                    .filter(status -> !ACCEPTED_ON_A_DATE.matcher(status).matches())
                    .forEach(status -> statuses.add(record.getFileName() + ": " + status));
            text.lines()
                    .filter(line -> HISTORY.matcher(line).find())
                    .forEach(line -> history.add(record.getFileName() + ": " + line));
        }
        Files.readAllLines(DECISION_RECORDS.resolve("README.md")).stream()
                .filter(line -> {
                    Matcher row = INDEX_ROW.matcher(line);
                    return row.find() && Integer.parseInt(row.group(1)) >= FIRST_WITHOUT_HISTORY;
                })
                .filter(line -> HISTORY.matcher(line).find())
                .forEach(line -> history.add("README.md: " + line));
        claim(
                "the records are read",
                () -> assertThat(recent).hasSizeGreaterThanOrEqualTo(12));
        claim(
                "each record's status is the date it was accepted and nothing else",
                () -> assertThat(statuses).isEmpty());
        claim(
                "no line names a review, a session, a pull request, a commit, an answer it was given or a test's first"
                        + " failure",
                () -> assertThat(history).isEmpty());
    }

    private static List<Path> records() {
        try (Stream<Path> files = Files.list(DECISION_RECORDS)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".md")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A record's number, or -1 for the index. */
    private static int numberOf(Path record) {
        String name = record.getFileName().toString();
        return name.matches("\\d{4}-.*") ? Integer.parseInt(name.substring(0, 4)) : -1;
    }
}
