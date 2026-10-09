package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each rule the deliverable is written by has one class in {@code synthesis}, and nothing is written
 * twice (ADR-213). Read off the compiled classes, as {@link OnlyPipelineNamesSpringBatchTest} reads them,
 * so a copy is found wherever it is put.
 *
 * <p><b>ADR-134's reopen trigger, held by a test for the first time.</b> ADR-134 §2 reads: <em>a second
 * class under {@code synthesis} grows a Markdown escaping method</em>. Until this class it was a sentence
 * checked by whoever read it; {@code DeliverableTest} holds what each surrounding writes, and nothing held
 * where the rule lives. Exactly one class may carry a Markdown escaping rule, and here a class carries
 * one when its compiled form shows any of five things:
 *
 * <ul>
 *   <li>a text holding a backslash escape of a character a renderer reads as markup ({@link
 *       #MARKDOWN_ESCAPES}), the citation pattern excepted;
 *   <li>a text built by putting a backslash straight before a value ({@link #A_BACKSLASH_BEFORE_A_VALUE}),
 *       which is what {@code "\\" + c} compiles to and how {@code MarkdownSurroundings} itself escapes;
 *   <li>a text that is one backslash and nothing else ({@link #A_BACKSLASH});
 *   <li>a regex replacement writing a backslash before what it matched ({@link
 *       #A_BACKSLASH_BEFORE_WHAT_A_REGEX_MATCHED});
 *   <li>a backslash character appended to a text being built, as {@code append('\\')}.
 * </ul>
 *
 * <p><b>What it cannot see</b>: an escaper whose backslash appears in none of those forms, one held in a
 * {@code char} variable before it is appended or taken from another class among them. There the
 * sentence is still checked by whoever reads it.
 *
 * <p><b>Red against {@code 4b99a03}, by design</b>: every rule below lives in {@code Deliverable} there,
 * the citation pattern and the filename stem twice, and the cluster key three times.
 */
@Epic("Architecture")
@Feature("Where the deliverable's rules live")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-134", url = Adr.A_BREAK_IS_FOLDED_AND_THREE_ESCAPING_RULES_STAND, type = "adr")
@Link(name = "ADR-137", url = Adr.A_DESTINATIONS_AMPERSAND_IS_PERCENT_ENCODED, type = "adr")
class DeliverableRulesHaveOneHomeTest {

    /** The module every rule here belongs to. */
    private static final String SYNTHESIS = "synthesis";

    /** Where the module's classes are named from. */
    private static final String IN_SYNTHESIS = "io.algernon.vespera.synthesis.";

    /**
     * A backslash before a character a Markdown renderer reads as markup: what each Markdown surrounding
     * inserts (ADR-136, ADR-138, ADR-148). A class whose constants hold one carries a Markdown escaping
     * rule.
     */
    private static final List<String> MARKDOWN_ESCAPES = List.of("\\<", "\\&", "\\[", "\\]", "\\|", "\\`");

    /** The one character every Markdown escape here begins with. */
    private static final String A_BACKSLASH = "\\";

    /**
     * A backslash straight before a value, in a text built at run time: the compiler holds such a text as
     * its fixed parts with the character U+0001 where each value goes, so {@code "\\" + c} is held as a
     * backslash and that mark.
     */
    private static final String A_BACKSLASH_BEFORE_A_VALUE = A_BACKSLASH + '\u0001';

    /**
     * Two backslashes and a dollar sign: in a regex replacement, a literal backslash followed by a
     * reference to what was matched, as in {@code replaceAll("([<&])", "\\\\$1")}.
     */
    private static final String A_BACKSLASH_BEFORE_WHAT_A_REGEX_MATCHED = "\\\\$";

    /** A citation as the model writes it (ADR-109), the one regular expression that also holds {@code \[}. */
    private static final String THE_CITATION_PATTERN = "\\[(\\d+)\\]";

    /** What a destination's rule adds on top of the URI's own quoting (ADR-135, ADR-137 §1). */
    private static final List<String> PERCENT_ENCODINGS = List.of("%26", "%28", "%29");

    /** A quote doubled inside a quoted CSV field (RFC 4180). */
    private static final String A_DOUBLED_QUOTE = "\"\"";

    /** The package-private collaborators ADR-213 §1 and §4 name, each carrying one rule. */
    private static final List<String> THE_COLLABORATORS = List.of(
            "MarkdownSurroundings",
            "ArchiveLink",
            "ManifestCsv",
            "ClusterPage",
            "IndexPage",
            "EntryPictures",
            "Citation",
            "FilenameStem");

    @Test
    @Story("Each rule has one class")
    @DisplayName("Each collaborator the deliverable is split into exists in synthesis and is package-private")
    void eachCollaboratorIsPackagePrivate() throws Exception {
        Set<String> classes = synthesisClasses();
        for (String collaborator : THE_COLLABORATORS) {
            claim(
                    collaborator + " is a class of synthesis and is not public: it carries one rule of the"
                            + " deliverable, and Deliverable is the module's one door to them (ADR-213 §1)",
                    () -> {
                        assertThat(classes).contains(IN_SYNTHESIS + collaborator);
                        assertThat(Modifier.isPublic(loaded(IN_SYNTHESIS + collaborator).getModifiers()))
                                .as("whether %s is public", collaborator)
                                .isFalse();
                    });
        }
    }

    @Test
    @Story("Each rule has one class")
    @DisplayName("One class under synthesis carries a Markdown escaping rule, MarkdownSurroundings, and Deliverable carries none")
    void oneClassCarriesAMarkdownEscapingRule() throws Exception {
        Set<String> escaping = classesHolding(DeliverableRulesHaveOneHomeTest::writesAMarkdownEscape);
        escaping.addAll(classesAppending(A_BACKSLASH));

        claim(
                "the classes under synthesis that write a backslash in front of markup are exactly one,"
                        + " MarkdownSurroundings. A class counts when its compiled form holds a backslash"
                        + " escape of < & [ ] | or `, a text built with a backslash straight before a value,"
                        + " a text that is one backslash, a regex replacement that puts a backslash before"
                        + " what it matched, or a backslash character appended to a text. A second such"
                        + " class is two copies of one rule for one grammar, and Deliverable being one"
                        + " means the rules were copied rather than moved",
                () -> assertThat(escaping)
                        .as("the classes under synthesis that write a backslash in front of markup")
                        .containsExactly(IN_SYNTHESIS + "MarkdownSurroundings"));
    }

    @Test
    @Story("Each rule has one class")
    @DisplayName("The destination's percent-encoding is in ArchiveLink alone, and the CSV's doubled quote in ManifestCsv alone")
    void theOtherTwoSurroundingsHaveOneClassEach() throws Exception {
        Set<String> encoding = classesHolding(constant -> PERCENT_ENCODINGS.stream().anyMatch(constant::contains));
        Set<String> doubling = classesHolding(constant -> constant.contains(A_DOUBLED_QUOTE));

        claim(
                "%26, %28 and %29 are written by one class, ArchiveLink: the destination answers to a resolver,"
                        + " so its rule is percent-encoding and not a row of the Markdown table (ADR-137 §4)",
                () -> assertThat(encoding).containsExactly(IN_SYNTHESIS + "ArchiveLink"));
        claim(
                "a doubled quote is written by one class, ManifestCsv: the CSV answers to a parser (ADR-136 §5)",
                () -> assertThat(doubling).containsExactly(IN_SYNTHESIS + "ManifestCsv"));
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("The citation pattern is written once, in Citation")
    void theCitationPatternIsWrittenOnce() throws Exception {
        Set<String> holding = classesHolding(THE_CITATION_PATTERN::equals);

        claim(
                "one class holds the citation pattern, Citation, so the range check and the link rewrite cannot"
                        + " come to disagree about what a citation is (ADR-213 §4)",
                () -> assertThat(holding).containsExactly(IN_SYNTHESIS + "Citation"));
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("No class under synthesis declares a stem method of its own, so FilenameStem is the one rule")
    void theFilenameStemIsWrittenOnce() throws Exception {
        Set<String> declaring = new TreeSet<>();
        for (String name : synthesisClasses()) {
            for (Method method : loaded(name).getDeclaredMethods()) {
                if (method.getName().toLowerCase(Locale.ROOT).contains("stem")) {
                    declaring.add(name + "." + method.getName());
                }
            }
        }

        claim(
                "no method under synthesis is named for a stem: ClusterLabel and the deliverable both call"
                        + " FilenameStem.of, where the one rule is (ADR-213 §4)",
                () -> assertThat(declaring).isEmpty());
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("One record under synthesis is a winning seed and an ordinal, ClusterSlot")
    void theClusterKeyIsWrittenOnce() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (String name : synthesisClasses()) {
            Class<?> type = loaded(name);
            if (type.isRecord()
                    && Arrays.stream(type.getRecordComponents())
                            .map(RecordComponent::getType)
                            .toList()
                            .equals(List.of(io.algernon.vespera.ledger.OccurrenceId.class, int.class))) {
                keys.add(name);
            }
        }

        claim(
                "the only record under synthesis made of a winning seed and an ordinal is ClusterSlot: the"
                        + " private keys Deliverable and SynthesisDocs kept are gone into it (ADR-213 §3)",
                () -> assertThat(keys).containsExactly(IN_SYNTHESIS + "ClusterSlot"));
    }

    /** The class {@code name}, loaded without running its static initialisers. */
    private static Class<?> loaded(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }

    /** Every shipped class under {@code synthesis}, nested classes included. */
    private static Set<String> synthesisClasses() throws Exception {
        Set<String> names = new TreeSet<>();
        for (String name : ShippedClasses.stringsByClass().keySet()) {
            if (SYNTHESIS.equals(ShippedClasses.moduleOf(name))) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("the scan found no class under synthesis, so an empty answer would mean nothing");
        }
        return names;
    }

    /**
     * The top-level classes under {@code synthesis} one of whose string constants {@code holds} matches,
     * a nested class answering for the class it is declared in.
     */
    private static Set<String> classesHolding(Predicate<String> holds) throws Exception {
        Set<String> holding = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.stringsByClass().entrySet()) {
            if (SYNTHESIS.equals(ShippedClasses.moduleOf(shipped.getKey()))
                    && shipped.getValue().stream().anyMatch(holds)) {
                holding.add(shipped.getKey().split("\\$")[0]);
            }
        }
        return holding;
    }

    /**
     * The top-level classes under {@code synthesis} that append {@code character} to a text they are
     * building, a nested class answering for the class it is declared in.
     */
    private static Set<String> classesAppending(String character) throws Exception {
        Set<String> appending = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.appendedCharactersByClass().entrySet()) {
            if (SYNTHESIS.equals(ShippedClasses.moduleOf(shipped.getKey()))
                    && shipped.getValue().contains(character)) {
                appending.add(shipped.getKey().split("\\$")[0]);
            }
        }
        return appending;
    }

    /** Whether one text a class holds is, or is part of, a backslash written in front of markup. */
    private static boolean writesAMarkdownEscape(String constant) {
        boolean holdsAnEscape =
                !constant.equals(THE_CITATION_PATTERN) && MARKDOWN_ESCAPES.stream().anyMatch(constant::contains);
        return holdsAnEscape
                || constant.equals(A_BACKSLASH)
                || constant.contains(A_BACKSLASH_BEFORE_A_VALUE)
                || constant.contains(A_BACKSLASH_BEFORE_WHAT_A_REGEX_MATCHED);
    }
}
