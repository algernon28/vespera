package io.algernon.vespera.engine.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that decides where a chunk ends carries an identity naming the budget it actually
 * enforced (ADR-091), so the two can never drift apart — the asymmetry #103 asked to close.
 *
 * <p>A stored chunk row is keyed under this identity, so a rule that changed its budget while
 * keeping its name would serve boundaries produced under the old one.
 */
@Epic("Extraction")
@Feature("Chunking")
@Issue("103")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
@Link(name = "ADR-044", url = Adr.THE_BAKE_OFF_RE_CHUNKS_PER_CANDIDATE_MODEL, type = "adr")
class ChunkingRuleTest {

    /** The budget every chunk is cut to unless a caller names another one — a code default. */
    private static final int DEFAULT_WORD_BUDGET = 512;

    /** A second, deliberately different budget, standing in for a rule tuned to another document kind. */
    private static final int TABULAR_WORD_BUDGET = 2048;

    @Test
    @Story("A rule's identity names the budget it enforces")
    @DisplayName("Two rules with different budgets cannot share an identity")
    void adifferentBudgetIsADifferentIdentity() {
        ChunkingRule prose = new ChunkingRule(DEFAULT_WORD_BUDGET);
        ChunkingRule tabular = new ChunkingRule(TABULAR_WORD_BUDGET);

        claim(
                "a rule budgeting " + TABULAR_WORD_BUDGET + " words identifies itself differently from one"
                        + " budgeting " + DEFAULT_WORD_BUDGET + ", so the chunk rows they produce are keyed"
                        + " apart rather than one serving the other's boundaries",
                () -> assertThat(tabular.identity()).isNotEqualTo(prose.identity()));
    }
}
