package io.algernon.vespera.engine.embedding;

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
 * <p>Since ADR-409 it also composes {@link Placements}, which records each scored document's winning topic and the
 * topics it fits beside its score, and {@link Topics}, which holds the topics the scoring step keeps: both are
 * discarded with the step's other rows (ADR-409 section 5).
 *
 * <p>Nothing here is a double: the scorer is ADR-020's real arithmetic and every table is read and written for
 * real, in whatever {@code jdbcTemplate} the importing context provides, which for {@code pipeline}'s invocation
 * tests is the in-memory database {@code schema.sql} was applied to.
 */
@Configuration
public class RelevanceScoringBeans {

    /** Real scoring over {@code jdbcTemplate}'s vector, relevance-score, placement and topic tables. */
    public static RelevanceScoring real(JdbcTemplate jdbcTemplate) {
        return new RelevanceScoring(
                new VectorCache(jdbcTemplate),
                new RelevanceScorer(),
                new RelevanceScoreCache(jdbcTemplate),
                new Placements(jdbcTemplate),
                new Topics(jdbcTemplate));
    }

    @Bean
    RelevanceScoring relevanceScoring(JdbcTemplate jdbcTemplate) {
        return real(jdbcTemplate);
    }
}
