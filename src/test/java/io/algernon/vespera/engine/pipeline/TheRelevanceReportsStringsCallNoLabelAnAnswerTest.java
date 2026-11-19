package io.algernon.vespera.engine.pipeline;

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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every string the relevance floor and the relevance report write their lines from calls no relevance label an answer,
 * and no document drawn into the label file one put to a person (ADR-424 §1, §3), read off their sources.
 *
 * <p>{@code TheRelevanceReportsLinesCallNoLabelAnAnswerInvocationTest} holds the lines an invocation writes. One line
 * it cannot reach: the report's step gated with every gate open and no survivor scored, which no whole-job fixture
 * arrives at. So the strings are read here, where that line's are too.
 */
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class TheRelevanceReportsStringsCallNoLabelAnAnswerTest {

    private static final Path THE_PIPELINES_SOURCES = Path.of("src/main/java/io/algernon/vespera/engine/pipeline");

    /** The two classes whose strings stage 5's relevance floor and relevance report write their lines from. */
    private static final List<String> THE_FLOOR_AND_THE_REPORT = List.of("RelevanceFloor.java", "RelevanceReportTasklet.java");

    /** A Java string literal, its escapes included. */
    private static final Pattern A_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Answer and question in any of their forms, as whole words, and the document put to a person. */
    private static final Pattern AN_ANSWER_A_QUESTION_OR_A_PERSON_ASKED =
            Pattern.compile("(?i)\\b(answer|question)\\w*|put to a person");

    /** The close of the line the report's step writes when no survivor carries a score (ADR-424 §3). */
    private static final String NOTHING_DRAWN = "Nothing was drawn to label.";

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("No string of the relevance floor or the relevance report says answer, question or put to a person, and a report with nothing scored says nothing was drawn to label")
    void noStringOfTheFloorOrTheReportSaysAnswerOrPutToAPerson() throws IOException {
        List<String> strings = new ArrayList<>();
        for (String source : THE_FLOOR_AND_THE_REPORT) {
            strings.addAll(stringsOf(THE_PIPELINES_SOURCES.resolve(source)));
        }

        claim("the report's step, gated for want of a score, says nothing was drawn to label",
                () -> assertThat(strings).anyMatch(string -> string.endsWith(NOTHING_DRAWN)));
        claim("and no string of " + THE_FLOOR_AND_THE_REPORT + " says answer or question, in any form, or put to a"
                        + " person",
                () -> assertThat(strings)
                        .noneMatch(string -> AN_ANSWER_A_QUESTION_OR_A_PERSON_ASKED.matcher(string).find()));
    }

    /** The string literals of {@code source}'s code, its comments and Javadoc left out. */
    private static List<String> stringsOf(Path source) throws IOException {
        List<String> strings = new ArrayList<>();
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String code = line.strip();
            if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")) {
                continue;
            }
            Matcher string = A_STRING.matcher(code);
            while (string.find()) {
                strings.add(string.group(1));
            }
        }
        return strings;
    }
}
