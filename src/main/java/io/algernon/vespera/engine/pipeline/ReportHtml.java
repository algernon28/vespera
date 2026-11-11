package io.algernon.vespera.engine.pipeline;

import com.samskivert.mustache.BasicCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Draws {@code report.html} from {@code report.json} through a Mustache template (ADR-362 sections 5 and 6): the
 * drawing is a function of the report's text alone. The template reads the model's keys and nothing else, so it
 * cannot run code; the model is plain data, {@code report}, the report's tree as written, and {@code drawn}, what a
 * logic-free template cannot compute (formatted figures, the geometry of the charts and the timeline), derived from
 * {@code report} alone. It holds no rule: it decides no verdict, cache key, cluster or text of the deliverable.
 *
 * <p>The jar's template is the resource {@code report.html.mustache}. The operator's, named by
 * {@code vespera.report.template}, is tried once over the invocation's first report and falls back to the jar's, with
 * one warning, on any failure, now or on a later drawing.
 */
final class ReportHtml {

    private static final Logger log = LoggerFactory.getLogger(ReportHtml.class);

    static final String FILE_NAME = "report.html";

    /** The resource beside this class that is the jar's template. */
    static final String TEMPLATE_RESOURCE = "report.html.mustache";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final DateTimeFormatter WRITTEN_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /** JSON keys that are also names of a table or a stage: written so that this class names none (see InvocationReport). */
    private static final String WALK = "Walk".toLowerCase(Locale.ROOT);

    private static final String ARRANGEMENT = "Arrangement".toLowerCase(Locale.ROOT);

    private static final int CHART_WIDTH = 1100;
    private static final int LINE_CHART_WIDTH = 600;
    private static final int CHART_HEIGHT = 260;
    private static final int PLOT_TOP = 36;
    private static final int PLOT_HEIGHT = 168;
    private static final int TIMELINE_STATES = 190;
    private static final int TIMELINE_LABELS = 320;
    private static final int TIMELINE_ROW = 24;
    private static final long RATE_SECONDS = 600;

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** The dash of each series after the first: a second line of equal values is seen beside the first. */
    private static final String[] DASHES = {"", "6 4", "2 3", "8 3 2 3", "1 4"};

    private static final Pattern UNIT_OF_AN_AXIS = Pattern.compile("\\((B|KB|MB|GB|TB)\\)\\s*$");

    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope", "code",
            "extraction-failed", "degenerate-output", "redundant-with", "translation-of", "below-threshold");

    /** What each tile shows, as hover text: one for each tile key and each blocking kind (ADR-275 section 2). */
    private static final Map<String, String> TILE_HOVER = Map.ofEntries(
            Map.entry("steps", "How many of the job's steps have finished or stopped at a gate."),
            Map.entry("step", "The step running now, or the last one to run, with its state."),
            Map.entry("elapsed", "Time since this invocation started."),
            Map.entry("database", "Size of the working directory's database and, where shown, how fast it has grown per minute lately."),
            Map.entry("survivors", "Occurrences the latest step that removes them has not removed, of those that entered it."),
            Map.entry("command", "The vespera command this page reports on."),
            Map.entry("broken", "Occurrences removed because the file is broken and cannot be read."),
            Map.entry("duplicate-of", "Occurrences removed because another file has exactly the same bytes."),
            Map.entry("superseded-by", "Occurrences removed because a newer version of the same file exists."),
            Map.entry("out-of-scope", "Occurrences removed because their format or place is outside what the job reads."),
            Map.entry("code", "Occurrences kept apart as code: text written to be compiled or interpreted, or a reference page a documentation generator wrote, which no later step reads."),
            Map.entry("extraction-failed", "Occurrences removed because the document converter could not turn them into text."),
            Map.entry("degenerate-output", "Occurrences removed because the converted text is too poor or too short to use."),
            Map.entry("redundant-with", "Occurrences removed because their text repeats or is contained in another document."),
            Map.entry("translation-of", "Occurrences removed because they are a translation of a document kept in another language."),
            Map.entry("below-threshold", "Occurrences removed because their relevance scores below the threshold."));

    /**
     * A name against a Map's keys and nothing else: JMustache's own map fetcher also answers {@code entrySet}, a Java
     * member, which a template of the operator's must not reach.
     */
    private static final Mustache.VariableFetcher KEY_FETCHER =
            (ctx, name) -> ctx instanceof Map<?, ?> map && map.containsKey(name) ? map.get(name) : Template.NO_FETCHER_FOUND;

    /** What the compiler reads: a name against a Map's keys, and nothing else (ADR-362 section 5). */
    private static final class KeysOnly extends BasicCollector {
        @Override
        public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
            return ctx instanceof Map<?, ?> ? KEY_FETCHER : null;
        }

        @Override
        public <K, V> Map<K, V> createFetcherCache() {
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * No template loader is given, so a partial compiles and throws as it is drawn, JMustache's own failing loader's
     * {@code UnsupportedOperationException}, which the trial meets.
     */
    private static Mustache.Compiler compiler() {
        return Mustache.compiler().withCollector(new KeysOnly()).defaultValue("");
    }

    private final String named;
    private Template jarTemplate;
    private Template namedTemplate;
    private boolean tried;
    private boolean useNamed;

    /** {@code named} is the value of {@code vespera.report.template}, blank where unset. */
    ReportHtml(String named) {
        this.named = named == null || named.isBlank() ? null : named;
    }

    static Path pathIn(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    /** The path the property names, else null. */
    String named() {
        return named;
    }

    /** {@code named} where the operator's template draws, else {@code jar}. */
    String used() {
        return useNamed ? "named" : "jar";
    }

    boolean tried() {
        return tried;
    }

    /**
     * The trial (ADR-362 section 5): the operator's template is read, compiled and drawn once over {@code reportText};
     * anything that fails is one warning, and the jar's draws from then on.
     */
    void trial(String reportText) {
        tried = true;
        useNamed = false;
        if (named == null) {
            return;
        }
        try {
            Path path = Path.of(named);
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("not absolute");
            }
            Template template;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                template = compiler().compile(reader);
            }
            drawing(template, reportText);
            namedTemplate = template;
            useNamed = true;
        } catch (IOException | RuntimeException | StackOverflowError unusable) {
            warnedOf(unusable);
        }
    }

    private void warnedOf(Throwable e) {
        log.warn("the report template {} could not be used, so the jar's own draws report.html: {}", named,
                e.getClass().getName());
    }

    /** True while the operator's template draws. */
    boolean namedDraws() {
        return useNamed;
    }

    /** The operator's template threw while drawing: one warning, and the jar's draws from here. */
    void fallBack(RuntimeException thrown) {
        useNamed = false;
        namedTemplate = null;
        warnedOf(thrown);
    }

    /** {@code report.html}'s text, drawn from {@code reportText} alone. */
    String draw(String reportText) {
        Template template = useNamed ? namedTemplate : jarTemplate();
        return drawing(template, reportText);
    }

    private Template jarTemplate() {
        if (jarTemplate == null) {
            try (InputStream in = ReportHtml.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("the jar holds no " + TEMPLATE_RESOURCE);
                }
                jarTemplate = compiler().compile(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return jarTemplate;
    }

    private static String drawing(Template template, String reportText) {
        Map<String, Object> report = asMap(plain(MAPPER.readTree(reportText)));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("report", report);
        model.put("drawn", drawn(report));
        StringWriter out = new StringWriter();
        template.execute(model, out);
        return out.toString();
    }

    // ---- plain data

    private static Object plain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.properties().forEach(entry -> map.put(entry.getKey(), plain(entry.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(each -> list.add(plain(each)));
            return list;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        return node.asString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long num(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static double dbl(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static Map<String, Object> object(Object... pairs) {
        return InvocationReport.object(pairs);
    }

    private static Instant instantOf(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    // ---- figures

    private static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** A count: whole, with {@code ,} between groups of three digits (ADR-268 section 6). */
    static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static int unitOf(double bytes) {
        int unit = 0;
        double value = bytes;
        while (unit < SIZE_UNITS.length - 1 && value >= 1000) {
            value /= 1000;
            unit++;
            if (unit < SIZE_UNITS.length - 1 && Math.round(value * 100) >= 100_000) {
                value /= 1000;
                unit++;
            }
        }
        return unit;
    }

    /** A size in the largest unit of B, KB, MB, GB and TB whose value is at least 1 (ADR-268 section 6). */
    static String size(long bytes) {
        int unit = unitOf(bytes);
        if (unit == 0) {
            return count(bytes) + " B";
        }
        return String.format(Locale.ROOT, "%,.2f", bytes / Math.pow(1000, unit)) + " " + SIZE_UNITS[unit];
    }

    private static String clockTime(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    // ---- what the template is given beside the report

    private static Map<String, Object> drawn(Map<String, Object> report) {
        Map<String, Object> invocation = asMap(report.get("invocation"));
        ZoneId zone = ZoneId.systemDefault();
        boolean ended = "ended".equals(str(invocation.get("state")));
        Instant started = instantOf(invocation.get("started"));
        Instant writtenAt = instantOf(invocation.get("writtenAt"));
        Instant until = ended && invocation.get("ended") != null ? instantOf(invocation.get("ended")) : writtenAt;
        String command = str(invocation.get("command"));
        boolean label = command.startsWith("label");
        long seconds = Math.max(1, num(invocation.get("interval")));

        Map<String, Object> drawn = new LinkedHashMap<>();
        drawn.put("title", "Vespera report");
        drawn.put("command", command);
        drawn.put("status", status(invocation, ended, started, until, zone, seconds));
        drawn.put("writtenAt", "Written at " + at(writtenAt, zone));
        drawn.put("refreshSeconds", seconds);
        drawn.put("hasClosing", report.get("closing") != null);

        List<Object> steps = asList(report.get("steps"));
        List<Map<String, Object>> charts = chartsOf(asList(report.get("charts")));
        drawn.put("tiles", tiles(report, invocation, steps, label, started, until));
        Map<String, Object> timeline = steps.isEmpty() ? null : timeline(report, steps, started, until);
        drawn.put("hasTimeline", timeline != null);
        drawn.put("timeline", timeline == null ? Map.of() : timeline);
        drawn.put("hasCharts", !charts.isEmpty());
        drawn.put("charts", charts);
        drawn.put("hasSteps", !steps.isEmpty());
        drawn.put("steps", stepRows(steps, zone));
        drawn.put("gates", gates(asList(report.get("gates"))));
        drawn.put("hasGates", !asList(report.get("gates")).isEmpty());
        drawn.put("next", next(asMap(report.get("next"))));
        drawn.put("hasNext", report.get("next") != null);
        drawn.put("hasRefusals", !asList(report.get("refusals")).isEmpty());
        drawn.put("hasFaults", !asList(report.get("faults")).isEmpty());
        drawn.put("faults", faults(asList(report.get("faults"))));
        drawn.put("analysisReports", analysisReports(asList(report.get("analysisReports"))));
        drawn.put("hasLabel", report.get("label") != null);
        drawn.put("answers", answers(asMap(report.get("label"))));
        drawn.put("runs", runs(asMap(report.get("runs"))));
        drawn.put("hasRuns", !asList(asMap(report.get("runs")).get("rows")).isEmpty());
        drawn.put("files", files(asList(report.get("files"))));
        drawn.put("hasFiles", !asList(report.get("files")).isEmpty());
        drawn.put("display", display(asMap(report.get("display"))));
        drawn.put("opened", opened(report.get("opened")));
        return drawn;
    }

    private static String at(Instant instant, ZoneId zone) {
        return instant == null ? "" : WRITTEN_AT.format(LocalDateTime.ofInstant(instant, zone));
    }

    /** ADR-269 section 2's line while it runs, ADR-327 section 2's sentence after it ends. */
    private static String status(
            Map<String, Object> invocation, boolean ended, Instant started, Instant until, ZoneId zone, long seconds) {
        String startedAt = at(started, zone);
        if (!ended) {
            return "This invocation is running. It started at " + startedAt + ", and this page is rewritten every "
                    + seconds + " s.";
        }
        String outcome = str(invocation.get("outcome"));
        return "This invocation has ended: " + (outcome.isEmpty() ? "ended" : outcome) + ". It started at "
                + startedAt + " and ended at " + at(until, zone) + "."
                + " This page is reloaded every " + seconds + " s, so it shows the next invocation in this working"
                + " directory once one starts.";
    }

    // ---- tiles

    private static Map<String, Object> tile(String key, String label, String value, String detail) {
        return object(
                "key", key, "label", label, "value", value, "detail", detail == null ? "" : detail,
                "hasDetail", detail != null, "hover", TILE_HOVER.get(key));
    }

    private static List<Object> tiles(
            Map<String, Object> report, Map<String, Object> invocation, List<Object> steps, boolean label,
            Instant started, Instant until) {
        List<Object> tiles = new ArrayList<>();
        Duration elapsed = started == null || until == null ? Duration.ZERO : Duration.between(started, until);
        long bytes = num(asMap(report.get("database")).get("bytes"));
        Rate rate = rateOf(asList(report.get("charts")));
        if (label || steps.isEmpty()) {
            tiles.add(tile("command", "Command", str(invocation.get("command")), null));
            tiles.add(tile("elapsed", "Elapsed", clockTime(elapsed), null));
            tiles.add(tile("database", "Database size", size(bytes), growth(rate)));
            return tiles;
        }
        int finished = 0;
        String running = null;
        String latest = null;
        String shownState = null;
        Map<String, String> states = new LinkedHashMap<>();
        for (Object each : steps) {
            Map<String, Object> step = asMap(each);
            String name = str(step.get("name"));
            String state = str(step.get("state"));
            states.put(name, state);
            if (state.equals("finished") || state.equals("stopped at a gate")) {
                finished++;
            }
            if (state.equals("running")) {
                running = name;
            }
            if (!state.equals("not started")) {
                latest = name;
            }
        }
        String shown = running != null ? running : latest;
        shownState = shown == null ? null : states.get(shown);
        tiles.add(tile("steps", "Steps finished", finished + " of " + steps.size(), null));
        tiles.add(shown == null ? tile("step", "Step", "none yet", null) : tile("step", "Step", shown, shownState));
        tiles.add(tile("elapsed", "Elapsed", clockTime(elapsed), null));
        tiles.add(tile("database", "Database size", size(bytes), growth(rate)));
        Map<String, Object> removing = asMap(report.get("removing"));
        if (report.get("removing") != null) {
            String step = str(removing.get("step"));
            tiles.add(tile(
                    "survivors", "Survivors", count(num(removing.get("survivors"))),
                    "of " + count(num(removing.get("entered"))) + " when " + step + " started"
                            + ("running".equals(states.get(step)) ? ", including those not yet judged" : "")));
            Map<String, Object> verdicts = asMap(removing.get("verdicts"));
            Map<String, Object> perMinute = asMap(removing.get("perMinute"));
            for (String kind : BLOCKING) {
                if (!verdicts.containsKey(kind)) {
                    continue;
                }
                String detail = perMinute.containsKey(kind) && rate != null
                        ? String.format(Locale.ROOT, "%,.1f", dbl(perMinute.get(kind))) + over(rate)
                        : "rate not measured yet";
                tiles.add(tile(kind, "code".equals(kind) ? "code (kept)" : kind, count(num(verdicts.get(kind))), detail));
            }
        }
        return tiles;
    }

    /** The database's size at two samples, the base at least ten minutes before the latest where one is (ADR-268 section 3). */
    private record Rate(long baseSeconds, long latestSeconds, double perMinuteBytes) {}

    private static Rate rateOf(List<Object> charts) {
        for (Object each : charts) {
            Map<String, Object> chart = asMap(each);
            if (!"database".equals(chart.get("id"))) {
                continue;
            }
            List<Object> seconds = asList(chart.get("seconds"));
            List<Object> lines = asList(chart.get("lines"));
            if (seconds.size() < 2 || lines.isEmpty()) {
                return null;
            }
            double divisor = Math.pow(1000, unitIndex(str(chart.get("axis"))));
            List<Object> values = asList(asMap(lines.getFirst()).get("values"));
            int latest = seconds.size() - 1;
            int base = 0;
            for (int i = 0; i < latest; i++) {
                if (num(seconds.get(latest)) - num(seconds.get(i)) >= RATE_SECONDS) {
                    base = i;
                }
            }
            long span = num(seconds.get(latest)) - num(seconds.get(base));
            if (base == latest || span <= 0) {
                return null;
            }
            double grown = (dbl(values.get(latest)) - dbl(values.get(base))) * divisor;
            return new Rate(num(seconds.get(base)), num(seconds.get(latest)), grown * 60.0 / span);
        }
        return null;
    }

    private static String over(Rate rate) {
        return " / min over the last " + clockTime(Duration.ofSeconds(rate.latestSeconds() - rate.baseSeconds()));
    }

    private static String growth(Rate rate) {
        if (rate == null) {
            return "growth not measured yet";
        }
        return (rate.perMinuteBytes() < 0 ? "-" : "") + size(Math.round(Math.abs(rate.perMinuteBytes()))) + over(rate);
    }

    private static int unitIndex(String axisTitle) {
        Matcher unit = UNIT_OF_AN_AXIS.matcher(axisTitle);
        if (!unit.find()) {
            return 0;
        }
        return Arrays.asList(SIZE_UNITS).indexOf(unit.group(1));
    }

    // ---- the timeline

    private static Map<String, Object> bar(
            String name, String state, Instant start, Instant end, Instant started, long total, int plot) {
        long from = Math.clamp(Duration.between(started, start).toMillis(), 0, total);
        long to = Math.clamp(Duration.between(started, end).toMillis(), from, total);
        return object(
                "x", f1(TIMELINE_LABELS + (double) from / total * plot),
                "width", f1(Math.max(2.0, (double) (to - from) / total * plot)),
                "title", name + ": " + state + ", " + clockTime(Duration.between(start, end)));
    }

    private static Map<String, Object> timeline(
            Map<String, Object> report, List<Object> steps, Instant started, Instant until) {
        long total = Math.max(1000, Duration.between(started, until).toMillis());
        int plot = CHART_WIDTH - TIMELINE_LABELS - 20;
        Map<String, Object> timeline = object(
                "title", "Timeline of the steps",
                "caption", "The steps fall into four parts. Each part's bar runs from the start of its first step to the end"
                        + " of its last, or to this write while it runs, and is labelled with its state; open a part to see"
                        + " each step's bar.",
                "viewBox", "0 0 " + CHART_WIDTH + " 48",
                "legViewBox", "0 0 " + CHART_WIDTH + " " + TIMELINE_ROW,
                "labelsX", (long) TIMELINE_LABELS,
                "endX", (long) (TIMELINE_LABELS + plot),
                "endText", clockTime(Duration.between(started, until)),
                "titleX", (long) (TIMELINE_LABELS + plot / 2),
                "statesX", (long) TIMELINE_STATES);
        List<Object> legs = new ArrayList<>();
        for (Object eachLeg : asList(report.get("legs"))) {
            Map<String, Object> leg = asMap(eachLeg);
            String name = str(leg.get("name"));
            String state = str(leg.get("state"));
            Instant from = null;
            Instant to = null;
            List<Object> rows = new ArrayList<>();
            int row = 0;
            for (Object eachStep : steps) {
                Map<String, Object> step = asMap(eachStep);
                if (!name.equals(step.get("leg"))) {
                    continue;
                }
                String stepName = str(step.get("name"));
                String stepState = str(step.get("state"));
                Instant start = instantOf(step.get("started"));
                Instant end = step.get("ended") == null ? until : instantOf(step.get("ended"));
                int y = row++ * TIMELINE_ROW;
                Map<String, Object> bar = start == null ? null : bar(stepName, stepState, start, end, started, total, plot);
                if (start != null) {
                    from = from == null || start.isBefore(from) ? start : from;
                    to = to == null || end.isAfter(to) ? end : to;
                }
                rows.add(object(
                        "name", stepName, "state", stepState, "title", StepNames.description(stepName),
                        "textY", (long) (y + 16), "barY", (long) (y + 5),
                        "hasBar", bar != null,
                        "barX", bar == null ? "" : bar.get("x"), "barWidth", bar == null ? "" : bar.get("width"),
                        "barTitle", bar == null ? "" : bar.get("title")));
            }
            Map<String, Object> span = from == null ? null : bar(name, state, from, to, started, total, plot);
            legs.add(object(
                    "name", name, "state", state, "open", Boolean.TRUE.equals(leg.get("open")),
                    "title", name + ": " + state,
                    "hasBar", span != null,
                    "barX", span == null ? "" : span.get("x"), "barWidth", span == null ? "" : span.get("width"),
                    "barTitle", span == null ? "" : span.get("title"),
                    "stepsTitle", "Steps of " + name,
                    "stepsViewBox", "0 0 " + CHART_WIDTH + " " + Math.max(1, rows.size()) * TIMELINE_ROW,
                    "steps", rows));
        }
        timeline.put("legs", legs);
        return timeline;
    }

    // ---- charts

    private record Axis(String title, double lo, double hi, List<Double> ticks, int decimals) {}

    private static List<Map<String, Object>> chartsOf(List<Object> charts) {
        List<Map<String, Object>> drawn = new ArrayList<>();
        for (Object each : charts) {
            Map<String, Object> chart = asMap(each);
            if (asList(chart.get("seconds")).isEmpty() || asList(chart.get("lines")).isEmpty()) {
                continue;
            }
            drawn.add(chart(chart));
        }
        return drawn;
    }

    private static String captionOf(String id) {
        return switch (id) {
            case "database" -> "The size of the database and its write-ahead log on disk at each sample.";
            case "verdicts" -> "The verdicts recorded in this invocation, by kind, at each sample.";
            default -> "The running step's counts at each sample.";
        };
    }

    private static Axis countAxis(String title, double most) {
        double top = Math.max(1, most);
        double step = Math.max(1, niceStep(top));
        List<Double> ticks = new ArrayList<>(List.of(0.0));
        for (double tick = step; tick <= top * 0.88; tick += step) {
            ticks.add(tick);
        }
        ticks.add(top);
        return new Axis(title, 0, top, ticks, 0);
    }

    private static Axis sizeAxis(String title, double lo, double hi, boolean wholeBytes) {
        double step = niceStep(hi - lo);
        if (wholeBytes) {
            step = Math.max(1, step % 1 == 0 ? step : step * 2);
        }
        List<Double> ticks = new ArrayList<>();
        for (long k = (long) Math.ceil(lo / step - 1e-9); k * step <= hi + step * 1e-9; k++) {
            ticks.add(k * step);
        }
        int decimals = 0;
        while (decimals < 9 && Math.abs(step * Math.pow(10, decimals) - Math.rint(step * Math.pow(10, decimals))) > 1e-6) {
            decimals++;
        }
        return new Axis(title, lo, hi, ticks, decimals);
    }

    private static double niceStep(double range) {
        double raw = range / 4;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double multiple : new double[] {1, 2, 2.5, 5}) {
            if (multiple * magnitude >= raw * (1 - 1e-9)) {
                return multiple * magnitude;
            }
        }
        return 10 * magnitude;
    }

    private static Map<String, Object> chart(Map<String, Object> chart) {
        String id = str(chart.get("id"));
        String title = str(chart.get("title"));
        List<Object> secondsRaw = asList(chart.get("seconds"));
        List<Long> seconds = new ArrayList<>();
        secondsRaw.forEach(each -> seconds.add(num(each)));
        List<Object> lineObjects = asList(chart.get("lines"));
        List<String> names = new ArrayList<>();
        List<double[]> values = new ArrayList<>();
        for (Object each : lineObjects) {
            Map<String, Object> line = asMap(each);
            names.add(str(line.get("name")));
            values.add(asList(line.get("values")).stream().mapToDouble(ReportHtml::dbl).toArray());
        }
        int samples = seconds.size();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).length != samples) {
                values.set(i, Arrays.copyOf(values.get(i), samples));
            }
        }
        boolean sizes = "database".equals(id);
        int unit = sizes ? unitIndex(str(chart.get("axis"))) : 0;
        double divisor = Math.pow(1000, unit);
        DoubleFunction<String> show = sizes ? v -> size(Math.round(v * divisor)) : v -> count(Math.round(v));
        Axis axis;
        if (sizes) {
            double[] all = values.getFirst();
            double min = Arrays.stream(all).min().orElse(0);
            double most = Arrays.stream(all).max().orElse(0);
            double span = Math.max(most - min, 1 / divisor);
            double lo = Math.max(0, min - (span - (most - min)) / 2 - span * 0.05);
            double hi = most + (span - (most - min)) / 2 + span * 0.05;
            axis = sizeAxis(str(chart.get("axis")), lo, hi, unit == 0);
        } else {
            double most = values.stream().flatMapToDouble(Arrays::stream).max().orElse(0);
            axis = countAxis(str(chart.get("axis")), most);
        }

        long from = seconds.getFirst();
        long span = Math.max(1, seconds.getLast() - from);
        List<String> tickTexts = new ArrayList<>();
        for (double tick : axis.ticks()) {
            tickTexts.add(String.format(Locale.ROOT, "%,." + axis.decimals() + "f", tick + 0.0));
        }
        int longest = tickTexts.stream().mapToInt(String::length).max().orElse(1);
        int left = Math.max(56, 8 * longest + 8);
        int bottom = PLOT_TOP + PLOT_HEIGHT;
        int n = names.size();
        double[] endY = new double[n];
        String[] labels = new String[n];
        int longestLabel = 0;
        for (int s = 0; s < n; s++) {
            double last = values.get(s)[samples - 1];
            endY[s] = bottom - (last - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
            labels[s] = (names.get(s).isEmpty() || n == 1 ? "" : names.get(s) + " ") + show.apply(last);
            longestLabel = Math.max(longestLabel, labels[s].length());
        }
        List<Integer> byHeight = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byHeight.add(i);
        }
        byHeight.sort((a, b) -> Double.compare(endY[a], endY[b]));
        double[] labelY = new double[n];
        for (int k = 0; k < byHeight.size(); k++) {
            double wanted = endY[byHeight.get(k)] + 4;
            labelY[k] = k == 0 ? wanted : Math.max(wanted, labelY[k - 1] + 14);
        }
        int lowest = byHeight.size() - 1;
        if (lowest >= 0 && labelY[lowest] > bottom + 4) {
            labelY[lowest] = bottom + 4;
            for (int k = lowest - 1; k >= 0; k--) {
                labelY[k] = Math.min(labelY[k], labelY[k + 1] - 14);
            }
        }
        int plotRight = LINE_CHART_WIDTH - 12 - 8 * longestLabel;
        int plot = plotRight - left;

        List<Object> grid = new ArrayList<>();
        for (int i = 0; i < axis.ticks().size(); i++) {
            double y = bottom - (axis.ticks().get(i) - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
            grid.add(object("y", f1(y), "labelY", f1(y + 4), "label", tickTexts.get(i)));
        }
        List<Object> timeTicks = new ArrayList<>();
        TreeSet<Long> ticks = new TreeSet<>();
        for (int i = 0; i <= 4; i++) {
            ticks.add(from + Math.round(span * i / 4.0));
        }
        for (long tick : ticks) {
            timeTicks.add(object(
                    "x", f1(left + (double) (tick - from) / span * plot), "text", clockTime(Duration.ofSeconds(tick))));
        }
        List<Object> legend = new ArrayList<>();
        List<Object> lines = new ArrayList<>();
        double endX = left;
        for (int s = 0; s < n; s++) {
            StringBuilder points = new StringBuilder();
            List<Object> hits = new ArrayList<>();
            double x = left;
            double y = bottom;
            String series = names.get(s).isEmpty() ? "size" : names.get(s);
            for (int i = 0; i < samples; i++) {
                x = left + (double) (seconds.get(i) - from) / span * plot;
                y = bottom - (values.get(s)[i] - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
                points.append(points.isEmpty() ? "" : " ").append(f1(x)).append(',').append(f1(y));
                String when = clockTime(Duration.ofSeconds(seconds.get(i)));
                String shown = show.apply(values.get(s)[i]);
                hits.add(object("cx", f1(x), "cy", f1(y), "title", when + ": " + shown));
            }
            String dash = s == 0 ? "" : DASHES[Math.min(s, DASHES.length - 1)];
            legend.add(object("css", "s" + (s + 1), "name", series));
            lines.add(object(
                    "css", "s" + (s + 1), "dash", dash, "hasDash", !dash.isEmpty(), "points", points.toString(),
                    "hits", hits, "endX", f1(x), "endY", f1(y)));
            endX = x;
        }
        List<Object> endLabels = new ArrayList<>();
        for (int k = 0; k < byHeight.size(); k++) {
            endLabels.add(object("x", f1(endX + 10), "y", f1(labelY[k]), "text", labels[byHeight.get(k)]));
        }
        List<Object> header = new ArrayList<>();
        header.add("Elapsed");
        names.forEach(name -> header.add(name.isEmpty() ? "size" : name));
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            List<Object> cells = new ArrayList<>();
            cells.add(object("text", clockTime(Duration.ofSeconds(seconds.get(i)))));
            for (int s = 0; s < n; s++) {
                cells.add(object("text", show.apply(values.get(s)[i])));
            }
            rows.add(object("cells", cells));
        }
        List<Object> headerCells = new ArrayList<>();
        header.forEach(text -> headerCells.add(object("text", text)));
        return object(
                "id", id, "title", title, "caption", captionOf(id), "viewBox", "0 0 " + LINE_CHART_WIDTH + " " + CHART_HEIGHT,
                "hasLegend", n >= 2, "legend", legend,
                "yTitle", axis.title(), "yTitleX", (long) left,
                "left", (long) left, "plotRight", (long) plotRight, "plotTop", (long) PLOT_TOP, "bottom", (long) bottom,
                "grid", grid, "timeTicks", timeTicks, "timeTickY", (long) (bottom + 18),
                "xTitleX", (long) (left + plot / 2), "xTitleY", (long) (bottom + 40),
                "lines", lines, "endLabels", endLabels,
                "tableTitle", title + ": its values",
                "header", headerCells, "rows", rows);
    }

    // ---- tables of the rest

    private static List<Object> stepRows(List<Object> steps, ZoneId zone) {
        List<Object> rows = new ArrayList<>();
        for (Object each : steps) {
            Map<String, Object> step = asMap(each);
            Instant start = instantOf(step.get("started"));
            Map<String, Object> progress = asMap(step.get("progress"));
            rows.add(object(
                    "name", str(step.get("name")), "state", str(step.get("state")),
                    "read", count(num(step.get("read"))), "written", count(num(step.get("written"))),
                    "skipped", count(num(step.get("skipped"))),
                    "duration", step.get("seconds") == null ? "" : clockTime(Duration.ofSeconds(num(step.get("seconds")))),
                    "startedAt", start == null ? "" : at(start, zone),
                    "progress", str(progress.get("line"))));
        }
        return rows;
    }

    private static List<Object> gates(List<Object> gates) {
        List<Object> rows = new ArrayList<>();
        for (Object each : gates) {
            Map<String, Object> gate = asMap(each);
            List<Object> reports = new ArrayList<>();
            for (Object name : asList(gate.get("analysisReports"))) {
                reports.add(object("file", str(name)));
            }
            rows.add(object(
                    "step", str(gate.get("step")), "hasKey", gate.get("key") != null, "key", str(gate.get("key")),
                    "reports", reports));
        }
        return rows;
    }

    private static Map<String, Object> next(Map<String, Object> next) {
        List<Object> wants = new ArrayList<>();
        asList(next.get("wants")).forEach(each -> wants.add(object("key", str(each))));
        return object(
                "invocation", str(next.get("invocation")), "wants", wants, "hasWants", !wants.isEmpty(),
                "answer", Boolean.TRUE.equals(next.get("answer")),
                "hasArrangement", next.get(ARRANGEMENT) != null, ARRANGEMENT, str(next.get(ARRANGEMENT)));
    }

    private static List<Object> faults(List<Object> faults) {
        List<Object> rows = new ArrayList<>();
        for (Object each : faults) {
            Map<String, Object> fault = asMap(each);
            String causes = String.join(", ", asList(fault.get("causes")).stream().map(ReportHtml::str).toList());
            rows.add(object("step", str(fault.get("step")), "type", str(fault.get("type")), "causes", causes));
        }
        return rows;
    }

    private static List<Object> analysisReports(List<Object> reports) {
        List<Object> rows = new ArrayList<>();
        for (Object each : reports) {
            Map<String, Object> report = asMap(each);
            List<Object> headline = new ArrayList<>();
            for (Object figure : asList(report.get("headline"))) {
                Map<String, Object> one = asMap(figure);
                headline.add(object("label", str(one.get("label")), "value", figureOf(one)));
            }
            rows.add(object(
                    "name", str(report.get("name")), "file", str(report.get("name")) + ".html",
                    "written", Boolean.TRUE.equals(report.get("written")),
                    "hasHeadline", !headline.isEmpty(), "headline", headline));
        }
        return rows;
    }

    private static String figureOf(Map<String, Object> figure) {
        double value = dbl(figure.get("value"));
        return switch (str(figure.get("unit"))) {
            case "count" -> count(Math.round(value));
            case "share" -> String.format(Locale.ROOT, "%.1f%%", value * 100);
            case "bytes" -> size(Math.round(value));
            case "seconds" -> clockTime(Duration.ofSeconds(Math.round(value)));
            default -> String.format(Locale.ROOT, "%,.2f", value);
        };
    }

    private static List<Object> answers(Map<String, Object> label) {
        List<Object> rows = new ArrayList<>();
        for (Object each : asList(label.get("answers"))) {
            Map<String, Object> answer = asMap(each);
            rows.add(object("words", str(answer.get("words")), "count", count(num(answer.get("count")))));
        }
        return rows;
    }

    private static List<Object> runs(Map<String, Object> runs) {
        List<Object> rows = new ArrayList<>();
        for (Object each : asList(runs.get("rows"))) {
            Map<String, Object> run = asMap(each);
            String steps = run.get("stepsOf") == null ? "" : num(run.get("stepsFinished")) + " of " + num(run.get("stepsOf"));
            List<String> verdicts = new ArrayList<>();
            asMap(run.get("verdicts")).forEach((kind, value) -> verdicts.add(kind + " " + count(num(value))));
            rows.add(object(
                    WALK, str(run.get(WALK)), "stage", str(run.get("stage")), "ordinal", str(run.get("ordinal")),
                    "steps", steps, "state", str(run.get("state")),
                    "thisInvocation", Boolean.TRUE.equals(run.get("thisInvocation")) ? "yes" : "",
                    "verdicts", String.join(", ", verdicts)));
        }
        return rows;
    }

    private static List<Object> files(List<Object> files) {
        List<Object> rows = new ArrayList<>();
        for (Object each : files) {
            Map<String, Object> file = asMap(each);
            String path = str(file.get("path"));
            String href;
            try {
                href = Path.of(path).toUri().toString();
            } catch (RuntimeException unaddressable) {
                href = "";
            }
            rows.add(object("role", str(file.get("role")).replace('-', ' '), "path", path, "href", href));
        }
        return rows;
    }

    private static String display(Map<String, Object> display) {
        return (Boolean.TRUE.equals(display.get("present")) ? "A display is present" : "No display is present")
                + " (" + str(display.get("reason")).replace('-', ' ') + ").";
    }

    private static Map<String, Object> opened(Object opened) {
        if (opened == null) {
            return object("known", false, "text", "What this invocation opened is not recorded.", "files", List.of());
        }
        Map<String, Object> map = asMap(opened);
        List<Object> files = new ArrayList<>();
        asList(map.get("files")).forEach(file -> files.add(object("path", str(file))));
        return object(
                "known", true,
                "text", files.isEmpty() ? "Nothing was opened: " + str(map.get("because")).replace('-', ' ') + "." : "Opened:",
                "files", files);
    }
}
