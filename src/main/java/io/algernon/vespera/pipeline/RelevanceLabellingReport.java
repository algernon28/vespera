package io.algernon.vespera.pipeline;

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

    static String render(RelevanceDistribution.Distribution distribution, List<Preview> previews) {
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

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
