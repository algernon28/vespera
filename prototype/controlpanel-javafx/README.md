# PROTOTYPE — JavaFX control panel (throwaway, branch `javafx` only)

**Question:** what should a desktop JavaFX control panel look like, in place of the loopback-browser one in `io.algernon.controlpanel`?

Three structurally different variants share one in-memory stub. The stub writes nothing to disk and starts no process. Its "run" is a timer that walks the 16 steps and stops at `relevance-floor` to ask for `relevanceScoreFloor`.

| Key | Variant | Shape |
|---|---|---|
| A | Sidebar pages | A port of the browser panel: a left rail with Launch, Invocation, Progress, Next, Relevance, Code sets and Profile, one page at a time |
| B | Single-screen cockpit | No pages. A 16-step strip across the top, "what's next" as the main action, a live output console, settings in an accordion and tabs |
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

The root pom does not include this project, so `./mvnw test` never builds it. JavaFX also stays out of the shipped pom, so ADR-046 is not touched.
