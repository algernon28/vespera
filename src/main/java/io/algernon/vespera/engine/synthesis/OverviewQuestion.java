package io.algernon.vespera.engine.synthesis;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The two questions a topic's overview is asked (ADR-402 sections 1, 3 and 4), in English whatever the deliverable
 * language (ADR-321 section 2): each written cluster under its own number among the topic's clusters, titled, its
 * writing's own citations taken out; at most {@link #WORD_LIMIT} words; ADR-159's example drawn from the numbers sent;
 * the answer's one field and the language last. The second is the first with {@link #SECOND_ASKING} before its last
 * paragraph, one wording whatever the first answer was.
 */
public final class OverviewQuestion {

    /** The words the question asks the overview to keep to (ADR-402 section 3). */
    public static final int WORD_LIMIT = 150;

    /**
     * The paragraph the second question adds (ADR-402 section 4): it names what failed by the check that failed and
     * carries nothing of the first answer.
     */
    public static final String SECOND_ASKING = "An earlier answer to this was turned down because a sentence cited no"
            + " group, or cited a number not shown above: every sentence must end with the bracketed number of at least"
            + " one group shown above.";

    /** A writing's own citation, with the blanks before it: they point at documents of one cluster (section 1). */
    private static final Pattern A_CITATION_OF_ITS_OWN = Pattern.compile("\\s*" + Citation.AS_WRITTEN.pattern());

    private OverviewQuestion() {}

    /** The first question, over {@code sources} in the order given. */
    public static String first(String topicName, List<OverviewSource> sources, DeliverableLanguage language) {
        return question(topicName, sources, language, false);
    }

    /** The second question: the first with {@link #SECOND_ASKING} before its last paragraph. */
    public static String second(String topicName, List<OverviewSource> sources, DeliverableLanguage language) {
        return question(topicName, sources, language, true);
    }

    private static String question(
            String topicName, List<OverviewSource> sources, DeliverableLanguage language, boolean second) {
        int sent = sources.size();
        String written = sources.stream()
                .map(source -> "[" + source.number() + "] " + source.title() + "\n"
                        + A_CITATION_OF_ITS_OWN.matcher(source.writing()).replaceAll(""))
                .collect(Collectors.joining("\n\n"));
        String between = second ? SECOND_ASKING + "\n\n" : "";
        return "Write a short overview of a topic from an archive, over what was written on " + sent
                + " of its groups.\n\n"
                + "The topic is called \"" + topicName + "\".\n\n"
                + "What was written on each group follows below, under the number to cite it by.\n\n"
                + written + "\n\n"
                + "That is all " + sent + " group(s). Write the overview in at most " + WORD_LIMIT + " words: say what"
                + " the groups cover together, how they relate and where they differ. Do not describe them one by one."
                + " End every sentence with the bracketed number of each group it draws on, written in square brackets"
                + " exactly as the numbers appear above, such as " + citationExample(sources) + ", and use no other"
                + " citation of any kind.\n\n"
                + between
                + "Answer with one JSON object of one field: \"prose\", the overview, which ends with its last"
                + " sentence. Write it in " + language.englishName() + ".\n";
    }

    /** ADR-159's example, drawn from the numbers sent: never one the call did not send. */
    private static String citationExample(List<OverviewSource> sources) {
        return switch (sources.size()) {
            case 0 -> "[1]";
            case 1 -> "[" + sources.get(0).number() + "]";
            case 2 -> "[" + sources.get(0).number() + "] or [" + sources.get(0).number() + "][" + sources.get(1).number()
                    + "]";
            default -> "[" + sources.get(0).number() + "] or [" + sources.get(1).number() + "][" + sources.get(2).number()
                    + "]";
        };
    }
}
