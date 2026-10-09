package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.Arrays;
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
 */
@Epic("Architecture")
@Feature("Dead code")
@Issue("352")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class MethodsNothingShippedCallsAreGoneTest {

    /** The ledger's record of walks. */
    private static final String WALKS = "io.algernon.vespera.ledger.Walks";

    /** What the relevance floor entitles a run to do, a type nested in the floor's own class. */
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
    void theFloorNoLongerSaysWhetherItRemovesAnything() throws ClassNotFoundException {
        Set<String> methods = declaredMethodsOf(RELEVANCE_FLOOR_STATE);

        claim(
                "the outcome type no longer declares removesAnything: the step that applies the floor decides by"
                        + " which of the three outcomes it holds, and only a test asked the method",
                () -> assertThat(methods).doesNotContain("removesAnything"));
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

    /** The names of the methods {@code className} itself declares, synthetic ones left out. */
    private static Set<String> declaredMethodsOf(String className) throws ClassNotFoundException {
        Class<?> type = Class.forName(className, false, MethodsNothingShippedCallsAreGoneTest.class.getClassLoader());
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
