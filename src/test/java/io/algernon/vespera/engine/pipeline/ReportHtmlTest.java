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
    @Story("The drawing is a function of report.json alone")
    @DisplayName("One report.json drawn twice, and at two times of day, draws the same report.html, and the one on disk is that drawing")
    void theDrawingIsAFunctionOfTheReportAlone() throws Exception {
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(workingDirectory, clock, "");
        report.begins("run");
        clock.at(3600);
        report.job(aJob(false, 5, "2000"));
        String text = Files.readString(workingDirectory.resolve("report.json"), StandardCharsets.UTF_8);
        String onDisk = Files.readString(workingDirectory.resolve(PAGE), StandardCharsets.UTF_8);

        String first = new ReportHtml("").draw(text);
        Thread.sleep(1100);
        String second = new ReportHtml("").draw(text);

        claim("the same report.json draws the same report.html, a second apart", () -> assertThat(second).isEqualTo(first));
        claim("and the report.html the jar wrote is the drawing of the text it wrote beside it",
                () -> assertThat(onDisk).isEqualTo(first));
    }

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
    @Story("A partial throws at its first drawing, so the trial meets it")
    @DisplayName("A named template with a partial compiles and throws at its trial drawing: one warning, template.used is jar, and the jar's template draws every write")
    void aPartialCompilesAndThrowsAtItsFirstDrawingSoTheTrialMeetsIt() throws IOException {
        Path template = workingDirectory.resolve("partial.mustache");
        Files.writeString(template, "before {{> name}} after", StandardCharsets.UTF_8);
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(workingDirectory, clock, template.toString());

        List<ILoggingEvent> warnings = warningsWhile(() -> {
            report.begins("run");
            report.job(aJob(false, 3, "2000"));
            clock.at(10);
            report.job(aJob(false, 4, "2000"));
        });

        claim("one warning says the template could not be used and names the exception class that met it",
                () -> assertThat(warnings)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("the report template " + template + " could not be used, so the jar's own draws"
                                + " report.html: java.lang.UnsupportedOperationException"));
        claim("report.json says the path was named and the jar's template drew",
                () -> assertThat(reportJson().path("template").path("named").asString()).isEqualTo(template.toString()));
        claim("and used jar", () -> assertThat(reportJson().path("template").path("used").asString()).isEqualTo("jar"));
        claim("the page is the jar's, at the second write as at the first",
                () -> assertThat(page()).contains("<!DOCTYPE html>").doesNotContain("before"));
    }

    @Test
    @Story("The operator's template is named by an absolute path")
    @DisplayName("vespera.report.template naming a file by an absolute path: report.html is its drawing and template.used is named")
    void aNamedTemplateDrawsTheReport() throws IOException {
        Path template = workingDirectory.resolve("mine.mustache");
        Files.writeString(template, "mine {{report.invocation.command}} {{drawn.refreshSeconds}}s", StandardCharsets.UTF_8);
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), template.toString());
        report.begins("run");
        report.job(aJob(false, 3, "2000"));

        claim("report.html is the operator's template drawn over the report",
                () -> assertThat(page()).isEqualTo("mine run 7s"));
        claim("and report.json names the template and says it drew",
                () -> assertThat(reportJson().path("template").toString())
                        .isEqualTo("{\"named\":\"" + template + "\",\"used\":\"named\"}"));
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
    @Story("Every element says on hover what it shows")
    @DisplayName("Every div of the drawing carries a title of fixed words, and every step of the timeline says what it does")
    void everyDivOfTheShippedTemplateSaysOnHoverWhatItShows() throws IOException {
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), "");
        report.begins("run");
        report.job(aJob(false, 5, "2000"));
        String page = page();

        List<String> divs = new ArrayList<>();
        Matcher div = A_DIV.matcher(page);
        while (div.find()) {
            divs.add(div.group());
        }
        claim("the drawing has divs to look at", () -> assertThat(divs).isNotEmpty());
        for (String each : divs) {
            Matcher title = A_TITLE.matcher(each);
            claim("the div " + each + " carries a title",
                    () -> assertThat(title.find() && !title.group(1).isBlank()).isTrue());
        }
        for (String step : InvocationReport.STEPS_IN_JOB_ORDER) {
            claim("the timeline's mark of " + step + " says what the step does",
                    () -> assertThat(page).contains("data-step=\"" + step + "\"")
                            .contains("<title>" + escaped(StepNames.description(step)) + "</title>"));
        }
    }

    @Test
    @Story("Every text of the report is escaped")
    @DisplayName("A working directory whose name holds <, & and \" is escaped wherever it is drawn")
    void everyTextOfTheReportIsEscaped() throws IOException {
        Path directory = Files.createDirectories(workingDirectory.resolve("a<b&c\"d"));
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(directory, clock, "");
        report.begins("run");
        report.job(aJob(false, 5, "2000"));
        clock.at(5);
        report.job(aJob(false, 6, "2000"));
        String page = Files.readString(directory.resolve(PAGE), StandardCharsets.UTF_8);

        claim("the name is drawn, escaped", () -> assertThat(page).contains("a&lt;b&amp;c&quot;d"));
        claim("and drawn raw nowhere", () -> assertThat(page).doesNotContain("a<b").doesNotContain("c\"d"));
    }

    @Test
    @Story("The label page is its answers and nothing of a job")
    @DisplayName("A label report is drawn with its table of answers, no timeline and no chart")
    void theLabelReportIsDrawnWithItsAnswersAndNoTimeline() throws IOException {
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), "");
        report.begins("label");
        report.labelStarts();
        Map<String, Integer> answered = new LinkedHashMap<>();
        answered.put("recorded as relevant", 3);
        answered.put("recorded as not relevant", 1);
        report.labelEnds("recorded", answered, 0, "Recorded 4 answers.", null);
        String page = page();

        claim("the answers are a table of their words and counts",
                () -> assertThat(page).contains("<td>recorded as relevant</td><td class=\"count\">3</td>")
                        .contains("<td>recorded as not relevant</td><td class=\"count\">1</td>"));
        claim("the tiles are the elapsed time and the database's size",
                () -> assertThat(page).contains("data-tile=\"elapsed\"").contains("data-tile=\"database\""));
        claim("and there is no timeline and no chart",
                () -> assertThat(page).doesNotContain("class=\"timeline").doesNotContain("data-chart=")
                        .doesNotContain("class=\"leg\"").doesNotContain("data-section=\"charts\""));
    }

    @Test
    @Story("The charts stand in a row that wraps, each 600 units wide and legible down to 500 px")
    @DisplayName("Three charts in one row that wraps, each viewBox 600 units wide, legible down to 500 px, each point with a title, and each chart's values in a table beneath")
    void theThreeChartsStandInOneRowThatWrapsEachDrawnSixHundredUnitsWide() throws IOException {
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), "");
        report.begins("run");
        report.job(aJob(false, 5, "2000"));
        String page = page();
        String row = page.substring(page.indexOf("<div class=\"chart-row\""), page.indexOf("</div>\n</section>", page.indexOf("chart-row")));
        List<String> cards = List.of(row.split("<figure class=\"chart-card\">", -1)).subList(1, 4);
        String css = page.substring(page.indexOf("<style>"), page.indexOf("</style>"));

        claim("the row holds three charts, each a figure", () -> assertThat(count(row, "<figure ")).isEqualTo(3));
        claim("and they are the database, the counts and the verdicts, in that order",
                () -> assertThat(List.of(row.indexOf("data-chart=\"database\""), row.indexOf("data-chart=\"counts\""),
                        row.indexOf("data-chart=\"verdicts\""))).isSorted().doesNotContain(-1));
        for (String card : cards) {
            Matcher box = VIEW_BOX.matcher(card);
            claim("a chart's viewBox is 600 units wide", () -> assertThat(box.find() && box.group(1).equals("600")).isTrue());
            claim("each point of a chart has a title that a browser shows on hover",
                    () -> assertThat(A_POINT.matcher(card).results().count()).isGreaterThan(0));
            claim("the chart's values are a table in a details beneath its drawing",
                    () -> assertThat(card.indexOf("</svg>")).isLessThan(card.indexOf("<details>"))
                            .isLessThan(card.indexOf("<table>")));
        }
        claim("each of the 3 samples of the database chart is a point with its title",
                () -> assertThat(A_POINT.matcher(cards.getFirst()).results().count()).isEqualTo(3));
        claim("the row wraps: its columns are as many as 534 px or a third of the row allow",
                () -> assertThat(css).contains("repeat(auto-fill, minmax(max(min(534px, 100%), (100% - 48px) / 3), 1fr))"));
        claim("and a chart is legible down to 500 px: it scrolls sideways under that, and never shrinks",
                () -> assertThat(css).contains("svg.chart { min-width: 500px; }").contains("overflow-x: auto"));
    }

    @Test
    @Story("The timeline draws the four legs, open where the report says")
    @DisplayName("Four details, each open where legs[].open says, its steps in job order, the axis once above them and every drawing 1,100 units wide")
    void theTimelineDrawsTheFourLegsOpenWhereTheReportSaysAndEachStepInJobOrder() throws IOException {
        TheClock clock = new TheClock(T0);
        InvocationReport report = aReport(workingDirectory, clock, "");
        report.begins("run");
        clock.at(600);
        report.job(aJob(false, 7, "2000"));
        JsonNode written = TheReportFile.in(workingDirectory);
        String page = page();
        String timeline = page.substring(page.indexOf("data-section=\"timeline\""), page.indexOf("data-section=\"charts\""));

        claim("there are four legs, one details each", () -> assertThat(count(timeline, "<details class=\"leg\"")).isEqualTo(4));
        for (JsonNode leg : written.path("legs")) {
            String name = leg.path("name").asString();
            Matcher opening = Pattern.compile("<details class=\"leg\" data-leg=\"" + Pattern.quote(name) + "\"([^>]*)>")
                    .matcher(timeline);
            claim("the leg " + name + " is open exactly where legs[].open says, " + leg.path("open").asBoolean(),
                    () -> assertThat(opening.find() && opening.group(1).contains("open")).isEqualTo(leg.path("open").asBoolean()));
        }
        List<String> inPage = Pattern.compile("<g class=\"step\" data-step=\"([^\"]+)\"").matcher(timeline)
                .results().map(match -> match.group(1)).toList();
        claim("the steps stand in job order, each once", () -> assertThat(inPage).isEqualTo(InvocationReport.STEPS_IN_JOB_ORDER));
        claim("the axis is drawn once, above the legs",
                () -> assertThat(count(timeline, "class=\"timeline-axis\"")).isEqualTo(1));
        claim("and the axis comes before the first leg",
                () -> assertThat(timeline.indexOf("timeline-axis")).isLessThan(timeline.indexOf("<details")));
        Matcher box = VIEW_BOX.matcher(timeline);
        int boxes = 0;
        while (box.find()) {
            boxes++;
            String width = box.group(1);
            claim("every drawing of the timeline is 1,100 units wide", () -> assertThat(width).isEqualTo("1100"));
        }
        int drawings = boxes;
        claim("and the timeline has drawings to measure", () -> assertThat(drawings).isEqualTo(1 + 4 + 4));
    }

    @Test
    @Story("The colours are the palette on record, and text is legible in both schemes")
    @DisplayName("The stylesheet's light and dark colours are the data-viz method's palette, whose text inks clear 4.5:1 against the surface and the page in both schemes, and no text wears a series' colour")
    void theStylesheetsColoursAreTheDataVizPaletteAndTextClearsFourPointFiveToOne() throws IOException {
        InvocationReport report = aReport(workingDirectory, new TheClock(T0), "");
        report.begins("run");
        report.job(aJob(false, 5, "2000"));
        String page = page();

        Matcher darkBlock = DARK_SCHEME.matcher(page);
        claim("the stylesheet has a dark scheme", () -> assertThat(darkBlock.find()).isTrue());
        Map<String, String> dark = properties(darkBlock.group(1));
        Matcher lightBlock = LIGHT_SCHEME.matcher(page.substring(0, darkBlock.start()) + page.substring(darkBlock.end()));
        claim("and a light one", () -> assertThat(lightBlock.find()).isTrue());
        Map<String, String> light = properties(lightBlock.group(1));

        Map<String, String> expectedLight = new LinkedHashMap<>();
        Map<String, String> expectedDark = new LinkedHashMap<>();
        String[][] palette = {
            {"page", "#f9f9f7", "#0d0d0d"}, {"surface", "#fcfcfb", "#1a1a19"}, {"text", "#0b0b0b", "#f0efec"},
            {"muted", "#52514e", "#c3c2b7"}, {"grid", "#e1e0d9", "#2c2c2a"}, {"axis", "#c3c2b7", "#383835"},
            {"series-1", "#2a78d6", "#3987e5"}, {"series-2", "#eb6834", "#d95926"}, {"series-3", "#1baf7a", "#199e70"},
            {"series-4", "#eda100", "#c98500"}, {"series-5", "#e87ba4", "#d55181"},
            {"finished", "#0ca30c", "#0ca30c"}, {"gate", "#fab219", "#fab219"}, {"failed", "#d03b3b", "#d03b3b"},
            {"running", "#2a78d6", "#3987e5"}
        };
        for (String[] role : palette) {
            expectedLight.put(role[0], role[1]);
            expectedDark.put(role[0], role[2]);
        }
        // A precondition of the palette written out above, not a claim about the page: once the page's colours are
        // these, each ratio is a fixed number (WCAG 2.2, 1.4.3).
        for (Map<String, String> scheme : List.of(expectedLight, expectedDark)) {
            for (String ink : List.of("text", "muted")) {
                for (String ground : List.of("surface", "page")) {
                    Assumptions.assumeTrue(contrast(scheme.get(ink), scheme.get(ground)) >= 4.5);
                }
            }
        }
        claim("the light scheme's colours are the palette's light values",
                () -> assertThat(light).containsAllEntriesOf(expectedLight));
        claim("the dark scheme's colours are the palette's dark values",
                () -> assertThat(dark).containsAllEntriesOf(expectedDark));
        claim("no text wears a series' colour: a series colours a line, a marker and a key, and a text is in an ink",
                () -> assertThat(page).doesNotContainPattern("<text[^>]*class=\"[^\"]*\\bs[0-9]\\b")
                        .doesNotContainPattern("svg text \\{[^}]*series"));
    }

    @Test
    @Story("A size is written in a unit that keeps it at least 1, a count grouped by thousands")
    @DisplayName("0, 2,050 and 1,234,567 bytes are 0 B, 2.05 KB and 1.23 MB on the tile, in the tables and in the points' titles; 51,732,300 is grouped by thousands; every drawing follows a heading and a caption and titles its axes")
    void sizesAreInAUnitThatKeepsThemAtLeastOneAndCountsAreGroupedByThousands() throws IOException {
        Map<Long, String> written = Map.of(0L, "0 B", 2_050L, "2.05 KB", 1_234_567L, "1.23 MB");
        for (Map.Entry<Long, String> each : written.entrySet()) {
            Path directory = Files.createDirectories(workingDirectory.resolve("tile-" + each.getKey()));
            InvocationReport report = aReport(directory, new TheClock(T0), "");
            report.begins("run");
            report.job(aJobOverBytes(each.getKey()));
            String page = Files.readString(directory.resolve(PAGE), StandardCharsets.UTF_8);
            claim(each.getKey() + " bytes are " + each.getValue() + " on the tile",
                    () -> assertThat(page).containsPattern("data-tile=\"database\"[^>]*><span class=\"label\">Database size"
                            + "</span><span class=\"value\">" + Pattern.quote(each.getValue()) + "</span>"));
        }

        InvocationReport report = aReport(workingDirectory, new TheClock(T0), "");
        report.begins("run");
        report.job(aJobOverBytes(0, 2_050, 1_234_567));
        String page = page();
        String database = page.substring(page.indexOf("data-chart=\"database\""), page.indexOf("data-chart=\"counts\""));
        claim("the points' titles of the chart say the sizes in the unit that keeps each at least 1",
                () -> assertThat(A_POINT.matcher(database).results().map(match -> match.group(1)).toList())
                        .containsExactly("0:00:00: 0 B", "0:00:01: 2.05 KB", "0:00:02: 1.23 MB"));
        claim("and so does its table of values",
                () -> assertThat(database).contains("<td>0 B</td>").contains("<td>2.05 KB</td>").contains("<td>1.23 MB</td>"));

        claim("a count of 51,732,300 is grouped by thousands in the tile",
                () -> assertThat(page).contains("<span class=\"value\">51,732,300</span>"));

        Matcher headed = HEADED_DRAWING.matcher(page);
        int drawings = 0;
        while (headed.find()) {
            drawings++;
        }
        int found = drawings;
        claim("each chart and the timeline follow a heading and a caption", () -> assertThat(found).isEqualTo(4));
        claim("and every chart titles its axes",
                () -> assertThat(count(page, "class=\"axis-title y\"")).isEqualTo(3));
        claim("both of them", () -> assertThat(count(page, "class=\"axis-title x\"")).isEqualTo(3 + 1));
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
