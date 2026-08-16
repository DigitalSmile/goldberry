# ADR-0041: Three platforms, four artifacts, two backends

- **Status:** Accepted
- **Date:** 2026-08-16
- **Amends:** [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)
- **Relates to:** `docs/ARCHITECTURE.md` §1, §3.2, §4, §15, §16

## Context

Goldberry's scope has carried three commitments that were written down before
anything ran, and each of them costs something on every commit.

**A third backend.** ADR-0003 listed three implementations behind the `Backend`
SPI: `sdl3`, `headless`, and one for an OS compositor that does not exist yet in
any form this repository can build or test against. It appeared in the layer
diagram, in the SPI's own javadoc, in the damage-rect documentation, in the
keyboard-translation notes, in the theme layer, in the popup and tray designs,
and in the M4 milestone — nine places in the design document alone, all of them
describing a consumer that has never linked. The `--os-*` theme variables existed
solely for it.

**Six native artifacts.** Two of them, `windows-aarch64` and `macos-x64`, exist
by cross-targeting inside a native toolchain: MSVC at `-A ARM64`, Xcode at
`CMAKE_OSX_ARCHITECTURES=x86_64`. Neither has ever been built — the Windows
workflow has never run at all, and the macOS one runs both legs. Both are rare
in the audience this toolkit is for. Windows on ARM is a small and shrinking
share of Windows desktops; Intel Macs are a shrinking share of Macs and the last
one shipped in 2023. Each costs a CI leg on every push, a row in every matrix
document, and — the part that actually bites — a second artifact to explain when
the export machinery breaks differently on it.

The cost is not the compute. It is that every one of these appears in prose,
and prose about something nothing exercises drifts into fiction. Three of the
open questions in `book/src/status.md` were about paths that had never run.

## Decision

Goldberry targets **Linux, macOS and Windows**, and nothing else.

The backend list is **two**: `sdl3` for all three desktop platforms, `headless`
for tests. The compositor backend is removed from the SPI's contract, the layer
diagram, the milestone ladder and the theme layer. ADR-0003's reasoning is
untouched — SDL3 remains the permanent desktop windowing layer and the SPI still
exists to keep the platform boundary in one place, which `headless` alone is
enough to enforce.

The distribution matrix is **four rows**:

| Target | Runner | Output |
|---|---|---|
| `linux-x64` | `ubuntu-24.04` + `manylinux_2_28_x86_64` | `libgoldberry.so` |
| `linux-aarch64` | `ubuntu-24.04-arm` + `manylinux_2_28_aarch64` | `libgoldberry.so` |
| `windows-x64` | `windows-2022` | `goldberry.dll` |
| `macos-aarch64` | `macos-14` | `libgoldberry.dylib` |

One runner per artifact, and no cross-targeting anywhere. ADR-0012's mechanism —
native runners plus a manylinux container for the glibc floor — is unchanged and
is precisely what a dropped row would come back through.

`NativePlatform` enforces the matrix rather than describing it. Its compact
constructor refuses `macos-x64` and `windows-aarch64`, so the pair fails where it
is named, with a message that lists the four rows. The alternative was an
`UnsatisfiedLinkError` from `NativeLibrary` three layers later, naming a resource
path instead of a decision.

## Alternatives considered

- **Keep the compositor backend as a documented aspiration.** Rejected. The
  problem was never that it might be built; it is that nine paragraphs of design
  document described how it would behave, and none of it could be checked. The
  SPI is the thing worth keeping, and it survives intact — a backend that arrives
  later arrives against the same interface, which is the whole point of having
  one.
- **Keep the two rare targets but stop testing them.** Rejected: an artifact
  published to Maven Central that CI does not verify is worse than no artifact.
  A user who downloads `goldberry-natives-macos-x64` has a right to expect it
  loads.
- **Keep `macos-x64` only, since Intel Macs still exist in the wild.** The
  strongest of the three, and rejected on the same grounds as the other:
  Rosetta 2 does not help, because an x86_64 JVM on Apple Silicon would need this
  artifact and an aarch64 JVM would not, so the row buys nothing on current
  hardware and only serves machines Apple stopped selling.
- **Let `NativePlatform` keep accepting all six pairs and fail at load.**
  Rejected: `classifier()` would keep producing `macos-x64`, a string that now
  names nothing, and the error would surface as a missing resource rather than as
  an unsupported platform.

## Consequences

- Two CI legs disappear. The Windows workflow becomes a single job, and the
  macOS one stops building a dylib nobody downloads.
- **`NativePlatform` can now throw where it previously could not.** Constructing
  the record — not just `of()` — validates the pair. Any code that built a
  platform to ask a question about it, rather than to load a library, has to
  stop at the two removed pairs. There is no such code today; the tests assert
  both directions so there cannot be tomorrow without noticing.
- **An Intel Mac and a Windows-on-ARM machine can no longer run Goldberry**, and
  the failure is at start-up with a clear message rather than at link time with
  an opaque one. That is the intended trade and it is a real loss for those
  users.
- Reversing either row is cheap and stays cheap: the cross-targeting flags are
  recorded in ADR-0012's amendment note and in the workflow comments, one
  `nativeTargets` entry and one `switch` arm bring a row back.
- Reversing the backend cut is more expensive — the design document no longer
  describes shm buffers, dmabuf acceptance, or `--os-*` theme mapping, and that
  prose is gone rather than parked. The SPI it would attach to is not.
- The milestone ladder's M1 criterion becomes measurable. It used to name a
  specific laptop model and a compositor that does not run yet; "60 fps on
  Linux, macOS and Windows" names three things CI can be pointed at.
