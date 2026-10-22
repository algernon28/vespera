package io.algernon.vespera.deliverable;

import java.util.Arrays;
import java.util.function.LongConsumer;

/**
 * Where each membership entry of a cluster's page written whole ends (ADR-286 §2, ADR-303 §3): counts the entries
 * and, where the tree has a ceiling to plan by, holds one number for each until the cluster's pages are written.
 */
final class EntryEnds implements LongConsumer {

    private final boolean held;
    private long[] ends = new long[16];
    private int count;

    /** @param held whether to keep each end, which only a cluster that may be split needs */
    EntryEnds(boolean held) {
        this.held = held;
    }

    @Override
    public void accept(long end) {
        if (held) {
            if (count == ends.length) {
                ends = Arrays.copyOf(ends, count * 2);
            }
            ends[count] = end;
        }
        count++;
    }

    /** How many entries the page holds, with or without a document. */
    int count() {
        return count;
    }

    /** The bytes of each entry, given the bytes the page holds before its first. */
    long[] sizes(long beforeTheFirst) {
        long[] sizes = new long[count];
        long previous = beforeTheFirst;
        for (int at = 0; at < count; at++) {
            sizes[at] = ends[at] - previous;
            previous = ends[at];
        }
        return sizes;
    }
}
