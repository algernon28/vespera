package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ExtractionFaults;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 2's own Batch wiring (extraction), kept apart from {@link CensusJobConfiguration} and
 * {@link ByteLevelReductionJobConfiguration} for the same reason those are already separate: each stage
 * contributes its own step bean rather than growing one shared configuration class.
 *
 * <p>The first chunk-oriented step in the job (ADR: "stage 2 runs as a chunk-oriented Spring Batch
 * step (not a tasklet), so that its fault-tolerance mechanics — skip, circuit breaker — have the
 * machinery they need"), unlike stage 1's tasklet.
 */
@Configuration
public class ExtractionJobConfiguration {

    /**
     * The step's chunk size. That it is a whole number of {@link #CONVERSION_CONCURRENCY} waves is
     * ADR-140's constraint; that it is two waves rather than one or three is the implementer's choice
     * (unlike the timeout and streak counts ADR-071 pins). Kept small because each item is up to a
     * 5-minute HTTP call:
     * a small chunk bounds how much already-cached-and-therefore-cheap work a rolled-back chunk would
     * redo, and keeps verdict commits frequent.
     *
     * <p>Sixteen rather than ten because a chunk is now the read-ahead (ADR-140): {@link
     * ConversionDispatch} dispatches every occurrence of a chunk before the first is processed, and a
     * chunk pays one 2-second tick per <em>wave</em> of {@link #CONVERSION_CONCURRENCY}. A chunk of ten
     * at a width of eight paid a second tick for two documents — fivefold, not eightfold. Two whole
     * waves deliver the width the record claims, and {@code ExtractionStepTest} pins that this stays a
     * multiple of the width.
     */
    static final int CHUNK_SIZE = 16;

    /**
     * Spring Batch's own cumulative skip limit — a generous backstop against a slowly-degrading
     * sidecar over a very long run, deliberately not the circuit breaker (ADR-071's own distinction).
     * An implementer's default: large enough that it is not what fires during a healthy run, however
     * long, since {@link ExtractionCircuitBreaker}'s consecutive-streak count is the mechanism that
     * actually protects a run against a dead sidecar.
     */
    static final long SKIP_LIMIT = 10_000;

    /**
     * How many Docling calls stage 2 keeps in flight at once (ADR-140): not the chunk size above, and
     * not a Profile gate either, on ADR-071's own reasoning for the call timeout and the two streak
     * counts it fixes. A concurrency width is the same species of number as those -- it says how much
     * work the *machine* stage 2 runs on can carry at once, not what an operator has judged about the
     * corpus being read, so it ships as a code default exactly as they do, read back by
     * {@code ExtractionConcurrencyTest}.
     *
     * <p>Eight, and not sixteen or thirty-two, because the win from raising it is close to linear in the
     * width while the cost of raising it too far is not. Measured against the pinned sidecar
     * (docling-serve-cpu:v1.32.0 / docling 2.124.0), throughput rises roughly 0.5 documents per second
     * per unit of concurrency with no sign of a ceiling through 16 -- so the number is not chosen because
     * higher stops helping. It is chosen because a real corpus's conversion times are lumpy: a measured
     * live run's mean sits near 1.5 seconds with a tail as long as 58 seconds, and {@link
     * io.algernon.vespera.extraction.DoclingClient#CALL_TIMEOUT} fixes that call's whole budget, queue
     * included, at five minutes (ADR-071). Widen this past eight and a slow document waits behind more
     * and more of the documents dispatched ahead of it inside that same five-minute clock, so a document
     * that was never itself slow starts reading as a timeout. Eight keeps a 58-second document plus the
     * seven calls that could be queued ahead of it comfortably inside that budget while still buying
     * roughly an eightfold reduction in the 2-second-per-call serial wait ADR-140 measured -- the width
     * where the linear win is largest and the queue behind the slowest document is not yet what trips
     * the timeout it is measured against.
     */
    static final int CONVERSION_CONCURRENCY = 8;

    @Bean
    Step extractionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ConversionDispatch extractionConversionDispatch,
            ExtractionItemProcessor extractionItemProcessor,
            ExtractionItemWriter extractionItemWriter,
            ExtractionCircuitBreaker extractionCircuitBreaker,
            ExtractionHealthCheckListener extractionHealthCheckListener,
            ExtractionFaultRecorder extractionFaultRecorder,
            RunCompletion extractionRunCompletion) {
        return new StepBuilder(ExtractionRun.STAGE, jobRepository)
                .<OccurrenceId, ExtractionOutcome>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(extractionConversionDispatch)
                .processor(extractionItemProcessor)
                .writer(extractionItemWriter)
                .faultTolerant()
                .skip(ServiceScopeFailureException.class)
                .skipLimit(SKIP_LIMIT)
                .listener(extractionCircuitBreaker)
                .listener(extractionHealthCheckListener)
                .listener(extractionRunCompletion)
                .listener(extractionFaultRecorder)
                .build();
    }

    /**
     * The step's own reader, widened to keep {@link #CONVERSION_CONCURRENCY} Docling calls in flight
     * (ADR-140) without ever setting {@code StepBuilder.taskExecutor(...)} on {@link #extractionStep}:
     * {@link ConversionDispatch} still runs on the thread the step began on, and only the Docling calls
     * it dispatches run on worker threads of their own.
     *
     * <p>Dispatch happens from {@code read()}, and a worker runs only the Docling call: the cache
     * lookup before it and the write after it both stay on this thread (ADR-140 section 3), so no
     * worker ever needs a connection. Spring Batch reads a whole chunk before it processes any of it,
     * which is what puts up to the width in flight at once without anything holding more than one
     * chunk ahead -- see {@link ConversionDispatch}'s own javadoc.
     *
     * <p>Wraps {@link #extractionReader} rather than folding into it, because {@code
     * ExtractionRunTest} calls that method directly, by its current five-argument signature, and reads
     * the result as a plain {@code ItemStreamReader<OccurrenceId>} -- widening it here, once it already
     * exists, changes nothing that call sees.
     */
    @Bean
    @StepScope
    ConversionDispatch extractionConversionDispatch(
            OccurrenceReader extractionReader,
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor doclingExtractor,
            ExtractorIdentity extractorIdentity,
            ExtractionRun extractionRun,
            PendingConversions extractionPendingConversions) {
        return new ConversionDispatch(
                extractionReader,
                ledger,
                contentIdentity,
                detectedFormats,
                doclingExtractor,
                extractorIdentity,
                extractionRun,
                extractionPendingConversions,
                CONVERSION_CONCURRENCY);
    }

    /**
     * The one instance {@link #extractionConversionDispatch} files an occurrence's dispatched answer
     * into and {@link ExtractionItemProcessor} collects it from (ADR-140) -- step-scoped so both beans
     * of the one step execution share it.
     */
    @Bean
    @StepScope
    PendingConversions extractionPendingConversions() {
        return new PendingConversions();
    }

    /**
     * {@link ExtractionFaultRecorder}, wired here rather than made {@code @Component} because {@link
     * ExtractionFaults} is not one (ADR-139, on {@code ClusterFaults}' own precedent) — the same reason
     * {@link #extractionRunCompletion} below builds its listener by hand from an ambient {@link Ledger}.
     *
     * <p><b>Registered after {@code extractionRunCompletion} above, deliberately.</b> Spring Batch's
     * {@code CompositeStepExecutionListener} runs {@code afterStep} in the reverse of registration
     * order (confirmed against the {@code spring-batch-core} sources), so the listener named last in
     * the chain below is the one whose {@code afterStep} runs first. Registering this one after {@code
     * extractionRunCompletion} is what makes its verdicts commit before that listener records the step
     * as holding all of its own work (ADR-139 section 4) -- naming it first in the chain, which reads
     * as "runs first," would in fact run it last. {@code @Order} was considered and refused: both
     * listeners arrive here as {@code @StepScope} CGLIB subclasses, and {@code OrderedComposite.add}
     * only detects {@code @Order} through {@code AnnotationUtils.isAnnotationDeclaredLocally}, which a
     * generated subclass never satisfies -- the annotation would be silently ignored rather than honoured.
     */
    @Bean
    @StepScope
    ExtractionFaultRecorder extractionFaultRecorder(
            JdbcTemplate jdbcTemplate,
            Ledger ledger,
            ExtractionRun extractionRun,
            PlatformTransactionManager transactionManager) {
        return new ExtractionFaultRecorder(
                new ExtractionFaults(jdbcTemplate), ledger, extractionRun, transactionManager);
    }

    /**
     * The survivors reader (ADR-060), scoped to whichever run {@link ExtractionRun} minted — itself scoped
     * to whichever walk the {@code root} job parameter names, never hard-coded to the corpus walk.
     */
    @Bean
    @StepScope
    OccurrenceReader extractionReader(
            Ledger ledger,
            ExtractionRun extractionRun,
            ExtractionMetrics extractionMetrics,
            Shingler shingler,
            JdbcTemplate jdbcTemplate) {
        // This step's own work under this run is already recorded, so it runs in its usual place and
        // reads nothing (ADR-115, ADR-116) -- the shape a shut gate already uses, for a different
        // reason.
        if (ledger.stepFinished(extractionRun.runId(), ExtractionRun.STAGE)) {
            return OccurrenceReader.yieldingNothing();
        }

        // Not finished: an invocation that stopped partway may have left rows behind under this same
        // run id -- extraction_metric and extraction_fault are keyed (occurrence_id, run_id), so a
        // second write would collide on the first; shingle carries no such key at all and would
        // otherwise silently double stage 3's document-frequency count. The two
        // DEGENERATE_OUTPUT/EXTRACTION_FAILED verdict kinds are this run's own too (ADR-115's discard
        // half, ADR-116, extended by ADR-139 to extraction_fault). One discard, one list, done here and
        // nowhere else: splitting it across two classes would let the next table added beside these
        // read this list as complete when it is not, and the stepFinished guard above is the same
        // expression that decides whether this reader yields anything at all -- duplicating it
        // elsewhere would be two readings of one fact with nothing keeping them in agreement.
        //
        // Here rather than in ExtractionItemProcessor's constructor, which is where it sat and was
        // wrong: that bean is step-scoped, so its constructor first runs inside the first chunk's
        // transaction, and this step is fault-tolerant. A skip in that chunk rolls the transaction
        // back, restoring the rows just deleted, and step scope survives the rollback -- so the
        // discard never runs again and the collision it exists to prevent comes back. A reader is
        // opened outside the chunk transaction, which is the only place a delete can be made to stick.
        ledger.discardVerdicts(extractionRun.runId(), VerdictKind.EXTRACTION_FAILED, VerdictKind.DEGENERATE_OUTPUT);
        extractionMetrics.discardForRun(extractionRun.runId());
        shingler.discardForRun(extractionRun.runId());
        new ExtractionFaults(jdbcTemplate).discardForRun(extractionRun.runId());

        return new OccurrenceReader(ledger.survivors(extractionRun.runId()));
    }

    /** Marks this step's own work as holding all of it, once it has finished doing it (ADR-115, ADR-116). */
    @Bean
    @StepScope
    RunCompletion extractionRunCompletion(Ledger ledger, ExtractionRun extractionRun) {
        return new RunCompletion(ledger, extractionRun::runId, ExtractionRun.STAGE);
    }

    /**
     * The engine identity the extraction cache is keyed by and {@code configConsumed} records
     * (ADR-012: "the serving runtime is config, not code"; ADR-090 for what "full extractor identity"
     * turned out to mean).
     *
     * <p>Two parts, and between them they cover what a conversion depends on. The sidecar's whole
     * {@code /version} map says what it is built from — {@code docling} and {@code docling-ibm-models}
     * are what actually convert, and either can move while the serving wrapper stays put. The options
     * this client sends say what was asked of it. An option that is <em>not</em> sent is the server's
     * default, and that default is fixed by the schema of a version the map already pins, so the two
     * parts together need no third.
     *
     * <p><b>The base URL is deliberately absent.</b> It says where the sidecar is, never what it does:
     * two instances on the same versions with the same options produce the same text. Keying on it had
     * this exactly backwards — moving the sidecar's port invalidated the whole extraction cache, while
     * a genuine engine change slipped through unnoticed.
     *
     * <p>Entries are sorted, so an identity never varies with map iteration order. Two runs against
     * one unchanged sidecar must key the same rows; an identity that reshuffled would re-extract a
     * corpus for no reason at all.
     *
     * <p>{@code @Lazy}, because composing this needs the sidecar to answer: an eager singleton would
     * demand that at context refresh, before {@link ExtractionHealthCheckListener} has established the
     * sidecar is even there (ADR-071's lazy readiness check). Deferred, it is first built when stage
     * 2's step asks for it — after that listener has run — and then reused, so {@link RedundancyRun}
     * re-deriving stage 2's identity later in the job costs no second call and does not require the
     * sidecar to still be up.
     *
     * <p>Not {@code @StepScope}: a scoped bean is injected as a CGLIB proxy, and {@link
     * ExtractorIdentity} is a record and therefore final. That is a fair constraint rather than an
     * obstacle — the identity does not vary between steps, so nothing wanted it scoped per step.
     *
     * <p><b>The image is in it too</b> (ADR-147). The stock image and the one with LibreOffice report
     * the same versions, and 5 of 14 PDFs were measured to convert differently between them; keyed by
     * the versions alone, a cache would serve one image's conversions as the other's. The image is the
     * one {@code compose.yaml} runs, named in {@code vespera.docling.image}; the sidecar does not report
     * it, so it is taken as configured, the one fact here not read back from the sidecar.
     */
    @Bean
    @Lazy
    ExtractorIdentity extractorIdentity(
            DoclingClient doclingClient, @Value("${vespera.docling.image}") String image) {
        String versions = doclingClient.version().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(component -> component.getKey() + "=" + component.getValue())
                .collect(Collectors.joining(";"));
        return new ExtractorIdentity(
                "docling-serve;image=" + image + ";" + versions + ";" + DoclingClient.sentOptions());
    }

    /**
     * {@code pipeline}'s reading of the profile's tier-2 key (#48, ADR-070): {@code extraction} may
     * depend only on {@code ledger}, so this is read here, not there, and handed down as a plain
     * value.
     *
     * <p><b>An unreadable value leaves the floor unset rather than stopping the context</b>
     * (ADR-120). Until then this called {@code Double.valueOf} with no catch, and because it runs at
     * bean creation a single mistyped character in this one key meant the application never started —
     * no invocation, no report, and nothing naming the key at fault. The floor ships unset and stage 2
     * runs without it, so unset is a state this stage is already built for.
     */
    @Bean
    DegenerateOutputConfidenceFloor degenerateOutputConfidenceFloor(ProfileStore profileStore) {
        Profile profile = profileStore.load();
        return new DegenerateOutputConfidenceFloor(
                profile.degenerateOutputConfidenceFloor().reading() instanceof NumericValue.Answered answered
                        ? answered.number()
                        : null);
    }
}
