package io.algernon.vespera.ledger;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A page written beside its place, moved over it in one move that replaces it, so that a reader finds the old
 * page or the new and no half of either (ADR-223 sections 3 and 6), and moved again a few times where the move
 * is refused as access denied (ADR-242).
 *
 * <p><b>Why a refusal is worth a second attempt.</b> On Windows a program that has just opened the page or its
 * place without letting it be deleted, a virus scanner looking at what was written, an indexer or a folder kept
 * in step with a cloud, makes the move fail as access denied for as long as it holds the file, which is usually
 * a moment. At 6b such a failure would roll back every synthesis doc the invocation wrote. Any other failure is
 * thrown at once: it is not one a program holding the file lifts.
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

    /** How many times a move refused as access denied is attempted in all, the first included. */
    static final int ATTEMPTS = 5;

    /** The wait after the first refusal, in milliseconds; each wait after it is twice the one before. */
    static final long FIRST_WAIT_MILLIS = 100;

    /** The move every page is put in place by: one atomic move that replaces, attempted again as above. */
    public static final ReplacingMove ATOMIC = new ReplacingMove(
            (part, target) -> Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
            Thread::sleep);

    private final Move move;

    private final Wait wait;

    /** A move made by {@code move}, waiting between attempts through {@code wait}. */
    public ReplacingMove(Move move, Wait wait) {
        this.move = move;
        this.wait = wait;
    }

    /**
     * {@code part} over {@code target}, attempted again after a wait where the move is refused as access denied,
     * up to {@value #ATTEMPTS} attempts in all.
     *
     * @throws AccessDeniedException the last refusal, where every attempt was refused; {@code part} is then
     *     still beside {@code target}, and {@code target} as it was
     * @throws InterruptedIOException where the thread is interrupted while it waits, the interruption kept
     */
    public void intoPlace(Path part, Path target) throws IOException {
        long waitMillis = FIRST_WAIT_MILLIS;
        for (int attempt = 1; ; attempt++) {
            try {
                move.move(part, target);
                return;
            } catch (AccessDeniedException refused) {
                if (attempt == ATTEMPTS) {
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
}
