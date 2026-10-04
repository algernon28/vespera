package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ControlConversion;
import io.algernon.vespera.extraction.FailuresInARow;
import io.algernon.vespera.extraction.InARow;
import io.algernon.vespera.ledger.OccurrenceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The consecutive-service-scope-failure circuit breaker (ADR-071): a separate streak from Spring
 * Batch's own cumulative {@code skipLimit}, which stays configured only as a generous backstop. The
 * count of consecutive {@link ServiceScopeFailureException} skips, of any mix of categories summed
 * together, is {@code extraction}'s {@link FailuresInARow}, with the rule for what ends it and the
 * control conversion it sends when it reaches {@link FailuresInARow#CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT}
 * (ADR-184, ADR-189). This listener tells it what the step saw and acts on what it answers: the step goes
 * on, each failure the file's own, if the converter converts the control document, and stops outright if
 * it does not.
 *
 * <p>One object plays two listener roles, deliberately: {@link SkipListener#onSkipInProcess} is the
 * only place a service-scope skip is observable, and {@link ItemProcessListener#afterProcess} is the
 * only place a completed, non-skipped item is. A completed item ends the streak only when it is
 * evidence that the converter answers about files now (ADR-184 section 4); {@link
 * ExtractionItemProcessor} marks the occurrences that are not through the same {@link FailuresInARow}.
 *
 * <p>The text that names the stage stays here, word for word, because {@code extraction} knows no stage.
 * Step-scoped, and sharing the step's one {@link FailuresInARow} with {@link ExtractionItemProcessor}.
 */
@Component
@StepScope
class ExtractionCircuitBreaker
        implements SkipListener<OccurrenceId, ExtractionOutcome>, ItemProcessListener<OccurrenceId, ExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCircuitBreaker.class);

    private final FailuresInARow failuresInARow;

    /** For a caller with no converter to send a control conversion to: a streak that is reached stops. */
    ExtractionCircuitBreaker() {
        this(new FailuresInARow(ControlConversion.never()));
    }

    @Autowired
    ExtractionCircuitBreaker(FailuresInARow failuresInARow) {
        this.failuresInARow = failuresInARow;
    }

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        InARow row = failuresInARow.setAside();
        // ADR-093: this is the case that motivated logging at all -- a service-scope failure writes no
        // row at the moment it is thrown (ADR-071), so this WARN is the only record it happened at this
        // instant. ExtractionFaultRecorder turns it into a fault row afterward and, where the step goes
        // on to complete, an extraction-failed verdict (ADR-139); the streak below may still trip the
        // breaker first.
        log.warn(
                "[extraction] service-scope failure on {} (consecutive streak: {}/{}): {}",
                item.value(),
                row.inARow(),
                FailuresInARow.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT,
                t.toString());
        if (!(row instanceof InARow.Reached)) {
            return;
        }
        switch (failuresInARow.controlConversionAfterSetAsides()) {
            case InARow.ControlConverted converted -> log.warn(
                    "Stage 2 (extraction): the converter converted the control document after {} files in a"
                            + " row failed, so each failure is the file's own and the stage goes on",
                    converted.inARow());
            case InARow.ControlNotConverted stop -> {
                DoclingControlConversion.logReading(stop.reading());
                log.error(
                        "[extraction] circuit breaker tripped after {} consecutive service-scope failures and the"
                                + " control conversion did not convert; stopping the step",
                        stop.inARow());
                throw new ExtractorStoppedAnsweringException(stop.inARow(), t);
            }
            case InARow.Counted counted ->
                throw new IllegalStateException("a sent control conversion is never only counted");
            case InARow.Reached reached ->
                throw new IllegalStateException("a sent control conversion is never due again");
        }
    }

    @Override
    public void afterProcess(OccurrenceId item, ExtractionOutcome result) {
        failuresInARow.occurrenceCompleted();
    }
}
