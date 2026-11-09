package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.LabelledSpread;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The page a person reads before choosing a relevance threshold (ADR-088) — plain, hand-assembled
 * HTML, no templating library, the shared {@link ReportPage} module the reports beside the database
 * all supply their title, prose and rows to (ADR-046, ADR-130).
 *
 * <p><b>It never declines to proceed.</b> ADR-088 turns ADR-028's go/no-go from an engine refusal
 * into a human reading of this page, recorded as the provenance of whatever threshold is set. A
 * mechanical shape test would itself be an unmeasured threshold: a cosine-similarity spread over one
 * archive is very often tight and unimodal even when the seed set is sound.
 *
 * <p><b>What "no" means is in the page's own words.</b> A spread with no usable separation is a seed
 * set problem rather than a threshold problem, and the response is a different seed folder and a
 * re-score — not a cut picked out of a distribution with no boundary in it to find. A reader told
 * only the numbers has no way to reach that conclusion, and every available action on the page would
 * be the wrong one.
 */
final class RelevanceLabellingReport {

    private RelevanceLabellingReport() {}

    /** The name the page carries in the working directory, beside the database and the profile. */
    static final String FILE_NAME = "relevance-labelling.html";

    /**
     * What a person needs on screen to answer one question without opening the file.
     *
     * @param occurrenceId which sampled document this belongs to
     * @param path the document as the walk spells it relative to the corpus root (ADR-051)
     * @param winningSeedPath the seed it scored closest to
     * @param textOpening the start of its extracted text, so the common case is answerable here
     */
    record Preview(OccurrenceId occurrenceId, String path, String winningSeedPath, String textOpening) {}

    /**
     * Why a threshold an operator did set is not being applied, or nothing where one is.
     *
     * @param value the number being ignored, named so a reader recognises it as theirs
     * @param calibratedUnder the scale the answers behind the number were given on
     * @param currentIdentity the scale this run scored on
     */
    record IgnoredFloor(double value, String calibratedUnder, String currentIdentity) {}

    /** The name of the page's JSON beside it, {@code relevance-labelling.json} (ADR-362 section 9). */
    static final String NAME = "relevance-labelling";

    /** The format of the page's JSON, raised by a change that removes, renames or retypes a field. */
    static final int FORMAT = 1;

    private static AnalysisReportJson.Section prose(String id, String title, List<String> text) {
        return section(id, title, text, null, List.of(), List.of());
    }

    private static AnalysisReportJson.Section section(
            String id,
            String title,
            List<String> text,
            AnalysisReportJson.Chart chart,
            List<AnalysisReportJson.Column> columns,
            List<List<Object>> rows) {
        return new AnalysisReportJson.Section(
                id, title, text, chart, columns.isEmpty() ? null : new AnalysisReportJson.Table(columns, rows));
    }

    /**
     * Writes the page's JSON and then the page in {@code directory}, from the one set of values, and throws where
     * either cannot be written. The JSON is moved into place first, so that a JSON that cannot be moved leaves the
     * earlier page, and the earlier JSON, as they were (ADR-362 section 9).
     */
    static void write(
            Path directory,
            RelevanceDistribution.Distribution distribution,
            List<Preview> previews,
            LabelledSpread.Spread spread,
            IgnoredFloor ignoredFloor,
            Instant writtenAt)
            throws IOException {
        AnalysisReportJson.write(directory.resolve(NAME + ".json"), json(distribution, previews, spread, ignoredFloor, writtenAt));
        Files.writeString(
                directory.resolve(FILE_NAME), render(distribution, previews, spread, ignoredFloor), StandardCharsets.UTF_8);
    }

    /**
     * What the page shows as JSON: the headline numbers, then the page's parts in its order, each table's rows in the
     * page's order. A document is named by its path and its opening as the page names it, and no number is
     * rounded: the page rounds the scores it draws to two places, the JSON holds them whole.
     */
    static AnalysisReportJson.Document json(
            RelevanceDistribution.Distribution distribution,
            List<Preview> previews,
            LabelledSpread.Spread spread,
            IgnoredFloor ignoredFloor,
            Instant writtenAt) {
        List<AnalysisReportJson.Figure> headline = List.of(
                new AnalysisReportJson.Figure("scored", "Documents scored", distribution.scoredDocumentCount(), "count"),
                new AnalysisReportJson.Figure("lowest", "Lowest score", distribution.lowestScore(), "number"),
                new AnalysisReportJson.Figure("highest", "Highest score", distribution.highestScore(), "number"),
                new AnalysisReportJson.Figure("bands", "Bands", distribution.bands().size(), "count"),
                new AnalysisReportJson.Figure("sampled", "Documents put to a person", distribution.sample().size(), "count"),
                new AnalysisReportJson.Figure("judged", "Documents judged so far", spread.labelled(), "count"));
        List<AnalysisReportJson.Section> sections = new ArrayList<>();
        if (ignoredFloor != null) {
            sections.add(prose(
                    "threshold-not-applied",
                    "Your threshold is not being applied",
                    List.of("The profile sets a relevance cut of " + ignoredFloor.value()
                            + ", and this run kept every document anyway. The answers this number was read off were"
                            + " given while " + ignoredFloor.calibratedUnder() + " was in use, and the scores below were"
                            + " produced by " + ignoredFloor.currentIdentity() + ".")));
        }
        sections.add(spreadSection(distribution));
        sections.add(prose(
                "if-the-bands-look-alike",
                "If the bands look alike",
                List.of("A spread where every band holds much the same documents, and the ones you judge relevant are"
                        + " scattered evenly through all of them, is telling you something about the seed set rather than"
                        + " about the threshold: a different seed folder and another scoring run, not a cut chosen"
                        + " from this page anyway.")));
        sections.addAll(answerSections(spread));
        sections.add(documentsSection(distribution, previews));
        return new AnalysisReportJson.Document(FORMAT, NAME, "Choosing the relevance cut", writtenAt, headline, sections);
    }

    private static AnalysisReportJson.Section spreadSection(RelevanceDistribution.Distribution distribution) {
        List<List<Object>> rows = new ArrayList<>();
        for (RelevanceDistribution.Band band : distribution.bands()) {
            rows.add(List.of(
                    "Band " + (band.ordinal() + 1),
                    band.lowerBound(),
                    band.upperBound(),
                    band.documentCount(),
                    band.sampledCount(),
                    band.shortfall()));
        }
        return section(
                "score-spread",
                "How the scores are spread",
                List.of(distribution.scoredDocumentCount() + " document(s) were scored, between "
                                + format(distribution.lowestScore()) + " and " + format(distribution.highestScore())
                                + ", divided into " + distribution.bands().size() + " equal bands.",
                        "A band holding fewer documents than were asked for supplies what it has, and the difference"
                                + " is shown rather than made up from a neighbouring band."),
                new AnalysisReportJson.Chart("bars", "band", List.of("documents", "asked")),
                List.of(
                        new AnalysisReportJson.Column("band", "Band", "text"),
                        new AnalysisReportJson.Column("from", "From", "number"),
                        new AnalysisReportJson.Column("to", "To", "number"),
                        new AnalysisReportJson.Column("documents", "Documents", "count"),
                        new AnalysisReportJson.Column("asked", "Being asked about", "count"),
                        new AnalysisReportJson.Column("short", "Short by", "count")),
                rows);
    }

    private static List<AnalysisReportJson.Section> answerSections(LabelledSpread.Spread spread) {
        if (spread.labelled() == 0) {
            return List.of(prose(
                    "what-the-answers-say-so-far",
                    "What the answers say so far",
                    List.of("Nobody has answered any of the questions yet.")));
        }
        List<List<Object>> judgedRows = new ArrayList<>();
        for (LabelledSpread.BandLabels band : spread.bandLabels()) {
            judgedRows.add(List.of("Band " + (band.bandOrdinal() + 1), band.labelled(), band.labelledRelevant()));
        }
        List<List<Object>> cutRows = new ArrayList<>();
        for (LabelledSpread.CandidateCut cut : spread.cuts()) {
            cutRows.add(List.of(
                    cut.score(),
                    cut.surviving(),
                    cut.labelledAbove(),
                    cut.labelledRelevantAbove(),
                    cut.discarded(),
                    cut.labelledBelow(),
                    cut.labelledRelevantBelow()));
        }
        return List.of(
                section(
                        "what-the-answers-say-so-far",
                        "What the answers say so far",
                        List.of(spread.labelled() + " document(s) judged so far, spread across the bands."),
                        new AnalysisReportJson.Chart("bars", "band", List.of("judged", "relevant")),
                        List.of(
                                new AnalysisReportJson.Column("band", "Band", "text"),
                                new AnalysisReportJson.Column("judged", "Judged", "count"),
                                new AnalysisReportJson.Column("relevant", "Judged relevant", "count")),
                        judgedRows),
                section(
                        "what-each-cut-would-cost",
                        "What each cut would cost",
                        List.of("One line per place the cut could go. Nothing here recommends one."),
                        null,
                        List.of(
                                new AnalysisReportJson.Column("cut", "Cut at", "number"),
                                new AnalysisReportJson.Column("surviving", "Documents that survive", "count"),
                                new AnalysisReportJson.Column("judgedAbove", "Judged above it", "count"),
                                new AnalysisReportJson.Column("relevantAbove", "Relevant above it", "count"),
                                new AnalysisReportJson.Column("discarded", "Documents discarded", "count"),
                                new AnalysisReportJson.Column("judgedBelow", "Judged below it", "count"),
                                new AnalysisReportJson.Column("relevantBelow", "Relevant below it", "count")),
                        cutRows));
    }

    private static AnalysisReportJson.Section documentsSection(
            RelevanceDistribution.Distribution distribution, List<Preview> previews) {
        List<List<Object>> rows = new ArrayList<>();
        for (RelevanceDistribution.Sampled sampled : distribution.sample()) {
            Optional<Preview> preview = previews.stream()
                    .filter(candidate -> candidate.occurrenceId().equals(sampled.occurrenceId()))
                    .findFirst();
            rows.add(List.of(
                    "Band " + (sampled.bandOrdinal() + 1),
                    preview.map(Preview::path).orElse("(path not recorded)"),
                    sampled.score(),
                    preview.map(Preview::winningSeedPath).orElse("(seed not recorded)"),
                    preview.map(Preview::textOpening).orElse("(no text was extracted)")));
        }
        return section(
                "the-documents-to-judge",
                "The documents to judge",
                List.of(distribution.sample().isEmpty()
                        ? "No document was sampled, because nothing was scored."
                        : "Each one below is drawn from a band, and the same run always asks about the same documents."),
                null,
                distribution.sample().isEmpty()
                        ? List.of()
                        : List.of(
                                new AnalysisReportJson.Column("band", "Band", "text"),
                                new AnalysisReportJson.Column("document", "Document", "text"),
                                new AnalysisReportJson.Column("score", "Score", "number"),
                                new AnalysisReportJson.Column("seed", "Closest seed", "text"),
                                new AnalysisReportJson.Column("opening", "How it begins", "text")),
                rows);
    }

    static String render(
            RelevanceDistribution.Distribution distribution,
            List<Preview> previews,
            LabelledSpread.Spread spread,
            IgnoredFloor ignoredFloor) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "Choosing the relevance cut"))
                .append(ReportPage.paragraph(distribution.scoredDocumentCount()
                        + " document(s) were scored, between " + format(distribution.lowestScore())
                        + " and " + format(distribution.highestScore())
                        + ". This page reports what was found and decides nothing: the number that"
                        + " separates what is kept from what is not is yours to choose, and the record"
                        + " of how you chose it belongs in the profile beside the value."));

        if (ignoredFloor != null) {
            body.append(ReportPage.heading(2, "Your threshold is not being applied"))
                    .append(ReportPage.paragraph("The profile sets a relevance cut of <strong>"
                            + ignoredFloor.value()
                            + "</strong>, and this run kept every document anyway. A cut is a number on"
                            + " a scale, and the scale is whichever model produced the scores. The"
                            + " answers this number was read off were given while <code>"
                            + ReportPage.escape(ignoredFloor.calibratedUnder())
                            + "</code> was in use, and the scores below were produced by <code>"
                            + ReportPage.escape(ignoredFloor.currentIdentity())
                            + "</code>. Applying the old number to the new scores would remove"
                            + " documents against a spread it was never read off. Judge the documents"
                            + " below again and the number will apply, or put the previous model"
                            + " back."));
        }

        body.append(ReportPage.heading(2, "How the scores are spread"))
                .append(ReportPage.paragraph("The range above is divided into " + distribution.bands().size()
                        + " equal bands. They"
                        + " are cut between the lowest and highest score anything actually got, not"
                        + " across every score that was possible, because scores over one archive"
                        + " usually sit in a narrow part of the range and bands cut over the whole of"
                        + " it would be mostly empty."));
        StringBuilder bandRows = new StringBuilder();
        for (RelevanceDistribution.Band band : distribution.bands()) {
            bandRows.append(ReportPage.row(
                    ReportPage.textCell("Band " + (band.ordinal() + 1)),
                    ReportPage.textCell(format(band.lowerBound())),
                    ReportPage.textCell(format(band.upperBound())),
                    ReportPage.numberCell(band.documentCount()),
                    ReportPage.numberCell(band.sampledCount()),
                    ReportPage.numberCell(band.shortfall())));
        }
        body.append(ReportPage.table(
                        ReportPage.headerRow(
                                "Band", "From", "To", "Documents", "Being asked about", "Short by"),
                        bandRows.toString()))
                .append(ReportPage.paragraph("A band holding fewer documents than were asked for"
                        + " supplies what it has, and the difference is shown above rather than made"
                        + " up from a neighbouring band. A proportion read off four answers is not the"
                        + " same evidence as one read off " + eachBandWasAskedFor(distribution)
                        + ", and topping it up from documents"
                        + " that scored differently would hide that."))
                .append(ReportPage.heading(2, "If the bands look alike"))
                .append(ReportPage.paragraph("A spread where every band holds much the same documents,"
                        + " and the ones you judge relevant are scattered evenly through all of them,"
                        + " is telling you something about the <strong>seed set</strong> rather than"
                        + " about the threshold. It means the seeds are not separating this archive:"
                        + " everything resembles them about equally, so there is no boundary in these"
                        + " scores to find. The answer is a different seed folder and another scoring"
                        + " run, not a cut chosen from this page anyway."))
                .append(answersSoFar(spread))
                .append(ReportPage.heading(2, "The documents to judge"))
                .append(ReportPage.paragraph("Each one below is drawn from a band, and the same run"
                        + " always asks about the same documents, so you can stop and come back. Write"
                        + " your answers in the label file beside this page."));

        if (distribution.sample().isEmpty()) {
            body.append(ReportPage.paragraph("No document was sampled, because nothing was scored."));
        } else {
            StringBuilder sampleRows = new StringBuilder();
            for (RelevanceDistribution.Sampled sampled : distribution.sample()) {
                Optional<Preview> preview = previews.stream()
                        .filter(candidate -> candidate.occurrenceId().equals(sampled.occurrenceId()))
                        .findFirst();
                sampleRows.append(ReportPage.row(
                        ReportPage.textCell("Band " + (sampled.bandOrdinal() + 1)),
                        ReportPage.textCell(preview.map(Preview::path).orElse("(path not recorded)")),
                        ReportPage.numberCell(format(sampled.score())),
                        ReportPage.textCell(
                                preview.map(Preview::winningSeedPath).orElse("(seed not recorded)")),
                        ReportPage.htmlCell("<blockquote>"
                                + ReportPage.escape(preview.map(Preview::textOpening)
                                        .orElse("(no text was extracted)"))
                                + "</blockquote>")));
            }
            body.append(ReportPage.table(
                    ReportPage.headerRow(
                            "Band", "Document", "Score", "Closest seed", "How it begins"),
                    sampleRows.toString()));
        }

        return ReportPage.render("Choosing the relevance cut", body.toString());
    }

    /**
     * The number of documents each band was asked for, which the sample's preset {@code
     * vespera.relevance.sample.per-band} gave and the scoring run was minted under (ADR-330 section 9): what a band
     * supplied and what it fell short by, which is the same in every band.
     */
    private static int eachBandWasAskedFor(RelevanceDistribution.Distribution distribution) {
        return distribution.bands().stream()
                .findFirst()
                .map(band -> band.sampledCount() + band.shortfall())
                .orElse(0);
    }

    /** Scores are shown to two places: more digits than that is precision a reader cannot use. */
    private static String format(double score) {
        return String.format(java.util.Locale.ROOT, "%.2f", score);
    }

    /**
     * What the answers given so far say, and what each candidate cut would cost — the arithmetic
     * ADR-088 asks the engine for, stated in documents rather than in statistics vocabulary.
     *
     * <p>Nothing here recommends a cut. The proportions are written as one number out of another
     * rather than as a percentage, because a dozen answers in a band is a thin measurement and "9 in
     * 12" says so where "75%" does not.
     */
    private static String answersSoFar(LabelledSpread.Spread spread) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(2, "What the answers say so far"));
        if (spread.labelled() == 0) {
            return body.append(ReportPage.paragraph("Nobody has answered any of the questions yet. Fill"
                    + " in the label file beside this page, run the tool again, and this section will"
                    + " say what the answers came to and what each possible cut would cost.")).toString();
        }

        StringBuilder bandRows = new StringBuilder();
        for (LabelledSpread.BandLabels band : spread.bandLabels()) {
            bandRows.append(ReportPage.row(
                    ReportPage.textCell("Band " + (band.bandOrdinal() + 1)),
                    ReportPage.numberCell(band.labelled()),
                    ReportPage.numberCell(band.labelledRelevant() + " in " + band.labelled())));
        }
        body.append(ReportPage.paragraph(spread.labelled()
                        + " document(s) judged so far, spread across the bands like this."))
                .append(ReportPage.table(
                        ReportPage.headerRow("Band", "Judged", "Judged relevant"),
                        bandRows.toString()));

        body.append(ReportPage.heading(2, "What each cut would cost"))
                .append(ReportPage.paragraph("One line per place the cut could go. Nothing here"
                        + " recommends one: the trade between what is kept and what is lost is the"
                        + " judgement this whole page exists to hand to you."))
                .append("<ul>\n");
        for (LabelledSpread.CandidateCut cut : spread.cuts()) {
            body.append("<li>Cut at <strong>")
                    .append(format(cut.score()))
                    .append("</strong> and ")
                    .append(cut.surviving())
                    .append(" document(s) survive; of the ")
                    .append(cut.labelledAbove())
                    .append(" judged above it, ")
                    .append(cut.labelledRelevantAbove())
                    .append(" were relevant. ")
                    .append(cut.discarded())
                    .append(" are discarded; of the ")
                    .append(cut.labelledBelow())
                    .append(" judged below it, ")
                    .append(cut.labelledRelevantBelow())
                    .append(" were relevant.</li>\n");
        }
        return body.append("</ul>\n")
                .append(ReportPage.paragraph("Write the number you choose into"
                        + " <code>relevanceScoreFloor</code> in the profile, and say in its"
                        + " <code>provenance</code> how you arrived at it. Nothing writes that number"
                        + " for you, and nothing checks that you read this page first — what stands"
                        + " between a guess and the archive is what you record there."))
                .toString();
    }
}
