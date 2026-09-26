package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 2 says, and what it leaves behind, when {@code docling-serve} cannot be used at the start
 * of the step: unreachable, unhealthy, or healthy but refusing {@code /version} (#319).
 *
 * <p>{@link ExtractionHealthCheckListener#beforeStep} throws when the sidecar does not answer its
 * health check, so the step fails before it opens its reader (ADR-071). Spring Batch still calls every
 * listener's {@code afterStep} from a {@code finally}, and then closes every stream the step
 * registered ({@code AbstractStep.execute}, spring-batch-core 6.0.5). Until #319 was fixed, both of
 * those built step-scoped beans the step never needed: {@link ExtractionFaultRecorder} asked for stage 2's run
 * in its constructor, and closing the reader built {@link ConversionDispatch}, which asked for the
 * extractor identity, and the survivors reader beneath it, which asked for the run. Asking for the run
 * asks for the identity, and composing the identity calls the sidecar's {@code /version}.
 *
 * <ul>
 *   <li>With the sidecar <b>unreachable</b>, that call threw inside {@code afterStep}. {@code
 *       CompositeStepExecutionListener} stops at the first listener that throws, so {@link
 *       ExtractionHealthCheckListener#afterStep}, which writes stage 2's closing line (#311), never ran.
 *       The operator got a second and a third stack trace and no line saying what to do.
 *   <li>With the sidecar <b>answering but not healthy</b>, the same path succeeded and minted stage 2's
 *       run behind the health check that had just failed. #319 asks that no run be minted behind that
 *       failed check, which is ADR-080's rule for a gate applied to this check.
 *   <li>With the sidecar <b>healthy but refusing {@code /version}</b>, the health check passes and the
 *       step fails while opening its reader, because building the survivors reader asks for the run and
 *       the run asks for the identity. Closing the reader then built the survivors reader a second
 *       time, which asked the sidecar again and threw again.
 * </ul>
 *
 * <p>{@link ConversionDispatch#close} now closes the delegate only if {@link ConversionDispatch#open}
 * got past opening it. All three tests fail with that guard removed, since closing a reader nobody
 * opened would build the survivors reader in each. Only the third tells apart where the guard's flag
 * is set: raised before the delegate is opened rather than after, the first two still pass and the
 * third fails, because it is the one case that starts opening the reader and does not finish. Both
 * were measured.
 *
 * <p>The unreachable sidecar is a real one: the client is the production {@link DoclingClient}, pointed
 * at a loopback port nothing listens on, so the refusal is the operating system's and the message is
 * the one an operator would read. The other two are a small HTTP server on that same port, answering
 * {@code /health} and {@code /version} with the statuses each test names. The extractor is a {@link
 * ScriptedExtractor} with nothing queued, because no conversion should ever be asked for.
 *
 * <p>{@code @DirtiesContext} per method, because the extractor identity is a lazily built singleton:
 * once one test had composed it, a later one would never call {@code /version} again, and the order
 * the tests ran in would decide what each saw.
 */
@CascadeSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(ExtractionWithTheSidecarDownTest.UnreachableSidecarBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("319")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
@Link(name = "ADR-157", url = Adr.A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE, type = "adr")
class ExtractionWithTheSidecarDownTest {

    /** A loopback port nothing listens on unless a test starts a sidecar on it. */
    private static final int SIDECAR_PORT = aFreePort();

    /** Where the client looks for the sidecar, and so what a failure to reach it names. */
    private static final String SIDECAR = "http://127.0.0.1:" + SIDECAR_PORT;

    /** How stage 2's closing line opens when the step did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed";

    /** The status a sidecar answers with when it has what was asked for. */
    private static final int OK = 200;

    /** The status a sidecar answers its health check with when it is up but not ready to convert. */
    private static final int UNAVAILABLE = 503;

    /** The status a sidecar answers with when asking it failed on its side. */
    private static final int SERVER_ERROR = 500;

    /** What the operator should be told to do once the cause is fixed, in the words every closing line uses. */
    private static final String RUN_THE_SAME_COMMAND_AGAIN = "run the same command again";

    /** What Spring Batch logs when a step listener's end-of-step callback throws. */
    private static final String AFTER_STEP_THREW = "Exception in afterStep callback";

    /** What Spring Batch logs when closing a step's reader throws. */
    private static final String CLOSING_THREW = "Exception while closing step execution resources";

    /** The stages recorded ahead of stage 2: census mints no run, and stage 1 has no sidecar to lose. */
    private static final List<String> BEFORE_STAGE_2 = List.of(StageModules.BYTE_LEVEL_REDUCTION.stage());

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;
    private HttpServer sidecar;

    /**
     * On the root logger rather than the application's own, because what must not appear is Spring
     * Batch's line, written through Spring Batch's logger.
     */
    @BeforeEach
    void captureEveryLine() {
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseEveryLineAndTheSidecar() {
        rootLogger.detachAppender(logged);
        logged.stop();
        if (sidecar != null) {
            sidecar.stop(0);
        }
    }

    @Test
    @Story("A step that failed says it failed")
    @DisplayName("When the document converter is not running as extraction starts, the closing line says the stage failed, names the converter and says to run the same command again, with no second stack trace")
    void anUnreachableSidecarStillGetsItsClosingLine(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: the document converter was not running when"
                        + " the stage checked it",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "a line says stage 2 failed, names the converter by the address it was looked for at, and"
                        + " says to run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(SIDECAR)
                                && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
        claim(
                "nothing at the end of the step threw: no end-of-step callback and no closing of the"
                        + " step's reader asked the converter for anything once its check had failed",
                () -> assertThat(lines()).noneMatch(line -> line.contains(AFTER_STEP_THREW) || line.contains(CLOSING_THREW)));
        claim(
                "and no extraction is recorded over this corpus, only the byte-level reduction in front of"
                        + " it: a failed check leaves no record of work behind it",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_2));
    }

    @Test
    @Story("A converter that fails its check leaves no record of work behind it")
    @DisplayName("When the document converter answers but reports itself unhealthy as extraction starts, no extraction is recorded, and the closing line says the stage failed")
    void anUnhealthySidecarLeavesNoRunBehindItsCheck(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");
        sidecar = aSidecarAnswering(UNAVAILABLE, OK);

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: the converter reported itself unhealthy",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "no extraction is recorded over this corpus, only the byte-level reduction in front of it,"
                        + " although the converter would have told anything that asked what it is built from",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_2));
        claim(
                "a line says stage 2 failed and says to run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
        claim(
                "and nothing at the end of the step threw",
                () -> assertThat(lines()).noneMatch(line -> line.contains(AFTER_STEP_THREW) || line.contains(CLOSING_THREW)));
    }

    /**
     * The one case that gets past the health check and still never finishes opening the reader:
     * opening it builds the survivors reader, which asks for the run, which asks {@code /version}, and
     * that fails. So closing the step must not build the survivors reader a second time, which would
     * ask again.
     */
    @Test
    @Story("A step that failed says it failed")
    @DisplayName("When the document converter passes its health check but will not say what it is built from, the closing line says the stage failed, nothing further throws, and no extraction is recorded")
    void aSidecarThatWillNotReportItsVersionIsNotAskedAgainOnClose(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("document.txt"), "content of the one document");
        sidecar = aSidecarAnswering(OK, SERVER_ERROR);

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: without what the converter is built from, stage 2"
                        + " cannot say which conversions are its own",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "a line says stage 2 failed and says to run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
        claim(
                "nothing at the end of the step threw: closing the step did not ask the converter a second"
                        + " time for what it had just refused",
                () -> assertThat(lines()).noneMatch(line -> line.contains(AFTER_STEP_THREW) || line.contains(CLOSING_THREW)));
        claim(
                "and no extraction is recorded over this corpus, only the byte-level reduction in front of it",
                () -> assertThat(stagesRecordedOver(root)).containsExactlyElementsOf(BEFORE_STAGE_2));
    }

    /**
     * A sidecar on {@link #SIDECAR_PORT} that answers its health check with {@code healthStatus} and
     * {@code /version} with {@code versionStatus}, carrying a version report in the real sidecar's shape
     * whenever that status is {@link #OK}.
     */
    private static HttpServer aSidecarAnswering(int healthStatus, int versionStatus) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), SIDECAR_PORT), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(healthStatus, -1);
            exchange.close();
        });
        server.createContext("/version", exchange -> {
            if (versionStatus != OK) {
                exchange.sendResponseHeaders(versionStatus, -1);
                exchange.close();
                return;
            }
            byte[] body = "{\"docling-serve\":\"1.32.0\",\"docling\":\"2.124.0\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(OK, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> stagesRecordedOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? ORDER BY r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /** A loopback port that was free a moment ago; nothing in this class listens on it unless told to. */
    private static int aFreePort() {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("no loopback port could be reserved for the sidecar", e);
        }
    }

    /**
     * The production {@link DoclingClient} pointed at {@link #SIDECAR}, and an extractor with nothing
     * queued. {@code @TestConfiguration}, for the reason {@link StubbedExtractionBeans} gives.
     */
    @TestConfiguration
    static class UnreachableSidecarBeans {

        @Bean
        DoclingClient doclingClient() {
            return new DoclingClient(SIDECAR);
        }

        @Bean
        DoclingExtractor doclingExtractor() {
            return new ScriptedExtractor();
        }
    }
}
