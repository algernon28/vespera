package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules every sentence ADR-174 fixes is held to, checked over the sentences themselves so a
 * later edit to one cannot break a rule the invocation tests would only meet on the page it lands on.
 *
 * <p>These hold today and are meant to: they guard the wording, while {@link
 * GenerationFaultInvocationTest} and {@link GenerationBreakerInvocationTest} are where the page is
 * held to it.
 *
 * <p><b>Three words are refused because a re-run can make them false</b> (ADR-174 §5, ADR-111): the
 * page is written again by every invocation that reaches the writing, so a sentence about this run is
 * replaced by the writing once a repair pass gets it, and a sentence saying the gap was permanent
 * would have been false the day it was written.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("325")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
class UnwrittenPageWordingTest {

    /** Words a sentence about one run may not use, since running again can make each one false. */
    private static final List<String> WORDS_A_RE_RUN_CAN_MAKE_FALSE = List.of("permanent", "never", "always");

    /** The word this project uses for what the reader of the tree is told is a group (ADR-122). */
    private static final String OUR_OWN_WORD = "cluster";

    /** How many cases the record gives a sentence: four kinds of fault, and three cases with no row. */
    private static final int SEVEN_CASES = 7;

    /** How many asterisks a line of emphasis carries: the one that opens it and the one that closes it. */
    private static final long ONE_PAIR_OF_ASTERISKS = 2L;

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("Every case has a sentence of its own, and no two cases share one")
    void givesEveryCaseASentenceOfItsOwn() {
        claim(
                "there are " + SEVEN_CASES + " sentences, one for each way a group can end up with nothing"
                        + " written over it, and no two are the same: a reader who meets one has to be able"
                        + " to tell which case it was without the database",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).hasSize(SEVEN_CASES).doesNotHaveDuplicates());
        claim(
                "and every way an answer can be turned down has one of the four that say so",
                () -> assertThat(Arrays.stream(ClusterFaultKind.values()).map(UnwrittenPage::forKind))
                        .doesNotHaveDuplicates()
                        .allSatisfy(sentence -> assertThat(UnwrittenPage.EVERY_SENTENCE).contains(sentence)));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("No sentence says the gap is for good, since running again can fill it")
    void saysNothingARepairCanMakeFalse() {
        claim(
                "no sentence says permanent, never or always: each describes what happened in this run,"
                        + " and the next run over the same settings writes the page again, so a gap called"
                        + " permanent would be a false page the day a repair filled it",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).allSatisfy(sentence -> assertThat(
                                sentence.toLowerCase(Locale.ROOT))
                        .doesNotContain(WORDS_A_RE_RUN_CAN_MAKE_FALSE)));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("No sentence uses a name only the code knows")
    void namesNothingOnlyTheCodeKnows() {
        claim(
                "no sentence carries the name the code gives a reason, since a reader of the tree has no"
                        + " way to look one up",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .doesNotContain(Arrays.stream(ClusterFaultKind.values())
                                .map(Enum::name)
                                .toList())));
        claim(
                "and every sentence says group rather than the word the code uses, which to that reader"
                        + " means a set of things that are all alike",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .contains("group")
                        .doesNotContainIgnoringCase(OUR_OWN_WORD)));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("Every sentence is one line of emphasis, as the line it replaces was")
    void keepsTheShapeOfTheLineItReplaces() {
        claim(
                "each sentence is a single line wrapped in one pair of asterisks, the shape the line it"
                        + " replaces has, so it renders as a paragraph of emphasis and nothing inside it"
                        + " opens a second one",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .startsWith("*")
                        .endsWith(".*")
                        .doesNotContain("\n")
                        .satisfies(s -> assertThat(s.chars().filter(c -> c == '*').count())
                                .isEqualTo(ONE_PAIR_OF_ASTERISKS))));
        claim(
                "and no sentence carries a link or anything a renderer could read as one, so the tree"
                        + " still needs no database and no network to read",
                () -> assertThat(UnwrittenPage.EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .doesNotContain("[", "]", "(", ")", "<", ">", "`", "|", "&", "\\")));
    }
}
