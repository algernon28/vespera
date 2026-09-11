package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
     * The step's chunk size — an implementer's default, not a spec-fixed number (unlike the timeout
     * and streak counts ADR-071 pins). Kept small because each item is up to a 5-minute HTTP call:
     * a small chunk bounds how much already-cached-and-therefore-cheap work a rolled-back chunk would
     * redo, and keeps verdict commits frequent.
     */
    static final int CHUNK_SIZE = 10;

    /**
     * Spring Batch's own cumulative skip limit — a generous backstop against a slowly-degrading
     * sidecar over a very long run, deliberately not the circuit breaker (ADR-071's own distinction).
     * An implementer's default: large enough that it is not what fires during a healthy run, however
     * long, since {@link ExtractionCircuitBreaker}'s consecutive-streak count is the mechanism that
     * actually protects a run against a dead sidecar.
     */
    static final long SKIP_LIMIT = 10_000;

    /**
     * How many conversions are in flight at once, and the one number in this class that was measured
     * rather than chosen: extraction is the only step whose items cost seconds instead of
     * milliseconds — 2.0 to 2.6 of them per document over a real archive — and every one of those
     * seconds is spent waiting on an out-of-process sidecar, with the calling thread doing nothing.
     * Serially, a 13,000-document archive is an eight-hour step on a sixteen-core machine using one
     * core.
     *
     * <p>Four rather than sixteen, for two reasons that both cap it well below the core count.
     * {@code compose.yaml} gives the sidecar four workers, and a fifth parallel request only queues
     * behind them — the number is kept in step with that file deliberately, and raising one without
     * the other buys nothing. And every completed item writes to SQLite, which locks the file per
     * write (see {@code application.yaml}), so concurrency past the sidecar's own limit converts
     * waiting on HTTP into waiting on a lock.
     */
    static final int CONCURRENT_CONVERSIONS = 4;

    /**
     * How many times a chunk is retried when the database was locked, and why that is a retry
     * rather than a skip.
     *
     * <p>A lock collision says nothing about the document -- the same item under the same converter
     * will succeed the moment the lock is free, which is what makes it retryable where a
     * service-scope failure is skippable (ADR-071's distinction, applied to a failure it did not
     * have to consider). Skipping would silently drop a document for a reason that had nothing to
     * do with it.
     *
     * <p>Three, because the writes this contends with are now milliseconds long and
     * {@code busy_timeout} already absorbs 30 seconds of waiting before an exception is raised at
     * all. If three retries are not enough, the cause is not contention and a failed step is the
     * honest outcome.
     */
    static final int LOCK_RETRY_LIMIT = 3;

    /**
     * The step's own pool, fixed-size and named for what it does.
     *
     * <p>Fixed rather than a {@code SimpleAsyncTaskExecutor}: an unbounded executor would launch a
     * thread per chunk and put the sidecar's memory — the thing that OOM-killed it on the first real
     * run — back under exactly the pressure {@code compose.yaml}'s limit now bounds. The pool size
     * <em>is</em> the concurrency, since Spring Batch no longer throttles a multi-threaded step
     * separately from the executor it was given.
     */
    @Bean
    AsyncTaskExecutor extractionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CONCURRENT_CONVERSIONS);
        executor.setMaxPoolSize(CONCURRENT_CONVERSIONS);
        executor.setThreadNamePrefix("extraction-");
        // Daemon threads, or `vespera run` never returns. An invocation is one process that ends
        // (ADR-047), and SpringApplication.run hands control back as soon as the job is over -- the
        // JVM then exits only when no non-daemon thread is left, and exiting is what fires the
        // shutdown hook that closes the context. A pool of four non-daemon core threads sitting
        // idle after the step is therefore not a leak that gets cleaned up later: it is a process
        // that never terminates, on a successful run as readily as a failed one.
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }

    @Bean
    Step extractionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            OccurrenceReader extractionReader,
            ExtractionItemProcessor extractionItemProcessor,
            ExtractionItemWriter extractionItemWriter,
            ExtractionCircuitBreaker extractionCircuitBreaker,
            ExtractionHealthCheckListener extractionHealthCheckListener,
            AsyncTaskExecutor extractionTaskExecutor) {
        return new StepBuilder(ExtractionRun.STAGE, jobRepository)
                .<OccurrenceId, ExtractionOutcome>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(extractionReader)
                .processor(extractionItemProcessor)
                .writer(extractionItemWriter)
                .faultTolerant()
                .skip(ServiceScopeFailureException.class)
                .skipLimit(SKIP_LIMIT)
                // A locked database is Spring's UncategorizedSQLException, because SQLITE_BUSY has no
                // portable SQLState for the translator to map. That is broader than this needs, and
                // it is the narrowest type the exception actually arrives as; the retry is bounded so
                // a genuinely uncategorized failure costs three attempts rather than a run.
                .retry(UncategorizedSQLException.class)
                .retryLimit(LOCK_RETRY_LIMIT)
                .listener(extractionCircuitBreaker)
                .listener(extractionHealthCheckListener)
                // Chunks run in parallel from here, which is safe only because the reader is a
                // JdbcPagingItemReader (Spring Batch's own thread-safe reader) and because the three
                // pieces of state a chunk touches -- both streaks and the progress counter -- were
                // made concurrent alongside this line rather than left to be discovered.
                .taskExecutor(extractionTaskExecutor)
                .build();
    }

    /**
     * The survivors reader (ADR-060), scoped to whichever run {@link ExtractionRun} minted — itself scoped
     * to whichever walk the {@code root} job parameter names, never hard-coded to the corpus walk.
     */
    @Bean
    @StepScope
    OccurrenceReader extractionReader(Ledger ledger, ExtractionRun extractionRun) {
        return new OccurrenceReader(ledger.survivors(extractionRun.runId()));
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
     */
    @Bean
    @Lazy
    ExtractorIdentity extractorIdentity(DoclingClient doclingClient) {
        String versions = doclingClient.version().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(component -> component.getKey() + "=" + component.getValue())
                .collect(Collectors.joining(";"));
        return new ExtractorIdentity("docling-serve;" + versions + ";" + DoclingClient.sentOptions());
    }

    /**
     * {@code pipeline}'s reading of the profile's tier-2 key (#48, ADR-070): {@code extraction} may
     * depend only on {@code ledger}, so this is read here, not there, and handed down as a plain
     * value.
     */
    @Bean
    DegenerateOutputConfidenceFloor degenerateOutputConfidenceFloor(ProfileStore profileStore) {
        Profile profile = profileStore.load();
        String value = profile.degenerateOutputConfidenceFloor().value();
        return new DegenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor().isSet()
                ? Double.valueOf(value)
                : null);
    }
}
