package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Whether a holder line is held, read by the control panel without a lock and without the jar (ADR-278 §4, landed by
 * ADR-285 §5): held while its pid is alive and that process started no later than {@code started} plus one second, a
 * process that gives no start instant counting as held.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class HolderLineTest {

    /** How far after {@code started} the holding process may have started: {@code started} is to the second. */
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    /** The smallest step past that bound a start instant on Windows or Linux can be. */
    private static final Duration ONE_MILLISECOND = Duration.ofMillis(1);

    private static final String STARTED = "2026-10-19T10:15:30+02:00";

    /** A corpus-root lock's holder line as the jar writes it: the working-directory lock's three fields, then two. */
    private static final String A_HOLDER_LINE = "pid=4242 started=" + STARTED
            + " command=run C:\\corpus root=C:\\corpus working-directory=C:\\work";

    private static final HolderLine LINE = HolderLine.parse(A_HOLDER_LINE).orElseThrow();

    private static final Instant STARTED_INSTANT = OffsetDateTime.parse(STARTED).toInstant();

    @ParameterizedTest(name = "{0}")
    @MethodSource("processes")
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A line is held by a live process that started no later than one second after the line's start time, and not by one started later, whose pid was reused")
    void aLineIsHeldWhileItsProcessIsAliveAndStartedInTime(HolderProcess process, boolean held) {
        claim("the line is " + (held ? "held" : "not held") + " by that process",
                () -> assertThat(LINE.heldWhile(process)).isEqualTo(held));
    }

    /** Either side of the rule's edge: a live process started exactly one second after the line, and a millisecond later. */
    static Stream<Arguments> processes() {
        return Stream.of(
                Arguments.of(Named.of("a live process that started exactly one second after the line's start time holds"
                        + " it", new HolderProcess(true, Optional.of(STARTED_INSTANT.plus(ONE_SECOND)))), true),
                Arguments.of(Named.of("a live process that started one millisecond past one second after the line's"
                                + " start time does not hold it: the pid was reused",
                        new HolderProcess(true, Optional.of(STARTED_INSTANT.plus(ONE_SECOND).plus(ONE_MILLISECOND)))),
                        false));
    }
}
