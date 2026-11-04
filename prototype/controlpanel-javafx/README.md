# PROTOTYPE — JavaFX control panel (throwaway, branch `javafx` only)

**Question:** what should a desktop JavaFX control panel look like, in place of the loopback-browser one in `io.algernon.controlpanel`?

Three structurally different variants share one in-memory stub. The stub writes nothing to disk and starts no process. Its "run" is a timer that walks the 16 steps and stops at `relevance-floor` to ask for `relevanceScoreFloor`.

| Key | Variant | Shape |
|---|---|---|
| A | Sidebar pages | A port of the browser panel: a left rail with Launch, Invocation, Progress, Next, Relevance, Code sets and Profile, one page at a time |
| B | Single-screen cockpit | **The operator's choice.** No pages: Run and Stop with the next value wanted in the main card, a chart of the 16 steps with progress bars beside the live output, then Profile (constrained controls for every key) and Inclusions (a folder tree) as tabs. The sidebar holds Preflight, Folders, Services, Seed folder, Code sets, Command options and Reports |
| C | Guided wizard | One question per screen: Where → With what → Seed → Run → Answer, then back to Run |

## Run

```
export JAVA_HOME=~/.jdks/openjdk-26.0.2.1
./mvnw -f prototype/controlpanel-javafx/pom.xml javafx:run              # starts on A
./mvnw -f prototype/controlpanel-javafx/pom.xml javafx:run -Dvariant=B
```

- To switch variants, use the floating bar at the bottom or `←` / `→`. The arrow keys do nothing while a text field has focus.
- Each switch prints the full stub state to the console.
- `-Dvariant="A smoke"` cycles through every variant and exits.

**Starting values are read from the real files and never written to them.** These are the environment, the main checkout's `.env`, the control panel's `launch-values.properties`, `application.yaml`, and the working directory's `profile.yaml`, `not-relevant-folders.yaml` and `code-set-names.yaml`. Save writes copies named `target/*.PROTOTYPE-wipe-me.yaml` instead. To go back to the real files, delete those copies.

**Open question.** B asks for no "why" behind a value and writes its own `provenance` line. The jar requires that line, so whether a person must still give a reason needs a decision before this is built for real.

The root pom does not include this project, so `./mvnw test` never builds it. JavaFX also stays out of the shipped pom, so ADR-046 is not touched.
