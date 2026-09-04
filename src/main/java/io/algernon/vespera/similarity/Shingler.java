package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code similarity}'s own shingling function and its table (ADR-038, ADR-073): overlapping word
 * windows of extracted text, hashed to 64 bits and written under one run's shingle set. {@code pipeline}
 * calls {@link #write} once per converted document, in the same open-document pass that hands the same
 * text to {@code extraction}'s metric writer — this class never reaches into Docling's response itself,
 * so it stays usable against any text, extracted or not.
 *
 * <p>Only the raw hashes are written here. Document frequency is a {@code GROUP BY} stage 3 runs later
 * over this table; MinHash signature computation over these hashes is stage 4's own decision (ADR-018),
 * out of scope for this pass.
 */
@Component
public class Shingler {

    private final JdbcTemplate jdbcTemplate;

    public Shingler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Computes and stores {@code text}'s shingle hashes under today's default granularity. */
    public void write(OccurrenceId occurrenceId, RunId runId, String text) {
        write(occurrenceId, runId, text, ShingleParameters.DEFAULT);
    }

    /** As {@link #write(OccurrenceId, RunId, String)}, under an explicitly named granularity. */
    void write(OccurrenceId occurrenceId, RunId runId, String text, ShingleParameters parameters) {
        for (long hash : hashesOf(text, parameters)) {
            jdbcTemplate.update(
                    "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                            + " VALUES (?, ?, ?, ?)",
                    occurrenceId.value(),
                    runId.value(),
                    parameters.identity(),
                    hash);
        }
    }

    /**
     * {@code text}'s shingle hashes under {@code parameters}, computed with no database involved — the
     * seam the shingling function itself is tested at, independent of how (or whether) a row is ever
     * stored.
     */
    List<Long> hashesOf(String text, ShingleParameters parameters) {
        List<String> words = words(text);
        int windowSize = parameters.windowSizeWords();
        if (words.size() < windowSize) {
            return List.of();
        }
        List<Long> hashes = new ArrayList<>(words.size() - windowSize + 1);
        for (int start = 0; start <= words.size() - windowSize; start++) {
            hashes.add(hash64(String.join(" ", words.subList(start, start + windowSize))));
        }
        return hashes;
    }

    /**
     * Lowercased, whitespace-separated words with punctuation stripped — one shared normalisation so
     * that two documents sharing a passage produce matching hashes for it regardless of case or
     * punctuation around the words.
     */
    private static List<String> words(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
        return Arrays.stream(normalized.split("\\s+")).filter(word -> !word.isBlank()).toList();
    }

    /**
     * The first 8 bytes of {@code window}'s SHA-256, as a signed 64-bit value — no new hashing
     * dependency (ADR-046's rule, the same call ADR-068 made for the broken-document floor), reusing
     * the algorithm {@code extraction}'s own {@code ContentHashing} already relies on every JVM to
     * provide.
     */
    private static long hash64(String window) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
        byte[] hash = digest.digest(window.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
    }
}
