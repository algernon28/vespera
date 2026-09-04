package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Chunking respects document structure rather than an arbitrary character window, and the chunk
 * cache never conflates two different tokenizers' output (ADR-029, ADR-044).
 *
 * <p>The cache is the real one against a real database, mirroring {@code DoclingExtractorTest}'s own
 * reasoning: a stubbed cache would make a hit an assumption rather than a claim.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Chunking")
@Issue("49")
@Link(name = "ADR-029", url = Adr.CHUNKING_STRUCTURE_FIRST_WITH_A_MEASURED_LLM_FALLBACK, type = "adr")
@Link(name = "ADR-044", url = Adr.THE_BAKE_OFF_RE_CHUNKS_PER_CANDIDATE_MODEL, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_THE_DERIVED_METRIC_COLUMNS, type = "adr")
class HybridChunkerTest {

    /** A content hash standing in for one an earlier stage computed within a size-matched group. */
    private static final String CONTENT_HASH = "0".repeat(63) + "1";

    /** How many words a one-token-per-word stub tokenizer packs into one chunk before it must split. */
    private static final int WORDS_PER_CHUNK = HybridChunker.MAX_CHUNK_TOKENS;

    /** A document with two headed sections, each short enough to stay inside one chunk. */
    private static final String TWO_SECTION_DOCUMENT =
            """
            {
              "document": {
                "json_content": {
                  "texts": [
                    {"text": "Introduction", "label": "section_header"},
                    {"text": "This is the opening paragraph.", "label": "paragraph"},
                    {"text": "page 1 of 12", "label": "page_footer"},
                    {"text": "Method", "label": "section_header"},
                    {"text": "This is the method paragraph.", "label": "paragraph"}
                  ]
                }
              }
            }
            """;

    /** A document with no structural text items at all — Docling reported none. */
    private static final String STRUCTURELESS_DOCUMENT = """
            {"document": {"json_content": {"texts": []}}}
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Chunk boundaries follow document structure")
    @DisplayName("Each heading starts a fresh chunk, and a running footer contributes nothing to any of them")
    void chunksAlongStructuralBoundaries() {
        HybridChunker chunker = chunker();

        List<Chunk> chunks = chunker.chunk(TWO_SECTION_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v1"));

        claim(
                "two headed sections, each short enough to fit in one chunk, produce exactly two"
                        + " chunks rather than one chunk per text item or one chunk for the whole"
                        + " document",
                () -> assertThat(chunks).hasSize(2));
        claim(
                "the running page footer between the two sections contributes nothing to either"
                        + " chunk's text, since it carries no document content of its own",
                () -> assertThat(chunks.stream().map(Chunk::text)).noneMatch(text -> text.contains("page 1 of 12")));
        claim(
                "the first chunk carries its own heading as context, the way a reader would need it"
                        + " to understand the paragraph beneath it",
                () -> assertThat(chunks.get(0).text()).startsWith("Introduction"));
        claim(
                "and chunks are numbered in document order, starting at zero",
                () -> assertThat(chunks).extracting(Chunk::ordinal).containsExactly(0, 1));
    }

    @Test
    @Story("A chunk never exceeds its token budget")
    @DisplayName("A section long enough to exceed the token budget splits into more than one chunk")
    void splitsASectionThatExceedsTheTokenBudget() {
        HybridChunker chunker = chunker();
        String longParagraph = "word ".repeat(WORDS_PER_CHUNK + 50).trim();
        String document =
                """
                {"document": {"json_content": {"texts": [
                    {"text": "Body", "label": "section_header"},
                    {"text": "%s", "label": "paragraph"}
                ]}}}
                """
                        .formatted(longParagraph);

        List<Chunk> chunks = chunker.chunk(document, CONTENT_HASH, wordCountingTokenizer("v1"));

        claim(
                "a paragraph whose word count alone exceeds the " + WORDS_PER_CHUNK
                        + "-token budget is split across more than one chunk, rather than producing"
                        + " one oversized chunk",
                () -> assertThat(chunks.size()).isGreaterThan(1));
        claim(
                "and no single chunk exceeds the " + WORDS_PER_CHUNK + "-token budget",
                () -> assertThat(chunks)
                        .allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(WORDS_PER_CHUNK)));
    }

    @Test
    @Story("A structureless document falls back safely")
    @DisplayName("A document with no structural text items produces no chunks, rather than failing")
    void structurelessDocumentProducesNoChunks() {
        HybridChunker chunker = chunker();

        List<Chunk> chunks = chunker.chunk(STRUCTURELESS_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v1"));

        claim(
                "a document Docling reported no structure for produces no chunks, through the"
                        + " disabled-by-default structureless fallback, rather than throwing",
                () -> assertThat(chunks).isEmpty());
    }

    @Test
    @Story("The same content is chunked once per tokenizer")
    @DisplayName("Chunking the same content twice under the same tokenizer chunks it only once")
    void cachesChunksUnderOneTokenizer() {
        HybridChunker chunker = chunker();

        List<Chunk> first = chunker.chunk(TWO_SECTION_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v1"));
        List<Chunk> second = chunker.chunk(TWO_SECTION_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v1"));

        claim(
                "the second call answers with the exact chunks stored the first time, rather than"
                        + " re-chunking",
                () -> assertThat(second).isEqualTo(first));
        claim(
                "and exactly one chunk row per produced chunk is stored under this content and"
                        + " tokenizer, not one row per call",
                () -> assertThat(chunker.chunkCount(CONTENT_HASH, wordCountingTokenizer("v1")))
                        .isEqualTo(first.size()));
    }

    @Test
    @Story("A tokenizer change re-chunks instead of overwriting")
    @DisplayName("Chunking the same content under a different tokenizer identity adds new rows rather than replacing the old ones")
    void reChunksUnderADifferentTokenizerIdentity() {
        HybridChunker chunker = chunker();

        chunker.chunk(TWO_SECTION_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v1"));
        chunker.chunk(TWO_SECTION_DOCUMENT, CONTENT_HASH, wordCountingTokenizer("v2"));

        claim(
                "the first tokenizer's chunks are still there — the second tokenizer's run did not"
                        + " overwrite them",
                () -> assertThat(chunker.chunkCount(CONTENT_HASH, wordCountingTokenizer("v1"))).isPositive());
        claim(
                "and the second tokenizer's own chunks are stored separately, under its own identity",
                () -> assertThat(chunker.chunkCount(CONTENT_HASH, wordCountingTokenizer("v2"))).isPositive());
    }

    private HybridChunker chunker() {
        return new HybridChunker(new ChunkCache(jdbcTemplate), new WindowedStructurelessChunkingFallback());
    }

    /** A tokenizer counting one token per whitespace-separated word — deterministic, no real model needed. */
    private static Tokenizer wordCountingTokenizer(String identityValue) {
        return new Tokenizer() {
            @Override
            public int countTokens(String text) {
                return text.isBlank() ? 0 : text.trim().split("\\s+").length;
            }

            @Override
            public TokenizerIdentity identity() {
                return new TokenizerIdentity(identityValue);
            }
        };
    }
}
