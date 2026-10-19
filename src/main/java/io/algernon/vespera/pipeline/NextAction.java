package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.LanguagesValue;
import io.algernon.vespera.profile.NameEndingsValue;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.deliverable.CodeSetNames;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.SynthesisDocs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<Clusters> clusters;
    private final ObjectProvider<SynthesisDocs> synthesisDocs;
    private final ObjectProvider<ArrangementGate> arrangementGate;
    private final Path workingDirectory;
    private final DeliverableRoot deliverableRoot;

    NextAction(
            ProfileStore profileStore,
            RelevanceLabels relevanceLabels,
            Ledger ledger,
            GenerationModel generationModel,
            ObjectProvider<Clusters> clusters,
            ObjectProvider<SynthesisDocs> synthesisDocs,
            ObjectProvider<ArrangementGate> arrangementGate,
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory,
            @Value("${" + VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY + ":}") String deliverableRoot) {
        this.profileStore = profileStore;
        this.deliverableRoot = DeliverableRoot.of(deliverableRoot);
        this.relevanceLabels = relevanceLabels;
        this.ledger = ledger;
        this.generationModel = generationModel;
        this.clusters = clusters;
        this.synthesisDocs = synthesisDocs;
        this.arrangementGate = arrangementGate;
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
        Closing closing = closingAfterRecordingAnswers(
                profile, answersRecordedAgainst(profile), questionsWritten(), generationModelName());
        NextInvocationFile.write(workingDirectory, closing.next());
        return closing.line();
    }

    /**
     * What the closing line says, with what the file beside it holds, for {@code vespera label}: the one decision
     * {@link #line()} makes, apart from the profile and the ledger it reads.
     */
    static Closing closingAfterRecordingAnswers(
            Profile profile, int answersRecorded, boolean questionsWritten, String generationModel) {
        if (profile.relevanceScoreFloor().isSet() && anEarlierValueWanted(profile, answersRecorded).isEmpty()) {
            return afterRecordingAnswers(profile);
        }
        // No root and no invocation record: there is no arrangement of this invocation's own to match
        // an approval against, so there is nothing for the gate's rule to say yes to (ADR-154 §2).
        return closing(profile, answersRecorded, questionsWritten, null, generationModel, false);
    }

    /** The closing line and the contents of {@code next-invocation.properties} that one branch gives (ADR-294 §1). */
    record Closing(String line, NextInvocationFile.Contents next) {}

    private static Closing runWanting(String line, String... keys) {
        return new Closing(line, new NextInvocationFile.Contents(
                NextInvocationFile.Invocation.RUN, List.of(keys), false, Optional.empty()));
    }

    /**
     * {@code vespera label}'s line once the relevance floor is set (ADR-154 §2).
     *
     * <p>Recording answers arranges nothing and prints no gate line, so this line may neither point at a
     * gated line above it nor say an approval matches, since it checked none. It says what the next
     * {@code vespera run} will do instead.
     */
    private static Closing afterRecordingAnswers(Profile profile) {
        if (!profile.arrangementApproved().isSet()) {
            return runWanting("Every value the profile asks for is answered, including relevanceScoreFloor."
                    + " Next: run vespera run, which arranges the documents and writes "
                    + ArrangementTasklet.ARRANGEMENT_FILE_NAME + " for you to approve.");
        }
        return runWanting("Every value the profile asks for is answered, and arrangementApproved holds "
                + quoted(profile.arrangementApproved().value()) + ". Next: run vespera run, which checks that"
                + " value against the arrangement the documents are in then and asks for a new approval if"
                + " they differ.");
    }

    /**
     * The closing line for an invocation against {@code corpusRoot}, which is the one that can hand
     * over an arrangement to approve.
     *
     * <p>{@code vespera label} has no root and uses {@link #line()}: it ingests answers and arranges
     * nothing, so there is never an arrangement of its making to name.
     *
     * <p>{@code invocationRuns} is what the invocation recorded (ADR-154 §1) — the run of the
     * arrangement and generation stages this invocation minted or continued, if either ran. This is
     * never the latest run of either stage, and that is the point (ADR-154 §2): a re-arrangement over a
     * reused walk (ADR-115) leaves an older arrangement still standing in the walk's history, and only
     * this invocation's own arrangement is the one the page just written and the closing line agree on.
     */
    String line(Path corpusRoot, InvocationRuns invocationRuns) {
        Profile profile = profileStore.load();
        Optional<RunId> arrangement = invocationRuns.runOf(StageModules.ARRANGEMENT.stage());
        Optional<RunId> generation = invocationRuns.runOf(StageModules.GENERATION.stage());
        boolean approvalMatchesThisInvocation =
                arrangementGate.getObject().approvedArrangement(arrangement).isPresent();
        Closing closing = closing(
                profile,
                answersRecordedAgainst(profile),
                questionsWritten(),
                arrangement.map(ArrangementGate::shortNameOf).orElse(null),
                generationModelName(),
                deliverableFor(generation, invocationRuns).orElse(null),
                approvalMatchesThisInvocation);
        NextInvocationFile.write(workingDirectory, closing.next());
        return closing.line();
    }

    /**
     * The deliverable this invocation's corpus has, if a generation run stands for this invocation — and, since
     * every invocation that reaches the tree draws it (ADR-274 §6), the count of documents not copied in the one it
     * drew, which the generation step handed over in the job's execution context (ADR-274 §7).
     */
    private Optional<DeliverableSummary> deliverableFor(Optional<RunId> generation, InvocationRuns invocationRuns) {
        return generation.flatMap(this::summarize).map(summary -> new DeliverableSummary(
                summary.tree(),
                summary.clustersLeftUnwritten(),
                invocationRuns.documentsNotCopied(),
                invocationRuns.documentsFolderLeftBehind(),
                invocationRuns.foldersLeftBehind(),
                invocationRuns.filesLeftBehind(),
                invocationRuns.codeFilesNotCopied(),
                invocationRuns.dormantCodeSetNames()));
    }

    /**
     * The tree a generation run left, and how many of its clusters carry no writing: every cluster the
     * arrangement it read gave, less every one a call actually produced (ADR-111) — faulted and
     * unsendable both, since neither carries a {@code synthesis_doc} row.
     */
    private Optional<DeliverableSummary> summarize(RunId generation) {
        Clusters clustersBean = clusters.getIfAvailable();
        SynthesisDocs synthesisDocsBean = synthesisDocs.getIfAvailable();
        if (clustersBean == null || synthesisDocsBean == null) {
            // Neither is wired into every context that carries this class -- a handful of narrower
            // invocation tests wire only what their own claim is about (ClosingLineWithoutAGenerationModelTest
            // names its own four beans in as many words). None of them reach a state where a
            // generation run stands, so this branch is never asked to count against one for real. A
            // fabricated zero here would print a real path beside a false claim that nothing is
            // unwritten, so the whole summary is withheld instead: the caller's fall-through already
            // renders a correct, shorter line.
            return Optional.empty();
        }
        Path tree = deliverableRoot.treeOf(workingDirectory, generation.value());
        List<RunId> upstream = ledger.runs().upstreamRuns(generation);
        if (upstream.size() != 1) {
            throw new IllegalStateException("generation run " + generation.value() + " records "
                    + upstream.size() + " upstream runs; exactly one arrangement is expected");
        }
        int arranged = clustersBean.countForRun(upstream.getFirst());
        int written = synthesisDocsBean.countForRun(generation);
        return Optional.of(new DeliverableSummary(
                tree, Math.max(0, arranged - written), OptionalInt.empty(), false, 0, 0, OptionalInt.empty(), 0));
    }

    /**
     * What the closing line says about the deliverable: where its index is, how many of its clusters carry
     * no writing, and how many documents were not copied into the tree this invocation wrote.
     *
     * @param tree the tree's own path
     * @param clustersLeftUnwritten how many clusters of the arrangement carry no writing
     * @param documentsNotCopied how many documents' copies do not stand in the tree this invocation wrote, or empty
     *     where this invocation wrote none (ADR-274 §7)
     * @param documentsFolderLeftBehind whether the tree holds a {@code documents/} folder an earlier layout wrote and
     *     this drawing did not (ADR-280 §4)
     * @param foldersLeftBehind how many other folders this drawing did not write the tree holds, at its top or in a
     *     partition folder this drawing wrote (ADR-280 §4)
     * @param filesLeftBehind how many files an earlier drawing left in the cluster folders this drawing wrote (ADR-286
     *     §4)
     * @param codeFilesNotCopied how many files of code have no copy standing in the tree this invocation wrote, or
     *     empty where the tree has no code set or this invocation wrote none (ADR-296 §6.1)
     * @param dormantCodeSetNames how many entries of {@code code-set-names.yaml} name no code set of the run (ADR-296
     *     §6.2)
     */
    record DeliverableSummary(
            Path tree,
            int clustersLeftUnwritten,
            OptionalInt documentsNotCopied,
            boolean documentsFolderLeftBehind,
            int foldersLeftBehind,
            int filesLeftBehind,
            OptionalInt codeFilesNotCopied,
            int dormantCodeSetNames) {

        /** A summary of a tree with no code set and no dormant name. */
        DeliverableSummary(
                Path tree,
                int clustersLeftUnwritten,
                OptionalInt documentsNotCopied,
                boolean documentsFolderLeftBehind,
                int foldersLeftBehind,
                int filesLeftBehind) {
            this(
                    tree,
                    clustersLeftUnwritten,
                    documentsNotCopied,
                    documentsFolderLeftBehind,
                    foldersLeftBehind,
                    filesLeftBehind,
                    OptionalInt.empty(),
                    0);
        }
    }

    /**
     * What the line says of code, to follow the count of documents not copied (ADR-296 §6): the files of code not
     * copied where the tree has a code set, even at none, and then the entries of the names file that name no set of
     * the run, whether or not it has one. Nothing where this invocation drew no tree.
     */
    private static String ofCode(DeliverableSummary deliverable) {
        if (deliverable.documentsNotCopied().isEmpty()) {
            return "";
        }
        String files = "";
        if (deliverable.codeFilesNotCopied().isPresent()) {
            int count = deliverable.codeFilesNotCopied().getAsInt();
            files = ", and " + count + (count == 1 ? " file" : " files") + " of code not copied";
        }
        int dormant = deliverable.dormantCodeSetNames();
        String names = dormant == 0
                ? ""
                : "; " + dormant + (dormant == 1 ? " name" : " names") + " in " + CodeSetNames.FILE_NAME
                        + (dormant == 1 ? " names" : " name") + " no code set of this run";
        return files + names;
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
     * @param approvalMatchesThisInvocation whether the approval opens the gate on the arrangement this
     *     invocation made or continued, per {@link ArrangementGate#approvedArrangement} -- the caller's
     *     to supply, since only the gate's own rule (a prefix of the full id) decides it (ADR-154 §2).
     */
    static String line(
            Profile profile,
            int answersRecorded,
            boolean questionsWritten,
            String arrangementToApprove,
            String generationModel,
            boolean approvalMatchesThisInvocation) {
        return closing(profile, answersRecorded, questionsWritten, arrangementToApprove, generationModel,
                approvalMatchesThisInvocation).line();
    }

    /** {@link #line(Profile, int, boolean, String, String, boolean)} with what the file beside it holds. */
    static Closing closing(
            Profile profile,
            int answersRecorded,
            boolean questionsWritten,
            String arrangementToApprove,
            String generationModel,
            boolean approvalMatchesThisInvocation) {
        return closing(
                profile,
                answersRecorded,
                questionsWritten,
                arrangementToApprove,
                generationModel,
                null,
                approvalMatchesThisInvocation);
    }

    /**
     * What the line says of what an earlier drawing left in the tree, to follow the count of documents not copied and
     * precede the line's last full stop (ADR-280 §4, ADR-286 §4): {@code documents/} named, any other folder and any
     * file counted and not named, in one clause, and nothing where nothing was left or this invocation drew no tree.
     */
    private static String leftBehind(DeliverableSummary deliverable) {
        if (deliverable.documentsNotCopied().isEmpty()) {
            return "";
        }
        String documents = deliverable.documentsFolderLeftBehind()
                ? "documents/ holds an earlier layout's copies and can be deleted by hand"
                : "";
        int others = deliverable.foldersLeftBehind();
        int files = deliverable.filesLeftBehind();
        String counted = (others == 0 ? "" : others + (others == 1 ? " folder" : " folders"))
                + (others != 0 && files != 0 ? " and " : "")
                + (files == 0 ? "" : files + (files == 1 ? " file" : " files"));
        String earlier = counted.isEmpty() ? "" : counted + " left by an earlier drawing can be deleted by hand";
        if (documents.isEmpty() && earlier.isEmpty()) {
            return "";
        }
        return "; " + documents + (documents.isEmpty() || earlier.isEmpty() ? "" : ", and ") + earlier;
    }

    /**
     * {@link #line(Profile, int, boolean, String, String, boolean)}, with the deliverable this
     * invocation's corpus already has, where there is one (ADR-103, ADR-111, #186) — empty for {@code
     * vespera label}, which arranges nothing, and for every state before the arrangement is approved,
     * where naming a tree would be naming one that cannot exist yet.
     */
    static String line(
            Profile profile,
            int answersRecorded,
            boolean questionsWritten,
            String arrangementToApprove,
            String generationModel,
            DeliverableSummary deliverable,
            boolean approvalMatchesThisInvocation) {
        return closing(profile, answersRecorded, questionsWritten, arrangementToApprove, generationModel,
                deliverable, approvalMatchesThisInvocation).line();
    }

    /**
     * {@link #line(Profile, int, boolean, String, String, DeliverableSummary, boolean)} with what the file beside it
     * holds (ADR-294 §2): the branch that words the line gives the contents, so the two cannot disagree.
     */
    static Closing closing(
            Profile profile,
            int answersRecorded,
            boolean questionsWritten,
            String arrangementToApprove,
            String generationModel,
            DeliverableSummary deliverable,
            boolean approvalMatchesThisInvocation) {
        Optional<Closing> earlier = anEarlierValueWanted(profile, answersRecorded);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        if (profile.relevanceScoreFloor().isSet()) {
            if (arrangementToApprove == null) {
                // Approval set, and no arrangement this invocation, and approval unset with no
                // arrangement this invocation are one outcome (ADR-154 §2): in each case no arrangement
                // has been approved, because none exists yet for this invocation to approve.
                return runWanting("Every value the profile asks for is answered, including relevanceScoreFloor."
                        + " Nothing was arranged this invocation, so there is nothing to approve yet."
                        + " Next: fix what the gated line above names, and run again.");
            }
            if (!profile.arrangementApproved().isSet()) {
                return approvalWanted("Every value the profile asks for is answered, and the documents are"
                        + " arranged. Next: read " + ArrangementTasklet.ARRANGEMENT_FILE_NAME
                        + ", and if that arrangement is the one you want, write " + quoted(arrangementToApprove)
                        + " into arrangementApproved in " + PROFILE + " -- with what you checked in"
                        + " provenance beside it -- and run again" + writtenWith(generationModel) + ".",
                        arrangementToApprove);
            }
            if (!approvalMatchesThisInvocation) {
                // The approval was set, but names an arrangement this invocation did not arrive at --
                // an older one of this walk, or a typo (ADR-154 §2). No deliverable is named: no
                // write-up happened this invocation, and an earlier arrangement's tree is not this
                // one's.
                return approvalWanted("Every value the profile asks for is answered, but arrangementApproved holds "
                        + quoted(profile.arrangementApproved().value()) + ", which is not the arrangement"
                        + " the documents are in now. Next: read " + ArrangementTasklet.ARRANGEMENT_FILE_NAME
                        + ", and if that arrangement is the one you want, write " + quoted(arrangementToApprove)
                        + " into arrangementApproved in " + PROFILE + " -- with what you checked in"
                        + " provenance beside it -- and run again" + writtenWith(generationModel) + ".",
                        arrangementToApprove);
            }
            if (deliverable == null) {
                return nothingLeft("Every value the profile asks for is answered, including the arrangement you"
                        + " approved. Nothing is left to set.");
            }
            String notCopied = deliverable.documentsNotCopied().isEmpty()
                    ? ""
                    : " and " + deliverable.documentsNotCopied().getAsInt() + " "
                            + (deliverable.documentsNotCopied().getAsInt() == 1 ? "document" : "documents")
                            + " not copied";
            return nothingLeft("Every value the profile asks for is answered, including the arrangement you"
                    + " approved. Nothing is left to set. The deliverable is at "
                    + deliverable.tree().resolve(Deliverable.INDEX_FILE_NAME)
                    + ", with " + deliverable.clustersLeftUnwritten() + " "
                    + (deliverable.clustersLeftUnwritten() == 1 ? "group" : "groups") + " left unwritten"
                    + notCopied + ofCode(deliverable) + leftBehind(deliverable) + ".");
        }
        if (!questionsWritten) {
            return runWanting(whatIsSet(profile, answersRecorded) + " No questions were written this invocation,"
                    + " so there is nothing to answer yet. Next: fix what the gated line above names,"
                    + " and run again.");
        }
        if (answersRecorded == 0) {
            return new Closing(whatIsSet(profile, answersRecorded) + " Next: answer the questions in "
                    + RelevanceLabelFile.FILE_NAME + " -- " + RelevanceLabellingReport.FILE_NAME
                    + " says what each one is asking and what your answers are for -- then record them"
                    + " with vespera label.",
                    new NextInvocationFile.Contents(
                            NextInvocationFile.Invocation.LABEL, List.of(), true, Optional.empty()));
        }
        return runWanting(whatIsSet(profile, answersRecorded) + " Next: read a number off "
                + RelevanceLabellingReport.FILE_NAME + ", which says what each candidate cut would keep"
                + " and discard, write it into relevanceScoreFloor in " + PROFILE + " with how you"
                + " arrived at it, and run again.", "relevanceScoreFloor");
    }

    /** The line that asks for the approval of {@code arrangement}, with the file naming it (ADR-294 §2). */
    private static Closing approvalWanted(String line, String arrangement) {
        return new Closing(line, new NextInvocationFile.Contents(
                NextInvocationFile.Invocation.RUN, List.of("arrangementApproved"), false, Optional.of(arrangement)));
    }

    /** The line that says nothing is left to set, with a file that offers no next command (ADR-294 §2). */
    private static Closing nothingLeft(String line) {
        return new Closing(line, new NextInvocationFile.Contents(
                NextInvocationFile.Invocation.NONE, List.of(), false, Optional.empty()));
    }

    /**
     * The line for a value wanted before the relevance floor's own outcome can be told — a run value
     * unset or unreadable, or a numeric floor holding no number — empty where there is none. Shared by
     * both invocations' lines, so {@code vespera label} names such a value exactly as {@code vespera run}
     * does.
     */
    private static Optional<Closing> anEarlierValueWanted(Profile profile, int answersRecorded) {
        List<String> stillWanted = runValuesStillWanted(profile);
        if (!stillWanted.isEmpty()) {
            return Optional.of(runWanting(whatIsSet(profile, answersRecorded) + " Next: write "
                    + listed(stillWanted) + " into " + PROFILE + ", and run again.",
                    runValues(profile).stream().filter(value -> !value.isSet()).map(RunValue::key)
                            .toArray(String[]::new)));
        }
        if (profile.relevanceScoreFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return Optional.of(runWanting("Every run value is set, but relevanceScoreFloor reads "
                    + quoted(unreadable.text())
                    + ", which is not a number, so this run ignored it and removed nothing. Next: write"
                    + " a score on the scale " + RelevanceLabellingReport.FILE_NAME + " reports into"
                    + " relevanceScoreFloor in " + PROFILE + ", and run again.", "relevanceScoreFloor"));
        }
        // The one key each of these names, or the two lists where they share an ending (ADR-294 §2), in the
        // order the line takes them.
        Optional<Closing> unreadable = Optional.<Closing>empty()
                .or(() -> theConfidenceFloorUnreadable(profile)
                        .map(line -> runWanting(line, "degenerateOutputConfidenceFloor")))
                .or(() -> theWordFloorIgnored(profile)
                        .map(line -> runWanting(line, DegenerateOutputWordFloor.KEY)))
                .or(() -> theLogFloorUnreadable(profile)
                        .map(line -> runWanting(line, "logTimestampShareFloor")))
                .or(() -> theNameEndingsUnreadable(profile)
                        .map(line -> runWanting(line, "outOfScopeNameEndings")))
                .or(() -> theCodeNameEndingsUnreadable(profile)
                        .map(line -> runWanting(line, "codeNameEndings")))
                .or(() -> theSharedNameEndings(profile)
                        .map(line -> runWanting(line, "outOfScopeNameEndings", "codeNameEndings")))
                .or(() -> theConfigurationFloorUnreadable(profile)
                        .map(line -> runWanting(line, "configurationSettingShareFloor")))
                .or(() -> theTranslationFloorUnreadable(profile)
                        .map(line -> runWanting(line, "translationSimilarityFloor")))
                .or(() -> thePreferredLanguagesUnreadable(profile)
                        .map(line -> runWanting(line, "preferredLanguages")))
                .or(() -> theExtractionAttemptIgnored(profile)
                        .map(line -> runWanting(line, ExtractionAttempt.KEY)));
        return unreadable;
    }

    // The line for translationSimilarityFloor (ADR-271 section 8), in the shape of logTimestampShareFloor's:
    // empty unless it holds something no number can be read from. Unset is not reported.
    private static Optional<String> theTranslationFloorUnreadable(Profile profile) {
        if (profile.translationSimilarityFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but translationSimilarityFloor reads "
                    + quoted(unreadable.text()) + ", which is not a number, so this run ignored it and applied"
                    + " no likeness between languages. Next: write a similarity between 0 and 1 into"
                    + " translationSimilarityFloor in " + PROFILE + ", read off " + TranslationTasklet.PAGE_FILE_NAME
                    + ", and run again.");
        }
        return Optional.empty();
    }

    // The line for preferredLanguages (ADR-271 section 8): empty unless a piece of it is not a two-letter code.
    private static Optional<String> thePreferredLanguagesUnreadable(Profile profile) {
        if (profile.preferredLanguages().reading() instanceof LanguagesValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but preferredLanguages reads "
                    + quoted(unreadable.text()) + ", which is not a list of two-letter language codes, so this"
                    + " run ignored it and removed no copy in another language. Next: write the languages to keep"
                    + " into preferredLanguages in " + PROFILE + ", most preferred first, as two-letter codes"
                    + " separated by spaces or commas, and run again.");
        }
        return Optional.empty();
    }

    /**
     * The line for {@code outOfScopeNameEndings} (ADR-256 §5), where it holds a piece that is not an ending
     * -- empty otherwise. Unset is not reported: it ships unset, and unset leaves nothing out by its name.
     */
    private static Optional<String> theNameEndingsUnreadable(Profile profile) {
        if (profile.outOfScopeNameEndings().reading() instanceof NameEndingsValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but outOfScopeNameEndings reads "
                    + quoted(unreadable.text()) + ", which is not a list of name endings each beginning with a"
                    + " dot, so this run ignored it and left nothing out for the ending of its name. Next: write"
                    + " the endings into outOfScopeNameEndings in " + PROFILE + ", each beginning with a dot,"
                    + " with no wildcard, and separated by spaces, and run again.");
        }
        return Optional.empty();
    }

    /**
     * The line for {@code degenerateOutputWordFloor} (ADR-282 section 3), where it holds something that is not
     * a whole number of 0 or more -- empty otherwise. Unset is not reported: it ships unset and removes
     * nothing. The reading is {@link DegenerateOutputWordFloor}'s, the one stage 2's identity uses.
     */
    private static Optional<String> theWordFloorIgnored(Profile profile) {
        if (DegenerateOutputWordFloor.of(profile.degenerateOutputWordFloor())
                instanceof DegenerateOutputWordFloor.Ignored ignored) {
            return Optional.of("Every run value is set, but " + DegenerateOutputWordFloor.KEY + " reads "
                    + quoted(ignored.text()) + ", which is not a whole number of 0 or more, so this run ignored"
                    + " it and removed nothing for having too few words. Next: write a whole number of words,"
                    + " read off " + ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME + ", into "
                    + DegenerateOutputWordFloor.KEY + " in " + PROFILE + ", and run again.");
        }
        return Optional.empty();
    }

    /**
     * The line for {@code codeNameEndings} (ADR-277 §2), where it holds a piece that is not an ending --
     * empty otherwise. Unset is not reported: it ships unset, and unset keeps nothing as code for its name.
     */
    private static Optional<String> theCodeNameEndingsUnreadable(Profile profile) {
        if (profile.codeNameEndings().reading() instanceof NameEndingsValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but codeNameEndings reads "
                    + quoted(unreadable.text()) + ", which is not a list of name endings each beginning with a"
                    + " dot, so this run ignored it and kept nothing as code for the ending of its name. Next: write"
                    + " the endings into codeNameEndings in " + PROFILE + ", each beginning with a dot,"
                    + " with no wildcard, and separated by spaces, and run again.");
        }
        return Optional.empty();
    }

    /**
     * The line for an ending both {@code outOfScopeNameEndings} and {@code codeNameEndings} list (ADR-277 §6),
     * empty where none is shared. Several are named in canonical order, separated by commas.
     */
    private static Optional<String> theSharedNameEndings(Profile profile) {
        if (profile.outOfScopeNameEndings().reading() instanceof NameEndingsValue.Answered outOfScope
                && profile.codeNameEndings().reading() instanceof NameEndingsValue.Answered code) {
            List<String> shared = outOfScope.endings().stream().filter(code.endings()::contains).toList();
            if (!shared.isEmpty()) {
                return Optional.of("Every run value is set, but outOfScopeNameEndings and codeNameEndings both list "
                        + String.join(", ", shared) + ", so this run left its files out of scope and kept none of them"
                        + " as code. Next: remove each from the list it does not belong in, in " + PROFILE
                        + ", and run again.");
            }
        }
        return Optional.empty();
    }

    /**
     * The line for {@code extractionAttempt} (ADR-185 §1), where it holds something no attempt is
     * numbered by -- empty otherwise. Unset, {@code 1} and a numbered attempt are all read, so none is
     * reported. The reading is {@link ExtractionAttempt}'s, the one stage 2's identity uses.
     */
    private static Optional<String> theExtractionAttemptIgnored(Profile profile) {
        if (ExtractionAttempt.of(profile.extractionAttempt()) instanceof ExtractionAttempt.Ignored ignored) {
            return Optional.of("Every run value is set, but " + ExtractionAttempt.KEY + " reads "
                    + quoted(ignored.text()) + ", which is not a whole number of 1 or more, so this run"
                    + " ignored it and extracted under the first attempt. Next: write a whole number of 1"
                    + " or more into " + ExtractionAttempt.KEY + " in " + PROFILE + ", or remove it, and run"
                    + " again.");
        }
        return Optional.empty();
    }

    /**
     * The line for {@code configurationSettingShareFloor} (ADR-287 §7), where it holds something no number can be
     * read from -- empty otherwise. An unset floor is not reported: it ships unset, and unset means no rule.
     */
    private static Optional<String> theConfigurationFloorUnreadable(Profile profile) {
        if (profile.configurationSettingShareFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but configurationSettingShareFloor reads "
                    + quoted(unreadable.text()) + ", which is not a number, so this run ignored it and found no"
                    + " configuration file. Next: write a share between 0 and 1 into configurationSettingShareFloor"
                    + " in " + PROFILE + ", and run again.");
        }
        return Optional.empty();
    }

    /**
     * The line for the log floor (ADR-171), where it holds something no number can be read from --
     * empty otherwise. An unset floor is not reported: it ships unset, and unset means no log rule.
     */
    private static Optional<String> theLogFloorUnreadable(Profile profile) {
        if (profile.logTimestampShareFloor().reading() instanceof NumericValue.Unreadable unreadable) {
            return Optional.of("Every run value is set, but logTimestampShareFloor reads "
                    + quoted(unreadable.text()) + ", which is not a number, so this run ignored it and"
                    + " removed no logs. Next: write a share between 0 and 1 into logTimestampShareFloor in "
                    + PROFILE + ", and run again.");
        }
        return Optional.empty();
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
     * <p>It is here because the generation model is the one default this line names before it is used:
     * every other value this line names is one the operator writes, and a default they never chose
     * would otherwise reach them only afterwards, as provenance on a deliverable already written.
     * Naming it is disclosure before the call is spent, and it is still a value rather than a stage,
     * which is what ADR-098 asks of everything on this line. The embedding model is never asked for
     * either (ADR-298 section 5); whether this line names it is left open by ADR-298's "What this
     * does not decide".
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
                        "read off document-frequency.html, which stage 3 writes"));
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
