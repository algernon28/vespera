package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.extraction.ExtractionMetrics;
import io.algernon.vespera.engine.extraction.MeasuredFormRow;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceAtPath;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.Verdicts;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * What the analysis reports write as JSON beside their pages (ADR-362 section 9), one case for each report, added by
 * the ticket of the class that writes it. This ticket's are the review list and the survivor list, each streamed
 * a row at a time beside its page: the same numbers and rows in the page's order, the earlier pair left as it was
 * where the JSON cannot be moved, the schema each follows, and the headline alone copied to {@code report.json}.
 */
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("741")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
class EveryStreamedAnalysisReportWritesItsNumbersTest {

    private static final String MARKER = "ZXQ-the-operators-private-word";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;


    private static final RunId STAGE_TWO_RUN = new RunId("a-stage-2-run");

    private static final String THE_REVIEW_LIST = "extraction-failures";

    private static final String THE_SURVIVOR_LIST = "extraction-survivors";

    private static final OccurrenceAtPath NAMED_FIRST =
            new OccurrenceAtPath(new OccurrenceId(2), new OccurrencePath("a/named-first.pdf"));

    private static final OccurrenceAtPath NAMED_LAST =
            new OccurrenceAtPath(new OccurrenceId(1), new OccurrencePath("c/named-last.txt"));

    /** The survivors as the ledger hands them by path: two, one page. */
    private static final List<OccurrenceAtPath> THE_SURVIVORS_BY_PATH = List.of(NAMED_FIRST, NAMED_LAST);

    private static final RemovedOccurrence THE_FIRST_FAILURE =
            new RemovedOccurrence(new OccurrenceId(1), "a/first.pdf", "internal: it failed");

    private static final RemovedOccurrence THE_SECOND_FAILURE =
            new RemovedOccurrence(new OccurrenceId(2), "b/second \"quoted\".pdf", "timeout: it took too long");

    private static final List<RemovedOccurrence> TWO_FAILURES = List.of(THE_FIRST_FAILURE, THE_SECOND_FAILURE);

    /** What the page of the survivors holds for the pages of a file with none, and the JSON holds as {@code null}. */
    private static final String A_DASH_ON_THE_PAGE = "&mdash;";

    private static final Path SCHEMAS = Path.of("docs", "report");

    private static final String WHAT_THE_READ_SAID = "the database stopped answering partway through the read";

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private Logger reviewLogger;

    private Logger survivorLogger;

    @BeforeEach
    void captureTheListenersLines() {
        reviewLogger = (Logger) LoggerFactory.getLogger(ReviewListListener.class);
        survivorLogger = (Logger) LoggerFactory.getLogger(SurvivorListListener.class);
        logged.start();
        reviewLogger.addAppender(logged);
        survivorLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheListenersLines() {
        reviewLogger.detachAppender(logged);
        survivorLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The review list and the survivor list each write their JSON beside their page, with the page's numbers and rows in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        reviewListener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        survivorListener(survivors(), metrics(), root, workingDirectory).afterStep(completed());

        JsonNode review = json(workingDirectory, THE_REVIEW_LIST);
        JsonNode survivor = json(workingDirectory, THE_SURVIVOR_LIST);
        String reviewPage = page(workingDirectory, THE_REVIEW_LIST);
        String survivorPage = page(workingDirectory, THE_SURVIVOR_LIST);

        claim("the review list's JSON names itself, its page's title and format 1",
                () -> {
                    assertThat(review.get("format").asInt()).isEqualTo(1);
                    assertThat(review.get("analysisReport").asString()).isEqualTo(THE_REVIEW_LIST);
                    assertThat(review.get("title").asString()).isEqualTo("Files stage 2 could not read");
                    assertThat(reviewPage).contains("<h1>Files stage 2 could not read</h1>");
                });
        claim("its headline is the count the page states first, " + TWO_FAILURES.size() + " files, as a count",
                () -> {
                    JsonNode figure = review.get("headline").get(0);
                    assertThat(review.get("headline")).hasSize(1);
                    assertThat(figure.get("key").asString()).isEqualTo("failed");
                    assertThat(figure.get("value").asLong()).isEqualTo(TWO_FAILURES.size());
                    assertThat(figure.get("unit").asString()).isEqualTo("count");
                    assertThat(reviewPage).contains("Files that could not be read: " + TWO_FAILURES.size());
                });
        JsonNode failureRows = review.get("sections").get(0).get("table").get("rows");
        claim("its table holds each file and the reason its page shows, in the page's order",
                () -> {
                    assertThat(failureRows).hasSize(TWO_FAILURES.size());
                    for (int i = 0; i < TWO_FAILURES.size(); i++) {
                        assertThat(failureRows.get(i).get(0).asString()).isEqualTo(TWO_FAILURES.get(i).path());
                        assertThat(failureRows.get(i).get(1).asString()).isEqualTo(TWO_FAILURES.get(i).reason());
                    }
                    assertThat(reviewPage.indexOf(THE_FIRST_FAILURE.path()))
                            .isLessThan(reviewPage.indexOf("b/second"));
                });
        claim("its first section carries the page's paragraph, whole",
                () -> assertThat(review.get("sections").get(0).get("text").get(0).asString())
                        .startsWith("Files that could not be read: " + TWO_FAILURES.size() + ". Each was marked"));

        JsonNode survivorRows = survivor.get("sections").get(0).get("table").get("rows");
        claim("the survivor list's JSON holds a count of the files that came through as its headline",
                () -> {
                    assertThat(survivor.get("analysisReport").asString()).isEqualTo(THE_SURVIVOR_LIST);
                    assertThat(survivor.get("headline").get(0).get("key").asString()).isEqualTo("cameThrough");
                    assertThat(survivor.get("headline").get(0).get("value").asLong()).isEqualTo(THE_SURVIVORS_BY_PATH.size());
                    assertThat(survivorPage).contains("Files that came through: " + THE_SURVIVORS_BY_PATH.size());
                });
        claim("its rows are each file's path, pages and words in the page's order, a file with no page count holding null where the page holds a dash",
                () -> {
                    assertThat(survivorRows).hasSize(THE_SURVIVORS_BY_PATH.size());
                    assertThat(survivorRows.get(0).get(0).asString()).isEqualTo(NAMED_FIRST.path().value());
                    assertThat(survivorRows.get(0).get(1).asLong()).isEqualTo(4);
                    assertThat(survivorRows.get(0).get(2).asLong()).isEqualTo(900);
                    assertThat(survivorRows.get(1).get(0).asString()).isEqualTo(NAMED_LAST.path().value());
                    assertThat(survivorRows.get(1).get(1).isNull()).isTrue();
                    assertThat(survivorRows.get(1).get(2).asLong()).isEqualTo(12);
                    assertThat(survivorPage).contains(A_DASH_ON_THE_PAGE);
                });
        claim("a list of nothing has no table and a headline of zero, as its page has no table",
                () -> {
                    Path empty = Files.createTempDirectory(workingDirectory, "empty");
                    reviewListener(listingEvery(List.of()), root, empty).afterStep(completed());
                    JsonNode none = json(empty, THE_REVIEW_LIST);
                    assertThat(none.get("headline").get(0).get("value").asLong()).isZero();
                    assertThat(none.get("sections").get(0).get("table").isNull()).isTrue();
                    assertThat(page(empty, THE_REVIEW_LIST)).doesNotContain("<table");
                });
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("Where the JSON cannot be put in place, the review list and the survivor list each log one error naming it and go on, and the earlier JSON and the earlier page stay one pair")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(
            @TempDir Path root, @TempDir Path workingDirectory) throws IOException {
        reviewListener(listingEvery(List.of(THE_FIRST_FAILURE)), root, workingDirectory).afterStep(completed());
        survivorListener(survivors(), metrics(), root, workingDirectory).afterStep(completed());
        Map<String, String> before = new LinkedHashMap<>();
        for (String name : List.of(THE_REVIEW_LIST, THE_SURVIVOR_LIST)) {
            before.put(name + ".json", Files.readString(workingDirectory.resolve(name + ".json")));
            before.put(name + ".html", page(workingDirectory, name));
        }
        logged.list.clear();
        for (String name : List.of(THE_REVIEW_LIST, THE_SURVIVOR_LIST)) {
            Files.delete(workingDirectory.resolve(name + ".json"));
            Files.createDirectory(workingDirectory.resolve(name + ".json"));
            Files.writeString(workingDirectory.resolve(name + ".json").resolve("kept"), "a folder cannot be replaced");
        }

        Throwable fromReview = catchThrowable(
                () -> reviewListener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed()));
        Throwable fromSurvivors = catchThrowable(
                () -> survivorListener(survivors(), metrics(), root, workingDirectory).afterStep(completed()));

        claim("nothing was thrown from the end of the step by either",
                () -> {
                    assertThat(fromReview).isNull();
                    assertThat(fromSurvivors).isNull();
                });
        claim("each page is as it was, since the page is not written where its JSON is not in place",
                () -> {
                    assertThat(page(workingDirectory, THE_REVIEW_LIST)).isEqualTo(before.get(THE_REVIEW_LIST + ".html"));
                    assertThat(page(workingDirectory, THE_SURVIVOR_LIST)).isEqualTo(before.get(THE_SURVIVOR_LIST + ".html"));
                });
        claim("each logged one error, naming its JSON",
                () -> {
                    List<String> errors = logged.list.stream()
                            .filter(event -> event.getLevel() == Level.ERROR)
                            .map(ILoggingEvent::getFormattedMessage)
                            .toList();
                    assertThat(errors).hasSize(2);
                    assertThat(errors.get(0)).contains(workingDirectory.resolve(THE_REVIEW_LIST + ".json").toAbsolutePath().toString());
                    assertThat(errors.get(1)).contains(workingDirectory.resolve(THE_SURVIVOR_LIST + ".json").toAbsolutePath().toString());
                });
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("Where the read of the rows fails partway, the earlier JSON and the earlier page of each list are left whole")
    void aReadThatFailsLeavesTheEarlierPair(@TempDir Path root, @TempDir Path workingDirectory) throws IOException {
        reviewListener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        String jsonBefore = Files.readString(workingDirectory.resolve(THE_REVIEW_LIST + ".json"));
        String pageBefore = page(workingDirectory, THE_REVIEW_LIST);

        reviewListener(failingAfterItsFirstRow(), root, workingDirectory).afterStep(completed());

        claim("the JSON and the page before stand, character for character, as one pair",
                () -> {
                    assertThat(Files.readString(workingDirectory.resolve(THE_REVIEW_LIST + ".json"))).isEqualTo(jsonBefore);
                    assertThat(page(workingDirectory, THE_REVIEW_LIST)).isEqualTo(pageBefore);
                });
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The JSON of the review list and of the survivor list follow their schemas, field by field, with their headline's numbers alone")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path workingDirectory) throws IOException {
        reviewListener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        survivorListener(survivors(), metrics(), root, workingDirectory).afterStep(completed());
        for (String name : List.of(THE_REVIEW_LIST, THE_SURVIVOR_LIST)) {
            JsonNode schema = TheReportFile.parse(Files.readString(SCHEMAS.resolve(name + ".schema.json"), StandardCharsets.UTF_8));
            JsonNode written = json(workingDirectory, name);
            List<String> problems = new ArrayList<>();
            validate(written, schema, "$", problems);
            claim(name + "'s JSON follows docs/report/" + name + ".schema.json, field by field",
                    () -> assertThat(problems).isEmpty());
            claim(name + "'s schema's format is a const, the one the jar writes and reads",
                    () -> assertThat(schema.get("properties").get("format").get("const").asInt())
                            .isEqualTo(InvocationReport.FORMAT_OF_A_REPORT.get(name)));
            claim(name + "'s headline holds numbers alone",
                    () -> written.get("headline").forEach(figure -> assertThat(figure.get("value").isNumber()).isTrue()));
        }
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The invocation report copies the headline of the review list and of the survivor list, and no path, name or row of either")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path corpus) throws IOException {
        Files.writeString(corpus.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);
        Files.writeString(corpus.resolve(SeedScriptedExtractionBeans.CONVERTER_FAULT), "a file the converter faults on " + MARKER);

        cli.run("run", corpus.toString());

        JsonNode review = json(workingDirectory, THE_REVIEW_LIST);
        JsonNode survivors = json(workingDirectory, THE_SURVIVOR_LIST);
        JsonNode report = TheReportFile.in(workingDirectory);
        Map<String, JsonNode> byName = new LinkedHashMap<>();
        report.get("analysisReports").forEach(each -> byName.put(each.get("name").asString(), each));
        claim("the review list's JSON names the file that failed, as its page does, and the survivor list's names the file that came through by its marked name",
                () -> {
                    assertThat(review.toString()).contains(SeedScriptedExtractionBeans.CONVERTER_FAULT);
                    assertThat(survivors.toString()).contains(MARKER);
                });
        claim("report.json holds the headline of each of the two lists, as their JSON holds it",
                () -> {
                    assertThat(byName.get(THE_REVIEW_LIST).get("headline")).isEqualTo(review.get("headline"));
                    assertThat(byName.get(THE_SURVIVOR_LIST).get("headline")).isEqualTo(survivors.get("headline"));
                    assertThat(byName.get(THE_REVIEW_LIST).get("headline")).isNotEmpty();
                });
        claim("no field of report.json holds the marker or the failed file's name: no path, name or text of a document, and no row",
                () -> assertThat(TheReportFile.textIn(workingDirectory))
                        .doesNotContain(MARKER)
                        .doesNotContain(SeedScriptedExtractionBeans.CONVERTER_FAULT));
    }

    private static JsonNode json(Path workingDirectory, String name) throws IOException {
        return TheReportFile.parse(Files.readString(workingDirectory.resolve(name + ".json"), StandardCharsets.UTF_8));
    }

    private static String page(Path workingDirectory, String name) throws IOException {
        return Files.readString(workingDirectory.resolve(name + ".html"), StandardCharsets.UTF_8);
    }

    private static StepExecution completed() {
        StepExecution completed = new StepExecution(
                1L, "extraction", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        completed.setExitStatus(ExitStatus.COMPLETED);
        return completed;
    }

    private static StageRuns stageRuns(Ledger ledger, Path root) {
        return new StageRuns(
                ledger, new ImplementationVersions(), null, null, null, null, null, null, null, null, null, null,
                PresetsFixture.shipped(), root, new ExecutionContext()) {
            @Override
            RunId extraction() {
                return STAGE_TWO_RUN;
            }
        };
    }

    private static ReviewListListener reviewListener(Verdicts verdicts, Path root, Path workingDirectory) {
        Ledger ledger = new Ledger(null) {
            @Override
            public Verdicts verdicts() {
                return verdicts;
            }
        };
        return new ReviewListListener(ledger, stageRuns(ledger, root), workingDirectory);
    }

    private static SurvivorListListener survivorListener(
            Verdicts verdicts, ExtractionMetrics metrics, Path root, Path workingDirectory) {
        Ledger ledger = new Ledger(null) {
            @Override
            public Verdicts verdicts() {
                return verdicts;
            }
        };
        return new SurvivorListListener(ledger, metrics, stageRuns(ledger, root), workingDirectory);
    }

    private static Verdicts listingEvery(List<RemovedOccurrence> failures) {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return failures.size();
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                failures.forEach(failure);
            }
        };
    }

    private static Verdicts failingAfterItsFirstRow() {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return 3;
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                failure.accept(THE_FIRST_FAILURE);
                throw new org.springframework.dao.DataAccessResourceFailureException(WHAT_THE_READ_SAID);
            }
        };
    }

    private static Verdicts survivors() {
        return new Verdicts(null) {
            @Override
            public void eachPageOfSurvivors(RunId runId, Consumer<List<OccurrenceId>> page) {
                page.accept(List.of(NAMED_LAST.occurrenceId(), NAMED_FIRST.occurrenceId()));
            }

            @Override
            public void eachPageOfSurvivorsByPath(RunId runId, Consumer<List<OccurrenceAtPath>> page) {
                page.accept(THE_SURVIVORS_BY_PATH);
            }
        };
    }

    /** Four pages and 900 words for the file named first; no page count and 12 words for the one named last. */
    private static ExtractionMetrics metrics() {
        Map<OccurrenceId, Object[]> rows = new LinkedHashMap<>();
        rows.put(NAMED_LAST.occurrenceId(), new Object[] {null, 12});
        rows.put(NAMED_FIRST.occurrenceId(), new Object[] {4, 900});
        return new ExtractionMetrics(null, null) {
            @Override
            public Set<OccurrenceId> recordedAmong(RunId runId, Collection<OccurrenceId> occurrences) {
                Set<OccurrenceId> recorded = new HashSet<>(rows.keySet());
                recorded.retainAll(occurrences);
                return recorded;
            }

            @Override
            public void eachMeasuredFormOf(RunId runId, Collection<OccurrenceId> occurrences, MeasuredFormRow row) {
                rows.forEach((occurrence, measured) -> {
                    if (occurrences.contains(occurrence)) {
                        row.read(occurrence, "en", false, (Integer) measured[1], (Integer) measured[0], 0, 0);
                    }
                });
            }
        };
    }

    /** The instance against the schema: type, enum, const, properties, required, additionalProperties and items. */
    private static void validate(JsonNode instance, JsonNode schema, String path, List<String> problems) {
        if (schema.has("type")) {
            List<String> types = new ArrayList<>();
            if (schema.get("type").isArray()) {
                schema.get("type").forEach(type -> types.add(type.asString()));
            } else {
                types.add(schema.get("type").asString());
            }
            if (types.stream().noneMatch(type -> isOf(instance, type))) {
                problems.add(path + " is not of type " + types + ": " + instance);
                return;
            }
        }
        if (schema.has("enum") && java.util.stream.StreamSupport.stream(schema.get("enum").spliterator(), false)
                .noneMatch(allowed -> allowed.equals(instance))) {
            problems.add(path + " is none of the values allowed: " + instance);
        }
        if (schema.has("const") && !schema.get("const").equals(instance)) {
            problems.add(path + " is not " + schema.get("const"));
        }
        if (instance.isObject() && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            instance.propertyNames().forEach(name -> {
                if (!properties.has(name)) {
                    problems.add(path + "." + name + " is written and the schema does not name it");
                } else {
                    validate(instance.get(name), properties.get(name), path + "." + name, problems);
                }
            });
            if (schema.has("required")) {
                schema.get("required").forEach(name -> {
                    if (!instance.has(name.asString())) {
                        problems.add(path + "." + name.asString() + " is required and not written");
                    }
                });
            }
        }
        if (instance.isArray() && schema.has("items")) {
            for (int i = 0; i < instance.size(); i++) {
                validate(instance.get(i), schema.get("items"), path + "[" + i + "]", problems);
            }
        }
    }

    private static boolean isOf(JsonNode instance, String type) {
        return switch (type) {
            case "object" -> instance.isObject();
            case "array" -> instance.isArray();
            case "string" -> instance.isString();
            case "integer" -> instance.isIntegralNumber();
            case "number" -> instance.isNumber();
            case "boolean" -> instance.isBoolean();
            case "null" -> instance.isNull();
            default -> false;
        };
    }
}
