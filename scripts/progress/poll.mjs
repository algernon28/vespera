// Polls the counting script of ADR-212 and rewrites target/progress/progress.html, a page of a run's counts
// that reloads itself. Numbers only, exactly what the counting script prints.
// Run from the repository root: node scripts/progress/poll.mjs [working dir] [seconds]
// The working directory defaults to VESPERA_WORKING_DIR, from the environment or from .env.
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const COUNTS = join(root, '.claude', 'hooks', 'working-directory-counts.mjs');
const envFile = join(root, '.env');
const fromEnvFile = existsSync(envFile)
  ? /^VESPERA_WORKING_DIR\s*=\s*"?([^"\r\n]*)"?/m.exec(readFileSync(envFile, 'utf8'))?.[1]
  : undefined;
const workingDir = process.argv[2] ?? process.env.VESPERA_WORKING_DIR ?? fromEnvFile ?? '.vespera';
const interval = Number(process.argv[3] ?? 10);
const out = join(root, 'target', 'progress');
mkdirSync(out, { recursive: true });
const samplesFile = join(out, 'samples.json');
const samples = existsSync(samplesFile) ? JSON.parse(readFileSync(samplesFile, 'utf8')) : [];

function reading() {
  const run = spawnSync(process.execPath, [COUNTS, workingDir], { encoding: 'utf8' });
  if (run.status !== 0) throw new Error(`counts script exited ${run.status}: ${run.stderr.trim()}`);
  const rows = run.stdout.trim().split(/\r?\n/).map(line => {
    const [record, ...pairs] = line.split(' ');
    return { record, ...Object.fromEntries(pairs.map(p => p.split('='))) };
  });
  // A working directory can hold a stage's run twice, after a change moved its run id: finished is by run,
  // and the run in progress, where there is one, is the one charted and shown last.
  const finished = new Set(rows.filter(r => r.record === 'finished-steps').map(r => r.run));
  const stages = rows.filter(r => r.record === 'run').map(r => ({ stage: r.stage, run: r.run, finished: finished.has(r.run) }));
  stages.sort((a, b) => a.finished === b.finished ? 0 : a.finished ? -1 : 1);
  const extraction = stages.findLast(s => s.stage === 'extraction');
  const upstream = stages.find(s => s.stage === 'byte-level-reduction');
  const of = (record, run, kind) =>
    Number(rows.find(r => r.record === record && r.run === run && (!kind || r.kind === kind))?.count ?? 0);
  return {
    stages,
    entered: upstream ? of('survivors', upstream.run) : 0,
    sample: {
      t: Date.now(),
      failed: extraction ? of('verdicts', extraction.run, 'EXTRACTION_FAILED') : 0,
      degenerate: extraction ? of('verdicts', extraction.run, 'DEGENERATE_OUTPUT') : 0,
      surviving: extraction ? of('survivors', extraction.run) : 0,
      db: Number(rows.find(r => r.record === 'files' && r.folder === 'database')?.bytes ?? 0),
    },
  };
}

function tick() {
  try {
    const { stages, entered, sample } = reading();
    samples.push(sample);
    writeFileSync(samplesFile, JSON.stringify(samples));
    const data = JSON.stringify({ samples, stages, entered, generated: Date.now(), interval });
    const html = readFileSync(join(here, 'template.html'), 'utf8').replace(/\/\*DATA\*\/.*\/\*END\*\//, () => data);
    writeFileSync(join(out, 'progress.html'), html);
    console.log(new Date().toLocaleTimeString(), `failed=${sample.failed} surviving=${sample.surviving} stage=${stages.at(-1)?.stage}`);
  } catch (e) {
    console.error(new Date().toLocaleTimeString(), e.message);
  }
}

console.log(`Writing ${join(out, 'progress.html')} every ${interval} s from ${workingDir}`);
tick();
setInterval(tick, interval * 1000);
