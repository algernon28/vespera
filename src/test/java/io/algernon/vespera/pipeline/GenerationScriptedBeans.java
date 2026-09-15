package io.algernon.vespera.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link EmbeddingScriptedBeans}' sibling one stage along (#180): a generating model that answers in
 * the shape the call imposes on it, and can be told to answer one cluster differently from the next.
 *
 * <p><b>Scripted by the cluster's name rather than by sequence</b>, for the reason {@code
 * PathScriptedExtractor} keys on the path: the order clusters are written in is the arrangement's to
 * decide, so a fixture that answered by call number would quietly pin an ordering no test is making
 * a claim about.
 *
 * <p>It never refuses, never runs out of room and never returns anything its schema would reject, so
 * nothing here exercises the checks ADR-108 puts on a response — each of those has its own ticket and
 * will want a double that can fail on purpose.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would silently replace the real chat model in every {@code
 * @SpringBootTest} — including the one that talks to a real serving engine.
 */
@TestConfiguration
class GenerationScriptedBeans {

    /** The heading this fixture's model gives a cluster it has not been scripted for by name. */
    static final String GENERATED_TITLE = "Site Safety Audits, 2018 to 2021";

    /** The writing it returns, with a marker in it so nothing downstream has to invent one. */
    static final String GENERATED_PROSE = "Both audits [1] cover the same site a year apart.";

    /** Answers scripted for a particular cluster, by the name stage 6a gave it. */
    private static final Map<String, Answer> BY_LABEL = new LinkedHashMap<>();

    /**
     * Scripts one answer for the cluster named {@code label}, in place of the default one.
     *
     * <p>Held statically because the bean is built by the context and a test cannot reach the instance
     * before the invocation it is scripting; {@link #forgetScriptedAnswers()} is what keeps one test's
     * script from reaching the next.
     */
    static void answerFor(String label, String title, String prose) {
        BY_LABEL.put(label, new Answer(title, prose));
    }

    /** Drops every scripted answer, so nothing a test wrote outlives it. */
    static void forgetScriptedAnswers() {
        BY_LABEL.clear();
    }

    @Bean
    ChatModel chatModel() {
        return prompt -> {
            Answer answer = BY_LABEL.entrySet().stream()
                    .filter(scripted -> prompt.getContents().contains(scripted.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(new Answer(GENERATED_TITLE, GENERATED_PROSE));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"title\":\"" + answer.title() + "\",\"prose\":\"" + answer.prose() + "\"}"))));
        };
    }

    /** One scripted answer: what this fixture's model says about one cluster. */
    private record Answer(String title, String prose) {}
}
