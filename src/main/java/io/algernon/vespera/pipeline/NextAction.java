package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(NextAction.class);

    /** Where every value this line names is written, and the only file it ever asks anyone to edit. */
    private static final String PROFILE = "profile.yaml";

    private final ProfileStore profileStore;
    private final RelevanceLabels relevanceLabels;
    private final Ledger ledger;
    private final GenerationModel generationModel;
    private final Path workingDirectory;

    NextAction(
            ProfileStore profileStore,
            RelevanceLabels relevanceLabels,
            Ledger ledger,
            GenerationModel generationModel,
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory) {
        this.profileStore = profileStore;
        this.relevanceLabels = relevanceLabels;
        this.ledger = ledger;
        this.generationModel = generationModel;
        this.workingDirectory = workingDirectory;
    }

    /**
     * The model the next invocation would generate under, or null where none resolves.
     *
     * <p>Swallowed for the reason {@link #answersRecordedAgainst} swallows its own failure: this line
     * is the last thing a successful invocation prints, and a misconfiguration that has not stopped
     * anything yet must not turn it into a stack trace. The run that would actually generate refuses
     * on its own, before minting anything (ADR-114).
     *
     * <p><b>Swallowed is not the same as silent.</b> ADR-114 decides that a blanked default is not a
     * gate and earns no clause on this line; it does not decide that the operator learns nothing until
     * the invocation that would generate dies inside a constructor. The warning is what makes the
     * missing clause legible as a misconfiguration rather than as a line that happens to be shorter.
     *
     * <p>The catch names {@link NoGenerationModelNamedException} rather than its supertype, so it
     * swallows the one fault it was written for and nothing else the profile read might raise.
     */
    private String generationModelName() {
        try {
            return generationModel.name();
        } catch (NoGenerationModelNamedException nothingNamed) {
            LOG.warn(
                    "No model is named to generate under, so the closing line does not say what the next"
                            + " invocation would write with: {} carries no value and generationModel is"
                            + " unanswered in the profile. The invocation that reaches stage 6b will refuse.",
                    GenerationModel.CONFIGURED_DEFAULT_KEY);
            return null;
        }
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
        return line(profile, answersRecordedAgainst(profile), questionsWritten(), null, generationModelName());
    }

    /**
     * The closing line for an invocation against {@code corpusRoot}, which is the one that can hand
     * over an arrangement to approve.
     *
     * <p>{@code vespera label} has no root and uses {@link #line()}: it ingests answers and arranges
     * nothing, so there is never an arrangement of its making to name.
     */
    String line(Path corpusRoot) {
        Profile profile = profileStore.load();
        return line(
                profile,
                answersRecordedAgainst(profile),
                questionsWritten(),
                arrangementToApprove(corpusRoot).orElse(null),
                generationModelName());
    }

    /**
     * The short name of the arrangement this invocation wrote for {@code corpusRoot}, or empty where
     * nothing has been arranged.
     *
     * <p>Read off the ledger at the end rather than handed down from the step, for the reason the rest
     * of this class is: a line assembled from what each step happened to see is a line that can
     * disagree with what was actually written.
     *
     * <p>The run written last, never the greatest — a run id is a hash of what the run consumed, so
     * sorting by it would hand the operator whichever arrangement happened to hash highest. The value
     * on this line has to be the one on the page they were just told to read (ADR-107).
     */
    private Optional<String> arrangementToApprove(Path corpusRoot) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(corpusRoot))
                .flatMap(walk -> ledger.latestRunFor(ArrangementRun.STAGE, walk))
                .map(ArrangementGate::shortNameOf);
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
     * @param arrangementToApprove the short name of the arrangement this invocation wrote, or {@code
     *     null} where none was. It is a parameter rather than a profile key because an approval can only be
     *     asked for once the thing it is about exists: naming it any earlier would ask the operator
     *     for a value they have no way to supply (ADR-107).
     */
    static String line(
            Profile profile,
            int answersRecorded,
            boolean questionsWritten,
            String arrangementToApprove,
            String generationModel) {
        List<String> stillWanted = runValuesStillWanted(profile);
        if (!stillWanted.isEmpty()) {
            return whatIsSet(profile, answersRecorded) + " Next: write "
                    + listed(stillWanted) + " into " + PROFILE + ", and run again.";
        }
        if (profile.relevanceScoreFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return "Every run value is set, but relevanceScoreFloor reads " + quoted(unreadable.text())
                    + ", which is not a number, so this run ignored it and removed nothing. Next: write"
                    + " a score on the scale " + RelevanceLabellingReport.FILE_NAME + " reports into"
                    + " relevanceScoreFloor in " + PROFILE + ", and run again.";
        }
        Optional<String> otherUnreadable = theConfidenceFloorUnreadable(profile);
        if (otherUnreadable.isPresent()) {
            return otherUnreadable.get();
        }
        if (profile.relevanceScoreFloor().isSet() && !profile.arrangementApproved().isSet()) {
            if (arrangementToApprove == null) {
                return "Every value the profile asks for is answered, including relevanceScoreFloor."
                        + " Nothing was arranged this invocation, so there is nothing to approve yet."
                        + " Next: fix what the gated line above names, and run again.";
            }
            return "Every value the profile asks for is answered, and the documents are"
                    + " arranged. Next: read " + ArrangementTasklet.ARRANGEMENT_FILE_NAME
                    + ", and if that arrangement is the one you want, write " + quoted(arrangementToApprove)
                    + " into arrangementApproved in " + PROFILE + " -- with what you checked in"
                    + " provenance beside it -- and run again" + writtenWith(generationModel) + ".";
        }
        if (profile.relevanceScoreFloor().isSet()) {
            return "Every value the profile asks for is answered, including the arrangement you"
                    + " approved. Nothing is left to set.";
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
     * The line for the conversion-quality floor, where it holds something no number can be read from —
     * empty otherwise.
     *
     * <p><b>Every numeric key is reported, not just the relevance floor</b> (ADR-120). Before it, a
     * mistyped value in this key stopped the application from starting, which at least made the mistake
     * impossible to miss. Now that it is ignored the way the relevance floor always was, saying so is
     * what keeps "ignored" from meaning "silently dropped".
     *
     * <p>It needs a branch of its own because it is not a run value: the other two numeric keys are
     * reported by {@link #runValuesStillWanted}, which since ADR-120 counts an unreadable one as unanswered
     * and quotes back what is written there. This key is named by nothing else, so it is named here.
     */
    private static Optional<String> theConfidenceFloorUnreadable(Profile profile) {
        if (profile.degenerateOutputConfidenceFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but degenerateOutputConfidenceFloor reads "
                    + quoted(unreadable.text()) + ", which is not a number, so this run ignored it and"
                    + " removed nothing for poor conversion quality. Next: write a score between 0 and 1"
                    + " into degenerateOutputConfidenceFloor in " + PROFILE + ", and run again.");
        }
        return Optional.empty();
    }

    /**
     * The clause naming the model the next invocation will generate under (ADR-114), on the one line
     * where that is about to happen.
     *
     * <p>It is here because the generation model is the only thing about the next invocation the
     * operator is never asked to supply: every other value this line names is one they write, and a
     * default they never chose would otherwise reach them only afterwards, as provenance on a
     * deliverable already written. Naming it is disclosure before the call is spent, and it is still a
     * value rather than a stage, which is what ADR-098 asks of everything on this line.
     *
     * <p>Empty where nothing resolved, so a misconfigured default costs this line one clause rather
     * than replacing a successful invocation's last words with a stack trace.
     */
    private static String writtenWith(String generationModel) {
        return generationModel == null || generationModel.isBlank()
                ? ""
                : " to write the connecting text with " + generationModel;
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
     *
     * <p><b>A key holding something no number can be read from belongs to neither group</b> (ADR-120),
     * and gets a clause of its own. Counting it as set would credit the operator with a floor the run
     * ignored; calling it unset would tell someone who wrote something that they wrote nothing, which
     * is the one reading {@code CONTEXT.md} rules out for this state. It is a third thing, and the only
     * sentence that is true of it says what is actually sitting in the file.
     */
    private static String whatIsSet(Profile profile, int answersRecorded) {
        List<String> set = setRunValues(profile);
        List<String> unreadable = unreadableRunValues(profile);
        List<String> unset = new ArrayList<>(unsetRunValueKeys(profile));
        if (unset.isEmpty() && unreadable.isEmpty() && !profile.relevanceScoreFloor().isSet()) {
            unset.add("relevanceScoreFloor");
        }
        String answers = answersRecorded == 0
                ? ""
                : ", and " + answersRecorded + (answersRecorded == 1 ? " answer is" : " answers are")
                        + " recorded";
        String mistyped = unreadable.isEmpty() ? "" : "; " + listed(unreadable);
        if (set.isEmpty()) {
            return "No value in the profile is answered yet" + answers + mistyped + ".";
        }
        String notSet = unset.isEmpty()
                ? ""
                : "; " + listed(unset) + (unset.size() == 1 ? " is" : " are") + " not";
        return listed(set) + (set.size() == 1 ? " is" : " are") + " set" + answers + notSet + mistyped + ".";
    }

    /**
     * The run values holding something no number can be read from, each as a whole clause saying what
     * is there — because the key alone would leave a reader guessing which of the three states it is in.
     */
    private static List<String> unreadableRunValues(Profile profile) {
        return runValues(profile).stream()
                .filter(RunValue::isUnreadable)
                .map(value -> value.key() + " reads " + quoted(value.writtenText()) + ", which is not a number")
                .toList();
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
                .filter(value -> !value.isSet() && !value.isUnreadable())
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
     *
     * <p><b>Named for what it returns rather than for "unset"</b>, because since ADR-120 it is not the
     * same set as {@link #unsetRunValueKeys}: a key holding something unreadable is wanted here and is
     * deliberately absent there, since the state half names it in its own clause instead. Two methods
     * whose names both said "unset" while returning different sets is an invitation to reconcile them
     * in the wrong direction.
     */
    private static List<String> runValuesStillWanted(Profile profile) {
        return runValues(profile).stream()
                .filter(value -> !value.isSet())
                .map(value -> value.key() + " (" + value.hint() + ")")
                .toList();
    }

    /** One run value: the key the profile calls it, whether it is answered, and how to choose it. */
    private record RunValue(String key, ProfileValue value, String hint) {

        /**
         * Whether the next invocation can actually act on this value.
         *
         * <p><b>An unreadable number is not an answer here</b> (ADR-120), though {@link
         * ProfileValue#isSet()} says it is one. Both are true of it: somebody answered, and nothing can
         * use what they wrote. This line is about what the tool can use, so counting it as answered
         * would print "boilerplateDocumentFrequencyFloor is set" over a value the run just ignored --
         * and would leave the operator no line telling them so.
         */
        boolean isSet() {
            if (value instanceof NumericValue numeric) {
                return numeric.reading() instanceof NumericValue.Answered;
            }
            return value.isSet();
        }

        /** Whether somebody answered this key with something no number can be read from (ADR-120). */
        boolean isUnreadable() {
            return value instanceof NumericValue numeric
                    && numeric.reading() instanceof NumericValue.Unreadable;
        }

        /**
         * What is actually written there, for the two places that quote it back. Empty for a key in any
         * other state, which neither caller asks.
         */
        String writtenText() {
            return value instanceof NumericValue numeric
                            && numeric.reading() instanceof NumericValue.Unreadable unreadable
                    ? unreadable.text()
                    : "";
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
