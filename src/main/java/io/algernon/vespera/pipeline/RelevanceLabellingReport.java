package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.LabelledSpread;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;
import java.util.Optional;

/**
 * The page a person reads before choosing a relevance threshold (ADR-088) — plain, hand-assembled
 * HTML, no templating library, the same shape {@link ConfidenceDistributionReport} and {@link
 * SeedCorpusComparisonReport} already established (ADR-046).
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
            Optional<IgnoredFloor> ignoredFloor) {
        StringBuilder page = new StringBuilder();
        page.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<title>Choosing the relevance cut</title>\n<style>\n")
                .append("body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n")
                .append("table { border-collapse: collapse; margin-bottom: 1.5em; }\n")
                .append("td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n")
                .append("td.count { text-align: right; }\n")
                .append("blockquote { color: #444; font-style: italic; margin: 0.4em 0 0 1em; }\n")
                .append("</style>\n</head>\n<body>\n")
                .append("<h1>Choosing the relevance cut</h1>\n")
                .append("<p>")
                .append(distribution.scoredDocumentCount())
                .append(" document(s) were scored, between ")
                .append(format(distribution.lowestScore()))
                .append(" and ")
                .append(format(distribution.highestScore()))
                .append(". This page reports what was found and decides nothing: the number that"
                        + " separates what is kept from what is not is yours to choose, and the record of"
                        + " how you chose it belongs in the profile beside the value.</p>\n");

        ignoredFloor.ifPresent(ignored -> page.append("<h2>Your threshold is not being applied</h2>\n")
                .append("<p>The profile sets a relevance cut of <strong>")
                .append(ignored.value())
                .append("</strong>, and this run kept every document anyway. A cut is a number on a"
                        + " scale, and the scale is whichever model produced the scores. The answers"
                        + " this number was read off were given while <code>")
                .append(escape(ignored.calibratedUnder()))
                .append("</code> was in use, and the scores below were produced by <code>")
                .append(escape(ignored.currentIdentity()))
                .append("</code>. Applying the old number to the new scores would remove documents"
                        + " against a spread it was never read off. Judge the documents below again and"
                        + " the number will apply, or put the previous model back.</p>\n"));

        page.append("<h2>How the scores are spread</h2>\n")
                .append("<p>The range above is divided into five equal bands. They are cut between the"
                        + " lowest and highest score anything actually got, not across every score that"
                        + " was possible, because scores over one archive usually sit in a narrow part of"
                        + " the range and bands cut over the whole of it would be mostly empty.</p>\n")
                .append("<table>\n<tr><th>Band</th><th>From</th><th>To</th><th>Documents</th>")
                .append("<th>Being asked about</th><th>Short by</th></tr>\n");
        for (RelevanceDistribution.Band band : distribution.bands()) {
            page.append("<tr><td>Band ")
                    .append(band.ordinal() + 1)
                    .append("</td><td>")
                    .append(format(band.lowerBound()))
                    .append("</td><td>")
                    .append(format(band.upperBound()))
                    .append("</td><td class=\"count\">")
                    .append(band.documentCount())
                    .append("</td><td class=\"count\">")
                    .append(band.sampledCount())
                    .append("</td><td class=\"count\">")
                    .append(band.shortfall())
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");

        page.append("<p>A band holding fewer documents than were asked for supplies what it has, and the"
                + " difference is shown above rather than made up from a neighbouring band. A"
                + " proportion read off four answers is not the same evidence as one read off twelve,"
                + " and topping it up from documents that scored differently would hide that.</p>\n");

        page.append("<h2>If the bands look alike</h2>\n")
                .append("<p>A spread where every band holds much the same documents, and the ones you"
                        + " judge relevant are scattered evenly through all of them, is telling you"
                        + " something about the <strong>seed set</strong> rather than about the"
                        + " threshold. It means the seeds are not separating this archive: everything"
                        + " resembles them about equally, so there is no boundary in these scores to"
                        + " find. The answer is a different seed folder and another scoring run, not a"
                        + " cut chosen from this page anyway.</p>\n");

        page.append(answersSoFar(spread));

        page.append("<h2>The documents to judge</h2>\n")
                .append("<p>Each one below is drawn from a band, and the same run always asks about the"
                        + " same documents, so you can stop and come back. Write your answers in the"
                        + " label file beside this page.</p>\n");
        if (distribution.sample().isEmpty()) {
            page.append("<p>No document was sampled, because nothing was scored.</p>\n");
        } else {
            page.append("<table>\n<tr><th>Band</th><th>Document</th><th>Score</th><th>Closest seed</th>")
                    .append("<th>How it begins</th></tr>\n");
            for (RelevanceDistribution.Sampled sampled : distribution.sample()) {
                Optional<Preview> preview = previews.stream()
                        .filter(candidate -> candidate.occurrenceId().equals(sampled.occurrenceId()))
                        .findFirst();
                page.append("<tr><td>Band ")
                        .append(sampled.bandOrdinal() + 1)
                        .append("</td><td>")
                        .append(escape(preview.map(Preview::path).orElse("(path not recorded)")))
                        .append("</td><td class=\"count\">")
                        .append(format(sampled.score()))
                        .append("</td><td>")
                        .append(escape(preview.map(Preview::winningSeedPath).orElse("(seed not recorded)")))
                        .append("</td><td><blockquote>")
                        .append(escape(preview.map(Preview::textOpening).orElse("(no text was extracted)")))
                        .append("</blockquote></td></tr>\n");
            }
            page.append("</table>\n");
        }

        page.append("</body>\n</html>\n");
        return page.toString();
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
        StringBuilder page = new StringBuilder();
        page.append("<h2>What the answers say so far</h2>\n");
        if (spread.labelled() == 0) {
            page.append("<p>Nobody has answered any of the questions yet. Fill in the label file beside"
                    + " this page, run the tool again, and this section will say what the answers came to"
                    + " and what each possible cut would cost.</p>\n");
            return page.toString();
        }

        page.append("<p>")
                .append(spread.labelled())
                .append(" document(s) judged so far, spread across the bands like this.</p>\n")
                .append("<table>\n<tr><th>Band</th><th>Judged</th><th>Judged relevant</th></tr>\n");
        for (LabelledSpread.BandLabels band : spread.bandLabels()) {
            page.append("<tr><td>Band ")
                    .append(band.bandOrdinal() + 1)
                    .append("</td><td class=\"count\">")
                    .append(band.labelled())
                    .append("</td><td class=\"count\">")
                    .append(band.labelledRelevant())
                    .append(" in ")
                    .append(band.labelled())
                    .append("</td></tr>\n");
        }
        page.append("</table>\n");

        page.append("<h2>What each cut would cost</h2>\n")
                .append("<p>One line per place the cut could go. Nothing here recommends one: the trade"
                        + " between what is kept and what is lost is the judgement this whole page exists"
                        + " to hand to you.</p>\n<ul>\n");
        for (LabelledSpread.CandidateCut cut : spread.cuts()) {
            page.append("<li>Cut at <strong>")
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
        page.append("</ul>\n")
                .append("<p>Write the number you choose into <code>relevanceScoreFloor</code> in the"
                        + " profile, and say in its <code>provenance</code> how you arrived at it. Nothing"
                        + " writes that number for you, and nothing checks that you read this page first"
                        + " — what stands between a guess and the archive is what you record there.</p>\n");
        return page.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
