package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.DeliverableLanguage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
 * The pictures a document carries under its membership entry (ADR-149, ADR-150, ADR-286, #285): which pictures
 * across the whole tree are furniture, and what is shown for the rest, inline.
 *
 * <p><b>Two passes that never hold more than one document's pixels at a time (ADR-149 §9).</b> {@link
 * #among} is the first: every listed survivor's pictures are asked for once, and only the digest and difference
 * hash of each distinct picture are kept, from which the set of furniture digests is decided under
 * ADR-149 §1(a)/(b) and ADR-150 §3(c)/(d) (ADR-223 §7). {@link #writeUnder} is the second, one entry at a time: the
 * source is asked again, and what passes the furniture rule is written into the page's stream (ADR-286 §2).
 *
 * <p><b>One SHA-256 per picture per pass</b> (ADR-213 §5). The second pass digests each picture once, for the
 * furniture check; the first pass's digests are not carried into it, the second answer being a new answer.
 */
final class EntryPictures {

    /** The media type a PNG's bytes are shown under (ADR-286 §1). */
    private static final String PNG_MEDIA_TYPE = "image/png";

    /** The media type a JPEG's bytes are shown under (ADR-286 §1). */
    private static final String JPEG_MEDIA_TYPE = "image/jpeg";

    /** The eight bytes a PNG begins with. */
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /** The three bytes a JPEG begins with. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** How many bytes of a copy are read at a time to be encoded. */
    private static final int COPY_BUFFER_BYTES = 1 << 15;

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

    private final DeliverableLanguage language;

    private EntryPictures(SurvivorPictures source, Set<String> furniture, DeliverableLanguage language) {
        this.source = source;
        this.furniture = furniture;
        this.language = language;
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
        return new EntryPictures(pictures, Set.copyOf(furniture), source.language());
    }

    /** The digests, in full lower-case hexadecimal, of every picture judged furniture across the tree. */
    Set<String> furniture() {
        return furniture;
    }

    /**
     * What a survivor's membership entry shows under its link, written to {@code page} (ADR-286 §1, §2, §6): its
     * copy where {@code copy} names one that stands and begins with the PNG or the JPEG signature, then the first
     * {@link Deliverable#PICTURES_PER_DOCUMENT} of its pictures that pass the furniture rule, each shown by an
     * {@code <img>} whose {@code src} is a {@code data:} URI, then, where any were left uncounted or not of the kind
     * they declare, one line saying how many. Nothing is written into any folder.
     *
     * <p><b>The source is asked once, here</b>, while the survivor's own entry is written (ADR-149 §9), and what is
     * held is what it hands for that survivor. Each payload is encoded straight from its bytes into {@code page}, a
     * copy a buffer at a time, and no string of either is made.
     *
     * <p><b>The budget is positions, not pictures shown.</b> The first {@link Deliverable#PICTURES_PER_DOCUMENT}
     * non-furniture pictures in reading order are the ones this entry may show; among those, one whose bytes do not
     * begin with the signature of the media type it declares, or whose type is neither PNG nor JPEG, holds its
     * position and is counted as not shown (ADR-149 §6, ADR-286 §1). A copy is no picture: outside the budget and
     * the count (ADR-286 §6).
     *
     * <p><b>The alt text is the caption under the one escaping rule</b> (ADR-149 §5, ADR-274 §1.3); a copy's is empty.
     *
     * @param copy the survivor's copy in its cluster's folder where one stands, otherwise empty
     */
    void writeUnder(OutputStream page, ListedSurvivor member, Optional<Path> copy) throws IOException {
        writeUnder(page, member, copy, ConfluenceEntries.NONE);
    }

    /**
     * As {@link #writeUnder(OutputStream, ListedSurvivor, Optional)}, and the same entry's pictures handed to {@code
     * also}, the Confluence rendering's (ADR-314 §1): the same pictures from the same bytes, in the same order, with the
     * same count of those not shown, from the one answer the source gives here, so no document is asked a third time.
     */
    void writeUnder(OutputStream page, ListedSurvivor member, Optional<Path> copy, ConfluenceEntries also)
            throws IOException {
        if (copy.isPresent() && writeCopyInline(page, copy.get())) {
            also.copyShown();
        }
        Shown shown = shownOf(member);
        for (NamedPicture candidate : shown.pictures()) {
            startImage(page, candidate.mediaType());
            encodeInto(page, candidate.picture().pixels());
            text(page, "\" alt=\"" + Html.escape(candidate.picture().caption()) + "\"></p>\n");
            also.picture(candidate.digest(), candidate.mediaType().equals(PNG_MEDIA_TYPE), candidate.picture());
        }
        if (shown.notShown() > 0) {
            text(page, notShownLine(shown.notShown(), language));
            also.notShown(shown.notShown());
        }
    }

    /**
     * The pictures an entry shows and how many it does not (ADR-149 §6, ADR-286 §1): the first {@link
     * Deliverable#PICTURES_PER_DOCUMENT} that pass the furniture rule, less those whose bytes do not carry the
     * signature of the type they declare, which are counted with the ones past the budget. The source is asked here.
     */
    private Shown shownOf(ListedSurvivor member) {
        List<NamedPicture> kept = new ArrayList<>();
        for (ListedPicture picture : source.of(member)) {
            String digest = sha256Hex(picture.pixels());
            if (!furniture.contains(digest)) {
                kept.add(new NamedPicture(picture, digest, null));
            }
        }
        int withinBudget = Math.min(Deliverable.PICTURES_PER_DOCUMENT, kept.size());
        int notShown = kept.size() - withinBudget;
        List<NamedPicture> shown = new ArrayList<>();
        for (NamedPicture candidate : kept.subList(0, withinBudget)) {
            Optional<String> mediaType = declaredAndCarried(candidate.picture().mediaType(), candidate.picture().pixels());
            if (mediaType.isEmpty()) {
                notShown++;
            } else {
                shown.add(new NamedPicture(candidate.picture(), candidate.digest(), mediaType.get()));
            }
        }
        return new Shown(List.copyOf(shown), notShown);
    }

    /**
     * The line saying how many of a document's pictures are not shown, {@code notShown} being one or more, in {@code
     * language} (ADR-321 §4).
     */
    static String notShownLine(int notShown, DeliverableLanguage language) {
        String sentence = switch (language) {
            case ENGLISH -> notShown == 1
                    ? " more picture from this document is not shown."
                    : " more pictures from this document are not shown.";
            case ITALIAN -> notShown == 1
                    ? " altra immagine di questo documento non è mostrata."
                    : " altre immagini di questo documento non sono mostrate.";
        };
        return "<p><em>" + notShown + sentence + "</em></p>\n";
    }

    /**
     * Shows {@code copy} if its first bytes are a PNG's or a JPEG's signature, and writes nothing if not, or if the
     * copy cannot be opened or its first bytes read (ADR-286 §6); those bytes are read before any tag is begun. Answers
     * whether it was shown.
     */
    private static boolean writeCopyInline(OutputStream page, Path copy) throws IOException {
        InputStream opened = null;
        byte[] head;
        try {
            opened = Files.newInputStream(copy);
            head = opened.readNBytes(PNG_SIGNATURE.length);
        } catch (IOException unreadable) {
            if (opened != null) {
                opened.close();
            }
            return false;
        }
        try (InputStream in = opened) {
            String mediaType = mediaTypeOfSignature(head);
            if (mediaType == null) {
                return false;
            }
            startImage(page, mediaType);
            try (OutputStream encoder = Base64.getEncoder().wrap(new NonClosing(page))) {
                encoder.write(head);
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    encoder.write(buffer, 0, read);
                }
            }
            // An empty alt text: its value is the empty string between these two writes (ADR-286 §6).
            text(page, "\" alt=\"");
            text(page, "\"></p>\n");
            return true;
        }
    }

    private static void startImage(OutputStream page, String mediaType) throws IOException {
        text(page, "<p><img src=\"data:" + mediaType + ";base64,");
    }

    private static void encodeInto(OutputStream page, byte[] bytes) throws IOException {
        try (OutputStream encoder = Base64.getEncoder().wrap(new NonClosing(page))) {
            encoder.write(bytes);
        }
    }

    private static void text(OutputStream page, String text) throws IOException {
        page.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** The media type {@code declared} where {@code bytes} begin with that type's signature, otherwise empty. */
    private static Optional<String> declaredAndCarried(String declared, byte[] bytes) {
        String carried = mediaTypeOfSignature(bytes);
        return carried != null && carried.equals(declared) ? Optional.of(declared) : Optional.empty();
    }

    /** The media type whose signature {@code bytes} begin with (ADR-286 §1), or {@code null} for neither. */
    private static String mediaTypeOfSignature(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return PNG_MEDIA_TYPE;
        }
        return startsWith(bytes, JPEG_SIGNATURE) ? JPEG_MEDIA_TYPE : null;
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
    }

    /** A stream that passes everything on and leaves the stream beneath it open when it is closed. */
    private static final class NonClosing extends OutputStream {
        private final OutputStream beneath;

        NonClosing(OutputStream beneath) {
            this.beneath = beneath;
        }

        @Override
        public void write(int b) throws IOException {
            beneath.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            beneath.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            beneath.flush();
        }
    }

    /**
     * A picture the second pass kept, with the one digest taken of its bytes and, once its signature is checked, the
     * media type it is shown under.
     */
    private record NamedPicture(ListedPicture picture, String digest, String mediaType) {}

    /** The pictures an entry shows, and how many it does not. */
    private record Shown(List<NamedPicture> pictures, int notShown) {}

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

    /** {@code pixels}' SHA-256 digest, lower-case hexadecimal, in full (ADR-149 §1, §3). */
    private static String sha256Hex(byte[] pixels) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
