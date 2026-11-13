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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A value's provenance is no "note" in the control panel's names (ADR-377 §11): {@code CONTEXT.md}'s Profile entry
 * lists the word among those it avoids, so no identifier of {@code io.algernon.vespera.ui}'s sources holds it as one of
 * its words, and no string naming an id of the window does. The window's own texts are written for a reader outside
 * this project and are free of the lists (ADR-122), so a string holding a space, which names no id, is not read.
 *
 * <p>The sources are read with their comments taken out, a javadoc being prose. An identifier is split into its words
 * at each underscore and each change of case, so {@code notExists} is the words {@code not} and {@code Exists}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("801")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProvenanceIsNamedSoTest {

    /** The control panel's sources, read from the checkout the build runs in. */
    private static final Path THE_CONTROL_PANELS_SOURCES = Path.of("src/main/java/io/algernon/vespera/ui");

    /** The word a provenance is not called, singular and plural. */
    private static final List<String> THE_WORDS_AVOIDED = List.of("note", "notes");

    /** Two names the renames gave, which the scan must read, so that a scan of nothing cannot pass. */
    private static final List<String> NAMES_THE_SCAN_MUST_READ = List.of("FILLED_IN_PROVENANCE", "launchLines");

    /** An identifier of Java's: a letter, a dollar or an underscore, then those or digits. */
    private static final Pattern AN_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** A string that may name an id: no space, only what an id or a key of a lookup is spelled with. */
    private static final Pattern A_STRING_NAMING_AN_ID = Pattern.compile("[#A-Za-z0-9._-]+");

    /** Where an identifier is split into its words: at an underscore, or between a lower case letter or digit and an upper case one. */
    private static final Pattern A_WORD_BREAK = Pattern.compile("_|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    @Test
    @Story("A value's provenance is named as such")
    @DisplayName("No name the control panel gives in its code calls the provenance saved with a value a note")
    void noNameOfTheControlPanelCallsAProvenanceANote() {
        List<Source> sources = sources();
        List<String> identifiers = sources.stream().flatMap(source -> source.identifiers().stream()).toList();

        claim("the scan read the control panel's sources, among their names " + NAMES_THE_SCAN_MUST_READ
                        + ", so that a scan of nothing cannot pass",
                () -> assertThat(identifiers).containsAll(NAMES_THE_SCAN_MUST_READ));
        claim("no identifier of them, split into its words at each change of case and each underscore, holds the"
                        + " word " + THE_WORDS_AVOIDED + " in any case",
                () -> assertThat(identifiers.stream().filter(TheProvenanceIsNamedSoTest::holdsTheWordAvoided).distinct())
                        .isEmpty());
        claim("and no string that names an id of the window holds it either",
                () -> assertThat(sources.stream()
                                .flatMap(source -> source.strings().stream())
                                .filter(string -> A_STRING_NAMING_AN_ID.matcher(string).matches())
                                .filter(TheProvenanceIsNamedSoTest::holdsTheWordAvoidedInAnId))
                        .isEmpty());
    }

    private static boolean holdsTheWordAvoided(String identifier) {
        return Stream.of(A_WORD_BREAK.split(identifier))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .anyMatch(THE_WORDS_AVOIDED::contains);
    }

    private static boolean holdsTheWordAvoidedInAnId(String id) {
        return Stream.of(id.split("[#._-]+")).anyMatch(TheProvenanceIsNamedSoTest::holdsTheWordAvoided);
    }

    private static List<Source> sources() {
        try (Stream<Path> files = Files.walk(THE_CONTROL_PANELS_SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java")).map(Source::read).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A source's code, its comments taken out, as its identifiers and its string literals. */
    private record Source(List<String> identifiers, List<String> strings) {

        static Source read(Path file) {
            String text;
            try {
                text = Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            StringBuilder code = new StringBuilder();
            List<String> strings = new ArrayList<>();
            int i = 0;
            while (i < text.length()) {
                if (text.startsWith("//", i)) {
                    int end = text.indexOf('\n', i);
                    i = end < 0 ? text.length() : end;
                } else if (text.startsWith("/*", i)) {
                    int end = text.indexOf("*/", i + 2);
                    i = end < 0 ? text.length() : end + 2;
                    code.append(' ');
                } else if (text.startsWith("\"\"\"", i)) {
                    int end = text.indexOf("\"\"\"", i + 3);
                    int stop = end < 0 ? text.length() : end;
                    strings.add(text.substring(i + 3, stop));
                    i = end < 0 ? text.length() : end + 3;
                    code.append(' ');
                } else if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                    char quote = text.charAt(i);
                    StringBuilder literal = new StringBuilder();
                    int j = i + 1;
                    while (j < text.length() && text.charAt(j) != quote) {
                        if (text.charAt(j) == '\\' && j + 1 < text.length()) {
                            literal.append(text.charAt(j + 1));
                            j += 2;
                        } else {
                            literal.append(text.charAt(j++));
                        }
                    }
                    if (quote == '"') {
                        strings.add(literal.toString());
                    }
                    i = j + 1;
                    code.append(' ');
                } else {
                    code.append(text.charAt(i++));
                }
            }
            return new Source(AN_IDENTIFIER.matcher(code).results().map(match -> match.group()).toList(), strings);
        }
    }
}
