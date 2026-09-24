# ADR-144 — A chunk the runtime refuses as too long is split until every piece fits

- **Date**: 2026-09-24
- **Status**: accepted
- **Amends**: [ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md), whose rule that "a rejected chunk is split and retried" stands. This record makes the split always terminate, and limits it to the one refusal it was written for.
- **Settles** [#272](https://github.com/algernon28/vespera/issues/272).

## Context

`ChunkEmbedder` calls `/api/embed` with `truncate: false`. When the runtime refuses an input, it halves the text by words, embeds each half the same way, and averages the two vectors. A single word could not be halved, so the refusal was rethrown and failed the whole invocation.

#272 found that happening on real data. `Dati_tecnici_tetra_TID-257999.txt` is 4,183 characters in 40 words, and one of those "words" is 3,537 characters of semicolon-joined identifiers. Measured on 2026-09-24 against `bge-m3:latest` on `ollama/ollama:0.33.2`:

- **The limit is Ollama's batch size, not the model's context.** `ollama show` reports a context length of 8,192. The longest prefix of that word that embeds is 3,323 characters, which is 2,048 tokens. Sending `num_ctx: 8192` alone changes nothing; only `num_ctx` and `num_batch` together let the whole word through.
- **Prose never gets near that limit.** 3,500 characters of ordinary prose cost 724 tokens. Identifier soup costs about 0.6 tokens per character, so a 512-word chunk of it can exceed 2,048 tokens.

The javadoc said this path was "close to dead" because of "the candidate model's 32k-token context". That is false of the configured model, and false of the limit that actually applies.

## Decision

**A refused single word is split by code points.** Halving by words is tried first, as before. Where one word is left, it is cut at its middle code point, never between the two halves of a surrogate pair. Only a single code point cannot split further, and no runtime refuses one on length, so the recursion always ends in inputs the runtime accepts. The pieces are averaged exactly as word halves are.

**Only a refusal on length is split.** The split runs when the refusal's message carries Ollama's own words, `input length exceeds`. Any other refusal — a model the runtime does not have, for instance — reaches the caller on the first call. Splitting it would only multiply the calls before the same failure, and with character splitting that would be thousands of calls per chunk.

**`num_batch` is not sent.** Raising it would let most such chunks through whole, but it changes the options of every embed call, and whether it changes any vector has not been measured. The split changes nothing for a chunk that fits today, so every cached vector stays valid.

## Consequences

**A chunk of any shape now embeds.** The #272 folder, re-run on 2026-09-24 with this change, exits 0 and scores all six survivors, the two identifier dumps among them at 0.659 and 0.657. `ChunkEmbedderTest.splitsAWordTooLongForTheRuntimeByCharacters` pins the split: every piece the runtime is handed fits, and the pieces join back into the whole word.

**The vector of a chunk split this way is an average of pieces cut through the middle of a token.** For identifier dumps that costs little, since they are not prose to begin with, and the alternative was losing the whole invocation.

**A refusal that is not about length now fails at once instead of after a word-by-word descent.** `ChunkEmbedderTest.doesNotSplitARefusalThatIsNotAboutLength` pins that it is called once. What it fails is the invocation, as before: a runtime that refuses every call poisons every row, which is what stopping the run is for.

**The match is on Ollama's message text.** If a later Ollama words its refusal differently, an overlong chunk fails the invocation again rather than being embedded wrongly. That is the safe direction, and it is loud.
