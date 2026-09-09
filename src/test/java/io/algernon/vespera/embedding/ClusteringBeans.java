package io.algernon.vespera.embedding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a real {@link Clustering} for a test outside this package, for the reason {@link
 * RelevanceScoringBeans} already does for {@code RelevanceScoring}: its constructor and those of two
 * of the three collaborators it composes — {@link VectorCache} and {@link RelevanceScoreCache} — are
 * package-private, deliberately, so clustering is only ever assembled by {@code embedding}'s own
 * wiring or by a package-mate on its behalf.
 *
 * <p>Nothing here is a double: the graph, the communities and both caches are the real ones, reading
 * and writing the real tables of whatever {@code jdbcTemplate} the importing context provides.
 */
@Configuration
public class ClusteringBeans {

    /** Real clustering over {@code jdbcTemplate}'s vector, relevance-score and document-cluster tables. */
    public static Clustering real(JdbcTemplate jdbcTemplate) {
        return new Clustering(
                new VectorCache(jdbcTemplate),
                new RelevanceScoreCache(jdbcTemplate),
                new DocumentClusters(jdbcTemplate));
    }

    @Bean
    Clustering clustering(JdbcTemplate jdbcTemplate) {
        return real(jdbcTemplate);
    }
}
