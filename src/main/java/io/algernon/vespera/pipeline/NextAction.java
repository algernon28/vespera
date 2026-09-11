package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The line an invocation ends on (ADR-098): what is set, what is not, and the single next action.
 *
 * <p>The one place this sentence is worded, for the reason {@link
 * RedundancyJobConfiguration#logGateClosed} is the one place stage 4's gate is worded: an operator
 * reading two invocations should be reading the same sentence, and two call sites wording it
 * separately is how they stop being.
 *
 * <p>Computed per invocation rather than written down, so it cannot go stale the way a document can.
 * It is not a gate and moves nothing: every value it names is one a person writes into the profile,
 * and every invocation it ends still exits on its own terms.
 *
 * <p><b>The action names every value the next invocation wants, not the next one it would gate on.</b>
 * Naming them one at a time is what turns four invocations into five — each one spent discovering a
 * value that was already knowable — and the four is the contract ADR-098 records.
 */
@Component
class NextAction {

    /** Where every value this line names is written, and the only file it ever asks anyone to edit. */
    private static final String PROFILE = "profile.yaml";

    private final ProfileStore profileStore;
    private final RelevanceLabels relevanceLabels;
    private final Path workingDirectory;

    NextAction(
            ProfileStore profileStore,
            RelevanceLabels relevanceLabels,
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory) {
        this.profileStore = profileStore;
        this.relevanceLabels = relevanceLabels;
        this.workingDirectory = workingDirectory;
    }

    /**
     * The closing line for the invocation that has just finished, read off the profile and the ledger
     * as they now stand.
     *
     * <p>Read at the end rather than accumulated as the invocation goes: a value the operator set
     * before this invocation is a value this invocation used, and a line assembled from what each step
     * happened to see is a line that disagrees with the file.
     */
    String line() {
        Profile profile = profileStore.load();
        return line(profile, answersRecordedAgainst(profile), questionsWritten());
    }

    /**
     * Whether a label file exists to be answered.
     *
     * <p>Asked rather than inferred from the profile, because every value being set is not the same
     * as the questions having been written: a seed folder that produced no usable text leaves stage 5
     * gated with the profile complete, and an operator sent to a file nothing wrote finds {@code
     * vespera label} refusing for want of it.
     */
    private boolean questionsWritten() {
        return Files.exists(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));
    }

    /**
     * How many answers stand against the seed set this profile names, and none where it names no
     * folder — there is no seed set for an answer to be about yet.
     *
     * <p>Canonicalised the way every other reader of the seed folder does it (ADR-051), because a
     * label is keyed by the seed set as a path and two spellings of one folder are two seed sets.
     */
    private int answersRecordedAgainst(Profile profile) {
        if (!profile.seedFolder().isSet()) {
            return 0;
        }
        try {
            return relevanceLabels.countFor(
                    Walk.canonicalRoot(Path.of(profile.seedFolder().value())).toString());
        } catch (IllegalArgumentException cannotBeResolved) {
            // A seed folder naming nothing that is a directory, swallowed for the reason SeedGate
            // swallows it: census already recorded why it could not walk it (ADR-064), and raising
            // here would end an invocation that succeeded with a stack trace over a typo. There is no
            // seed set for an answer to have been given about, so there are no answers.
            return 0;
        }
    }

    /**
     * The closing line for an invocation that ends with this profile and this many answers recorded
     * against the seed set.
     *
     * <p>Those two are every input the next action depends on. The threshold is the one value whose
     * turn is not decided by the profile alone: unset with nothing answered, the next act is
     * labelling, and unset with answers recorded it is the number itself — so the count is what tells
     * the two apart.
     *
     * @param profile the profile as it stands after the invocation
     * @param answersRecorded how many relevance labels are recorded against the seed set
     */
    static String line(Profile profile, int answersRecorded, boolean questionsWritten) {
        List<String> unsetRunValues = unsetRunValues(profile);
        if (!unsetRunValues.isEmpty()) {
            return whatIsSet(profile, answersRecorded) + " Next: write "
                    + listed(unsetRunValues) + " into " + PROFILE + ", and run again.";
        }
        if (isUnreadable(profile.relevanceScoreFloor())) {
            return "Every run value is set, but relevanceScoreFloor reads "
                    + quoted(profile.relevanceScoreFloor().value().trim())
                    + ", which is not a number, so this run ignored it and removed nothing. Next: write"
                    + " a score on the scale " + RelevanceLabellingReport.FILE_NAME + " reports into"
                    + " relevanceScoreFloor in " + PROFILE + ", and run again.";
        }
        if (profile.relevanceScoreFloor().isSet()) {
            return "Every value the profile asks for is answered, including relevanceScoreFloor."
                    + " Nothing is left to set.";
        }
        if (!questionsWritten) {
            return whatIsSet(profile, answersRecorded) + " No questions were written this invocation,"
                    + " so there is nothing to answer yet. Next: fix what the gated line above names,"
                    + " and run again.";
        }
        if (answersRecorded == 0) {
            return whatIsSet(profile, answersRecorded) + " Next: answer the questions in "
                    + RelevanceLabelFile.FILE_NAME + " -- " + RelevanceLabellingReport.FILE_NAME
                    + " says what each one is asking and what your answers are for -- then record them"
                    + " with vespera label.";
        }
        return whatIsSet(profile, answersRecorded) + " Next: read a number off "
                + RelevanceLabellingReport.FILE_NAME + ", which says what each candidate cut would keep"
                + " and discard, write it into relevanceScoreFloor in " + PROFILE + " with how you"
                + " arrived at it, and run again.";
    }

    /**
     * Whether a threshold is written but unreadable - set, and not a number.
     *
     * <p>{@link RelevanceFloor} parses the same value and falls back to treating it as unset, so a
     * comma written for a decimal point silently removes nothing. Reported rather than passed over: a
     * value an operator wrote and the engine ignored is the one state where a closing line saying
     * nothing is left to set would contradict the step line above it.
     */
    private static boolean isUnreadable(ProfileValue floor) {
        if (!floor.isSet()) {
            return false;
        }
        try {
            Double.parseDouble(floor.value().trim());
            return false;
        } catch (NumberFormatException notANumber) {
            return true;
        }
    }

    /** What the operator actually wrote, quoted so a stray comma or space is visible as one. */
    private static String quoted(String written) {
        return '"' + written + '"';
    }

    /**
     * The half of the line that reports state: which values are answered, how many questions have
     * been, and what is still unset.
     *
     * <p>It credits what the operator has already written, because the alternative is telling someone
     * who took step zero that nothing is answered — and a line that is wrong about what it can see is
     * not worth reading for what it cannot.
     *
     * <p>The threshold is reported as unset only once it is the value actually wanted next. Listing it
     * beside values that are wanted first invites an operator to go and set it, which costs the
     * invocation ADR-098's four exists to save.
     */
    private static String whatIsSet(Profile profile, int answersRecorded) {
        List<String> set = setRunValues(profile);
        List<String> unset = new ArrayList<>(unsetRunValueKeys(profile));
        if (unset.isEmpty() && !profile.relevanceScoreFloor().isSet()) {
            unset.add("relevanceScoreFloor");
        }
        String answers = answersRecorded == 0
                ? ""
                : ", and " + answersRecorded + (answersRecorded == 1 ? " answer is" : " answers are")
                        + " recorded";
        if (set.isEmpty()) {
            return "No value in the profile is answered yet" + answers + ".";
        }
        return listed(set) + (set.size() == 1 ? " is" : " are") + " set" + answers
                + "; " + listed(unset) + (unset.size() == 1 ? " is" : " are") + " not.";
    }

    /** The values a {@code vespera run} needs, in the order an operator can supply them. */
    private static List<RunValue> runValues(Profile profile) {
        return List.of(
                new RunValue("seedFolder", profile.seedFolder(),
                        "the folder of documents you already know are relevant"),
                new RunValue("boilerplateDocumentFrequencyFloor", profile.boilerplateDocumentFrequencyFloor(),
                        "read off similarity's shingle_document_frequency and shingle_corpus_size tables,"
                                + " which this invocation measured"),
                new RunValue("embeddingModel", profile.embeddingModel(), "which model turns scoring on"));
    }

    /** The answered keys, named as the profile names them. */
    private static List<String> setRunValues(Profile profile) {
        return runValues(profile).stream()
                .filter(RunValue::isSet)
                .map(RunValue::key)
                .toList();
    }

    /** The unanswered keys on their own, for the half of the line that reports state. */
    private static List<String> unsetRunValueKeys(Profile profile) {
        return runValues(profile).stream()
                .filter(value -> !value.isSet())
                .map(RunValue::key)
                .toList();
    }

    /**
     * The unanswered keys, each with what an operator needs in order to choose it — the action's own
     * subject, and the only half of the line that carries a hint.
     *
     * <p>The seed folder comes first because it is the only one available on day one: the floor is read
     * off a measurement and the model is a choice from outside, but the exemplars are the operator's
     * own knowledge and nothing the tool prints can ask for them in time (ADR-098).
     */
    private static List<String> unsetRunValues(Profile profile) {
        return runValues(profile).stream()
                .filter(value -> !value.isSet())
                .map(value -> value.key() + " (" + value.hint() + ")")
                .toList();
    }

    /** One run value: the key the profile calls it, whether it is answered, and how to choose it. */
    private record RunValue(String key, ProfileValue value, String hint) {

        boolean isSet() {
            return value.isSet();
        }
    }

    /** An English list, because this is a sentence a person reads and not a serialisation. */
    private static String listed(List<String> names) {
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }
}
