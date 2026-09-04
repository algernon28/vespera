package io.algernon.vespera.extraction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a real {@link HybridChunker} for a test outside this package (mirrors why
 * {@link ScriptedExtractor} lives here rather than beside its callers): {@link HybridChunker}'s
 * constructor, {@link ChunkCache}, and {@link WindowedStructurelessChunkingFallback} are all
 * package-private, deliberately, so a chunker is only ever built by {@code extraction}'s own wiring
 * or a package-mate on its behalf — never by widening them for one caller outside the package.
 *
 * <p>{@link #real(JdbcTemplate)} is the plain factory a test constructing {@link HybridChunker}
 * manually can call; the {@code @Bean} method is the same factory for a test that instead wires it
 * through a Spring context by class ({@code @Import(HybridChunkerBeans.class)}), the way {@code
 * pipeline}'s own {@code StubbedExtractionBeans} fakes {@link DoclingExtractor} for the same reason.
 */
@Configuration
public class HybridChunkerBeans {

    /** A real chunker over {@code jdbcTemplate}'s chunk cache, chunking with the windowed fallback. */
    public static HybridChunker real(JdbcTemplate jdbcTemplate) {
        return new HybridChunker(new ChunkCache(jdbcTemplate), new WindowedStructurelessChunkingFallback());
    }

    @Bean
    HybridChunker hybridChunker(JdbcTemplate jdbcTemplate) {
        return real(jdbcTemplate);
    }
}
