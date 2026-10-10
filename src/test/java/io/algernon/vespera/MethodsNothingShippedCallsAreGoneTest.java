package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The three shipped methods no shipped class called are gone (ADR-216 section 3): the ledger's question
 * whether a walk finished, the relevance floor's answer whether it removes anything, and the clusters'
 * list of the seed partitions a run clustered.
 *
 * <p>Each was read against the tree at {@code 3fc6f5d}. The first two were called by tests alone, which
 * ask the same question through what ships instead; the third was called by nothing at all. The three
 * live in modules whose removal moves only the run ids an earlier change already moved, which is why
 * they go now and the dead code in two other modules waits (ADR-216 section 8).
 *
 * <p>Read by reflection over each class's own declared methods, so a method of the same name added to
 * another class does not satisfy a claim here. Written before the change it pins: against the tree it was
 * written on, every test here fails, naming the method that is still there.
 *
 * <p><b>The dead code in the two other modules is gone too</b> (ADR-216 section 5, #469), carried by the
 * change ADR-220 records, which re-mints both: {@code corpus}'s read of the representative, which only
 * tests called, and {@code extraction}'s three structureless fallback types and its second copy of the rule
 * that resolves a Docling reference. The last three tests hold those. They were written after that change,
 * so none of the three was seen to fail.
 */
@Epic("Architecture")
@Feature("Dead code")
@Issue("352")
@Issue("469")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class MethodsNothingShippedCallsAreGoneTest {

    /** The record of content identity and of which copy stands for the others. */
    private static final String CONTENT_IDENTITY = "io.algernon.vespera.corpus.ContentIdentity";

    private static final String EXTRACTION = "io.algernon.vespera.extraction.";

    /** The three types of the structureless fallback, both halves and the seam between them. */
    private static final List<String> THE_STRUCTURELESS_FALLBACK = List.of(
            EXTRACTION + "StructurelessChunkingFallback",
            EXTRACTION + "LlmStructurelessChunkingFallback",
            EXTRACTION + "WindowedStructurelessChunkingFallback");

    /** The one class of the module that resolves a Docling reference, by a method the picture reader calls too. */
    private static final String THE_ONE_RESOLVER = EXTRACTION + "DoclingDocumentTexts";

    /** The type of a node of Docling's JSON, by its simple name: what a resolver takes and answers. */
    private static final String A_JSON_NODE = "JsonNode";

    /** The ledger's record of walks. */
    private static final String WALKS = "io.algernon.vespera.ledger.Walks";

    /**
     * What the relevance floor lets a run do. A type nested in the floor's class of {@code pipeline} until
     * ADR-226 moved the rule into {@code embedding}, as a type that answers the two actions.
     */
    private static final String FLOOR_REACH = "io.algernon.vespera.embedding.FloorReach";

    /** Where the outcome type lived before ADR-226. */
    private static final String RELEVANCE_FLOOR_STATE = "io.algernon.vespera.pipeline.RelevanceFloor$State";

    /** The clusters a run made, as the embedding module records them. */
    private static final String DOCUMENT_CLUSTERS = "io.algernon.vespera.embedding.DocumentClusters";

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("The record of walks no longer answers whether one walk finished, a question only tests asked")
    void theWalksNoLongerAnswerWhetherOneFinished() throws ClassNotFoundException {
        Set<String> methods = declaredMethodsOf(WALKS);

        claim(
                "the record of walks was read and has its methods, so an empty answer below is not an empty class",
                () -> assertThat(methods).contains("finishedWalkFor"));
        claim(
                "and it no longer declares walkFinished: no shipped class asked it, and a test that wants to know"
                        + " asks for the finished walk of a root, as every stage does",
                () -> assertThat(methods).doesNotContain("walkFinished"));
    }

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("The relevance floor's outcome no longer says whether it removes anything, a question only tests asked")
    @Issue("479")
    @Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
    void theFloorNoLongerSaysWhetherItRemovesAnything() throws ClassNotFoundException {
        Set<String> methods = declaredMethodsOf(FLOOR_REACH);

        claim(
                "the floor's outcome was read and answers the two actions the step takes, so an empty answer"
                        + " below is not an empty type",
                () -> assertThat(methods).contains("withdrawsStandingRemovals", "removesBelow"));
        claim(
                "and it declares no removesAnything: the step that applies the floor acts on the two actions"
                        + " it is answered, below which number to remove and whether to withdraw what it"
                        + " removed before, and only a test asked the method",
                () -> assertThat(methods).doesNotContain("removesAnything"));
        claim(
                "nor is the outcome type of the code that runs the stages still there to declare it",
                () -> assertThat(loads(RELEVANCE_FLOOR_STATE)).isFalse());
    }

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("The clusters no longer list the seed partitions a run clustered, which nothing asked for")
    void theClustersNoLongerListTheirPartitions() throws ClassNotFoundException {
        Set<String> methods = declaredMethodsOf(DOCUMENT_CLUSTERS);

        claim(
                "the clusters' record was read and has its methods, so an empty answer below is not an empty class",
                () -> assertThat(methods).isNotEmpty());
        claim(
                "and it no longer declares partitionsFor, which neither shipped code nor any test called",
                () -> assertThat(methods).doesNotContain("partitionsFor"));
    }

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("The record of identical files no longer answers which copy stands for another, a question only tests asked")
    void contentIdentityNoLongerAnswersWhichCopyStandsForAnother() throws ClassNotFoundException {
        Set<String> methods = declaredMethodsOf(CONTENT_IDENTITY);

        claim(
                "the record was read and still writes which copy stands for another, so the table a person may"
                        + " query is still filled",
                () -> assertThat(methods).contains("recordSupersededBy"));
        claim(
                "and it no longer declares representativeFor: no shipped class asked it, and a test that wants"
                        + " to know reads the table itself",
                () -> assertThat(methods).doesNotContain("representativeFor"));
    }

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("Nothing is left of the two ways of splitting a document that has no structure, neither of which ever split one")
    void theStructurelessFallbackIsGone() {
        ClassLoader shipped = MethodsNothingShippedCallsAreGoneTest.class.getClassLoader();
        List<String> stillThere = THE_STRUCTURELESS_FALLBACK.stream()
                .filter(type -> {
                    try {
                        Class.forName(type, false, shipped);
                        return true;
                    } catch (ClassNotFoundException gone) {
                        return false;
                    }
                })
                .toList();

        claim(
                "none of the three types is there to load: the one that asked a model threw when used, and the"
                        + " one that cut by word count was only ever handed empty text",
                () -> assertThat(stillThere).isEmpty());
    }

    @Test
    @Story("Nothing ships that nothing calls")
    @DisplayName("One class works out what a reference inside a converted document points at, and no second copy of that rule exists")
    void oneClassOfExtractionResolvesADoclingReference() throws ClassNotFoundException, IOException {
        Set<String> resolvers = new TreeSet<>();
        for (String shippedClass : ShippedClasses.stringsByClass().keySet()) {
            if (!shippedClass.startsWith(EXTRACTION)) {
                continue;
            }
            Class<?> type = Class.forName(shippedClass, false, MethodsNothingShippedCallsAreGoneTest.class.getClassLoader());
            boolean resolves = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic())
                    .anyMatch(method -> method.getReturnType().getSimpleName().equals(A_JSON_NODE)
                            && method.getParameterCount() == 2
                            && method.getParameterTypes()[0].getSimpleName().equals(A_JSON_NODE)
                            && method.getParameterTypes()[1] == String.class);
            if (resolves) {
                resolvers.add(shippedClass);
            }
        }

        claim(
                "of the classes that read a converted document, exactly one declares a method that takes the"
                        + " document and a reference and answers what the reference points at: the reader of a"
                        + " document's texts, which the reader of its pictures calls instead of keeping a copy",
                () -> assertThat(resolvers).containsExactly(THE_ONE_RESOLVER));
    }

    /** Whether {@code className} is there to load. */
    private static boolean loads(String className) {
        try {
            Class.forName(className, false, MethodsNothingShippedCallsAreGoneTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException gone) {
            return false;
        }
    }

    /** The names of the methods {@code className} itself declares, synthetic ones left out. */
    private static Set<String> declaredMethodsOf(String className) throws ClassNotFoundException {
        Class<?> type = Class.forName(className, false, MethodsNothingShippedCallsAreGoneTest.class.getClassLoader());
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
