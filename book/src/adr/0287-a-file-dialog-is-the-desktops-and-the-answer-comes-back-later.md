# 287. A file dialog is the desktop's, and the answer comes back later

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G9.

## Context

G9: *"No binding. SDL3 has `SDL_ShowOpenFileDialog` / `SDL_ShowSaveFileDialog`.
Needed for: export a board as PNG or SVG and a note as Markdown, import an image,
open a local `.am` snapshot while sync is still being built."*

Every one of those is the same sentence — *a path the user chose* — and the
toolkit had no way to say it. The alternative to binding one is drawing one, and
a file browser is the single worst thing a toolkit can draw: it would be the one
piece of the desktop the desktop is certain to have already, in a theme that does
not match it, without the places sidebar, the search, the recent list, the
network mounts, or — on a sandboxed platform — the permission grant that only
arrives *because* the system picker was used. A Flatpak application that drew its
own file list would show the user a home directory it cannot open.

So the decision was never whether to bind it. It was what shape the API takes,
and that is decided by one fact: **a person is inside the call**.

## Decision

### Asynchronous, and there is no synchronous overload

```java
host.fileDialog(
        FileDialogSpec.saveFile().filters(FileFilter.of("PNG image", "png")),
        choice -> switch (choice) {
            case FileChoice.Chosen(var paths, var filter) -> export(paths.getFirst());
            case FileChoice.Cancelled ignored             -> { }
            case FileChoice.Failed(var message)           -> toast(message);
        });
```

`Path openFile()` would read better at every call site and cannot exist. The UI
thread is the only thread allowed to touch windows, so blocking it until the user
has finished browsing their disk stops the frame loop, the animations, the resize
handler and the window's own repainting. On Linux it would also stop the pump the
portal dialog needs in order to answer, which is a deadlock rather than a stutter
— SDL's own header says as much: *"apps that do not use SDL to handle events
should add a call to `SDL_PumpEvents` in their main loop"*.

### Three kinds, not one with flags

`FileDialogKind` is `OPEN_FILE`, `SAVE_FILE`, `OPEN_FOLDER`, because the
platforms have three and they are not the same conversation: an open dialog names
files that exist, a save dialog names one that may not — which is the whole point
of it — and a folder dialog has nothing to filter. A single "file dialog" with
booleans would have to document which combinations are real. `FileDialogSpec`
refuses the two that survive the type system (filters on a folder, many on a
save) in its compact constructor, rather than leaving each platform to drop them
quietly.

### The answer is sealed, and cancelling is not failing

`FileChoice` is `Chosen | Cancelled | Failed`. The C callback packs all three
into one pointer — NULL is an error, a pointer to NULL is a cancel, anything else
is a choice — and reading that convention is the native layer's job, not the
application's. Sealed rather than an enum plus a payload so that a `switch`
without a `default` stops compiling if a fourth case is ever added.

`Cancelled` is deliberately not a failure. The user pressing Escape leaves no
error to show, and an export that toasted "operation failed" because somebody
changed their mind would be wrong about what happened.

### Extensions, not patterns

`FileFilter.of("Images", "png", ".jpg", "*.jpeg")` normalises to three bare,
lower-cased extensions. Every platform spells a filter differently — Windows
wants `*.png;*.jpg`, GTK wants a glob per entry, macOS wants uniform type
identifiers, SDL wants `png;jpg` — so the toolkit's vocabulary is the part they
agree on, and the dialect is the backend's. A leading dot is accepted because it
is what a `Path` shows you and removed because none of them store it; anything
that looks like one platform's dialect is **refused**, because "undefined
behaviour" in SDL's header means a dialog that lists no files, and that is the
hardest kind of bug to read backwards.

A filter is a suggestion, not a validator: several platforms let the user switch
filtering off. Code that must not open an `.exe` checks the path it was given.

### `SdlFileDialogs` lives in `natives.sdl`, not in `natives.sdl.desktop`

Beside the clipboard and the tray is where it belongs by subject matter, and it
is not there, for one parameter: a dialog is modal for a window, and
`SDL_Window*` is `SdlWindowHandle`'s package-private secret. Moving that pointer
into another package to reach a wrapper would undo exactly what the class exists
to do (`docs/ARCHITECTURE.md` §3.1). The values it traffics in — `SdlFileFilter`,
`SdlDialogKind`, `SdlFileDialogCallback` — touch no foreign memory, so they get
their own package, which is the split ADR-0172 asks for.

### The request's memory is in an automatic arena

SDL requires the filter array to stay valid until the callback runs, so each
request gets an `Arena` of its own, registered under an id that travels as the
`userdata` pointer and dropped when that id's callback arrives.

The arena is `Arena.ofAuto()`, and **this is the part that was learned by
crashing**. The first version used a shared arena and closed it in the callback,
which is correct for the case everyone thinks about — a portal answering minutes
later on the DBus thread — and fatal for the case nobody does: a machine with no
XDG portal and no `zenity` refuses **synchronously**, calling the callback from
inside the very downcall that handed the memory over. The linker still holds that
arena's session there, so `close()` throws `IllegalStateException`, inside an
upcall, which takes the process down. An automatic arena has no close: the memory
lives exactly as long as the request that owns it is reachable, which is
precisely the contract SDL's header states. `pendingRequests()` still reports
what is outstanding, so the leak is still testable.

### The hop to the UI thread is the pump's, not an executor's

SDL's callback may arrive on any thread; the SPI promises the UI thread. So
`Sdl3FileDialogs` parks the answer in a concurrent queue, calls
`Backend.wakeup()`, and delivers it from `deliverPending()` at the **top** of the
next `pumpEvents` — before the blocking wait, so the repaint the answer asks for
belongs to that pump rather than the next one. A pending answer also zeroes the
wait, so a dialog answered during the loop's quiet second is not made to sit
there.

`UiExecutor` is the toolkit's general answer to this question and is deliberately
not used: it belongs to the `EventLoop`, which is built *on* a backend and cannot
be reached from inside one. A backend reaching up into the loop that drives it
would be a cycle and a second lifetime to get right; the queue is nine lines.

### `Host.fileDialog` owes the repaint

Like a tray row (ADR-0191), a dialog's answer arrives with no input event behind
it, so nothing asks for a frame and a handler that changed the model changes
nothing anybody looks at. `Host.fileDialog(spec, onChoice)` fills in this window
as the one to be modal for and repaints in a `finally`, so the frame is owed
whether or not the handler finished. `Host.fileDialogs()` is the process-global
facility underneath it, for the menu that wants to ask `supported()` before
offering an "Export…" item.

## Consequences

- **Three symbols were added** — `SDL_ShowOpenFileDialog`,
  `SDL_ShowSaveFileDialog`, `SDL_ShowOpenFolderDialog`.
  `SDL_ShowFileDialogWithProperties` was not: it is the only way to set a title
  or relabel the buttons, and it costs a properties object and a second code path
  for three strings the platforms are entitled to ignore — macOS has no dialog
  title at all. `FileDialogSpec` has room for them the day something needs them.
- **This is the third upcall family in the library**, after Yoga's measure
  function (ADR-0017) and the clipboard's offer (ADR-0286), and the first whose
  callback can arrive on a thread the toolkit did not create.
- **It is tested without a desktop.** Pointing `SDL_FILE_DIALOG_DRIVER` at a
  driver that does not exist makes SDL refuse *through the callback* — the same
  upcall a real answer arrives on — so the NULL-is-an-error branch, the calling
  convention and the request's release are all exercised on a CI runner with no
  display. What cannot be tested there is a person choosing a file.
- **The headless backend has real ones**, scripted: `answerWith(...)` says what
  the user did and `shown()` says what was asked for. A backend that refused
  would make every test of an export button pass for the wrong reason, which is
  the argument its clipboard and its tray already make.
- **`Backend` grew a sixth facility** and, like the clipboard, it is never
  `Optional`: absence is `FileDialogs.none()`, which answers every request with a
  `Failed` rather than a silent cancel, because a caller whose export quietly did
  nothing would go looking for the bug in its own code.
- **Nothing is drawn, so nothing is photographed.** There is no golden image for
  this feature and cannot be one — the dialog is the desktop's window, not
  Goldberry's. The showcase gained no card for the same reason.

## Alternatives considered

- **`Optional<Path> openFile(...)`, blocking.** The one call site everybody wants
  and a frozen window plus, on Linux, a deadlock against the event loop the
  portal needs.
- **`Optional<FileDialogs> fileDialogs()` on `Backend`,** the way popups and the
  tray report absence. A caller that got empty would write `FileDialogs.none()`
  itself, which is the argument `Clipboard` already made and won.
- **A `Dialogs` static facade,** as G9 proposed —
  `Dialogs.openFile(...)`/`saveFile(...)`/`openFolder(...)`. Static access to a
  per-session facility is the thing `Host` exists to avoid (ADR-0140), and the
  names survive as `FileDialogSpec`'s factories, which read the same at the call
  site and carry the request as a value.
- **Message boxes (`SDL_ShowSimpleMessageBox`) in the same cut.** A different
  question — the toolkit *can* draw a dialog, and `docs/core-widgets.md` says it
  should — and bundling them would have hidden that difference behind a shared
  package name.
