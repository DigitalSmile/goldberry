# 357. A test that paints asks for the library, and a download asks again

Date: 2026-09-17

## Status

Accepted. Repairs Snapshot run 12 (commit `f716adec`). Applies ADR-0338's rule
to one more test and adds retries to `:assets`' downloads.

## Context

Snapshot run 11 was green. Run 12 was red on four jobs, for two unrelated
reasons, both read off the runners' check-run annotations and job logs:

1. **`publish / {linux,macos,windows} / Java`** — `:core:test`, 2267 tests, 1
   failed: `WindowResizeTest > what the manager decided arrives through
   onResize, and a frame follows`, with `UnsatisfiedLinkError: libgoldberry not
   found`. Those jobs build with `-Pgoldberry.skipNative=true`, so there is no
   library by design. The test came in with ADR-0342. It is the only one of the
   five that runs the frame loop, and a frame rasterizes. It never called
   `RendererRequirement.enforce()`, which is the fifth instance of the defect
   ADR-0338 fixed in four tests.
2. **`publish / linux / Verify layouts (linux-aarch64)`** — `:core:prepareAssets`
   failed with `Server returned HTTP response code: 500` for
   `github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip`. The other three
   verify legs downloaded the same archive in the same minute. It was GitHub's
   failure, and `AssetCache` made one attempt.

Because the Java job stops at the first failing task, `:widgets`, `:html`,
`:example` and `:natives` never ran there. A local run of every module's tests
with `-Dgoldberry.native.library=/nonexistent/libgoldberry.so` found no other
test in that state: 0 failures across 5263 tests.

## Decision

**The painting test skips without a library. A download retries a failure
that can pass on its own, and only that.**

- `WindowResizeTest.arrivesThroughTheHandler` calls
  `RendererRequirement.enforce()`. The other four tests there only move a
  headless window and keep running without the library.
- `io.github.digitalsmile.goldberry.assets.download.Downloader` makes the
  request through `java.net.http` with redirects followed. It tries **four
  times**, waiting 2, 4 and 8 seconds, on HTTP 408, 429, any 5xx or an
  `IOException` from the connection. Any other status fails on the first
  attempt: a 404 is a pin that names nothing. Each failed attempt is printed to
  stderr, so a slow asset step says why.
- `AssetCache.fetch` and `fetchText` go through it. `fetchText` is now an
  instance method, so the licence download shares the cache's downloader.
- **The checksum is not retried.** An archive that arrives whole and hashes
  wrong is a changed upstream, and `AssetCache`'s message says so.

## Consequences

- A GitHub outage that lasts longer than about 14 seconds still fails the asset
  step, and should: a longer wait makes a stuck run look like a slow one.
- The transport, the sleeper and the attempt count are constructor arguments,
  so `DownloaderTest` covers the retry policy with no network and no waiting.
- Any new `:core` test that opens a window and runs `Goldberry.run()` with a
  paint handler needs the same guard. The no-library run above is how to check:
  `./gradlew test --continue -Dgoldberry.native.library=/nonexistent/libgoldberry.so`.
