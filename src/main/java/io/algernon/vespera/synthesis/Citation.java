package io.algernon.vespera.synthesis;

import java.util.regex.Pattern;

/**
 * What a citation is, written once (ADR-109, ADR-212 §4): a bracketed ordinal into the exemplars one
 * call sent. {@link ClusterSynthesis} reads it to check every ordinal a stored answer carries is in
 * range, and {@link ClusterPage} reads it to rewrite each one into a link, so the check and the rewrite
 * cannot come to disagree about what a citation is.
 */
final class Citation {

    /** A citation as the model wrote it: {@code [n]}, with the ordinal as group 1. */
    static final Pattern AS_WRITTEN = Pattern.compile("\\[(\\d+)\\]");

    private Citation() {}
}
