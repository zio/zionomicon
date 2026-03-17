# Agent Instructions

- Use `sbtn --client` instead of `sbt`
- Commit after each change

## Background Compilation Watcher

- Start once per session: run `sbtn --client "~compile"` in background via Bash tool with `run_in_background: true`, save the returned task ID
- After every file edit, use `TaskOutput(task_id, block: true)` to check for `[success]` or `[error]` messages
- Fix errors immediately; the watcher recompiles automatically on file changes, then confirm with another `TaskOutput` call
- Only run `sbtn --client compile` manually (blocking) if no watcher task ID is available
