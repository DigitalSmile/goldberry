# ADR-0023: Log through SLF4J, bind nothing; and fold the example back in

- **Status:** Accepted (supersedes the build arrangement in [ADR-0021](0021-the-example-is-a-separate-build.md))
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §15, [ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md), [ADR-0021](0021-the-example-is-a-separate-build.md)

## Context

Two things, related only in that they both concern what a consumer of Goldberry
sees.

**The example was invisible to Gradle.** ADR-0021 made `example/` a separate
build so it would consume Goldberry through published coordinates. The reasoning
holds and the cost was higher than estimated: `./gradlew projects` did not list
it, IDEs did not import it, and `./gradlew run` failed with "task 'run' not
found". The workaround — a root task shelling out to the example's own wrapper —
worked and did not fix the underlying problem, which is that a directory in the
repository was not part of the project as far as any tool was concerned. That was
reported twice.

**Nothing logged.** A UI toolkit that fails to find a display, loads the wrong
native library, or is handed a surface format it cannot blit into should be able
to say so. Until now the only diagnostic was an exception message, and only for
the failures fatal enough to throw.

The constraint on logging is specific: **an application that configures no
logging must see nothing at all**. SLF4J 2 does not cooperate by default. With no
provider on the path it prints

```text
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

to stderr on first use — noise on the console of every application that made a
deliberate choice, and noise that reads like a complaint from Goldberry rather
than from a library beneath it.

## Decision

**Goldberry logs through SLF4J and binds no implementation.** slf4j-api is an
`api` dependency of every module; no provider is a dependency of any of them.
Choosing one is the application's call, and a library that ships a backend takes
that choice away — and starts a fight with whatever the application already uses.

**The no-provider warning is silenced at source.** `Logs` sets
`slf4j.internal.verbosity=ERROR` in a static initializer, and every logger in the
toolkit comes from `Logs.of(...)` — which is what guarantees the ordering, since
that initializer runs before SLF4J's `Reporter` reads the property. It is set
only if the application has not, so `-Dslf4j.internal.verbosity=INFO` still shows
everything SLF4J has to say.

Levels are conventional and worth stating so they stay that way: **info** for
things a user would want in a bug report (which library loaded from where, which
SDL, a window opening or closing, a scale change), **debug** for the toolkit's
own lifecycle (runtime start, buffer allocation, window creation flags),
**trace** for per-frame detail, **error** only where something was swallowed.

**`example` is a subproject again.** `include ':example'` in `settings.gradle`,
`implementation project(':core')`, no second wrapper, no dependency
substitution. Gradle lists it, IDEs import it, `./gradlew run` finds its `run`
task the way `./gradlew test` finds every module's tests.

What ADR-0021 wanted is kept where it can be kept without a separate build: the
example is still a **module**, still runs on the **module path**, and still
declares `--enable-native-access=io.github.digitalsmile.goldberry.natives`. Those
are what caught real problems — an unexported package and a wrong module name
both fail here and nowhere else.

## Alternatives considered

**Ship `slf4j-nop` as a runtime dependency.** It removes the warning without
touching a system property, and it is what several libraries do. It also *is* a
provider, so an application adding Logback gets SLF4J's multiple-bindings warning
instead — trading a warning nobody asked for against a worse one that appears
only for users who did the right thing.

**`java.util.logging`, or `System.Logger`.** In the JDK, no dependency, and
`System.Logger` is the modern answer for exactly this. Rejected because every
Java application that logs at all already has an SLF4J bridge configured, and
`System.Logger` output lands in whatever `java.util.logging` is doing by default
— which is usually not where the application's other logs go.

**Leave the warning.** It is two lines, once, and it is arguably SLF4J's business
rather than Goldberry's. It also appears on the console of every application that
deliberately configured nothing, and the user's requirement here was explicit.

**Keep the example as a separate build and document the friction.** The
coordinate check it provided is real and is now lost: nothing verifies that the
published artifact is called `goldberry-core` rather than `core` until somebody
publishes. That is a genuine regression, recorded below, and the price of a
project layout that tools understand.

## Consequences

An application sees exactly the logging it asked for and nothing else. One that
adds Logback gets Goldberry's diagnostics for free; one that adds nothing gets
silence, including from SLF4J itself.

Goldberry sets a system property it does not own. It is scoped to SLF4J's
internal reporting, applied only when unset, and named in one place with a test
asserting the string — but it is a global side effect of loading a class, and
that is worth knowing about.

`Logs` is exported unqualified from `:natives`. A qualified `exports ... to
io.github.digitalsmile.goldberry.core` would say what is meant and does not
compile: `:core` is not on `:natives`' compile module path (it depends the other
way), so javac warns the target module is unknown and `-Werror` makes it fatal.
The docstring carries the intent the module system cannot.

The published-coordinates check is gone. `implementation project(':core')` does
not care what the artifact is called, so a mismatch between §15's
`goldberry-core` and the project name `core` will surface at publishing time
rather than now. Publishing is not configured yet; when it is, it should assert
the coordinates.

The example's tests and the toolkit's now run in one command, and a broken
showcase breaks `./gradlew build`. That is the intended coupling — a showcase
that does not compile is a broken API — and it does mean the example can no
longer be left temporarily broken while the toolkit moves.
