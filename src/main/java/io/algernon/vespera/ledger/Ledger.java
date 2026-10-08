package io.algernon.vespera.ledger;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The single record of file occurrence identity, verdicts and run identity (see CONTEXT.md's
 * "Ledger"), handed out as four records and holding no SQL of its own (ADR-209).
 *
 * <p>Deep by construction: the tables are behind these four records and nothing else queries them
 * (ADR-041). The survivors query is the case that makes the rule worth the trouble: {@link
 * Verdicts#survivors} hands out the ids rather than SQL, so no other module gets to join against {@code
 * verdict} and quietly depend on its shape (ADR-060).
 *
 * <p>Kept as one bean so that no constructor changes for it: a caller says which job it uses at each
 * call, {@code ledger.walks()}, {@code ledger.occurrences()}, {@code ledger.runs()} or {@code
 * ledger.verdicts()}. Each is overridable, so a test can stand a recording record in the real one's place.
 */
@Component
@DependsOnDatabaseInitialization
public class Ledger {

    private final Walks walks;
    private final Occurrences occurrences;
    private final Runs runs;
    private final Verdicts verdicts;

    public Ledger(JdbcTemplate jdbcTemplate) {
        this.walks = new Walks(jdbcTemplate);
        this.occurrences = new Occurrences(jdbcTemplate);
        this.runs = new Runs(jdbcTemplate);
        this.verdicts = new Verdicts(jdbcTemplate);
    }

    /** What is asked about a walk: the {@code walk} table. */
    public Walks walks() {
        return walks;
    }

    /** What is asked about the file occurrences a walk found: the {@code file_occurrence} table. */
    public Occurrences occurrences() {
        return occurrences;
    }

    /** What is asked about a run and its steps: {@code run}, {@code run_upstream} and {@code finished_step}. */
    public Runs runs() {
        return runs;
    }

    /** What is asked about verdicts and the survivors they leave: the {@code verdict} table. */
    public Verdicts verdicts() {
        return verdicts;
    }
}
