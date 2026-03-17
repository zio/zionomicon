# Claude Code Instructions for Zionomicon Solutions

## Build System

- Use `sbtn --client` instead of `sbt` for all builds
- Commit after each change

## Background Compilation Watcher

The project uses a background compilation watcher for fast feedback during development:

1. **Start once per session**: Run `sbtn --client "~compile"` in background via Bash tool with `run_in_background: true`, save the returned task ID
2. **After every file edit**: Use `TaskOutput(task_id, block: true)` to check for `[success]` or `[error]` messages
3. **Fix errors immediately**: The watcher recompiles automatically on file changes, then confirm with another `TaskOutput` call
4. **Manual fallback**: Only run `sbtn --client compile` manually (blocking) if no watcher task ID is available

### Example workflow

```bash
# Session start
sbtn --client "~compile"
# Returns: task_id = "xyz123"

# After editing a file
# Use TaskOutput(task_id="xyz123", block=true) to check compilation status
# Fix any errors shown
# TaskOutput confirms [success]
```
