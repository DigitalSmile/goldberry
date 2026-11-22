# Guide piece for painting in place

Neutral prose: no ADR numbers, no history.

## `book/src/guide/logging.md`, the properties table, after `goldberry.gpu.power`

| `goldberry.gpu.inPlace` | `auto`, `on`, `off` | whether a composited window paints its frame in memory the GPU copies from, skipping a copy on the CPU; `auto` by default, which measures the device's upload memory once at start-up and stays staged where it reads slower than ordinary memory |
