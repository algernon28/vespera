package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the jar gives the control panel to read without starting it: a holder line whose {@code started} bounds the
 * holder's own start (ADR-278 section 4), and {@code CI=1} as the value that keeps every page closed (section 2).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("559")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class WhatTheControlPanelReadsOfAnInvocationTest {

    /** The value the control panel gives {@code CI} in its child's environment. */
    private static final Map<String, String> AS_THE_CONTROL_PANEL_SETS_IT = Map.of("CI", "1", "DISPLAY", ":0");

    /** How far after {@code started} a process may have started and still be the holder: {@code started} is to the second. */
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    private static final Pattern STARTED = Pattern.compile("pid=(\\d+) started=(\\S+) command=.*");

    private static final long HOLDER_END_BOUND_SECONDS = 30;

    @BeforeEach
    void startWithNothingHeld() {
        WorkingDirectoryLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        WorkingDirectoryLock.release();
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A holding process started no later than its holder line's started plus one second, so a pid reused after it fails the rule")
    void theHoldersStartIsNoLaterThanStartedPlusOneSecond(@TempDir Path workingDirectory) throws Exception {
        Process holder = WorkingDirectoryLockTest.startHolder(workingDirectory);
        try {
            Optional<Instant> processStart = holder.info().startInstant();
            assumeTrue(processStart.isPresent(), "this system gives no start instant of a process");
            String line = Files.readString(workingDirectory.resolve(WorkingDirectoryLock.FILE_NAME), StandardCharsets.UTF_8)
                    .strip();
            Matcher holderLine = STARTED.matcher(line);
            claim("the holder line names the holding process", () -> {
                assertThat(holderLine.matches()).isTrue();
                assertThat(Long.parseLong(holderLine.group(1))).isEqualTo(holder.pid());
            });
            Instant started = OffsetDateTime.parse(holderLine.group(2)).toInstant();
            claim("and that process's start instant is no later than started plus one second",
                    () -> assertThat(processStart.get()).isBeforeOrEqualTo(started.plus(ONE_SECOND)));
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("The control panel shows the pages itself")
    @DisplayName("CI=1, a display set, opens no page on Windows, macOS or Linux")
    void ciSetToOneOpensNoPage() {
        URI page = Path.of("progress.html").toAbsolutePath().toUri();
        for (String system : List.of("Windows 11", "Mac OS X", "Linux")) {
            claim("on " + system + " with CI=1, no command is started",
                    () -> assertThat(SystemPageOpener.command(page, system, AS_THE_CONTROL_PANEL_SETS_IT)).isEmpty());
        }
    }
}
