# AGENTS.md

Entry point for AI agents in this repository. Primary project conventions, tech stack and
role/skill routing live in `GEMINI.md` and `.agents/roles/Roles.md` — read those for specifics.
This file adds the cross-tool **second-brain sync** rule below.

## Second-brain sync (start & end of session)

Vladimir keeps a **knowledge card** for this project in his Obsidian "second brain" (an LLM Wiki
in the spirit of Karpathy). It lives in a separate folder, **outside this repo**:

- CachyOS: `~/YandexDisk/Obsidian/My Vault/projects/Habit Tracker.md`
- Other OSes (macOS/Windows): the vault path differs — confirm with Vladimir or check the vault's
  `reference/Рабочие репозитории.md` registry.

**At the START of a working session** on this project:

1. **Read that card first** to recover prior context — past decisions, what was already done,
   open questions, follow-ups, and the working rules. It complements this repo's own docs and
   git history; treat it as the running log of our work together.
2. Reading is read-only (just open the absolute path) — no permission to write is needed.
3. If the path doesn't exist (different OS, or the vault isn't synced yet), ask Vladimir for the
   vault path instead of guessing — but don't block the work if he prefers to skip it.

**At the END of a working session** (after finishing a task, fix, or feature):

1. **Proactively ask** whether to record the outcome into that card. Do **not** write to it
   automatically or silently.
2. If Vladimir agrees, **append** to the `## Решения и заметки` section (newest entry on top,
   dated), **in Russian**: a short 1–3 line summary — what changed, key decisions, version/branch,
   any follow-ups. Add `[[wiki-links]]` where relevant and bump the card's `updated:` field.
3. The card lives outside this repo, so editing it means writing to an **absolute path** —
   request permission as usual.
4. **Never** stage or commit the card edit into this repo's git history. The card belongs to the
   vault (synced by Yandex.Disk, no git there).

Reminder: **do not commit or push** until Vladimir has verified the build locally and explicitly
approved.
