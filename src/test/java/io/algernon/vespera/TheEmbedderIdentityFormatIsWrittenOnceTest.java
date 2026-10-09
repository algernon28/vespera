package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The embedder identity's model part, and the escaping that lets a model's name be matched against it
 * literally, are each written in one class of the embedding module (ADR-216 section 4, applying its
 * ride-along rule).
 *
 * <p>A stored vector's embedder identity begins {@code model=<name>;} (ADR-084, ADR-091). Two readers match
 * it by the model's name alone, with an SQL {@code LIKE} pattern built from that prefix and the name with
 * its {@code \}, {@code %} and {@code _} escaped. On {@code 3fc6f5d} the prefix was written in three
 * classes and the escaping in two, so the identity's format and the pattern that matches it could move
 * apart. The embedding module's run ids move with this change in any case, which is the condition under
 * which ADR-216 lets a copy go.
 *
 * <p>Read off the compiled classes' string texts, as {@link DeliverableRulesHaveOneHomeTest} reads them: a
 * text built at run time from fixed parts is held as those parts, so {@code "model=" + name + ";%"} is held
 * as a text beginning {@code model=}. What the readers match is held by {@code AModelNameMatchesOnlyItselfTest},
 * which passes before and after the change.
 *
 * <p>Red against {@code 3fc6f5d}, by design: three classes hold the prefix and two hold the escaping.
 */
@Epic("Architecture")
@Feature("Where the embedder identity's format lives")
@Issue("352")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class TheEmbedderIdentityFormatIsWrittenOnceTest {

    /** The module the identity and both of its readers belong to. */
    private static final String EMBEDDING = "embedding";

    /** The one class that composes an embedder identity, and so the one that may write its format. */
    private static final String THE_IDENTITY = "io.algernon.vespera.embedding.EmbedderIdentity";

    /** How every embedder identity begins: the name of the model that produced the vector. */
    private static final String THE_MODEL_PART = "model=";

    /**
     * What escaping a {@code %} in a model's name writes, so that the name matches literally under {@code
     * ESCAPE '\'}. Of the three characters escaped it is the one no other text in the module holds.
     */
    private static final String AN_ESCAPED_PERCENT = "\\%";

    @Test
    @Story("The embedder identity's format is written once")
    @DisplayName("Only the identity itself writes the model part every stored identity begins with")
    void onlyTheIdentityWritesItsModelPart() throws Exception {
        Set<String> writing = embeddingClassesHolding(text -> text.startsWith(THE_MODEL_PART));

        claim(
                "exactly one class of the module holds a text beginning with the model part, the identity that"
                        + " composes it, so the pattern a reader matches by is built where the format is written",
                () -> assertThat(writing).containsExactly(THE_IDENTITY));
    }

    @Test
    @Story("The embedder identity's format is written once")
    @DisplayName("A model's name is escaped for literal matching in one place")
    void aModelNameIsEscapedInOnePlace() throws Exception {
        Set<String> escaping = embeddingClassesHolding(AN_ESCAPED_PERCENT::equals);

        claim(
                "exactly one class of the module escapes a model's name for a pattern match, the identity, so"
                        + " both readers escape it the same way",
                () -> assertThat(escaping).containsExactly(THE_IDENTITY));
    }

    /** The shipped classes of the embedding module holding at least one string text {@code held} accepts. */
    private static Set<String> embeddingClassesHolding(Predicate<String> held) throws Exception {
        Map<String, List<String>> strings = ShippedClasses.stringsByClass();
        Set<String> holding = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : strings.entrySet()) {
            if (EMBEDDING.equals(ShippedClasses.moduleOf(shipped.getKey()))
                    && shipped.getValue().stream().anyMatch(held)) {
                holding.add(shipped.getKey());
            }
        }
        return holding;
    }
}
