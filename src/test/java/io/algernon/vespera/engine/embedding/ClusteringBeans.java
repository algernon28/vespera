package io.algernon.vespera.engine.embedding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a real {@link Clustering} for a test outside this package, for the reason {@link
 * RelevanceScoringBeans} already does for {@code RelevanceScoring}: its constructor and that of {@link VectorCache},
 * one of the three collaborators it composes, are package-private, deliberately, so clustering is only ever
 * assembled by {@code embedding}'s own wiring or by a package-mate on its behalf.
 *
 * <p>Since ADR-409 it reads a topic's placed survivors from {@link Placements}, where it read a seed partition's
 * members from the relevance scores, and records each member's subtopic and cluster through {@link
 * DocumentClusters} (ADR-409 section 8).
 *
 * <p>Nothing here is a double: the graph, the communities and every table are the real ones, reading
 * and writing the real tables of whatever {@code jdbcTemplate} the importing context provides.
 */
@Configuration
public class ClusteringBeans {

    /** Real clustering over {@code jdbcTemplate}'s vector, placement and cluster-member tables. */
    public static Clustering real(JdbcTemplate jdbcTemplate) {
        return new Clustering(
                new VectorCache(jdbcTemplate),
                new Placements(jdbcTemplate),
                new DocumentClusters(jdbcTemplate));
    }

    @Bean
    Clustering clustering(JdbcTemplate jdbcTemplate) {
        return real(jdbcTemplate);
    }
}
