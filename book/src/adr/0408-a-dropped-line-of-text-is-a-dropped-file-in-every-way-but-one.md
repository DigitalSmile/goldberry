# 408. A dropped line of text is a dropped file in every way but one

Date: 2026-09-19

## Status

Accepted. Closes what [ADR-0330](0330-a-dropped-file-arrives-somewhere.md) left
out — "`onTextDrop` is not here" — and what
[ADR-0406](0406-a-uri-list-is-a-list-of-names.md) recorded as blocked.

**The library has not been rebuilt yet.** The one C line this needs is in
`goldberry_shim.c` in this change; until the superbuild runs,
`LayoutVerificationTest.handWrittenLayoutsAgreeWithC` is red. That is stated
plainly below rather than glossed, because a green suite is the claim this ADR
would otherwise be making.

## Context

ADR-0330 bound four of SDL's five drop events and left `SDL_EVENT_DROP_TEXT`
alone with a one-line reason: "the same shape and nothing has asked for it. A
second event with no caller is a second event with no test."

ADR-0406 went looking for it while building the clipboard's file-list reader and
found that the reason had changed underneath. Binding it is **not** a Java-side
change, and the interesting part is *why* — because the failure it produces looks
nothing like the failure a missing binding usually produces.

### The blocker was a constant, not a symbol

A missing native *symbol* fails loudly and early: the lookup does not resolve and
the descriptor never binds. An event number is not a symbol. It is an `int` the
Java side hard-codes, and a wrong one does nothing at all — the event simply never
arrives, which is the quietest possible failure and is exactly why
`NativeConstants` exists (ADR-0010).

Every value of `SdlEventType` is entered into that registry by a loop over
`values()`, so adding one enumerator adds one row to the contract, and
`LayoutVerifier` fails any row the compiled library does not report back:

```
SDL_EVENT_DROP_TEXT is declared in Java but not registered in goldberry_shim.c,
so nothing verifies it
```

That is the message this change produces today, verbatim, against the
`libgoldberry` built before it. So the bill for a "Java-side" event was: one
`GB_CONSTANT` row in C, a rebuild on four platforms, and an ABI bump — which is
why ADR-0406 stopped and wrote the reason down instead of paying it alone. It is
paid here because ADR-0422 is bumping the ABI 12 → 13 for its own reasons in the
same batch; this change adds a row to the same table and deliberately does **not**
touch `GOLDBERRY_ABI_VERSION` or `GoldberryShim.SUPPORTED_ABI_VERSION`, so the
version number has exactly one author.

### What a text drop actually is, which is not what ADR-0330 assumed

ADR-0330 said `DROP_TEXT` was "the same shape" as `DROP_FILE` and meant it
loosely — one event, one payload. Reading SDL's own senders makes it literally
true in a way that matters, and the reason is in the tokeniser:

```c
/* SDL_waylandevents.c — and Windows, macOS and Emscripten all do this */
char *token = SDL_strtok_r((char *)buffer, "\r\n", &saveptr);
while (token) {
    SDL_SendDropText(data_device->dnd_window, token);
    token = SDL_strtok_r(NULL, "\r\n", &saveptr);
}
SDL_SendDropComplete(data_device->dnd_window);
```

**SDL splits dropped text on `\r\n` and raises one event per line.** A two-line
selection is two `SDL_EVENT_DROP_TEXT`s followed by one
`SDL_EVENT_DROP_COMPLETE` — structurally identical to two files. It is the same
loop, three lines above, that turns a `text/uri-list` into one `SDL_SendDropFile`
per entry.

Two consequences fall out of that and neither is a matter of taste:

- **The separators are gone.** Nothing downstream can tell `\n` from `\r\n`, or a
  trailing newline from none, because `SDL_strtok_r` consumed them. A record
  holding one `String` would have to invent them.
- **Empty lines are gone too** — `SDL_strtok_r` skips empty tokens — so a blank
  line in the middle of a dropped selection does not arrive.

## Decision

**`TextDrop` is `FileDrop` with lines instead of paths, and the two share the
gesture's end.**

```java
// io.github.digitalsmile.goldberry.input.drop
public record TextDrop(List<String> lines, LogicalPoint at) {
    public String text();   // lines joined with \n
    public String first();
    public int count();
}

// io.github.digitalsmile.goldberry.Window
public Subscription onTextDrop(Consumer<TextDrop> listener);

// io.github.digitalsmile.goldberry.render.event.BackendEvent
record TextDropped(BackendWindow window, String text, float x, float y) { … }
```

### Lines, because the platform says lines

Not `TextDrop(String text, …)`. The value is the list because the list is what
arrives, and `text()` — which joins with `\n` — is a **named reconstruction**
rather than the model. Its javadoc says the `\n` is this toolkit's choice, and
`textJoinsWithNewline` asserts it so that changing it is a decision somebody makes
on purpose. For the overwhelmingly common drop — a URL, a word, a line out of a
terminal — there is one line and nothing to reconstruct, and `drop.text()` is what
an application writes.

### One completion for both kinds

SDL has one `SDL_EVENT_DROP_COMPLETE` and no per-kind completion, so
`BackendEvent.FileDropCompleted` now ends a text drop as well. Its name is
narrower than its job.

**The name is kept, and that is a decision with a cost.** `FileDropCompleted` is a
case of a sealed SPI type: it is the word every exhaustive `switch` over
`BackendEvent` spells, in this repo and in any backend outside it. Renaming it to
`DropCompleted` would be a source-breaking change to the SPI to gain an adjective,
so the javadoc carries the correction instead — the record already said "the
drag-and-drop gesture ended" rather than "the file drop ended", so only the
identifier is wrong.

What *was* renamed is the internal half: `Window.handleFileDropCompleted` is now
`handleDropCompleted`, because it is package-private, has three call sites, and
raising a `TextDrop` from something called `handleFileDropCompleted` is the kind of
line that gets read as a bug for years. The rule the two halves come from: a name
inside the toolkit is worth fixing when it is wrong; a name in the SPI is a
promise.

### Two buffers, not one

`Window` accumulates paths and lines separately, and on completion raises a
`FileDrop` if paths arrived, a `TextDrop` if lines did, and both if both did.

No platform SDL supports is known to send both in one gesture — Wayland's handler
is `if (has_mime_file) … else if (has_mime_text)`, and Windows and macOS pick a
representation the same way. The second buffer is not modelling a case that
happens; it is refusing to lose half of one if it ever does, which costs one list
and one `if`. A shared buffer would have had to decide whether `/tmp/a.png` was a
path or a line, and the answer would have been whichever kind arrived first.

### An empty line is not text

The backend drops an empty token rather than forwarding it, and `Window` drops one
too if a platform sends it anyway, so an otherwise empty gesture stays silent —
`FileDrop`'s rule, for `FileDrop`'s reason. `TextDrop` refuses to be constructed
empty, like `FileDrop`: a listener handed a drop with nothing in it would have to
check, and every listener would forget.

### `droppedText()` beside `droppedPath()`

One `SDL_DropEvent.data` field, two accessors on `SdlEventBuffer`, because SDL's
header says the field is "the text for `SDL_EVENT_DROP_TEXT` and the file name for
`SDL_EVENT_DROP_FILE`". Two names cost one delegating method and make each backend
arm say which event it is reading; a `droppedPath()` in the text arm would be read
as a bug every time anybody looked at it.

### Nothing is interpreted

A dropped URL is a line of text. The toolkit does not notice that it looks like a
`file:` URI and does not turn it into a `Path` — an application that wants that
says so, with `UriList` (ADR-0406). This is the same restraint ADR-0330 applied to
dropped file names: the platform handed over a name, and what it means is the
accepting application's business.

## Consequences

- **`LayoutVerificationTest.handWrittenLayoutsAgreeWithC` fails until the
  superbuild runs.** The `GB_CONSTANT` row is in this change; the `.so` on this
  machine predates it. The only claim that cannot be checked from Java is the
  number itself, and it was checked the one other way available — compiling
  against SDL's shipped header on this machine, which reports
  `SDL_EVENT_DROP_TEXT=0x1001`. After the rebuild the probe compares the same
  number against the same library and this ADR is either confirmed or loudly
  wrong, which is the whole point of that table.
- **`TextDropTest` holds the gesture** — twelve cases, of which
  `severalLinesAreOneGesture`, `textJoinsWithNewline`,
  `theTwoKindsDoNotCrossOver` and `aGestureCarryingBothRaisesBoth` are the ones
  that would catch the shared completion being got wrong. `SdlDropEventTest`
  gained three: the text reads back out of `data`, the two accessors are one
  field, and the five drop numbers are SDL's in SDL's order.
- **No end-to-end test through a real drag exists, and none is added.** The
  `sdl3` arm is one `switch` case, the number in it is the probe's business, and
  the reassembly is tested where ADR-0330 put it. A test that needed a desktop to
  drag from would not run in CI on any of the three OSes.
- **`BackendEvent` gained a case**, so every exhaustive switch over it stopped
  compiling until it said what it does — the property ADR-0004 chose the shape
  for, working for the second time on this same interface.
- **`HeadlessBackend` still produces no drops**, and `Window` is still where the
  gesture lives, so the headless path is exactly the file drop's: install the
  backend, open a window, deliver the run.
- **SDL had already made ADR-0406's decisions.** `SDL_URIToLocal`, which SDL uses
  for the drop path, rejects a non-`file:` scheme, accepts `localhost`
  case-insensitively as this machine, and percent-decodes — the same three
  judgements `UriList` arrived at independently. It goes one step further and also
  accepts this machine's own `gethostname()`; `UriList` does not, because Java's
  cheap equivalent is not cheap (`InetAddress.getLocalHost()` can go to a
  resolver), and a paste is not a place to block. That difference is recorded
  rather than fixed.

## Alternatives considered

- **`TextDrop(String text, LogicalPoint at)`, joining SDL's tokens on arrival.**
  The obvious shape, and it invents a separator the platform destroyed while
  hiding that it did. `text()` does the join where a caller can see it.
- **Raise one `TextDrop` per `DROP_TEXT`, immediately.** Simpler, and it breaks
  the one promise `onFileDrop` makes — once per gesture — for the kind where the
  gesture most often has one payload anyway. It would also give the drop the
  position from the middle of the gesture rather than its end.
- **Rename `FileDropCompleted` to `DropCompleted`.** Honest, and a source-breaking
  change to a sealed SPI type for one word. Recorded in its javadoc instead.
- **One buffer for both kinds, discriminated by the event that filled it.** Fewer
  fields, and the discrimination has to be invented at exactly the moment a
  platform does something unexpected.
- **Bump the ABI version here.** Two changes in one batch both editing
  `#define GOLDBERRY_ABI_VERSION` is a conflict on the one line that must not be
  wrong. ADR-0422 owns the number; this owns a row in the table.
- **Treat a dropped `file:` URL as a file drop.** Convenient, and a guess about
  intent made in the layer with the least information. `UriList` is one call away
  for an application that wants it.
