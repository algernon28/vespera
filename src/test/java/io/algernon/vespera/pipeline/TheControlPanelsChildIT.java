package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;
import picocli.CommandLine;

/**
 * The packaged jar, started as the control panel starts it, with the two encoding flags, {@code CI=1} and its
 * standard error merged into a standard output read through the pipe, prints UTF-8 (ADR-277 section 1 and 3).
 *
 * <p>The line read is the refusal of a working directory in use, which the jar prints on standard error with {@code
 * println}, the print path that writes windows-1252 without the flags on a Windows whose ANSI code page is 1252. The
 * working directory's name holds {@code ł} and {@code 漢}, which windows-1252 holds neither of. It reaches the jar
 * through {@code VESPERA_WORKING_DIR}, because a child JVM on such a Windows receives its arguments through the ANSI
 * code page and would see each of the two as {@code ?} (ADR-277, What was measured). Where the native encoding is
 * UTF-8 already, as on Linux, the flags change nothing and this holds whatever they are.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("559")
@Link(name = "ADR-277", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class TheControlPanelsChildIT {

    /** Generous over a start-up of a few seconds; the point is only that the process does end. */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the control panel starts. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** A working directory's name holding a Polish letter and a Chinese one, neither of them in windows-1252. */
    private static final String NAME_OUTSIDE_WINDOWS_1252 = "wd-ł-漢";

    /** The whole of what a refused invocation prints: one line. */
    private static final int ONE_LINE = 1;

    /** Whatever an earlier test class in this JVM left held is handed back before this one takes its own. */
    @BeforeEach
    void startWithNothingHeld() {
        WorkingDirectoryLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        WorkingDirectoryLock.release();
    }

    @Test
    @Story("The control panel reads its child's output")
    @DisplayName("Started with the two encoding flags and CI=1, the jar's refusal of a working directory in use whose name holds ł and 漢 reads as strict UTF-8 on the merged stream, naming the directory whole")
    void itsRefusalOfAWorkingDirectoryInUseIsUtf8OnTheMergedStream(@TempDir Path temporary) throws Exception {
        Path workingDirectory = Files.createDirectory(temporary.resolve(NAME_OUTSIDE_WINDOWS_1252)).toAbsolutePath();
        hold(workingDirectory);

        ProcessBuilder builder = new ProcessBuilder(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-Dspring.docker.compose.enabled=false",
                        "-jar", EXECUTABLE_JAR.toAbsolutePath().toString(),
                        "label")
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        builder.environment().put("CI", "1");
        builder.environment().put("VESPERA_WORKING_DIR", workingDirectory.toString());
        Process child = builder.start();
        CompletableFuture<byte[]> printed = CompletableFuture.supplyAsync(() -> {
            try {
                return child.getInputStream().readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        boolean finished = child.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            child.destroyForcibly();
            child.waitFor();
        }
        byte[] bytes = printed.get(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);

        claim("the child ends within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(finished).isTrue());
        claim("with exit code 1, a refusal", () -> assertThat(child.exitValue()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        String text = strictUtf8(bytes);
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        claim("what it printed is one line that is not blank", () -> assertThat(lines).hasSize(ONE_LINE));
        claim("naming the working directory whole, ł and 漢 included, where windows-1252 would have printed ? for each",
                () -> assertThat(lines.getFirst())
                        .startsWith("another Vespera invocation is already using the working directory")
                        .contains(workingDirectory.toString()));
    }

    /** Decodes as UTF-8 and fails on any byte sequence UTF-8 does not allow, rather than replacing it. */
    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            throw new AssertionError("the child's output is not UTF-8", notUtf8);
        }
    }

    /** Holds the working directory in this process, as a running invocation would. */
    private static void hold(Path workingDirectory) {
        new WorkingDirectoryLock().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[] {"run", "the-first-invocation"},
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString())));
    }
}
