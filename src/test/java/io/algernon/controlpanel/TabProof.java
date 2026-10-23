package io.algernon.controlpanel;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * What a tab of the control panel computes to tell whether the server answering it holds the start's token (ADR-325
 * §4.1 to §4.3), computed as the control panel's script computes it, for the tests that play the tab or check the
 * server's answer.
 */
final class TabProof {

    /** The path a tab polls, answered without the cookie. */
    static final String ALIVE = "/alive";

    /** The path of the control panel's one script. */
    static final String SCRIPT = "/control-panel.js";

    /** The ASCII text whose HMAC-SHA256, keyed with the token, is the tab's key. */
    static final String TAB_KEY_TEXT = "vespera-control-panel-tab-key";

    /** How many random bytes a nonce is drawn from: 16, written as 32 lowercase hexadecimal characters. */
    static final int NONCE_BYTES = 16;

    private static final String HMAC = "HmacSHA256";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TabProof() {
    }

    /** The tab's key a page carries for {@code token}, as 43 characters of base64url without padding. */
    static String tabKeyOf(String token) {
        return base64url(hmac(token.getBytes(StandardCharsets.US_ASCII), TAB_KEY_TEXT));
    }

    /**
     * The proof a server holding the token answers {@code nonce} with, at {@code port} under {@code mark}: the HMAC-SHA256
     * of {@code <port> <mark> <nonce>} under the tab's key; empty where the key is not 32 bytes of base64url.
     */
    static String proof(String tabKey, int port, String mark, String nonce) {
        byte[] key = decoded(tabKey);
        return key.length == 0 ? "" : base64url(hmac(key, port + " " + mark + " " + nonce));
    }

    /** Whether {@code proof} is the one {@link #proof} gives, compared as the script compares it, in constant time. */
    static boolean verifies(String tabKey, int port, String mark, String nonce, String proof) {
        String expected = proof(tabKey, port, mark, nonce);
        return !expected.isEmpty() && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                proof.getBytes(StandardCharsets.US_ASCII));
    }

    /** A nonce as the script draws it: 16 random bytes as 32 lowercase hexadecimal characters. */
    static String nonce() {
        byte[] drawn = new byte[NONCE_BYTES];
        RANDOM.nextBytes(drawn);
        return HexFormat.of().formatHex(drawn);
    }

    /** The request target a tab polls with {@code nonce}. */
    static String aliveTarget(String nonce) {
        return ALIVE + "?nonce=" + nonce;
    }

    private static byte[] hmac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(text.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("the JDK computes no " + HMAC, e);
        }
    }

    private static byte[] decoded(String base64url) {
        try {
            byte[] key = Base64.getUrlDecoder().decode(base64url);
            return key.length == 32 ? key : new byte[0];
        } catch (IllegalArgumentException notBase64url) {
            return new byte[0];
        }
    }

    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
