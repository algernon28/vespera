package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The call that names the topics before the arrangement gate (ADR-407 §4 to §8), against a scripted model.
 *
 * <p>One call a topic, to the naming model, at temperature 0 and a fixed seed, thinking off, under a one-field
 * schema: the topic's passages strongest first, whole, filled under a word budget, then the instruction in English,
 * naming the deliverable language last. What comes back is checked as a cluster's writing is (ADR-108): an answer the
 * checks turn down gives the topic its fallback name, and nothing stops; a serving engine that gives no answer at all
 * is no answer to turn down, and the failure reaches the caller. Siblings are named in key order, and a name the same
 * as an earlier sibling's, case and blanks aside, is asked for once more, naming the names taken; a second such name
 * falls back, and a fallback the same as an earlier name stands.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("829")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TopicNamingTest {

    /** The model the call names: a name of the test's own. */
    private static final String MODEL_NAME = "a-writing-model:8b";

    /** The window the naming call reads in, in tokens. */
    private static final int WINDOW = 4096;

    /** The tokens it leaves a name. */
    private static final int REPLY = 64;

    /** The seed it sends with a temperature of 0. */
    private static final int SEED = 1;

    /** The words of passage it sends at most. */
    private static final int BUDGET = 1000;

    /** The most words a name may have. */
    private static final int LONGEST = 12;

    private static final String FIRST_PASSAGE = "SUGAR CROP FORECAST CUT. The cane harvest is put lower.";
    private static final String SECOND_PASSAGE = "WHITE SUGAR EXPORTS RISE. Shipments rose in March.";

    /** A fallback name, as {@code TopicFallbackName} would give it. */
    private static final String FALLBACK = "Sugar report";

    @Test
    @Story("The same seeds give the same names")
    @DisplayName("A name is asked for at temperature 0 with a fixed seed, no thinking, a one-field answer and the measured window")
    void sendsTheMeasuredOptions() {
        Scripted model = new Scripted(answer("{\"name\":\"Sugar crops\"}"));

        new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(FIRST_PASSAGE), FALLBACK)), MODEL_NAME, DeliverableLanguage.ENGLISH);
        OllamaChatOptions sent = (OllamaChatOptions) model.asked.getFirst().getOptions();

        claim("the call names the model it was handed, a window of " + WINDOW + " tokens, " + REPLY + " tokens for"
                        + " the name, -1 for how much of a question too long for the window to keep, a temperature"
                        + " of 0, a seed of " + SEED + ", thinking off, and an answer of one required string field,"
                        + " name",
                () -> assertThat(List.of(
                                sent.getModel(), sent.getNumCtx(), sent.getNumPredict(), sent.getNumKeep(),
                                sent.getTemperature(), sent.getSeed(), sent.getThinkOption(),
                                sent.getOutputSchema().replaceAll("\\s", "")))
                        .containsExactly(
                                MODEL_NAME, WINDOW, REPLY, -1, 0.0, SEED,
                                OllamaChatOptions.builder().disableThinking().build().getThinkOption(),
                                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
                                        + "\"required\":[\"name\"]}"));
        claim("and the numbers are the constants the record names, which the arrangement run records",
                () -> assertThat(List.of(
                                TopicNaming.NAMING_WINDOW, TopicNaming.NAME_REPLY_ALLOWANCE, TopicNaming.NAMING_SEED,
                                TopicNaming.WORD_BUDGET, TopicNaming.LONGEST_NAME_IN_WORDS))
                        .containsExactly(WINDOW, REPLY, SEED, BUDGET, LONGEST));
    }

    @Test
    @Story("A name is written in the pages' language")
    @DisplayName("The passages go strongest first, and the instruction after them names the pages' language last")
    void thePassagesComeFirstAndTheLanguageIsNamedLast() {
        Scripted inEnglish = new Scripted(answer("{\"name\":\"Sugar crops\"}"));
        Scripted inItalian = new Scripted(answer("{\"name\":\"Raccolti di zucchero\"}"));
        NameCall call = new NameCall(List.of(FIRST_PASSAGE, SECOND_PASSAGE), FALLBACK);

        new TopicNaming(inEnglish).nameSiblings(List.of(call), MODEL_NAME, DeliverableLanguage.ENGLISH);
        new TopicNaming(inItalian).nameSiblings(List.of(call), MODEL_NAME, DeliverableLanguage.ITALIAN);
        String english = inEnglish.asked.getFirst().getContents();
        String italian = inItalian.asked.getFirst().getContents();

        claim("the first passage stands before the second, and the question ends naming Italian where the pages are"
                        + " Italian",
                () -> assertThat(italian.indexOf(FIRST_PASSAGE))
                        .isNotNegative()
                        .isLessThan(italian.indexOf(SECOND_PASSAGE)));
        claim("and the question ends \"Write it in Italian.\" for Italian pages and \"Write it in English.\" for"
                        + " English ones, the two differing in that word alone",
                () -> assertThat(List.of(italian.endsWith("Write it in Italian."), english.replace("English", "Italian")))
                        .containsExactly(true, italian));
    }

    @Test
    @Story("A name is asked from the passages most like the rest of the topic")
    @DisplayName("Whole passages are sent strongest first until the next would pass the word budget, one longer than the budget passed over")
    void wholePassagesFillTheWordBudget() {
        String tooLong = words(BUDGET + 1);
        String sevenHundred = words(700);
        String fourHundred = words(400);
        String oneHundred = words(100);

        claim("of passages of " + (BUDGET + 1) + ", 700, 400 and 100 words, the first, longer than the budget of "
                        + BUDGET + " words on its own, is passed over, the 700 are sent, and the fill ends at the 400,"
                        + " which do not fit the 300 left, so the 100 after them are not sent either",
                () -> assertThat(TopicNaming.whatFits(List.of(tooLong, sevenHundred, fourHundred, oneHundred)))
                        .containsExactly(sevenHundred));
    }

    @Test
    @Story("A name the model writes is kept")
    @DisplayName("A name that passes every check is kept, the blanks around it removed and nothing else")
    void aNameThatPassesIsKept() {
        Scripted model = new Scripted(answer("{\"name\":\"  Sugar crops and exports. \\n\"}"));

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(FIRST_PASSAGE), FALLBACK)), MODEL_NAME, DeliverableLanguage.ENGLISH);

        claim("the topic is named \"Sugar crops and exports.\", its full stop kept, as the model wrote it",
                () -> assertThat(given).containsExactly(
                        new GivenName("Sugar crops and exports.", NameOrigin.WRITTEN, Optional.empty())));
    }

    static Stream<Arguments> turnDowns() {
        return Stream.of(
                Arguments.of("a question cut, counted at the window less one token",
                        (Supplier<ChatResponse>) () -> response("{\"name\":\"Sugar\"}", "stop", WINDOW - 1),
                        NameTurnDown.QUESTION_CUT),
                Arguments.of("an answer that ran out of room",
                        (Supplier<ChatResponse>) () -> response("{\"name\":\"Sugar", "length", 100),
                        NameTurnDown.RAN_OUT_OF_ROOM),
                Arguments.of("an answer that is not one object with a string name",
                        (Supplier<ChatResponse>) () -> answer("Sugar crops"),
                        NameTurnDown.NOT_THE_SHAPE_ASKED),
                Arguments.of("an empty name",
                        (Supplier<ChatResponse>) () -> answer("{\"name\":\"  \"}"),
                        NameTurnDown.EMPTY),
                Arguments.of("a name of " + (LONGEST + 1) + " words",
                        (Supplier<ChatResponse>) () -> answer("{\"name\":\"" + words(LONGEST + 1) + "\"}"),
                        NameTurnDown.NOT_ONE_SHORT_LINE),
                Arguments.of("a name broken over two lines",
                        (Supplier<ChatResponse>) () -> answer("{\"name\":\"Sugar\\ncrops\"}"),
                        NameTurnDown.NOT_ONE_SHORT_LINE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("turnDowns")
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("An answer the checks turn down gives the topic its fallback name, and the arrangement goes on")
    void anAnswerTurnedDownFallsBack(String what, Supplier<ChatResponse> response, NameTurnDown why) {
        Scripted model = new Scripted(response.get());

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(FIRST_PASSAGE), FALLBACK)), MODEL_NAME, DeliverableLanguage.ENGLISH);

        claim(what + " is turned down, said why, and the topic takes its fallback name, nothing being thrown",
                () -> assertThat(given).containsExactly(new GivenName(FALLBACK, NameOrigin.FALLBACK, Optional.of(why))));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A topic none of whose passages fits the word budget is asked nothing and falls back")
    void aTopicWithNoPassageThatFitsIsAskedNothing() {
        Scripted model = new Scripted();

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(words(BUDGET + 1), words(BUDGET + 2)), FALLBACK)),
                MODEL_NAME,
                DeliverableLanguage.ENGLISH);

        claim("with passages of " + (BUDGET + 1) + " and " + (BUDGET + 2) + " words, each longer than the budget of "
                        + BUDGET + ", nothing is sent, no call is made, and the topic takes its fallback name",
                () -> assertThat(List.of(given, model.asked.size())).containsExactly(
                        List.of(new GivenName(FALLBACK, NameOrigin.FALLBACK, Optional.of(NameTurnDown.NOTHING_SENT))),
                        0));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A question the serving engine refuses as longer than the window falls back as a cut one does")
    void aQuestionRefusedAsTooLongFallsBack() {
        Scripted model = new Scripted(() -> {
            throw new NonTransientAiException("400 - {\"error\":\"the prompt is longer than the context length\"}");
        });

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(FIRST_PASSAGE), FALLBACK)), MODEL_NAME, DeliverableLanguage.ENGLISH);

        claim("the refusal is a cut question, and the topic takes its fallback name",
                () -> assertThat(given).containsExactly(
                        new GivenName(FALLBACK, NameOrigin.FALLBACK, Optional.of(NameTurnDown.QUESTION_CUT))));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A serving engine that gives no answer at all is no answer to turn down, and the failure reaches the step")
    void noAnswerAtAllIsNoTurnDown() {
        ResourceAccessException unreachable = new ResourceAccessException("Connection refused");
        Scripted model = new Scripted(() -> {
            throw unreachable;
        });

        Throwable thrown = catchThrowable(() -> new TopicNaming(model).nameSiblings(
                List.of(new NameCall(List.of(FIRST_PASSAGE), FALLBACK)), MODEL_NAME, DeliverableLanguage.ENGLISH));

        claim("the engine's own failure reaches the caller unchanged, so that no fallback is recorded for a name"
                        + " that was never answered",
                () -> assertThat(thrown).isSameAs(unreachable));
    }

    @Test
    @Story("Topics side by side have different names")
    @DisplayName("A name the same as an earlier topic's, case and blanks aside, is asked for once more, naming the names taken")
    void aNameTakenByAnEarlierSiblingIsAskedForOnceMore() {
        Scripted model = new Scripted(
                answer("{\"name\":\"Sugar Market\"}"),
                answer("{\"name\":\"Cotton\"}"),
                answer("{\"name\":\"sugar  market\"}"),
                answer("{\"name\":\"Sugar exports\"}"));

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(
                        new NameCall(List.of(FIRST_PASSAGE), "first"),
                        new NameCall(List.of("COTTON CROP. Cotton is up."), "second"),
                        new NameCall(List.of(SECOND_PASSAGE), "third")),
                MODEL_NAME,
                DeliverableLanguage.ENGLISH);
        String askedAgain = model.asked.get(3).getContents();

        claim("the third topic, first named \"sugar  market\", the first's name but for case and blanks, is named"
                        + " by the answer to the second asking, and says so",
                () -> assertThat(given).containsExactly(
                        new GivenName("Sugar Market", NameOrigin.WRITTEN, Optional.empty()),
                        new GivenName("Cotton", NameOrigin.WRITTEN, Optional.empty()),
                        new GivenName("Sugar exports", NameOrigin.WRITTEN_WHEN_ASKED_AGAIN, Optional.empty())));
        claim("and the second asking names both names already taken, in the order they were given, and still ends"
                        + " naming the language",
                () -> assertThat(List.of(
                                askedAgain.indexOf("\"Sugar Market\"") >= 0,
                                askedAgain.indexOf("\"Sugar Market\"") < askedAgain.indexOf("\"Cotton\""),
                                askedAgain.endsWith("Write it in English.")))
                        .containsExactly(true, true, true));
    }

    @Test
    @Story("Topics side by side have different names")
    @DisplayName("A second name taken by an earlier topic falls back, and a fallback that is taken too stands")
    void aSecondTakenNameFallsBackAndATakenFallbackStands() {
        Scripted model = new Scripted(
                answer("{\"name\":\"Sugar Market\"}"),
                answer("{\"name\":\"Sugar Market\"}"),
                answer("{\"name\":\"SUGAR MARKET\"}"));

        List<GivenName> given = new TopicNaming(model).nameSiblings(
                List.of(
                        new NameCall(List.of(FIRST_PASSAGE), "Sugar report"),
                        new NameCall(List.of(SECOND_PASSAGE), "sugar market")),
                MODEL_NAME,
                DeliverableLanguage.ENGLISH);

        claim("the second topic, named the first's name twice, takes its fallback, which is the first's name too and"
                        + " stands as it is, three calls having been made and no fourth",
                () -> assertThat(List.of(given, model.asked.size())).containsExactly(
                        List.of(
                                new GivenName("Sugar Market", NameOrigin.WRITTEN, Optional.empty()),
                                new GivenName("sugar market", NameOrigin.FALLBACK,
                                        Optional.of(NameTurnDown.SAME_AS_AN_EARLIER_NAME))),
                        3));
    }

    /** {@code n} words, each a short one. */
    private static String words(int n) {
        return String.join(" ", Collections.nCopies(n, "cane"));
    }

    /** An answer finished of its own accord, reporting a question well inside the window. */
    private static ChatResponse answer(String text) {
        return response(text, "stop", 300);
    }

    private static ChatResponse response(String text, String finishReason, int promptTokens) {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage(text),
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 1)).build());
    }

    /** A model answering each call with the next scripted response, keeping every prompt it was asked. */
    private static final class Scripted implements ChatModel {

        private final Deque<Supplier<ChatResponse>> responses = new ArrayDeque<>();

        private final List<Prompt> asked = new ArrayList<>();

        Scripted(ChatResponse... scripted) {
            for (ChatResponse response : scripted) {
                responses.add(() -> response);
            }
        }

        Scripted(Supplier<ChatResponse> scripted) {
            responses.add(scripted);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            asked.add(prompt);
            return responses.removeFirst().get();
        }
    }
}
