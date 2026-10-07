package io.algernon.vespera.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Byte-exact content identity: a SHA-256 over a file's bytes (ADR-067). Unconditional and
 * cryptographic, deliberately — at this corpus's scale collision is not a practical concern, the
 * JDK carries {@code MessageDigest} for free, and stage 1 is I/O-bound reading the file regardless
 * of digest choice, so a faster non-cryptographic hash would buy nothing.
 */
public final class ContentHash {

    /**
     * How many bytes of a file are held at once while it is hashed (ADR-207): one buffer of this length,
     * whatever the file's size, so no file is too large to hash.
     */
    private static final int BUFFER_BYTES = 65_536;

    private ContentHash() {}

    /**
     * The SHA-256 of {@code file}'s content, as lowercase hex, read through one buffer of {@link
     * #BUFFER_BYTES} bytes and never gathered whole (ADR-207).
     */
    public static String sha256(Path file) throws IOException {
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
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }
}
