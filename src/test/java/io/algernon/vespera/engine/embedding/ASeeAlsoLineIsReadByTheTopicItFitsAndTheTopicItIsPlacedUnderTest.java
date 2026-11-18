package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import java.util.ArrayList;
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
 * {@link Placements#eachPageOfSeeAlso} over rows {@code see_also}, {@code winning_topic} and {@code cluster_member}
 * really hold (ADR-415 §5, §11): the documents that fit one topic and are placed under another, each with the cluster
 * it lies in there, in occurrence order, under one scoring run.
 *
 * <p>Three topics, on sluices, pumps and gates. Under the run read: two documents placed under pumps fit sluices, in
 * two clusters, one of them in a subtopic, recorded in the reverse of their occurrence order; one placed under gates
 * fits sluices; one placed under pumps fits gates and not sluices; one placed under pumps fits sluices and is in no
 * cluster. Under another run of the same walk, a document placed under pumps fits sluices. So a statement that swapped
 * the topic fitted and the topic placed under, read across runs, kept a document no clustering placed, or listed in
 * the order recorded, would answer something else.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Topics")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class ASeeAlsoLineIsReadByTheTopicItFitsAndTheTopicItIsPlacedUnderTest {

    private static final TopicKey SLUICES = new TopicKey(new SeedPassage("seeds/sluices.docx", 0));

    private static final TopicKey PUMPS = new TopicKey(new SeedPassage("seeds/pumps.docx", 3));

    private static final TopicKey GATES = new TopicKey(new SeedPassage("seeds/gates.docx", 1));

    /** The subtopic of pumps one of the two documents lies in, and the cluster ordinals each lies at. */
    private static final int A_SUBTOPIC = 1;

    private static final int FIRST_CLUSTER = 0;

    private static final int THIRD_CLUSTER = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A document that fits another topic is linked from that topic's page")
    @DisplayName("The documents fitting one topic and placed under another are read with their clusters, in occurrence order, under the run asked about alone, and none no clustering placed")
    void eachDocumentFittingATopicIsReadUnderTheTopicItIsPlacedUnderWithItsCluster() {
        Placements placements = new Placements(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        WalkId walk = new Ledger(jdbcTemplate).walks().startWalk(Path.of("C:/corpus"));
        OccurrenceId earlier = anOccurrence(walk, "reports/earlier.pdf");
        OccurrenceId later = anOccurrence(walk, "reports/later.pdf");
        OccurrenceId underGates = anOccurrence(walk, "reports/under gates.pdf");
        OccurrenceId fitsGatesAlone = anOccurrence(walk, "reports/fits gates.pdf");
        OccurrenceId neverClustered = anOccurrence(walk, "reports/never clustered.pdf");
        OccurrenceId ofAnotherRun = anOccurrence(walk, "reports/another run.pdf");
        RunId run = aScoringRun(walk);
        RunId anotherRun = aScoringRun(walk);

        placements.record(later, run, new Placement(PUMPS, List.of(SLUICES)));
        clusters.record(run, later, PUMPS, OptionalInt.of(A_SUBTOPIC), THIRD_CLUSTER);
        placements.record(earlier, run, new Placement(PUMPS, List.of(SLUICES, GATES)));
        clusters.record(run, earlier, PUMPS, OptionalInt.empty(), FIRST_CLUSTER);
        placements.record(underGates, run, new Placement(GATES, List.of(SLUICES)));
        clusters.record(run, underGates, GATES, OptionalInt.empty(), FIRST_CLUSTER);
        placements.record(fitsGatesAlone, run, new Placement(PUMPS, List.of(GATES)));
        clusters.record(run, fitsGatesAlone, PUMPS, OptionalInt.empty(), FIRST_CLUSTER);
        placements.record(neverClustered, run, new Placement(PUMPS, List.of(SLUICES)));
        placements.record(ofAnotherRun, anotherRun, new Placement(PUMPS, List.of(SLUICES)));
        clusters.record(anotherRun, ofAnotherRun, PUMPS, OptionalInt.empty(), FIRST_CLUSTER);

        claim("fitting sluices and placed under pumps: the two documents clustered under pumps by the run asked about, in"
                        + " occurrence order though recorded the other way, each with the subtopic and ordinal of its"
                        + " cluster there; not the one no clustering placed, nor the one of the other run",
                () -> assertThat(read(placements, run, SLUICES, PUMPS)).containsExactly(
                        new Placements.SeeAlsoRow(earlier, PUMPS, OptionalInt.empty(), FIRST_CLUSTER),
                        new Placements.SeeAlsoRow(later, PUMPS, OptionalInt.of(A_SUBTOPIC), THIRD_CLUSTER)));
        claim("fitting sluices and placed under gates: the one document placed under gates",
                () -> assertThat(read(placements, run, SLUICES, GATES)).containsExactly(
                        new Placements.SeeAlsoRow(underGates, GATES, OptionalInt.empty(), FIRST_CLUSTER)));
        claim("fitting gates and placed under pumps: the two documents that fit gates, and not those fitting sluices"
                        + " alone, so the topic fitted and the topic placed under are not read the other way round",
                () -> assertThat(read(placements, run, GATES, PUMPS)).containsExactly(
                        new Placements.SeeAlsoRow(earlier, PUMPS, OptionalInt.empty(), FIRST_CLUSTER),
                        new Placements.SeeAlsoRow(fitsGatesAlone, PUMPS, OptionalInt.empty(), FIRST_CLUSTER)));
        claim("fitting pumps and placed under sluices: nothing, no document being placed under sluices",
                () -> assertThat(read(placements, run, PUMPS, SLUICES)).isEmpty());
    }

    private static List<Placements.SeeAlsoRow> read(
            Placements placements, RunId run, TopicKey fitted, TopicKey placedUnder) {
        List<Placements.SeeAlsoRow> rows = new ArrayList<>();
        placements.eachPageOfSeeAlso(run, fitted, placedUnder, rows::addAll);
        return rows;
    }

    private OccurrenceId anOccurrence(WalkId walk, String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        ledger.occurrences().fileOccurrence(
                walk,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-11-18T10:15:30Z"),
                Instant.parse("2026-11-10T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aScoringRun(WalkId walk) {
        return new Ledger(jdbcTemplate).runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walk, List.of());
    }
}
