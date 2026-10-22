package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.Unwritten;
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
 * The rules every sentence ADR-174 fixes is held to, checked over the sentences themselves so a later edit to one
 * cannot break a rule the invocation tests would only meet on the page it lands on. The sentences are written by
 * {@code deliverable}'s {@link UnwrittenSentences} since ADR-274 §4; {@code Unwritten} stays a reason with no words.
 *
 * <p>These hold today and are meant to: they guard the wording, while {@code GenerationFaultInvocationTest} and
 * {@code GenerationBreakerInvocationTest} are where the page is held to it.
 *
 * <p><b>Three words are refused because a re-run can make them false</b> (ADR-174 §5, ADR-111): the page is written
 * again by every invocation that reaches the writing, so a sentence about this run is replaced by the writing once a
 * repair pass gets it, and a sentence saying the gap was permanent would have been false the day it was written.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("325")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class UnwrittenPageWordingTest {

    /** Words a sentence about one run may not use, since running again can make each one false. */
    private static final List<String> WORDS_A_RE_RUN_CAN_MAKE_FALSE = List.of("permanent", "never", "always");

    /** The word this project uses for what the reader of the tree is told is a group (ADR-122). */
    private static final String OUR_OWN_WORD = "cluster";

    /** How many cases the record gives a sentence: four kinds of fault, and three cases with no row. */
    private static final int SEVEN_CASES = 7;

    private static final List<String> EVERY_SENTENCE = UnwrittenSentencesOnRecord.EVERY_SENTENCE;

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("Every case has a sentence of its own, and no two cases share one")
    void givesEveryCaseASentenceOfItsOwn() {
        claim(
                "there are " + SEVEN_CASES + " sentences, one for each way a group can end up with nothing"
                        + " written over it, and no two are the same: a reader who meets one has to be able"
                        + " to tell which case it was without the database",
                () -> assertThat(EVERY_SENTENCE).hasSize(SEVEN_CASES).doesNotHaveDuplicates());
        claim(
                "and every way an answer can be turned down has one of the four that say so",
                () -> assertThat(Arrays.stream(ClusterFaultKind.values()).map(UnwrittenSentencesOnRecord::forKind))
                        .doesNotHaveDuplicates()
                        .allSatisfy(sentence -> assertThat(EVERY_SENTENCE).contains(sentence)));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("The code gives each case exactly the sentence the record gives it")
    void theCodeSaysWhatTheRecordSays() {
        claim(
                "the code's sentence for every way an answer can be turned down is the record's, word for"
                        + " word, and a new way of turning one down cannot be added without one",
                () -> assertThat(ClusterFaultKind.values()).allSatisfy(kind -> assertThat(
                                UnwrittenSentences.of(Unwritten.of(kind)))
                        .isEqualTo(UnwrittenSentencesOnRecord.forKind(kind))));
        claim(
                "and so is its sentence for each of the three cases no reason is recorded for",
                () -> {
                    assertThat(UnwrittenSentences.of(Unwritten.NO_SENDABLE_DOCUMENT))
                            .isEqualTo(UnwrittenSentencesOnRecord.NO_DOCUMENT_COULD_BE_SENT);
                    assertThat(UnwrittenSentences.of(Unwritten.NOTHING_FITS_THE_WINDOW))
                            .isEqualTo(UnwrittenSentencesOnRecord.NO_DOCUMENT_FITS_THE_ROOM);
                    assertThat(UnwrittenSentences.of(Unwritten.NOT_REACHED))
                            .isEqualTo(UnwrittenSentencesOnRecord.WRITING_STOPPED_BEFORE_IT);
                });
        claim(
                "and the code has no sentence the record does not",
                () -> assertThat(Arrays.stream(Unwritten.values()).map(UnwrittenSentences::of))
                        .containsExactlyInAnyOrderElementsOf(EVERY_SENTENCE));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("No sentence says the gap is for good, since running again can fill it")
    void saysNothingARepairCanMakeFalse() {
        claim(
                "no sentence says permanent, never or always: each describes what happened in this run,"
                        + " and the next run over the same settings writes the page again, so a gap called"
                        + " permanent would be a false page the day a repair filled it",
                () -> assertThat(EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence.toLowerCase(Locale.ROOT))
                        .doesNotContain(WORDS_A_RE_RUN_CAN_MAKE_FALSE)));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("No sentence uses a name only the code knows")
    void namesNothingOnlyTheCodeKnows() {
        claim(
                "no sentence carries the name the code gives a reason, since a reader of the tree has no"
                        + " way to look one up",
                () -> assertThat(EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .doesNotContain(Arrays.stream(ClusterFaultKind.values())
                                .map(Enum::name)
                                .toList())));
        claim(
                "and every sentence says group rather than the word the code uses, which to that reader"
                        + " means a set of things that are all alike",
                () -> assertThat(EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .contains("group")
                        .doesNotContainIgnoringCase(OUR_OWN_WORD)));
    }

    /**
     * The line ADR-319 §5.1 puts beneath the reason on the page of a cluster that stands without writing, held to the
     * rules every one of ADR-174's sentences is held to here, word for word the same rules, since it lands on the same
     * page in the same italics.
     */
    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and its page says so")
    @DisplayName("The line saying a group stands without writing keeps every rule the reasons keep")
    @Link(name = "ADR-319", url = Adr.A_CLUSTER_TURNED_DOWN_THE_LAST_TWO_TIMES_STANDS_WITHOUT_WRITING, type = "adr")
    void theStandingLineKeepsTheRulesEverySentenceKeeps() {
        String standing = UnwrittenSentencesOnRecord.STANDS_WITHOUT_WRITING;
        claim(
                "it is none of the seven reasons, so a reader meeting it beneath one is told something the reason"
                        + " did not say",
                () -> assertThat(EVERY_SENTENCE).doesNotContain(standing));
        claim(
                "it says none of permanent, never or always: a run of other settings asks the group afresh, so"
                        + " what it says is true of this run and no more",
                () -> assertThat(standing.toLowerCase(Locale.ROOT)).doesNotContain(WORDS_A_RE_RUN_CAN_MAKE_FALSE));
        claim(
                "it says group and not the word the code uses, and carries no name the code gives a reason",
                () -> assertThat(standing)
                        .contains("group")
                        .doesNotContainIgnoringCase(OUR_OWN_WORD)
                        .doesNotContain(Arrays.stream(ClusterFaultKind.values()).map(Enum::name).toList()));
        claim(
                "and it is one plain line ending in a full stop, with nothing markup could be made of",
                () -> assertThat(standing)
                        .endsWith(".")
                        .doesNotContain("\n", "*", "[", "]", "(", ")", "<", ">", "`", "|", "&", "\\", "_"));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("Every sentence is one plain line of text, which the page shows in italics")
    void isOnePlainLineOfText() {
        claim(
                "each sentence is a single line ending in a full stop, with no asterisk: the page puts it in"
                        + " italics itself, and the asterisks Markdown wanted would show as typed",
                () -> assertThat(EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .endsWith(".")
                        .doesNotContain("\n", "*")));
        claim(
                "and no sentence carries a link or anything markup could be made of, so the tree still needs no"
                        + " database and no network to read",
                () -> assertThat(EVERY_SENTENCE).allSatisfy(sentence -> assertThat(sentence)
                        .doesNotContain("[", "]", "(", ")", "<", ">", "`", "|", "&", "\\", "_")));
    }
}
