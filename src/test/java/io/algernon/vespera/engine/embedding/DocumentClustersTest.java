package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Which cluster each survivor landed in (ADR-087), inside the topic it is placed under and, where that topic is
 * divided, its subtopic (ADR-409 section 3).
 *
 * <p><b>A cluster exists as the set of rows carrying its identity</b> — the run, the topic, the subtopic and the
 * ordinal — rather than as a row of its own that membership would have to be kept in step with. There is nothing to
 * go stale, and a cluster with no members simply has no rows.
 *
 * <p><b>No in-topic unattributed bucket.</b> ADR-022's unattributed bucket sits at the top level, for documents with
 * nowhere to go at all, which is a different condition with a different cause. A second one inside a topic would
 * fabricate a page no document belongs to.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class DocumentClustersTest {

    /** The topic both documents are placed under. */
    private static final TopicKey THE_TOPIC = new TopicKey(new SeedPassage("exemplar.pdf", 2));

    /** The subtopic of it both documents lie in. */
    private static final OptionalInt THE_SUBTOPIC = OptionalInt.of(1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Each document is recorded once, in one cluster of its own topic")
    void recordsEachDocumentOnceInOneCluster() {
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        OccurrenceId first = anOccurrence("a.txt");
        OccurrenceId second = anOccurrence("b.txt");
        RunId run = aRun(first);

        clusters.record(run, first, THE_TOPIC, THE_SUBTOPIC, 0);
        clusters.record(run, second, THE_TOPIC, THE_SUBTOPIC, 0);

        claim(
                "both documents are recorded, so membership is total over the topic -- every"
                        + " survivor has to land somewhere for the page tree above it to be complete -- and"
                        + " each appears once, in one cluster: membership is disjoint by construction, since"
                        + " a document carries a single ordinal rather than appearing in a list per cluster",
                () -> assertThat(WholeRun.membership(jdbcTemplate,run))
                        .extracting(DocumentCluster::occurrenceId)
                        .containsExactlyInAnyOrder(first, second));
        claim(
                "and each row carries the topic and the part of it its cluster lies in, which with its ordinal are a"
                        + " cluster's identity",
                () -> assertThat(WholeRun.membership(jdbcTemplate,run))
                        .allSatisfy(member -> {
                            assertThat(member.topic()).isEqualTo(THE_TOPIC);
                            assertThat(member.subtopic()).isEqualTo(THE_SUBTOPIC);
                        }));
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        ledger.occurrences().fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
