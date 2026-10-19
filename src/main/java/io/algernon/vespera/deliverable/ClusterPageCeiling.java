package io.algernon.vespera.deliverable;

/**
 * What the tree is told of the profile's byte ceiling on a cluster page (ADR-303 §11): a plain value, so this module
 * learns no {@code Profile} (ADR-110). {@code pipeline} reads the profile's key and hands one of the three.
 */
public sealed interface ClusterPageCeiling {

    /** The key as {@code profile.yaml} spells it, which {@code cluster-page-sizes.html} names. */
    String KEY = "clusterPageByteCeiling";

    /** Nothing written: no cluster's page is split. */
    record Unset() implements ClusterPageCeiling {}

    /**
     * A cluster page larger than this many bytes is split at an entry's end.
     *
     * @param bytes from 1
     */
    record Bytes(long bytes) implements ClusterPageCeiling {

        public Bytes {
            if (bytes < 1) {
                throw new IllegalArgumentException("a ceiling is 1 byte or more, not " + bytes);
            }
        }
    }

    /** Something no whole number of bytes from 1 is, read as unset; the report says only that. */
    record Ignored() implements ClusterPageCeiling {}
}
