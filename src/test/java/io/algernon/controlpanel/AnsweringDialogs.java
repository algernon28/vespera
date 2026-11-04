package io.algernon.controlpanel;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The window's {@link Dialogs} as a test answers them (ADR-339 §6): no native dialog opens. Each question the window
 * asks is kept with its two answers' words and answered with {@link #answer}; each report opened is kept; no folder and
 * no file is ever chosen.
 */
final class AnsweringDialogs implements Dialogs {

    /** A question the window asked: its sentence, the words of the answer that goes on, and of the one that does not. */
    record Asked(String sentence, String yes, String no) {
    }

    /** Every question asked, in order. */
    final List<Asked> asked = new CopyOnWriteArrayList<>();

    /** Every report the window opened, in order. */
    final List<Path> opened = new CopyOnWriteArrayList<>();

    /** What the next question is answered: true for the answer that goes on. */
    volatile boolean answer;

    @Override
    public Optional<Path> folder(String title, String initial) {
        return Optional.empty();
    }

    @Override
    public Optional<Path> file(String title, String initial) {
        return Optional.empty();
    }

    @Override
    public void open(Path report) {
        opened.add(report);
    }

    @Override
    public boolean confirm(String sentence, String yes, String no) {
        asked.add(new Asked(sentence, yes, no));
        return answer;
    }
}
