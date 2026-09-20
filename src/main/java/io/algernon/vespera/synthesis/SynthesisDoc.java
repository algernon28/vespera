package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;

/**
 * What one call produced (ADR-106, ADR-108, ADR-110, ADR-133): the title the model wrote, the prose
 * it wrote, and which documents it was working from.
 *
 * <p><b>The prose is exactly what came back, markers intact.</b> The file a reader opens is a
 * rendering of this rather than a copy of it — each {@code [n]} becomes a link and the membership
 * list is composed at write time (ADR-109) — so rewriting anything here would leave the record and
 * the deliverable disagreeing about what was said.
 *
 * <p><b>Which documents, not how many</b> (ADR-133). A count beside the list it counts is two
 * statements of one fact kept in step by nothing, and it is what let the deliverable number its own
 * membership a second way and disagree with the numbering the model was given. The count is the
 * size of the recorded list and is stored nowhere.
 *
 * @param title what the model called the cluster, which the deliverable uses in place of the
 *     derived label
 * @param prose the text, with its citation markers exactly as they came back
 * @param sent the documents the call carried, in the order it was given them — so the document at
 *     index {@code n - 1} is the one the model wrote {@code [n]} about (ADR-133)
 */
public record SynthesisDoc(String title, String prose, List<OccurrenceId> sent) {

    /** How many documents the call was written from, which is the size of what it was written from. */
    public int documentsSent() {
        return sent.size();
    }
}
