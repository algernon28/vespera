package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which shipped modules name a Spring Batch type: {@code pipeline}, and no other (ADR-209 section 2,
 * amending ADR-060's consumer contract).
 *
 * <p>{@code ModuleBoundariesTest} cannot hold this. It checks one module's references to another, and
 * Spring Batch is a library, so a capability module that named an {@code ItemStreamReader} passed it.
 * Five did: {@code ledger} returned one from three methods, and {@code corpus}, {@code extraction},
 * {@code similarity} and {@code embedding} each opened, read and closed the one it returned. This reads
 * every name each shipped class holds: a Batch type it returns, takes, extends, calls or only mentions
 * in a signature is among them.
 *
 * <p>The second test is the ticket's own criterion, that no helper is left which empties a reader into a
 * set. Its type is looked up by name, so that this class compiles whether or not the type exists.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class OnlyPipelineNamesSpringBatchTest {

    /** How a Spring Batch type is written inside a compiled class, in a reference or in a signature. */
    private static final String SPRING_BATCH = "org/springframework/batch/";

    /** The one module that assembles the job, and so the one that may name the framework the job runs on. */
    private static final String THE_MODULE_THAT_ASSEMBLES_THE_JOB = "pipeline";

    /** The helper that read a whole reader into a set, which went with the readers it emptied. */
    private static final String THE_HELPER_THAT_EMPTIED_A_READER = "io.algernon.vespera.pipeline.ItemStreamReaders";

    @Test
    @Story("The batch framework is named only where the job is assembled")
    @DisplayName("No module but the one that assembles the job names a Spring Batch type")
    void noModuleButPipelineNamesASpringBatchType() throws Exception {
        Map<String, List<String>> names = ShippedClasses.namesByClass();
        Set<String> naming = new TreeSet<>();
        Set<String> modulesRead = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : names.entrySet()) {
            String module = ShippedClasses.moduleOf(shipped.getKey());
            modulesRead.add(module);
            if (!THE_MODULE_THAT_ASSEMBLES_THE_JOB.equals(module)
                    && shipped.getValue().stream().anyMatch(name -> name.contains(SPRING_BATCH))) {
                naming.add(shipped.getKey());
            }
        }

        claim(
                "the classes read include those of the module that assembles the job and of the modules it"
                        + " assembles, so an empty answer below is not an empty scan",
                () -> assertThat(modulesRead)
                        .contains("ledger", "corpus", "extraction", "similarity", "embedding", "synthesis", "pipeline"));
        claim(
                "no class outside the module that assembles the job names a Spring Batch type, in what it"
                        + " returns, takes, extends or calls: a class named here hands the batch framework's"
                        + " own reader to whoever calls it, so every module that reads the surviving documents"
                        + " has to open, read and close it the way a batch step does",
                () -> assertThat(naming).isEmpty());
    }

    @Test
    @Story("The batch framework is named only where the job is assembled")
    @DisplayName("No helper is left that empties a batch reader into a set")
    void theHelperThatEmptiedAReaderIsGone() throws Exception {
        Map<String, List<String>> names = ShippedClasses.namesByClass();

        claim(
                "the helper that read a whole batch reader into a set is not among the shipped classes: what"
                        + " it emptied is no longer a batch reader, and a set of the surviving documents is"
                        + " built where one is needed by going through them",
                () -> assertThat(names).doesNotContainKey(THE_HELPER_THAT_EMPTIED_A_READER));
    }
}
