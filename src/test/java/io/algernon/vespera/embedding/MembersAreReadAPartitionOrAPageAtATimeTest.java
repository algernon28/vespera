package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The cluster membership of a run read without ever being held whole (ADR-223 sections 3 and 7): how many
 * members one seed partition has, that partition's members, and every member of the run a page at a time.
 *
 * <p>Until ADR-223 stages 6a and 6b each began by reading every membership row of the scoring run, sorted by
 * the database. Each now goes through one partition at a time, and the manifest and the pictures' first pass
 * through the run a page at a time. Both keep the order the old read gave, occurrence order, because a
 * cluster's mean score, its lead document and the manifest's rows all depend on it.
 *
 * <p><b>The rows are recorded against that order</b>, the later occurrence first, so a read that answered in
 * the order rows were written is told apart; and a second run holds rows for the same occurrences and the
 * same seed, which no read of the first may return or count.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Clustering")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class MembersAreReadAPartitionOrAPageAtATimeTest {

    /** The most membership rows one page holds. */
    private static final int A_FULL_PAGE = 1_000;

    /** One more member than a page holds, so the run takes a full page and a page of one. */
    private static final int ONE_MORE_THAN_A_PAGE = A_FULL_PAGE + 1;

    /** How many of those members sit under the second seed: every fourth. */
    private static final int UNDER_THE_SECOND_SEED = 251;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DocumentClusters documentClusters;
    private OccurrenceId firstSeed;
    private OccurrenceId secondSeed;
    private RunId scoring;
    private RunId anotherScoring;

    /** The members in occurrence order, which is the order the ledger recorded them in. */
    private final List<OccurrenceId> members = new ArrayList<>();

    @BeforeEach
    void oneMoreMemberThanAPageUnderTwoSeeds() {
        documentClusters = new DocumentClusters(jdbcTemplate);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-members-" + System.nanoTime()));
        firstSeed = anOccurrence(ledger, walk, "seeds/first.docx");
        secondSeed = anOccurrence(ledger, walk, "seeds/second.docx");
        scoring = ledger.runs().startRun("embedding-scoring", "s" + System.nanoTime(), "{}", walk, List.of());
        anotherScoring = ledger.runs().startRun("embedding-scoring", "t" + System.nanoTime(), "{}", walk, List.of());
        members.clear();
        for (int member = 0; member < ONE_MORE_THAN_A_PAGE; member++) {
            members.add(anOccurrence(ledger, walk, "reports/document-" + member + ".docx"));
        }
        // Recorded last occurrence first, every fourth under the second seed, in three clusters a seed.
        for (int member = ONE_MORE_THAN_A_PAGE - 1; member >= 0; member--) {
            documentClusters.record(scoring, members.get(member), seedOf(member), member % 3);
        }
        // Another run's rows for two of the same occurrences, under the same seeds.
        documentClusters.record(anotherScoring, members.get(0), secondSeed, 0);
        documentClusters.record(anotherScoring, members.get(1), firstSeed, 0);
    }

    @Test
    @Story("One exemplar's documents are counted and read on their own")
    @DisplayName("An exemplar's documents are counted under one run, and an exemplar with none counts none")
    void countsOnePartition() {
        claim(
                "every fourth of the " + ONE_MORE_THAN_A_PAGE + " documents sits under the second exemplar, "
                        + UNDER_THE_SECOND_SEED + " of them, and the other run's row under it is not counted",
                () -> assertThat(documentClusters.sizeOf(scoring, secondSeed)).isEqualTo(UNDER_THE_SECOND_SEED));
        claim(
                "the rest sit under the first",
                () -> assertThat(documentClusters.sizeOf(scoring, firstSeed))
                        .isEqualTo(ONE_MORE_THAN_A_PAGE - UNDER_THE_SECOND_SEED));
        claim(
                "and an exemplar no document was matched to counts none, which is how a stage knows there is"
                        + " nothing under it to arrange",
                () -> assertThat(documentClusters.sizeOf(scoring, members.get(5))).isZero());
    }

    @Test
    @Story("One exemplar's documents are counted and read on their own")
    @DisplayName("An exemplar's documents come back in the order they were first recorded, each with its group, and no other exemplar's")
    void readsOnePartitionInOccurrenceOrder() {
        List<DocumentCluster> underTheSecond = documentClusters.membersOf(scoring, secondSeed);

        claim(
                "the " + UNDER_THE_SECOND_SEED + " documents under the second exemplar come back, each naming"
                        + " that exemplar and the group it was put in, and none of the first exemplar's",
                () -> assertThat(underTheSecond)
                        .hasSize(UNDER_THE_SECOND_SEED)
                        .allSatisfy(member -> {
                            assertThat(member.winningSeedOccurrenceId()).isEqualTo(secondSeed);
                            assertThat(member.clusterOrdinal())
                                    .isEqualTo(members.indexOf(member.occurrenceId()) % 3);
                        }));
        claim(
                "in the order the documents were first recorded, though their rows were written in the reverse"
                        + " of it: a group's average closeness and the document it is named after are taken in"
                        + " this order, so another order could name the group after another document",
                () -> assertThat(underTheSecond)
                        .extracting(DocumentCluster::occurrenceId)
                        .isSortedAccordingTo(Comparator.comparingLong(OccurrenceId::value))
                        .startsWith(members.get(0), members.get(4), members.get(8)));
    }

    @Test
    @Story("Every document of a run is read a page at a time")
    @DisplayName("A run's documents are handed over a thousand at a time, each once, in the order they were first recorded")
    void readsTheRunAPageAtATime() {
        List<List<DocumentCluster>> pages = new ArrayList<>();

        documentClusters.eachPage(scoring, page -> pages.add(List.copyOf(page)));

        claim(
                "the " + ONE_MORE_THAN_A_PAGE + " documents come in a page of " + A_FULL_PAGE + " and a page of"
                        + " one: never more than " + A_FULL_PAGE + " are in hand at once, and no empty page is"
                        + " handed over",
                () -> assertThat(pages).extracting(List::size).containsExactly(A_FULL_PAGE, 1));
        claim(
                "taken together the pages hold every document of the run once, in the order they were first"
                        + " recorded, across both exemplars, which is the order the listing of every document is"
                        + " written in; the other run's two rows are in neither page",
                () -> assertThat(pages.stream().flatMap(List::stream).map(DocumentCluster::occurrenceId))
                        .containsExactlyElementsOf(members));
        claim(
                "and each still names its own exemplar and group",
                () -> assertThat(pages.getFirst().get(4))
                        .isEqualTo(new DocumentCluster(members.get(4), secondSeed, 1)));
    }

    @Test
    @Story("Every document of a run is read a page at a time")
    @DisplayName("A run with no document hands over no page")
    void aRunWithNoMemberHandsOverNothing() {
        RunId empty = new Ledger(jdbcTemplate)
                .runs()
                .startRun("embedding-scoring", "u" + System.nanoTime(), "{}", theWalkOf(firstSeed), List.of());
        List<List<DocumentCluster>> pages = new ArrayList<>();

        documentClusters.eachPage(empty, pages::add);

        claim("nothing is handed over, not even an empty page", () -> assertThat(pages).isEmpty());
    }

    private OccurrenceId seedOf(int member) {
        return member % 4 == 0 ? secondSeed : firstSeed;
    }

    private static OccurrenceId anOccurrence(Ledger ledger, WalkId walk, String path) {
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }
}
