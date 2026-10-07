package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A document whose conversion and chunks are taken out of the caches between two invocations, and can be
 * put back: how a test reaches a cluster nothing can be sent for (ADR-121) with rows an arrangement
 * really records.
 *
 * <p>The file is not touched, so the walk sees the same archive, every run is continued under its own id
 * and the approval still names the arrangement (ADR-115, ADR-154). No step converts or chunks a document
 * again once its work is recorded: stage 2 and the embedding step are finished and walked past, and the
 * labelling page reads the extraction cache and converts nothing (ADR-152). Stage 6b then finds no chunk
 * on record for the document, so the member is still there and nothing of it can be sent.
 *
 * <p><b>Both caches, not the chunks alone.</b> The labelling page is written on every invocation and
 * chunks a sampled document's conversion again where its chunks are missing, which would put them back
 * before 6b ran. With the conversion gone too it has nothing to chunk.
 *
 * <p><b>Why not a file rewritten in place</b>, which is what these tests used until ADR-206
 * ({@link UnseenEditFixture}). Until then every step found a document's conversion by hashing its file
 * again, so changed bytes led to nothing cached. Each step now reads the key stage 2 recorded, and a
 * document rewritten in place is sent as it was converted. Removing the rows reaches the same branch of
 * 6b under either reading, so the tests that use this say the same thing before and after that record.
 * It is a state no run leaves behind, which the fixture it replaces was not; ADR-206 section 5 says so of
 * the route it closed.
 */
final class ConversionOffTheRecordFixture {

    private static final List<String> CACHES = List.of("extraction_cache", "chunk_cache");

    private ConversionOffTheRecordFixture() {
    }

    /** What was on record for one document's content, so that {@link #putBack} can restore it. */
    record Held(String contentHash, Map<String, List<Map<String, Object>>> rowsByCache) {
    }

    /**
     * Removes every conversion and every chunk on record for the bytes {@code file} holds.
     *
     * @throws IllegalStateException if no conversion was on record for them, since a test that went on
     *     would then claim something about a document nothing was ever taken from
     */
    static Held takenOffTheRecord(JdbcTemplate jdbcTemplate, Path file) throws IOException {
        String contentHash = sha256Of(file);
        Map<String, List<Map<String, Object>>> rowsByCache = CACHES.stream()
                .collect(Collectors.toMap(
                        cache -> cache,
                        cache -> jdbcTemplate.queryForList(
                                "SELECT * FROM " + cache + " WHERE content_hash = ?", contentHash)));
        if (rowsByCache.get("extraction_cache").isEmpty()) {
            throw new IllegalStateException("no conversion is on record for " + file
                    + ", so this fixture has nothing to take off the record");
        }
        for (String cache : CACHES) {
            jdbcTemplate.update("DELETE FROM " + cache + " WHERE content_hash = ?", contentHash);
        }
        return new Held(contentHash, rowsByCache);
    }

    /** Puts back, row for row, what {@link #takenOffTheRecord} removed. */
    static void putBack(JdbcTemplate jdbcTemplate, Held held) {
        for (String cache : CACHES) {
            for (Map<String, Object> row : held.rowsByCache().get(cache)) {
                List<String> columns = List.copyOf(row.keySet());
                jdbcTemplate.update(
                        "INSERT INTO " + cache + " (" + String.join(", ", columns) + ") VALUES ("
                                + columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")",
                        columns.stream().map(row::get).toArray());
            }
        }
    }

    /** The SHA-256 of {@code file}'s bytes as lowercase hexadecimal, computed with the JDK alone. */
    private static String sha256Of(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
