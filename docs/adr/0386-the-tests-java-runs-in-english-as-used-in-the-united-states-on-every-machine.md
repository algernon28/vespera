# ADR-386 — The tests' Java runs in English as used in the United States on every machine

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Settles**: which locale the tests' Java runs in.
- **Keeps**: [ADR-357](0357-the-unit-runs-http-client-selector-managers-run-on-platform-threads.md) whole: surefire's `argLine` keeps its selector property, and failsafe's still does not carry it. [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) whole. Every line of `src/main`. No run id moves.
- **Rests on**: JavaFX's controls, which name the items of a text control's context menu from a resource bundle chosen by `Locale.getDefault()`; and the JDK 26's start, measured on Windows 11 with an Italian locale.

## Context

A test that reads a text JavaFX or the JDK localizes reads it in the locale of the tests' Java, which surefire and failsafe start from the machine's own unless told otherwise. `EveryTextOfTheWindowCanBeCopiedTest` finds the context menu's items of the outcome by their names, `Copy` and `Select All`. On a machine whose locale is Italian JavaFX names them `Copia` and `Seleziona tutto`, the test finds none, and it fails on every run there, while CI's machines, in English, pass it. A test whose outcome depends on how the machine is set up holds nothing a reader can rely on: it fails where the code is right, and a test that reads a localized text could pass where it is wrong.

### Measured

On Windows 11 with an Italian locale, Java 26:

- **The JDK's default locale**, of every category, is `it_IT`; started with `-Duser.language=en -Duser.country=US` it is `en_US` for `Locale.getDefault()`, `Locale.Category.FORMAT` and `Locale.Category.DISPLAY` alike, and no `user.language.format` is set.
- **`EveryTextOfTheWindowCanBeCopiedTest`** fails on its claim that Copy and Select All are enabled in the outcome's context menu, the menu's items read in Italian; with the two properties in surefire's `argLine` it passes.

## Decision

### §1. Surefire and failsafe start the tests' Java in `en_US`

Both plugins' `argLine` carries `-Duser.language=en -Duser.country=US`, beside what each already carries. Every unit and integration test then runs in English as used in the United States, on every machine, and a text JavaFX or the JDK localizes, a context menu's items, a dialog's buttons, a number or a date formatted by default, reads the same in CI and on an operator's machine.

### §2. The shipped jar keeps the machine's locale

Nothing under `src/main`, `application.yaml` or the jar's start changes. A test that starts the packaged jar as a child process starts it without the two properties, and it runs as the jar runs for the operator.

## Alternatives rejected

- **Find the context menu's items by their place or their action, not their name.** It fixes the one test and leaves every other test that reads a localized text to the machine's locale, found only when a machine of another locale runs it.
- **Set the default locale in the test, `Locale.setDefault`.** The JVM's default is one of the whole run, shared by every test the one JVM runs one at a time, and JavaFX reads its resource bundles once; a test that sets it leaks into the tests after it, or comes after the bundle is read.

## Consequences

- **A test reads a localized text as it reads in English, on every machine**, and the machine's locale changes no test's outcome.
- **No test runs in another locale.** A defect of the code that only another locale shows, a decimal comma or a localized name parsed back, is found by no test; a test that wants one sets the locale it needs where it needs it.
- **A run from an IDE that does not take the pom's `argLine`** runs in the machine's locale, as before.
- **Nothing under `src/main` changes, and no run id moves.**

## What this does not decide

- **Whether the jar should run in one locale itself**, whatever the machine's, is not decided here.

## Tests

- `ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText`: the tests' Java runs in `en_US`, the language the context menu's items are named in, and Copy and Select All are enabled in the outcome's context menu (§1). Without the two properties it fails on a machine of another locale, and passes on one in English.
