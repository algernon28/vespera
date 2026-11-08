package io.algernon.vespera.publish.website;

import io.algernon.vespera.engine.deliverable.ClusterPage;
import io.algernon.vespera.engine.deliverable.Copies;
import io.algernon.vespera.engine.deliverable.CopyOutcome;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.Entries;
import io.algernon.vespera.engine.deliverable.EntryPictures;
import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.ListedPicture;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One cluster's membership entries in the website rendering's HTML, written as the HTML tree's page written whole writes
 * its own (ADR-286 §2), so that a survivor's pictures are asked for once for every rendering (ADR-314 §1). The entries
 * are written to {@value #PART_NAME} in the cluster's folder under {@code website/}, each one's end told, and {@link
 * WebsitePage} copies each page's run of them into that page once the HTML pages' split is known (ADR-303); the file is
 * deleted when the cluster's pages are written.
 *
 * <p>An entry opens with the id a citation links to, {@code document-n}, and links its copy, which stands beside the
 * page in the website once a publish has refreshed it (ADR-333 §3); an entry with no copy names its path and says why,
 * as the HTML page does. A picture is inlined as the HTML page inlines it (ADR-286): the copy of an image document, and
 * each picture the HTML page shows, from the bytes it was handed.
 */
final class WebsiteEntries implements Entries {

    /** The entries while the cluster's pages are written, deleted after. */
    static final String PART_NAME = "entries" + Deliverable.PART_SUFFIX;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** One entry whose document the cluster holds: its number and the name its page shows it by. */
    record Named(int number, String name) {}

    private final Path file;
    private final String folderPath;
    private final Path clusterFolder;
    private final Copies copies;
    private final DeliverableLanguage language;
    private final Counting out;
    private final List<Long> ends = new ArrayList<>();
    private final List<List<ListedFile>> files = new ArrayList<>();
    private final List<Named> names = new ArrayList<>();
    private int number;
    private String copyName;

    /**
     * Entries written into {@code folder}, the cluster's folder under {@code website/}, whose path from there is {@code
     * folderPath}; {@code clusterFolder} is its folder in the HTML tree, where its copies stand.
     */
    WebsiteEntries(Path folder, String folderPath, Path clusterFolder, Copies copies, DeliverableLanguage language)
            throws IOException {
        this.file = folder.resolve(PART_NAME);
        this.folderPath = folderPath;
        this.clusterFolder = clusterFolder;
        this.copies = copies;
        this.language = language;
        this.out = new Counting(new BufferedOutputStream(Files.newOutputStream(file), 1 << 16));
    }

    @Override
    public void start(int number, Optional<ListedSurvivor> entry) throws IOException {
        this.number = number;
        this.copyName = null;
        List<ListedFile> ofTheEntry = new ArrayList<>();
        files.add(ofTheEntry);
        StringBuilder text = new StringBuilder("<li id=\"").append(ClusterPage.ANCHOR_PREFIX).append(number).append("\">\n");
        if (entry.isEmpty()) {
            text.append("<em>").append(Html.escape(FixedWords.theClusterNoLongerHoldsIt(language))).append("</em>\n");
            write(text.toString());
            return;
        }
        ListedSurvivor member = entry.get();
        String path = Html.escape(member.path().value());
        names.add(new Named(number, member.path().value()));
        CopyOutcome outcome = copies.outcomeOf(member);
        if (outcome == CopyOutcome.COPIED) {
            copyName = copies.nameOf(member);
            text.append("<a href=\"./").append(Html.escape(ClusterPage.percentEncoded(copyName))).append("\">")
                    .append(path).append("</a>\n");
            ofTheEntry.add(new ListedFile(
                    folderPath + "/" + copyName,
                    "../" + copies.pathOf(member),
                    Files.size(clusterFolder.resolve(copyName)),
                    member.contentHash().toLowerCase(Locale.ROOT),
                    ListedFile.COPY));
        } else {
            text.append(path).append(" <em>").append(Html.escape(outcome.sentence(language))).append("</em>\n");
        }
        write(text.toString());
    }

    /** The entry's copy is a picture the HTML page shows inline, so this page shows it inline too (ADR-286 §6). */
    @Override
    public void copyShown() throws IOException {
        if (copyName == null) {
            return;
        }
        try (InputStream in = Files.newInputStream(clusterFolder.resolve(copyName))) {
            byte[] head = in.readNBytes(PNG_SIGNATURE.length);
            String mediaType = mediaTypeOf(head);
            if (mediaType == null) {
                return;
            }
            write("<p><img src=\"data:" + mediaType + ";base64,");
            try (OutputStream encoder = Base64.getEncoder().wrap(new NonClosing(out))) {
                encoder.write(head);
                in.transferTo(encoder);
            }
            // An empty alt text, as the HTML page gives a copy.
            write("\" alt=\"\"></p>\n");
        }
    }

    /** A picture the HTML page shows, inlined from the bytes it was handed (ADR-286). */
    @Override
    public void picture(String digest, boolean png, ListedPicture picture) throws IOException {
        write("<p><img src=\"data:" + (png ? "image/png" : "image/jpeg") + ";base64,");
        out.write(Base64.getEncoder().encode(picture.pixels()));
        write("\" alt=\"" + Html.escape(picture.caption()) + "\"></p>\n");
    }

    /** The line the HTML page writes for the pictures it does not show. */
    @Override
    public void notShown(int notShown) throws IOException {
        write(EntryPictures.notShownLine(notShown, language));
    }

    /** Closes the entry, and tells where it ends. */
    @Override
    public void end() throws IOException {
        write("</li>\n");
        ends.add(out.count());
    }

    /** The byte the entry at {@code at}, counted from 0, begins at in {@link #file}. */
    long startOf(int at) {
        return at == 0 ? 0 : ends.get(at - 1);
    }

    /** The byte past the end of the entry at {@code at}, counted from 0. */
    long endOf(int at) {
        return ends.get(at);
    }

    /** The files the entry at {@code at}, counted from 0, carries: its copy where one stands. */
    List<ListedFile> filesOf(int at) {
        return files.get(at);
    }

    /** The entries whose document the cluster holds, in membership order. */
    List<Named> names() {
        return names;
    }

    /** Where the entries are, once closed. */
    Path file() {
        return file;
    }

    private void write(String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String mediaTypeOf(byte[] head) {
        if (startsWith(head, PNG_SIGNATURE)) {
            return "image/png";
        }
        return startsWith(head, JPEG_SIGNATURE) ? "image/jpeg" : null;
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    /** Counts the bytes written through it. */
    private static final class Counting extends FilterOutputStream {
        private long count;

        Counting(OutputStream beneath) {
            super(beneath);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            count += length;
        }

        long count() {
            return count;
        }
    }

    /** Passes everything on and leaves the stream beneath it open when closed. */
    private static final class NonClosing extends FilterOutputStream {
        NonClosing(OutputStream beneath) {
            super(beneath);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
