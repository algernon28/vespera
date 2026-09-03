package io.algernon.vespera.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
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
 * <p>The hash computed here is used only to key {@code extraction}'s own cache row — it is not written
 * back into {@code corpus}'s {@code content_hash} table, which {@code corpus} owns (ADR-067, ADR-041);
 * writing into another capability's table would cross the same boundary depending on its Java types
 * would.
 */
final class ContentHashing {

    private ContentHashing() {}

    /** The SHA-256 of {@code file}'s content, as lowercase hex, streamed rather than loaded whole. */
    static String sha256(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
        try (InputStream in = Files.newInputStream(file);
                DigestInputStream digesting = new DigestInputStream(in, digest)) {
            digesting.readAllBytes();
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
