package io.algernon.vespera.synthesis;

/**
 * What one call produced (ADR-106, ADR-108, ADR-110): the heading the model wrote, the prose it
 * wrote, and how many documents it was working from.
 *
 * <p><b>The prose is exactly what came back, markers intact.</b> The file a reader opens is a
 * rendering of this rather than a copy of it — each {@code [n]} becomes a link and the membership
 * list is composed at write time (ADR-109) — so rewriting anything here would leave the record and
 * the deliverable disagreeing about what was said.
 *
 * @param title the heading the model gave the group, which the deliverable uses in place of the
 *     derived label
 * @param prose the text, with its citation markers exactly as they came back
 * @param documentsSent how many documents the call was written from, which is what lets the
 *     deliverable say so
 */
public record SynthesisDoc(String title, String prose, int documentsSent) {}
