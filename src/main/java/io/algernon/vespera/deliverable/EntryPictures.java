package io.algernon.vespera.deliverable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The pictures a document carries under its membership entry (ADR-149, ADR-150, #285): which pictures
 * across the whole tree are furniture, and what is written and shown for the rest.
 *
 * <p><b>Two passes that never hold more than one document's pixels at a time (ADR-149 §9).</b> {@link
 * #among} is the first: every listed survivor's pictures are asked for once, and only the digest and difference
 * hash of each distinct picture are kept, from which the set of furniture digests is decided under
 * ADR-149 §1(a)/(b) and ADR-150 §3(c)/(d) (ADR-223 §7). {@link #appendUnder} is the second, one entry at a time: the
 * source is asked again, and what passes the furniture rule is written beside the cluster page.
 *
 * <p><b>One SHA-256 per picture per pass</b> (ADR-213 §5). The second pass digests each picture once, and
 * that one digest answers the furniture check and names the file. The first pass's digests are not
 * carried into it: the second answer is a new answer, and a picture's file is named from its own bytes
 * (ADR-149 §3), so the bytes digested must be the bytes written.
 */
final class EntryPictures {

    /** The media type a survivor's picture is written under a {@code .png} file for. */
    private static final String PNG_MEDIA_TYPE = "image/png";

    /** The media type a survivor's picture is written under a {@code .jpg} file for. */
    private static final String JPEG_MEDIA_TYPE = "image/jpeg";

    /** How many hexadecimal characters of a picture's SHA-256 digest its file is named from (ADR-149 §3). */
    private static final int PICTURE_NAME_LENGTH = 16;

    /** The near-copy rule's pixel tolerance, on width and height each (ADR-150 §3(c)). */
    private static final int NEAR_COPY_PIXELS = 2;

    /** The near-copy rule's hash tolerance, in bits (ADR-150 §3(c)). */
    private static final int NEAR_COPY_BITS = 2;

    /** The same-place rule's bounding-box tolerance, in points, on each edge (ADR-150 §3(d)). */
    private static final double SAME_PLACE_POINTS = 3.0;

    /** The same-place rule's hash tolerance, in bits (ADR-150 §3(d)). */
    private static final int SAME_PLACE_BITS = 8;

    private final SurvivorPictures source;

    private final Set<String> furniture;

    private final Map<Long, String> prepared = new HashMap<>();

    private EntryPictures(SurvivorPictures source, Set<String> furniture) {
        this.source = source;
        this.furniture = furniture;
    }

    /**
     * The first pass over the survivors {@code source} hands over a page at a time: each asked about once,
     * announced to {@code progress} before the first and after each (ADR-192 §5), and the furniture among
     * their pictures decided.
     *
     * <p>What is kept until the last survivor is read is, for each distinct picture, its digest and its
     * difference hash; a picture's place is compared while its own document's pictures are in hand and kept
     * no longer (ADR-223 §7). The population stays every picture of every survivor the tree lists.
     */
    static EntryPictures among(
            ArrangedSurvivors source,
            SurvivorPictures pictures,
            DeliverableProgress progress,
            Consumer<List<ListedSurvivor>> alsoEachPage) {
        // A digest to the difference hash of the first picture seen with it, or null where its bytes do not
        // decode: equal digests are equal bytes, so equal hashes, and the first-seen picture stands for all.
        Map<String, DifferenceHash> hashes = new HashMap<>();
        Set<String> furniture = new HashSet<>();
        progress.toListPictures(source.survivorCount());
        source.eachPageOfSurvivorsForTheirPictures(page -> {
            alsoEachPage.accept(page);
            for (ListedSurvivor survivor : page) {
                List<PictureEntry> ofOneOccurrence = new ArrayList<>();
                for (ListedPicture picture : pictures.of(survivor)) {
                    String digest = sha256Hex(picture.pixels());
                    if (picture.inFurnitureLayer()) {
                        furniture.add(digest);
                    }
                    if (hashes.containsKey(digest)) {
                        furniture.add(digest);
                    } else {
                        hashes.put(digest, DifferenceHash.of(picture.pixels()).orElse(null));
                    }
                    ofOneOccurrence.add(new PictureEntry(digest, hashes.get(digest), picture.place()));
                }
                furniture.addAll(samePlaceFurniture(ofOneOccurrence));
                progress.picturesListed();
            }
        });
        furniture.addAll(nearCopyFurniture(hashes));
        return new EntryPictures(pictures, Set.copyOf(furniture));
    }

    /** The digests, in full lower-case hexadecimal, of every picture judged furniture across the tree. */
    Set<String> furniture() {
        return furniture;
    }

    /**
     * A survivor's pictures under its own membership entry (ADR-149 §3, §5, §6): the first {@link
     * Deliverable#PICTURES_PER_DOCUMENT} that pass the furniture rule, each written as a file beside the
     * page and shown by an {@code <img>} in the entry, then, where any were left uncounted or
     * unwritten, one line saying how many.
     *
     * <p><b>The budget is positions, not files written.</b> The first {@link
     * Deliverable#PICTURES_PER_DOCUMENT} non-furniture pictures in reading order are the ones this entry
     * may show; among those, only the two media types ever measured in the cache are written, and any
     * other kind, though it holds a position within the budget, is counted as not shown rather than
     * written under no extension (ADR-149 §6).
     *
     * <p><b>The alt text is the caption under the one escaping rule</b> (ADR-149 §5, ADR-274 §1.3): the
     * converter's own text, escaped like every value the tree did not write, in an attribute.
     *
     * @param folder the cluster's folder, the page's own, into which each picture is written and by whose file name
     *     alone it is shown (ADR-280 §0)
     */
    void appendUnder(StringBuilder page, ListedSurvivor member, Path folder) throws IOException {
        String held = prepared.remove(member.occurrence().value());
        page.append(held != null ? held : writeAndShow(member, folder).html());
    }

    /**
     * Writes the pictures of each of {@code members} into {@code folder} and holds how each is shown, returning the
     * names written (ADR-280 §1.5): a copy's name yields to a picture's, so the names are wanted before the cluster's
     * first copy, and a document's pictures are asked of the source once for the page, never again for the entry that
     * shows them (ADR-149 §9). What is held is each entry's few lines of HTML until {@link #appendUnder} takes it, or
     * {@link #forgetPrepared} lets it go, and never a picture's pixels.
     */
    Set<String> writeFor(List<ListedSurvivor> members, Path folder) throws IOException {
        Set<String> names = new HashSet<>();
        for (ListedSurvivor member : members) {
            Written written = writeAndShow(member, folder);
            prepared.put(member.occurrence().value(), written.html());
            names.addAll(written.names());
        }
        return names;
    }

    /** Lets go of every entry {@link #writeFor} held and {@link #appendUnder} did not take. */
    void forgetPrepared() {
        prepared.clear();
    }

    private Written writeAndShow(ListedSurvivor member, Path folder) throws IOException {
        List<NamedPicture> kept = new ArrayList<>();
        for (ListedPicture picture : source.of(member)) {
            String digest = sha256Hex(picture.pixels());
            if (!furniture.contains(digest)) {
                kept.add(new NamedPicture(picture, digest));
            }
        }
        StringBuilder page = new StringBuilder();
        Set<String> names = new HashSet<>();
        if (kept.isEmpty()) {
            return new Written("", names);
        }
        int withinBudget = Math.min(Deliverable.PICTURES_PER_DOCUMENT, kept.size());
        int notShown = kept.size() - withinBudget;
        for (NamedPicture candidate : kept.subList(0, withinBudget)) {
            Optional<String> extension = fileExtensionFor(candidate.picture().mediaType());
            if (extension.isEmpty()) {
                notShown++;
                continue;
            }
            Files.createDirectories(folder);
            String fileName = candidate.digest().substring(0, PICTURE_NAME_LENGTH) + extension.get();
            Files.write(folder.resolve(fileName), candidate.picture().pixels());
            names.add(fileName);
            page.append("<p><img src=\"")
                    .append(Html.escape(fileName))
                    .append("\" alt=\"")
                    .append(Html.escape(candidate.picture().caption()))
                    .append("\"></p>\n");
        }
        if (notShown > 0) {
            page.append("<p><em>")
                    .append(notShown)
                    .append(notShown == 1 ? " more picture from this document is not shown." : " more"
                            + " pictures from this document are not shown.")
                    .append("</em></p>\n");
        }
        return new Written(page.toString(), names);
    }

    /** What one entry shows of its document's pictures, and the names of the files written for it. */
    private record Written(String html, Set<String> names) {}

    /** A picture the second pass kept, with the one digest taken of its bytes. */
    private record NamedPicture(ListedPicture picture, String digest) {}

    /**
     * One picture of one survivor, as the same-place rule needs to know about it (ADR-150 §3(d)): its digest,
     * its difference hash or {@code null} where its bytes do not decode, and where on its page it sat. Never
     * its pixels, and let go with the survivor it belongs to.
     */
    private record PictureEntry(String digest, DifferenceHash hash, Optional<ListedPicturePlace> place) {}

    /**
     * Rule (c) (ADR-150 §3(c)): the digests of every pair of distinct pictures, anywhere in the tree, that
     * are near-copies of one another; a picture matched makes furniture of the other too, the first
     * included. Compared only within a window of {@value #NEAR_COPY_PIXELS} pixels of width, the pictures
     * with a hash being sorted by width first so that window can be found by breaking out of the inner loop
     * rather than scanning every pair. A picture whose bytes do not decode has no hash and is not compared.
     */
    private static Set<String> nearCopyFurniture(Map<String, DifferenceHash> hashes) {
        Set<String> furniture = new HashSet<>();
        List<Map.Entry<String, DifferenceHash>> distinct = new ArrayList<>();
        for (Map.Entry<String, DifferenceHash> picture : hashes.entrySet()) {
            if (picture.getValue() != null) {
                distinct.add(picture);
            }
        }
        distinct.sort(Comparator.comparingInt(picture -> picture.getValue().width()));
        for (int i = 0; i < distinct.size(); i++) {
            Map.Entry<String, DifferenceHash> a = distinct.get(i);
            for (int j = i + 1; j < distinct.size(); j++) {
                Map.Entry<String, DifferenceHash> b = distinct.get(j);
                if (b.getValue().width() - a.getValue().width() > NEAR_COPY_PIXELS) {
                    break;
                }
                if (!isNearCopy(a.getValue(), b.getValue())) {
                    continue;
                }
                furniture.add(a.getKey());
                furniture.add(b.getKey());
            }
        }
        return furniture;
    }

    /**
     * Rule (d) (ADR-150 §3(d)): the digests of every pair of one survivor's own pictures that repeat at one
     * place in its document. The rule is about where a picture recurs within its own document, not across the
     * tree, so it is decided from the pictures of one occurrence, with a hash and a place each.
     */
    private static Set<String> samePlaceFurniture(List<PictureEntry> ofOneOccurrence) {
        Set<String> furniture = new HashSet<>();
        for (int i = 0; i < ofOneOccurrence.size(); i++) {
            PictureEntry a = ofOneOccurrence.get(i);
            if (a.hash() == null || a.place().isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < ofOneOccurrence.size(); j++) {
                PictureEntry b = ofOneOccurrence.get(j);
                if (b.hash() == null || b.place().isEmpty() || !isSamePlace(a, b)) {
                    continue;
                }
                furniture.add(a.digest());
                furniture.add(b.digest());
            }
        }
        return furniture;
    }

    /**
     * Whether two pictures are a near-copy of one another (ADR-150 §3(c)): their pixel width and height
     * are each within {@value #NEAR_COPY_PIXELS} pixels, and their difference hashes are at most {@value
     * #NEAR_COPY_BITS} bits apart.
     */
    private static boolean isNearCopy(DifferenceHash a, DifferenceHash b) {
        return Math.abs(a.width() - b.width()) <= NEAR_COPY_PIXELS
                && Math.abs(a.height() - b.height()) <= NEAR_COPY_PIXELS
                && a.bitsApartFrom(b) <= NEAR_COPY_BITS;
    }

    /**
     * Whether two pictures of one document repeat at one place (ADR-150 §3(d)): they sit on different
     * pages, each of the four bounding-box edges is within {@value #SAME_PLACE_POINTS} points, and their
     * difference hashes are at most {@value #SAME_PLACE_BITS} bits apart. Both are already known to
     * carry a hash and a place; the caller checks that.
     */
    private static boolean isSamePlace(PictureEntry a, PictureEntry b) {
        return a.place().get().withinPointsOf(b.place().get(), SAME_PLACE_POINTS)
                && a.hash().bitsApartFrom(b.hash()) <= SAME_PLACE_BITS;
    }

    /** The file extension a picture's media type is written under, or empty for a kind never measured. */
    private static Optional<String> fileExtensionFor(String mediaType) {
        if (PNG_MEDIA_TYPE.equals(mediaType)) {
            return Optional.of(".png");
        }
        if (JPEG_MEDIA_TYPE.equals(mediaType)) {
            return Optional.of(".jpg");
        }
        return Optional.empty();
    }

    /** {@code pixels}' SHA-256 digest, lower-case hexadecimal, in full (ADR-149 §1, §3). */
    private static String sha256Hex(byte[] pixels) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
