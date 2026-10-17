package io.algernon.vespera.synthesis;

import java.util.regex.Pattern;

/**
 * What a citation is, written once (ADR-109, ADR-213 §4): a bracketed ordinal into the exemplars one
 * call sent. {@link ClusterSynthesis} reads it to check every ordinal a stored answer carries is in
 * range, and {@code deliverable} reads it to rewrite each one into a link (ADR-273 §4), so the check and
 * the rewrite cannot come to disagree about what a citation is.
 */
public final class Citation {

    /** A citation as the model wrote it: {@code [n]}, with the ordinal as group 1. */
    public static final Pattern AS_WRITTEN = Pattern.compile("\\[(\\d+)\\]");

    private Citation() {}
}
