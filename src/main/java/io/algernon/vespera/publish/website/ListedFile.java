package io.algernon.vespera.publish.website;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One file of the website rendering as its list, {@value #LIST_FILE_NAME}, names it (ADR-333 §3): its {@code path} in
 * the website, its {@code source} from the rendering's folder, its {@code size}, its {@code sha256} and its {@code
 * kind}, one of {@link #PAGE}, {@link #ASSET}, {@link #LISTING} or {@link #COPY}.
 */
record ListedFile(String path, String source, long size, String sha256, String kind) {

    /** The rendering's list, written last (ADR-333 §3). */
    static final String LIST_FILE_NAME = "files.json";

    static final String PAGE = "page";
    static final String ASSET = "asset";
    static final String LISTING = "listing";
    static final String COPY = "copy";

    /** The SHA-256 of {@code file}'s bytes, lower-case hexadecimal, read a buffer at a time. */
    static String sha256Of(Path file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest())) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        }
    }

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
