// Run only in the Computer Use node_repl with its imported @oai/sky client.
// Activate a newly observed benchmark window once, before recording. Never reclaim focus mid-launch.
import { watch } from 'node:fs';
import { appendFile, mkdir, readFile, readdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';

export async function startForegroundHelper(sky, root, series = 'aa31',
  archiveRelative = 'docs/benchmarks/2026-10-22-aa-pilot') {
  const archive = join(root, archiveRelative, 'attempts');
  const log = join(root, `run/alacer/stability/${series}/foreground-helper.jsonl`);
  await mkdir(dirname(log), { recursive: true });
  await mkdir(archive, { recursive: true });
  const activated = new Set();
  const pending = new Map();
  const events = [];
  let stopped = false;
  let busy = false;
  async function record(event, data = {}) {
    const row = { t: Date.now() / 1000, event, ...data };
    events.push(row);
    await appendFile(log, JSON.stringify(row) + '\n');
  }
  async function consider(filename) {
    if (stopped || !filename?.endsWith('.json')) return;
    try {
      const attempt = JSON.parse(await readFile(join(archive, filename), 'utf8'));
      if (!attempt.label.startsWith(series + '-') || attempt.status !== 'running'
          || !attempt.launcher?.pid || activated.has(attempt.label)) return;
      if (!pending.has(attempt.label)) pending.set(attempt.label, { filename, tries: 0 });
    } catch { /* Atomic replacement can briefly hide a file; the next event retries it. */ }
  }
  const watcher = watch(archive, (_event, filename) => { void consider(filename); });
  async function tick() {
    if (busy || stopped || !pending.size) return;
    busy = true;
    try {
      for (const [label, entry] of pending) {
        const attempt = JSON.parse(await readFile(join(archive, entry.filename), 'utf8'));
        if (attempt.status !== 'running' || ++entry.tries > 30) {
          pending.delete(label);
          await record('activation-abandoned', { label, status: attempt.status });
          continue;
        }
        const windows = (await sky.list_windows()).filter(w => w.title === 'Minecraft 1.8.9'
          && w.app.toLowerCase() === ('process:' + attempt.launcher.exe).toLowerCase());
        if (windows.length !== 1) continue; // Never guess or target an unrelated window.
        const window = await sky.get_window({ id: windows[0].id, app: windows[0].app });
        await sky.activate_window({ window });
        await sky.get_window_state({ window, include_screenshot: false, include_text: true });
        activated.add(label);
        pending.delete(label);
        await record('activated-once', { label, pid: attempt.launcher.pid, window: window.id });
      }
    } catch (error) {
      await record('activation-error', { reason: String(error) });
    } finally { busy = false; }
  }
  for (const filename of await readdir(archive)) await consider(filename);
  await record('helper-started');
  return {
    events,
    // Computer Use API calls require an active node_repl execution context. Never call them from a background timer.
    async service(milliseconds = 45000) {
      const end = Date.now() + Math.min(milliseconds, 45000);
      while (!stopped && Date.now() < end) {
        await tick();
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
      return events.slice(-3);
    },
    async stop() {
      stopped = true;
      watcher.close();
      await record('helper-stopped');
    },
  };
}
