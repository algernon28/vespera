package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.CheckpointMismatchException;
import io.algernon.vespera.corpus.ExcludesNothingViolationException;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 0: walk the corpus, walk the seed folder if there is one, and bring the profile up to date.
 *
 * <p>A tasklet rather than a chunk-oriented step, because census is the one stage that reads no
 * survivors: it produces the occurrences every later stage reads, so there is no input to chunk. The
 * chunking that matters here is the walk's own commit cadence, which {@code WalkRecorder} owns.
 *
 * <p>The two walks are independent (ADR-064). The seed folder is walked by the same instrument under
 * its own id, and neither walk blocks the other or knows the other happened — the walk row records a
 * root, and a consumer already knows which root it is asking about, so there is no purpose tag to
 * set.
 *
 * <p>Census writes no verdicts, so it mints no run (ADR-048).
 */
@Component
@StepScope
public class CensusTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CensusTasklet.class);

    private final WalkRecorder walkRecorder;
    private final ProfileStore profileStore;
    private final Clock clock;
    private final Path root;

    public CensusTasklet(
            WalkRecorder walkRecorder,
            ProfileStore profileStore,
            Clock clock,
            @Value("#{jobParameters['root']}") Path root) {
        this.walkRecorder = walkRecorder;
        this.profileStore = profileStore;
        this.clock = clock;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("Stage 0 (census) starting for root {}", root);
        Profile profile = profileStore.load();

        // The two walks are independent, so neither is allowed to cost the other its chance to run
        // (ADR-064). A corpus root that cannot be walked is still worth knowing the seed set for, and
        // the invocation fails afterwards either way.
        Walked corpus = walkOrCapture(root, "the corpus");
        Optional<Walked> seed = walkSeedFolderIfNamed(profile);

        profileStore.save(profile.withSeedFolderMeasurement(seedFolderMeasurement(seed)));
        log.info("Census merged the profile at {}", profileStore.file());

        if (corpus.failure() != null) {
            throw corpus.failure();
        }
        if (seed.isPresent() && abortsTheInvocation(seed.get().failure())) {
            throw seed.get().failure();
        }
        // The one failure nothing rethrows: a seed folder that could not be walked for a reason census
        // survives (ADR-064). This is its only report in the log, and the profile carries the same
        // text for whoever reads it there instead.
        if (seed.isPresent() && seed.get().failure() != null) {
            log.warn(
                    "Census could not walk the seed folder at {}, and carried on: {}",
                    profile.seedFolder().value(),
                    causeChain(seed.get().failure()));
        }
        log.info("Stage 0 (census) finished for root {}", root);
        return RepeatStatus.FINISHED;
    }

    /**
     * Whether a failed walk is one no census may outlive, whichever root it was walking.
     *
     * <p>Both of these say the same thing: the ledger may now hold fewer occurrences than the archive
     * holds files. {@link ExcludesNothingViolationException} is that caught at the finish (ADR-056), and
     * {@link CheckpointMismatchException} is a resumed walk about to skip a subtree that has moved under it
     * (ADR-055). Neither has a degraded mode, and neither becomes survivable by having happened to the
     * seed folder rather than the corpus — a seed set quietly missing entries scores relevance against
     * the wrong set, silently, for every stage downstream.
     *
     * <p>Everything else a walk can raise is the seed folder's own business and stays in the profile:
     * a mistyped seed path is an operator's typo, not a reason to lose the corpus census that ran
     * beside it.
     */
    private static boolean abortsTheInvocation(Exception failure) {
        return failure instanceof ExcludesNothingViolationException || failure instanceof CheckpointMismatchException;
    }

    /**
     * Walks a root, returning what went wrong rather than raising it, so the other walk still runs.
     *
     * <p><b>Nothing is logged here.</b> A failure this method captures is reported exactly once, by
     * whoever ends up owning it: {@link #execute} rethrows the ones no census may outlive, and the
     * exception is then the report; the rest are recorded in the profile and logged once, there.
     * Logging at the point of capture as well would report the fatal ones twice — once as a stack
     * nothing had yet decided was fatal, and again as the exception that actually stopped the run.
     */
    private Walked walkOrCapture(Path walkRoot, String what) {
        try {
            WalkId walkId = walkRecorder.walk(walkRoot);
            log.info("Census recorded {} at {} under walk {}", what, walkRoot, walkId.value());
            return new Walked(walkId, null);
        } catch (Exception e) {
            return new Walked(null, e);
        }
    }

    /**
     * Every message in a failure's cause chain, joined — {@code "IllegalArgumentException: root cannot
     * be resolved: C:\seeds; caused by InvalidPathException: Illegal char <?>"}.
     *
     * <p>A survivable failure is never rethrown, so this text is the whole of what anyone will ever
     * learn about it, in the log and in the profile alike. {@link Throwable#getMessage()} alone is not
     * enough for that: the outermost message names what the code was doing, and the cause names what
     * actually went wrong, which is usually the half an operator needs. The stack is deliberately not
     * here — a condition the system handled and recorded is not a crash, and printing one as though it
     * were teaches a reader to skim the stack traces that do matter.
     */
    private static String causeChain(Throwable failure) {
        StringBuilder chain = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (!chain.isEmpty()) {
                chain.append("; caused by ");
            }
            chain.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                chain.append(": ").append(cause.getMessage());
            }
        }
        return chain.toString();
    }

    /** One walk's outcome, held rather than raised. Exactly one of the two is present. */
    private record Walked(WalkId walkId, Exception failure) {}

    /**
     * Walks the seed folder if the operator has named one, and empty if nobody has.
     *
     * <p>An unset key is not a failure: a corpus can be censused before anyone has decided what the
     * seed set is, and the profile is where that gap is visible.
     */
    private Optional<Walked> walkSeedFolderIfNamed(Profile profile) {
        if (!profile.seedFolder().isSet()) {
            return Optional.empty();
        }
        Path seedFolder = Path.of(profile.seedFolder().value());
        return Optional.of(walkOrCapture(seedFolder, "the seed folder"));
    }

    /** What the profile's seed-folder key is set to say: the walk that ran, or why none did. */
    private Measurement seedFolderMeasurement(Optional<Walked> seed) {
        if (seed.isEmpty()) {
            return new Measurement("no seed folder is set in the profile", clock.instant());
        }
        Walked walked = seed.get();
        if (walked.failure() != null) {
            return new Measurement(
                    "the seed folder could not be walked: " + causeChain(walked.failure()), clock.instant());
        }
        return new Measurement("walk " + walked.walkId().value(), clock.instant());
    }
}
