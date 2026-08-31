package io.algernon.vespera.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * The identity a run is minted under: the hash of everything that determines what the run would
 * produce — its implementation version, the configuration it consumed, the walk it read, and the
 * runs it read downstream of (ADR-048).
 *
 * <p>Content-derived rather than surrogate, and that is the point: two runs that would produce
 * identical verdicts have identical ids, so re-running a stage nothing has changed for is
 * recognisable as such rather than a second opinion. A walk's identity is a surrogate key for the
 * opposite reason — an observation of a filesystem is not determined by anything the tool holds.
 */
public record RunId(String value) {

    public RunId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a run id is never blank");
        }
    }

    /**
     * Derives the identity of a run from the four things ADR-048 names.
     *
     * <p>Upstream ids are sorted before hashing: the set of runs read is what determines the output,
     * the order they were listed in is not, so two callers naming the same upstream runs differently
     * must not mint two ids.
     */
    public static RunId of(
            String implementationVersion, String configConsumed, WalkId walkId, List<RunId> upstreamRunIds) {
        StringBuilder material = new StringBuilder()
                .append(implementationVersion)
                .append('\u0000')
                .append(configConsumed)
                .append('\u0000')
                .append(walkId.value());
        upstreamRunIds.stream().map(RunId::value).sorted().forEach(id -> material.append('\u0000')
                .append(id));

        return new RunId(sha256(material.toString()));
    }

    private static String sha256(String material) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(material.getBytes(StandardCharsets.UTF_8))) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }

    @Override
    public String toString() {
        return value;
    }
}
