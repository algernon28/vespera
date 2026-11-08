package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.synthesis.ClusterFault;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
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
