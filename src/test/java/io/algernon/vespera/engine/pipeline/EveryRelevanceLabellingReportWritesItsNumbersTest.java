package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelledSpread;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Each analysis report writes its numbers as JSON beside its page, from the values the page is drawn from (ADR-362
 * section 9). A case is added to each method by the ticket of the class that writes the report; this class holds the
 * one of {@code RelevanceReportTasklet}, whose page {@code RelevanceLabellingReport} draws.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The analysis reports")
@Issue("746")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryRelevanceLabellingReportWritesItsNumbersTest {

    private static final String RELEVANCE_LABELLING = "relevance-labelling";

    /** A word no part of the report's own wording holds, put in each document's path. */
    private static final String MARKER = JarWrittenAnalysisReports.MARKER;

    private static final String LABEL_FILE = "relevance-labels.yaml";

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-09T10:15:30.123Z");

    /** The bands of the fixture: five, each with the documents it holds, those asked about and the shortfall. */
    private static final int BANDS = 5;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The relevance labelling page's JSON holds every number and row its page holds, in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path directory) throws IOException {
        RelevanceDistribution.Distribution distribution = JarWrittenAnalysisReports.aDistribution();
        LabelledSpread.Spread spread = JarWrittenAnalysisReports.aSpread();
        List<RelevanceLabellingReport.Preview> previews = JarWrittenAnalysisReports.previews();

        RelevanceLabellingReport.write(directory, distribution, previews, spread, null, WRITTEN_AT);

        String page = Files.readString(directory.resolve(RELEVANCE_LABELLING + ".html"), StandardCharsets.UTF_8);
        JsonNode json = TheReportFile.parse(Files.readString(directory.resolve(RELEVANCE_LABELLING + ".json"), StandardCharsets.UTF_8));
        JsonNode headline = json.get("headline");
        JsonNode bands = section(json, "score-spread").get("table").get("rows");
        JsonNode cuts = section(json, "what-each-cut-would-cost").get("table").get("rows");
        JsonNode judged = section(json, "what-the-answers-say-so-far").get("table").get("rows");
        JsonNode sampled = section(json, "the-documents-to-judge").get("table").get("rows");

        claim("the page is still written beside its JSON", () -> assertThat(page).contains("Choosing the relevance cut"));
        claim("the JSON names its report, its format and the instant it was written to the millisecond",
                () -> {
                    assertThat(json.get("format").asInt()).isEqualTo(RelevanceLabellingReport.FORMAT);
                    assertThat(json.get("analysisReport").asString()).isEqualTo(RELEVANCE_LABELLING);
                    assertThat(json.get("writtenAt").asString()).isEqualTo("2026-11-09T10:15:30.123Z");
                });
        claim("the headline states the page's first numbers: 200 scored documents, the lowest and highest score, "
                        + BANDS + " bands, the 1 document put to a person and the 3 judged so far",
                () -> {
                    assertThat(figure(headline, "scored").asLong()).isEqualTo(200L);
                    assertThat(figure(headline, "lowest").asDouble()).isEqualTo(0.80);
                    assertThat(figure(headline, "highest").asDouble()).isEqualTo(0.90);
                    assertThat(figure(headline, "bands").asLong()).isEqualTo(BANDS);
                    assertThat(figure(headline, "sampled").asLong()).isEqualTo(1L);
                    assertThat(figure(headline, "judged").asLong()).isEqualTo(3L);
                });
        claim("a row of the bands' table for each band in the page's order, each with its bounds, documents, those asked about and its shortfall",
                () -> {
                    assertThat(bands).hasSize(BANDS);
                    for (int i = 0; i < BANDS; i++) {
                        RelevanceDistribution.Band band = distribution.bands().get(i);
                        assertThat(bands.get(i).get(0).asString()).isEqualTo("Band " + (i + 1));
                        assertThat(bands.get(i).get(1).asDouble()).isEqualTo(band.lowerBound());
                        assertThat(bands.get(i).get(2).asDouble()).isEqualTo(band.upperBound());
                        assertThat(bands.get(i).get(3).asInt()).isEqualTo(band.documentCount());
                        assertThat(bands.get(i).get(4).asInt()).isEqualTo(band.sampledCount());
                        assertThat(bands.get(i).get(5).asInt()).isEqualTo(band.shortfall());
                        assertThat(page).contains("<td>Band " + (i + 1) + "</td><td>" + two(band.lowerBound()) + "</td><td>"
                                + two(band.upperBound()) + "</td>");
                    }
                });
        claim("a row for each band of the judged answers, " + 2 + " judged of " + 3 + " in the second band and 1 of 1 relevant in the first",
                () -> {
                    assertThat(judged).hasSize(2);
                    assertThat(judged.get(0).get(1).asInt()).isEqualTo(1);
                    assertThat(judged.get(0).get(2).asInt()).isEqualTo(1);
                    assertThat(judged.get(1).get(1).asInt()).isEqualTo(2);
                    assertThat(judged.get(1).get(2).asInt()).isEqualTo(1);
                });
        claim("a row for each candidate cut with its score and the six counts its line on the page states, lowest cut first",
                () -> {
                    assertThat(cuts).hasSize(1);
                    assertThat(cuts.get(0).get(0).asDouble()).isEqualTo(0.84);
                    assertThat(cuts.get(0).get(1).asInt()).isEqualTo(120);
                    assertThat(cuts.get(0).get(2).asInt()).isEqualTo(3);
                    assertThat(cuts.get(0).get(3).asInt()).isEqualTo(2);
                    assertThat(cuts.get(0).get(4).asInt()).isEqualTo(80);
                    assertThat(cuts.get(0).get(5).asInt()).isEqualTo(1);
                    assertThat(cuts.get(0).get(6).asInt()).isEqualTo(0);
                    assertThat(page).contains("Cut at <strong>0.84</strong> and 120 document(s) survive; of the 3 judged above it, 2 were relevant.");
                });
        claim("a row for the one document to judge with its band, path, score, closest seed and opening, as the page shows them",
                () -> {
                    assertThat(sampled).hasSize(1);
                    assertThat(sampled.get(0).get(0).asString()).isEqualTo("Band 2");
                    assertThat(sampled.get(0).get(1).asString()).isEqualTo("reports/" + MARKER + ".pdf");
                    assertThat(sampled.get(0).get(2).asDouble()).isEqualTo(0.83);
                    assertThat(sampled.get(0).get(3).asString()).isEqualTo("seeds/exemplar.pdf");
                    assertThat(sampled.get(0).get(4).asString()).isEqualTo("The quarterly figures were tabled on the third.");
                    assertThat(page).contains("reports/" + MARKER + ".pdf", "The quarterly figures were tabled on the third.");
                });
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("A relevance labelling JSON that cannot be moved in place fails the step as its page does, and the earlier page and JSON stay as they were")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(@TempDir Path directory) throws IOException {
        RelevanceLabellingReport.write(directory, JarWrittenAnalysisReports.aDistribution(), JarWrittenAnalysisReports.previews(), JarWrittenAnalysisReports.aSpread(), null, WRITTEN_AT);
        Path json = directory.resolve(RELEVANCE_LABELLING + ".json");
        Path page = directory.resolve(RELEVANCE_LABELLING + ".html");
        String earlierPage = Files.readString(page, StandardCharsets.UTF_8);
        String earlierJson = Files.readString(json, StandardCharsets.UTF_8);
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(json.resolve("held"), "a folder that is in the JSON's place and holds a file");
        RelevanceDistribution.Distribution another = new RelevanceDistribution.Distribution(
                7, 0.1, 0.2, List.of(new RelevanceDistribution.Band(0, 0.1, 0.2, 7, 0, 0)), List.of());

        claim("the write throws, as a page that cannot be written throws and so fails the step",
                () -> assertThatThrownBy(() -> RelevanceLabellingReport.write(
                                directory, another, List.of(), new LabelledSpread.Spread(List.of(), List.of(), 0), null, WRITTEN_AT))
                        .isInstanceOf(IOException.class));
        claim("the page is not written after a JSON that failed, so the earlier page is as it was, byte for byte",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(earlierPage));
        claim("what stands in the JSON's place is untouched, and no half-written part of the new JSON is left",
                () -> {
                    assertThat(json).isDirectory();
                    assertThat(directory.resolve(RELEVANCE_LABELLING + ".json.part")).doesNotExist();
                });
        Files.delete(json.resolve("held"));
        Files.delete(json);
        Files.writeString(json, earlierJson, StandardCharsets.UTF_8);
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("Each analysis report's JSON follows its schema, its headline numbers alone, and the schema uses only the eight keywords")
    void eachJsonFollowsItsSchema(@TempDir Path directory) throws IOException {
        RelevanceLabellingReport.write(directory, JarWrittenAnalysisReports.aDistribution(), JarWrittenAnalysisReports.previews(), JarWrittenAnalysisReports.aSpread(),
                new RelevanceLabellingReport.IgnoredFloor(0.9, "an-older-model", "a-newer-model"), WRITTEN_AT);
        JsonNode schema = TheReportFile.parse(Files.readString(Path.of("docs", "report", RELEVANCE_LABELLING + ".schema.json"), StandardCharsets.UTF_8));
        JsonNode json = TheReportFile.parse(Files.readString(directory.resolve(RELEVANCE_LABELLING + ".json"), StandardCharsets.UTF_8));

        claim("the JSON holds every field the schema requires and no field it does not name, each of the type it gives",
                () -> assertThat(SchemaWalker.problems(json, schema)).isEmpty());
        claim("the schema's format is the jar's and its analysisReport a const of the report's name",
                () -> {
                    assertThat(schema.get("properties").get("format").get("const").asInt()).isEqualTo(RelevanceLabellingReport.FORMAT);
                    assertThat(schema.get("properties").get("analysisReport").get("const").asString()).isEqualTo(RELEVANCE_LABELLING);
                });
        claim("the schema names no keyword outside the eight and describes every field",
                () -> assertThat(SchemaWalker.keywordProblems(schema)).isEmpty());
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The invocation report copies the relevance labelling headline and no row: a word in every document's path is in the page's JSON and in no field of report.json")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says something");
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed document that says something");
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        Path json = workingDirectory.resolve(RELEVANCE_LABELLING + ".json");
        assumeTrue(Files.isRegularFile(workingDirectory.resolve(LABEL_FILE)), "the run reached the relevance report");
        JsonNode report = TheReportFile.in(workingDirectory);
        JsonNode entry = null;
        for (JsonNode each : report.get("analysisReports")) {
            if (RELEVANCE_LABELLING.equals(each.get("name").asString())) {
                entry = each;
            }
        }
        JsonNode labelling = entry;
        claim("the run wrote the page's JSON beside its page, and it names a document by its path as the page does",
                () -> assertThat(Files.readString(json, StandardCharsets.UTF_8)).contains(MARKER));
        claim("report.json lists the report and copies the headline its JSON holds, figure for figure",
                () -> {
                    assertThat(labelling).isNotNull();
                    assertThat(labelling.get("headline").isArray()).isTrue();
                    JsonNode written = TheReportFile.parse(Files.readString(json, StandardCharsets.UTF_8)).get("headline");
                    assertThat(labelling.get("headline")).isEqualTo(written);
                });
        claim("no row of a report's JSON reaches report.json: the word put in every document's path is in no field of it",
                () -> assertThat(TheReportFile.textIn(workingDirectory)).doesNotContain(MARKER));
    }

    private static JsonNode section(JsonNode json, String id) {
        for (JsonNode section : json.get("sections")) {
            if (id.equals(section.get("id").asString())) {
                return section;
            }
        }
        throw new AssertionError("the JSON holds no section " + id);
    }

    private static JsonNode figure(JsonNode headline, String key) {
        for (JsonNode figure : headline) {
            if (key.equals(figure.get("key").asString())) {
                return figure.get("value");
            }
        }
        throw new AssertionError("the headline holds no figure " + key);
    }

    private static String two(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }
}
