# Measuring page additions held back until the release

Written 2026-10-06. For `book/src/performance/measuring.md`.

1. In the sentence under `## The showcase under load`, "The launcher reads
   four flags" becomes "five flags".
2. In the table under `## The flags`, after the `--frames=N` row:

| `--capture=PATH` | Writes the last frame of a `--frames=N` run to `PATH` as a PNG, as the screen showed it |

The guide's rendering-offscreen section (`components-gpu.md` here) links this
table as `../performance/measuring.md#the-flags`.
