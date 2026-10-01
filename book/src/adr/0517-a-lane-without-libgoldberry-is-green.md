# ADR-0517: A lane without libgoldberry is green

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0016](0016-verify-the-artifact-and-never-skip-the-check.md),
  [ADR-0019](0019-the-backend-spis-first-cut.md),
  [ADR-0338](0338-a-red-run-says-why-in-public.md),
  [ADR-0357](0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md),
  [ADR-0495](0495-media-is-published-and-snapshots-publish-again.md),
  [ADR-0508](0508-ffmpegs-source-is-published-beside-its-binaries-from-the-same-place.md),
  `docs/testing.md` §1.2, §4 and §5, `docs/snapshot-2026-10-01.md`

## Context

The Snapshot workflow had been red on every push since 2026-09-27. Its last
green run was 8209fcb0 on 2026-09-23; the twenty-five commits after it added
`goldberry-media` and the GPU milestone, and the first red run failed on a GPU
test that ADR-0503 has since fixed. Behind that, five jobs were failing for
reasons of their own, every one of them in a lane that has no `libgoldberry`:

- the three `Java` jobs (`-Pgoldberry.skipNative=true`, every OS), where
  `:media:test`, `:example:test` and `:widgets:test` failed;
- the two `FFmpeg and :media` jobs, which build FFmpeg and dav1d but not
  `libgoldberry`, where 54 of `:media`'s 715 tests failed.

Four defects, none in shipped code:

1. **A media widget's status callback starts the desktop backend.** Every
   media widget posts the player's status changes to the UI thread through
   `Goldberry.ui()`, and that starts the runtime if nothing has: `Sdl3Backend`,
   `SdlVideo`, and under them `NativeLibrary`. In a test that mounts the widget
   without a window, the first status change — often `MediaPlayer.close()`'s
   last one, on the test thread — booted SDL, and in a build with no library
   failed in `NativeLibrary$Holder`'s initialiser. The `Holder` is then
   poisoned for the rest of the JVM, so every test after it that touches a
   binding fails with `NoClassDefFoundError`, including ones that would have
   skipped cleanly. 7 tests in `:example` and about forty in `:media`.
2. **A picture golden was decoded by the rasterizer.** `PictureGolden`
   compared FFmpeg's decoded pictures byte for byte with PNGs it read through
   `Image.decode`, which is Blend2D. The Media workflow's runners have FFmpeg
   and no `libgoldberry`, so every S5 golden failed with
   `UnsatisfiedLinkError` — and no lane in CI has both, so the goldens had
   never been checked there.
3. **An aborted `@BeforeEach` still runs `@AfterEach`.** `WebViewPollingTest`
   mounts in `@BeforeEach` after `RendererRequirement.enforce()`; where the
   library is absent the mount aborts, the tree is null, and `@AfterEach`
   threw `NullPointerException` from `tree.unmount()`. Three failures, every
   OS.
4. **A guide sample inflates through md4c.** `BookMarkupTest` inflates every
   `kdl` block in the book; `markdown-view` parses its text as it inflates,
   through `libgoldberry`. One failure per OS.

And two more that were not about the library:

5. **`git archive` converts line endings.** `UpstreamSource` archives FFmpeg's
   and dav1d's source for the `ffmpeg-sources` classifier (ADR-0508) with the
   clone's `info/attributes` set to `* -export-ignore -export-subst`. Git for
   Windows checks out CRLF by default, `git archive` converts text the way a
   checkout would, and `UpstreamSourceTest` on the Windows runner found
   `version.h` ending in `\r\n`. The same tag archived on two runners was two
   different tarballs.
6. **A virtualised VideoToolbox gives VP9 up partway.** `HardwareFallbackTest`
   probes whether VP9 plays on this machine's device before injecting a failure
   into it. On the `macos-14` runner the device opens, decodes a few pictures,
   fails on its own, and the ladder falls to software — correctly — so the
   probe's "it started on the device" passed and the test's "one decoder all
   the way" did not. The sibling `HardwareDecodeTest` skips there, because its
   probe asks whether the device decoded, not whether it began.

## Decision

**A test that needs the runtime is given a headless one.** A new JUnit
extension in `:core`'s test fixtures, `dev.goldberry.junit.HeadlessRuntime`,
installs a `HeadlessBackend` before each test and shuts it down after — the
pair `:core`'s own tests write by hand in `@BeforeEach` and `@AfterEach`
(ADR-0019), as an annotation a test in another module can use. The five test
classes that mount a media widget carry `@ExtendWith(HeadlessRuntime.class)`.
The widget's `Goldberry.ui()` then reaches a loop that exists, with no window
and no native library; nobody pumps it, and the tests do not need it pumped,
because every media widget reads the player's status afresh on each build.

The widgets are not changed. A widget that follows a player belongs in a
running application, and `Goldberry.ui()` starting the runtime is the
documented behaviour every other caller relies on. Teaching the widget to
check for a runtime first would need a public "is the toolkit started" on
`Goldberry` and would silently change what a windowless tree does; a test
that wants a runtime should say so, as `:core`'s do.

**A picture golden is read and written in `java.base`.** `PictureGolden` uses
the golden harness's own `Png`, now public, the way `GoldenImage` already
does, and no longer touches `Image` at all. The toolkit's `PngEncoder` writes
the same shape — 8-bit RGBA, unfiltered, one `IDAT` — so the committed goldens
read unchanged. The S5 goldens are therefore checked on the Media workflow's
runners, where FFmpeg is; before this they were checked nowhere in CI.

**An upstream's archive carries the committed bytes.** The clone's attributes
are `* -export-ignore -export-subst -text`. `-text` outranks `core.autocrlf`
and `core.eol` on the archiving machine, so a tag archived on Windows is the
same tarball as on Linux. `UpstreamSourceTest` has a test that sets
`core.autocrlf=true` on the clone and expects LF, which fails on Linux without
the attribute and so guards it everywhere, not only on the runner that found
it.

**The hardware probe asks for the whole clip.** `assumeVp9OnTheDevice` skips
unless the probe run saw exactly one decoder, the device's, from the first
picture to the end. A device that gives the stream up on its own is the
environment the ladder exists for, and leaves nothing for an injected failure
to test.

**Two small fixes.** `WebViewPollingTest.unmount` tolerates a null tree, and
`BookMarkupTest` aborts the one sample that inflates through `libgoldberry`
with the same three errors `RendererRequirement` treats as "no library"
(ADR-0357). The other samples still inflate in the Java lane, and all of them
in the Linux verify lane, which runs `:example`'s tests against the library.

## Alternatives considered

- **Build `libgoldberry` in the Media workflow too.** Three to five minutes per
  target, a second toolchain in a job that has its own already, and a lane that
  would then test the same library the per-OS lanes test. The Media job tests
  FFmpeg; a PNG reader in `java.base` is the proportionate answer.
- **Skip the picture goldens where the library is absent.** A green tick over
  nothing (ADR-0016): no lane has both FFmpeg and `libgoldberry`, so the
  goldens would never run in CI.
- **Guard `Goldberry.ui()` in `FollowingState` and `MediaScreen`.** Rejected
  above: it adds API to `Goldberry` for a test's convenience and changes the
  behaviour of a windowless tree, which is a decision about the toolkit and
  not about its tests.
- **Install the headless runtime once per class, or once per JVM.** Per JVM
  would collide with any test that installs its own backend; per class leaves
  a runtime up across tests that close players in `@AfterEach`. Per test, with
  JUnit's guarantee that extension callbacks bracket the test's own
  `@BeforeEach` and `@AfterEach`, is the order `:core`'s tests already rely on.
- **Normalise line endings in `UpstreamSourceTest` instead.** The test would
  pass and the published tarball would still depend on which runner made it.
  ADR-0508's claim is "the source of those binaries"; that has to be one set of
  bytes.
- **Make `HardwareFallbackTest.playsOnTheDevice` tolerate a fall.** It would
  then not test what its name says. The probe is the right place to learn
  that the device cannot be relied on.

## Consequences

- The Snapshot workflow's five failing jobs pass with the library pointed at
  nothing locally, which is the Java lane's condition
  (`docs/testing.md` §5), and with FFmpeg present and `libgoldberry` absent,
  which is the Media lane's. The run on the next push is the confirmation.
- `HeadlessRuntime` is the way a test outside `:core` gets a runtime from now
  on; `docs/testing.md` §1.2 says so. `HeadlessRuntimeTest` covers it with two
  tests, the second of which can only run if the first's runtime was taken
  down.
- The S5 picture goldens are now verified in CI, on both Media runners.
- A `-text` attribute on every path means an upstream that one day marks a
  file with `eol=crlf` would have that conversion suppressed in the archive
  too. That is the committed bytes, which is what the classifier claims to be.
- `docs/snapshot-2026-10-01.md` is the working record: each failure, its cause
  and its fix, for the next time a lane goes red for a reason that is not the
  code under test.
