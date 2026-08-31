package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.time.Clock;
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
        Profile profile = profileStore.load();

        // The two walks are independent, so neither is allowed to cost the other its chance to run
        // (ADR-064). A corpus root that cannot be walked is still worth knowing the seed set for, and
        // the invocation fails afterwards either way.
        Walked corpus = walkOrCapture(root, "the corpus");
        Measurement seedFolder = measureSeedFolder(profile);

        profileStore.save(profile.withSeedFolderMeasurement(seedFolder));
        log.info("Census merged the profile at {}", profileStore.file());

        if (corpus.failure() != null) {
            throw corpus.failure();
        }
        return RepeatStatus.FINISHED;
    }

    /** Walks a root, returning what went wrong rather than raising it, so the other walk still runs. */
    private Walked walkOrCapture(Path walkRoot, String what) {
        try {
            WalkId walkId = walkRecorder.walk(walkRoot);
            log.info("Census recorded {} at {} under walk {}", what, walkRoot, walkId.value());
            return new Walked(walkId, null);
        } catch (Exception e) {
            log.error("Census could not walk {} at {}", what, walkRoot, e);
            return new Walked(null, e);
        }
    }

    /** One walk's outcome, held rather than raised. Exactly one of the two is present. */
    private record Walked(WalkId walkId, Exception failure) {}

    /**
     * Walks the seed folder if the operator has named one, and points the profile's seed-folder key
     * at the walk that resulted.
     *
     * <p>An unset key is not a failure: a corpus can be censused before anyone has decided what the
     * seed set is, and the profile is where that gap is visible.
     */
    private Measurement measureSeedFolder(Profile profile) {
        if (!profile.seedFolder().isSet()) {
            return new Measurement("no seed folder is set in the profile", clock.instant());
        }
        Path seedFolder = Path.of(profile.seedFolder().value());
        Walked seed = walkOrCapture(seedFolder, "the seed folder");
        if (seed.failure() != null) {
            return new Measurement(
                    "the seed folder could not be walked: " + seed.failure().getMessage(), clock.instant());
        }
        return new Measurement("walk " + seed.walkId().value(), clock.instant());
    }
}
