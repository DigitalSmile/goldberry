# 174. What both halves need is its own module

Date: 2026-08-23

## Status

Accepted. Answers the question
[ADR-0028](0028-the-start-up-timeline.md) left open in its last paragraph, and
finishes what [ADR-0023](0023-logging-and-the-example-as-a-subproject.md)
started. Relates to `docs/ARCHITECTURE.md` §15.

## Context

`Logs` and `Startup` are not native code. `Logs` exists so that every logger in
the toolkit is created *after* SLF4J's internal verbosity has been turned down,
which is what keeps a "No SLF4J providers were found" warning off the console of
an application that deliberately configured no logging (ADR-0023). `Startup`
records what the toolkit did before the first pixel, at trace (ADR-0028).
Neither has an opinion about foreign memory.

They lived in `io.github.digitalsmile.goldberry.natives.log` anyway, and the
reason was the module graph and nothing else. `:core` requires `:natives`, so
`:natives` is the lower of the two; shared code had to sit in the lower one or
in neither. The descriptor said so out loud:

> `exports ... to io.github.digitalsmile.goldberry.core` would say that precisely
> and does not compile: :core depends on :natives, so :core is not on the module
> path when this compiles […] So it is a plain export with a docstring that says
> what it is for.

ADR-0028 saw where that was going:

> The package is becoming the place where that compromise accumulates, and is
> worth watching.

It had accumulated. Seven files in `:natives`, thirteen in `:core`, one in
`:widgets` — twenty-one call sites, all reaching into a package named for a layer
that none of them is part of. And the ordering guarantee `Logs` exists for is
strongest exactly where it looks worst: `NativeLibrary` is usually the first
thing in the process to want a logger, so the class that quiets SLF4J has to be
visible to the native layer whatever else is true of it.

## Decision

**`:common` is a new module, below everything.** It requires nothing of
Goldberry's; `:natives` and `:core` both require it. `Logs` and `Startup` move
into it as `io.github.digitalsmile.goldberry.log`, and `:natives` stops exporting
a package it never owned.

```
:common ← :natives ← :core ← :widgets
   ↖________________________/
```

**`:core` names it directly** rather than taking it through `:natives`. It would
arrive either way — `requires transitive` on the chain would carry it — but a
graph that has to be traced through the native layer to explain why a widget can
log is a graph that still says logging is native. `:core` requires `:common`, and
reading the descriptor is enough.

**The bar for putting something here is that both halves need it and neither owns
it.** That is a narrow bar and it is meant to be: a module below the FFM boundary
is a module the boundary cannot protect, so the less in it the better.

## Alternatives considered

**Move `Logs` to `:core` and leave `Startup` behind.** The obvious cheap
version, and it splits a package in half. `Startup` cannot move — `NativeLibrary`
produces the first marks (`libgoldberry mapped`), and a timeline that begins
after the library is loaded is a timeline missing the part that takes longest.
So `:natives` would keep `Startup` and gain a private logger factory of its own,
which means the three lines that set `slf4j.internal.verbosity` exist twice. The
single ordering guarantee that is the entire point of `Logs` becomes two
guarantees that have to agree.

**Rename the package but leave the classes in `:natives`.** JPMS does not require
a package to match its module, so `io.github.digitalsmile.goldberry.log` could be
exported from `:natives` today, one line per file and no new artifact. Rejected
because it makes the descriptor lie more quietly rather than less: the classes
still ship in `goldberry-natives`, and an application that wants Goldberry's
logger factory still gets it by depending on the FFM bindings. It also sets up a
split package the day `:core` decides it owns `…goldberry.log` too, which is a
hard error rather than a warning.

**Move `NativePlatform` down as well.** It was the strongest other candidate —
"which OS and architecture am I" reads like something no layer owns. It is not:
`classifier()` and `libraryFileName()` exist to name a native artifact, and
`cLongSize()` is an ABI fact. `:core` mentions the class exactly once, in a
comment. It stays.

Nothing else in `:natives` qualified. Every remaining class that touches no
foreign memory — `BlendVersion`, `SdlException`, `HarfBuzzVersion`, `Insets`,
`NativeConstants` — is *about* a specific native library even when it does not
call into one.

## Consequences

**A sixth published artifact, `goldberry-common`**, holding two classes. That is
the cost, and it is the honest one: a module is what the Java platform gives you
to say "below both of these", and there is no lighter way to say it. It has no
dependencies but the SLF4J facade, so it adds nothing to a consumer's graph that
was not already there.

**`:natives` exports one package fewer**, and the paragraph of apology in its
descriptor is gone. What it exports now is wrapper packages and nothing else,
which is what `docs/ARCHITECTURE.md` §3.1 always claimed.

**Twenty-one imports changed, and one ADR aged well.** ADR-0028's closing
sentence is the reason this was easy to argue: the compromise was written down
when it was made, so the case for undoing it did not have to be reconstructed.

**A place for the next one to go.** The bar above is deliberately hard to clear,
but the next thing that clears it now has somewhere to be — which is the second
reason to pay for the module once rather than to keep renaming a package inside
`:natives`.
