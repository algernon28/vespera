package io.algernon.vespera.synthesis;

/**
 * A filename without its folders or its extension, written once (ADR-213 §4). {@link ClusterLabel}
 * names a cluster from it where the lead document has no title, and the deliverable names a partition
 * directory and a picture directory from it.
 */
final class FilenameStem {

    private FilenameStem() {}

    /**
     * {@code path}'s last {@code /}-separated segment without its last extension.
     *
     * <p>A stored path is separator-normalised to {@code /} already (ADR-051), so the last segment is
     * the filename whatever the walk found it on.
     *
     * <p>A name that is <em>only</em> an extension yields an empty stem rather than itself, which is
     * what makes {@link ClusterLabel}'s third tier reachable at all. Left the other way the chain would
     * have had a tier nothing could arrive at — the defect ADR-096 was written about one stage earlier.
     */
    static String of(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int extension = filename.lastIndexOf('.');
        return extension < 0 ? filename : filename.substring(0, extension);
    }
}
