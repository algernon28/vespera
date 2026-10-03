package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RemovedOccurrence;
import java.util.List;

/**
 * Renders the review list of files stage 2 could not read as one self-contained HTML file (ADR-175
 * section 7), through the shared {@link ReportPage} module (ADR-130).
 *
 * <p>The page is the other half of the rule that a file that fails is marked and skipped: the run goes
 * on, so this is where the operator finds what it went on without.
 *
 * <p>It does not say that running the same command again asks about these files again, which #326 asked
 * it to say: a finished stage 2 reads nothing under the same run (ADR-115), and a resumed one keeps the
 * verdicts its committed chunks wrote (ADR-181). What is true is what it says, and it says it only of
 * the rows it is true of: a call that failed, a timeout, and a failure the converter blamed on itself
 * leave nothing in the extraction cache (ADR-183), so a new stage-2 run asks again. A failure the
 * converter blamed on the document is stored and is not asked about again, and the page promises
 * nothing for it.
 */
final class ReviewListReport {

    private ReviewListReport() {}

    /** The page for {@code failures}, which arrive in path order. */
    static String render(List<RemovedOccurrence> failures) {
        StringBuilder body = new StringBuilder();
        body.append(ReportPage.heading(1, "Files stage 2 could not read"));
        if (failures.isEmpty()) {
            body.append(ReportPage.paragraph("No file failed. Stage 2 read every file it was given."));
            return ReportPage.render("Files stage 2 could not read", body.toString());
        }
        body.append(ReportPage.paragraph("Files that could not be read: " + failures.size()
                        + ". Each was marked and skipped; the run went on. They stay removed under this"
                        + " run. A file whose reason begins with rejected, crashed the converter, timeout,"
                        + " capacity, target_unavailable or internal left nothing stored, so the next run of"
                        + " this stage asks the converter about it again."));
        StringBuilder rows = new StringBuilder();
        for (RemovedOccurrence failure : failures) {
            rows.append(ReportPage.row(
                    ReportPage.textCell(failure.path()), ReportPage.textCell(failure.reason())));
        }
        body.append(ReportPage.table(ReportPage.headerRow("Path", "Why"), rows.toString()));
        return ReportPage.render("Files stage 2 could not read", body.toString());
    }
}
