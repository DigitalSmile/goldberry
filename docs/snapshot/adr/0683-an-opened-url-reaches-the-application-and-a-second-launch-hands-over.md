# ADR-0683: An opened URL reaches the application, and a second launch hands over

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0291 (a URL scheme is packaging), which it partly supersedes; ADR-0003 and ADR-0041 (one platform-facing interface, SDL3's)

## Context

ADR-0291 declined deep links, single-instance handoff and the OS keychain. It
split a deep link into three problems (registration, delivery, handoff) and
gave all three to the application, on two grounds. Registration is
packaging. Delivery and handoff are the application's process model.

Two things changed:

- ADR-0291 promised that "a macOS deep link will fall out of" binding the drop
  events. The drop events are bound now, but SDL delivers an opened URL (and a
  document opened from Finder) as a `DROP_FILE` with window 0. `Sdl3Backend`
  skipped any event whose window it did not know, so the URL was discarded.
  The promise was broken.
- A second application asked. ADR-0291 reserved the decision for that case,
  and on 2026-11-08 the owner reversed it for delivery, handoff and the
  keychain. The keychain is its own record.

Registration stays as ADR-0291 says: an installer writes it, and a library
cannot install itself.

## Decision

**Delivery.** A `DROP_FILE` with no window becomes `BackendEvent.OpenUrl`. It
names whichever window is open, because every event names one, and the
runtime hands it to the launcher before looking a window up. The launcher
calls the new **`Application.onOpenUrl(URI)`** on the UI thread. A file
arrives as a `file:` URI (`OpenTargets`): a URL needs a scheme of two letters
or more, so `C:\…` is a path, and a path is opened only when the file is
there.

**Handoff.** **`dev.goldberry.instance.SingleInstance`** does in plain Java
what ADR-0291 sketched for the application to write itself:

- a `FileLock` in a directory of the application's own, under
  `XDG_RUNTIME_DIR` or the temporary directory with the user's name;
- a Unix-domain socket beside the lock;
- a second launch that writes its command line, length-prefixed UTF-8, to the
  socket and is told to exit.

`onArguments(executor, listener)` hands each command line over on the
caller's executor, and keeps any that arrived before a listener did. Two claims
in one JVM are reported as "held", not as an `OverlappingFileLockException`.

**`Application.singleInstance()`**, an id or empty, makes the launcher claim it
before any window opens. A second launch opens nothing. The running copy
hears each argument that is a URL or an existing file through `onOpenUrl`;
flags are not opened.

## Consequences

- `Launcher.Options` carries the whole command line, for the hand-over.
- `BackendEvent` gained a case; the runtime's exhaustive switch names it.
- The macOS half is written against SDL's source and is unverified on a Mac.
  The headless backend's `post` is what the tests drive.
- Tested by `SingleInstanceTest` (a hand-over whole and in order, non-ASCII
  included; arguments before a listener; release; ids), `OpenTargetsTest`, and
  `OpenUrlTest`: an `OpenUrl` posted with no window of its own, and a second
  launch's URL reaching a running single-instance application, end to end on
  the headless backend.
