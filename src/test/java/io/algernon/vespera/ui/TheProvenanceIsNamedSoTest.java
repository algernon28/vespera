package io.algernon.vespera.ui;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A provenance is not a note, in the control panel's names (ADR-377 §11): {@code CONTEXT.md}'s Profile entry avoids the
 * word for a value's provenance, so no identifier of {@code io.algernon.vespera.ui} holds it as a word, and no string
 * naming a node's id does.
 *
 * <p>The sources are read with their comments taken out, and each identifier is split into its words at each change of
 * case and each underscore, so that {@code notExists} reads {@code not} and {@code exists}, and {@code codeSetNote}
 * reads {@code note}. A string names an id where it is a node id's spelling, lower case, digits, hyphens, dots and
 * angle brackets with no space, as {@code code-set-note} and {@code .provenance} are.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("801")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProvenanceIsNamedSoTest {

    /** The control panel's sources. */
    private static final Path SOURCES = Path.of("src/main/java/io/algernon/vespera/ui");

    /** The words no name of the control panel holds. */
    private static final Set<String> THE_WORDS = Set.of("note", "notes");

    /** A name the scan must read, so that a scan of nothing cannot pass: the file and the identifier it declares. */
    private static final Map<String, String> MUST_READ = Map.of(
            "ProfileEdit.java", "FILLED_IN_PROVENANCE",
            "ControlPanelState.java", "launchLines");

    /** A Java identifier. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** A string naming a node's id: no space, no capital, and at least one hyphen or dot between its words. */
    private static final Pattern AN_ID = Pattern.compile("[a-z0-9<>]*(?:[-.][a-z0-9<>]+)+|[-.][a-z0-9<>-]+");

    /** A boundary between two words of an identifier: an underscore, or a change from lower case to upper. */
    private static final Pattern WORD_BREAK = Pattern.compile("_+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    @Test
    @Story("A value's provenance is called so in the control panel's own names")
    @DisplayName("No name in the control panel's code calls the provenance a note")
    void noNameOfTheControlPanelCallsAProvenanceANote() throws IOException {
        Map<String, Set<String>> identifiersByFile = new TreeMap<>();
        List<String> offending = new ArrayList<>();
        try (Stream<Path> files = Files.list(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                Split split = split(Files.readString(file, StandardCharsets.UTF_8));
                identifiersByFile.put(file.getFileName().toString(), split.identifiers());
                for (String identifier : split.identifiers()) {
                    if (wordsOf(identifier).stream().anyMatch(THE_WORDS::contains)) {
                        offending.add(file.getFileName() + ": the identifier " + identifier);
                    }
                }
                for (String string : split.strings()) {
                    if (AN_ID.matcher(string).matches()
                            && List.of(string.split("[-.<>]+")).stream().anyMatch(THE_WORDS::contains)) {
                        offending.add(file.getFileName() + ": the id \"" + string + "\"");
                    }
                }
            }
        }

        MUST_READ.forEach((file, identifier) -> claim("the scan read " + file + " and found " + identifier
                        + " in it, so that it cannot pass by reading nothing",
                () -> assertThat(identifiersByFile.getOrDefault(file, Set.of())).contains(identifier)));
        claim("no identifier of the control panel holds the word note or notes, and no string naming an id holds it",
                () -> assertThat(offending).isEmpty());
    }

    /** An identifier's words, in lower case. */
    private static List<String> wordsOf(String identifier) {
        return Stream.of(WORD_BREAK.split(identifier)).filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT)).toList();
    }

    /** What a source holds outside its comments: the identifiers of its code and the contents of its string literals. */
    private record Split(Set<String> identifiers, List<String> strings) {
    }

    /** Reads a source, its comments taken out and its strings and characters read as literals, not as code. */
    private static Split split(String source) {
        StringBuilder code = new StringBuilder();
        List<String> strings = new ArrayList<>();
        int at = 0;
        while (at < source.length()) {
            char c = source.charAt(at);
            if (source.startsWith("//", at)) {
                int end = source.indexOf('\n', at);
                at = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", at)) {
                int end = source.indexOf("*/", at + 2);
                at = end < 0 ? source.length() : end + 2;
            } else if (source.startsWith("\"\"\"", at)) {
                int end = source.indexOf("\"\"\"", at + 3);
                strings.add(source.substring(at + 3, end));
                code.append(' ');
                at = end + 3;
            } else if (c == '"' || c == '\'') {
                StringBuilder literal = new StringBuilder();
                at++;
                while (source.charAt(at) != c) {
                    if (source.charAt(at) == '\\') {
                        literal.append(source.charAt(at + 1));
                        at += 2;
                    } else {
                        literal.append(source.charAt(at++));
                    }
                }
                at++;
                if (c == '"') {
                    strings.add(literal.toString());
                }
                code.append(' ');
            } else {
                code.append(c);
                at++;
            }
        }
        Set<String> identifiers = new TreeSet<>();
        Matcher matcher = IDENTIFIER.matcher(code);
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return new Split(identifiers, strings);
    }
}
