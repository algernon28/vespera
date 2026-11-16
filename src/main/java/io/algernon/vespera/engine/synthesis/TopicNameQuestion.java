package io.algernon.vespera.engine.synthesis;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The question a topic is named by (ADR-403 section 5): in English whatever the deliverable language, the passages
 * a blank line apart after its opening, the instruction after them, and the language's English name last.
 */
public final class TopicNameQuestion {

    private TopicNameQuestion() {}

    /** The question for the passages {@code sent}, in the order given, asking for the name in {@code language}. */
    public static String of(List<NamingPassage> sent, DeliverableLanguage language) {
        int count = sent.size();
        String passages = sent.stream().map(NamingPassage::text).collect(Collectors.joining("\n\n"));
        return "Name a topic of an archive from " + count + " passage(s) of the documents it was found in.\n\n"
                + "The passages follow below, the most typical of the topic first, a blank line apart.\n\n"
                + passages + "\n\n"
                + "That is all " + count + " passage(s). Name what they have in common as a heading of at most 8"
                + " words for the topic: no sentence, no quotation marks and no full stop.\n\n"
                + "Answer with one JSON object of one field: \"name\", the topic's name, on one line. Write it in "
                + language.englishName() + ".";
    }
}
