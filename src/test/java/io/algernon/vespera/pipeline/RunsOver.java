package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import java.nio.file.Path;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The runs the ledger holds over one corpus root, read the way every full-run test reads them (#678).
 *
 * <p><b>Scoped to the root's walks.</b> A full-run class shares one working directory, and so one
 * database, across its tests; each test walks a root of its own. A read that is not joined to the walk of
 * that root is a read of every corpus any test of the class walked.
 */
final class RunsOver {

    private final JdbcTemplate jdbc;
    private final String root;

    RunsOver(JdbcTemplate jdbc, Path root) {
        this.jdbc = jdbc;
        this.root = Walk.canonicalRoot(root).toString();
    }

    /** The run of {@code stage} most recently minted over the root: the one the latest page names. */
    RunId latest(String stage) {
        return new RunId(jdbc.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                stage,
                root));
    }

    /** The configuration each run of {@code stage} over the root was derived from, oldest first; none if none ran. */
    List<String> configConsumed(String stage) {
        return jdbc.queryForList(
                "SELECT r.config_consumed FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                stage,
                root);
    }
}
