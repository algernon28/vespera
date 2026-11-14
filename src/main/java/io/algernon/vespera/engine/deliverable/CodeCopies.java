package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The code sets of the tree (ADR-296): each set copied whole under {@value #DIRECTORY_NAME}{@code /<set>/}, its paths
 * kept, each member checked against the size and last-modified time the walk recorded and not against a digest, which
 * code has none of (ADR-277); {@code code.csv}, one row for each member; and the section of the index, one row for each
 * set.
 *
 * <p><b>The outcomes are ADR-274 §2.3's</b>: none fails the step, throws or writes a row, and a file at a copy's name is
 * taken as copied without reading it or the original (§2.4). The original is opened for reading alone (§2.6), after its
 * size and time are read and found as recorded, and its size and time are read again when the stream ends.
 *
 * <p><b>What is held is a drawn name for each set, and one set's members and kept paths while that set is copied</b>
 * (ADR-296 §3.4, ADR-220), and, from the earlier listing, one folder in {@code code/} for each set it holds (ADR-377
 * §9). This class writes no line; it reports numbers through {@link DeliverableProgress}, and hands the sets whose
 * folder in {@code code/} changed to {@link Deliverable}, which tells them once the listing is in place.
 */
final class CodeCopies {

    /** Where the code sets lie, directly beneath the tree's root (ADR-296 §1). */
    static final String DIRECTORY_NAME = "code";

    /** The listing of the members, at the tree's root (ADR-296 §5). */
    static final String LISTING_FILE_NAME = "code.csv";

    /** The listing's columns, in order. A machine-read header, naming columns as the ledger names them (ADR-122). */
    private static final String HEADER =
            "occurrence_id,path,code_set,role,generator,size_bytes,last_modified,copy,copy_outcome";

    /** The header's columns, by which an earlier listing's columns are found. */
    private static final List<String> COLUMNS = List.of(HEADER.split(","));

    /** Where {@link #HEADER} names the set's folder in the archive, and the member's copy, counting from 0. */
    private static final int SET_COLUMN = 2;

    private static final int COPY_COLUMN = 7;

    private static final int BUFFER_BYTES = 1 << 16;

    private final Path tree;
    private final String corpusRoot;
    private final ReplacingMove moves;
    private final LongSupplier usableSpace;
    private final DeliverableProgress progress;
    private long atALongPath;
    private List<FolderChange> foldersChanged = List.of();

    CodeCopies(
            Path tree,
            String corpusRoot,
            ReplacingMove moves,
            LongSupplier usableSpace,
            DeliverableProgress progress) {
        this.tree = tree;
        this.corpusRoot = corpusRoot;
        this.moves = moves;
        this.usableSpace = usableSpace;
        this.progress = progress;
    }

    /** How many code copies stand, made or kept, at an absolute path longer than {@value Copies#LONGEST_WINDOWS_PATH}. */
    long copiesAtALongPath() {
        return atALongPath;
    }

    /**
     * The sets of this drawing whose folder in {@code code/} is not the one the tree's earlier {@code code.csv} gave
     * them, each as its earlier folder and its folder now, in the order the sets are drawn (ADR-377 §9); none before
     * {@link #draw} or where it found no change. The caller tells them once the listing is in place.
     */
    List<FolderChange> foldersChanged() {
        return foldersChanged;
    }

    /** A set's folder in {@code code/} as the earlier listing gave it, and as this drawing gives it. */
    record FolderChange(String earlier, String now) {}

    /**
     * Copies every member of every set, writes {@code code.csv} beside its target and the index's section of code as
     * each set ends, and returns where {@code code.csv} is; none where the run has no code set, which writes nothing.
     * Before it writes the listing it reads the one the tree holds from the drawing before, where there is one, for
     * {@link #foldersChanged}.
     *
     * @param written the folders of the tree this drawing wrote, to which {@code code} and each set's folder are added
     */
    Path draw(ListedCodeSets code, Appendable index, Set<String> written, DeliverableLanguage language)
            throws IOException {
        List<ListedCodeSet> sets = code.sets();
        if (sets.isEmpty()) {
            return null;
        }
        Map<String, String> folders = CodeSetFolderNames.of(sets);
        foldersChanged = changesFrom(earlierFolders(tree.resolve(LISTING_FILE_NAME)), sets, folders);

        long files = 0;
        long bytes = 0;
        for (ListedCodeSet set : sets) {
            for (ListedCodeFile member : code.membersOf(set)) {
                files++;
                bytes += member.recordedSizeBytes();
            }
        }
        progress.toCopyCodeFiles(sets.size(), files, bytes, usableSpace.getAsLong());

        Path root = tree.resolve(DIRECTORY_NAME);
        Files.createDirectories(root);
        written.add(DIRECTORY_NAME);
        IndexPage.codeHeading(index, language);
        Path listingPart = tree.resolve(LISTING_FILE_NAME + Deliverable.PART_SUFFIX);
        try (Writer listing = Files.newBufferedWriter(listingPart, StandardCharsets.UTF_8)) {
            listing.append(HEADER).append('\n');
            for (ListedCodeSet set : sets) {
                String folderName = folders.get(set.folder());
                written.add(DIRECTORY_NAME + "/" + folderName);
                long[] standing = copySet(code.membersOf(set), root.resolve(folderName), folderName, listing);
                IndexPage.codeRow(index, folderName, set.folder(), standing[0], standing[1], language);
            }
        }
        IndexPage.codeEnd(index);
        return listingPart;
    }

    /**
     * Copies the members of one set into {@code setFolder}, appends their rows to {@code listing}, and returns how many
     * copies stand and how many do not.
     */
    private long[] copySet(
            List<ListedCodeFile> members, Path setFolder, String folderName, Writer listing) throws IOException {
        List<ListedCodeFile> ascending = new ArrayList<>(members);
        ascending.sort(Comparator.comparingLong(member -> member.occurrence().value()));
        Map<OccurrenceId, String> paths = CodePaths.of(ascending);
        long[] counts = new long[2];
        for (ListedCodeFile member : ascending) {
            String kept = paths.get(member.occurrence());
            CopyOutcome outcome = copyOne(member, setFolder, kept);
            counts[outcome == CopyOutcome.COPIED ? 0 : 1]++;
            listing.append(String.valueOf(member.occurrence().value()))
                    .append(',')
                    .append(ManifestCsv.cell(member.path().value()))
                    .append(',')
                    .append(ManifestCsv.cell(member.setFolder()))
                    .append(',')
                    .append(ManifestCsv.cell(member.role()))
                    .append(',')
                    .append(member.generator() == null ? "" : ManifestCsv.cell(member.generator()))
                    .append(',')
                    .append(String.valueOf(member.recordedSizeBytes()))
                    .append(',')
                    .append(member.recordedLastModified().toString())
                    .append(',')
                    .append(outcome == CopyOutcome.COPIED
                            ? ManifestCsv.cell(DIRECTORY_NAME + "/" + folderName + "/" + kept)
                            : "")
                    .append(',')
                    .append(outcome.word())
                    .append('\n');
        }
        return counts;
    }

    private CopyOutcome copyOne(ListedCodeFile member, Path setFolder, String kept) {
        Path copy = null;
        try {
            String name = kept.substring(kept.lastIndexOf('/') + 1);
            if ((name + Deliverable.PART_SUFFIX).getBytes(StandardCharsets.UTF_8).length
                    > CopyNames.LONGEST_NAME_IN_BYTES) {
                // A name that with .part holds more than 255 UTF-8 bytes is not made (ADR-280 §1.6).
                progress.codeFileNotCopied();
                return CopyOutcome.NOT_COPIED;
            }
            copy = setFolder.resolve(kept);
            if (Files.isRegularFile(copy)) {
                progress.codeFileAlreadyCopied();
                countIfLong(copy);
                return CopyOutcome.COPIED;
            }
            CopyOutcome outcome = copy(member, copy);
            if (outcome == CopyOutcome.COPIED) {
                countIfLong(copy);
            }
            return outcome;
        } catch (RuntimeException refused) {
            // A name the platform's path parser refuses, an InvalidPathException among them, is that file's own
            // failure and not the run's (ADR-274 §2.3).
            progress.codeFileNotCopied();
            return CopyOutcome.NOT_COPIED;
        }
    }

    private void countIfLong(Path copy) {
        if (copy.toAbsolutePath().normalize().toString().length() > Copies.LONGEST_WINDOWS_PATH) {
            atALongPath++;
        }
    }

    private CopyOutcome copy(ListedCodeFile member, Path copy) {
        Path part = copy.resolveSibling(copy.getFileName() + Deliverable.PART_SUFFIX);
        try {
            Path original = Path.of(corpusRoot).resolve(member.path().value());
            long size = Files.size(original);
            if (!asRecorded(original, size, member)) {
                progress.codeFileChanged();
                return CopyOutcome.CHANGED;
            }
            if (usableSpace.getAsLong() - size < Deliverable.COPY_HEADROOM_BYTES) {
                progress.codeFileWithNoRoom();
                return CopyOutcome.NO_ROOM;
            }
            Files.createDirectories(copy.getParent());
            long bytes = 0;
            try (InputStream in = Files.newInputStream(original, StandardOpenOption.READ);
                    OutputStream out = Files.newOutputStream(
                            part,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    out.write(buffer, 0, read);
                    bytes += read;
                }
            }
            if (bytes != member.recordedSizeBytes() || !asRecorded(original, Files.size(original), member)) {
                Files.deleteIfExists(part);
                progress.codeFileChanged();
                return CopyOutcome.CHANGED;
            }
            moves.intoPlace(part, copy);
            progress.codeFileCopied(bytes);
            return CopyOutcome.COPIED;
        } catch (NoSuchFileException | NotDirectoryException gone) {
            leaveNoPart(part);
            progress.codeFileGone();
            return CopyOutcome.GONE;
        } catch (IOException | RuntimeException other) {
            // Deliberately every failure, a path the parser refuses among them: a bad copy is that file's, not the
            // run's, and an exception out of the tree would roll back every fault row (ADR-274 §2.3).
            leaveNoPart(part);
            progress.codeFileNotCopied();
            return CopyOutcome.NOT_COPIED;
        }
    }

    /** Whether {@code original} has the size and last-modified time the walk recorded, the time read as the census wrote it. */
    private static boolean asRecorded(Path original, long size, ListedCodeFile member) throws IOException {
        Instant time = Files.getLastModifiedTime(original).toInstant();
        return size == member.recordedSizeBytes() && time.equals(member.recordedLastModified());
    }

    /**
     * The changes of folder in {@code code/}: each set of {@code sets}, in their order, whose earlier folder shares no
     * key of ADR-280 §1.4 with the one {@code folders} gives it. A change of case alone shares a key and is no change.
     */
    private static List<FolderChange> changesFrom(
            Map<String, String> earlier, List<ListedCodeSet> sets, Map<String, String> folders) {
        List<FolderChange> changes = new ArrayList<>();
        for (ListedCodeSet set : sets) {
            String was = earlier.get(ManifestCsv.shownAsText(set.folder()));
            String now = folders.get(set.folder());
            if (was != null && CopyNames.keysOf(was).stream().noneMatch(CopyNames.keysOf(now)::contains)) {
                changes.add(new FolderChange(was, now));
            }
        }
        return List.copyOf(changes);
    }

    /**
     * The earlier listing's folder in {@code code/} for each set folder of its {@code code_set} column, as it spells
     * the cell: the folder of the first {@code copy} cell of that set's rows that is not empty (ADR-377 §9). None where
     * there is no listing, or it cannot be read, a quoted cell cut short among the ways, or its header names neither
     * column. One string pair a set is held, and no row; the rows decide no name and key nothing.
     */
    private static Map<String, String> earlierFolders(Path listing) {
        if (!Files.isRegularFile(listing)) {
            return Map.of();
        }
        Map<String, String> folders = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(listing, StandardCharsets.UTF_8)) {
            List<String> header = recordOf(in);
            if (header == null) {
                return Map.of();
            }
            int setColumn = header.indexOf(COLUMNS.get(SET_COLUMN));
            int copyColumn = header.indexOf(COLUMNS.get(COPY_COLUMN));
            if (setColumn < 0 || copyColumn < 0) {
                return Map.of();
            }
            for (List<String> row = recordOf(in); row != null; row = recordOf(in)) {
                if (row.size() <= Math.max(setColumn, copyColumn) || folders.containsKey(row.get(setColumn))) {
                    continue;
                }
                String copy = row.get(copyColumn);
                String prefix = DIRECTORY_NAME + "/";
                int end = copy.indexOf('/', prefix.length());
                if (copy.startsWith(prefix) && end > prefix.length()) {
                    folders.put(row.get(setColumn), copy.substring(prefix.length(), end));
                }
            }
            return folders;
        } catch (IOException | RuntimeException unreadable) {
            // A listing that cannot be read tells nothing, and the drawing goes on (ADR-377 §9).
            return Map.of();
        }
    }

    /**
     * The next record of an RFC 4180 listing as {@link ManifestCsv#quoted} writes it, its cells unquoted; {@code null}
     * at the end of the text. A blank line is no record.
     *
     * @throws IOException where a quoted cell is not closed before the text ends
     */
    private static List<String> recordOf(BufferedReader in) throws IOException {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int c = in.read(); ; c = in.read()) {
            if (quoted) {
                if (c < 0) {
                    throw new IOException("a quoted cell is not closed");
                }
                if (c == '"') {
                    in.mark(1);
                    if (in.read() == '"') {
                        cell.append('"');
                    } else {
                        in.reset();
                        quoted = false;
                    }
                } else {
                    cell.append((char) c);
                }
                continue;
            }
            if (c < 0 || c == '\n') {
                if (!any && cells.isEmpty() && cell.isEmpty()) {
                    if (c < 0) {
                        return null;
                    }
                    continue;
                }
                cells.add(cell.toString());
                return cells;
            }
            any = true;
            if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c != '\r') {
                cell.append((char) c);
            }
        }
    }

    private static void leaveNoPart(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException | RuntimeException stays) {
            // A part that cannot be removed is written over by the next attempt (ADR-274 §2.4).
        }
    }
}
