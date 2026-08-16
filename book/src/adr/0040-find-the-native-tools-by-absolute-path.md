# ADR-0040: Find the native tools by absolute path

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §3.2, [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md), [ADR-0038](0038-the-superbuild-download-is-not-a-hang.md)

## Context

A build failed like this:

```
> Task :natives:checkToolchain
Native toolchain OK; host target is macos-aarch64.

> Task :natives:cmakeConfigure FAILED
> A problem occurred starting process 'command 'cmake''
  Exec failed, error: 2 (No such file or directory)
```

Two tasks, one second apart. The first ran `cmake --version` successfully and
said so. The second could not find `cmake`. It was installed, executable, and on
the `PATH` the build itself printed — `/opt/homebrew/bin/cmake`, a Homebrew
symlink, mode `r-xr-xr-x`.

The two tasks look the executable up differently, and that is the whole bug.

`checkToolchain` probed with a bare `ProcessBuilder` and no environment
override. That ends in `execvp`, which searches the `PATH` the child inherits —
the JVM's own. Gradle's `Exec` passes an explicit environment map to every
process it starts. That switches the JDK to `execvpe`, which searches
`parentPathv`: a copy of `PATH` taken from the JVM's **native `environ` at
start-up**, and never refreshed.

In an ordinary program those two are the same string. In a Gradle daemon they
are not. The daemon outlives the shell that started it and serves builds
launched from anywhere, so Gradle rewrites what `System.getenv()` reports to
match the *current* client. It cannot rewrite the native `environ` underneath,
and it certainly cannot rewrite a snapshot the JDK took before Gradle's own
classes loaded.

The daemon on the machine that prompted this record had been started by IntelliJ
IDEA. Reading its real environment:

```
PATH=/usr/bin:/bin:/usr/sbin:/sbin
```

launchd's default, with no Homebrew in it — while the same daemon reported the
full shell `PATH` to the build. Every `Exec` in the build was therefore
resolving names against launchd's four directories, and had been since the
daemon started.

Bisecting `ProcessBuilder` inside that daemon confirms it exactly:

| working directory set | environment map set | result |
|---|---|---|
| no | no | finds cmake 4.3.4 |
| yes | no | finds cmake 4.3.4 |
| no | **yes** | `Exec failed, error: 2` |
| yes | **yes** | `Exec failed, error: 2` |

The environment map is the trigger. The working directory is innocent.

Three things make this worse than an ordinary missing tool:

- **The error names a file that exists.** Every obvious check a developer runs —
  `which cmake`, `cmake --version`, `ls -l` — passes. So does Gradle's own
  toolchain check, one line above the failure.
- **`./gradlew --stop` fixes it,** which makes it look intermittent. The next
  build from a terminal starts a daemon with a good `PATH`; the next build from
  the IDE starts one with launchd's.
- **`checkToolchain` was actively misleading.** It exists to fail early with
  instructions, and here it certified a toolchain that the very next task could
  not use. A check that disagrees with the thing it is checking is worse than no
  check, because it is believed.

That last point is the one worth generalising. The check and the use were asking
two different layers of the JDK the same question and getting two different
answers. Nothing about a `PATH` was going to keep them in step.

## Decision

Resolve every native tool to an absolute path, once, and have both the check and
the use run *that*.

`execvpe` only consults `parentPathv` for a name with no `/` in it. An absolute
path skips the lookup entirely, so the stale snapshot stops mattering — not
worked around, not made less likely, but removed from the code path.

**`ToolResolver`, in `build-logic`.** Ordinary Java with unit tests, for the
reason `:assets` is a module and not a script (ADR-0033): locating a tool across
three platforms is real logic, and this failure is not one anybody reproduces by
reading. It searches the client's `PATH` first — read through
`providers.environmentVariable`, which is the accurate one — and then the
directories the supported installers actually use: `/opt/homebrew/bin`,
`/usr/local/bin`, `/opt/local/bin`, `/Applications/CMake.app/Contents/bin`,
`~/.local/bin` for `pip install --user`, and the Windows equivalents. `PATH`
wins, because a contributor who put a newer CMake ahead on their `PATH` meant
it.

**`natives/build.gradle` resolves `cmake`, `ninja` and `meson` at configuration
time**, because `Exec.commandLine` needs a value then. A tool that is absent
resolves to `null` rather than failing there; `checkToolchain` still owns that
error and now says considerably more.

**`-DCMAKE_MAKE_PROGRAM` is passed explicitly.** Otherwise the same disagreement
reappears one level down: `checkToolchain` finds a Ninja in a conventional
directory, and CMake — searching only the `PATH` — does not.

**`-Pgoldberry.cmake=<path>`** (also `-D`, also `ninja` and `meson`) overrides
the search. Taken at its word, and failing if it is wrong, rather than silently
falling back to a copy on the `PATH`: an override that can resolve to something
else is not an override.

**`checkToolchain` prints what it found.**

```
Native toolchain OK; host target is macos-aarch64.
  cmake  4.3.4    /opt/homebrew/bin/cmake
  ninja  1.13.2   /opt/homebrew/bin/ninja
```

When a machine has two CMakes — a system one, a Homebrew one, a pip upgrade
shadowing both — *which one did it use* is the first question, and the answer
costs a line. It also makes the version floors honest: they now measure the
executable the build will run rather than whichever copy a bare-name lookup
reached.

Verified by rebuilding under the failing condition itself, with
`PATH=/usr/bin:/bin:/usr/sbin:/sbin` and nothing else — a stricter test than the
original, where the client `PATH` was at least correct. Configure, compile, link
and install all succeed.

## Alternatives considered

**Tell people to run `./gradlew --stop`, or to launch the IDE from a shell.**
The actual advice given for this class of bug, and it works. It is also advice
that has to be given again every time, to everyone, for a failure whose message
points at a file that is present — and it leaves the build's own toolchain check
lying. Fixing the daemon's environment fixes one machine until the next GUI
launch; fixing the lookup fixes the build.

**Set the `Exec` tasks' `environment['PATH']` explicitly.** One line, and it
would have worked here. It does not survive contact with a `PATH` that is
genuinely missing the tool — a `pip install --user` cmake, or a CMake.app
install — and it leaves `execvpe`'s snapshot in the path, so the next symptom is
the same symptom. Resolving to an absolute path is strictly more of a fix for
about the same amount of code.

**Use a CMake toolchain plugin from the Gradle plugin portal.** Handles
discovery, and rather more besides. ADR-0012's superbuild wiring is deliberately
hand-written so that CI can invoke CMake directly on runners with no JDK, and a
plugin that owns the invocation makes that harder rather than easier. Not worth a
dependency to replace one resolver.

**Fail `checkToolchain` when the daemon's `environ` and `System.getenv()`
disagree.** Detects exactly this situation and explains it, which is tempting.
But it diagnoses a condition that no longer breaks anything once the tools are
resolved absolutely, and it would fire on daemons that are working perfectly
well. A build that refuses to run because of something it has already handled is
a worse experience than the one it replaced.

**Put the resolver in `natives/build.gradle` as a closure.** Where the previous
probing lived, and no new files. It is also the one piece of this build that is
worth a test — three platforms, symlinks, executable bits, empty `PATH` entries
— and Groovy in a build script is the one place in this repository where a test
cannot reach.

## Consequences

`natives/build.gradle` no longer runs a tool it cannot name. That is the point,
and the cost is one more indirection between reading the build file and knowing
what gets executed: `commandLine` now shows a variable where it used to show
`'cmake'`.

**The conventional-directory list is a maintenance surface.** It is a list of
places that were true when it was written. A new installer, or a Homebrew prefix
change, means editing it — mitigated by `-Pgoldberry.cmake`, which is the escape
hatch for exactly that, and by `PATH` still coming first, which means the list is
only ever consulted when the `PATH` has already failed.

**`build-logic` has tests now, and `:natives:check` runs them** through
`gradle.includedBuild('build-logic').task(':test')`. A separate build's tests do
not run just because this one does, and a test nobody runs is a test that does
not hold. It also means `check` now depends on a second build, which is a link
that did not exist before.

**A tool found somewhere the `PATH` never mentioned is a slight surprise.** A
contributor who deliberately removed Homebrew from their `PATH` will still get
Homebrew's CMake. The alternative is failing on a machine that has a perfectly
good toolchain installed, which is the failure this record is about;
`checkToolchain` printing the absolute path is what keeps the surprise visible.

**`CMAKE_MAKE_PROGRAM` is pinned in the CMake cache** to the Ninja resolved at
configure time. Moving Ninja now needs a reconfigure rather than being picked up
silently — which is the same trade the absolute `cmake` makes, and the same
answer: a build that changes tools without saying so is the harder problem.

## Addendum, 2026-08-16: the superbuild ran a different meson

This record's own failure mode survived one layer below where it was fixed.
`checkToolchain` resolved meson to an absolute path, checked its version against
libxkbcommon's floor, and printed it — and the superbuild then ran a bare
`meson`, because `CMakeLists.txt` spelled the `ExternalProject` commands that
way. Three processes down from Gradle, through CMake and Ninja, that resolves
against whatever `PATH` the chain happens to carry.

The symptom was the shape this record describes: `checkToolchain` printing
"meson 1.9.1 /home/…/meson19" immediately followed by the build failing on meson
1.3.2's build directory. The check and the use were asking different things.

Fixed the same way as Ninja: Gradle passes `-DGOLDBERRY_MESON=<absolute path>`
at configure time, and the `ExternalProject` uses it, falling back to a bare
`meson` only for a CI leg that drives CMake directly. **The general lesson is
narrower than "use absolute paths": it is that any tool named inside a
*generated* build — a CMake command, a Ninja rule, a script — is outside the
reach of a check that only looks at what Gradle itself will spawn.**

Two other things were found while getting there and are worth writing down:

- The message this check produces when a tool *is* too old had never run.
  `checkToolchain` built it with a `+` at the start of a continuation line,
  which Groovy reads as unary plus on a `String` — so the branch that was meant
  to say "meson 1.3.2 is too old, here is how to upgrade" threw
  `No signature of method: java.lang.String.positive()` instead. A helpful
  error path that nothing exercises is not a helpful error path.
- Upgrading meson is not enough on its own: an `ExternalProject` build directory
  configured by an older meson has to be removed, because meson refuses a
  `build.dat` written by a version it does not recognise. That is upstream
  behaviour and correct; it is recorded because the error names a file rather
  than a cause.
