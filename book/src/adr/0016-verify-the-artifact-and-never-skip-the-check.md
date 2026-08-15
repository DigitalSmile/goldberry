# ADR-0016: Verify the downloaded artifact, and never skip the check in CI

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §15, [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)

## Context

CI ran for the first time on 2026-08-15. The native jobs succeeded on both Linux
targets — `libgoldberry.so` built inside the manylinux container on x64 *and*
aarch64 — and both `Verify layouts` jobs failed, after 14 and 34 seconds. Nothing
was verified.

The verify jobs exist because building an artifact is not the same as testing it
(ADR-0012). They download the library the native job produced and run
`:natives:test` against it, which is where the hand-written struct layouts are
checked against the compiled library — the check ADR-0010 rests on.

Two mistakes met in the middle.

The first: `:natives:test` depended on `cmakeBuild`. That wiring exists so a
local `./gradlew build` verifies against a real library instead of skipping, and
it is right for a developer machine. On a verify runner it is exactly wrong.
Those runners have no C/C++ toolchain and are not meant to — the whole point of
the split is that the library is built once, on a runner chosen for its glibc,
and tested elsewhere. So `checkToolchain` failed before a single test ran. That
is the 14 seconds.

The second was worse, and would have outlived the first. The `test` task set
`goldberry.native.library` unconditionally to the host's install directory. A
system property set on the task wins over one passed to the Gradle JVM with
`-D`, so the downloaded artifact was never going to be read: the tests would
have looked for a library at a path that does not exist on that runner, found
nothing, and — because `LayoutVerificationTest` skips when no library is
loadable — reported three skipped tests and a green job.

A red job that verified nothing is a nuisance. A green job that verified nothing
is a lie, and it is the one that survives, because nobody investigates a passing
build. Fixing only the toolchain failure would have converted the first into the
second.

## Decision

Handing Gradle a library to verify is also what tells it not to build one:
when `goldberry.native.library` is set, `:natives:test` no longer depends on
`cmakeBuild`, and the supplied path is what the tests load. One flag carries both
halves of the intent, so the two cannot drift apart.

And `goldberry.native.required=true` makes a missing library a failure rather
than a skip. CI passes it on every verify job. A test that needs native code
still skips on a contributor's machine — a Java-only change should not require a
C++ toolchain and twenty minutes of superbuild — but in the one place whose only
purpose is to run that check, not running it is a failure.

Both properties are accepted as either `-D` or `-P`.

## Alternatives considered

**Pass `-Pgoldberry.skipNative=true` to the verify jobs.** It would have fixed
the toolchain failure with a flag that already existed. It says the wrong thing —
the job is the most native thing in the pipeline — and it leaves the property
override in place, so the job would have gone green having verified nothing. It
treats the symptom that shouts and leaves the one that whispers.

**Install a C/C++ toolchain on the verify runners.** Then the job would build
its own library and test that. It would pass, and it would be meaningless: the
artifact under test would be one built outside the manylinux container, against
the wrong glibc, by a different compiler — not the binary that ships. This
inverts the reason the jobs are split at all.

**Drop the skip and always fail when the library is absent.** Simpler: one
behaviour everywhere, no flag. It also means a contributor fixing a typo in
`NativePlatform` cannot run `./gradlew build` without CMake, Ninja, Meson and a
dozen X11 development headers. The skip is a real convenience for a real person;
what it lacked was a way to say "not here".

**Assert on the test report in a CI step** — fail the job if the skip count is
non-zero. This works, and it puts the rule in YAML, three workflow files away
from the test it governs, where a new verify job would forget it. The rule
belongs next to the assumption it overrides.

## Consequences

The verify jobs test the binary that ships, and cannot pass without loading it.

`:natives:test -Dgoldberry.native.library=<path>` is now the supported way to
check a library built anywhere else — a downloaded CI artifact, a colleague's
build, a hand-configured CMake tree — and it no longer starts a twenty-minute
superbuild to do so.

The cost is a third build property, and a new way to be wrong: passing
`goldberry.native.library` and expecting a native build. The build logs which
library it is verifying when it declines to build one.

The stronger guarantee is still missing. `required=true` proves the tests ran
against *a* library; it does not prove it was the downloaded one. If the download
step silently produced nothing, `isAvailable()` is false and the job fails — the
outcome we want, by luck rather than by design. Binding the artifact's identity
to the check waits for something to bind it to, most likely the build fingerprint
`goldberry_abi_version` already implies.
