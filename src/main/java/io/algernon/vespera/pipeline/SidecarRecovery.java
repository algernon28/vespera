package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Waits for {@code docling-serve} to answer its health check again after it dropped a connection
 * (ADR-175 section 2). The sidecar restarts by itself (ADR-164), so a dropped connection is first read
 * as a sidecar on its way back, and the call that lost it is placed once more when it is.
 *
 * <p>One instance for both steps that convert, stage 2 and seed extraction, so they wait the same way.
 */
@Component
class SidecarRecovery {

    private static final Logger log = LoggerFactory.getLogger(SidecarRecovery.class);

    /**
     * How long a sidecar is given to answer again. On the whole archive on 2026-09-28 a killed container
     * was back in 6 to 9 seconds (ADR-175's evidence); three minutes also covers one that reloads its
     * models first.
     */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(180);

    /** How long between two health checks while waiting. */
    private static final Duration POLL_EVERY = Duration.ofSeconds(2);

    private final DoclingClient client;
    private final Duration waitAtMost;
    private final Duration pollEvery;

    @Autowired
    SidecarRecovery(DoclingClient client) {
        this(client, WAIT_AT_MOST, POLL_EVERY);
    }

    /** The seam a test needs: the same wait, on a clock short enough to run. */
    SidecarRecovery(DoclingClient client, Duration waitAtMost, Duration pollEvery) {
        this.client = client;
        this.waitAtMost = waitAtMost;
        this.pollEvery = pollEvery;
    }

    /**
     * Returns once the sidecar answers its health check, at once if it already does.
     *
     * @throws DoclingDidNotComeBackException if it has not answered within the bound
     */
    void awaitHealthy() {
        if (client.isHealthy()) {
            return;
        }
        long started = System.nanoTime();
        log.warn("docling-serve dropped a connection and is not answering its health check; waiting for it");
        while (!client.isHealthy()) {
            if (System.nanoTime() - started >= waitAtMost.toNanos()) {
                throw new DoclingDidNotComeBackException(waitAtMost);
            }
            sleep();
        }
        log.info(
                "docling-serve answers its health check again after {} seconds",
                Duration.ofNanos(System.nanoTime() - started).toSeconds());
    }

    private void sleep() {
        try {
            Thread.sleep(pollEvery);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for docling-serve to come back", interrupted);
        }
    }
}
