package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.Occurrences;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reads that ask a page of occurrences by key, for what the tree lists of each survivor (ADR-240), cut
 * their pages by the ledger's one page size, {@link KeysetPages#ROWS_IN_A_PAGE}, and declare none of their own
 * (ADR-243), the measures of stage 2 and the counts of pieces among them (ADR-245), and the confidence
 * distribution's read of the measures (ADR-246). The classes that are not
 * public are named here by their names.
 *
 * <p>Read by reflection over each class's own declared fields: a page size of its own is a static final
 * number. Written before the change it pins: against the tree it was written on it fails, naming the content
 * keys' and the scores' own {@code OCCURRENCES_ASKED_AT_ONCE}.
 */
@Epic("Architecture")
@Feature("Reads a page at a time")
@Link(name = "ADR-243", url = Adr.THE_READS_BY_KEY_TAKE_THE_LEDGERS_PAGE, type = "adr")
@Link(name = "ADR-245", url = Adr.A_PAGE_IS_ASKED_WHETHER_IT_IS_KEPT_AND_A_SHARING_VIOLATION_IS_TRIED_AGAIN_ONLY_ON_WINDOWS, type = "adr")
@Link(name = "ADR-246", url = Adr.A_MOVE_ON_A_DRIVE_WINDOWS_SERVES_IS_TRIED_AGAIN_AND_THE_SURVIVORS_PAGES_ARE_THE_LEDGERS, type = "adr")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class TheReadsByKeyTakeTheLedgersPageTest {

    @Test
    @Story("A page asked by key is the ledger's page")
    @DisplayName("No class that asks a page of documents by their keys keeps a page size of its own")
    void noReadByKeyKeepsAPageSizeOfItsOwn() throws Exception {
        List<Class<?>> readsByKey = List.of(
                ExtractionCacheKeys.class,
                RelevanceScoring.class,
                Class.forName("io.algernon.vespera.embedding.RelevanceScoreCache"),
                Occurrences.class,
                ExtractionMetrics.class,
                Class.forName("io.algernon.vespera.extraction.ChunkCache"),
                ConfidenceDistribution.class);

        List<String> ownPageSizes = new ArrayList<>();
        for (Class<?> read : readsByKey) {
            for (Field field : read.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == int.class) {
                    ownPageSizes.add(read.getSimpleName() + "." + field.getName());
                }
            }
        }

        claim(
                "none of the reads that ask a page of documents by their keys, the content keys, the scores and"
                        + " their cache, the paths, the measures, their confidences and the counts of pieces, keeps a"
                        + " number of its own for how many it asks at once: each asks a page of the ledger's size, so"
                        + " the pages cannot drift apart",
                () -> assertThat(ownPageSizes).isEmpty());
    }
}
