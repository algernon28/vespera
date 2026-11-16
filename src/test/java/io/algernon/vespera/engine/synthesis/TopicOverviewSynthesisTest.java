package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * How a topic's overview is asked for (ADR-402 §1, §3, §4): an answer turned down by the citation check is asked for
 * once more, by the second question, and never a third time; an answer turned down by another check is not asked for
 * again; every answer is asked at temperature 0 with seed 1 under a schema of one field; and a topic with no written
 * cluster asks nothing.
 *
 * <p>The model is scripted: it answers each answering call with the next writing it was given, and every counting call
 * (one token of answer, ADR-166) with an answer nobody reads. Answering calls are told apart by the tokens they allow.
 */
@Epic("Synthesis")
@Feature("Writing over a topic")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class TopicOverviewSynthesisTest {

    /** A name of the test's own for the writing model. */
    private static final String MODEL_NAME = "a-writing-model:8b";

    private static final String TOPIC_NAME = "Irrigation and water rights";

    /** Clusters 1 and 3 of a topic of three, the second unwritten. */
    private static final OverviewCall GROUPS_ONE_AND_THREE = new OverviewCall(
            TOPIC_NAME,
            List.of(
                    new OverviewSource(1, "Filters and their schedule", "The consortium cleans its filters each quarter."),
                    new OverviewSource(3, "What repairs cost", "Repairs cost more than inspections.")));

    /** Writing whose every sentence cites a cluster sent. */
    private static final String CITED = "The groups share a schedule [1]. Repairs cost more [3].";

    /** Writing whose second sentence cites nothing. */
    private static final String A_SENTENCE_UNCITED = "The groups share a schedule [1]. Repairs cost more.";

    /** What the check makes of {@link #A_SENTENCE_UNCITED} (ADR-402 §2, §7). */
    private static final ClusterFault THE_SECOND_SENTENCE_UNCITED =
            new ClusterFault(ClusterFaultKind.CITATION_NOT_IN_RANGE, "sentence 2 of 2 cites no group");

    /** An answer that is no JSON object, which the imposed shape turns down. */
    private static final String NOT_AN_OBJECT = "The groups share a schedule [1].";

    /** The two numbers {@link #GROUPS_ONE_AND_THREE} sends. */
    private static final List<Integer> ONE_AND_THREE = List.of(1, 3);

    private static final int ASKED_ONCE = 1;

    private static final int ASKED_TWICE = 2;

    /** What the record sends as the overview's temperature and seed (ADR-402 §3). */
    private static final double TEMPERATURE_ZERO = 0.0;

    private static final int SEED_ONE = 1;

    @Test
    @Story("A topic's overview turned down for its citations is asked once more")
    @DisplayName("Writing turned down for a sentence citing nothing is asked for once more, and the second answer kept")
    void aCitationFaultIsAskedOnceMoreAndTheSecondAnswerBelieved() {
        ScriptedChatModel model = new ScriptedChatModel(object(A_SENTENCE_UNCITED), object(CITED));

        TopicOverviewOutcome outcome = overviewFrom(model);

        claim(
                "the second answer is kept as the overview, over groups 1 and 3, asked for twice",
                () -> assertThat(outcome)
                        .isEqualTo(new TopicOverviewOutcome.Written(CITED, ONE_AND_THREE, ASKED_TWICE)));
        claim(
                "the second answer was asked for by the second question, which is the first one's question asked"
                        + " again with the fixed paragraph, and the first by the first question",
                () -> assertThat(model.answeringQuestions())
                        .containsExactly(
                                OverviewQuestion.first(TOPIC_NAME, GROUPS_ONE_AND_THREE.written(), DeliverableLanguage.ENGLISH),
                                OverviewQuestion.second(TOPIC_NAME, GROUPS_ONE_AND_THREE.written(), DeliverableLanguage.ENGLISH)));
    }

    @Test
    @Story("A topic's overview turned down for its citations is asked once more")
    @DisplayName("Writing turned down twice for its citations is a fault of the second answer, and no third is asked for")
    void turnedDownTwiceIsTheSecondAnswersFaultAfterTwoAnswers() {
        ScriptedChatModel model =
                new ScriptedChatModel(object(A_SENTENCE_UNCITED), object(A_SENTENCE_UNCITED), object(CITED));

        TopicOverviewOutcome outcome = overviewFrom(model);

        claim(
                "the overview is turned down with the second answer's fault, after two answers",
                () -> assertThat(outcome)
                        .isEqualTo(new TopicOverviewOutcome.TurnedDown(THE_SECOND_SENTENCE_UNCITED, ASKED_TWICE)));
        claim(
                "two answers were asked for and no third, though the model had a cited one ready",
                () -> assertThat(model.answeringQuestions()).hasSize(ASKED_TWICE));
    }

    @Test
    @Story("A topic's overview turned down for its citations is asked once more")
    @DisplayName("An answer not in the shape asked for is not asked for again")
    void anAnswerOutOfShapeIsNotAskedAgain() {
        ScriptedChatModel model = new ScriptedChatModel(NOT_AN_OBJECT, object(CITED));

        TopicOverviewOutcome outcome = overviewFrom(model);

        claim(
                "the overview is turned down as not in the shape asked for, after one answer",
                () -> assertThat(outcome).isInstanceOfSatisfying(TopicOverviewOutcome.TurnedDown.class, turnedDown -> {
                    assertThat(turnedDown.fault().kind()).isEqualTo(ClusterFaultKind.SCHEMA_VIOLATION);
                    assertThat(turnedDown.asked()).isEqualTo(ASKED_ONCE);
                }));
        claim(
                "one answer was asked for", () -> assertThat(model.answeringQuestions()).hasSize(ASKED_ONCE));
    }

    @Test
    @Story("A topic's overview turned down for its citations is asked once more")
    @DisplayName("A first answer that passes is kept, asked for once")
    void aCitedFirstAnswerIsAskedOnce() {
        ScriptedChatModel model = new ScriptedChatModel(object(CITED), object(CITED));

        claim(
                "the first answer is the overview, over groups 1 and 3, asked for once",
                () -> assertThat(overviewFrom(model))
                        .isEqualTo(new TopicOverviewOutcome.Written(CITED, ONE_AND_THREE, ASKED_ONCE)));
    }

    @Test
    @Story("A topic's overview is asked the same way every run")
    @DisplayName("Every answer is asked for at temperature 0, with seed 1, under a shape of one field")
    void everyAnswerIsAskedAtTemperatureZeroWithTheSeedAndOneField() {
        ScriptedChatModel model = new ScriptedChatModel(object(A_SENTENCE_UNCITED), object(CITED));

        overviewFrom(model);

        claim(
                "the code's temperature and seed are 0 and 1",
                () -> {
                    assertThat(TopicOverviewSynthesis.TEMPERATURE).isEqualTo(TEMPERATURE_ZERO);
                    assertThat(TopicOverviewSynthesis.SEED).isEqualTo(SEED_ONE);
                });
        claim(
                "both answering calls send temperature 0 and seed 1, and a shape requiring the one field prose",
                () -> assertThat(model.answeringOptions()).hasSize(ASKED_TWICE).allSatisfy(options -> {
                    assertThat(options.getTemperature()).isEqualTo(TEMPERATURE_ZERO);
                    assertThat(options.getSeed()).isEqualTo(SEED_ONE);
                    JsonNode schema = JsonMapper.builder().build().readTree(options.getOutputSchema());
                    assertThat(schema.get("required").valueStream().map(JsonNode::asString).toList())
                            .containsExactly("prose");
                    assertThat(schema.get("properties").propertyNames()).containsExactly("prose");
                }));
    }

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("A topic none of whose groups was written asks the model nothing")
    void aTopicWithNoWrittenGroupAsksNothing() {
        ScriptedChatModel model = new ScriptedChatModel(object(CITED));

        TopicOverviewOutcome outcome = new TopicOverviewSynthesis(model)
                .overviewFor(new OverviewCall(TOPIC_NAME, List.of()), MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW,
                        DeliverableLanguage.ENGLISH);

        claim(
                "there is nothing to send, and no call of any kind was made",
                () -> {
                    assertThat(outcome).isEqualTo(new TopicOverviewOutcome.NothingToSend());
                    assertThat(model.calls()).isZero();
                });
    }

    private static TopicOverviewOutcome overviewFrom(ChatModel model) {
        return new TopicOverviewSynthesis(model)
                .overviewFor(GROUPS_ONE_AND_THREE, MODEL_NAME, ClusterSynthesis.CONTEXT_WINDOW, DeliverableLanguage.ENGLISH);
    }

    /** {@code writing} as the one-field object the call imposes. */
    private static String object(String writing) {
        return "{\"prose\":\"" + writing + "\"}";
    }

    /**
     * A model that answers each answering call with the next of its answers, and each counting call, which allows one
     * token, with an answer nobody reads; it keeps what each answering call asked and the options it sent.
     */
    private static final class ScriptedChatModel implements ChatModel {

        private static final int A_COUNTING_CALLS_ALLOWANCE = 1;

        private final Deque<String> answers;

        private final List<String> answeringQuestions = new ArrayList<>();

        private final List<OllamaChatOptions> answeringOptions = new ArrayList<>();

        private int calls;

        ScriptedChatModel(String... answers) {
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            if (options.getNumPredict() != null && options.getNumPredict() == A_COUNTING_CALLS_ALLOWANCE) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{"))));
            }
            answeringQuestions.add(prompt.getContents());
            answeringOptions.add(options);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answers.removeFirst()))));
        }

        List<String> answeringQuestions() {
            return answeringQuestions;
        }

        List<OllamaChatOptions> answeringOptions() {
            return answeringOptions;
        }

        int calls() {
            return calls;
        }
    }
}
