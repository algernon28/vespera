# ADR-447 — The Architecture window's texts stand clear of their outlines, and the products' parts carry their official logos

- **Date**: 2026-11-23
- **Status**: accepted on 2026-11-23.
- **Release**: minor
- **Decided**: by the operator: that the boxes of the Architecture window keep a space between their texts and their outlines, so that every text fits its box (§1); and that the parts standing for a product, and the Docker group, carry that product's official logo, in its own colours (§2). Under the operator's delegation: the sizes and places of §1, the source of each logo, the tile beneath it and its size (§2), the classes and the tests.
- **Amends**: [ADR-442](0442-the-architecture-window-draws-the-reference-picture-and-lights-what-runs-from-what-the-cockpit-reads.md) §4's *"so the window adds no colour of its own to either theme"*: the logos of §2 are drawn in their brands' colours on a white tile, in either theme, each colour a token of both theme rules. The rest of §4, the kinds' colours, the parts filled with the surface, stands. It carries a note at its head.
- **Keeps**: ADR-442 §2, the reference picture, its parts and wires where the picture places them, the drawing a little wider and taller (§1); §5, a state a colour, an outline and a word, every text in the ink colour at 4.5 : 1 against its box, every text a `SelectableText`; §6, the wires and their motion; §7, the drawing fitting the window or standing at 100%. [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §2: no class of the control panel sets a colour of its own; every colour is the stylesheet's, written in its two theme rules.

## Context

**Texts ran into the outlines.** ADR-442's drawing gave each part's texts 4 px of padding. A stage's box was 76 px wide, and *Arrangement*, in bold at 11 px, is about 67 px: it ran from one side of its box to the other, into the inner line of the double outline a stage at a gate draws 4 to 6 px inside its edge. The Vespera CLI's box was 46 px high and holds three lines of about 17 px each while the CLI runs, its title, its line and its word: the first and the last were cut by the box's edge. More padding alone would cut more.

**The picture names products.** Five of its parts and one of its groups stand for a product the operator knows by its logo: Docling, Ollama, the SQLite ledger, the GPU, which the containers use only on an NVIDIA card (ADR-445 §3), Confluence, and Docker. The other parts, the archive, the seed folder, the control panel, the CLI, the stages, the deliverable, the reports and the website, are Vespera's own or the operator's, and have no logo.

**Where the logos come from.**

| Logo | Source | Licence |
| --- | --- | --- |
| Docling | `docs/assets/logo.png` of [docling-project/docling](https://github.com/docling-project/docling), 1,024 × 1,024, a picture of many colours | MIT |
| Ollama, SQLite, Confluence, Docker, NVIDIA | [Simple Icons](https://github.com/simple-icons/simple-icons) 13.21.0, each one path on a 24 × 24 view box, with the brand's colour the project records from the brand's own press kit or source | CC0-1.0 |

Docling's logo is a drawing of 41 shapes in several colours, which one path in one colour cannot carry; Simple Icons has none of it. Each logo is its owner's trademark, drawn to name the product the part stands for.

**Colours of their own.** Ollama's mark is black and SQLite's and Confluence's dark blue: on the dark theme's surface, `#25232f`, none would show. Every colour of the window is a token of the stylesheet's two theme rules, which `TheControlPanelsThemeTest` holds.

## Decision

### 1. Every text of a part fits its box, clear of its outline

**Each part's texts lie 8 px inside its box's sides and 6 px inside its top and bottom**, `.architecture-texts`, so that no text reaches the thickest outline a state draws, the 4 px of a running part or the inner line of a part at a gate, which ends 6 px inside. **No text of a part is cut**: none shows the "…" of a line whose end is out of view (ADR-377 §6), in any state of ADR-442 §5, a running stage's percent among its lines.

**Whatever font the system draws in.** The stylesheet names a size and no font, so each system draws the drawing in its own: on Linux, where no font is configured, the font drawn is wider than Windows', and at 11 px *Arrangement*, *redundancy*, *Document archive* and *⏸ at a gate* do not fit inside the padding of boxes they fit on Windows. **A line of a part whose texts stand one beneath another that does not fit the width inside its padding is drawn at 10, 9 or 8 px, the largest that fits**, measured in the font drawn, by the style classes `architecture-size-10`, `architecture-size-9` and `architecture-size-8`; a line that fits again goes back to the stylesheet's size. The ledger's row is wide enough in any font. Measured with the drawing's texts set to Verdana, a font as wide as those Linux draws in: by this rule every line fits in every state; without it, lines are cut in every state.

So that they fit, **the drawing is 1,226 × 550 px**, from 1,130 × 530: each stage 88 px wide, 94 px apart; the application, the control, the ledger, the operator's machine and the Docker group wider by as much, the outputs, the connectors and the legend moved right with them; the control 100 px high, the control panel's and the CLI's boxes 68 px; the containers' boxes 70 px high, Docling's and Ollama's 270 px wide and the GPU's 180. Each part and wire keeps its place in the picture, its neighbours and the sides it is joined at (ADR-442 §2). A line is cut into lines at its spaces to the width its box leaves inside its padding, and beside its logo where it has one.

### 2. The products' parts carry their official logos

| Part | Logo | Its node |
| --- | --- | --- |
| Docling | Docling's | `architecture-logo-docling` |
| Ollama | Ollama's | `architecture-logo-ollama` |
| the SQLite ledger | SQLite's | `architecture-logo-sqlite` |
| the GPU | NVIDIA's | `architecture-logo-nvidia` |
| Confluence | Confluence's | `architecture-logo-confluence` |
| the Docker group | Docker's | `architecture-logo-docker` |

**Each logo is its official one, in its brand's colours**: Docling's as its project draws it, scaled to 96 × 96 px; the others as Simple Icons draws them, one path each, filled with the colour it records, Ollama `#000000`, SQLite `#003b57`, Confluence `#172b4d`, Docker `#2496ed`, NVIDIA `#76b900`. **Each stands on a white tile**, 22 px square with rounded corners, the logo 16 px inside it, **in either theme**, so that each is seen as its brand draws it on white, on the light theme's surface, on a running part's fill and on the dark theme's surface alike. The white and the five colours are tokens, `-vespera-logo-tile` and `-vespera-brand-<name>`, written with the same value in both theme rules; no class names a colour.

**Where it stands**: beside the texts, on the left, in a box at least twice as wide as it is high, the containers' and the ledger's, first in the ledger's row; above the title, in a taller box, Confluence's; and left of the title, in the Docker group. It lies inside its box's padding and over no text. It is no text, so it is neither selected nor copied, and it lights nothing: a part's state is still its outline, its fill and its word (ADR-442 §5).

**The logos are resources of the control panel's package**, `io/algernon/vespera/ui/logos/`: `docling.png`; `ollama.svg`, `sqlite.svg`, `confluence.svg`, `docker.svg` and `nvidia.svg`, as Simple Icons ships them, whose path the drawing reads; and `NOTICE.txt`, naming each source and licence and Docling's copyright, as its MIT licence asks.

### 3. What changes, by class, and run ids

- **`ArchitectureView`**: §1's sizes and places, a line cut to the width inside its padding, a line too wide for it drawn smaller before each layout, and §2's tiles, a part's beside or above its texts and the Docker group's before its title.
- **`control-panel.css`**: §1's padding and its three smaller sizes; §2's tile and its colours, with the six tokens in both theme rules.
- **Resources**: §2's six logos and their notice.

`io.algernon.vespera.ui` is in no module and no class of the jar changes, so **no run id moves**.

## Consequences

- Every text of the drawing reads whole, in every state, in either theme, in whatever font the system draws; where that font is wide, some lines are smaller than the rest.
- The operator knows Docling, Ollama, SQLite, NVIDIA, Confluence and Docker at a glance.
- A logo its owner changes is not followed until its file here is replaced.
- The drawing is about 8 % wider: in a window of the same size it is drawn a little smaller.

## Alternatives refused

- **The logos in one colour, the part's kind's**, which would have kept ADR-442 §4 whole. Docling's, the one most often lit, would have been a shape no one knows, and the operator asked for the official ones.
- **The logos on the part's own fill, without a tile.** Ollama's black and SQLite's and Confluence's dark blue would not show in the dark theme.
- **More padding in boxes of the same size.** *Arrangement* and the CLI's three lines would have been cut more.
- **Boxes wide enough for the widest font.** No width is known to be enough for every system's font, and the drawing would shrink further in the window on every system for the sake of one.
- **A font named by the stylesheet, shipped with the jar.** It would make the drawing alone differ from the rest of the window, which follows the system's font.

## What this does not decide

- **A logo for the control panel, the CLI, the website or the stages**, which are Vespera's own.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheArchitectureWindowTest.everyTextOfAPartFitsItsBoxClearOfItsOutlineInEveryState` | §1: in every state of ADR-442 §5, a running stage showing its percent, every text of every part lies 6 px or more inside its box, and none shows a "…", on Windows and on Linux, whose fonts differ |
| `ui.TheArchitectureWindowTest.theProductsPartsCarryTheirOfficialLogosOnAWhiteTile` | §2: Docling, Ollama, the ledger, the GPU and Confluence each hold their logo's node, the Docker group its own beside its title; each mark is drawn, Docling's picture loaded and each other's path in its brand's colour; each tile is white in the light theme and in the dark; and each lies inside its box's padding and over none of its texts |
| `ui.TheArchitectureWindowTest.aReportReadLightsTheDrawingAndTheLine` | §2: unchanged, every text of a part still at 4.5 : 1 against its box in either theme, the logos being no text |
