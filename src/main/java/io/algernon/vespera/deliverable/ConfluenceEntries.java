package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * One cluster's membership entries in the Confluence rendering's storage format, written as its HTML page written whole
 * writes its entries (ADR-286 §2), so a survivor's pictures are asked for once for both renderings (ADR-314 §1). The
 * entries are written to {@value #PART_NAME} in the cluster's folder under {@code confluence/}, each one's end told, and
 * {@link ConfluencePage} copies each page's run of them into that page's body once the HTML pages' split is known
 * (ADR-303); the file is deleted when the cluster's pages are written.
 *
 * <p><b>Each entry opens with its anchor macro</b>, {@code document-n} (ADR-309 §1), and links its copy, a file on its
 * page, by {@code ri:attachment} under the copy's name (ADR-313 §1); an entry with no copy names its path and says why,
 * as the HTML page does. An image survivor's copy is shown by its own name and not written again; every other picture is
 * written as the picture's file, through {@code .part}, its bytes the ones the HTML page encodes (ADR-314 §2).
 *
 * <p><b>A picture's file is named {@code picture-<16 hex of its SHA-256>.png} or {@code .jpg}</b>, and where a key of
 * that name (ADR-280 §1.4) is a key of a copy of the cluster it takes {@code ~<entry>} before the extension (ADR-314
 * §3). The copies of the whole cluster are compared, which on a split cluster may give a suffix a name on another page
 * would not have needed; the cost is the suffix alone, as ADR-280 §1.4's over-matching is.
 */
final class ConfluenceEntries implements AutoCloseable {

    /** The entries while the cluster's pages are written, deleted after. */
    static final String PART_NAME = "entries" + Deliverable.PART_SUFFIX;

    /** What a picture's file's name opens with (ADR-314 §3). */
    private static final String PICTURE_STEM = "picture-";

    /** How many hexadecimal characters of a picture's SHA-256 its file's name carries (ADR-314 §3). */
    private static final int PICTURE_DIGEST_CHARACTERS = 16;

    /** Entries for nobody: what the HTML page's further pages and the tests of the HTML tree alone are written with. */
    static final ConfluenceEntries NONE = new ConfluenceEntries();

    private final Path file;
    private final Path folder;
    private final String folderPath;
    private final Path clusterFolder;
    private final Copies copies;
    private final ReplacingMove moves;
    private final DeliverableLanguage language;
    private final Set<String> copyKeys = new HashSet<>();
    private final OutputStream out;
    private final List<Long> ends = new ArrayList<>();
    private final List<List<PageFile>> files = new ArrayList<>();
    private long written;
    private int number;
    private String copyName;

    private ConfluenceEntries() {
        this.file = null;
        this.folder = null;
        this.folderPath = null;
        this.clusterFolder = null;
        this.copies = null;
        this.moves = null;
        this.language = DeliverableLanguage.DEFAULT;
        this.out = null;
    }

    /**
     * Entries written into {@code folder}, the cluster's folder under {@code confluence/}, whose path from there is
     * {@code folderPath}; {@code clusterFolder} is its folder in the HTML tree, where its copies stand.
     */
    ConfluenceEntries(
            Path folder, String folderPath, Path clusterFolder, Copies copies, List<ListedSurvivor> members,
            ReplacingMove moves,
            DeliverableLanguage language)
            throws IOException {
        this.file = folder.resolve(PART_NAME);
        this.language = language;
        this.folder = folder;
        this.folderPath = folderPath;
        this.clusterFolder = clusterFolder;
        this.copies = copies;
        this.moves = moves;
        for (ListedSurvivor member : members) {
            if (copies.outcomeOf(member) == CopyOutcome.COPIED) {
                copyKeys.addAll(CopyNames.keysOf(copies.nameOf(member)));
            }
        }
        this.out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16);
    }

    /** Opens entry {@code number}, counted from 1: its anchor, and its copy's link or its path and why none stands. */
    void start(int number, Optional<ListedSurvivor> entry) throws IOException {
        if (out == null) {
            return;
        }
        this.number = number;
        this.copyName = null;
        List<PageFile> ofTheEntry = new ArrayList<>();
        files.add(ofTheEntry);
        // The parameter's name is the empty string between these two appends (ADR-309 §1).
        StringBuilder text = new StringBuilder("<li><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"")
                .append("\">")
                .append(ClusterPage.ANCHOR_PREFIX).append(number).append("</ac:parameter></ac:structured-macro>\n");
        if (entry.isEmpty()) {
            text.append("<em>").append(Html.escape(FixedWords.theClusterNoLongerHoldsIt(language))).append("</em>\n");
            write(text.toString());
            return;
        }
        ListedSurvivor member = entry.get();
        String path = Html.escape(member.path().value());
        CopyOutcome outcome = copies.outcomeOf(member);
        if (outcome == CopyOutcome.COPIED) {
            copyName = copies.nameOf(member);
            text.append("<ac:link><ri:attachment ri:filename=\"").append(Html.escape(copyName))
                    .append("\" /><ac:link-body>").append(path).append("</ac:link-body></ac:link>\n");
            ofTheEntry.add(new PageFile(copyName, "../" + copies.pathOf(member), PageFile.COPY,
                    Files.size(clusterFolder.resolve(copyName)), member.contentHash().toLowerCase(Locale.ROOT)));
        } else {
            text.append(path).append(" <em>").append(Html.escape(outcome.sentence(language))).append("</em>\n");
        }
        write(text.toString());
    }

    /** The entry's copy is a picture the HTML page shows inline, so this page shows the copy by its name (ADR-314 §2). */
    void copyShown() throws IOException {
        if (out == null || copyName == null) {
            return;
        }
        // An empty alt text, as the HTML page gives a copy: the empty string between these two writes.
        write("<p><ac:image ac:alt=\"");
        write("\"><ri:attachment ri:filename=\"" + Html.escape(copyName) + "\" /></ac:image></p>\n");
    }

    /** A picture the HTML page shows, written as the picture's file and shown by its name (ADR-314 §2, §3). */
    void picture(String digest, boolean png, ListedPicture picture) throws IOException {
        if (out == null) {
            return;
        }
        String stem = PICTURE_STEM + digest.substring(0, PICTURE_DIGEST_CHARACTERS);
        String extension = png ? ".png" : ".jpg";
        String name = stem + extension;
        if (CopyNames.keysOf(name).stream().anyMatch(copyKeys::contains)) {
            name = stem + "~" + number + extension;
        }
        Path part = folder.resolve(name + Deliverable.PART_SUFFIX);
        Files.write(part, picture.pixels());
        moves.intoPlace(part, folder.resolve(name));
        files.getLast().add(new PageFile(name, folderPath + "/" + name, PageFile.PICTURE, picture.pixels().length, digest));
        write("<p><ac:image ac:alt=\"" + Html.escape(picture.caption()) + "\"><ri:attachment ri:filename=\""
                + Html.escape(name) + "\" /></ac:image></p>\n");
    }

    /** The line the HTML page writes for the pictures it does not show. */
    void notShown(int notShown) throws IOException {
        if (out != null) {
            write(EntryPictures.notShownLine(notShown, language));
        }
    }

    /** Closes the entry, and tells where it ends. */
    void end() throws IOException {
        if (out == null) {
            return;
        }
        write("</li>\n");
        ends.add(written);
    }

    /** The byte the entry at {@code at}, counted from 0, begins at in {@link #file}. */
    long startOf(int at) {
        return at == 0 ? 0 : ends.get(at - 1);
    }

    /** The byte past the end of the entry at {@code at}, counted from 0. */
    long endOf(int at) {
        return ends.get(at);
    }

    /** The files the entry at {@code at} carries, counted from 0: its copy where one stands, then its pictures' files. */
    List<PageFile> filesOf(int at) {
        return files.get(at);
    }

    /** Where the entries are, once closed. */
    Path file() {
        return file;
    }

    private void write(String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write(bytes);
        written += bytes.length;
    }

    @Override
    public void close() throws IOException {
        if (out != null) {
            out.close();
        }
    }
}
