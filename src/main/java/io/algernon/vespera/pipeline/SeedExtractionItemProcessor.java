package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.extraction.Tokenizer;
import io.algernon.vespera.extraction.UsableText;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Extracts and chunks one seed document (ADR-083), reusing stage 2's instruments exactly as they
 * stand: {@link DoclingExtractor}'s content-addressed cache and {@link HybridChunker}'s.
 *
 * <p>Not stage 2's own processor pointed at a second walk. That one is bound to the corpus walk and
 * exists to write corpus verdicts — {@code extraction-failed}, {@code degenerate-output} — and every
 * one of those removes a document from publication. A seed is never published, so this pass writes no
 * verdict at all, whatever a conversion does.
 *
 * <p><b>Nothing here needs a run.</b> The extraction and chunk caches are content-addressed and carry
 * no {@code run_id}, which is what lets the whole seed folder be extracted <em>before</em> anything
 * decides whether a run should exist — the ordering ADR-083's gate requires, since "no usable seed at
 * all" cannot be answered without extracting the seeds. It is also what makes ADR-083's "a seed that
 * is also a corpus member costs nothing" true by construction: the hash is the same SHA-256 stage 1
 * and stage 2 already keyed their rows under (ADR-067), so the second read is a cache hit rather than
 * a second Docling call.
 *
 * <p>The usability bar is {@link UsableText}, which <em>is</em> stage 2's tier 1 rather than a copy of
 * it — ADR-083 fixes the bar as tier 1 "exactly" and no stricter, and two implementations of one
 * sentence is how that instruction would drift.
 */
@Component
@StepScope
class SeedExtractionItemProcessor implements ItemProcessor<OccurrenceId, SeedExtractionOutcome> {

    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker chunker;
    private final Tokenizer tokenizer;
    private final SeedGate.SeedWalk seedWalk;

    SeedExtractionItemProcessor(
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker chunker,
            Tokenizer tokenizer,
            SeedGate seedGate) {
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.chunker = chunker;
        this.tokenizer = tokenizer;
        this.seedWalk = seedGate.seedWalk()
                .orElseThrow(() -> new IllegalStateException(
                        "the seed extraction processor must not be instantiated while the seed gate is closed"));
    }

    @Override
    public SeedExtractionOutcome process(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        String contentHash = extractor.contentHashFor(file);
        DoclingResponse response = extractor.convert(file, contentHash, extractorIdentity);

        String text = ExtractionOutputText.of(response.rawResponse());
        if (!UsableText.hasAlphanumericContent(text)) {
            // Recorded, never judged, and it does not stop the run: scoring proceeds against whatever
            // survived extraction, and a corrected seed folder is a different run because the seed
            // folder is part of what that run's identity is derived from (ADR-083).
            return SeedExtractionOutcome.unusable(occurrenceId, UsableText.NO_ALPHANUMERIC_CONTENT);
        }
        chunker.chunk(response.rawResponse(), contentHash, tokenizer);
        return SeedExtractionOutcome.usable(occurrenceId);
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(() ->
                        new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return seedWalk.canonicalRoot().resolve(facts.path().value());
    }
}
