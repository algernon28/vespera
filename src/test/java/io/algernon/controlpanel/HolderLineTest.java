package io.algernon.controlpanel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /** How long before this JVM started a holder line says its holder started, in the case of a pid reused by it. */
    private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

    private static final String STARTED = "2026-10-19T10:15:30+02:00";

    /** A corpus-root lock's holder line as the jar writes it: the working-directory lock's three fields, then two. */
    private static final String A_HOLDER_LINE = "pid=4242 started=" + STARTED
            + " command=run C:\\corpus root=C:\\corpus working-directory=C:\\work";

    private static final long A_PID = 4242;

    /** The byte the jar locks, far past the end, so the line stays readable while it is held. */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long ENDED_BOUND_SECONDS = 60;

    private static final String CONTROL_PANEL = "io.algernon.controlpanel";

    private static final HolderLine LINE = HolderLine.parse(A_HOLDER_LINE).orElseThrow();

    private static final Instant STARTED_INSTANT = OffsetDateTime.parse(STARTED).toInstant();

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A holder line is read into its pid and its start time, and its text is kept whole")
    void aLineIsReadIntoItsPidAndStartedAndKeptWhole() {
        claim("the line is read into the pid it names, the instant its started field names, and its whole text",
                () -> assertThat(HolderLine.parse(A_HOLDER_LINE))
                        .contains(new HolderLine(A_PID, STARTED_INSTANT, A_HOLDER_LINE)));
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A line whose process is alive and started no later than one second after the line's start time is held")
    void aLineWhoseProcessIsAliveAndStartedNoLaterThanStartedPlusOneSecondIsHeld() {
        claim("a live process that started exactly one second after the line's start time holds it",
                () -> assertThat(LINE.heldWhile(new HolderProcess(true, Optional.of(STARTED_INSTANT.plus(ONE_SECOND)))))
                        .isTrue());
        claim("and so does one that started before it",
                () -> assertThat(LINE.heldWhile(new HolderProcess(true, Optional.of(STARTED_INSTANT.minus(ONE_SECOND)))))
                        .isTrue());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A line whose process started more than one second after the line's start time is not held: the pid was reused")
    void aLineWhoseProcessStartedMoreThanOneSecondAfterStartedIsNotHeld() {
        claim("a live process that started one millisecond past one second after the line's start time does not hold it",
                () -> assertThat(LINE.heldWhile(new HolderProcess(
                                true, Optional.of(STARTED_INSTANT.plus(ONE_SECOND).plus(ONE_MILLISECOND)))))
                        .isFalse());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A line whose process is not alive is not held, whatever its start")
    void aLineWhosePidIsDeadIsNotHeld() {
        claim("a process that is not alive holds nothing, though its start would fit the rule",
                () -> assertThat(LINE.heldWhile(new HolderProcess(false, Optional.of(STARTED_INSTANT)))).isFalse());
        claim("nor one that is not alive and gives no start",
                () -> assertThat(LINE.heldWhile(new HolderProcess(false, Optional.empty()))).isFalse());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A line whose live process gives no start instant counts as held")
    void aLineWhoseProcessGivesNoStartInstantIsHeld() {
        claim("a live process whose start the system does not give holds the line",
                () -> assertThat(LINE.heldWhile(new HolderProcess(true, Optional.empty()))).isTrue());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("The rule reads the processes the system names: this one, this one under a start before it, and one that ended")
    void theRuleReadsTheProcessTheSystemNames() throws Exception {
        ProcessHandle me = ProcessHandle.current();
        Optional<Instant> myStart = me.info().startInstant();
        assumeTrue(myStart.isPresent(), "this system gives no start instant of a process");
        Instant toTheSecond = myStart.get().truncatedTo(ChronoUnit.SECONDS);

        claim("a line naming this process, with its start to the second, is held",
                () -> assertThat(lineOf(me.pid(), toTheSecond).held()).isTrue());
        claim("a line naming this process's pid with a start ten seconds before it began is not held: the pid reads"
                        + " as reused",
                () -> assertThat(lineOf(me.pid(), toTheSecond.minus(TEN_SECONDS)).held()).isFalse());

        Process ended = ChildForTests.builder("lines", "1").redirectErrorStream(true).start();
        ended.getInputStream().readAllBytes();
        claim("the short child ended", () -> assertThat(ended.waitFor(ENDED_BOUND_SECONDS, TimeUnit.SECONDS)).isTrue());
        claim("a line naming the pid of a process that ended is not held",
                () -> assertThat(lineOf(ended.pid(), Instant.now().truncatedTo(ChronoUnit.SECONDS)).held()).isFalse());
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A holder line is read while its file is locked the way the jar locks it")
    void readingALockedHolderLineTakesNoLock(@TempDir Path workingDirectory) throws Exception {
        Path lockFile = workingDirectory.resolve("vespera.lock");
        Files.writeString(lockFile, A_HOLDER_LINE + "\r\n", StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false)) {
            claim("this test holds the lock the jar holds", () -> assertThat(lock).isNotNull());
            claim("the line is read while it is held, as the line the jar wrote, its line separator left out; a read"
                            + " that took a lock overlapping the jar's would throw here",
                    () -> assertThatCode(() -> assertThat(HolderReading.read(lockFile).line()).contains(LINE))
                            .doesNotThrowAnyException());
            claim("and the lock is still this test's after the read", () -> assertThat(lock.isValid()).isTrue());
        }
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("Reading a holder line starts no process and takes no lock")
    void readingAHolderLineStartsNoProcessAndTakesNoLock() {
        JavaClasses controlPanel = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(CONTROL_PANEL);
        DescribedPredicate<JavaMethodCall> aLockOrAnExec = DescribedPredicate.describe(
                "a lock of a file channel or an exec of the runtime",
                call -> (call.getTargetOwner().isAssignableTo(FileChannel.class)
                                && (call.getName().equals("lock") || call.getName().equals("tryLock")))
                        || (call.getTargetOwner().isEquivalentTo(Runtime.class) && call.getName().equals("exec")));

        claim("no class that reads a holder line names a process builder, locks a file channel or execs a"
                        + " command, so finding a holder starts no jar and takes no lock",
                () -> assertThatCode(() -> noClasses()
                                .that().haveFullyQualifiedName(HolderLine.class.getName())
                                .or().haveFullyQualifiedName(HolderProcess.class.getName())
                                .or().haveFullyQualifiedName(HolderReading.class.getName())
                                .should().dependOnClassesThat().areAssignableTo(ProcessBuilder.class)
                                .orShould().callMethodWhere(aLockOrAnExec)
                                .check(controlPanel))
                        .doesNotThrowAnyException());
    }

    private static HolderLine lineOf(long pid, Instant started) {
        String text = "pid=" + pid + " started="
                + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(started, ZoneOffset.UTC))
                + " command=run";
        return HolderLine.parse(text).orElseThrow();
    }
}
