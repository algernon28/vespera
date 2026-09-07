package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("The identity spells out the unit and the budget, so a reader of a stored row knows both")
    void identityNamesTheUnitAndTheBudget() {
        ChunkingRule rule = new ChunkingRule(DEFAULT_WORD_BUDGET);

        claim(
                "the identity of a " + DEFAULT_WORD_BUDGET + "-word rule reads words-" + DEFAULT_WORD_BUDGET
                        + "-v1 — the unit, the budget, and the packing algorithm's own version, since a"
                        + " change to any of the three moves chunk boundaries",
                () -> assertThat(rule.identity().value()).isEqualTo("words-" + DEFAULT_WORD_BUDGET + "-v1"));
    }

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

    @Test
    @Story("A rule refuses a budget it could not enforce")
    @DisplayName("A budget of zero words is refused, rather than producing chunks no text fits in")
    void refusesABudgetNoTextCouldFit() {
        claim(
                "a rule budgeting no words at all is refused when it is built, since it could only ever"
                        + " produce empty chunks or loop forever trying to place one word",
                () -> assertThatThrownBy(() -> new ChunkingRule(0)).isInstanceOf(IllegalArgumentException.class));
    }
}
