package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A survivor as the manifest and a cluster page list it, made in {@code deliverable} from the scores on
 * record (ADR-226, moving ADR-222's rule 8, unchanged; ADR-104; ADR-274 §4), and the key of the cluster it is in.
 *
 * <p>A survivor with no score on record is listed with {@code 0.0}, as {@code GenerationTasklet} listed it.
 * {@code LeadDocument} weighs a member with no score as {@code 0.0} as well, for which member leads a
 * cluster: the two are separate outputs, and this one is the manifest's and the page order's.
 */
@Epic("The deliverable")
@Feature("The manifest")
@Issue("479")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class ListedSurvivorTest {

    @Test
    @Story("One key names a cluster")
    @DisplayName("A survivor gives the key of the cluster it is in, which is the cluster's own")
    void aSurvivorGivesItsClustersKey() {
        ListedSurvivor member = new ListedSurvivor(SURVIVOR, PATH, CONTENT_KEY, WINNING_SEED, SEED_PATH, CLUSTER_ORDINAL, 0.5);
        RecordedCluster itsCluster = new RecordedCluster(
                new ArrangedCluster(WINNING_SEED, CLUSTER_ORDINAL, 2, 1, 1), new ClusterLabel("Settlements"));

        claim(
                "the survivor and its arranged cluster give one key, the seed and the ordinal, so a survivor is"
                        + " lined up with its cluster by the key alone",
                () -> assertThat(member.slot())
                        .isEqualTo(ClusterSlot.of(itsCluster))
                        .isEqualTo(new ClusterSlot(WINNING_SEED, CLUSTER_ORDINAL)));
        claim(
                "and a survivor of another ordinal under the same seed gives another key",
                () -> assertThat(new ListedSurvivor(
                                        SURVIVOR, PATH, CONTENT_KEY, WINNING_SEED, SEED_PATH, CLUSTER_ORDINAL + 1, 0.5)
                                .slot())
                        .isNotEqualTo(member.slot()));
    }

    private static final OccurrenceId SURVIVOR = new OccurrenceId(7);

    private static final OccurrencePath PATH = new OccurrencePath("guides/settlement.pdf");

    private static final String CONTENT_KEY = "0".repeat(63) + "7";

    private static final OccurrenceId WINNING_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "C:/seeds/settlement-overview.pdf";

    private static final int CLUSTER_ORDINAL = 2;

    @Test
    @Story("Every survivor is listed with the score on record for it")
    @DisplayName("A survivor with a score on record is listed with that score and everything else as handed")
    void aScoreOnRecordIsListed() {
        ListedSurvivor listed = ListedSurvivor.of(
                SURVIVOR, PATH, CONTENT_KEY, WINNING_SEED, SEED_PATH, CLUSTER_ORDINAL, Map.of(SURVIVOR, 0.73));

        claim(
                "the survivor is the one the record's own constructor makes from the same values and the score"
                        + " on record for it",
                () -> assertThat(listed)
                        .isEqualTo(new ListedSurvivor(
                                SURVIVOR, PATH, CONTENT_KEY, WINNING_SEED, SEED_PATH, CLUSTER_ORDINAL, 0.73)));
    }

    @Test
    @Story("Every survivor is listed with the score on record for it")
    @DisplayName("A survivor with no score on record is listed with 0.0, as it always was")
    void noScoreOnRecordIsListedAsZero() {
        ListedSurvivor listed = ListedSurvivor.of(
                SURVIVOR,
                PATH,
                CONTENT_KEY,
                WINNING_SEED,
                SEED_PATH,
                CLUSTER_ORDINAL,
                Map.of(new OccurrenceId(8), 0.73));

        claim(
                "a survivor whose score is not on record is still listed, every survivor being listed,"
                        + " and its score is 0.0, the value the manifest has always shown for it",
                () -> assertThat(listed.score()).isEqualTo(0.0));
        claim(
                "and nothing else about it changes",
                () -> assertThat(listed)
                        .isEqualTo(new ListedSurvivor(
                                SURVIVOR, PATH, CONTENT_KEY, WINNING_SEED, SEED_PATH, CLUSTER_ORDINAL, 0.0)));
    }
}
