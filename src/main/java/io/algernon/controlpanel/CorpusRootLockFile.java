package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Where the jar keeps the corpus-root lock of a corpus root (ADR-279 §2, §3), named by the same rule in {@code
 * java.base}, because the control panel links no class of the jar (ADR-285 §3): the root's real path, or its absolute
 * normalised path where it has none, and the first sixteen hexadecimal digits of that key's SHA-256 with {@code .lock}
 * after them, in the folder ADR-302 §2 names. A contract test holds this to the jar's {@code CorpusRootLock}.
 */
final class CorpusRootLockFile {

    private static final int DIGITS = 16;

    private CorpusRootLockFile() {
    }

    /** The lock file of {@code corpusRoot} in {@code folder}. */
    static Path in(Path folder, Path corpusRoot) {
        Path key;
        try {
            key = corpusRoot.toRealPath();
        } catch (IOException | RuntimeException notThere) {
            key = corpusRoot.toAbsolutePath().normalize();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return folder.resolve(HexFormat.of().formatHex(digest).substring(0, DIGITS) + ".lock");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }
}
