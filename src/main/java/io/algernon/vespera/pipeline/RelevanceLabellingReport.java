package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.LabelledSpread;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;
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
                .append(ReportPage.paragraph("The range above is divided into five equal bands. They"
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
                        + " same evidence as one read off twelve, and topping it up from documents"
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

    /** Scores are shown to two places: more digits than that is precision a reader cannot use. */
    private static String format(double score) {
        return String.format(java.util.Locale.ROOT, "%.2f", score);
    }

    /**
     * What the answers given so far say, and what each candidate cut would cost — the arithmetic
     * ADR-088 asks the engine for, stated in documents rather than in statistics vocabulary.
     *
     * <p>Nothing here recommends a cut. The proportions are written as one number out of another
     * rather than as a percentage, because twelve answers in a band is a thin measurement and "9 in
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
