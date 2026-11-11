package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * {@code report.html}, drawn from {@code report.json} through the jar's Mustache template or the operator's
 * (ADR-362 sections 5 and 6): a function of the report alone, a template that reads the model's keys and nothing else,
 * the operator's template and its fall-backs, and what the shipped template draws.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("738")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class ReportHtmlTest {

    private static final Instant T0 = Instant.parse("2026-11-06T08:00:00Z");

    private static final Duration INTERVAL = Duration.ofSeconds(7);

    private static final String PAGE = "report.html";

    private static final Pattern A_DIV = Pattern.compile("<div\\b[^>]*>");

    private static final Pattern A_TITLE = Pattern.compile("\\btitle=\"([^\"]*)\"");

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"0 0 ([0-9.]+) ([0-9.]+)\"");

    private static final Pattern A_POINT = Pattern.compile("<circle class=\"hit\"[^>]*><title>([^<]*)</title></circle>");

    private static final Pattern A_PROPERTY = Pattern.compile("--([a-z0-9-]+)\\s*:\\s*(#[0-9a-fA-F]{6})");

    private static final Pattern DARK_SCHEME =
            Pattern.compile("@media \\(prefers-color-scheme: dark\\)\\s*\\{\\s*:root\\s*\\{([^}]*)\\}");

    private static final Pattern LIGHT_SCHEME = Pattern.compile(":root\\s*\\{([^}]*)\\}");

    private static final Pattern HEADED_DRAWING = Pattern.compile(
            "<h2>([^<]+)</h2>\\s*<p class=\"caption\">[^<]+</p>\\s*(?:<ul class=\"legend\">.*?</ul>\\s*)?"
                    + "<svg[^>]*>\\s*<title>([^<]+)</title>",
            Pattern.DOTALL);

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The shipped template is one self-contained page that reloads at the interval")
    @DisplayName("The jar's template draws no script, no fetch and no unescaped name, has its policy in the head, and one refresh at the interval, on a running and on an ended report")
    void theShippedTemplateHoldsNoScriptAndReloadsAtTheInterval() throws Exception {
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(workingDirectory, clock, "");
        report.begins("run");
        clock.at(60);
        report.job(aJob(false, 5, "2000"));
        String running = page();
        clock.at(120);
        report.job(aJob(true, 16, "2000"));
        String ended = page();
        String template = new String(
                ReportHtml.class.getResourceAsStream(ReportHtml.TEMPLATE_RESOURCE).readAllBytes(), StandardCharsets.UTF_8);

        for (String drawing : List.of(running, ended)) {
            claim("the page holds no script and no fetch",
                    () -> assertThat(drawing).doesNotContainIgnoringCase("<script").doesNotContain("fetch"));
            claim("its head holds the policy that lets it open from disk and nothing else",
                    () -> assertThat(drawing.substring(0, drawing.indexOf("</head>"))).contains(
                            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src"
                                    + " 'unsafe-inline'; img-src data:\">"));
            claim("it refreshes once, at the interval, outside any noscript",
                    () -> assertThat(count(drawing, "http-equiv=\"refresh\" content=\"7\"")).isEqualTo(1));
            claim("and has no stylesheet, font or picture outside it",
                    () -> assertThat(drawing).doesNotContain("<link ").doesNotContain("@import").doesNotContain("<img"));
        }
        claim("the page says the invocation is running, and then that it ended",
                () -> assertThat(running).contains("This invocation is running."));
        claim("and the template writes no name unescaped",
                () -> assertThat(template).doesNotContain("{{{").doesNotContain("{{&").doesNotContain("<noscript"));
        claim("the ended page is the same page", () -> assertThat(ended).contains("This invocation has ended: completed."));
    }

    @Test
    @Story("A template reads the model's keys and no Java member")
    @DisplayName("A template naming {{class}}, {{bytes}} and {{report.invocation.class}} draws nothing for them, and the keys it names draw")
    void aTemplateReadsTheModelsKeysAndNoJavaMember() throws IOException {
        Path template = workingDirectory.resolve("keys.mustache");
        Files.writeString(template,
                "[{{class}}][{{bytes}}][{{report.invocation.class}}][{{report.invocation.pid.class}}]"
                        + "[{{report.entrySet}}][{{#report.entrySet}}x{{/report.entrySet}}][{{report.size}}][{{report.isEmpty}}]"
                        + "[{{report.steps.class}}][{{report.steps.length}}][{{report.invocation.command}}]",
                StandardCharsets.UTF_8);
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), template.toString());
        report.begins("run");
        report.job(aJob(false, 3, "2000"));

        claim("a name that is no key of the model draws nothing, and a key draws its value",
                () -> assertThat(page()).isEqualTo("[][][][][][][][][][][run]"));
        claim("and the template is the one that drew", () -> assertThat(reportJson().path("template").path("used").asString())
                .isEqualTo("named"));
    }

    @Test
    @Story("A template that cannot be used is one warning and the jar's drawing")
    @DisplayName("A path that is not absolute, a file not there, one that does not compile and one whose drawing throws partway: one warning each, the jar's drawing, template.used is jar")
    void aNamedTemplateThatCannotBeUsedIsOneWarningAndTheJarsDraws() throws IOException {
        Path doesNotCompile = Files.writeString(workingDirectory.resolve("open.mustache"), "{{#report.steps}} never closed");
        Path throwsPartway = Files.writeString(
                workingDirectory.resolve("partway.mustache"), "start {{#report.refusals}}{{> name}}{{/report.refusals}} end");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("relative", "templates/mine.mustache");
        cases.put("missing", workingDirectory.resolve("not-there.mustache").toString());
        cases.put("uncompilable", doesNotCompile.toString());
        cases.put("partway", throwsPartway.toString());

        for (Map.Entry<String, String> each : cases.entrySet()) {
            Path directory = Files.createDirectories(workingDirectory.resolve(each.getKey()));
            TheClock clock = new TheClock(T0);
            InvocationReport report = aReport(directory, clock, each.getValue());
            List<ILoggingEvent> warnings = warningsWhile(() -> {
                report.begins("run");
                report.job(aJob(false, 3, "2000"));
                clock.at(5);
                report.said("a refusal of the jar's own");
                report.job(aJob(false, 4, "2000"));
            });
            JsonNode written = TheReportFile.in(directory);

            claim(each.getKey() + ": exactly one warning, and it says the template could not be used",
                    () -> assertThat(warnings)
                            .hasSize(1)
                            .allMatch(event -> event.getFormattedMessage().startsWith("the report template " + each.getValue()
                                    + " could not be used, so the jar's own draws report.html: ")));
            claim(each.getKey() + ": the jar's template draws, and report.json says so",
                    () -> assertThat(written.path("template").path("used").asString()).isEqualTo("jar"));
            claim(each.getKey() + ": report.html is the jar's drawing",
                    () -> assertThat(Files.readString(directory.resolve(PAGE), StandardCharsets.UTF_8))
                            .contains("<!DOCTYPE html>").doesNotContain("never closed").doesNotContain("start  end"));
        }
    }

    @Test
    @Story("Every text of the report is escaped")
    @DisplayName("A line of the report that holds <, & and \" is escaped wherever it is drawn")
    void everyTextOfTheReportIsEscaped() throws IOException {
        // The text goes in as a refusal line, not as a folder's name: Windows refuses <, > and " in a path.
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(workingDirectory, clock, "");
        report.begins("run");
        report.job(aJob(false, 5, "2000"));
        report.said("a<b&c\"d");
        clock.at(5);
        report.job(aJob(false, 6, "2000"));
        String page = page();

        claim("the line is drawn, escaped", () -> assertThat(page).contains("a&lt;b&amp;c&quot;d"));
        claim("and drawn raw nowhere", () -> assertThat(page).doesNotContain("a<b").doesNotContain("c\"d"));
    }

    // ---- the fixtures

    private InvocationReport aReport(Path directory, Clock clock, String template) {
        return new InvocationReport(
                directory, INTERVAL, clock, () -> new DisplayPresence.Answer(true, "display-variable", null), "", template);
    }

    private String page() throws IOException {
        return Files.readString(workingDirectory.resolve(PAGE), StandardCharsets.UTF_8);
    }

    private JsonNode reportJson() {
        return TheReportFile.in(workingDirectory);
    }

    /** The warnings the jar logs while {@code action} runs, of the loggers of the invocation report and its page. */
    private static List<ILoggingEvent> warningsWhile(Runnable action) {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(logged);
        try {
            action.run();
        } finally {
            root.detachAppender(logged);
            logged.stop();
        }
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }

    /** A job with {@code finished} steps done and the next running (or all, when {@code last}), in a database of about {@code bytes}. */
    private static InvocationReport.Reading aJob(boolean last, int finished, String bytes) {
        List<InvocationReport.Step> steps = new ArrayList<>();
        List<String> order = InvocationReport.STEPS_IN_JOB_ORDER;
        for (int i = 0; i < order.size(); i++) {
            String state = i < finished ? "finished" : i == finished && !last ? "running" : last ? "finished" : "not started";
            Instant start = i <= finished || last ? T0.plusSeconds(i * 30L) : null;
            Instant end = start != null && "finished".equals(state) ? start.plusSeconds(25) : null;
            steps.add(new InvocationReport.Step(
                    order.get(i), state, 1_000L * i, 900L * i, i, start, end, start == null ? null : 25L,
                    "running".equals(state) ? "Stage 4b (content redundancy, near-duplicate verdicts): 3 of 7 (42%)" : null,
                    "running".equals(state) ? 3L : null, "running".equals(state) ? 7L : null));
        }
        Map<String, Long> verdicts = new LinkedHashMap<>();
        verdicts.put("broken", 2L);
        verdicts.put("duplicate-of", 51_732_300L);
        Map<String, Double> perMinute = new LinkedHashMap<>();
        perMinute.put("broken", 0.5);
        perMinute.put("duplicate-of", 12.0);
        InvocationReport.Removing removing =
                new InvocationReport.Removing(StepNames.BYTE_LEVEL_REDUCTION, 60_000_000L, 8_267_698L, verdicts, perMinute);
        List<Long> seconds = List.of(0L, 30L, 60L);
        List<InvocationReport.Chart> charts = List.of(
                new InvocationReport.Chart("database", "Database size over time", "Database size (KB)", seconds,
                        List.of(new InvocationReport.Line("size", new double[] {1.0, 2.0, Double.parseDouble(bytes) / 1000}))),
                new InvocationReport.Chart("counts", "Stage 4b (content redundancy, near-duplicate verdicts) over time",
                        "Done (count)", seconds, List.of(new InvocationReport.Line("done", new double[] {1, 2, 3}))),
                new InvocationReport.Chart("verdicts", "Verdicts recorded by byte-level-reduction over time",
                        "Verdicts (count)", seconds,
                        List.of(new InvocationReport.Line("broken", new double[] {0, 1, 2}),
                                new InvocationReport.Line("duplicate-of", new double[] {0, 5, 51_732_300}))));
        return new InvocationReport.Reading(
                last, last ? "completed" : null, steps, Map.<String, Optional<String>>of(), 2_000L, removing, charts,
                new InvocationReport.Runs("none", List.of()), Map.of(), List.of());
    }

    /** A job whose database is each of {@code bytes} in turn, at a second apart, on a chart in the unit that fits. */
    private static InvocationReport.Reading aJobOverBytes(long... bytes) {
        InvocationReport.Reading base = aJob(false, 5, "2000");
        long most = java.util.Arrays.stream(bytes).max().orElse(0);
        int unit = most >= 1_000_000 ? 2 : most >= 1000 ? 1 : 0;
        double divisor = Math.pow(1000, unit);
        List<Long> seconds = new ArrayList<>();
        double[] values = new double[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            seconds.add((long) i);
            values[i] = bytes[i] / divisor;
        }
        List<InvocationReport.Chart> charts = new ArrayList<>(base.charts());
        charts.set(0, new InvocationReport.Chart("database", "Database size over time",
                "Database size (" + new String[] {"B", "KB", "MB"}[unit] + ")", seconds,
                List.of(new InvocationReport.Line("size", values))));
        return new InvocationReport.Reading(
                false, null, base.steps(), base.gateKeys(), bytes[bytes.length - 1], base.removing(), charts, base.runs(),
                base.arrivedAt(), base.faults());
    }

    // ---- small helpers

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            found++;
        }
        return found;
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;").replace("`", "&#x60;").replace("=", "&#x3D;");
    }

    private static Map<String, String> properties(String block) {
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher property = A_PROPERTY.matcher(block);
        while (property.find()) {
            properties.put(property.group(1), property.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        return properties;
    }

    /** WCAG 2.2's contrast ratio of two colours written {@code #rrggbb}. */
    private static double contrast(String one, String other) {
        double a = luminance(one);
        double b = luminance(other);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String colour) {
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(colour.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            channels[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /** A clock the test sets to a number of seconds after {@link #T0}. */
    private static final class TheClock extends Clock {

        private final Instant base;
        private final AtomicReference<Instant> now;

        TheClock(Instant base) {
            this.base = base;
            this.now = new AtomicReference<>(base);
        }

        void at(long secondsAfterTheStart) {
            now.set(base.plusSeconds(secondsAfterTheStart));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
