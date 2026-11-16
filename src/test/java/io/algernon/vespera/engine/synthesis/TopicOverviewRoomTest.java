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
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * What a topic's overview sends where its written clusters do not all fit the room (ADR-402 §1, §5, §7): a cluster
 * whose writing is too large for the room is passed over and the fill carries on, as a cluster's call passes over a
 * document too large (ADR-108, ADR-166); a run that fits is found by halving, and both questions send it; and the
 * topic is turned down only where every written cluster was passed over, its detail carrying what the engine said of
 * the last question counted, or that none was counted.
 *
 * <p>The serving engine is a double that counts a question by which clusters' writing it holds: each writing carries a
 * word of its own, and the count is {@link #THE_REST_OF_THE_QUESTION} plus the tokens given to each writing found in
 * it. Counting calls are told apart by the one token of answer they allow (ADR-166). The room is the shipped window
 * less the answer's allowance, written out rather than read off the code under test.
 */
@Epic("Synthesis")
@Feature("Writing over a topic")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
@Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
class TopicOverviewRoomTest {

    private static final String MODEL_NAME = "a-writing-model:8b";

    private static final String TOPIC_NAME = "Irrigation and water rights";

    /** The shipped window, written out. */
    private static final int THE_WINDOW = 8192;

    /** The tokens kept for the answer, written out. */
    private static final int THE_ANSWER_ALLOWANCE = 1024;

    /** The longest question that leaves the answer its whole allowance: the window less the allowance, 7,168. */
    private static final int THE_ROOM = THE_WINDOW - THE_ANSWER_ALLOWANCE;

    /** What the double counts a question at before any cluster's writing in it. */
    private static final int THE_REST_OF_THE_QUESTION = 300;

    /** A cluster's writing that fits beside the others: 200 tokens. */
    private static final int SMALL = 200;

    /** A cluster's writing of 3,000 tokens: any two fit the room together, and three do not. */
    private static final int A_THIRD_OF_TOO_MUCH = 3_000;

    /** A cluster's writing of 7,000 tokens: with the rest of the question, past the room on its own. */
    private static final int TOO_LARGE = 7_000;

    /** A cluster's writing of 7,500 tokens, past the room on its own and counted otherwise than {@link #TOO_LARGE}. */
    private static final int TOO_LARGE_AND_THEN_SOME = 7_500;

    /**
     * The words of writing the word estimate finds room for in the shipped window: the window less the answer's 1,024
     * and the instructions' 256, at two tokens a word (ADR-108).
     */
    private static final int WORDS_THE_ESTIMATE_HAS_ROOM_FOR = 3_456;

    /** The detail's opening, for a topic none of whose written clusters fits (ADR-402 §7). */
    private static final String NONE_FITS = "no cluster's writing fits the window by the serving engine's own count --"
            + " the last question counted: ";

    /** What the detail says where no question was counted at all (ADR-402 §7). */
    private static final String NEVER_COUNTED = "no cluster's writing was ever counted";

    private static final int ASKED_NONE = 0;

    private static final int ASKED_ONCE = 1;

    private static final int ASKED_TWICE = 2;

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("A group whose writing the engine counts past the room on its own is passed over, and the groups after"
            + " it are sent")
    void aClusterTheEngineCountsPastTheRoomIsPassedOverAndTheRestSent() {
        OverviewSource first = source(1, "sluices", 1);
        OverviewSource second = source(2, "pumps", 1);
        OverviewSource third = source(3, "levies", 1);
        CountingEngine engine = new CountingEngine(
                Map.of(first, TOO_LARGE, second, SMALL, third, SMALL), answer("Pumps break [2]. Levies hold [3]."));

        TopicOverviewOutcome outcome = overviewOf(engine, first, second, third);

        claim(
                "the overview is written over groups 2 and 3, asked for once: group 1's writing and the rest of the"
                        + " question come to " + (THE_REST_OF_THE_QUESTION + TOO_LARGE) + " tokens, past the room of "
                        + THE_ROOM + ", so it is passed over and the fill carries on",
                () -> assertThat(outcome).isEqualTo(new TopicOverviewOutcome.Written(
                        "Pumps break [2]. Levies hold [3].", List.of(2, 3), ASKED_ONCE)));
        claim(
                "the one question asked sends groups 2 and 3 and not group 1",
                () -> assertThat(engine.answered())
                        .containsExactly(OverviewQuestion.first(TOPIC_NAME, List.of(second, third), DeliverableLanguage.ENGLISH)));
    }

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("A group whose writing is longer than the room by the word estimate is passed over before anything is"
            + " counted, and the groups after it are sent")
    void aClusterLongerThanTheRoomByTheEstimateIsPassedOverBeforeAnyCount() {
        OverviewSource first = source(1, "sluices", WORDS_THE_ESTIMATE_HAS_ROOM_FOR);
        OverviewSource second = source(2, "pumps", 1);
        OverviewSource third = source(3, "levies", 1);
        CountingEngine engine = new CountingEngine(
                Map.of(first, SMALL, second, SMALL, third, SMALL), answer("Pumps break [2]. Levies hold [3]."));

        TopicOverviewOutcome outcome = overviewOf(engine, first, second, third);

        claim(
                "the overview is written over groups 2 and 3: group 1's title and writing are more than the "
                        + WORDS_THE_ESTIMATE_HAS_ROOM_FOR + " words the estimate has room for",
                () -> assertThat(outcome).isEqualTo(new TopicOverviewOutcome.Written(
                        "Pumps break [2]. Levies hold [3].", List.of(2, 3), ASKED_ONCE)));
        claim(
                "no question counted or asked held group 1's writing",
                () -> assertThat(engine.everyQuestion()).isNotEmpty().noneMatch(question -> question.contains("sluices")));
    }

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("A topic none of whose groups' writing fits the room is turned down without an answer asked for, saying"
            + " what the engine counted last")
    void noClusterFittingTheRoomTurnsTheTopicDownWithTheLastCount() {
        OverviewSource first = source(1, "sluices", 1);
        OverviewSource second = source(2, "pumps", 1);
        CountingEngine engine = new CountingEngine(
                Map.of(first, TOO_LARGE, second, TOO_LARGE_AND_THEN_SOME), answer("Pumps break [2]."));

        TopicOverviewOutcome outcome = overviewOf(engine, first, second);

        int lastCounted = THE_REST_OF_THE_QUESTION + TOO_LARGE_AND_THEN_SOME;
        claim(
                "the topic is turned down as a question past the room, with no answer asked for, and its detail gives"
                        + " the count of the last question counted, group 2's alone, " + lastCounted + " tokens",
                () -> assertThat(outcome).isEqualTo(new TopicOverviewOutcome.TurnedDown(
                        new ClusterFault(ClusterFaultKind.PROMPT_EVALUATION_CEILING, NONE_FITS + lastCounted),
                        ASKED_NONE)));
        claim(
                "each group was counted alone before it was passed over, and no answering call was made",
                () -> {
                    assertThat(engine.counted())
                            .contains(
                                    OverviewQuestion.second(TOPIC_NAME, List.of(first), DeliverableLanguage.ENGLISH),
                                    OverviewQuestion.second(TOPIC_NAME, List.of(second), DeliverableLanguage.ENGLISH));
                    assertThat(engine.answered()).isEmpty();
                });
    }

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("A topic every group of which is longer than the room by the word estimate is turned down without a"
            + " call, saying nothing was counted")
    void noClusterCountedTurnsTheTopicDownSayingSo() {
        OverviewSource first = source(1, "sluices", WORDS_THE_ESTIMATE_HAS_ROOM_FOR);
        OverviewSource second = source(2, "pumps", WORDS_THE_ESTIMATE_HAS_ROOM_FOR);
        CountingEngine engine = new CountingEngine(Map.of(first, SMALL, second, SMALL), answer("Pumps break [2]."));

        TopicOverviewOutcome outcome = overviewOf(engine, first, second);

        claim(
                "the topic is turned down as a question past the room, with no answer asked for, its detail saying no"
                        + " group's writing was ever counted",
                () -> assertThat(outcome).isEqualTo(new TopicOverviewOutcome.TurnedDown(
                        new ClusterFault(ClusterFaultKind.PROMPT_EVALUATION_CEILING, NONE_FITS + NEVER_COUNTED),
                        ASKED_NONE)));
        claim("no call of any kind was made", () -> assertThat(engine.everyQuestion()).isEmpty());
    }

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("Where the groups fit only in part, the leading run the engine counts inside the room is sent by both"
            + " questions, and the overview names the groups it was written over")
    void aRunThatFitsInPartIsSentByBothQuestions() {
        OverviewSource first = source(1, "sluices", 1);
        OverviewSource second = source(2, "pumps", 1);
        OverviewSource third = source(3, "levies", 1);
        CountingEngine engine = new CountingEngine(
                Map.of(first, A_THIRD_OF_TOO_MUCH, second, A_THIRD_OF_TOO_MUCH, third, A_THIRD_OF_TOO_MUCH),
                answer("Sluices and pumps share a schedule [1]. Pumps break."),
                answer("Sluices and pumps share a schedule [1]. Pumps break [2]."));

        TopicOverviewOutcome outcome = overviewOf(engine, first, second, third);

        claim(
                "the second answer is the overview, over groups 1 and 2 of the topic's 3, asked for twice: the three"
                        + " together come to " + (THE_REST_OF_THE_QUESTION + 3 * A_THIRD_OF_TOO_MUCH) + " tokens, past"
                        + " the room of " + THE_ROOM + ", and the first two to "
                        + (THE_REST_OF_THE_QUESTION + 2 * A_THIRD_OF_TOO_MUCH) + ", inside it; so the page says it"
                        + " was written from 2 of the 3 groups",
                () -> assertThat(outcome).isEqualTo(new TopicOverviewOutcome.Written(
                        "Sluices and pumps share a schedule [1]. Pumps break [2].", List.of(1, 2), ASKED_TWICE)));
        claim(
                "the first question and the second both send groups 1 and 2",
                () -> assertThat(engine.answered())
                        .containsExactly(
                                OverviewQuestion.first(TOPIC_NAME, List.of(first, second), DeliverableLanguage.ENGLISH),
                                OverviewQuestion.second(TOPIC_NAME, List.of(first, second), DeliverableLanguage.ENGLISH)));
        claim(
                "what was counted first is the second question, the longer, over all three groups",
                () -> assertThat(engine.counted())
                        .first()
                        .isEqualTo(OverviewQuestion.second(
                                TOPIC_NAME, List.of(first, second, third), DeliverableLanguage.ENGLISH)));
    }

    private static TopicOverviewOutcome overviewOf(ChatModel engine, OverviewSource... written) {
        return new TopicOverviewSynthesis(engine)
                .overviewFor(new OverviewCall(TOPIC_NAME, List.of(written)), MODEL_NAME, THE_WINDOW, DeliverableLanguage.ENGLISH);
    }

    /**
     * Cluster {@code number}, titled by one word, whose writing is {@code marker} and then {@code words - 1} words more:
     * {@code words} words in all. The marker is how the double knows the writing is in a question.
     */
    private static OverviewSource source(int number, String marker, int words) {
        List<String> writing = new ArrayList<>();
        writing.add(marker);
        writing.addAll(Collections.nCopies(words - 1, "water"));
        return new OverviewSource(number, "Works", String.join(" ", writing) + ".");
    }

    /** {@code writing} as the one-field object the call imposes. */
    private static String answer(String writing) {
        return "{\"prose\":\"" + writing + "\"}";
    }

    /**
     * A serving engine that counts a question at {@link #THE_REST_OF_THE_QUESTION} plus the tokens given to each
     * cluster whose marker the question holds, and answers each answering call with the next of its answers, finished of
     * its own accord and reporting that count. It keeps every question, counted and answered, in order.
     */
    private static final class CountingEngine implements ChatModel {

        private static final int ONE_TOKEN = 1;

        private final Map<String, Integer> tokensByMarker = new LinkedHashMap<>();

        private final Deque<String> answers;

        private final List<String> counted = new ArrayList<>();

        private final List<String> answered = new ArrayList<>();

        private final List<String> everyQuestion = new ArrayList<>();

        CountingEngine(Map<OverviewSource, Integer> tokens, String... answers) {
            tokens.forEach((source, count) -> tokensByMarker.put(source.writing().split(" ")[0].replace(".", ""), count));
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String question = prompt.getContents();
            everyQuestion.add(question);
            int count = THE_REST_OF_THE_QUESTION;
            for (Map.Entry<String, Integer> writing : tokensByMarker.entrySet()) {
                if (question.contains(writing.getKey())) {
                    count += writing.getValue();
                }
            }
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            boolean counting = options.getNumPredict() != null && options.getNumPredict() == ONE_TOKEN;
            String text;
            if (counting) {
                counted.add(question);
                text = "{";
            } else {
                answered.add(question);
                text = answers.removeFirst();
            }
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage(text),
                            ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(count, 1)).build());
        }

        /** Every counting call's question, oldest first. */
        List<String> counted() {
            return List.copyOf(counted);
        }

        /** Every answering call's question, oldest first. */
        List<String> answered() {
            return List.copyOf(answered);
        }

        /** Every question put to the engine, counted or answered, oldest first. */
        List<String> everyQuestion() {
            return List.copyOf(everyQuestion);
        }
    }
}
