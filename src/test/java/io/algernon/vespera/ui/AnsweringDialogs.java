package io.algernon.vespera.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The window's {@link Dialogs} as a test answers them (ADR-342 §6): no native dialog opens and nothing is handed to the
 * system. Each question the window asks is kept with its two answers' words and answered with {@link #answer}; each
 * address handed to the system is kept; no file is ever chosen, and a folder only where the test sets
 * {@link #folderChosen}.
 */
final class AnsweringDialogs implements Dialogs {

    /**
     * A question the window asked: its title (ADR-423 §1), its sentence, the words of the answer that goes on, and of
     * the one that does not.
     */
    record Asked(String title, String sentence, String yes, String no) {
    }

    /** Every question asked, in order. */
    final List<Asked> asked = new CopyOnWriteArrayList<>();

    /** Every address the window handed to the system to open, in order (ADR-344 §6). */
    final List<URI> handed = new CopyOnWriteArrayList<>();

    /** What the next question is answered: true for the answer that goes on. */
    volatile boolean answer;

    /** Every masked field the window asked to be filled in, by the sentence it asked with, in order (ADR-359 §3). */
    final List<String> maskedAsked = new CopyOnWriteArrayList<>();

    /** What the next masked field is answered: what is typed, or empty where it is cancelled. */
    volatile Optional<String> typed = Optional.empty();

    /** A warning that asked for a text: its title, its sentence and the text its field started with (ADR-383 §3). */
    record Warned(String title, String sentence, String initial) {
    }

    /** Every warning the window opened asking for a text, in order (ADR-383 §3). */
    final List<Warned> warned = new CopyOnWriteArrayList<>();

    /** What the next such warning is answered: what is typed, or empty where it is cancelled. */
    volatile Optional<String> entered = Optional.empty();

    /** A choice the window asked for (ADR-389 §4): its title, its sentence and its answers' words, in order. */
    record Chose(String title, String sentence, List<String> answers) {
    }

    /** Every choice asked for, in order. */
    final List<Chose> choices = new CopyOnWriteArrayList<>();

    /** What the next choice is answered: the words of one of its answers, or empty where the dialog is closed with none. */
    volatile Optional<String> chosen = Optional.empty();

    /**
     * Answers {@code Dialogs.choose} (ADR-389 §4): keeps the choice asked for and gives {@link #chosen}.
     */
    @Override
    public Optional<String> choose(String title, String sentence, List<String> answers) {
        choices.add(new Chose(title, sentence, List.copyOf(answers)));
        return chosen;
    }

    /** A secret the window asked for (ADR-444 §8): its title, its sentence, and its two answers' words. */
    record Secret(String title, String sentence, String yes, String no) {
    }

    /** Every secret asked for, in order. */
    final List<Secret> secrets = new CopyOnWriteArrayList<>();

    /** What the next secret is answered: what is typed, or empty where it is cancelled. */
    volatile Optional<String> secretTyped = Optional.empty();

    /** Answers {@code Dialogs.secret} (ADR-444 §8): keeps the secret asked for and gives {@link #secretTyped}'s characters. */
    @Override
    public Optional<char[]> secret(String title, String sentence, String yes, String no) {
        secrets.add(new Secret(title, sentence, yes, no));
        return secretTyped.map(String::toCharArray);
    }

    @Override
    public Optional<String> masked(String title, String sentence) {
        maskedAsked.add(sentence);
        return typed;
    }

    @Override
    public Optional<String> warnAndAsk(String title, String sentence, String initial) {
        warned.add(new Warned(title, sentence, initial));
        return entered;
    }

    /** The title of every folder dialog the window opened, in order (ADR-436 §5). */
    final List<String> foldersAsked = new CopyOnWriteArrayList<>();

    /** What the next folder dialog is answered: the folder chosen, or empty where none is (ADR-436 §5). */
    volatile Optional<Path> folderChosen = Optional.empty();

    /** Keeps the folder dialog's title and gives {@link #folderChosen}, none unless a test sets one. */
    @Override
    public Optional<Path> folder(String title, String initial) {
        foldersAsked.add(title);
        return folderChosen;
    }

    @Override
    public Optional<Path> file(String title, String initial) {
        return Optional.empty();
    }

    @Override
    public void handToTheSystem(URI address) {
        handed.add(address);
    }

    @Override
    public boolean confirm(String title, String sentence, String yes, String no) {
        asked.add(new Asked(title, sentence, yes, no));
        return answer;
    }
}
