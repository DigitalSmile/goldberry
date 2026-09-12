# 291. A URL scheme is packaging, and packaging is the application's

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G12 — **as a decision, not as a feature.**
Goldberry will not own deep links, single-instance handoff or the OS keychain.

## Context

G12 is the only entry in `docs/gaps.md` that asks a question rather than
proposing an API:

> **Why it might be Goldberry's.** It is window-and-platform integration, which
> is the toolkit's half of the world, and every desktop application that ships
> needs it. If Goldberry would rather not own it, brd will — but that is a
> decision to take deliberately rather than by default, which is what this entry
> is for. The OS keychain (plan `auth/`) is the same question and probably the
> same answer.

It has to be answered deliberately because the default answer is a slow yes: each
piece looks small, each is "platform integration", and by the third one the
toolkit has a Win32 path, a Cocoa path and a freedesktop path in it.

`brd://open/<token>` is three separate problems wearing one name, and they have
three different owners:

1. **Registration** — telling the OS that this application handles `brd://`. A
   `HKCU\Software\Classes\brd` key on Windows, a `CFBundleURLTypes` array in
   `Info.plist` on macOS, a `.desktop` file with `MimeType=x-scheme-handler/brd`
   and a `update-desktop-database` run on Linux.
2. **Delivery** — the URL reaching a running process. On macOS SDL already does
   this: `handleURLEvent:` becomes an `SDL_EVENT_DROP_FILE` with a NULL window.
   On Windows and Linux the URL is `argv[1]` of a **new** process.
3. **Handoff** — that second process finding the first, giving it the URL, and
   exiting so one window is focused rather than two opened.

## Decision

### None of the three is Goldberry's

**Registration is packaging.** It is written by an installer — an MSI, a `.app`
bundle, a `.deb`, a Flatpak manifest — at install time, as the user, into places
a running process should not be poking. Goldberry ships no installer, no bundler
and no manifest; it is a library an application is built with. A toolkit that
wrote a registry key at start-up would be an application's installer running with
the application's luck, and it would be *wrong* on every platform that has a
package manager, where the scheme belongs in the package metadata and gets
uninstalled with it.

This is also where ADR-0003 and ADR-0041 already point. The backend SPI exists so
that there is one platform-facing interface and it is SDL3's; those records say
in as many words that it "is not an invitation to grow hand-written Win32, Cocoa
or Wayland backends". Registration is exactly that invitation, in a shape that
does not even need a window.

**Delivery and handoff go with it**, and that is the part worth being explicit
about, because a narrower decision was available and was considered — see below.
They are not platform code, but they are an *application's process model*: what
"already running" means, whether a second launch is an error or a message,
whether two documents are two windows or two tabs, and what a URL is allowed to
do to a window that has unsaved work in it. None of those has an answer a toolkit
can pick, and the ones brd needs are brd's own.

**The keychain is the same answer.** `plan auth/`'s credential storage is
DPAPI, the macOS Keychain and libsecret — three platform APIs, no SDL coverage,
and a security surface whose failure mode is a leaked token. The entry guessed
this would be the same question and the same answer, and it was right.

### What Goldberry does give an application that wants this

Nothing new, and that is the point — the pieces already exist:

- **The URL arrives as an argument.** `Application` is handed the process's own
  `args`; an application reads `argv[1]` and decides what a `brd://` in it means.
- **On macOS it arrives as a drop.** SDL translates `handleURLEvent:` into
  `SDL_EVENT_DROP_FILE`. Goldberry does not bind the drop events yet; when
  something needs drag-and-drop that binding lands for *that* reason, and a
  macOS deep link will fall out of it. Filed as such rather than built now.
- **The handoff is ordinary Java.** A `FileLock` on a file under the application's
  own data directory, and a `java.net.UnixDomainSocketAddress` channel beside it.
  No native call, no SDL, nothing a toolkit has to lend.
- **Getting onto the UI thread from that socket's thread is solved**:
  `EventLoop.ui()` is a `UiExecutor`, and it is the one sanctioned way in
  (ADR-0019). An application's listener thread posts the URL and the loop wakes.

So an application that wants `brd://open/<token>` writes perhaps eighty lines and
one line in its packaging. A toolkit that owned it would write three platform
paths and still leave the packaging line to be written.

## Consequences

- **G12 closes with no code**, and `docs/gaps.md` stops carrying an open
  question. The entry becomes a record of the answer, with the sketch above, so
  that the next application does not re-open it.
- **The OS keychain will not be a gap either.** Named here so that the same
  question does not arrive under a different heading.
- **brd owns the handoff and writes it once.** It is the application that knows
  what a second launch means for a board with unsaved edits.
- **If a second application ever needs the same eighty lines**, that is the
  moment to reconsider — two consumers is evidence and one is a guess (ADR-0019's
  rule, which is why the popup, the clipboard and the tray each waited for one).
  Reconsidering means reopening this record, not quietly adding a method.
- **Drag-and-drop remains unbound**, and the macOS deep-link path rides on it.
  That is the one piece of this that is genuinely the toolkit's and is simply not
  needed yet: nothing in the catalog drops a file.

## Alternatives considered

- **Delivery and handoff in the toolkit, registration left to the installer.**
  The tempting middle, and the one this record spent the longest on. It is
  defensible: the handoff is platform-neutral Java and the delivery is an event.
  It was rejected because the resulting API is a `Host.onOpen(URI)` that does
  nothing on two platforms out of three until the application *also* does the
  packaging — a feature that looks supported and is inert, which is worse than
  one that is honestly absent. And the questions it would have to answer for the
  application — one window or two, what happens to unsaved work — are not
  questions a toolkit can answer.
- **Registration at start-up, on all three platforms.** Gets brd a working
  `brd://` with no installer work, and puts a registry writer, a plist editor and
  a `.desktop` generator inside a toolkit whose entire platform story is "SDL3,
  and one interface". It is also wrong on any packaged install, where the scheme
  belongs to the package.
- **Binding `SDL_OpenURL`.** The *outbound* half — opening a link in the user's
  browser — which is one symbol and genuinely SDL's. Not part of this decision
  and not blocked by it: when a widget needs a clickable link, that is its own
  small record.
