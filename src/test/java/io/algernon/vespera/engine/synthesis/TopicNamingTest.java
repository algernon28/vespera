package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.ai.retry.TransientAiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * How a topic is named (ADR-403 §3, §4, §6, §7): one call to the generation model, sent the topic's passages in the
 * order given until 1,500 words are reached, at temperature 0 and seed 1 under a shape of one field; the name it writes
 * used where it is one line of at most 16 words, the blanks around it set aside; and otherwise the strongest
 * contributing seed's title, its filename stem or its path standing in, with the reason, and never a stop, but for a
 * refusal that is not about the question's length, which reaches the caller.
 *
 * <p>The model is scripted: each test says how it answers, and it keeps every question it was asked and the options
 * sent with it.
 */
@Epic("Synthesis")
@Feature("Naming the topics")
@Issue("829")
@Link(name = "ADR-403", url = Adr.THE_GENERATION_MODEL_NAMES_EACH_TOPIC, type = "adr")
class TopicNamingTest {

    /** A name of the test's own for the generation model. */
    private static final String MODEL_NAME = "a-writing-model:8b";

    /** The window the record sends, in tokens. */
    private static final int WINDOW = 8192;

    /** The window's last token, the count a question cut to fit is reported at (ADR-166 §3). */
    private static final int THE_WINDOWS_LAST_TOKEN = WINDOW - 1;

    /** What a question well inside the window is counted at. */
    private static final int A_QUESTION_WELL_INSIDE_THE_WINDOW = 400;

    /** The answer's allowance the record sends, in tokens. */
    private static final int ANSWER_ALLOWANCE = 64;

    /** How much of a too-long question the engine is asked to keep: all of it that fits. */
    private static final int KEEP_ALL_THAT_FITS = -1;

    private static final double TEMPERATURE_ZERO = 0.0;

    private static final int SEED_ONE = 1;

    /** The words of passages a topic is sent at most. */
    private static final int BUDGET = 1500;

    /** The most words a name may have and be used. */
    private static final int WORDS_USED = 16;

    private static final NamingPassage ON_FILTERS =
            new NamingPassage("The consortium cleans the canal filters each quarter.", 8);

    private static final NamingPassage ON_WATER_RIGHTS =
            new NamingPassage("Water rights are allotted by the hour of the day.", 10);

    /** The Docling title of the topic's strongest contributing seed. */
    private static final String THE_SEEDS_TITLE = "Manuale degli impianti irrigui";

    private static final String THE_SEEDS_PATH = "manuali/Manuale impianti.pdf";

    private static final StrongestSeed A_TITLED_SEED = new StrongestSeed(THE_SEEDS_PATH, Optional.of(THE_SEEDS_TITLE));

    private static final TopicNameCall TWO_PASSAGES =
            new TopicNameCall(List.of(ON_FILTERS, ON_WATER_RIGHTS), A_TITLED_SEED);

    private static final String A_NAME = "Irrigazione e diritti sull'acqua";

    @Test
    @Story("A topic is named the same way every run")
    @DisplayName("The call names the model and sends the window, the answer's allowance, temperature 0, seed 1 and a shape of one field")
    void asksAtTemperatureZeroWithSeedOneUnderAShapeOfOneField() {
        ScriptedModel model = new ScriptedModel(prompt -> answered(object(A_NAME)));

        nameFrom(model, TWO_PASSAGES);

        claim(
                "the code's window, answer allowance, temperature and seed are " + WINDOW + ", " + ANSWER_ALLOWANCE
                        + ", 0 and 1",
                () -> {
                    assertThat(TopicNaming.CONTEXT_WINDOW).isEqualTo(WINDOW);
                    assertThat(TopicNaming.REPLY_ALLOWANCE).isEqualTo(ANSWER_ALLOWANCE);
                    assertThat(TopicNaming.TEMPERATURE).isEqualTo(TEMPERATURE_ZERO);
                    assertThat(TopicNaming.SEED).isEqualTo(SEED_ONE);
                });
        claim(
                "one call was made, naming the model, and sending a window of " + WINDOW + " tokens, an allowance of "
                        + ANSWER_ALLOWANCE + ", all of a too-long question that fits kept, temperature 0, seed 1, and a"
                        + " shape requiring the one field name",
                () -> assertThat(model.options()).singleElement().satisfies(options -> {
                    assertThat(options.getModel()).isEqualTo(MODEL_NAME);
                    assertThat(options.getNumCtx()).isEqualTo(WINDOW);
                    assertThat(options.getNumPredict()).isEqualTo(ANSWER_ALLOWANCE);
                    assertThat(options.getNumKeep()).isEqualTo(KEEP_ALL_THAT_FITS);
                    assertThat(options.getTemperature()).isEqualTo(TEMPERATURE_ZERO);
                    assertThat(options.getSeed()).isEqualTo(SEED_ONE);
                    JsonNode schema = JsonMapper.builder().build().readTree(options.getOutputSchema());
                    assertThat(schema.get("required").valueStream().map(JsonNode::asString).toList())
                            .containsExactly("name");
                    assertThat(schema.get("properties").propertyNames()).containsExactly("name");
                }));
    }

    @Test
    @Story("A topic is named from the passages most typical of it")
    @DisplayName("Passages are sent in the order given until 1,500 words: one longer than that is passed over, and the first that does not fit what is left ends the fill")
    void sendsThePassagesInTheOrderGivenUntilTheBudgetRunsOut() {
        NamingPassage longerThanTheBudget = new NamingPassage("A passage longer than the budget on its own.", 1600);
        NamingPassage firstOfSevenHundred = new NamingPassage("The first passage of seven hundred words.", 700);
        NamingPassage secondOfSevenHundred = new NamingPassage("The second passage of seven hundred words.", 700);
        NamingPassage ofTwoHundred = new NamingPassage("A passage of two hundred words.", 200);
        NamingPassage ofFifty = new NamingPassage("A passage of fifty words after it.", 50);
        ScriptedModel model = new ScriptedModel(prompt -> answered(object(A_NAME)));

        nameFrom(model, new TopicNameCall(
                List.of(longerThanTheBudget, firstOfSevenHundred, secondOfSevenHundred, ofTwoHundred, ofFifty),
                A_TITLED_SEED));

        claim("the budget is " + BUDGET + " words", () -> assertThat(TopicNaming.WORD_BUDGET).isEqualTo(BUDGET));
        claim(
                "the one question asked sends the two passages of 700 words, 1,400 in all, and no other: the passage"
                        + " of 1,600 is passed over, the one of 200 would bring the words past " + BUDGET + " and ends"
                        + " the fill, and the one of 50 after it is not reached",
                () -> assertThat(model.questions()).containsExactly(TopicNameQuestion.of(
                        List.of(firstOfSevenHundred, secondOfSevenHundred), DeliverableLanguage.ENGLISH)));
    }

    @Test
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("A topic none of whose passages fits the budget asks nothing, and its strongest seed's title stands in")
    void aTopicNoPassageOfWhichFitsAsksNothing() {
        ScriptedModel model = new ScriptedModel(prompt -> answered(object(A_NAME)));

        TopicName name = nameFrom(model, new TopicNameCall(
                List.of(new NamingPassage("A passage longer than the budget on its own.", 1600)), A_TITLED_SEED));

        claim(
                "the seed's title stands in, because nothing fitted",
                () -> assertThat(name).isEqualTo(new TopicName(
                        THE_SEEDS_TITLE, TopicNameSource.SEED_TITLE, Optional.of(NameFallbackReason.NOTHING_FITS))));
        claim("and no call was made", () -> assertThat(model.questions()).isEmpty());
    }

    @Test
    @Story("The name the model writes is used as it wrote it")
    @DisplayName("A name with blanks around it is used without them, and nothing else of it is changed")
    void keepsAUsableNameAsWrittenBarTheBlanksAroundIt() {
        ScriptedModel model = new ScriptedModel(prompt -> answered(object("  " + A_NAME + "  ")));

        claim(
                "the name is the model's, the blanks around it set aside, its apostrophe kept, with no reason to stand"
                        + " anything in",
                () -> assertThat(nameFrom(model, TWO_PASSAGES))
                        .isEqualTo(new TopicName(A_NAME, TopicNameSource.MODEL, Optional.empty())));
    }

    @Test
    @Story("The name the model writes is used as it wrote it")
    @DisplayName("A name of 16 words is used, and one of 17 is not")
    void usesSixteenWordsAndNotSeventeen() {
        String sixteenWords = String.join(" ", Collections.nCopies(WORDS_USED, "acqua"));
        String seventeenWords = sixteenWords + " acqua";

        claim("the most words a name may have is " + WORDS_USED, () -> assertThat(TopicNaming.WORDS_ACCEPTED)
                .isEqualTo(WORDS_USED));
        claim(
                "a name of " + WORDS_USED + " words is used as the model's",
                () -> assertThat(nameFrom(new ScriptedModel(prompt -> answered(object(sixteenWords))), TWO_PASSAGES))
                        .isEqualTo(new TopicName(sixteenWords, TopicNameSource.MODEL, Optional.empty())));
        claim(
                "and one of a word more is not: the seed's title stands in, the name having too many words",
                () -> assertThat(nameFrom(new ScriptedModel(prompt -> answered(object(seventeenWords))), TWO_PASSAGES))
                        .isEqualTo(new TopicName(
                                THE_SEEDS_TITLE,
                                TopicNameSource.SEED_TITLE,
                                Optional.of(NameFallbackReason.TOO_MANY_WORDS))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersThatCannotBeUsed")
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("An answer that cannot be used is stood in for by the strongest seed's title, with the reason, and nothing stops")
    void anAnswerItCannotUseIsStoodInForByTheStrongestSeedsTitle(Unusable unusable) {
        TopicName name = nameFrom(new ScriptedModel(unusable.answer()), TWO_PASSAGES);

        claim(
                "the seed's title stands in for " + unusable.what() + ", for the reason " + unusable.reason(),
                () -> assertThat(name).isEqualTo(
                        new TopicName(THE_SEEDS_TITLE, TopicNameSource.SEED_TITLE, Optional.of(unusable.reason()))));
    }

    static Stream<Unusable> answersThatCannotBeUsed() {
        return Stream.of(
                new Unusable("a blank name", prompt -> answered(object("   ")), NameFallbackReason.BLANK),
                new Unusable(
                        "a name on two lines",
                        prompt -> answered("{\"name\":\"Irrigazione\\ne diritti\"}"),
                        NameFallbackReason.NOT_ONE_LINE),
                new Unusable("an answer that is no object", prompt -> answered(A_NAME), NameFallbackReason.OUT_OF_SHAPE),
                new Unusable(
                        "an object with no name in it",
                        prompt -> answered("{\"title\":\"" + A_NAME + "\"}"),
                        NameFallbackReason.OUT_OF_SHAPE),
                new Unusable(
                        "a response carrying no answer at all",
                        prompt -> new ChatResponse(List.of()),
                        NameFallbackReason.OUT_OF_SHAPE),
                new Unusable(
                        "an answer stopped for its length",
                        prompt -> answer(object(A_NAME), "length", A_QUESTION_WELL_INSIDE_THE_WINDOW),
                        NameFallbackReason.RAN_OUT_OF_ROOM),
                new Unusable(
                        "a question counted at the window's last token",
                        prompt -> answer(object(A_NAME), "stop", THE_WINDOWS_LAST_TOKEN),
                        NameFallbackReason.PAST_THE_WINDOW),
                new Unusable(
                        "a question refused as longer than the window",
                        prompt -> {
                            throw new NonTransientAiException("HTTP 400 - {\"error\":\"request (9000 tokens) exceeds"
                                    + " the available context size (" + WINDOW + " tokens), try increasing it\"}");
                        },
                        NameFallbackReason.PAST_THE_WINDOW));
    }

    @Test
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("With no title, the seed's filename stem stands in, and with no stem, its path")
    void withNoTitleTheFilenameStemAndThenThePathStandIn() {
        String pathWithNoStem = "manuali/.pdf";

        claim(
                "a seed with no title is stood in for by its filename stem",
                () -> assertThat(blankAnswerStoodInFor(new StrongestSeed(THE_SEEDS_PATH, Optional.empty())))
                        .isEqualTo(new TopicName(
                                "Manuale impianti", TopicNameSource.SEED_FILENAME_STEM, Optional.of(NameFallbackReason.BLANK))));
        claim(
                "so is a seed whose title is blank",
                () -> assertThat(blankAnswerStoodInFor(new StrongestSeed(THE_SEEDS_PATH, Optional.of("  "))))
                        .isEqualTo(new TopicName(
                                "Manuale impianti", TopicNameSource.SEED_FILENAME_STEM, Optional.of(NameFallbackReason.BLANK))));
        claim(
                "and a seed whose filename is an extension alone is stood in for by its path",
                () -> assertThat(blankAnswerStoodInFor(new StrongestSeed(pathWithNoStem, Optional.empty())))
                        .isEqualTo(new TopicName(
                                pathWithNoStem, TopicNameSource.SEED_PATH, Optional.of(NameFallbackReason.BLANK))));
    }

    @Test
    @Story("A topic the model cannot name is named after its strongest seed")
    @DisplayName("A refusal that is not about the question's length, or a model that does not answer, reaches the caller and nothing stands in")
    void aRefusalNotAboutTheQuestionsLengthReachesTheCaller() {
        ScriptedModel notFound = new ScriptedModel(prompt -> {
            throw new NonTransientAiException("HTTP 404 - {\"error\":\"model '" + MODEL_NAME + "' not found\"}");
        });
        ScriptedModel notAnswering = new ScriptedModel(prompt -> {
            throw new TransientAiException("I/O error on POST request: Connection refused");
        });

        claim(
                "a model the engine says it does not have is thrown to the caller as the engine refused it",
                () -> assertThatThrownBy(() -> nameFrom(notFound, TWO_PASSAGES))
                        .isInstanceOf(NonTransientAiException.class)
                        .hasMessageContaining("not found"));
        claim(
                "and so is an engine that does not answer",
                () -> assertThatThrownBy(() -> nameFrom(notAnswering, TWO_PASSAGES))
                        .isInstanceOf(TransientAiException.class));
    }

    private static TopicName blankAnswerStoodInFor(StrongestSeed seed) {
        return nameFrom(
                new ScriptedModel(prompt -> answered(object(" "))),
                new TopicNameCall(List.of(ON_FILTERS, ON_WATER_RIGHTS), seed));
    }

    private static TopicName nameFrom(ChatModel model, TopicNameCall call) {
        return new TopicNaming(model).nameFor(call, MODEL_NAME, DeliverableLanguage.ENGLISH);
    }

    /** {@code name} as the one-field object the call imposes, escaped as JSON escapes a string's quotation marks. */
    private static String object(String name) {
        return "{\"name\":\"" + name.replace("\"", "\\\"") + "\"}";
    }

    /** An answer finished of its own accord, its question counted well inside the window. */
    private static ChatResponse answered(String text) {
        return answer(text, "stop", A_QUESTION_WELL_INSIDE_THE_WINDOW);
    }

    private static ChatResponse answer(String text, String finishReason, int promptTokens) {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage(text),
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 1)).build());
    }

    /** One way of answering the name cannot be taken from: what it is, how the model answers, and the reason recorded. */
    record Unusable(String what, Function<Prompt, ChatResponse> answer, NameFallbackReason reason) {

        @Override
        public String toString() {
            return what;
        }
    }

    /** A model answering every question as it is told, keeping each question and the options sent with it. */
    private static final class ScriptedModel implements ChatModel {

        private final Function<Prompt, ChatResponse> answer;

        private final List<String> questions = new ArrayList<>();

        private final List<OllamaChatOptions> options = new ArrayList<>();

        ScriptedModel(Function<Prompt, ChatResponse> answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            questions.add(prompt.getContents());
            options.add((OllamaChatOptions) prompt.getOptions());
            return answer.apply(prompt);
        }

        List<String> questions() {
            return questions;
        }

        List<OllamaChatOptions> options() {
            return options;
        }
    }
}
