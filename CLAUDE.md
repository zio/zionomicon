# Claude Code Instructions for Zionomicon Solutions

## Build System

- Do not use `sbtn --client compile` directly, instead rely on the background compilation watcher for fast feedback
- If there was no background compilation running, start a new long running background task with `sbtn --client ~compile`.