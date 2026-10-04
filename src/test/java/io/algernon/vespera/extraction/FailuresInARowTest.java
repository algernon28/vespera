package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The three counts of failures in a row stage 2 keeps, and what follows from each (ADR-071, ADR-139,
 * ADR-175 section 3a, ADR-184), now that they are {@code extraction}'s (ADR-189): timeouts, which flip a
 * timeout from the occurrence's to the converter's; occurrences set aside, five of which send the
 * control conversion; and occurrences that dropped the connection twice, five of which send it too.
 *
 * <p>Every sequence below was run through today's {@code ExtractionItemProcessor} and {@code
 * ExtractionCircuitBreaker} before the move, by a throwaway probe, and each claim expects what the probe
 * measured. Each one is built so that the other reading gives a different answer: a row that is said to
 * end is followed by enough failures to reach the count if it had not, and one that is said to stay is
 * followed by exactly the failure that reaches it.
 *
 * <p>{@link Drain} stands in for the step's one thread (ADR-140 section 3), calling {@link
 * FailuresInARow} at the three moments the step's listeners do: when an occurrence starts, when it
 * completes, and when it is set aside. It throws nothing: a stop is reported as {@link Outcome#STOPPED},
 * because which exception the step throws is {@code pipeline}'s.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
@Link(name = "ADR-184", url = Adr.FIVE_FAILURES_IN_A_ROW_STOP_STAGE_2_ONLY_AFTER_A_FAILED_CONTROL_CONVERSION, type = "adr")
class FailuresInARowTest {

    /** How many timeouts in a row are read as the converter's, written out rather than read off the class. */
    private static final int THREE_TIMEOUTS = 3;

    /** How many set-aside occurrences in a row send the control conversion, written out. */
    private static final int FIVE_SET_ASIDE = 5;

    /** How many occurrences in a row that dropped the connection twice send it, written out. */
    private static final int FIVE_DROPPED_TWICE = 5;

    /** One fewer than a row of five: the length at which one more reaches the count. */
    private static final int FOUR = 4;

    /** The converter's message on every failure here; nothing below reads it. */
    private static final String MESSAGE = "the message the converter reported";

    /** What the client says when a call brings no answer in time. */
    private static final String NO_ANSWER_IN_TIME = "no response from docling-serve within the call timeout";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every count keeps its number")
    @DisplayName("Three timeouts in a row, five set-aside files in a row and five files that each dropped the connection twice are the counts")
    void theCountsAreThreeFiveAndFive() {
        claim(
                "three timeouts in a row are read as the converter's rather than any one file's",
                () -> assertThat(FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT).isEqualTo(THREE_TIMEOUTS));
        claim(
                "five files set aside in a row send the control document, and so do five files in a row that"
                        + " each dropped the connection twice -- the same number, so a converter that converts"
                        + " nothing is asked as soon whichever way it fails",
                () -> {
                    assertThat(FailuresInARow.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT).isEqualTo(FIVE_SET_ASIDE);
                    assertThat(FailuresInARow.CONSECUTIVE_DROPPED_TWICE_COUNT).isEqualTo(FIVE_DROPPED_TWICE);
                });
    }

    @Test
    @Story("Timeouts in a row")
    @DisplayName("Once timeouts are read as the converter's, every further timeout in the row is too")
    void aTimeoutRowGoesOnCountingPastItsCount() {
        Drain drain = new Drain(4);

        List<Outcome> outcomes = List.of(drain.timeout(), drain.timeout(), drain.timeout(), drain.timeout());

        claim(
                "the first two timeouts are each the file's own, and the third and the fourth are both set aside:"
                        + " reaching three does not start the count again, so the fourth is still one of a row of"
                        + " timeouts",
                () -> assertThat(outcomes)
                        .containsExactly(Outcome.JUDGED, Outcome.JUDGED, Outcome.SET_ASIDE, Outcome.SET_ASIDE));
    }

    @Test
    @Story("Timeouts in a row")
    @DisplayName("Any answer from the converter between timeouts ends their row, an answer from earlier work included")
    void anAnswerEndsTheTimeoutRow() {
        Map<String, Function<Drain, Outcome>> answers = Map.of(
                "a file that converted", Drain::conversion,
                "a file converted by an earlier run and answered from that work", Drain::cachedConversion,
                "a file the converter could not convert and blamed on the file", Drain::documentScopeFailure,
                "a failure the converter blamed on itself", Drain::serviceScopeFailure,
                "a file the converter refused with an error status", Drain::rejection);
        answers.forEach((answer, between) -> {
            Drain drain = new Drain(4);
            drain.timeout();
            drain.timeout();
            between.apply(drain);
            Outcome after = drain.timeout();
            claim(
                    "two timeouts, then " + answer + ", then a third timeout: the third is the file's own, because"
                            + " the answer between them ended the row. An answer from earlier work counts as one"
                            + " here, as it did before this move; whether it should is a question still open",
                    () -> assertThat(after).isEqualTo(Outcome.JUDGED));
        });
    }

    @Test
    @Story("Timeouts in a row")
    @DisplayName("A file with no recorded format, a file that dropped the connection twice and a control document that converted leave the timeout row as it is")
    void whatLeavesTheTimeoutRow() {
        Map<String, Function<Drain, Outcome>> between = Map.of(
                "a file that dropped the connection twice", Drain::droppedTwice,
                "a file whose format was never recorded", Drain::noDetectedFormat,
                "five files that dropped the connection twice, after which the control document converted",
                drain -> {
                    for (int i = 0; i < FIVE_DROPPED_TWICE; i++) {
                        drain.droppedTwice();
                    }
                    return Outcome.JUDGED;
                });
        between.forEach((what, apply) -> {
            Drain drain = new Drain(8, Answer.CONVERTS);
            drain.timeout();
            drain.timeout();
            apply.apply(drain);
            Outcome after = drain.timeout();
            claim(
                    "two timeouts, then " + what + ", then a timeout: that timeout is the third in the row and is"
                            + " set aside, because nothing between them was an answer about a file",
                    () -> assertThat(after).isEqualTo(Outcome.SET_ASIDE));
        });
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("The fifth file set aside in a row sends the control document, and when it converts the count starts again")
    void fiveSetAsideSendTheControlConversionAndAConvertedOneStartsTheRowAgain() {
        Drain drain = new Drain(10, Answer.CONVERTS, Answer.CONVERTS);
        List<Outcome> outcomes = new ArrayList<>();
        List<Integer> sentAfterEach = new ArrayList<>();

        for (int i = 0; i < 2 * FIVE_SET_ASIDE; i++) {
            outcomes.add(drain.serviceScopeFailure());
            sentAfterEach.add(drain.controlConversionsSent());
        }

        claim(
                "every one of ten files set aside stays set aside, and none stops the stage, because the control"
                        + " document converted each time it was sent",
                () -> assertThat(outcomes).hasSize(2 * FIVE_SET_ASIDE).containsOnly(Outcome.SET_ASIDE));
        claim(
                "it was sent after the fifth and after the tenth and at no other moment: the count started again"
                        + " from nothing once it converted, so the sixth to the ninth sent nothing",
                () -> assertThat(sentAfterEach).containsExactly(0, 0, 0, 0, 1, 1, 1, 1, 1, 2));
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("Counting a set-aside file sends nothing; the control document goes only when the count is due")
    void countingASetAsideSendsNothingAndTheControlConversionWaitsUntilDue() {
        ScriptedControl control = new ScriptedControl();
        FailuresInARow rows = new FailuresInARow(control);

        List<InARow> counted = new ArrayList<>();
        for (int i = 0; i < FIVE_SET_ASIDE; i++) {
            counted.add(rows.setAside());
        }
        int sentWhileCounting = control.sent();
        InARow after = rows.controlConversionAfterSetAsides();

        claim(
                "the first four are counted and the fifth reaches the count, each carrying how long the row is"
                        + " so far, so the line the stage writes for each one can say so before anything is sent",
                () -> assertThat(counted)
                        .containsExactly(
                                new InARow.Counted(1),
                                new InARow.Counted(2),
                                new InARow.Counted(3),
                                new InARow.Counted(4),
                                new InARow.Reached(FIVE_SET_ASIDE)));
        claim(
                "and counting them sent nothing: the control document is sent only when asked for, after the"
                        + " fifth is counted",
                () -> assertThat(sentWhileCounting).isZero());
        claim(
                "a converter that gives no answer to it did not convert it, so the row of five stands and the"
                        + " stage stops, with nothing to name beyond the count",
                () -> assertThat(after)
                        .isEqualTo(new InARow.ControlNotConverted(FIVE_SET_ASIDE, ControlReading.NOT_ANSWERED, List.of())));
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("The control document cannot be sent for set-aside files before five are counted")
    void theControlConversionIsRefusedBeforeTheCountIsDue() {
        ScriptedControl control = new ScriptedControl();
        FailuresInARow rows = new FailuresInARow(control);
        rows.setAside();

        claim(
                "asking for it after one set-aside file is refused, and nothing is sent: what decides when it is"
                        + " sent is the count, not whoever asks",
                () -> {
                    assertThatThrownBy(rows::controlConversionAfterSetAsides).isInstanceOf(IllegalStateException.class);
                    assertThat(control.sent()).isZero();
                });
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("A control document answered with something other than its own text did not convert, and the stop says how")
    void aControlConversionThatDidNotConvertSaysHow() {
        FailuresInARow notAConversion = new FailuresInARow(new ScriptedControl(Answer.FAILS));
        FailuresInARow withoutItsText = new FailuresInARow(new ScriptedControl(Answer.CONVERTS_SOMETHING_ELSE));
        for (int i = 0; i < FIVE_SET_ASIDE; i++) {
            notAConversion.setAside();
            withoutItsText.setAside();
        }

        claim(
                "a converter that answers the control document with a failure did not convert it",
                () -> assertThat(notAConversion.controlConversionAfterSetAsides())
                        .isEqualTo(new InARow.ControlNotConverted(
                                FIVE_SET_ASIDE, ControlReading.NOT_A_CONVERSION, List.of())));
        claim(
                "and neither did one that converted it into text without the control document's own sentence",
                () -> assertThat(withoutItsText.controlConversionAfterSetAsides())
                        .isEqualTo(new InARow.ControlNotConverted(
                                FIVE_SET_ASIDE, ControlReading.LACKS_THE_SENTENCE, List.of())));
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("Only an answer about a file given now ends a row of set-aside files")
    void anAnswerGivenNowEndsTheSetAsideRow() {
        Map<String, Function<Drain, Outcome>> answers = Map.of(
                "a file that converted", Drain::conversion,
                "a file the converter could not convert and blamed on the file", Drain::documentScopeFailure,
                "a file the converter said it ran out of time on, below the count", Drain::reportedTimeout,
                "a file the converter refused with an error status", Drain::rejection);
        answers.forEach((answer, between) -> {
            Drain drain = new Drain(6);
            for (int i = 0; i < FOUR; i++) {
                drain.serviceScopeFailure();
            }
            between.apply(drain);
            Outcome fifth = drain.serviceScopeFailure();
            claim(
                    "four files set aside, then " + answer + ", then one more set aside: no control document is"
                            + " sent and the stage goes on, because the answer ended the row and the last one is the"
                            + " first of a new one",
                    () -> {
                        assertThat(fifth).isEqualTo(Outcome.SET_ASIDE);
                        assertThat(drain.controlConversionsSent()).isZero();
                    });
        });
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("An answer from earlier work, a timeout with no answer, a file that dropped twice and one with no recorded format leave a row of set-aside files as it is")
    void whatSaysNothingAboutTheConverterNowLeavesTheSetAsideRow() {
        Map<String, Function<Drain, Outcome>> between = Map.of(
                "a file converted by an earlier run and answered from that work", Drain::cachedConversion,
                "a file refused by an earlier run and answered from that work", Drain::cachedDocumentScopeFailure,
                "a call that brought no answer in time", Drain::timeout,
                "a file that dropped the connection twice", Drain::droppedTwice,
                "a file whose format was never recorded", Drain::noDetectedFormat);
        between.forEach((what, apply) -> {
            Drain drain = new Drain(6);
            for (int i = 0; i < FOUR; i++) {
                drain.serviceScopeFailure();
            }
            apply.apply(drain);
            Outcome fifth = drain.serviceScopeFailure();
            claim(
                    "four files set aside, then " + what + ", then one more set aside: that one is the fifth in the"
                            + " row, so the control document is sent, and with a converter that does not convert it"
                            + " the stage stops",
                    () -> {
                        assertThat(fifth).isEqualTo(Outcome.STOPPED);
                        assertThat(drain.controlConversionsSent()).isEqualTo(1);
                    });
        });
    }

    @Test
    @Story("Files set aside in a row")
    @DisplayName("What a file that never completed said about the converter is forgotten when the next one starts")
    void anOccurrenceThatNeverCompletedLeavesNothingForTheNext() {
        Drain drain = new Drain(7);
        for (int i = 0; i < FOUR; i++) {
            drain.serviceScopeFailure();
        }
        drain.noDetectedFormatNeverCompleted();
        drain.conversion();
        Outcome fifth = drain.serviceScopeFailure();

        claim(
                "four files set aside, a file with no recorded format that never completed, then a file that"
                        + " converted, then one more set aside: the conversion ended the row, because what the"
                        + " file before it said -- that it was no answer about the converter -- went with that file",
                () -> {
                    assertThat(fifth).isEqualTo(Outcome.SET_ASIDE);
                    assertThat(drain.controlConversionsSent()).isZero();
                });
    }

    @Test
    @Story("Files that drop the connection twice in a row")
    @DisplayName("The fifth file in a row to drop the connection twice sends the control document, and when it converts each keeps its own verdict")
    void fiveDroppedTwiceSendTheControlConversionAndAConvertedOneStartsTheRowAgain() {
        Drain drain = new Drain(10, Answer.CONVERTS, Answer.CONVERTS);
        List<InARow> rows = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();

        for (int i = 0; i < 2 * FIVE_DROPPED_TWICE; i++) {
            outcomes.add(drain.droppedTwice());
            rows.add(drain.lastDroppedRow());
        }

        claim(
                "every one of ten such files keeps its verdict and none stops the stage",
                () -> assertThat(outcomes).hasSize(2 * FIVE_DROPPED_TWICE).containsOnly(Outcome.JUDGED));
        claim(
                "the control document was sent at the fifth and at the tenth, and the sixth started a row of its"
                        + " own",
                () -> assertThat(rows)
                        .containsExactly(
                                new InARow.Counted(1),
                                new InARow.Counted(2),
                                new InARow.Counted(3),
                                new InARow.Counted(4),
                                new InARow.ControlConverted(FIVE_DROPPED_TWICE),
                                new InARow.Counted(1),
                                new InARow.Counted(2),
                                new InARow.Counted(3),
                                new InARow.Counted(4),
                                new InARow.ControlConverted(FIVE_DROPPED_TWICE)));
        claim(
                "so it was sent twice in all",
                () -> assertThat(drain.controlConversionsSent()).isEqualTo(2));
    }

    @Test
    @Story("Files that drop the connection twice in a row")
    @DisplayName("When the control document does not convert either, the stop names the five files in the order they came")
    void theStopNamesTheFiveInTheOrderTheyCame() {
        ScriptedControl control = new ScriptedControl();
        FailuresInARow rows = new FailuresInARow(control);
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, FIVE_DROPPED_TWICE);
        OccurrenceJudge judge = new OccurrenceJudge(metrics(), rows, occurrences.run(), null);
        // Out of alphabetical order on purpose, so a list that was sorted, or kept in a set, would differ.
        List<String> inTheOrderTheyCame = List.of(
                "corpus/e.docx", "corpus/c.pdf", "corpus/a.xls", "corpus/d.txt", "corpus/b.pptx");

        OccurrenceDecision fifth = null;
        for (int i = 0; i < FIVE_DROPPED_TWICE; i++) {
            fifth = judge.droppedTwice(occurrences.get(i), inTheOrderTheyCame.get(i), MESSAGE);
        }
        OccurrenceDecision stopped = fifth;

        claim(
                "the fifth sends the control document, and a converter that gives no answer to it stops the"
                        + " stage, naming the five paths in the order the files came, so the operator knows"
                        + " exactly which to move",
                () -> assertThat(((OccurrenceDecision.DroppedTwice) stopped).row())
                        .isEqualTo(new InARow.ControlNotConverted(
                                FIVE_DROPPED_TWICE, ControlReading.NOT_ANSWERED, inTheOrderTheyCame)));
        claim(
                "and it was sent once",
                () -> assertThat(control.sent()).isEqualTo(1));
    }

    @Test
    @Story("Files that drop the connection twice in a row")
    @DisplayName("Only an answer about a file given now ends a row of files that dropped the connection twice")
    void anAnswerGivenNowEndsTheDroppedRow() {
        Map<String, Function<Drain, Outcome>> answers = Map.of(
                "a file that converted", Drain::conversion,
                "a file the converter could not convert and blamed on the file", Drain::documentScopeFailure,
                "a file the converter said it ran out of time on, below the count", Drain::reportedTimeout,
                "a file the converter refused with an error status", Drain::rejection);
        answers.forEach((answer, between) -> {
            Drain drain = new Drain(6);
            for (int i = 0; i < FOUR; i++) {
                drain.droppedTwice();
            }
            between.apply(drain);
            Outcome fifth = drain.droppedTwice();
            claim(
                    "four files that dropped the connection twice, then " + answer + ", then one more: no control"
                            + " document is sent and that file keeps its verdict, because the answer ended the row",
                    () -> {
                        assertThat(fifth).isEqualTo(Outcome.JUDGED);
                        assertThat(drain.controlConversionsSent()).isZero();
                    });
        });
    }

    @Test
    @Story("Files that drop the connection twice in a row")
    @DisplayName("An answer from earlier work, a timeout with no answer, a set-aside file and one with no recorded format leave a row of dropped files as it is")
    void whatSaysNothingAboutTheConverterNowLeavesTheDroppedRow() {
        Map<String, Function<Drain, Outcome>> between = Map.of(
                "a file converted by an earlier run and answered from that work", Drain::cachedConversion,
                "a file refused by an earlier run and answered from that work", Drain::cachedDocumentScopeFailure,
                "a call that brought no answer in time", Drain::timeout,
                "a failure the converter blamed on itself", Drain::serviceScopeFailure,
                "a file whose format was never recorded", Drain::noDetectedFormat);
        between.forEach((what, apply) -> {
            Drain drain = new Drain(6);
            for (int i = 0; i < FOUR; i++) {
                drain.droppedTwice();
            }
            apply.apply(drain);
            Outcome fifth = drain.droppedTwice();
            claim(
                    "four files that dropped the connection twice, then " + what + ", then one more: that one is"
                            + " the fifth in the row, so the control document is sent, and with a converter that"
                            + " does not convert it the stage stops",
                    () -> {
                        assertThat(fifth).isEqualTo(Outcome.STOPPED);
                        assertThat(drain.controlConversionsSent()).isEqualTo(1);
                    });
        });
    }

    @Test
    @Story("Files that drop the connection twice in a row")
    @DisplayName("A timeout the converter reports, once timeouts are the converter's, leaves a row of dropped files as it is")
    void aTimeoutReadAsTheConvertersLeavesTheDroppedRow() {
        Drain drain = new Drain(8);
        drain.timeout();
        drain.timeout();
        for (int i = 0; i < FOUR; i++) {
            drain.droppedTwice();
        }
        Outcome third = drain.reportedTimeout();
        Outcome fifth = drain.droppedTwice();

        claim(
                "two timeouts, four files that dropped twice, then a reported timeout: that is the third timeout"
                        + " in a row, so it is set aside rather than taken as an answer about its file",
                () -> assertThat(third).isEqualTo(Outcome.SET_ASIDE));
        claim(
                "so the next file that drops twice is the fifth in its row, the control document is sent, and the"
                        + " stage stops -- where a timeout below the count would have ended the row",
                () -> {
                    assertThat(fifth).isEqualTo(Outcome.STOPPED);
                    assertThat(drain.controlConversionsSent()).isEqualTo(1);
                });
    }

    @Test
    @Story("One control document for both counts")
    @DisplayName("A control document that converted for files that dropped twice starts the set-aside row again too")
    void aConvertedControlConversionForDroppedFilesStartsTheSetAsideRowAgain() {
        Drain drain = new Drain(13, Answer.CONVERTS);
        List<Outcome> afterwards = new ArrayList<>();

        for (int i = 0; i < FOUR; i++) {
            drain.serviceScopeFailure();
        }
        for (int i = 0; i < FIVE_DROPPED_TWICE; i++) {
            drain.droppedTwice();
        }
        for (int i = 0; i < FOUR; i++) {
            afterwards.add(drain.serviceScopeFailure());
        }

        claim(
                "four files set aside, five that dropped the connection twice -- the fifth sends the control"
                        + " document, which converts -- then four more set aside: none of the four reaches the"
                        + " count, because the conversion started both rows again, and the first of them would"
                        + " otherwise have been the fifth",
                () -> {
                    assertThat(afterwards).containsOnly(Outcome.SET_ASIDE);
                    assertThat(drain.controlConversionsSent()).isEqualTo(1);
                });
    }

    @Test
    @Story("One control document for both counts")
    @DisplayName("A control document that converted for set-aside files starts the row of dropped files again too")
    void aConvertedControlConversionForSetAsideFilesStartsTheDroppedRowAgain() {
        Drain drain = new Drain(13, Answer.CONVERTS);
        List<Outcome> afterwards = new ArrayList<>();

        for (int i = 0; i < FOUR; i++) {
            drain.droppedTwice();
        }
        for (int i = 0; i < FIVE_SET_ASIDE; i++) {
            drain.serviceScopeFailure();
        }
        for (int i = 0; i < FOUR; i++) {
            afterwards.add(drain.droppedTwice());
        }

        claim(
                "four files that dropped twice, five set aside -- the fifth sends the control document, which"
                        + " converts -- then four more that dropped twice: each keeps its verdict and nothing more"
                        + " is sent, because the first of them would otherwise have been the fifth",
                () -> {
                    assertThat(afterwards).containsOnly(Outcome.JUDGED);
                    assertThat(drain.controlConversionsSent()).isEqualTo(1);
                });
    }

    private ExtractionMetrics metrics() {
        return new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
    }

    /** What became of one file occurrence on the drain. */
    private enum Outcome {
        /** It completed: a verdict, or a conversion that leaves it a survivor. */
        JUDGED,
        /** It was set aside unjudged, and the stage went on. */
        SET_ASIDE,
        /** The stage stops on it. */
        STOPPED
    }

    /** How the scripted converter answers one control document. */
    private enum Answer {
        CONVERTS,
        CONVERTS_SOMETHING_ELSE,
        FAILS
    }

    /** A control conversion whose answers are scripted; once they run out, it gets no answer at all. */
    private static final class ScriptedControl implements ControlConversion {

        private final Deque<Answer> answers = new ArrayDeque<>();
        private int sent;

        ScriptedControl(Answer... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public Optional<DoclingResponse> send() {
            sent++;
            Answer answer = answers.poll();
            if (answer == null) {
                return Optional.empty();
            }
            return Optional.of(switch (answer) {
                case CONVERTS -> converting("the " + ControlConversion.SENTENCE + " page");
                case CONVERTS_SOMETHING_ELSE -> converting("a page of something else entirely");
                case FAILS -> failing(FailureCategory.INTERNAL);
            });
        }

        int sent() {
            return sent;
        }
    }

    /**
     * The step's one thread, reduced to the three calls its listeners make: each occurrence is started,
     * judged, and then either completed or set aside -- never both, as with the step's own skip.
     */
    private final class Drain {

        private final FailuresInARow rows;
        private final OccurrenceJudge judge;
        private final ScriptedControl control;
        private final OccurrencesUnderARun occurrences;
        private int next;
        private InARow lastDroppedRow;

        Drain(int size, Answer... controlAnswers) {
            this.control = new ScriptedControl(controlAnswers);
            this.rows = new FailuresInARow(control);
            this.occurrences = OccurrencesUnderARun.of(jdbcTemplate, size);
            this.judge = new OccurrenceJudge(metrics(), rows, occurrences.run(), null);
        }

        Outcome timeout() {
            return occurrence(occurrence -> judge.timedOut(occurrence, NO_ANSWER_IN_TIME));
        }

        Outcome reportedTimeout() {
            return occurrence(occurrence -> judge.answered(occurrence, failing(FailureCategory.TIMEOUT), false));
        }

        Outcome conversion() {
            return occurrence(occurrence -> judge.answered(occurrence, converting("real text"), false));
        }

        Outcome cachedConversion() {
            return occurrence(occurrence -> judge.answered(occurrence, converting("real text"), true));
        }

        Outcome documentScopeFailure() {
            return occurrence(occurrence -> judge.answered(occurrence, failing(FailureCategory.BACKEND_FAILURE), false));
        }

        Outcome cachedDocumentScopeFailure() {
            return occurrence(occurrence -> judge.answered(occurrence, failing(FailureCategory.BACKEND_FAILURE), true));
        }

        Outcome serviceScopeFailure() {
            return occurrence(occurrence -> judge.answered(occurrence, failing(FailureCategory.CAPACITY), false));
        }

        Outcome rejection() {
            return occurrence(occurrence -> judge.rejected(occurrence, "docling-serve answered HTTP 500"));
        }

        Outcome droppedTwice() {
            int index = next;
            return occurrence(occurrence ->
                    judge.droppedTwice(occurrence, OccurrencesUnderARun.pathOf(index), "Connection reset"));
        }

        Outcome noDetectedFormat() {
            return occurrence(occurrence -> judge.noDetectedFormat(occurrence, occurrences.run()));
        }

        /**
         * Starts an occurrence with no recorded format and judges it, then neither completes it nor sets it
         * aside: the shape of an occurrence whose processing threw something the step does not skip.
         */
        void noDetectedFormatNeverCompleted() {
            rows.occurrenceStarted();
            judge.noDetectedFormat(occurrences.get(next++), occurrences.run());
        }

        int controlConversionsSent() {
            return control.sent();
        }

        InARow lastDroppedRow() {
            return lastDroppedRow;
        }

        private Outcome occurrence(Function<OccurrenceId, OccurrenceDecision> judging) {
            rows.occurrenceStarted();
            OccurrenceDecision decision = judging.apply(occurrences.get(next++));
            if (decision instanceof OccurrenceDecision.SetAside) {
                if (rows.setAside() instanceof InARow.Reached
                        && rows.controlConversionAfterSetAsides() instanceof InARow.ControlNotConverted) {
                    return Outcome.STOPPED;
                }
                return Outcome.SET_ASIDE;
            }
            if (decision instanceof OccurrenceDecision.DroppedTwice dropped) {
                lastDroppedRow = dropped.row();
                if (dropped.row() instanceof InARow.ControlNotConverted) {
                    return Outcome.STOPPED;
                }
            }
            rows.occurrenceCompleted();
            return Outcome.JUDGED;
        }
    }

    private static DoclingResponse converting(String text) {
        return new DoclingResponse(
                ConversionStatus.SUCCESS,
                List.of(),
                0d,
                null,
                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + text + "\"}]}}}");
    }

    private static DoclingResponse failing(FailureCategory category) {
        return new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError("document_backend", "docling", MESSAGE, category, null)),
                0d,
                null,
                "{}");
    }
}
