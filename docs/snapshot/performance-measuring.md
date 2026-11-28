# Measuring page additions held back until the release

For `book/src/performance/measuring.md`.

1. In the sentence under `## The showcase under load`, "The launcher reads
   four flags" becomes "five flags".
2. In the table under `## The flags`, after the `--frames=N` row:

| `--capture=PATH` | Writes the last frame of a `--frames=N` run to `PATH` as a PNG, as the screen showed it |

The rendering-offscreen section in `components-gpu.md` links this table as
`../performance/measuring.md#the-flags`.

3. Under `## The showcase under load`, add the startup-only trace command.

```sh
./goldberry-showcase-macos-aarch64 -Dgoldberry.startup.log.level=TRACE --frames=1
```

The timeline measures from process start to SDL's first successful
presentation. The macOS Showcase workflow includes it in the job summary.
