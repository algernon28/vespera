package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.similarity.Shingler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Watches what a finished invocation cannot be asked about afterwards: whether {@code shingle_by_hash}
 * stood in the database at the moment stage 2 wrote each document's shingles (ADR-182).
 *
 * <p><b>Why a probe and not a query.</b> The lookup by hash may be absent while stage 2 writes and
 * present again by the time the invocation ends, because stage 4b builds it, so the database after the
 * job says nothing about what stage 2 paid for. The only place to see it is inside the write, on the
 * same connection and in the same chunk transaction.
 *
 * <p>{@code @Primary} over the real {@link Shingler} rather than in place of it: the probe is a
 * subclass that delegates, so every row it watches is written by {@code Shingler}'s own code.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents: left plain, this sits inside the application's component-scan
 * package and would replace the shingler in every {@code @SpringBootTest} in the suite.
 */
@TestConfiguration
class ShingleWritesProbe {

    /** The index stage 2 must not maintain, by the name {@code sqlite_master} carries. */
    static final String LOOKUP_BY_HASH = "shingle_by_hash";

    /**
     * One entry per document whose shingles stage 2 wrote, {@code true} where the lookup by hash stood
     * at that moment. Static because the bean is built per application context and read per test
     * method; {@link #forget()} keeps one method's writes from being read as another's.
     */
    static final List<Boolean> LOOKUP_STOOD_WHEN_A_DOCUMENT_WAS_WRITTEN = new ArrayList<>();

    /** Drops everything observed so far. Called before each test, never only after one. */
    static void forget() {
        LOOKUP_STOOD_WHEN_A_DOCUMENT_WAS_WRITTEN.clear();
    }

    @Bean
    @Primary
    @DependsOnDatabaseInitialization
    Shingler shingleWritesProbingShingler(JdbcTemplate jdbcTemplate) {
        return new Shingler(jdbcTemplate) {
            @Override
            public void write(OccurrenceId occurrenceId, RunId runId, String text) {
                Long lookups = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                        Long.class,
                        LOOKUP_BY_HASH);
                LOOKUP_STOOD_WHEN_A_DOCUMENT_WAS_WRITTEN.add(lookups != null && lookups > 0);
                super.write(occurrenceId, runId, text);
            }
        };
    }
}
