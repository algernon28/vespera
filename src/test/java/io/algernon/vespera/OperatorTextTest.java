package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-098's rule that no ordinal reaches the operator, as a test: not "gate 3", not "step 2 of 5".
 *
 * <p>The numbering it bans is real and useful — the javadoc calls the embedding model gate 3, and the
 * seed folder ADR-083's second gate — but it lives nowhere an operator can see it. There is no gate 1
 * and no gate 2 anywhere in this codebase, so a message naming the third one asks its reader to count
 * a sequence they have never been shown, which is exactly what ADR-052 forbids report-visible text
 * from doing. The message names the missing key instead, which every message but one already did.
 *
 * <p><b>Ordinals also fossilise.</b> A scheme is stable only until a gate is added between two
 * others, and ADR-098 renumbered this sequence once in the writing of it.
 *
 * <p>Stage numbers are untouched. Every gate line names one, and "stage 4 is gated" says which pass
 * stopped rather than how many stops there are — a reader needs no sequence to make sense of it.
 *
 * <p>Source rather than bytecode, unlike {@link ExceptionNamingTest}: what is checked is a literal a
 * person wrote, and the javadoc this scan has to exclude does not survive compilation.
 */
@Epic("Architecture")
@Feature("Naming conventions")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Issue("136")
class OperatorTextTest {

    /** The application's own source, which is where a string an operator reads is written. */
    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");

    /** An ordinal against either of the two words ADR-098 names, in text rather than in a symbol. */
    private static final Pattern ORDINAL = Pattern.compile("\\b(gate|step) \\d");

    /**
     * One literal the scan must find, named rather than counted for the reason {@link
     * ExceptionNamingTest} names a throwable: a scan that has quietly stopped reading anything — a
     * moved source root, a scanner that loses its place — would pass the claim below while proving
     * nothing, and this is what fails instead.
     */
    private static final String A_LITERAL_AN_OPERATOR_READS = "is gated: the profile key";

    /**
     * A second literal the scan must find, and the one that caught an earlier scanner out.
     *
     * <p>It sits in a file whose source contains {@code //} <em>inside</em> a string — a URL default.
     * Stripping line comments by regex truncated that literal mid-quote, and every quote later in the
     * file then paired with the wrong partner, so {@code OllamaClient}'s own messages were never
     * scanned and an ordinal added to them would have passed. One named literal per failure mode is
     * what makes the emptiness claim below worth anything.
     */
    private static final String A_LITERAL_BESIDE_A_URL = "spring.ai.ollama.base-url";

    @Test
    @Story("The operator is told the next value, never the stage they are at")
    @DisplayName("No string the application writes names a gate or a step by number")
    void noOperatorTextNamesAnOrdinal() throws IOException {
        List<String> literals = literalsInMainSource();
        List<String> withOrdinals =
                literals.stream().filter(literal -> ORDINAL.matcher(literal).find()).sorted().toList();

        claim(
                "the scan read the strings this application writes, checked by naming one it must find --"
                        + " a scan reading nothing would pass the claim below having proved nothing",
                () -> assertThat(literals).anyMatch(literal -> literal.contains(A_LITERAL_AN_OPERATOR_READS)));
        claim(
                "including the strings in a file that writes // inside one of them, which is where a"
                        + " scanner that strips comments before it knows what a string is loses its place"
                        + " and silently stops checking the rest of the file",
                () -> assertThat(literals).anyMatch(literal -> literal.contains(A_LITERAL_BESIDE_A_URL)));
        claim(
                "and none of them numbers a gate or a step: there is no gate 1 or gate 2 in this codebase,"
                        + " so a message naming the third asks its reader to count a sequence nobody has"
                        + " shown them. The javadoc keeps its ordinals, which is why this reads source and"
                        + " knows the difference",
                () -> assertThat(withOrdinals).isEmpty());
    }

    /** Every string literal in the application's source. */
    private static List<String> literalsInMainSource() throws IOException {
        try (Stream<Path> sources = Files.walk(MAIN_SOURCE)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(source -> literalsOf(read(source)).stream())
                    .toList();
        }
    }

    /**
     * Every string literal in one compilation unit, read character by character.
     *
     * <p>Not by regex, and that is the whole of it: comments and literals nest inside each other both
     * ways round. A {@code //} inside a string is a URL, a {@code "} inside a comment is prose, and a
     * scanner that removes either without knowing which it is in loses its place for the rest of the
     * file — quietly, because what it goes on producing still looks like literals. This walks the four
     * states once, so it can be wrong about a character but never about where it is.
     *
     * <p>No text block is handled, because this source has none. One would arrive here as an empty
     * literal followed by its own contents read as code, which is the kind of silence the second
     * canary above exists to catch.
     */
    private static List<String> literalsOf(String code) {
        List<String> literals = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.CODE;
        for (int at = 0; at < code.length(); at++) {
            char character = code.charAt(at);
            char next = at + 1 < code.length() ? code.charAt(at + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (character == '/' && next == '/') {
                        state = State.LINE_COMMENT;
                        at++;
                    } else if (character == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        at++;
                    } else if (character == '"') {
                        state = State.STRING;
                        current.setLength(0);
                    } else if (character == '\'') {
                        state = State.CHARACTER;
                    }
                }
                case LINE_COMMENT -> {
                    if (character == '\n') {
                        state = State.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (character == '*' && next == '/') {
                        state = State.CODE;
                        at++;
                    }
                }
                case STRING -> {
                    if (character == '\\') {
                        current.append(character).append(next);
                        at++;
                    } else if (character == '"') {
                        literals.add(current.toString());
                        state = State.CODE;
                    } else {
                        current.append(character);
                    }
                }
                case CHARACTER -> {
                    if (character == '\\') {
                        at++;
                    } else if (character == '\'') {
                        state = State.CODE;
                    }
                }
            }
        }
        return literals;
    }

    /** Where in a compilation unit the scan currently is, which is all it has to get right. */
    private enum State {
        CODE,
        STRING,
        CHARACTER,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + source, e);
        }
    }
}
