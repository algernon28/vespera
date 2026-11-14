package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.CodeSets;
import io.algernon.vespera.engine.corpus.ConfigurationFiles;
import io.algernon.vespera.engine.corpus.DetectedFormat;
import io.algernon.vespera.engine.corpus.FormatMix;
import io.algernon.vespera.engine.embedding.LabelledSpread;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.SeedCorpusComparison;
import io.algernon.vespera.engine.embedding.Translations;
import io.algernon.vespera.engine.extraction.ConfidenceDistribution;
import io.algernon.vespera.engine.extraction.WordCountDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.similarity.DocumentFrequency;
import io.algernon.vespera.ui.Reports;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each analysis report's native view shows what its page shows (ADR-362 §10), and the title it shows is the one the
 * control panel links it by (ADR-377 §5): each of the twelve writers gives its page its {@code <title>} and first
 * heading, and its JSON its {@code title}, as {@code Reports.TITLES} names it. Each writer writes over the smallest
 * fixture it takes, its title being the same whatever it counts.
 */
@Epic("Reports")
@Feature("Each analysis report writes its numbers")
@Issue("806")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-08T10:00:00Z");

    private static final RunId A_RUN = new RunId("a-run");

    private static final Pattern PAGE_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

    private static final Pattern FIRST_HEADING = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL);

    @Test
    @Story("A report reads by one title everywhere")
    @DisplayName("Each report's page title, first heading and JSON title are the title the control panel links it by")
    void eachReportsTitleIsTheOneTheControlPanelLinksItBy(@TempDir Path base) throws Exception {
        Map<String, Written> written = new LinkedHashMap<>();
        written.put(FormatMixReport.NAME, formatMix());
        written.put(ReviewListReport.NAME, reviewList());
        written.put(SurvivorListReport.NAME, survivorList());
        written.put(ConfidenceDistributionReport.NAME, confidence());
        written.put(DocumentFrequencyReport.NAME, documentFrequency());
        written.put(WordCountDistributionReport.NAME, wordCounts());
        written.put(SeedCorpusComparisonReport.NAME, seedCorpus());
        written.put(TranslationReport.NAME, translations(base));
        written.put(ClusterSizeReport.NAME, clusterSizes());
        written.put(RelevanceLabellingReport.NAME, relevance());
        written.put(ArrangementReport.NAME, arrangement(base));
        written.put("cluster-page-sizes", clusterPageSizes(base));

        claim("the twelve writers are those of the twelve reports the control panel lists, in its order",
                () -> assertThat(written.keySet()).containsExactlyElementsOf(Reports.ALL));
        written.forEach((name, report) -> {
            String title = Reports.TITLES.get(name);
            claim("the " + name + " page's title is \"" + title + "\"",
                    () -> assertThat(first(PAGE_TITLE, report.page())).isEqualTo(title));
            claim("its first heading is the same title",
                    () -> assertThat(first(FIRST_HEADING, report.page())).isEqualTo(title));
            claim("and its JSON's title is the same title",
                    () -> assertThat(JSON.readTree(report.json()).get("title").asString()).isEqualTo(title));
        });
    }

    /** What one writer wrote: its page and its JSON. */
    private record Written(String page, String json) {
    }

    private static Written formatMix() throws IOException {
        Map<DetectedFormat, Integer> none = new EnumMap<>(DetectedFormat.class);
        FormatMixReport.Mix mix = new FormatMixReport.Mix(none, Map.of(), Map.of(), 0, 0, 0, 0,
                new int[FormatMix.TIMESTAMP_BANDS], 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(),
                List.of(), new CodeSets.Tally(0, 0, 0, 0, Map.of()), ConfigurationFiles.nothing(), null);
        return new Written(FormatMixReport.render(mix), jsonOf(FormatMixReport.document(mix, WRITTEN_AT)));
    }

    private static Written reviewList() throws IOException {
        StringWriter page = new StringWriter();
        StringWriter json = new StringWriter();
        ReviewListReport.write(page, json, 1, WRITTEN_AT,
                each -> each.accept(new RemovedOccurrence(new OccurrenceId(1), "a/one", "could not be read")));
        return new Written(page.toString(), json.toString());
    }

    private static Written survivorList() throws IOException {
        StringWriter page = new StringWriter();
        StringWriter json = new StringWriter();
        SurvivorListReport.write(page, json, 1, WRITTEN_AT,
                each -> each.accept(new SurvivorListReport.Row("a/one", 3, 40)));
        return new Written(page.toString(), json.toString());
    }

    private static Written confidence() throws IOException {
        ConfidenceDistribution.Distribution distribution = new ConfidenceDistribution.Distribution(
                List.of(new ConfidenceDistribution.Bucket("good", 0.5, 1.0, 2)), 0, A_RUN);
        return new Written(ConfidenceDistributionReport.render(distribution),
                jsonOf(ConfidenceDistributionReport.json(distribution, WRITTEN_AT)));
    }

    private static Written documentFrequency() throws IOException {
        DocumentFrequency.Shares shares = new DocumentFrequency.Shares(2,
                List.of(new DocumentFrequency.ShareLine(new BigDecimal("0.5"), 3, 1)));
        return new Written(DocumentFrequencyReport.render(shares, A_RUN),
                jsonOf(DocumentFrequencyReport.json(shares, WRITTEN_AT)));
    }

    private static Written wordCounts() throws IOException {
        List<Long> oneInEachBand = WordCountDistribution.BANDS.stream().map(band -> 1L).toList();
        WordCountDistribution.Distribution distribution =
                new WordCountDistribution.Distribution(Map.of(DetectedFormat.values()[0], oneInEachBand));
        return new Written(WordCountDistributionReport.render(distribution, A_RUN),
                jsonOf(WordCountDistributionReport.json(distribution, WRITTEN_AT)));
    }

    private static Written seedCorpus() throws IOException {
        SeedCorpusComparison.Comparison comparison =
                new SeedCorpusComparison.Comparison(1, 2, 0, List.of(), List.of(), List.of());
        return new Written(SeedCorpusComparisonReport.render(comparison),
                jsonOf(SeedCorpusComparisonReport.json(comparison, WRITTEN_AT)));
    }

    private static Written translations(Path base) throws IOException {
        Translations.Resolution resolution = new Translations.Resolution(List.of(), List.of(), 0, 0, 0, new TreeMap<>());
        Path json = Files.createDirectories(base.resolve("translations")).resolve("translations.json");
        TranslationReport.writeJson(json, WRITTEN_AT, resolution, A_RUN, "a model", 2, 0, "unset", "unset", false);
        return new Written(TranslationReport.render(resolution, A_RUN, "a model", 2, 0, "unset", "unset", false),
                Files.readString(json, StandardCharsets.UTF_8));
    }

    private static Written clusterSizes() throws IOException {
        List<ClusterSizeReport.Partition> partitions =
                List.of(ClusterSizeReport.Partition.of("seeds/one.docx", List.of(2, 1), Optional.empty()));
        return new Written(ClusterSizeReport.render(partitions),
                jsonOf(ClusterSizeReport.document(partitions, WRITTEN_AT)));
    }

    private static Written relevance() throws IOException {
        RelevanceDistribution.Distribution distribution = new RelevanceDistribution.Distribution(2, 0.8, 0.9,
                List.of(new RelevanceDistribution.Band(0, 0.8, 0.9, 2, 1, 0)),
                List.of(new RelevanceDistribution.Sampled(new OccurrenceId(1), 0.83, new OccurrenceId(2), 0)));
        LabelledSpread.Spread noAnswers = new LabelledSpread.Spread(List.of(), List.of(), 0);
        return new Written(RelevanceLabellingReport.render(distribution, List.of(), noAnswers, null),
                jsonOf(RelevanceLabellingReport.json(distribution, List.of(), noAnswers, null, WRITTEN_AT)));
    }

    private static Written arrangement(Path base) throws IOException {
        Path lead = Files.createDirectories(base.resolve("archive")).resolve("lead.docx");
        ArrangementReport.Partition partition = new ArrangementReport.Partition("seeds/one.docx",
                List.of(new ArrangementReport.Cluster("A group", 2, "lead.docx", lead.toUri().toString())));
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, "an-approval-name", base.resolve("archive").toString());
        ArrangementReport.partition(page, partition);
        ArrangementReport.close(page, 1);
        StringWriter json = new StringWriter();
        AnalysisReportJson report =
                ArrangementReport.openJson(json, "an-approval-name", base.resolve("archive").toString(), WRITTEN_AT);
        ArrangementReport.partitionJson(report, 1, partition);
        ArrangementReport.closeJson(report, 1, 1, 2);
        return new Written(page.toString(), json.toString());
    }

    private static Written clusterPageSizes(Path base) throws IOException {
        Path work = ClusterPageSizesDrawn.under(base.resolve("drawn"));
        return new Written(Files.readString(work.resolve("cluster-page-sizes.html"), StandardCharsets.UTF_8),
                Files.readString(work.resolve("cluster-page-sizes.json"), StandardCharsets.UTF_8));
    }

    /** {@code document} as {@link AnalysisReportJson} writes it into a file. */
    private static String jsonOf(AnalysisReportJson.Document document) throws IOException {
        Path file = Files.createTempFile("report", ".json");
        try {
            AnalysisReportJson.write(file, document);
            return Files.readString(file, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** The text of {@code pattern}'s first match in {@code html}, tags taken out and the escapes undone; null where none. */
    private static String first(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").strip();
    }
}
