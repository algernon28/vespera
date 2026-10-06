package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ExtractionFaults;
import io.algernon.vespera.extraction.ExtractionStatement;
import io.algernon.vespera.extraction.ExtractionStatementProgress;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailuresInARow;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.similarity.ShingleHashIndex;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 2's own Batch wiring (extraction), kept apart from {@link VesperaJobConfiguration} because it
 * holds a chunk step's reader, writer, listeners and the extractor's own bean, not one plain step
 * (ADR-157 §7).
 *
 * <p>The first chunk-oriented step in the job (ADR: "stage 2 runs as a chunk-oriented Spring Batch
 * step (not a tasklet), so that its fault-tolerance mechanics — skip, circuit breaker — have the
 * machinery they need"), unlike stage 1's tasklet.
 */
@Configuration
public class ExtractionJobConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExtractionJobConfiguration.class);

    /**
     * The step's chunk size. That it is a whole number of {@link #CONVERSION_CONCURRENCY} waves is
     * ADR-140's constraint; that it is two waves rather than one or three is the implementer's choice
     * (unlike the timeout and streak counts ADR-071 pins). Kept small because each item is up to a
     * 5-minute HTTP call:
     * a small chunk bounds how much work a rolled-back chunk loses -- its conversions go back to
     * Docling, because the extraction-cache rows it wrote roll back with it (ADR-181) -- and keeps
     * verdict commits frequent.
     *
     * <p>Sixteen rather than ten because a chunk was the read-ahead when this was chosen (ADR-140):
     * {@link ConversionDispatch} dispatches every occurrence of a chunk before the first is processed,
     * and a chunk pays one 2-second tick per <em>wave</em> of {@link #CONVERSION_CONCURRENCY}. A chunk
     * of ten at a width of eight paid a second tick for two documents — fivefold, not eightfold. Two
     * whole waves deliver the width the record claims, and {@code ExtractionStepTest} pins that this
     * stays a multiple of the width. The reader now also keeps {@link #LOOKAHEAD} occurrences
     * dispatched beyond the chunk (ADR-176), and this number is unchanged by that.
     */
    static final int CHUNK_SIZE = 16;

    /**
     * Spring Batch's own cumulative skip limit — a generous backstop against a slowly-degrading
     * sidecar over a very long run, deliberately not the circuit breaker (ADR-071's own distinction).
     * An implementer's default: large enough that it is not what fires during a healthy run, however
     * long, since {@link ExtractionCircuitBreaker}'s consecutive-streak count is the mechanism that
     * actually protects a run against a sidecar that answers only with failures. A sidecar that drops
     * connections is stopped by {@link SidecarRecovery}'s bound when it is gone, and by {@link
     * FailuresInARow#CONSECUTIVE_DROPPED_TWICE_COUNT} when it is up and converts nothing
     * (ADR-175).
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

    /**
     * How many occurrences beyond the one it is handing over {@link ConversionDispatch} has already
     * read and dispatched (ADR-176): two waves of {@link #CONVERSION_CONCURRENCY}. The window crosses
     * chunk boundaries, so up to this many calls beyond the chunk are there to convert while a chunk's
     * last occurrences are taken and while the chunk commits. Two waves rather than one, so that a wave
     * is queued behind the one converting when the chunk's own calls are done. That it equals {@link
     * #CHUNK_SIZE} today is a coincidence: the two are independent, and what is dispatched and not yet
     * taken is at most a chunk plus this many.
     */
    static final int LOOKAHEAD = 2 * CONVERSION_CONCURRENCY;

    /**
     * The entry the Docling image adds to the sidecar's {@code /version}, naming the image it was built
     * as (ADR-179 §2). Prefixed so that no docling component can report under the same key.
     */
    static final String IMAGE_ENTRY = "vespera-image";

    /** For the seconds the removal of {@code shingle_by_hash} took, as its closing line states them (ADR-187). */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /**
     * The lines of the read of the faults a stopped run left, written as {@code extraction} reports it
     * (ADR-199 section 2, ADR-193 sections 4.1 and 7) by {@link ReportedStatements}: one line before the read,
     * and one after it with how long it took, with progress between where SQLite calls back. {@code
     * extraction} calls it with an empty total where the run holds no fault, and nothing is written then, so a
     * first invocation writes nothing here.
     */
    private static ExtractionStatementProgress faultReadProgress() {
        return readProgress(
                ExtractionStatement.FAULTED_OCCURRENCES,
                "the faults the stopped run recorded",
                "Stage 2 (extraction, reading the faults the stopped run recorded)");
    }

    /** The same lines for the read of the occurrences the stopped run measured (ADR-199 section 2). */
    private static ExtractionStatementProgress measuredReadProgress() {
        return readProgress(
                ExtractionStatement.RECORDED_OCCURRENCES,
                "the occurrences the stopped run measured",
                "Stage 2 (extraction, reading the occurrences the stopped run measured)");
    }

    private static ExtractionStatementProgress readProgress(ExtractionStatement statement, String what, String label) {
        return ReportedStatements.saying()
                .counted(statement, "Stage 2 (extraction)", what, label)
                .build();
    }

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
            ReviewListListener reviewListListener,
            Ledger ledger) {
        return new StepBuilder(StepNames.EXTRACTION, jobRepository)
                .<OccurrenceId, ExtractionOutcome>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(extractionConversionDispatch)
                .processor(extractionItemProcessor)
                .writer(extractionItemWriter)
                .faultTolerant()
                .skip(ServiceScopeFailureException.class)
                .skipLimit(SKIP_LIMIT)
                .listener(extractionCircuitBreaker)
                // Before the health check listener, so that its afterStep runs after the fault recorder's
                // below: afterStep runs in the reverse of this order (ADR-175 section 7).
                .listener(reviewListListener)
                .listener(extractionHealthCheckListener)
                // Built inline rather than as a bean of its own (ADR-157 §6): it needs nothing scoped,
                // and a plain object is what the class itself is built to be handed as.
                .listener(new RunCompletion(ledger, StageModules.EXTRACTION, StepNames.EXTRACTION))
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
     * worker ever needs a connection. Each {@code read()} keeps {@link #LOOKAHEAD} occurrences
     * dispatched beyond the one it returns, across chunk boundaries (ADR-176), and Spring Batch reads a
     * whole chunk before it processes any of it, so up to the width are in flight at once and nothing
     * holds more than a chunk and that window ahead -- see {@link ConversionDispatch}'s own javadoc.
     *
     * <p>Wraps {@link #extractionReader} rather than folding into it, because the two decide separate
     * things: {@link #extractionReader} decides which occurrences this run reads at all -- the
     * already-finished check, the deletion of the fault rows and their resolving verdicts, and the
     * leaving out of what committed chunks already recorded (ADR-115, ADR-116, ADR-181) -- and this
     * class only adds the concurrent dispatch ADR-140 asks for on top of whatever it yields, without
     * duplicating that decision.
     */
    @Bean
    @StepScope
    ConversionDispatch extractionConversionDispatch(
            OccurrenceReader extractionReader,
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor doclingExtractor,
            ObjectProvider<ExtractorIdentity> extractorIdentity,
            StageRuns stageRuns,
            PendingConversions extractionPendingConversions) {
        return new ConversionDispatch(
                extractionReader,
                ledger,
                contentIdentity,
                detectedFormats,
                doclingExtractor,
                // A provider, read when the reader is opened: the identity asks the sidecar, and this
                // bean is also built when a step that failed its health check is closed (#319).
                extractorIdentity::getObject,
                stageRuns,
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
     * The control conversion both counts ask for when they reach five (ADR-184), step-scoped so the two
     * share one, and so that its wait covers the same {@link PendingConversions} the reader dispatches
     * into. Declared here and not as a component of its own: it is part of what stage 2's step is built
     * from, with the pending conversions it waits on.
     */
    @Bean
    @StepScope
    DoclingControlConversion extractionControlConversion(
            DoclingExtractor doclingExtractor,
            SidecarRecovery sidecarRecovery,
            PendingConversions extractionPendingConversions) {
        return new DoclingControlConversion(doclingExtractor, sidecarRecovery, extractionPendingConversions);
    }

    /**
     * The three counts of failures in a row (ADR-189), the one instance {@link ExtractionItemProcessor}
     * and {@link ExtractionCircuitBreaker} share, so that what either marks the other reads and a control
     * conversion that converted starts both rows again (ADR-184 section 4). Step-scoped so the counts
     * survive a chunk boundary and no longer; the class is plain and not final, which is what lets the
     * scope proxy it (ADR-140 section 3).
     */
    @Bean
    @StepScope
    FailuresInARow extractionFailuresInARow(DoclingControlConversion extractionControlConversion) {
        return new FailuresInARow(extractionControlConversion);
    }

    /**
     * {@link ExtractionFaultRecorder}, wired here rather than made {@code @Component} because {@link
     * ExtractionFaults} is not one (ADR-139, on {@code ClusterFaults}' own precedent) — the same reason
     * {@code extractionStep} builds its {@code RunCompletion} listener inline, by hand, from an ambient
     * {@link Ledger} (ADR-157 §6).
     *
     * <p><b>Registered after {@code RunCompletion} in {@code extractionStep}'s listener chain,
     * deliberately.</b> Spring Batch's {@code CompositeStepExecutionListener} runs {@code afterStep} in
     * the reverse of registration order (confirmed against the {@code spring-batch-core} sources), so
     * the listener named last in that chain is the one whose {@code afterStep} runs first. Registering
     * this one after {@code RunCompletion} is what makes its verdicts commit before that listener
     * records the step as holding all of its own work (ADR-139 section 4) -- naming it first in the
     * chain, which reads as "runs first," would in fact run it last. {@code @Order} was considered and
     * refused: this bean still arrives as a {@code @StepScope} CGLIB subclass, and {@code
     * OrderedComposite.add} only detects {@code @Order} through {@code
     * AnnotationUtils.isAnnotationDeclaredLocally}, which a generated subclass never satisfies -- the
     * annotation would be silently ignored rather than honoured.
     */
    @Bean
    @StepScope
    ExtractionFaultRecorder extractionFaultRecorder(
            JdbcTemplate jdbcTemplate,
            Ledger ledger,
            StageRuns stageRuns,
            PlatformTransactionManager transactionManager) {
        return new ExtractionFaultRecorder(
                new ExtractionFaults(jdbcTemplate), ledger, stageRuns, transactionManager);
    }

    /**
     * {@link ReviewListListener}, step-scoped as {@link #extractionFaultRecorder} is and for the
     * same reason: it is built only when the step first calls it, and asks for the run only then.
     */
    @Bean
    @StepScope
    ReviewListListener reviewListListener(
            Ledger ledger, StageRuns stageRuns, @Value("${vespera.working-dir}") Path workingDirectory) {
        return new ReviewListListener(ledger, stageRuns, workingDirectory);
    }

    /**
     * The survivors reader (ADR-060), scoped to whichever run {@link StageRuns#extraction} minted --
     * itself scoped to whichever walk the {@code root} job parameter names, never hard-coded to the
     * corpus walk.
     */
    @Bean
    @StepScope
    OccurrenceReader extractionReader(
            Ledger ledger,
            StageRuns stageRuns,
            ExtractionMetrics extractionMetrics,
            JdbcTemplate jdbcTemplate) {
        RunId extractionRun = stageRuns.extraction();
        // This step's own work under this run is already recorded, so it runs in its usual place and
        // reads nothing (ADR-115, ADR-116) -- the shape a shut gate already uses, for a different
        // reason.
        if (ledger.stepFinished(extractionRun, StepNames.EXTRACTION)) {
            return OccurrenceReader.yieldingNothing();
        }

        // Not finished: an invocation that stopped partway may have left rows behind under this same
        // run id, and a resume keeps most of them (ADR-181 section 1, amending ADR-115's discard half,
        // ADR-116 and ADR-139 section 2). A committed chunk is final within its run: every
        // extraction_metric row, every shingle and every verdict the processor's chunks wrote stays,
        // and those occurrences are not read again.
        //
        // What is deleted is the end of the step's work, not a chunk's: the extraction_fault rows and
        // the EXTRACTION_FAILED verdicts that resolved them (ADR-139). Every faulted occurrence is read
        // again, so this invocation's end of step records every fault of the stage afresh. The verdicts
        // go first, because the fault rows are how they are found; verdict has no unique key, so a
        // resolving verdict left behind would resolve the same fault twice. A faulted occurrence
        // carries no metric row (a fault leaves none of stage 2's own rows in its chunk), so the read
        // filter below does not hide it.
        //
        // Here rather than in ExtractionItemProcessor's constructor, which is where it sat and was
        // wrong: that bean is step-scoped, so its constructor first runs inside the first chunk's
        // transaction, and a chunk that fails rolls its transaction back while step scope survives it.
        // Under spring-batch-core 6.0.5 a skip in processing does not roll the chunk back (processItem
        // drops the item and the chunk goes on); a non-skippable failure or a tripped circuit breaker
        // does, restoring the rows just deleted, and the delete would never run again. A reader is
        // opened outside the chunk transaction, which is the only place a delete can be made to stick.
        //
        // The queries are extraction's (ADR-041); the one delete against verdict is the ledger's.
        ExtractionFaults extractionFaults = new ExtractionFaults(jdbcTemplate);
        Set<OccurrenceId> faulted = extractionFaults.occurrencesForRun(extractionRun, faultReadProgress());
        ledger.discardVerdictsAgainst(extractionRun, faulted, VerdictKind.EXTRACTION_FAILED);
        extractionFaults.discardForRun(extractionRun);

        // shingle_by_hash is not maintained while shingles are written (ADR-182 section 2.2): every new row
        // would land at a random place in an index far larger than any page cache, and stage 4b builds it
        // whole before its first read. Removed here, on the same test as the discard above -- the step is
        // not finished -- and for the same reason that discard is here: a drop inside a chunk that rolled
        // back would be restored. It removes no row and finds nothing to do on a resume. SQLite reads every
        // page of the index to remove it, which took two hours on a 16 GB database (ADR-187 section 1), so
        // where the index is there the drop has two lines of its own, and where it is not, nothing is said.
        ShingleHashIndex shingleHashIndex = new ShingleHashIndex(jdbcTemplate);
        if (shingleHashIndex.exists()) {
            log.info(
                    "Stage 2 (extraction) is removing shingle_by_hash, over up to {} shingle rows, before it writes"
                            + " any; SQLite reads the whole index to remove it, which took two hours on a 16 GB database"
                            + " for an index grown row by row or built into the pages one had freed, and stopping before"
                            + " it ends undoes it",
                    shingleHashIndex.shingleRowsUpTo());
            long dropStarted = System.nanoTime();
            shingleHashIndex.drop();
            log.info(
                    "Stage 2 (extraction) removed shingle_by_hash in {} s",
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - dropStarted) / NANOS_PER_SECOND));
        }

        Set<OccurrenceId> recorded = extractionMetrics.occurrencesForRun(extractionRun, measuredReadProgress());
        if (!recorded.isEmpty() || !faulted.isEmpty()) {
            // Said when anything was recorded or faulted (ADR-181 section 1): a run whose only leftovers
            // are fault rows reads those occurrences again, and says so. Counts follow their labels so
            // that one reads as correctly as many.
            log.info(
                    "Stage 2 (extraction) resumes run {}: occurrences already measured by committed chunks: {};"
                            + " faulted occurrences read again: {}",
                    extractionRun.value(),
                    recorded.size(),
                    faulted.size());
        }
        if (!recorded.isEmpty()) {
            return new OccurrenceReader(new UnrecordedOccurrences(ledger.survivors(extractionRun), recorded));
        }
        return new OccurrenceReader(ledger.survivors(extractionRun));
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
     * 2's step asks for it — after that listener has run — and then reused: an ordinary singleton bean,
     * so nothing that asks for it afterwards costs a second call or needs the sidecar still up.
     *
     * <p>Not {@code @StepScope}: a scoped bean is injected as a CGLIB proxy, and {@link
     * ExtractorIdentity} is a record and therefore final. That is a fair constraint rather than an
     * obstacle — the identity does not vary between steps, so nothing wanted it scoped per step.
     *
     * <p><b>The image is in it too</b> (ADR-147). The stock image and the one with LibreOffice report
     * the same versions, and 5 of 14 PDFs were measured to convert differently between them; keyed by
     * the versions alone, a cache would serve one image's conversions as the other's. The image is the
     * one {@code compose.yaml} runs, named in {@code vespera.docling.image}.
     *
     * <p><b>The sidecar says which image it runs, and this checks it first</b> (ADR-179). Our image adds
     * its own name to {@code /version} as {@code vespera-image}, and that name is compared, character for
     * character, with the configured one before anything is composed. A sidecar running another image, or
     * not saying which, throws {@link DoclingRunsAnotherImageException} rather than have its conversions
     * recorded under a name that is not its own. The identity still carries the whole map, that entry
     * included.
     */
    @Bean
    @Lazy
    ExtractorIdentity extractorIdentity(
            DoclingClient doclingClient, @Value("${vespera.docling.image}") String image) {
        Map<String, String> reported = doclingClient.version();
        String running = reported.get(IMAGE_ENTRY);
        if (running == null) {
            throw DoclingRunsAnotherImageException.notReporting(image);
        }
        if (!running.equals(image)) {
            throw DoclingRunsAnotherImageException.running(running, image);
        }
        return ExtractorIdentity.composedOf(image, reported);
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
