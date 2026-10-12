package io.algernon.vespera.ledger;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * A page written beside its place, moved over it in one move that replaces it, so that a reader finds the old
 * page or the new and no half of either (ADR-223 sections 3 and 6), and moved again a few times where the move
 * is refused as access denied (ADR-242) or, on Windows, by an error Java has no more specific type for (ADR-244,
 * ADR-245).
 *
 * <p><b>Why a refusal is worth a second attempt.</b> On Windows a program that has just opened the page or its
 * place without letting it be deleted or shared, a virus scanner looking at what was written, an indexer or a
 * folder kept in step with a cloud, makes the move fail for as long as it holds the file, which is usually a
 * moment: as access denied, which Java gives as {@link AccessDeniedException}, or as a sharing or lock
 * violation, which Java gives as a {@link FileSystemException} of no subclass, keeping no error code and a
 * reason in the machine's language. So the type is what is read, and never the reason: on Windows a failure of
 * exactly that type is attempted again, a full disk among them, said after the attempts are spent. At 6b such a
 * failure would roll back every synthesis doc the invocation wrote. Elsewhere no program holding a file stops
 * its move, and a failure of that type is a disk that is read-only or full, a folder in the page's place or a
 * fault of the device, which is thrown at once, as is a failure Java names by a subclass, a missing file or
 * folder among them, and any other {@link IOException}: none is one a program holding the file lifts.
 *
 * <p><b>How often, and how long.</b> {@value #ATTEMPTS} attempts, the waits between them doubling from {@value
 * #FIRST_WAIT_MILLIS} milliseconds, 1.5 seconds in all, after which the last refusal is thrown as it came. The
 * numbers are a judgement and not a measurement: long enough for a scanner's look at a page of this size, short
 * enough that a refusal that does not lift is said within two seconds.
 *
 * <p>It lives in {@code ledger}, the one module every capability may name (ADR-040), as {@link Placeholders}
 * does, so that {@code synthesis}'s tree and {@code pipeline}'s pages share one move, and holds no rule: what is
 * written, and where, stays the caller's.
 */
public final class ReplacingMove {

    /** One move of a page over its place, as the platform makes it. */
    @FunctionalInterface
    public interface Move {
        void move(Path part, Path target) throws IOException;
    }

    /** A wait between two attempts, of the given number of milliseconds. */
    @FunctionalInterface
    public interface Wait {
        void forMillis(long millis) throws InterruptedException;
    }

    /** How many times a refused move is attempted in all, the first included. */
    static final int ATTEMPTS = 5;

    /** The wait after the first refusal, in milliseconds; each wait after it is twice the one before. */
    static final long FIRST_WAIT_MILLIS = 100;

    /** Whether this program runs on Windows, the one platform where a sharing violation stops a move. */
    private static final boolean ON_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /** The move every page is put in place by: one atomic move that replaces, attempted again as above. */
    public static final ReplacingMove ATOMIC = new ReplacingMove(
            (part, target) -> Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
            Thread::sleep);

    private final Move move;

    private final Wait wait;

    private final boolean onWindows;

    /** A move made by {@code move}, waiting between attempts through {@code wait}, as on the platform it runs on. */
    public ReplacingMove(Move move, Wait wait) {
        this(move, wait, ON_WINDOWS);
    }

    /**
     * A move made by {@code move}, waiting between attempts through {@code wait}, as on Windows where {@code
     * onWindows} and as elsewhere where not: the seam a test needs to stand in for either platform on the one it
     * runs on.
     */
    public ReplacingMove(Move move, Wait wait, boolean onWindows) {
        this.move = move;
        this.wait = wait;
        this.onWindows = onWindows;
    }

    /**
     * {@code part} over {@code target}, attempted again after a wait where the move is refused as access denied
     * or, on Windows, by a {@link FileSystemException} of no subclass, up to {@value #ATTEMPTS} attempts in all.
     *
     * @throws FileSystemException the last refusal, where every attempt was refused; {@code part} is then
     *     still beside {@code target}, and {@code target} as it was
     * @throws InterruptedIOException where the thread is interrupted while it waits, the interruption kept
     */
    public void intoPlace(Path part, Path target) throws IOException {
        long waitMillis = FIRST_WAIT_MILLIS;
        for (int attempt = 1; ; attempt++) {
            try {
                move.move(part, target);
                return;
            } catch (FileSystemException refused) {
                if (!isLiftedByTheHolder(refused) || attempt == ATTEMPTS) {
                    throw refused;
                }
                try {
                    wait.forMillis(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException stopped = new InterruptedIOException(
                            "interrupted while waiting to move " + part + " over " + target + " again");
                    stopped.addSuppressed(refused);
                    throw stopped;
                }
                waitMillis *= 2;
            }
        }
    }

    /**
     * Whether {@code refused} is how Java gives a refusal that a program holding the file lifts once it lets go:
     * access denied, or on Windows a sharing or lock violation, which Java gives there as a {@link
     * FileSystemException} itself and no subclass (ADR-244, ADR-245).
     */
    private boolean isLiftedByTheHolder(FileSystemException refused) {
        return refused instanceof AccessDeniedException
                || (onWindows && refused.getClass() == FileSystemException.class);
    }
}
