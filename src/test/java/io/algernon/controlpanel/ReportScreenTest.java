package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reports of the working directory, each re-served read-only at {@code /report/<file name>} (ADR-302 §3): the
 * fifteen files the jar writes, each answered as written under a second policy, {@code sandbox allow-scripts}, a
 * missing one with one line, and no other file of the working directory at all.
 *
 * <p>The requests are written by {@link RawHttp}, holding the cookie as a browser does, so that each header's every
 * value and the body's bytes are read as sent.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Disabled(ScreenServer.UNTIL_THE_SCREENS_ARE_BUILT)
class ReportScreenTest {

    /** The fifteen reports the jar writes in the working directory, in the order the record lists them. */
    private static final List<String> THE_FIFTEEN_REPORTS = List.of("progress.html", "progress-steps.html",
            "progress-samples.html", "progress-runs.html", "format-mix.html", "extraction-failures.html",
            "extraction-survivors.html", "confidence-distribution.html", "document-frequency.html",
            "word-count-distribution.html", "seed-corpus-comparison.html", "translations.html", "cluster-sizes.html",
            "relevance-labelling.html", "arrangement.html");

    /** The control panel's own policy on every response. */
    private static final String THE_CONTROL_PANELS_POLICY = "frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    /** The second policy a report is answered with, so its script runs in an origin of its own. */
    private static final String THE_REPORTS_POLICY = "sandbox allow-scripts";

    @TempDir
    Path folder;

    @Test
    @Story("The operator reads the reports from the control panel")
    @DisplayName("Each report is served as written, at its own path, as HTML, under the control panel's policy and a sandbox")
    void eachReportIsServedAsWrittenInASandboxAtItsOwnPath() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Map<String, String> written = new LinkedHashMap<>();
        for (String report : THE_FIFTEEN_REPORTS) {
            String page = "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + report + "</title></head>"
                    + "<body><p>" + report + " łódź 漢字</p><script>document.title += '';</script></body></html>";
            Files.writeString(workingDirectory.resolve(report), page, StandardCharsets.UTF_8);
            written.put(report, page);
        }
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            List<List<Object>> answered = new ArrayList<>();
            for (String report : THE_FIFTEEN_REPORTS) {
                RawHttp.Response response = get(screens, "/report/" + report);
                answered.add(List.of(report, response.status(), response.header("Content-Type"),
                        response.header("Content-Security-Policy"), response.header("Cache-Control"),
                        response.body().equals(written.get(report))));
            }

            claim("each of the fifteen is answered 200, as HTML in UTF-8, under the control panel's policy and the"
                            + " sandbox, kept out of the cache, its body the file's bytes as written",
                    () -> assertThat(answered).containsExactlyElementsOf(THE_FIFTEEN_REPORTS.stream()
                            .map(report -> List.<Object>of(report, 200, List.of("text/html; charset=utf-8"),
                                    List.of(THE_CONTROL_PANELS_POLICY, THE_REPORTS_POLICY), List.of("no-store"), true))
                            .toList()));
        }
    }

    @Test
    @Story("The operator reads the reports from the control panel")
    @DisplayName("A report the working folder does not hold yet is not found, answered with one line naming it")
    void aReportNotWrittenIsNotFoundWithOneLine() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            RawHttp.Response response = get(screens, "/report/format-mix.html");

            claim("the report not written is answered 404, as plain text",
                    () -> assertThat(List.of(response.status(), response.header("Content-Type")))
                            .containsExactly(404, List.of("text/plain; charset=utf-8")));
            claim("with one line naming the file the working folder does not hold",
                    () -> assertThat(response.body().strip())
                            .isEqualTo("the working directory holds no format-mix.html")
                            .doesNotContain("\n"));
        }
    }

    @Test
    @Story("The operator reads the reports from the control panel")
    @DisplayName("No other file of the working folder is served under the reports' path, whatever its name or a path climbing out")
    void noOtherFileOfTheWorkingDirectoryIsServed() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        for (String file : List.of("index.html", "vespera.db", "profile.yaml", "vespera.lock", "progress")) {
            Files.writeString(workingDirectory.resolve(file), "not a report", StandardCharsets.UTF_8);
        }
        Files.writeString(workingDirectory.resolve("progress.html"), "<p>a report</p>", StandardCharsets.UTF_8);
        List<String> asked = List.of("/report/index.html", "/report/vespera.db", "/report/profile.yaml",
                "/report/vespera.lock", "/report/progress", "/report/", "/report/progress.html/",
                "/report/../vespera.db", "/report/%2e%2e/vespera.db", "/report/progress.html%00.db");
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            List<Integer> statuses = new ArrayList<>();
            for (String path : asked) {
                statuses.add(get(screens, path).status());
            }

            claim("every path that is not one of the fifteen reports is answered 404, though the file it names is in"
                            + " the working folder",
                    () -> assertThat(statuses).containsOnly(404).hasSize(asked.size()));
        }
    }

    private static RawHttp.Response get(ScreenServer screens, String path) throws IOException {
        ControlPanelServer server = screens.server();
        return RawHttp.send(server.address(), "GET", path, List.of(
                "Host: 127.0.0.1:" + server.address().getPort(),
                "Cookie: " + ControlPanelServer.COOKIE + "=" + server.token()));
    }
}
