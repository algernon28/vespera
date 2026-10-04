package io.algernon.vespera.pipeline;

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
 * Batch's own cumulative {@code skipLimit}, which stays configured only as a generous backstop. This
 * counts consecutive {@link ServiceScopeFailureException} skips, of any mix of categories summed together.
 * When the streak reaches {@link #CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT} it sends the control
 * conversion (ADR-184): the step goes on, each failure the file's own, if the converter converts it,
 * and fails outright if it does not.
 *
 * <p>One object plays two listener roles, deliberately: {@link SkipListener#onSkipInProcess} is the
 * only place a service-scope skip is observable, and {@link ItemProcessListener#afterProcess} is the
 * only place a completed, non-skipped item is. A completed item ends the streak only when it is
 * evidence that the converter answers about files now (ADR-184 section 4): an answer to a call made for
 * that occurrence in this invocation, or an HTTP error status. It is not one when the occurrence was
 * answered from the extraction cache, had no detected format so that no call was made, timed out with
 * no response, or dropped the connection twice; {@link ExtractionItemProcessor} says so through {@link
 * ExtractionRowEvidence}, and the streak is left as it is. A run of connections dropped twice is counted
 * by the processor's own count, not by this one, and a control conversion that converted starts both
 * counts again.
 *
 * <p>Step-scoped for the same reason {@link ExtractionTimeoutStreak} is: the streak has to survive a chunk
 * boundary.
 */
@Component
@StepScope
class ExtractionCircuitBreaker implements SkipListener<OccurrenceId, ExtractionOutcome>, ItemProcessListener<OccurrenceId, ExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCircuitBreaker.class);

    /** ADR-071: higher than the timeout count, because this one has to fire on a mix of categories. */
    static final int CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT = 5;

    private final ControlConversion controlConversion;
    private final ExtractionRowEvidence rowEvidence;

    private int consecutiveServiceScopeFailures = 0;

    /** How many control conversions had converted when this streak was last looked at. */
    private long controlConversionsSeen;

    /** For a caller with no converter to send a control conversion to: a streak that is reached stops. */
    ExtractionCircuitBreaker() {
        this(ControlConversion.never(), new ExtractionRowEvidence());
    }

    @Autowired
    ExtractionCircuitBreaker(ControlConversion controlConversion, ExtractionRowEvidence rowEvidence) {
        this.controlConversion = controlConversion;
        this.rowEvidence = rowEvidence;
        this.controlConversionsSeen = controlConversion.conversions();
    }

    /** A control conversion that converted, whichever count asked for it, starts this one again too. */
    private void startAgainIfControlConverted() {
        long converted = controlConversion.conversions();
        if (converted != controlConversionsSeen) {
            controlConversionsSeen = converted;
            consecutiveServiceScopeFailures = 0;
        }
    }

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        startAgainIfControlConverted();
        consecutiveServiceScopeFailures++;
        // ADR-093: this is the case that motivated logging at all -- a service-scope failure writes no
        // row at the moment it is thrown (ADR-071), so this WARN is the only record it happened at this
        // instant. ExtractionFaultRecorder turns it into a fault row afterward and, where the step goes
        // on to complete, an extraction-failed verdict (ADR-139); the streak below may still trip the
        // breaker first.
        log.warn(
                "[extraction] service-scope failure on {} (consecutive streak: {}/{}): {}",
                item.value(),
                consecutiveServiceScopeFailures,
                CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT,
                t.toString());
        if (consecutiveServiceScopeFailures >= CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT) {
            if (controlConversion.converts()) {
                log.warn(
                        "Stage 2 (extraction): the converter converted the control document after {} files in a"
                                + " row failed, so each failure is the file's own and the stage goes on",
                        consecutiveServiceScopeFailures);
                controlConversionsSeen = controlConversion.conversions();
                consecutiveServiceScopeFailures = 0;
                return;
            }
            log.error(
                    "[extraction] circuit breaker tripped after {} consecutive service-scope failures and the"
                            + " control conversion did not convert; stopping the step",
                    consecutiveServiceScopeFailures);
            throw new ExtractorStoppedAnsweringException(consecutiveServiceScopeFailures, t);
        }
    }

    @Override
    public void afterProcess(OccurrenceId item, ExtractionOutcome result) {
        startAgainIfControlConverted();
        if (rowEvidence.consumeNone()) {
            return;
        }
        consecutiveServiceScopeFailures = 0;
    }
}
