package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.ContentHash;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Both SHA-256 methods read a file through a buffer of fixed size, so no file is too large to hash
 * (ADR-207): {@code corpus}'s {@link ContentHash}, which stage 1 uses, and {@code extraction}'s
 * {@link ContentHashing}, which stage 2 and seed extraction use for the extraction cache key. Each used
 * to gather every byte into one array while it fed the digest, so a file over about half the heap, or
 * over 2 GB at any heap, ended in {@code OutOfMemoryError}.
 *
 * <p>In this package because {@link ContentHashing} is package-private; {@link ContentHash} is public,
 * so one class holds both to the same claims, as {@link ContentHashingTest} already holds them to each
 * other.
 *
 * <p><b>How the bound is shown, twice over and without relying on this JVM's heap.</b> A file longer
 * than {@link Integer#MAX_VALUE} bytes cannot be held in one Java array at any heap, so hashing one at
 * all is proof by construction that no method gathers the file. And what the calling thread allocates
 * while it hashes is read from the JVM, exactly, at two sizes sixteenfold apart: it stays under one
 * bound at both, where a method that held the file would allocate at least the file's own length.
 *
 * <p><b>The fixtures are sparse files</b>: a few bytes are written and the rest is a hole the file system
 * reads back as zeroes, so the 2 GiB file costs a few kilobytes of disk on NTFS, ext4, APFS and tmpfs.
 * The bytes written sit where a wrong read would show: at the start, across the end of a 64 KiB buffer,
 * across the 2 GiB mark and at the very end. Where the file system cannot make such a file the test is
 * aborted by assumption, with the reason, and is reported as skipped. The expected value of every large
 * file is taken here by a reader that shares nothing with either method under test: a
 * {@link FileChannel} read through a 1 MiB {@link ByteBuffer}.
 */
@Epic("Extraction")
@Feature("Hashing content that arrived unhashed")
@Issue("449")
@Link(name = "ADR-207", url = Adr.A_FILE_IS_HASHED_THROUGH_A_FIXED_BUFFER, type = "adr")
class AFileOfAnySizeIsHashedTest {

    /**
     * A way of taking a file's SHA-256, so each claim is made of both shipped methods. Each is named by a
     * phrase a claim reads on from: "... by the method the duplicate check uses ...".
     */
    @FunctionalInterface
    private interface Hasher {
        String sha256(Path file) throws IOException;
    }

    /** The two shipped methods, by the part of the system that uses each. */
    private static final Map<String, Hasher> BOTH_METHODS = bothMethods();

    private static Map<String, Hasher> bothMethods() {
        Map<String, Hasher> methods = new LinkedHashMap<>();
        methods.put("the method the duplicate check uses", ContentHash::sha256);
        methods.put("the method conversion and seed reading use", ContentHashing::sha256);
        return methods;
    }

    /** 1 MiB, the unit the sizes below are stated in. */
    private static final long MIB = 1L << 20;

    /** The length of the buffer the record fixes for both methods, and the boundary the fixtures straddle. */
    private static final int BUFFER_BYTES = 64 * 1024;

    /** The first length no single Java array can hold: one more than the largest array index space. */
    private static final long TWO_GIB = 1L << 31;

    /**
     * 2 GiB and 1 MiB more: longer than any Java array, with a megabyte beyond the mark so the read that
     * crosses it is not also the last.
     */
    private static final long LONGER_THAN_ANY_ARRAY = TWO_GIB + MIB;

    /** The smaller of the two files whose hashing is measured for what it allocates. */
    private static final long SIXTEEN_MIB = 16 * MIB;

    /** The larger: sixteen times the smaller, so an allocation that grew with the file would show. */
    private static final long TWO_HUNDRED_FIFTY_SIX_MIB = 256 * MIB;

    /**
     * What one hash may allocate, in bytes: 1 MiB, sixteen times the 64 KiB buffer, to leave room for the
     * digest, the hex text and the stream. It is a sixteenth of the smaller measured file and a 256th of
     * the larger. Measured before the change, gathering the file allocated twice the file's length.
     */
    private static final long ALLOCATION_BOUND_BYTES = MIB;

    /**
     * File systems with no sparse files, by the name the JDK reports: a 2 GiB fixture there would really
     * be written, so the test is aborted instead.
     */
    private static final Set<String> STORES_WITHOUT_SPARSE_FILES = Set.of("fat", "fat12", "fat16", "fat32", "vfat", "msdos", "exfat");

    /** The published SHA-256 of no bytes at all, as lowercase hex. */
    private static final String DIGEST_OF_NOTHING = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** The published SHA-256 of the three ASCII letters {@code abc}, as lowercase hex. */
    private static final String DIGEST_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /**
     * Lengths on each side of one buffer and of two, and one that ends partway through a buffer: where a
     * read in parts could drop, repeat or pad a byte. Zero and one are the smallest files there are.
     */
    private static final List<Integer> LENGTHS_AROUND_A_BUFFER = List.of(
            0, 1, BUFFER_BYTES - 1, BUFFER_BYTES, BUFFER_BYTES + 1, 2 * BUFFER_BYTES, 2 * BUFFER_BYTES + 1, 1_048_579);

    /** The seed of the bytes written into the small files, fixed so a failure repeats. */
    private static final long FIXTURE_SEED = 449L;

    @Test
    @Story("A file too large to hold in memory is hashed")
    @DisplayName("A file longer than any array can be is hashed, to the value an independent reader gives")
    void hashesAFileLongerThanAnyArray(@TempDir Path dir) throws IOException {
        Path file = sparseFile(dir.resolve("longer-than-any-array.bin"), LONGER_THAN_ANY_ARRAY);
        String expected = sha256ByChannel(file);

        for (Map.Entry<String, Hasher> method : BOTH_METHODS.entrySet()) {
            Outcome outcome = hashing(file, method.getValue());
            claim(
                    "hashing a file of " + LONGER_THAN_ANY_ARRAY + " bytes, which is 2 GiB and 1 MiB more, ends"
                            + " without an error by " + method.getKey() + ": no array can be"
                            + " longer than " + Integer.MAX_VALUE + " bytes, so a method that gathered the"
                            + " file's bytes while hashing them could not finish at any memory setting",
                    () -> assertThat(outcome.thrown())
                            .as("what hashing the file threw")
                            .isNull());
            claim(
                    "and the value is the one a separate reader gives for the same file, reading it"
                            + " 1 MiB at a time through another interface: every byte was read once, in"
                            + " order, across the 2 GiB mark and to the last byte",
                    () -> assertThat(outcome.sha256()).isEqualTo(expected));
        }
    }

    @Test
    @Story("A file too large to hold in memory is hashed")
    @DisplayName("Hashing a file sixteen times larger allocates no more memory")
    void allocatesNoMoreForALargerFile(@TempDir Path dir) throws IOException {
        com.sun.management.ThreadMXBean threads = allocationCounter();
        Path smaller = sparseFile(dir.resolve("sixteen-mib.bin"), SIXTEEN_MIB);
        Path larger = sparseFile(dir.resolve("two-hundred-fifty-six-mib.bin"), TWO_HUNDRED_FIFTY_SIX_MIB);
        Path warmUp = Files.write(dir.resolve("warm-up.bin"), new byte[BUFFER_BYTES + 1]);
        String expectedOfTheLarger = sha256ByChannel(larger);

        for (Map.Entry<String, Hasher> method : BOTH_METHODS.entrySet()) {
            // Hashed once first and not measured: the first call loads classes and the digest's provider,
            // and that is allocated once, whatever the file.
            hashing(warmUp, method.getValue());
            Measured small = measured(threads, smaller, method.getValue());
            Measured large = measured(threads, larger, method.getValue());

            claim(
                    "hashing a file of " + SIXTEEN_MIB + " bytes (16 MiB) by " + method.getKey()
                            + " allocates under " + ALLOCATION_BOUND_BYTES + " bytes (1 MiB, sixteen times"
                            + " the 64 KiB the file is read through), counted by the JVM for the thread"
                            + " that hashed: a method that held the file would allocate at least 16 MiB",
                    () -> {
                        assertThat(small.outcome().thrown()).as("what hashing the file threw").isNull();
                        assertThat(small.allocatedBytes())
                                .as("bytes allocated while hashing 16 MiB")
                                .isLessThan(ALLOCATION_BOUND_BYTES);
                    });
            claim(
                    "hashing a file of " + TWO_HUNDRED_FIFTY_SIX_MIB + " bytes (256 MiB), sixteen times the"
                            + " first, stays under the same " + ALLOCATION_BOUND_BYTES + " bytes: what is"
                            + " allocated does not grow with the file",
                    () -> {
                        assertThat(large.outcome().thrown()).as("what hashing the file threw").isNull();
                        assertThat(large.allocatedBytes())
                                .as("bytes allocated while hashing 256 MiB")
                                .isLessThan(ALLOCATION_BOUND_BYTES);
                    });
            claim(
                    "and the larger file's value is the one a separate reader gives, so the memory was"
                            + " not saved by reading less of the file",
                    () -> assertThat(large.outcome().sha256()).isEqualTo(expectedOfTheLarger));
        }
    }

    @Test
    @Story("Reading a file in parts changes no hash")
    @DisplayName("Files on each side of a buffer's length hash to the digest of their bytes taken in one piece")
    void hashesSmallFilesToTheDigestOfTheirBytes(@TempDir Path dir) throws IOException {
        Random bytes = new Random(FIXTURE_SEED);
        for (int length : LENGTHS_AROUND_A_BUFFER) {
            byte[] content = new byte[length];
            bytes.nextBytes(content);
            Path file = Files.write(dir.resolve("of-" + length + "-bytes.bin"), content);
            String expected = sha256InOnePiece(content);

            for (Map.Entry<String, Hasher> method : BOTH_METHODS.entrySet()) {
                String actual = method.getValue().sha256(file);
                claim(
                        "a file of " + length + " bytes hashes, by " + method.getKey() + ", to the"
                                + " SHA-256 the JDK gives for the same bytes handed over in one piece. The"
                                + " lengths tried sit on each side of " + BUFFER_BYTES + " bytes (64 KiB, the"
                                + " buffer a file is read through) and of twice that, with the empty file,"
                                + " a one-byte file and one that ends partway through a buffer: every value"
                                + " already on record for a file stays the value of that file",
                        () -> assertThat(actual).isEqualTo(expected));
            }
        }
    }

    @Test
    @Story("Reading a file in parts changes no hash")
    @DisplayName("The empty file and the standard test input hash to their published digests by both methods")
    void hashesThePublishedVectors(@TempDir Path dir) throws IOException {
        Path empty = Files.createFile(dir.resolve("empty.bin"));
        Path abc = Files.write(dir.resolve("abc.txt"), new byte[] {'a', 'b', 'c'});

        for (Map.Entry<String, Hasher> method : BOTH_METHODS.entrySet()) {
            String ofNothing = method.getValue().sha256(empty);
            String ofAbc = method.getValue().sha256(abc);
            claim(
                    "a file with no bytes hashes, by " + method.getKey() + ", to the digest"
                            + " published for no bytes: a read that ends at once still yields a hash",
                    () -> assertThat(ofNothing).isEqualTo(DIGEST_OF_NOTHING));
            claim(
                    "and the three letters of the standard test input hash to the digest published for"
                            + " them, in lowercase hexadecimal, so the value is the one any other tool"
                            + " computes",
                    () -> assertThat(ofAbc).isEqualTo(DIGEST_OF_ABC));
        }
    }

    /** What one hash came to: its value, or what it threw. An error is kept, not thrown on. */
    private record Outcome(String sha256, Throwable thrown) {}

    /** One hash and what the thread allocated while it took it. */
    private record Measured(Outcome outcome, long allocatedBytes) {}

    /**
     * Hashes {@code file}, catching whatever is thrown, an {@code Error} included: an
     * {@code OutOfMemoryError} left to reach the test engine would end the whole run, where this is one
     * test's failure.
     */
    private static Outcome hashing(Path file, Hasher hasher) {
        String[] sha256 = new String[1];
        Throwable thrown = catchThrowable(() -> sha256[0] = hasher.sha256(file));
        return new Outcome(sha256[0], thrown);
    }

    private static Measured measured(com.sun.management.ThreadMXBean threads, Path file, Hasher hasher) {
        long before = threads.getCurrentThreadAllocatedBytes();
        Outcome outcome = hashing(file, hasher);
        return new Measured(outcome, threads.getCurrentThreadAllocatedBytes() - before);
    }

    /** The JVM's count of bytes allocated per thread, or an aborted test where this JVM keeps none. */
    private static com.sun.management.ThreadMXBean allocationCounter() {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads)
                || !threads.isThreadAllocatedMemorySupported()) {
            return Assumptions.abort("this JVM does not count the bytes a thread allocates, so what hashing"
                    + " allocates cannot be measured here");
        }
        if (!threads.isThreadAllocatedMemoryEnabled()) {
            threads.setThreadAllocatedMemoryEnabled(true);
        }
        return threads;
    }

    /**
     * A file of {@code length} bytes that is a hole but for a few bytes: three at the start, three across
     * the end of the first 64 KiB, three across the 2 GiB mark where the file reaches it, and its last.
     * Aborts the test, by assumption and with the reason, where the file system has no sparse files or
     * refuses the file.
     */
    private static Path sparseFile(Path file, long length) throws IOException {
        String store = Files.getFileStore(file.getParent()).type().toLowerCase(Locale.ROOT);
        if (STORES_WITHOUT_SPARSE_FILES.contains(store)) {
            return Assumptions.abort("the temporary directory is on a " + store + " file system, which has no"
                    + " sparse files: a file of " + length + " bytes would have to be written out in full");
        }
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SPARSE)) {
            channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}), 0);
            channel.write(ByteBuffer.wrap(new byte[] {4, 5, 6}), BUFFER_BYTES - 1L);
            if (length > TWO_GIB + 1) {
                channel.write(ByteBuffer.wrap(new byte[] {7, 8, 9}), TWO_GIB - 1);
            }
            channel.write(ByteBuffer.wrap(new byte[] {10}), length - 1);
        } catch (IOException | UnsupportedOperationException refused) {
            return Assumptions.abort("the file system could not make a sparse file of " + length + " bytes: " + refused);
        }
        return file;
    }

    /** The reference: the file read 1 MiB at a time through a channel, sharing no code with either method. */
    private static String sha256ByChannel(Path file) throws IOException {
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate((int) MIB);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256InOnePiece(byte[] content) {
        return HexFormat.of().formatHex(sha256().digest(content));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
