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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * failure, and neither does its line of the index.
 *
 * <p>Every record, from ADR-001 on, is held to the second kind too, by the markers that cannot be the system's own
 * vocabulary (ADR-253): no pull request, no commit, no answer of the operator's, no review register or review of
 * the code, no first failure and no session that worked it, and a Status that says the record is accepted and
 * nothing else. Records from ADR-237 on are held to the wider set above as well, which would also catch words such
 * as the review list's or a session's that older records use of the system itself.
 *
 * <p>It reads text, so it sees the words it looks for and no paraphrase of them. The words are in {@code
 * docs/decision-record-markers.txt}, which {@code docs/check-claims.mjs} reads as well (ADR-258), so that a
 * record that breaks the rule also fails the claims job, in seconds.
 */
@Epic("Architecture")
@Feature("Decision records")
@Link(name = "ADR-249", url = Adr.A_RECORD_HOLDS_ITS_DECISION_AND_NO_HISTORY, type = "adr")
@Link(name = "ADR-253", url = Adr.EVERY_RECORD_HOLDS_ITS_DECISION_AND_NO_HISTORY, type = "adr")
class ADecisionRecordHoldsItsDecisionAndNoHistoryTest {

    private static final Path DECISION_RECORDS = Path.of("docs", "adr");

    /**
     * The words, from the one list the claims job reads too ({@code docs/check-claims.mjs}, ADR-258): a line is
     * a name, a tab and a regular expression, and a line that starts with {@code #} is a comment.
     */
    private static final Path THE_MARKERS = Path.of("docs", "decision-record-markers.txt");

    private static final Map<String, String> MARKERS = markers();

    /** The first record held to carry no history of its making. */
    private static final int FIRST_WITHOUT_HISTORY = Integer.parseInt(MARKERS.get("firstWithoutHistory"));

    private static final Pattern A_RUN_MADE_OR_NOT = Pattern.compile(MARKERS.get("runMadeOrNot"));

    private static final Pattern STATUS = Pattern.compile(MARKERS.get("status"), Pattern.MULTILINE);

    private static final Pattern ACCEPTED_ON_A_DATE = Pattern.compile(MARKERS.get("acceptedOnADate"));

    private static final Pattern HISTORY = Pattern.compile(MARKERS.get("historyFromTheFirstWithout"));

    private static final Pattern ACCEPTED = Pattern.compile(MARKERS.get("accepted"));

    /** What marks the history of a record's making in any record, and never the system's own vocabulary. */
    private static final Pattern HISTORY_IN_ANY_RECORD = Pattern.compile(MARKERS.get("historyInAnyRecord"));

    private static final Pattern INDEX_ROW = Pattern.compile(MARKERS.get("indexRow"));

    private static Map<String, String> markers() {
        Map<String, String> read = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(THE_MARKERS)) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                read.put(line.substring(0, tab), line.substring(tab + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return read;
    }

    @Test
    @Story("A record holds its decision")
    @DisplayName("The list of words both checks read holds every marker the rules need, and each is a pattern")
    @Link(name = "ADR-258", url = Adr.THE_RECORDS_RULES_ARE_CHECKED_IN_THE_CLAIMS_JOB_TOO, type = "adr")
    void theListHoldsEveryMarkerTheRulesNeed() {
        List<String> needed = List.of(
                "runMadeOrNot", "status", "accepted", "acceptedOnADate", "historyFromTheFirstWithout",
                "historyInAnyRecord", "indexRow", "firstWithoutHistory");

        claim(
                "the list names each of the eight markers once, and the claims job reads the same eight",
                () -> assertThat(MARKERS.keySet()).containsExactlyInAnyOrderElementsOf(needed));
        claim(
                "each marker but the number of the first record is a regular expression that compiles",
                () -> needed.stream()
                        .filter(name -> !name.equals("firstWithoutHistory"))
                        .forEach(name -> assertThat(Pattern.compile(MARKERS.get(name))).isNotNull()));
    }

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

    @Test
    @Story("A record holds its decision")
    @DisplayName("No decision record, and no line of their index, carries a pull request, a commit, an answer or a review of its making, and every Status says only that it is accepted")
    void noRecordTellsItsMaking() throws IOException {
        List<String> statuses = new ArrayList<>();
        List<String> history = new ArrayList<>();
        for (Path record : records()) {
            String text = Files.readString(record);
            STATUS.matcher(text).results()
                    .map(status -> status.group(1))
                    .filter(status -> !ACCEPTED.matcher(status).matches())
                    .forEach(status -> statuses.add(record.getFileName() + ": " + status));
            text.lines()
                    .filter(line -> HISTORY_IN_ANY_RECORD.matcher(line).find())
                    .forEach(line -> history.add(record.getFileName() + ": " + line));
        }
        claim(
                "every record's status says it is accepted, with the date it was, and nothing else",
                () -> assertThat(statuses).isEmpty());
        claim(
                "no record and no line of the index carries a pull request, a commit, an answer of the operator's, a"
                        + " review of the code, a first failure or the session that worked it",
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
