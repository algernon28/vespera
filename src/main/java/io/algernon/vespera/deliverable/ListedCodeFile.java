package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import java.time.Instant;

/**
 * One member of a code set as the tree copies and lists it (ADR-296 §2, §5): its occurrence and root-relative path,
 * the set's folder, its role and generator as {@code code_set_member} holds them, and the size and last-modified time
 * the walk recorded, which a copy is checked against.
 *
 * @param occurrence the member's occurrence id
 * @param path the member's root-relative path
 * @param setFolder the folder of the set the member belongs to, {@code ""} for the root's set
 * @param role {@code code}, {@code companion}, {@code build} or {@code configuration}
 * @param generator the generator of a companion or a marked page, or {@code null}
 * @param recordedSizeBytes the file's size as the walk recorded it
 * @param recordedLastModified the file's last-modified time as the walk recorded it
 */
public record ListedCodeFile(
        OccurrenceId occurrence,
        OccurrencePath path,
        String setFolder,
        String role,
        String generator,
        long recordedSizeBytes,
        Instant recordedLastModified) {}
