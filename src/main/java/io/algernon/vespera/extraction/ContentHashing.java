package io.algernon.vespera.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The same SHA-256 content identity {@code corpus}'s {@code ContentHash} computes (ADR-067), as
 * {@code extraction}'s own copy rather than a dependency on {@code corpus} — a capability module may
 * depend on {@code ledger} and nothing else horizontal (ADR-040), and stage 1's hashing is scoped to
 * survivors sharing a size (ADR-067's grouping), so an occurrence with no same-size peer reaches this
 * module never hashed at all. This is where that gap is closed: the extraction cache has to be keyed
 * on a content hash regardless of whether stage 1 ever computed one.
 *
 * <p>The hash computed here keys {@code extraction}'s cache rows, and the steps that read a file for the
 * first time record it in {@code extraction_cache_key}; every later step reads it from there (ADR-206)
 * and none hashes the file again. It is not written back into {@code corpus}'s {@code content_hash}
 * table, which {@code corpus} owns (ADR-067, ADR-041); writing into another capability's table would
 * cross the same boundary depending on its Java types would.
 */
final class ContentHashing {

    /**
     * How many bytes of a file are held at once while it is hashed (ADR-207): one buffer of this length,
     * whatever the file's size, so no file is too large to hash.
     */
    private static final int BUFFER_BYTES = 65_536;

    private ContentHashing() {}

    /**
     * The SHA-256 of {@code file}'s content, as lowercase hex, read through one buffer of {@link
     * #BUFFER_BYTES} bytes and never gathered whole (ADR-207).
     */
    static String sha256(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("could not hash " + file, e);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }
}
