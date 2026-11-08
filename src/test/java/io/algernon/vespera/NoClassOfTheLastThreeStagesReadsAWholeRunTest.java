package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.engine.embedding.DocumentCluster;
import io.algernon.vespera.engine.embedding.DocumentClusters;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.synthesis.Arrangement;
import io.algernon.vespera.engine.synthesis.ClusterFault;
import io.algernon.vespera.engine.synthesis.ClusterFaults;
import io.algernon.vespera.engine.synthesis.Clusters;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.SynthesisDocs;
import io.algernon.vespera.engine.synthesis.SynthesisStatement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reads and the forms ADR-223 takes out of stages 5f, 6a and 6b, held by their absence: no table of
 * theirs can be read a run at a time, and nothing that writes a page or the manifest is handed a list of
 * every cluster, every survivor or every member of a run.
 *
 * <p>A method that reads a whole run cannot be called by {@code src/main} once it is not there, which is a
 * shorter thing to hold than every caller's text. What replaced each is held where it can be exercised: a
 * partition's rows and a cluster's by key in {@code synthesis.PartitionsOfAnArrangementTest}, {@code
 * synthesis.AClusterIsAskedForByItsKeyTest} and {@code embedding.MembersAreReadAPartitionOrAPageAtATimeTest}.
 *
 * <p><b>By reflection, and by name where a type is not public</b>, so that this class compiles against the
 * tree as it stood before ADR-223 was built, where every claim here fails.
 */
@Epic("Pipeline")
@Feature("What the last three stages hold in memory")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class NoClassOfTheLastThreeStagesReadsAWholeRunTest {

    private static final String DELIVERABLE = "io.algernon.vespera.engine.deliverable.";

    private static final String PIPELINE = "io.algernon.vespera.engine.pipeline.";

    @Test
    @Story("No table of the last three stages is read a whole run at a time")
    @DisplayName("Nothing reads every grouped document, every group, every written text or every failure of a run in one go")
    void noTableIsReadAWholeRunAtATime() {
        for (List<Class<?>> tableAndRow : List.<List<Class<?>>>of(
                List.of(DocumentClusters.class, DocumentCluster.class),
                List.of(Clusters.class, RecordedCluster.class),
                List.of(SynthesisDocs.class, SynthesisDoc.class),
                List.of(ClusterFaults.class, ClusterFault.class))) {
            Class<?> table = tableAndRow.get(0);
            Class<?> row = tableAndRow.get(1);
            claim(
                    table.getSimpleName() + " has no read of everything one run recorded: no way in asked for a run"
                            + " alone hands back its rows together, which are asked for one exemplar's documents at"
                            + " a time, a page at a time, or one group at a time by its key",
                    () -> assertThat(Arrays.stream(table.getDeclaredMethods())
                                    .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {RunId.class}))
                                    .filter(method -> handsBackMany(method, row))
                                    .map(Method::getName))
                            .as("the reads of %s that take a run alone and hand back many %s", table.getSimpleName(),
                                    row.getSimpleName())
                            .isEmpty());
        }
    }

    @Test
    @Story("No table of the last three stages is read a whole run at a time")
    @DisplayName("Writing over the groups no longer announces a read of everything already written")
    void theReadOfEverythingAlreadyWrittenIsNotAnnounced() {
        claim(
                "the statement that read every written text of a run, to learn which groups to walk past, is"
                        + " gone from the statements this stage announces: the one left is the read of the failures"
                        + " standing under the run, and each group is asked about by its key",
                () -> assertThat(Arrays.stream(SynthesisStatement.values()).map(Enum::name))
                        .containsExactly("STANDING_FAULTS"));
    }

    @Test
    @Story("Nothing that writes a page is handed a whole run as a list")
    @DisplayName("The tree, the index, the listing and the pictures are written from a source asked a part at a time, never from lists of everything")
    void nothingThatWritesIsHandedAListOfEverything() throws ClassNotFoundException {
        claim(
                "the tree's writer takes a source it asks one exemplar at a time: none of its ways in takes a"
                        + " list, but for the plugin beans one of them is handed, which are the plugins compiled into"
                        + " the jar and not a run's rows (ADR-220, ADR-333 §1)",
                () -> assertThat(methodsNamed(Deliverable.class, "writeTo"))
                        .as("the ways into the tree's writer")
                        .isNotEmpty()
                        .allSatisfy(method -> assertThat(takesAList(method))
                                .as("whether %s takes a list", method)
                                .isFalse()));
        claim(
                "that source is the one type the writer was given for it",
                () -> assertThat(methodsNamed(Deliverable.class, "writeTo"))
                        .anySatisfy(method -> assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName))
                                .contains("ArrangedSurvivors")));
        Class<?> indexPage = Class.forName(DELIVERABLE + "IndexPage");
        Class<?> manifest = Class.forName(DELIVERABLE + "ManifestCsv");
        claim(
                "the index and the listing are appended to as their rows come: neither composes its whole file,"
                        + " and the index no longer gathers every group or every exemplar's path into a map",
                () -> {
                    assertThat(namesOf(indexPage.getDeclaredMethods())).doesNotContain("contents", "partitions", "seedPaths");
                    assertThat(namesOf(manifest.getDeclaredMethods())).doesNotContain("contents");
                });
        Class<?> entryPictures = Class.forName(DELIVERABLE + "EntryPictures");
        claim(
                "the pass that finds which pictures recur is fed its documents by the same source, not a list",
                () -> assertThat(methodsNamed(entryPictures, "among"))
                        .isNotEmpty()
                        .allSatisfy(method -> assertThat(takesAList(method))
                                .as("whether %s takes a list", method)
                                .isFalse()));
    }

    @Test
    @Story("Nothing that writes a page is handed a whole run as a list")
    @DisplayName("The arrangement is ordered one exemplar's groups at a time, and its page and the sizes page keep no list of every group")
    void theArrangementAndItsPagesKeepNoListOfEveryCluster() throws ClassNotFoundException {
        claim(
                "the groups are put in order one exemplar at a time: no ordering takes every exemplar's groups"
                        + " at once",
                () -> assertThat(methodsNamed(Arrangement.class, "order"))
                        .isNotEmpty()
                        .allSatisfy(method -> assertThat(takesAList(method))
                                .as("whether %s takes a list", method)
                                .isFalse()));
        Class<?> arrangementPage = Class.forName(PIPELINE + "ArrangementReport");
        claim(
                "the page the operator approves is written as each exemplar's groups come, so nothing renders"
                        + " it whole from a list",
                () -> assertThat(namesOf(arrangementPage.getDeclaredMethods()))
                        .doesNotContain("render")
                        .contains("open", "partition", "close"));
        Class<?> sizesRow = Class.forName(PIPELINE + "ClusterSizeReport$Partition");
        claim(
                "a row of the sizes page keeps its five numbers and not the size of every group they came from",
                () -> assertThat(Arrays.stream(sizesRow.getRecordComponents()).map(RecordComponent::getName))
                        .as("what a row of the sizes page is made of")
                        .doesNotContain("sizes")
                        .contains("documentCount", "clusterCount", "largest", "median", "singletons"));
    }

    /**
     * Whether {@code method} takes anything a whole run could be handed in: a list, and as much any other
     * collection, iterable, stream, map or array, which a form put back to take a run would reach for as
     * readily. A {@code List<Plugin>} is passed over: those are the plugin beans {@code pipeline} hands the
     * tree's writer, compiled into the jar and no run's rows (ADR-220, ADR-333 §1).
     */
    private static boolean takesAList(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Type[] generic = method.getGenericParameterTypes();
        return IntStream.range(0, types.length)
                .filter(i -> generic.length != types.length || !isThePluginBeans(generic[i]))
                .mapToObj(i -> types[i])
                .anyMatch(type -> type.isArray()
                        // A path is iterable over its names, and is no run.
                        || (Iterable.class.isAssignableFrom(type) && !java.nio.file.Path.class.isAssignableFrom(type))
                        || java.util.Map.class.isAssignableFrom(type)
                        || java.util.stream.BaseStream.class.isAssignableFrom(type));
    }

    /** Whether {@code type} is {@code List<io.algernon.vespera.engine.deliverable.Plugin>}, and nothing wider. */
    private static boolean isThePluginBeans(Type type) {
        return type instanceof ParameterizedType parameterized
                && parameterized.getRawType() == List.class
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0].getTypeName().equals(DELIVERABLE + "Plugin");
    }

    /**
     * Whether {@code method} hands back many {@code row}s at once: a collection, iterable, stream, map or
     * array whose declared type names the row.
     */
    private static boolean handsBackMany(Method method, Class<?> row) {
        Class<?> type = method.getReturnType();
        boolean many = type.isArray()
                || Iterable.class.isAssignableFrom(type)
                || java.util.Map.class.isAssignableFrom(type)
                || java.util.stream.BaseStream.class.isAssignableFrom(type);
        return many && method.getGenericReturnType().getTypeName().contains(row.getName());
    }

    private static List<String> namesOf(Method[] methods) {
        return Arrays.stream(methods).map(Method::getName).toList();
    }

    private static List<Method> methodsNamed(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .toList();
    }
}
