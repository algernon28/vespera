package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Work a test posts to the toolkit's thread through {@link HeadlessToolkit#onTheToolkit} is bounded on that thread's
 * processor time, not on the wall (ADR-385): where busy threads keep the toolkit's thread waiting for a processor, work
 * that costs it little answers however long the wall says it took, and work that waits for what does not come still
 * fails, naming what the thread was doing.
 */
@Epic("Control panel")
@Feature("The tests' toolkit")
@Issue("727")
@Link(name = "ADR-385", url = Adr.WORK_ON_THE_TOOLKITS_THREAD_IS_BOUNDED_ON_ITS_PROCESSOR_TIME, type = "adr")
class ABusyMachineDoesNotTimeOutTheToolkitsWorkTest {

    /** The old bound, on the wall, that the busy threads keep the work past. */
    private static final Duration THE_OLD_WALL_BOUND = Duration.ofSeconds(10);

    /** What the work costs the toolkit's thread: a tenth of its bound, and more than the costliest window shown (2.8 s). */
    private static final Duration ITS_COST = Duration.ofSeconds(1);

    /** Busy threads for each processor of this Java, twice ADR-352's eight. */
    private static final int BUSY_PER_PROCESSOR = 16;

    @BeforeAll
    static void toolkit() throws Exception {
        HeadlessToolkit.start();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Story("A busy machine does not time out the toolkit's work")
    @DisplayName("Work costing the toolkit's thread one second answers beside sixteen busy threads for each processor, past ten seconds on the wall")
    void workCostingLittleAnswersHoweverLongTheBusyMachineKeepsIt() throws Exception {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        AtomicBoolean busy = new AtomicBoolean(true);
        List<Thread> hogs = new ArrayList<>();
        int count = BUSY_PER_PROCESSOR * Runtime.getRuntime().availableProcessors();
        for (int each = 0; each < count; each++) {
            hogs.add(Thread.ofPlatform().daemon().name("busy-" + each).start(() -> {
                while (busy.get()) {
                    // keeps a processor busy, as another build beside the tests does
                }
            }));
        }
        long[] spent;
        long wall;
        try {
            long began = System.nanoTime();
            spent = HeadlessToolkit.onTheToolkit(() -> {
                long from = threads.getCurrentThreadCpuTime();
                long now = from;
                while (now - from < ITS_COST.toNanos()) {
                    now = threads.getCurrentThreadCpuTime();
                }
                return new long[] {now - from};
            });
            wall = System.nanoTime() - began;
        } finally {
            busy.set(false);
            for (Thread hog : hogs) {
                hog.join();
            }
        }

        Assumptions.assumeThat(Duration.ofNanos(wall))
                .as("the busy threads kept the work past the old bound on the wall, so the test reproduces #727")
                .isGreaterThan(THE_OLD_WALL_BOUND);
        claim("the work answered, having spent its second of the toolkit's processor time, past ten seconds on the wall",
                () -> assertThat(spent[0]).isGreaterThanOrEqualTo(ITS_COST.toNanos()));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Story("A busy machine does not time out the toolkit's work")
    @DisplayName("Work spending more than ten seconds of the toolkit's processor time fails, naming that time and where the thread stood")
    void workSpendingPastItsBoundFails() throws Exception {
        long began = System.nanoTime();
        AtomicBoolean spinning = new AtomicBoolean(true);
        Throwable failed;
        try {
            failed = catchThrowable(() -> HeadlessToolkit.onTheToolkit(() -> {
                long spin = 0;
                while (spinning.get()) {
                    spin++;
                }
                return spin;
            }));
        } finally {
            spinning.set(false);
        }
        long wall = System.nanoTime() - began;

        claim("the wait fails as a timeout naming the processor time the toolkit's thread spent and the work it was running",
                () -> assertThat(failed).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("of its processor time")
                        .hasMessageContaining(ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.class.getName()));
        claim("it fails no sooner than the bound", () -> assertThat(Duration.ofNanos(wall))
                .isGreaterThanOrEqualTo(HeadlessToolkit.WORK_PROCESSOR_TIME));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Story("A busy machine does not time out the toolkit's work")
    @DisplayName("Work waiting on the toolkit's thread for work posted behind it fails once the thread has stood still ten seconds, naming where")
    void workWaitingForWorkPostedBehindItFails() throws Exception {
        CountDownLatch behind = new CountDownLatch(1);
        long began = System.nanoTime();
        Throwable failed;
        try {
            failed = catchThrowable(() -> HeadlessToolkit.onTheToolkit(() -> {
                Platform.runLater(behind::countDown);
                return behind.await(1, TimeUnit.HOURS);
            }));
        } finally {
            behind.countDown();
        }
        long wall = System.nanoTime() - began;
        boolean freed = HeadlessToolkit.onTheToolkit(() -> true);

        claim("the wait fails as a timeout naming the thread standing still, waiting, and the work it waits in",
                () -> assertThat(failed).isInstanceOf(TimeoutException.class)
                        .hasMessageContaining("gaining no processor time")
                        .hasMessageContaining("WAITING")
                        .hasMessageContaining(ABusyMachineDoesNotTimeOutTheToolkitsWorkTest.class.getName()));
        claim("it fails no sooner than the thread stood still for the bound, and well before the ceiling",
                () -> assertThat(Duration.ofNanos(wall)).isGreaterThanOrEqualTo(HeadlessToolkit.STANDING_STILL)
                        .isLessThan(HeadlessToolkit.CEILING));
        claim("the toolkit's thread, let go, takes the next work", () -> assertThat(freed).isTrue());
    }

    @Test
    @Story("A busy machine does not time out the toolkit's work")
    @DisplayName("Work posted from the toolkit's own thread is refused, as it would wait for itself")
    void workPostedFromTheToolkitsOwnThreadIsRefused() throws Exception {
        Throwable refused = HeadlessToolkit.onTheToolkit(() -> catchThrowable(() -> HeadlessToolkit.onTheToolkit(() -> 1)));

        claim("the call on the toolkit's thread throws at once, saying it would wait for itself",
                () -> assertThat(refused).isInstanceOf(IllegalStateException.class).hasMessageContaining("wait for itself"));
    }
}
