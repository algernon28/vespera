#!/usr/bin/env node
// PreToolUse guard: an agent may prepare work, never publish it.
//
// AGENTS.md requires approval before any agent commits, pushes or merges. That is an
// instruction, and an agent that decides the task implies a commit can follow the task
// instead. This refuses the call outright.
//
// Matching is deliberately loose -- the verb anywhere after a git token, rather than a
// precise flag grammar -- because git takes flags with arguments (git -C . commit) and a
// guard that can be walked around by adding a flag is not a guard. A false positive costs
// one message; a false negative costs a commit nobody approved.
//
// Exit 2 blocks the tool call and the agent is shown stderr as the reason. Exit 0 allows.

const FORBIDDEN = [
  { pattern: /\bgit\b[^;&|]*\bcommit\b/, what: "git commit" },
  { pattern: /\bgit\b[^;&|]*\bpush\b/, what: "git push" },
  { pattern: /\bgit\b[^;&|]*\bmerge\b/, what: "git merge" },
  { pattern: /\bgit\b[^;&|]*\btag\b/, what: "git tag" },
  { pattern: /\bgh\b[^;&|]*\bpr\b[^;&|]*\bmerge\b/, what: "gh pr merge" },
  { pattern: /\bgh\b[^;&|]*\brelease\b[^;&|]*\bcreate\b/, what: "gh release create" },
];

export function verdict(command) {
  if (typeof command !== "string") return null;
  for (const { pattern, what } of FORBIDDEN) {
    if (pattern.test(command)) return what;
  }
  return null;
}

// Importable for its own tests; only the direct run reads stdin and exits.
if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split(String.fromCharCode(92)).join("/"))) {
  const input = await new Promise((done) => {
    let raw = "";
    process.stdin.on("data", (chunk) => (raw += chunk));
    process.stdin.on("end", () => done(raw));
  });

  let payload;
  try {
    payload = JSON.parse(input || "{}");
  } catch {
    // Unparsable input means the guard cannot tell what is being run, so it refuses rather
    // than waves it through: this one fails closed.
    console.error("Blocked: the git guard could not read the tool call, so it refused it.");
    process.exit(2);
  }

  const what = verdict(payload?.tool_input?.command);
  if (what) {
    console.error(
      `Blocked: ${what} needs a person's approval first. Leave the working tree as it is, ` +
        `report what you would commit and why, and let them run it or tell you to.`,
    );
    process.exit(2);
  }
  process.exit(0);
}
