package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A path's filename stem: its last {@code /}-separated segment without its last extension. One rule,
 * read by a cluster label's second tier (ADR-106) and by the deliverable's partition and picture
 * directories (ADR-213 §4).
 *
 * <p><b>Every expected stem here is what both {@code Deliverable.stemOf} and {@code
 * ClusterLabel.filenameStemOf} gave at {@code 4b99a03}</b>; the two bodies were the same.
 */
@Epic("Synthesis")
@Feature("Naming a cluster")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-106", url = Adr.A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE, type = "adr")
class FilenameStemTest {

    /** A cluster's ordinal, for the label's third tier; any value serves. */
    private static final int ORDINAL = 3;

    @Test
    @Story("A filename stem is the last segment without its last extension")
    @DisplayName("A cluster labelled by its lead document's filename gets exactly the stem the deliverable names a directory by")
    void isTheStemAClusterLabelFallsBackTo() {
        for (String path : List.of(
                "audits/2019/site-safety-audit.pdf", "reports/2019/Site Audit.v2.pdf", "archive.tar.gz", "README")) {
            claim(
                    "the label of a cluster whose lead document '" + path + "' has no title is the stem of its"
                            + " path, by the one rule the deliverable also reads",
                    () -> assertThat(ClusterLabel.derivedFrom(null, new OccurrencePath(path), ORDINAL).value())
                            .isEqualTo(FilenameStem.of(path)));
        }
    }
}
