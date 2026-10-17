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
 * Each rule the deliverable is written by has one class, and nothing is written twice (ADR-213), the classes lying
 * in {@code deliverable} since ADR-273 §4 and the citation pattern, the filename stem and the cluster key staying in
 * {@code synthesis}, written once where the check and the label read them. Read off the compiled classes, as {@link
 * OnlyPipelineNamesSpringBatchTest} reads them, so a copy is found wherever it is put.
 *
 * <p><b>The escaping rule is HTML's, in one class.</b> ADR-134's Markdown surroundings went with ADR-273 §1.3, and
 * their reopen trigger with them; what replaces it is that exactly one class of the two modules writes the five
 * entities, {@code Html}, and none writes a backslash in front of markup.
 *
 * <p><b>What it cannot see</b>: an escaper whose entities or backslash appear in no string constant the class holds.
 * There the sentence is still checked by whoever reads it.
 *
 * <p>Retired with ADR-273: the claim that the destination's percent-encoding lies in {@code ArchiveLink} alone, the
 * class and its rule being gone; and that {@code MarkdownSurroundings} is the one class with a Markdown escape.
 */
@Epic("Architecture")
@Feature("Where the deliverable's rules live")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-273", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class DeliverableRulesHaveOneHomeTest {

    /** The two modules the deliverable's rules lie in. */
    private static final Set<String> THE_MODULES = Set.of("synthesis", "deliverable");

    private static final String IN_SYNTHESIS = "io.algernon.vespera.synthesis.";

    private static final String IN_DELIVERABLE = "io.algernon.vespera.deliverable.";

    /** A backslash before a character a Markdown renderer reads as markup: what the Markdown surroundings inserted. */
    private static final List<String> MARKDOWN_ESCAPES = List.of("\\<", "\\&", "\\[", "\\]", "\\|", "\\`");

    /** A text that is one backslash and nothing else. */
    private static final String A_BACKSLASH = "\\";

    /** A backslash straight before a value, in a text built at run time ({@code "\\" + c}). */
    private static final String A_BACKSLASH_BEFORE_A_VALUE = A_BACKSLASH + '\u0001';

    /** A citation as the model writes it (ADR-109), the one regular expression that also holds {@code \[}. */
    private static final String THE_CITATION_PATTERN = "\\[(\\d+)\\]";

    /** The five entities ADR-273 §1.3's one escaping rule writes. */
    private static final List<String> THE_FIVE_ENTITIES = List.of("&amp;", "&lt;", "&gt;", "&quot;", "&#39;");

    /** A quote doubled inside a quoted CSV field (RFC 4180). */
    private static final String A_DOUBLED_QUOTE = "\"\"";

    /** The package-private collaborators of the tree's writer, each carrying one rule (ADR-213 §1, ADR-273 §4). */
    private static final List<String> THE_COLLABORATORS = List.of(
            "ManifestCsv",
            "ClusterPage",
            "IndexPage",
            "EntryPictures",
            "Copies",
            "Html",
            "ProseHtml",
            "UnwrittenSentences");

    @Test
    @Story("Each rule has one class")
    @DisplayName("Each collaborator the tree's writer is split into exists in its module and is not public")
    void eachCollaboratorIsPackagePrivate() throws Exception {
        Set<String> classes = classesOf(THE_MODULES);
        for (String collaborator : THE_COLLABORATORS) {
            claim(
                    collaborator + " is a class of deliverable and is not public: it carries one rule of the"
                            + " tree, and Deliverable is the module's one door to them",
                    () -> {
                        assertThat(classes).contains(IN_DELIVERABLE + collaborator);
                        assertThat(Modifier.isPublic(loaded(IN_DELIVERABLE + collaborator).getModifiers()))
                                .as("whether %s is public", collaborator)
                                .isFalse();
                    });
        }
        claim(
                "and the citation pattern and the filename stem are synthesis's, the generation check and the label"
                        + " reading them there as the tree does",
                () -> assertThat(classes).contains(IN_SYNTHESIS + "Citation", IN_SYNTHESIS + "FilenameStem"));
    }

    @Test
    @Story("Each rule has one class")
    @DisplayName("One class writes the five entities the escaping rule writes, and none writes a backslash in front of markup")
    void oneClassEscapes() throws Exception {
        Set<String> entities = classesHolding(constant -> THE_FIVE_ENTITIES.stream().anyMatch(constant::contains));
        Set<String> backslashing = classesHolding(DeliverableRulesHaveOneHomeTest::writesAMarkdownEscape);
        backslashing.addAll(classesAppending(A_BACKSLASH));

        claim(
                "the classes of synthesis and deliverable that hold any of &amp; &lt; &gt; &quot; and &#39; are"
                        + " exactly one, Html: a second is two copies of one rule, and the copy is where a"
                        + " character goes unescaped",
                () -> assertThat(entities).containsExactly(IN_DELIVERABLE + "Html"));
        claim(
                "and no class of the two writes a backslash escape of < & [ ] | or `, a backslash straight before"
                        + " a value or a text that is one backslash: the Markdown surroundings are gone, and"
                        + " nothing is written as Markdown",
                () -> assertThat(backslashing).isEmpty());
    }

    @Test
    @Story("Each rule has one class")
    @DisplayName("The CSV's doubled quote is in ManifestCsv alone")
    void theCsvsQuotingHasOneClass() throws Exception {
        Set<String> doubling = classesHolding(constant -> constant.contains(A_DOUBLED_QUOTE));

        claim(
                "a doubled quote is written by one class, ManifestCsv: the CSV answers to a parser (ADR-136 §5)",
                () -> assertThat(doubling).containsExactly(IN_DELIVERABLE + "ManifestCsv"));
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("The citation pattern is written once, in Citation")
    void theCitationPatternIsWrittenOnce() throws Exception {
        Set<String> holding = classesHolding(THE_CITATION_PATTERN::equals);

        claim(
                "one class of the two modules holds the citation pattern, Citation, so the range check and the"
                        + " link the page writes cannot come to disagree about what a citation is (ADR-213 §4)",
                () -> assertThat(holding).containsExactly(IN_SYNTHESIS + "Citation"));
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("No class of the two modules declares a stem method of its own, so FilenameStem is the one rule")
    void theFilenameStemIsWrittenOnce() throws Exception {
        Set<String> declaring = new TreeSet<>();
        for (String name : classesOf(THE_MODULES)) {
            for (Method method : loaded(name).getDeclaredMethods()) {
                if (method.getName().toLowerCase(Locale.ROOT).contains("stem")) {
                    declaring.add(name + "." + method.getName());
                }
            }
        }

        claim(
                "no method of synthesis or deliverable is named for a stem: ClusterLabel, the pages and the copies"
                        + " all call FilenameStem.of, where the one rule is (ADR-213 §4)",
                () -> assertThat(declaring).isEmpty());
    }

    @Test
    @Story("Nothing is written twice")
    @DisplayName("One record of the two modules is a winning seed and an ordinal, ClusterSlot")
    void theClusterKeyIsWrittenOnce() throws Exception {
        Set<String> keys = new TreeSet<>();
        for (String name : classesOf(THE_MODULES)) {
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
                "the only record of synthesis or deliverable made of a winning seed and an ordinal is ClusterSlot:"
                        + " a survivor answers its own with ListedSurvivor.slot, which makes one (ADR-213 §3)",
                () -> assertThat(keys).containsExactly(IN_SYNTHESIS + "ClusterSlot"));
    }

    /** The class {@code name}, loaded without running its static initialisers. */
    private static Class<?> loaded(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }

    /** Every shipped class of {@code modules}, nested classes included. */
    private static Set<String> classesOf(Set<String> modules) throws Exception {
        Set<String> names = new TreeSet<>();
        for (String name : ShippedClasses.stringsByClass().keySet()) {
            if (modules.contains(ShippedClasses.moduleOf(name))) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("the scan found no class of " + modules + ", so an empty answer would mean nothing");
        }
        return names;
    }

    /** The top-level classes of the two modules one of whose string constants {@code holds} matches. */
    private static Set<String> classesHolding(Predicate<String> holds) throws Exception {
        Set<String> holding = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.stringsByClass().entrySet()) {
            if (THE_MODULES.contains(ShippedClasses.moduleOf(shipped.getKey()))
                    && shipped.getValue().stream().anyMatch(holds)) {
                holding.add(shipped.getKey().split("\\$")[0]);
            }
        }
        return holding;
    }

    /** The top-level classes of the two modules that append {@code character} to a text they are building. */
    private static Set<String> classesAppending(String character) throws Exception {
        Set<String> appending = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : ShippedClasses.appendedCharactersByClass().entrySet()) {
            if (THE_MODULES.contains(ShippedClasses.moduleOf(shipped.getKey()))
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
        return holdsAnEscape || constant.equals(A_BACKSLASH) || constant.contains(A_BACKSLASH_BEFORE_A_VALUE);
    }
}
