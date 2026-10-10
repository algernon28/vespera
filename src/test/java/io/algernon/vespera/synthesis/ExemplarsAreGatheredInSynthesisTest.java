package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A cluster's exemplars are gathered in {@code synthesis}, from a score and an opening chunk {@code
 * pipeline} looks up for each member (ADR-226, moving ADR-222's rule 4 behind a callback, as ADR-190 §3
 * did for a lead's title).
 *
 * <p>The rule is two decisions and an order: a member nothing was chunked from is left out of the call,
 * and a member with no score stops the step, because the order the call carries its exemplars in is the
 * score (ADR-133, ADR-190). The order of what is asked and told for each member is the order {@code
 * GenerationTasklet} kept it in: the score, the opening chunk, the line for a member nothing was chunked
 * from, then the counter of members opened.
 *
 * <p>Written before {@code ClusterExemplars.gathered}, {@link OpeningChunk}, {@link OpeningText} and the two
 * new methods of {@link GenerationProgress} exist, so this class does not compile until the build does.
 */
@Epic("Synthesis")
@Feature("What a call is written from")
@Issue("479")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
class ExemplarsAreGatheredInSynthesisTest {

    private static final OccurrenceId FIRST = new OccurrenceId(1);

    private static final OccurrenceId NOTHING_CHUNKED = new OccurrenceId(2);

    private static final OccurrenceId THIRD = new OccurrenceId(3);

    private static final OpeningText FIRST_OPENING = new OpeningText("the first member's opening", 4);

    private static final OpeningText THIRD_OPENING = new OpeningText("the third member's opening", 4);

    @Test
    @Story("A member nothing was chunked from is left out of the call, and the others are sent")
    @DisplayName("A cluster's exemplars are its members that have an opening chunk, each with its score, in member order")
    void theMembersWithAnOpeningAreGatheredInOrder() {
        List<String> said = new ArrayList<>();

        List<Exemplar> exemplars = ClusterExemplars.gathered(
                List.of(FIRST, NOTHING_CHUNKED, THIRD),
                scoresSaidInto(said, Map.of(FIRST, 0.9, NOTHING_CHUNKED, 0.7, THIRD, 0.4)),
                openingsSaidInto(said, Map.of(FIRST, FIRST_OPENING, THIRD, THIRD_OPENING)),
                progressSaidInto(said));

        claim(
                "the exemplars are the first and third members, each carrying its opening chunk, that chunk's"
                        + " word count and its score, in the order the members came: the member nothing was"
                        + " chunked from is left out rather than sent empty",
                () -> assertThat(exemplars).containsExactly(
                        new Exemplar(FIRST, FIRST_OPENING.text(), FIRST_OPENING.wordCount(), 0.9),
                        new Exemplar(THIRD, THIRD_OPENING.text(), THIRD_OPENING.wordCount(), 0.4)));
        claim(
                "and for each member the score is asked first, then the opening chunk, then the line for a"
                        + " member nothing was chunked from where there is none, then the counter of members"
                        + " opened, which is the order the step wrote them in before the rule moved",
                () -> assertThat(said).containsExactly(
                        "score of 1",
                        "opening chunk of 1",
                        "opened",
                        "score of 2",
                        "opening chunk of 2",
                        "nothing chunked from 2",
                        "opened",
                        "score of 3",
                        "opening chunk of 3",
                        "opened"));
    }

    @Test
    @Story("A member with no score stops the step")
    @DisplayName("A member with no score stops the gathering before its opening chunk is asked for, in the same words")
    void aMemberWithNoScoreStops() {
        List<String> said = new ArrayList<>();

        claim(
                "a member with no score cannot be placed among the others, since the order they are sent in is"
                        + " the score, so the gathering stops with the sentence the step stopped with",
                () -> assertThatThrownBy(() -> ClusterExemplars.gathered(
                                List.of(FIRST, NOTHING_CHUNKED, THIRD),
                                scoresSaidInto(said, Map.of(FIRST, 0.9, THIRD, 0.4)),
                                openingsSaidInto(said, Map.of(FIRST, FIRST_OPENING, THIRD, THIRD_OPENING)),
                                progressSaidInto(said)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("occurrence 2 is in a cluster being written over but carries no relevance"
                                + " score, so the documents of that cluster cannot be put in order"));
        claim(
                "and it stops at that member's score: its opening chunk is not asked for, it is not counted as"
                        + " opened, and no member after it is asked about",
                () -> assertThat(said).containsExactly("score of 1", "opening chunk of 1", "opened", "score of 2"));
    }

    @Test
    @Story("A member nothing was chunked from is left out of the call, and the others are sent")
    @DisplayName("Whoever reports the walk need not hear about each member, since both new lines are optional")
    void bothNewLinesAreOptional() {
        GenerationProgress tellingOnlyTheClusterLines = new GenerationProgress() {
            @Override
            public void noSendableDocument(RecordedCluster cluster) {}

            @Override
            public void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow) {}

            @Override
            public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {}

            @Override
            public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {}
        };

        claim(
                "a progress that implements only the four lines about a cluster gathers as well, the line for a"
                        + " member nothing was chunked from and the count of members opened each doing nothing"
                        + " unless overridden",
                () -> assertThatCode(() -> ClusterExemplars.gathered(
                                List.of(FIRST, NOTHING_CHUNKED),
                                occurrence -> Optional.of(0.5),
                                occurrence -> occurrence.equals(FIRST) ? Optional.of(FIRST_OPENING) : Optional.empty(),
                                tellingOnlyTheClusterLines))
                        .doesNotThrowAnyException());
    }

    /** Each member's score from {@code scores}, saying {@code score of <id>} as it is asked. */
    private static Function<OccurrenceId, Optional<Double>> scoresSaidInto(
            List<String> said, Map<OccurrenceId, Double> scores) {
        return occurrence -> {
            said.add("score of " + occurrence.value());
            return Optional.ofNullable(scores.get(occurrence));
        };
    }

    /** Each member's opening chunk from {@code openings}, saying {@code opening chunk of <id>} as it is asked. */
    private static OpeningChunk openingsSaidInto(List<String> said, Map<OccurrenceId, OpeningText> openings) {
        return occurrence -> {
            said.add("opening chunk of " + occurrence.value());
            return Optional.ofNullable(openings.get(occurrence));
        };
    }

    /** A progress that says the two lines about a member, and nothing about a cluster. */
    private static GenerationProgress progressSaidInto(List<String> said) {
        return new GenerationProgress() {
            @Override
            public void nothingChunkedFrom(OccurrenceId occurrence) {
                said.add("nothing chunked from " + occurrence.value());
            }

            @Override
            public void occurrenceOpened() {
                said.add("opened");
            }

            @Override
            public void noSendableDocument(RecordedCluster cluster) {}

            @Override
            public void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow) {}

            @Override
            public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {}

            @Override
            public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {}
        };
    }
}
