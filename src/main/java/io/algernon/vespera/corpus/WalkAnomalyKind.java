package io.algernon.vespera.corpus;

/**
 * The closed vocabulary of walk anomalies (ADR-053), owned by {@code corpus} as a fact about
 * filesystem traversal rather than a ledger concept.
 */
enum WalkAnomalyKind {

    /**
     * The walk saw the entry and could not read or record it — permission denied, an I/O error, a
     * path too long, not a regular file, or a directory listing that did not complete.
     */
    UNPROCESSABLE,

    /** A symlink, junction, or volume mount point, skipped rather than followed (ADR-051). */
    SOFT_LINK_NOT_FOLLOWED,

    /** The filename has no UTF-8 encoding, so it cannot be stored as text (ADR-051). */
    UNENCODABLE_PATH,
}
