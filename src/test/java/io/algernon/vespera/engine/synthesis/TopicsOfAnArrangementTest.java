package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * An arrangement read one topic at a time (ADR-223 section 4, ADR-409 section 13): one topic's clusters, in the order
 * stored, with nothing of another topic or of another arrangement among them.
 *
 * <p>Until ADR-223 the only read of the table gave every cluster of the arrangement at once, sorted by the
 * database. Stage 6a's page and stage 6b's walk and tree now ask for a topic's clusters as they come to it. The order
 * is still the stored one (ADR-112), subtopic by subtopic in its subtopic order and each subtopic's in its cluster
 * order (ADR-409 section 3): the rows are written here out of it, so that a read which answered in the order they
 * were written, in the order of their ordinals or subtopics, or by the cluster order alone, is told apart.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Arrangement")
@Feature("Recording the arrangement")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-241", url = Adr.THE_MANIFEST_ASKS_A_PAGES_PLACES_AND_A_SYNTHESIS_DOC_IS_READ_ONCE, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class TopicsOfAnArrangementTest {

    /** The topic recorded first and placed second: two clusters, no subtopics. */
    private static final TopicKey FIRST = new TopicKey("seeds/first.docx", 0);

    /** The topic recorded second and placed first: three clusters in two subtopics. */
    private static final TopicKey SECOND = new TopicKey("seeds/second.docx", 3);

    /** A topic with no subtopics. */
    private static final OptionalInt NONE = OptionalInt.empty();

    /** The second topic's subtopic placed second, though its number is the lower. */
    private static final OptionalInt SUBTOPIC_ZERO = OptionalInt.of(0);

    /** The second topic's subtopic placed first. */
    private static final OptionalInt SUBTOPIC_ONE = OptionalInt.of(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Clusters clusters;
    private RunId arrangement;

    /**
     * Two topics under one arrangement. The topic recorded first is placed second and holds two clusters, written
     * with the later one first; the topic recorded second is placed first and holds three, in two subtopics: the
     * subtopic numbered 1 is placed first and holds two clusters, the subtopic numbered 0 is placed second and holds
     * one, written first. So neither topic's place is the number of groups under it, and neither the subtopics'
     * numbers nor the cluster order alone gives the second topic's stored order. A second arrangement holds a
     * cluster under the first topic, which no read of the first arrangement may return.
     */
    @BeforeEach
    void twoTopicsWrittenOutOfOrder() {
        clusters = new Clusters(jdbcTemplate);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-topics-" + System.nanoTime()));
        arrangement = ledger.runs().startRun("arrangement", "a" + System.nanoTime(), "{}", walk, List.of());
        RunId another = ledger.runs().startRun("arrangement", "b" + System.nanoTime(), "{}", walk, List.of());

        clusters.record(arrangement, new ArrangedCluster(FIRST, NONE, 0, 2, 2, 0, 2), new ClusterLabel("Placed last"));
        clusters.record(arrangement, new ArrangedCluster(FIRST, NONE, 5, 1, 2, 0, 1), new ClusterLabel("Placed second"));
        clusters.record(
                arrangement, new ArrangedCluster(SECOND, SUBTOPIC_ZERO, 4, 1, 1, 2, 1), new ClusterLabel("Third under the first"));
        clusters.record(
                arrangement, new ArrangedCluster(SECOND, SUBTOPIC_ONE, 0, 3, 1, 1, 1), new ClusterLabel("Placed first"));
        clusters.record(
                arrangement, new ArrangedCluster(SECOND, SUBTOPIC_ONE, 3, 1, 1, 1, 2), new ClusterLabel("Second under the first"));
        clusters.record(another, new ArrangedCluster(FIRST, NONE, 9, 4, 1, 0, 1), new ClusterLabel("Of another arrangement"));
    }

    @Test
    @Story("An arrangement is read one topic at a time")
    @DisplayName("One topic's groups come back in the order the arrangement placed them, and no other topic's")
    void oneTopicsClustersComeInStoredOrder() {
        claim(
                "the two groups under the topic placed second come back in their stored order, which is"
                        + " neither the order they were recorded in nor the order of their numbers, each with"
                        + " its name, its size and its places",
                () -> assertThat(clusters.ofTopic(arrangement, FIRST))
                        .containsExactly(
                                new RecordedCluster(
                                        new ArrangedCluster(FIRST, NONE, 5, 1, 2, 0, 1), new ClusterLabel("Placed second")),
                                new RecordedCluster(
                                        new ArrangedCluster(FIRST, NONE, 0, 2, 2, 0, 2), new ClusterLabel("Placed last"))));
        claim(
                "and the other topic's three groups come back on their own, in their stored order: the part of the"
                        + " topic placed first with its two groups in order, then the part placed second",
                () -> assertThat(clusters.ofTopic(arrangement, SECOND))
                        .containsExactly(
                                new RecordedCluster(
                                        new ArrangedCluster(SECOND, SUBTOPIC_ONE, 0, 3, 1, 1, 1),
                                        new ClusterLabel("Placed first")),
                                new RecordedCluster(
                                        new ArrangedCluster(SECOND, SUBTOPIC_ONE, 3, 1, 1, 1, 2),
                                        new ClusterLabel("Second under the first")),
                                new RecordedCluster(
                                        new ArrangedCluster(SECOND, SUBTOPIC_ZERO, 4, 1, 1, 2, 1),
                                        new ClusterLabel("Third under the first"))));
    }
}
