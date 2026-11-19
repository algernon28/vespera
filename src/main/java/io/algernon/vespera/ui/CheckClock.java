package io.algernon.vespera.ui;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * The clock of the window's check (ADR-430 §4): one thread of its own that does the check once as it starts, again every
 * 5 seconds, and again at once where {@link #now} asks, after a short settling that makes a burst of asks one check and
 * puts the check after the event that asked for it. It never does two at once, being one thread, skips a regular check
 * while a start or a shut-down runs, and stops with the window.
 */
final class CheckClock {

    /** How often the check is done again while the window is open. */
    static final Duration EVERY = Duration.ofSeconds(5);

    /** How long an ask settles before its check is done: a tenth of a second, far inside what at once means. */
    static final Duration SETTLING = Duration.ofMillis(100);

    private final Runnable check;
    private final BooleanSupplier skipped;
    private final Object lock = new Object();
    private boolean asked = true;
    private boolean stopped;
    private boolean started;
    /** Whether the check about to be done is the first, which settles nothing: the window waits on it to enable its buttons. */
    private boolean first = true;

    /** {@code check} does one check, waiting for its answer; {@code skipped} says whether the regular one is to be left undone now. */
    CheckClock(Runnable check, BooleanSupplier skipped) {
        this.check = check;
        this.skipped = skipped;
    }

    /** Starts the thread; the first check follows at once. */
    void start() {
        synchronized (lock) {
            if (started || stopped) {
                return;
            }
            started = true;
        }
        Thread.ofPlatform().daemon().name("the control panel checks Docker").start(this::loop);
    }

    /** Asks for a check at once, and the next regular one {@link #EVERY} after it. */
    void now() {
        synchronized (lock) {
            asked = true;
            lock.notifyAll();
        }
    }

    /** Stops the thread; a check under way ends, and none follows. */
    void stop() {
        synchronized (lock) {
            stopped = true;
            lock.notifyAll();
        }
    }

    private void loop() {
        while (true) {
            boolean regular;
            try {
                synchronized (lock) {
                    waitUntil(System.nanoTime() + EVERY.toNanos(), true);
                    if (stopped) {
                        return;
                    }
                    regular = !asked;
                    if (!regular && !first) {
                        waitUntil(System.nanoTime() + SETTLING.toNanos(), false);
                        if (stopped) {
                            return;
                        }
                    }
                    asked = false;
                    first = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (regular && skipped.getAsBoolean()) {
                continue;
            }
            try {
                check.run();
            } catch (RuntimeException failed) {
                // a check that fails is done again at the next
            }
        }
    }

    /** Waits, holding the lock, until {@code deadline}, or where {@code endsOnAnAsk}, until a check is asked for; or until the clock stops. */
    private void waitUntil(long deadline, boolean endsOnAnAsk) throws InterruptedException {
        while (!stopped && !(endsOnAnAsk && asked)) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return;
            }
            lock.wait(left / 1_000_000, (int) (left % 1_000_000));
        }
    }
}
