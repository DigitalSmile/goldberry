# 443. Somebody else's log line is still a log line

Date: 2026-09-20

## Status

Accepted.

## Context

A Goldberry process on Linux prints this, and nothing in this repository put it
there:

```text
(java:1034459): libayatana-appindicator-WARNING **: 21:30:36.281:
libayatana-appindicator is deprecated. Please use libayatana-appindicator-glib
in newly written code.
```

That is `g_log_default_handler` writing to the process's stderr. It comes from
libayatana-appindicator, which SDL loads the moment `docs/core-widgets.md` §9's
`tray-icon` creates a tray, and which [ADR-0441] already names as the GTK 3 in
the process.

Read it as an application author. It has no level, so nothing can filter it. It
has no logger name, so nothing can route it. It does not reach the file the rest
of the logs are in. Its timestamp is in a different format from every other line
on the console. It names a library the application has never heard of and cannot
upgrade, about a deprecation it cannot act on — and it arrives looking exactly
like Goldberry shouting at it.

Worst of all, it appears on the console of an application that **deliberately
configured logging to be silent**. [ADR-0023] made the toolkit bind no SLF4J
provider precisely so that "nothing" can mean nothing; `Logs` goes to the
trouble of turning SLF4J's own missing-provider notice down to errors for the
same reason. A library underneath writing to fd 2 defeats both.

SDL has the same shape of problem and a smaller version of it: `SDL_Log` writes
to stderr too, and "no video driver could be initialized" is a line worth having
in a bug report rather than in a terminal that has scrolled.

None of this is GLib's fault or SDL's. Both are C libraries with no idea that a
logging framework exists in the process, and both offer a documented hook for
exactly this. Nothing had used either.

## Decision

**A native library's log messages are routed into SLF4J, on logger names an
application can configure.**

`native.<source>.<domain>` — `native.glib.libayatana-appindicator`,
`native.sdl.video`. Three segments, because all three are things somebody wants
to level separately, and hierarchical, so that `native` silences the lot:

```xml
<logger name="native" level="warn"/>
<logger name="native.glib.libayatana-appindicator" level="off"/>
```

Two hooks are installed, and the asymmetry between them is the whole of the
interesting part:

| Hook | Catches | Installed |
|---|---|---|
| `g_log_set_default_handler` | `g_log`, so `g_warning`, `g_message`, `g_critical`, `g_debug` | always |
| `g_log_set_writer_func` | `g_log_structured`, which a default handler never sees | opt-in |
| `SDL_SetLogOutputFunction` | everything SDL emits | always |

**`g_log_set_writer_func` aborts the process if it is called twice.** Not a
return code and not a warning: GLib calls `g_error`, which is fatal by
definition, when the writer is no longer the default one. Goldberry cannot know
whether an embedding application, a JNI library or WebKit itself has already set
one, and a toolkit that could kill its host process to redirect a log line has
made a bad trade. So the structured path is behind
`-Dgoldberry.log.glib.writer=true`, set by an application that knows its own
process, and the legacy path — which is where the message in the Context
actually comes from — is on for everyone.

The whole bridge is off under `-Dgoldberry.log.native=false`, which gives each
library its own stderr back. That is not a courtesy: a bridge is a filter, and a
message dropped by a logging configuration is one somebody debugging the
platform layer wanted.

### Where GLib is found, and when

GLib is neither ours nor optional-ours. It is the **system's**, it is in the
process because something else wanted it, and it cannot be exported from
`libgoldberry` — `exports/goldberry.symbols` is a version script over the
archives the superbuild statically links, and GLib is not one of them. So it is
`dlopen`ed by soname, `libglib-2.0.so.0`, and `ExportListTest` is taught that
this is a third library whose symbols are not that file's business.

And it is asked for **late**. A lookup by soname maps the library, so the bridge
is installed at the three points that are about to load GLib anyway — creating a
tray, and the two ways of opening a page — rather than at start-up. An
application with no tray and no page never maps GLib.

SDL's bridge is installed **before `SDL_Init`**, which is the point: the message
worth having most is written during initialisation.

### What it does not do

It does not change what either library **emits**. SDL keeps its own per-category
thresholds; `SDL_SetLogPriorities` would lower them and is deliberately not
bound, because how verbose SDL should be is not a decision a toolkit should make
on every application's behalf. What changes is the destination.

## Alternatives considered

**Redirect fd 2 and parse it.** Catches everything, including libraries with no
hook at all, and is the wrong shape in every other way: it would capture the
JVM's own crash output and anything the application writes to `System.err`, it
has to re-parse a format each library is free to change, and reassembling a
multi-line message out of a byte stream is guesswork. It also cannot recover the
level or the domain except by matching on prose.

**Set `G_MESSAGES_DEBUG` or GLib's environment variables.** These control what
GLib prints, not where. The line still goes to stderr.

**Do nothing and document it.** Which is what was happening. The message is
already documented — `Webview.open` explains libayatana-appindicator's GTK 3 in
as many words — and documentation does not get it out of the console of an
application that asked for silence.

**Install only the writer function**, which is the modern GLib API and catches
both paths. Rejected on the abort: one process in the wild where something else
has set a writer is one process that dies at start-up, and the failure mode is a
`SIGABRT` with a GLib message rather than anything a user could act on. The
legacy handler catches the messages that actually occur and cannot fail this
way.

**Put the bridge in `:core`.** The hooks are FFM bindings and belong to the
native layer. What is in `:common` is the destination and the naming convention
alone — two classes that depend on nothing but SLF4J — which is exactly the bar
[ADR-0174] sets for that module.

## Consequences

**A platform message is now an ordinary event.** It can be levelled, routed to a
file, switched off by name, correlated by timestamp with the frame that caused
it, and included in a bug report. The showcase's `logback.xml` shows the
libayatana-appindicator line arriving as a `WARN`, which is the demonstration.

**Three upcall shapes were added**, and `ForeignSurface` names them so a native
image is told about them before a tray exists. `GlibLog` is the first owner in
that list to declare two.

**One struct is laid out by hand and is not on the layout table.** `GLogField`
is three machine words and [ADR-0010]'s rule is that such a layout is checked
against what the target's own C compiler computed — and it cannot be, because
GLib's headers are not a dependency of the superbuild and must not become one
for the sake of a log line. What makes it acceptable here: every field is read
and none written, GLib has published the struct unchanged since 2.50, the
segments are reinterpreted with a bound before anything is read out of them, and
nothing reaches it at all unless an application opted into the writer. A wrong
offset is a garbled message, not corrupted memory. It is the only exception in
the module and it is not a precedent.

**The native ABI is 15**, because `SDL_LogPriority` and `SDL_LogCategory` are on
the layout table now. They are ordinals in enums SDL has already renumbered once
— `SDL_LOG_PRIORITY_TRACE` was inserted at 1, below `VERBOSE`, moving everything
above it — and a binding that predates the insertion reports every message one
rung too loud with nothing anywhere to say so.

**A message can now be lost that used to be unmissable.** An application whose
root logger is at `error` will not see the deprecation notice, and that is the
point rather than a regression — but it is a real change, and
`-Dgoldberry.log.native=false` is the way back.

**The bridge must never throw.** Every entry point is an FFM upcall called from
C, sometimes with a GLib lock held and sometimes on the way to `abort()`. Both
handlers catch `Throwable` and so does `NativeLogBridge.log`. That is two bare
catches in a codebase that has almost none, and they are correct: a logging
bridge that can take the process down is worse than the stderr line it replaced.

**Only GLib and SDL are covered.** WebKit's own logging, D-Bus's and Wayland's
are not, and each would be its own hook. The shape is now there for them.

[ADR-0010]: 0010-hand-written-ffm-bindings.md
[ADR-0023]: 0023-logging-and-the-example-as-a-subproject.md
[ADR-0174]: 0174-what-both-halves-need-is-its-own-module.md
[ADR-0441]: 0441-a-web-page-is-a-window-not-a-box.md
