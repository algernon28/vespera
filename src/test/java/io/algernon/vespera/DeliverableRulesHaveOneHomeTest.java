package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each rule the deliverable is written by has one class, and nothing is written twice (ADR-213), the classes lying
 * in {@code deliverable} since ADR-274 §4 and the citation pattern, the filename stem and the cluster key staying in
 * {@code synthesis}, written once where the check and the label read them. Read off the compiled classes, as {@link
 * OnlyPipelineNamesSpringBatchTest} reads them, so a copy is found wherever it is put.
 *
 * <p><b>The escaping rule is HTML's, in one class.</b> ADR-134's Markdown surroundings went with ADR-274 §1.3, and
 * their reopen trigger with them; what replaces it is that exactly one class of the two modules writes the five
 * entities, {@code Html}, and none writes a backslash in front of markup.
 *
 * <p><b>What it cannot see</b>: an escaper whose entities or backslash appear in no string constant the class holds.
 * There the sentence is still checked by whoever reads it.
 *
 * <p>Retired with ADR-274: the claim that the destination's percent-encoding lies in {@code ArchiveLink} alone, the
 * class and its rule being gone; and that {@code MarkdownSurroundings} is the one class with a Markdown escape.
 */
@Epic("Architecture")
@Feature("Where the deliverable's rules live")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class DeliverableRulesHaveOneHomeTest {

    /** The two modules the deliverable's rules lie in. */
    private static final Set<String> THE_MODULES = Set.of("synthesis", "deliverable");

    private static final String IN_DELIVERABLE = "io.algernon.vespera.deliverable.";

    /** A backslash before a character a Markdown renderer reads as markup: what the Markdown surroundings inserted. */
    private static final List<String> MARKDOWN_ESCAPES = List.of("\\<", "\\&", "\\[", "\\]", "\\|", "\\`");

    /** A text that is one backslash and nothing else. */
    private static final String A_BACKSLASH = "\\";

    /** A backslash straight before a value, in a text built at run time ({@code "\\" + c}). */
    private static final String A_BACKSLASH_BEFORE_A_VALUE = A_BACKSLASH + '\u0001';

    /** A citation as the model writes it (ADR-109), the one regular expression that also holds {@code \[}. */
    private static final String THE_CITATION_PATTERN = "\\[(\\d+)\\]";

    /** The five entities ADR-274 §1.3's one escaping rule writes. */
    private static final List<String> THE_FIVE_ENTITIES = List.of("&amp;", "&lt;", "&gt;", "&quot;", "&#39;");

    /**
     * The package-private collaborators of the tree's writer, each carrying one rule (ADR-213 §1, ADR-274 §4), a copy's
     * name in its cluster folder among them (ADR-280 §6).
     */
    private static final List<String> THE_COLLABORATORS = List.of(
            "ManifestCsv",
            "ClusterPage",
            "IndexPage",
            "EntryPictures",
            "Copies",
            "CopyNames",
            "Html",
            "ProseHtml",
            "UnwrittenSentences");

    /**
     * Of those, the ones a plugin reads, which {@code deliverable} makes public for what a plugin reads of them and
     * nothing more (ADR-333 §2's table): every collaborator but {@code ManifestCsv}, which no plugin reads.
     */
    private static final Set<String> READ_BY_A_PLUGIN = Set.of(
            "ClusterPage",
            "IndexPage",
            "EntryPictures",
            "Copies",
            "CopyNames",
            "Html",
            "ProseHtml",
            "UnwrittenSentences");

    /** What a plugin reads of a member's copy (ADR-333 §2, §5): its outcome, its name and its path, and no writing. */
    private static final Set<String> WHAT_A_PLUGIN_READS_OF_THE_COPIES = Set.of("outcomeOf", "nameOf", "pathOf");

    /** What a plugin reads of {@code ClusterPage} (ADR-333 §2, ADR-339 §2). */
    private static final Set<String> WHAT_A_PLUGIN_READS_OF_THE_CLUSTER_PAGE = new TreeSet<>(Set.of(
            "ANCHOR_PREFIX", "firstPageBody", "listOpening", "nameOfPage", "navigation", "percentEncoded"));

    /** What a plugin reads of {@code ClusterPage.PageBreaks} (ADR-333 §2, ADR-338 §2, ADR-339 §2). */
    private static final Set<String> WHAT_A_PLUGIN_READS_OF_THE_PAGE_BREAKS =
            new TreeSet<>(Set.of("pages", "firstEntryOf", "nameOfPageHolding"));

    /** The names of {@code type}'s own public methods and fields, none a compiler wrote. */
    private static Set<String> publicMembersOf(Class<?> type) {
        Set<String> open = new TreeSet<>();
        Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .forEach(method -> open.add(method.getName()));
        Arrays.stream(type.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && !field.isSynthetic())
                .forEach(field -> open.add(field.getName()));
        return open;
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

    /** The sources that take a filename stem: the group's label, the index page and the Confluence pages. */
    private static final List<Path> STEM_READERS = List.of(
            Path.of("src/main/java/io/algernon/vespera/synthesis/ClusterLabel.java"),
            Path.of("src/main/java/io/algernon/vespera/deliverable/IndexPage.java"),
            Path.of("src/main/java/io/algernon/vespera/confluence/ConfluencePages.java"));

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
