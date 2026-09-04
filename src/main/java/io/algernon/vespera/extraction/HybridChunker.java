package io.algernon.vespera.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Chunks a converted document structure-first (ADR-029), over the same JSON/{@code DoclingDocument}
 * export {@link DoclingClient} already requested for this reason: a heading always starts a fresh
 * chunk and leads it, so related paragraphs stay grouped under the heading that introduces them
 * rather than being split at an arbitrary character offset; a section long enough to exceed the
 * token budget on its own still splits, once its own words alone cross that budget. Boundaries are
 * cached by content hash plus chunker identity plus tokenizer identity (ADR-044), so a re-chunk
 * under an unseen tokenizer mints new rows instead of overwriting another candidate's.
 *
 * <p>{@link #MAX_CHUNK_TOKENS} is a fixed default in code, not a {@code Profile} key, the same
 * reasoning ADR-071 applied to its own constants: it is an operational parameter, not a corpus
 * judgement a person has to supply.
 *
 * <p>The exact merge algorithm below is this ticket's own reading of "structure-first" — Docling's
 * published {@code HybridChunker} is a Python algorithm with no Java port on this classpath, and the
 * wayfinder map left the literal mechanism to the hand-off spec's "reasonable default" allowance.
 * What is pinned by the acceptance criteria this obeys: chunk boundaries respect document structure,
 * the cache key carries chunker and tokenizer identity, and a granularity/tokenizer change mints new
 * rows rather than overwriting old ones.
 */
@Component
public class HybridChunker {

    /** This chunker's own identity, half of the chunk cache's key alongside tokenizer identity. */
    static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";

    /** A chunk's token budget — generous enough to keep related paragraphs together, per-chunk. */
    static final int MAX_CHUNK_TOKENS = 512;

    /** Docling's own heading labels: each one starts a fresh chunk and becomes its leading context. */
    private static final Set<String> HEADING_LABELS = Set.of("title", "section_header");

    /** Labels carrying no document content — running headers/footers repeat on every page. */
    private static final Set<String> NOISE_LABELS = Set.of("page_header", "page_footer");

    private final ChunkCache cache;
    private final StructurelessChunkingFallback structurelessFallback;

    HybridChunker(ChunkCache cache, StructurelessChunkingFallback structurelessFallback) {
        this.cache = cache;
        this.structurelessFallback = structurelessFallback;
    }

    /**
     * The chunks for {@code rawDoclingResponse} under {@code tokenizer}, cached by content hash plus
     * this chunker's identity plus {@code tokenizer}'s own — a cache hit returns the stored chunks
     * without re-chunking.
     */
    public List<Chunk> chunk(String rawDoclingResponse, String contentHash, Tokenizer tokenizer) {
        List<Chunk> cached = cache.get(contentHash, CHUNKER_IDENTITY, tokenizer.identity().value());
        if (!cached.isEmpty()) {
            return cached;
        }
        List<DocumentText> texts = DoclingDocumentTexts.parse(rawDoclingResponse);
        List<String> chunkTexts = texts.isEmpty()
                ? structurelessFallback.chunk("", tokenizer, MAX_CHUNK_TOKENS)
                : chunkStructured(texts, tokenizer);
        List<Chunk> chunks = numbered(chunkTexts, tokenizer);
        cache.put(contentHash, CHUNKER_IDENTITY, tokenizer.identity().value(), chunks);
        return chunks;
    }

    /** How many chunks {@code contentHash} has stored under this chunker and {@code tokenizer}. */
    public int chunkCount(String contentHash, Tokenizer tokenizer) {
        return cache.count(contentHash, CHUNKER_IDENTITY, tokenizer.identity().value());
    }

    /**
     * Structure-first chunking, at word granularity so a single text item long enough to exceed the
     * token budget on its own still splits: a heading flushes whatever is accumulated so far and
     * leads the next chunk's words, and every other item's words are appended to the same running
     * accumulation, windowed to the token budget exactly as {@link #windowWords} does for
     * structureless text.
     */
    private static List<String> chunkStructured(List<DocumentText> texts, Tokenizer tokenizer) {
        List<String> chunks = new ArrayList<>();
        List<String> pendingWords = new ArrayList<>();

        for (DocumentText item : texts) {
            if (NOISE_LABELS.contains(item.label())) {
                continue;
            }
            if (HEADING_LABELS.contains(item.label())) {
                flushWords(chunks, pendingWords, tokenizer);
            }
            pendingWords.addAll(words(item.text()));
        }
        flushWords(chunks, pendingWords, tokenizer);
        return chunks;
    }

    private static void flushWords(List<String> chunks, List<String> pendingWords, Tokenizer tokenizer) {
        if (!pendingWords.isEmpty()) {
            chunks.addAll(windowWords(pendingWords, tokenizer));
            pendingWords.clear();
        }
    }

    private static List<String> words(String text) {
        return List.of(text.trim().split("\\s+"));
    }

    /** Packs {@code words} into chunks of at most {@link #MAX_CHUNK_TOKENS} tokens each. */
    private static List<String> windowWords(List<String> words, Tokenizer tokenizer) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && tokenizer.countTokens(candidate) > MAX_CHUNK_TOKENS) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static List<Chunk> numbered(List<String> texts, Tokenizer tokenizer) {
        List<Chunk> chunks = new ArrayList<>();
        for (int ordinal = 0; ordinal < texts.size(); ordinal++) {
            String text = texts.get(ordinal);
            chunks.add(new Chunk(ordinal, text, tokenizer.countTokens(text)));
        }
        return chunks;
    }
}
