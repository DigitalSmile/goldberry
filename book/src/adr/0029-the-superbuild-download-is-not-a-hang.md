# ADR-0029: The superbuild download is not a hang

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §3.2, [ADR-0008](0008-superbuild-before-the-vertical-slice.md), [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)

## Context

The first `./gradlew build` on a fresh checkout was reported as hung, and killed.
It was not hung. It was cloning.

The superbuild fetches five upstreams — Blend2D, AsmJit, Yoga, HarfBuzz and SDL3
— with `FetchContent`. Shallow, but still about 330 MB of working tree, of which
HarfBuzz alone is 161 MB and SDL3 116 MB. On the machine that prompted this
record that was three minutes, and CMake printed *nothing* for the whole of it.
Two defaults conspire:

- `FETCHCONTENT_QUIET` is `TRUE` by default, which swallows the populate step's
  output.
- `git clone` writes no progress when stdout is not a terminal, and under
  Gradle's `Exec` it never is.

So the console showed `> Task :natives:cmakeConfigure` and sat there. There is no
way for a newcomer to distinguish that from a deadlock, and the reasonable
response — Ctrl-C — is the one that guarantees the next attempt starts over.

It started over more often than it needed to for a second reason. `FetchContent`
puts its clones in `_deps` under the CMake binary directory, which here lives
inside `natives/build/`. `./gradlew clean` is therefore a 330 MB re-download, and
nothing said so.

Measured afterwards on that same machine, with the sources already present:
configure 32.8 s, compile and link 27.1 s. The build is a minute of work behind a
download that can be five times longer than it and says nothing.

## Decision

Make the download audible, and stop discarding it.

**Audible.** `FETCHCONTENT_QUIET FALSE` in the superbuild's `CMakeLists.txt`, and
`GIT_PROGRESS TRUE` on every declaration — the latter passes `--progress`, which
is what makes git report into a pipe. Before the silence rather than after it,
`cmakeConfigure` logs at lifecycle what is about to happen, how big it is, where
it is going, and roughly how long it takes. That message prints only on a cold
cache, so it is information the first time and not noise the twentieth.

**Kept.** Gradle points `FETCHCONTENT_BASE_DIR` at `natives/.deps/<target-id>`,
outside `build/`, so `clean` no longer touches it. `:natives:cleanNativeDeps`
discards it on purpose. Per target rather than one shared directory, because the
base directory also holds the generator-specific `<name>-build` and
`<name>-subbuild` trees; two targets sharing one would fight over them.

Two corrections came out of the same reading:

**The pinned refs are task inputs now.** They reached CMake as `-D` arguments but
were declared nowhere, so `cmakeConfigure` was up to date after a version bump in
`gradle/libs.versions.toml` and the build quietly kept building the old revision.
They are an `inputs.property` each.

**`cmakeConfigure` no longer declares the whole work directory as its output.**
That directory contained `_deps` — 7,597 files — and Gradle fingerprinted all of
it on both sides of every invocation. Its real outputs are `CMakeCache.txt` and
`build.ninja`. Moving `_deps` out of `build/` shrinks the tree anyway; declaring
the two files that actually mean "configured" is the honest description.

## Alternatives considered

**Leave it and document it in the README.** The README already says the native
build needs CMake, Ninja and a toolchain. It did not say the first run downloads
a third of a gigabyte in silence, and adding a sentence would help only the
people who read documentation before their first build — not the people who have
already hit Ctrl-C. The fix belongs where the silence is.

**Vendor the sources, or commit a lockfile of tarballs.** Removes the clone
entirely and makes builds reproducible offline. It also puts 330 MB of other
people's code in the history, and ADR-0012 already fixes reproducibility at the
pinned-ref level. Not worth the repository weight for a one-time cost.

**Release tarballs instead of git clones.** Materially smaller than a shallow
clone for HarfBuzz and SDL3, whose bulk is test fixtures. It does not work for
Blend2D and AsmJit, which are pinned at `master` and so have no tarball; a
superbuild that fetched two upstreams one way and three another would be harder
to read than what it saves. Worth revisiting when those two get pinned to
releases.

**A shared cache across targets, in `~/.cache` or the Gradle user home.** One
copy per machine instead of one per checkout. Rejected for the `-build` and
`-subbuild` collision above, and because a cache outside the working tree is a
cache people forget they have — `natives/.deps/` is visible next to the thing it
belongs to, and `.gitignore` covers it.

**Turn the whole thing off by default and make the native build opt-in.**
`-Pgoldberry.skipNative=true` already exists for Java-only contributors. Making
it the default would mean the ordinary `./gradlew build` no longer tests what it
claims to test, which is the trade ADR-0012's wiring deliberately made the other
way.

## Consequences

The configure step is noisier. It prints git's progress meter and a paragraph of
explanation on a cold cache, where before it printed one line. That is the point,
and it is the cost: anyone parsing configure output now has more to skip.

`clean` no longer means clean. `natives/.deps/` survives it, which is a small
surprise in the other direction — mitigated by naming `cleanNativeDeps` in the
message that creates the cache, but a surprise nonetheless. A stale cache is
still correct, because `FetchContent` re-checks the ref against the recorded
stamp; it only costs disk.

Version bumps in `gradle/libs.versions.toml` now actually re-configure. That is a
bug fix, and it means the next bump will be slower than the last one was — it
will do the work it was previously skipping.

The three-minute figure is one machine on one connection, and the message says
"typically a few minutes" rather than a number, because the honest answer is that
it depends on the link. What is not machine-specific is the ratio: the download
dominates, and the build everyone assumes is slow takes 27 seconds.
