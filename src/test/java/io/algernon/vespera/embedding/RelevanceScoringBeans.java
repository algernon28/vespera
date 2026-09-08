package io.algernon.vespera.embedding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a real {@link RelevanceScoring} for a test outside this package, for the reason {@link
 * ChunkEmbedderBeans} already does for {@code ChunkEmbedder}: its constructor and those of the three
 * collaborators it composes — {@link VectorCache}, {@link RelevanceScorer}, {@link RelevanceScoreCache}
 * — are package-private, deliberately, so scoring is only ever assembled by {@code embedding}'s own
 * wiring or by a package-mate on its behalf.
 *
 * <p>Nothing here is a double: the scorer is ADR-020's real arithmetic and both caches read and write
 * the real tables of whatever {@code jdbcTemplate} the importing context provides, which for {@code
 * pipeline}'s invocation tests is the in-memory database {@code schema.sql} was applied to.
 */
@Configuration
public class RelevanceScoringBeans {

    /** Real scoring over {@code jdbcTemplate}'s vector and relevance-score tables. */
    public static RelevanceScoring real(JdbcTemplate jdbcTemplate) {
        return new RelevanceScoring(
                new VectorCache(jdbcTemplate), new RelevanceScorer(), new RelevanceScoreCache(jdbcTemplate));
    }

    @Bean
    RelevanceScoring relevanceScoring(JdbcTemplate jdbcTemplate) {
        return real(jdbcTemplate);
    }
}
