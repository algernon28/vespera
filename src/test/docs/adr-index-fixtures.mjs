// The ADR index and Adr.java, as small as their shapes allow, for the tests of docs/merge-adr-index.mjs and of the
// claim of their order in docs/check-claims.mjs (ADR-363). Not a test file: node --test runs it only by name. The index
// has two tables, as docs/adr/README.md has, so that a merge's rows are held to the table they belong in.

export const INDEX = "docs/adr/README.md";
export const ADR_JAVA = "src/test/java/io/algernon/vespera/Adr.java";

const four = (n) => String(n).padStart(4, "0");
const id = (n) => `ADR-${String(n).padStart(3, "0")}`;

/** A record's row of the index, as the index writes one. */
export const row = (n, title = `The record numbered ${n}`) =>
  `| [${id(n)}](${four(n)}-the-record-numbered-${n}.md) | 2026-11-06 | ${title} |`;

/** The records of the index's first table, the reconstituted ones, unless a test gives its own. */
export const RECONSTITUTED = [1, 2];

/**
 * The index as the index lays itself out: a table of the reconstituted records, then the heading "Decisions written after
 * the reconstitution", its paragraph and a table of its own. `rows` are the second table's, in the order
 * given, and `reconstituted` the first's; each a record's number or a row's whole text.
 */
export function indexOf(rows, eol = "\n", reconstituted = RECONSTITUTED) {
  const asRow = (r) => (typeof r === "number" ? row(r) : r);
  return [
    "# Decision records",
    "",
    "One file per architecture decision, numbered from ADR-001.",
    "",
    "| ID | Date | Title |",
    "| --- | --- | --- |",
    ...reconstituted.map(asRow),
    "",
    "## Decisions written after the reconstitution",
    "",
    "These carry their own full text: context, decision and consequences, as originally intended for an ADR.",
    "",
    "| ID | Date | Title |",
    "| --- | --- | --- |",
    ...rows.map(asRow),
    "",
  ].join(eol);
}

/**
 * A record's constant of Adr.java as its lines: a javadoc of two lines and the record's file on a line of its own. Every
 * constant has the same first, third and fourth lines, as Adr.java's do, so that git leaves the `/**` two of them share
 * outside their conflict, and the conflict holds neither constant whole.
 */
export const constant = (n, name = `THE_RECORD_NUMBERED_${n}`) => [
  "    /**",
  `     * ${id(n)} -- the record numbered ${n}, whose javadoc`,
  "     * runs to two lines.",
  "     */",
  `    public static final String ${name} =`,
  `            FILE + "${four(n)}-the-record-numbered-${n}.md";`,
];

/** Adr.java, its constants in the order given, each a record's number or a constant's lines; a blank line after each. */
export function adrJavaOf(constants, eol = "\n") {
  return [
    "package io.algernon.vespera;",
    "",
    "/** Links from a test to the decision it exists because of. */",
    "public final class Adr {",
    "",
    '    private static final String FILE = "https://github.com/algernon28/vespera/blob/main/docs/adr/";',
    "",
    ...constants.flatMap((c) => [...(typeof c === "number" ? constant(c) : c), ""]),
    "    private Adr() {",
    "    }",
    "}",
    "",
  ].join(eol);
}
