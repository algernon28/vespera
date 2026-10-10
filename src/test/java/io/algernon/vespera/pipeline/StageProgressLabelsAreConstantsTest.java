package io.algernon.vespera.pipeline;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What lets the invocation account carry progress lines (ADR-198 section 4, #424): every line a progress
 * counter writes is a label its stage spelled and counts, and that is true only while no label is built
 * from anything a document carries.
 *
 * <p>Read from source, as {@code AdrLinkTest} reads it: a label's text does not survive compilation as
 * anything a test can ask a counter for, and a counter built from a title would pass every other test
 * the day it was written. The rule it holds is the one the account's own filter holds a second time at
 * runtime, so this fails the build where the filter would only withhold a line.
 *
 * <p>The first argument of every {@code StageProgress.over} and {@code StageProgress.running} call in
 * {@code src/main} is read as concatenated parts. The first part is a string literal starting {@code
 * "Stage "}, or {@code stage}, the constant {@code RedundancyResolutionTasklet} builds its labels from.
 * Every other part is a string literal, or a name allowed in that one file: {@code stage} in {@code
 * RedundancyResolutionTasklet}, and {@code place} and {@code of}, the two numbers a stage counts seed
 * partitions with, in {@code ClusteringTasklet} and, since ADR-223 gave stage 6a a counter for each
 * partition as stage 5f has, in {@code ArrangementTasklet}.
 *
 * <p><b>A part is a string literal only where it is one from its first quote to its last.</b> Until #472 a
 * part passed if it merely began with a quote, so {@code "Stage … %s".formatted(anything)} passed, and so
 * would a literal followed by any call on it: the one shape that can carry a document's title into a label
 * while still opening with literal text. A label that needs a number says so by concatenating a name this
 * class allows, where the scan can read which name it is.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("424")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class StageProgressLabelsAreConstantsTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");

    private static final String CALL_OPENING_PATTERN = "StageProgress\\s*\\.\\s*(over|running)\\s*\\(";

    /** The names a label may concatenate with its literal text, and the one file each is allowed in. */
    private static final Map<String, Set<String>> NAMES_ALLOWED_BY_FILE = Map.of(
            "RedundancyResolutionTasklet.java", Set.of("stage"),
            "ClusteringTasklet.java", Set.of("place", "of"),
            "ArrangementTasklet.java", Set.of("place", "of"));

    /** A whole string literal and nothing after it: an opening quote, no unescaped quote inside, a closing quote. */
    private static final java.util.regex.Pattern A_WHOLE_STRING_LITERAL =
            java.util.regex.Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /** Fewer than this many calls means the scan found nothing, and nothing found would pass every claim. */
    private static final int AT_LEAST_THIS_MANY_COUNTERS = 40;

    @Test
    @Story("A progress label is spelled by code and never built from a document")
    @DisplayName("Every progress counter's label is literal text, a stage prefix and at most two numbers")
    void everyLabelIsConstant() throws IOException {
        List<Label> labels = labelExpressions();

        claim(
                "the scan found at least " + AT_LEAST_THIS_MANY_COUNTERS + " counters, so it is reading the"
                        + " source tree and not an empty one",
                () -> assertThat(labels.size()).isGreaterThanOrEqualTo(AT_LEAST_THIS_MANY_COUNTERS));
        for (Label label : labels) {
            claim(
                    "the label " + label.expression() + " in " + label.file() + " is made of literal text and the names a label may carry, so"
                            + " no title, path or word of a document can be in it",
                    () -> assertThat(isConstant(label)).isTrue());
        }
    }

    /** One counter's label as written in source, and the file it is written in. */
    private record Label(String file, String expression) {}

    private static boolean isConstant(Label label) {
        Set<String> allowedHere = NAMES_ALLOWED_BY_FILE.getOrDefault(label.file(), Set.of());
        List<String> parts = partsOf(label.expression());
        if (parts.isEmpty()) {
            return false;
        }
        String first = parts.getFirst();
        boolean opensRight = first.startsWith("\"Stage ") || (first.equals("stage") && allowedHere.contains(first));
        return opensRight
                && parts.stream()
                        .allMatch(part -> A_WHOLE_STRING_LITERAL.matcher(part).matches() || allowedHere.contains(part));
    }

    @Test
    @Story("A progress label is spelled by code and never built from a document")
    @DisplayName("A label that opens with literal text and then formats something into it is not taken for literal text")
    void aLiteralWithACallOnItIsNotALiteral() {
        claim(
                "text with a placeholder, filled by a call on it, is refused: what fills the placeholder is"
                        + " whatever the call is handed, and the scan cannot read that it is a number",
                () -> assertThat(isConstant(new Label(
                                "ArrangementTasklet.java", "\"Stage 6a (arrangement, clusters, partition %d of %d)\".formatted(place")))
                        .isFalse());
        claim(
                "the same label built by joining literal text to the two numbers a stage counts its exemplars"
                        + " with is accepted in the file those names are allowed in",
                () -> assertThat(isConstant(new Label(
                                "ArrangementTasklet.java",
                                "\"Stage 6a (arrangement, clusters, partition \" + place + \" of \" + of + \")\"")))
                        .isTrue());
        claim(
                "and refused in a file they are not allowed in, where the same two names could be anything",
                () -> assertThat(isConstant(new Label(
                                "GenerationTasklet.java",
                                "\"Stage 6b (generation, clusters, partition \" + place + \" of \" + of + \")\"")))
                        .isFalse());
        claim(
                "literal text alone, with a bracket and a comma inside it, is still literal text",
                () -> assertThat(isConstant(new Label("GenerationTasklet.java", "\"Stage 6b (generation, clusters)\"")))
                        .isTrue());
    }

    /** Splits on a {@code +} outside a string literal, keeping each part trimmed. */
    private static List<String> partsOf(String expression) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean inLiteral = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '"' && (i == 0 || expression.charAt(i - 1) != '\\')) {
                inLiteral = !inLiteral;
            }
            if (c == '+' && !inLiteral) {
                parts.add(part.toString().strip());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString().strip());
        return parts;
    }

    /** The first argument of each call, up to the first comma or closing bracket outside a literal. */
    private static List<Label> labelExpressions() throws IOException {
        java.util.regex.Pattern opening = java.util.regex.Pattern.compile(CALL_OPENING_PATTERN);
        List<Label> expressions = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("StageProgress.java")) {
                    continue;
                }
                String source = Files.readString(file);
                java.util.regex.Matcher call = opening.matcher(source);
                while (call.find()) {
                    if (isInsideAComment(source, call.start())) {
                        continue;
                    }
                    expressions.add(new Label(file.getFileName().toString(), firstArgument(source, call.end())));
                }
            }
        }
        return expressions;
    }

    private static boolean isInsideAComment(String source, int at) {
        int lineStart = source.lastIndexOf('\n', at) + 1;
        String before = source.substring(lineStart, at).strip();
        return before.startsWith("*") || before.startsWith("//");
    }

    private static String firstArgument(String source, int from) {
        boolean inLiteral = false;
        int depth = 0;
        int i = from;
        for (; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' && source.charAt(i - 1) != '\\') {
                inLiteral = !inLiteral;
            } else if (!inLiteral && (c == '(')) {
                depth++;
            } else if (!inLiteral && (c == ')' && depth > 0)) {
                depth--;
            } else if (!inLiteral && depth == 0 && (c == ',' || c == ')')) {
                break;
            }
        }
        return source.substring(from, i).strip();
    }
}
