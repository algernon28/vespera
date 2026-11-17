package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code cluster-sizes.html} has one row a topic, and the row names the topic by its key, its first passage's seed path
 * and chunk index written {@code <path> chunk <i>} (ADR-409 §8), until #834 settles what the page shows of topics.
 *
 * <p>Drawn by {@link ClusterSizeReport} alone. The chunk index is {@value #THE_CHUNK}, not 0, so that a row naming the
 * seed's path alone, or the chunk by a number the code took from elsewhere, is told apart.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class TheClusterSizesPageNamesATopicByItsKeyTest {

    /** The seed path of the topic's first passage. */
    private static final String THE_SEED_PATH = "seeds/one.docx";

    /** The chunk index of the topic's first passage. */
    private static final int THE_CHUNK = 3;

    @Test
    @Story("Each topic's groups are counted on the group sizes page")
    @DisplayName("The group sizes page names each topic's row by the seed and the passage it begins with")
    void aTopicsRowIsNamedByItsPathAndChunk() {
        String page = ClusterSizeReport.render(
                List.of(ClusterSizeReport.Topic.of(THE_SEED_PATH, THE_CHUNK, List.of(2, 1), Optional.empty())));

        claim("the topic's row opens with a cell naming the seed's path, then the word chunk and the passage's number, "
                        + THE_CHUNK,
                () -> assertThat(page).contains("<tr><td>seeds/one.docx chunk 3</td>"));
    }
}
