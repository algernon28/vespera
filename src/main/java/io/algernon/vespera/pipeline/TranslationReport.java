package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.Translations;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.Map;

/**
 * Renders what the translation step found as {@code translations.html}, the page {@code
 * translationSimilarityFloor} and {@code preferredLanguages} are read off (ADR-271 section 7), through {@link
 * ReportPage}. The sets, the rows and the counts are {@code embedding}'s ({@link Translations.Resolution}); this
 * holds the words and the cells. The page holds counts, language codes, a run id and a model's name, and no path,
 * no title and no text.
 */
final class TranslationReport {

    private TranslationReport() {}

    /**
     * @param survivors how many survivors entered the step
     * @param removed how many copies were removed as {@code translation-of}
     * @param floorReading how {@code translationSimilarityFloor} was read, in words
     * @param languagesReading how {@code preferredLanguages} was read, in words
     * @param preferred whether {@code preferredLanguages} named a language
     */
    static String render(
            Translations.Resolution resolution,
            RunId scoringRunId,
            String embeddingModel,
            long survivors,
            long removed,
            String floorReading,
            String languagesReading,
            boolean preferred) {
        StringBuilder languages = new StringBuilder();
        for (Map.Entry<String, Long> language : resolution.detectedLanguages().entrySet()) {
            languages.append(ReportPage.row(ReportPage.codeCell(language.getKey()), ReportPage.numberCell(language.getValue())));
        }
        StringBuilder rows = new StringBuilder();
        for (Translations.SimilarityRow row : resolution.rows()) {
            rows.append(ReportPage.row(
                    ReportPage.numberCell(row.similarity().toPlainString()),
                    ReportPage.numberCell(row.likenesses()),
                    ReportPage.numberCell(row.confirmed()),
                    ReportPage.numberCell(row.refuted())));
        }
        long waiting = resolution.sets() - resolution.setsStanding();

        String body = ReportPage.heading(1, "Copies in another language")
                + ReportPage.paragraph("Scoring run <code>" + ReportPage.escape(scoringRunId.value())
                        + "</code>, embedding model <code>" + ReportPage.escape(embeddingModel)
                        + "</code>. Survivors entering the step: " + survivors + ".")
                + ReportPage.heading(2, "Languages detected")
                + ReportPage.paragraph("The survivors with a detected language, by its ISO 639-1 code.")
                + ReportPage.table(
                        "<thead>" + ReportPage.headerRow("Language", "Survivors") + "</thead>\n<tbody>\n",
                        languages + "</tbody>\n")
                + ReportPage.heading(2, "Translations by their paths")
                + ReportPage.paragraph("Sets of survivors whose paths differ only in a folder named for a language: "
                        + resolution.setsByPaths() + ".")
                + ReportPage.heading(2, "Likenesses by their embeddings")
                + ReportPage.paragraph("Each row is a hundredth of similarity, the cosine of two mean vectors rounded "
                        + "down from its exact value, with the likenesses of two survivors in different languages "
                        + "that lie on it, of which those their paths confirm (both are in one set of translations "
                        + "by their paths) and those their paths refute (one is a translation by its path of a "
                        + "third survivor in the other's language).")
                + ReportPage.table(
                        "<thead>"
                                + ReportPage.headerRow("Similarity", "Likenesses", "Confirmed by paths", "Refuted by paths")
                                + "</thead>\n<tbody>\n",
                        rows + "</tbody>\n")
                + ReportPage.paragraph("Set <code>translationSimilarityFloor</code> in the profile to the similarity "
                        + "a row shows, and the likenesses on that row and on every row after it are applied, none "
                        + "on the rows before. Say in its <code>provenance</code> how you arrived at it.")
                + ReportPage.heading(2, "Translation sets")
                + ReportPage.bulletList(List.of(
                        "Translation sets found: " + resolution.sets(),
                        "Standing whole, holding two copies in one language: " + resolution.setsStanding(),
                        preferred
                                ? "Copies removed as translation-of: " + removed
                                : "Sets waiting for preferredLanguages, from which nothing was removed: " + waiting))
                + ReportPage.paragraph("Write the languages to keep into <code>preferredLanguages</code> in the "
                        + "profile, most preferred first, as ISO 639-1 codes separated by spaces or commas; the "
                        + "copy in the first of them that a set holds survives, and the others are removed.")
                + ReportPage.heading(2, "The two keys")
                + ReportPage.bulletList(List.of(
                        "translationSimilarityFloor: " + floorReading,
                        "preferredLanguages: " + languagesReading));
        return ReportPage.render("Copies in another language", body);
    }
}
