# 286. A clipboard write is an offer, not a copy

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G7.

## Context

G7: *"`render.Clipboard` is `hasText` / `text` / `text(String)`. Pasting a
screenshot onto a board is the most-used way anything gets onto one. Copying
shapes between two windows needs a custom type as well."*

`Clipboard`'s own javadoc had already written the reason it stopped at text, and
it is worth quoting because it turned out to be exactly right:

> A clipboard can hold images, files and arbitrary MIME types, and every one of
> those is a *transfer negotiation* rather than a value — the owning application
> advertises formats and serialises on demand, which means an interface that
> admits them has to admit lazy providers, format lists and cancellation.

The entry's own note said the type it needed now exists (`image.Image`, ADR-0283)
and that "the platform half is the whole of the remaining work". That half is
`SDL_SetClipboardData`, and it is the first place in this toolkit where **the
caller owns memory across an upcall**.

## Decision

### The byte half is bytes and a MIME type, and nothing else

```java
boolean has(String mime);
byte[]  read(String mime);
boolean write(String mime, byte[] bytes);
boolean write(Map<String, byte[]> byMime);
boolean clear();
```

Not `hasImage()` / `image()` / `image(Image)` as the entry proposed. A clipboard
carries an image, a document's own format, a file list and whatever two
applications have agreed on; the only thing common to all of them is a type and
some bytes. Putting `Image` in this interface would also make `render` — the
**backend SPI**, which a platform implements — depend on the decoder, so every
backend would have to know what a PNG is, and `render` and `image` would depend
on each other.

So the image convenience lives beside the decoder instead:

```java
Image.onClipboard(clipboard);       // cheap: what has been advertised
Image.fromClipboard(clipboard);     // Optional<Image> — this is a paste, and a round trip
image.toClipboard(clipboard);       // as image/png
```

which is the same direction `Image.decode(Path)` already goes: the value knows
the places it can come from, and a file does not know about images.

**Every byte method is a `default` that does nothing.** A backend written before
this existed reports that it holds nothing and accepts nothing, which is honest;
`Clipboard.none()` needed no change at all.

### `write` is an offer, and the bytes stay ours

`SDL_SetClipboardText` copies. `SDL_SetClipboardData` does not: it takes a list
of MIME types and a callback, and calls the callback **if and when somebody
pastes** — which is what the platform protocols do underneath, where an X11
selection owner is asked to serialise on demand. A second callback says when the
offer has been replaced.

So a write allocates a shared arena, copies the bytes into it, and hands SDL two
upcall stubs. Three things fall out of that and each of them is a decision:

- **Each offer carries its own id as `userdata`**, because the cleanup for the
  previous offer arrives *while* the next one is being installed. A single
  "current offer" field would free the wrong arena, intermittently, under the one
  workload nobody tests: copying twice.
- **Ids are never reused**, so a late cleanup cannot free the memory of the offer
  that took its place.
- **The stubs live in an arena that is never closed.** They are two for the life
  of the process, and freeing a stub while it is running is the one way this
  arrangement crashes.

Nothing may be thrown out of an upcall — an exception crossing back into C takes
the process down — so a request this application cannot answer returns NULL and a
zero size, which is SDL's own way of saying so.

### Several types at once, in order

`write(Map)` rather than only `write(String, byte[])`, because one copy usually
is several: a board copying a shape offers its own format **and** a PNG, so
pasting back into the board keeps the shape and pasting into a chat window gets a
picture. The platform advertises them in the map's order and a well-behaved
pasting application takes the first type it understands — which is why a
`LinkedHashMap` says something a `Map.of` does not, and the javadoc says so.

### Reading an image tries only what can be decoded

`image/png`, then JPEG, then QOI — the formats Blend2D was compiled with. A
clipboard offering only `image/webp` is a paste this toolkit cannot do, and
finding nothing is better than handing bytes to a decoder that will refuse them.

Bytes that *were* advertised as a PNG and then fail to decode raise
`ImageDecodeException` rather than coming back empty: the clipboard said it had
one, and an application offering to paste should be told that it lied rather than
shown an `Optional.empty()` it will read as "there was nothing there".

## Consequences

- **Four symbols were added** — `SDL_SetClipboardData`, `SDL_ClearClipboardData`,
  `SDL_GetClipboardData`, `SDL_HasClipboardData` — and `SDL_free`, which was
  already there for text, closes the read loop unchanged.
- **This is the second upcall family in the library**, after Yoga's measure
  function (ADR-0017), and the first where memory outlives the call. The test for
  it is a real round trip: writing and then reading back runs SDL's own request
  path, which calls our callback — a wrong descriptor, a wrong `size_t*` write or
  a stub in a closed arena all fail there rather than somewhere later.
- **The headless backend's clipboard gained the byte half**, in memory, for the
  reason it has a text half at all: a paste that could not possibly have anything
  to paste tests nothing. What it cannot model is laziness or a refusal, and it
  does not pretend to.
- **Text and bytes are separate**, because SDL keeps them separate: a widget that
  copies text must not silently drop an image somebody else put on the clipboard.
  `clear()` drops both, which is the one place they meet.
- **Nothing watches the clipboard.** `Clipboard`'s existing note already argued
  this for text and it holds for bytes: the only consumer would be a paste button
  greying itself out, and one that asks `has(mime)` when its menu opens gets the
  same answer for none of the machinery.
- **The showcase's image card pastes now** — click it, `Ctrl+V` for a screenshot,
  `Ctrl+C` to copy its own picture out — and its caption grew a line, which moved
  the Canvas screen's masonry and its golden.

## Alternatives considered

- **`hasImage()` / `image()` / `image(Image)` on `Clipboard`, as G7 proposed.**
  The layering above: a backend would have to decode, and the SPI would depend on
  the image package that depends on it.
- **A typed `ClipboardContent` union** — text, image, files, custom. It reads well
  and it is a lie about the platform: a clipboard offers *several* types at once
  and the pasting application chooses, so a union would have to pick for it.
- **Copy the bytes to the platform eagerly.** There is no such call for arbitrary
  data in SDL3, and there is none in X11 or Wayland either — the laziness is the
  protocol rather than SDL's choice.
- **One "current offer" field instead of ids.** Simpler, and wrong in the case
  that happens every time somebody copies twice.
- **Encode a PNG lazily in `toClipboard`.** The image would have to stay reachable
  for as long as it was on the clipboard and would encode again for every paste.
  A UI-sized PNG is milliseconds and a copy is a deliberate act.
- **Watch for clipboard changes and expose it as a `Property`.** Still no
  consumer, and X11's answer to "what is on the clipboard now" is a round trip
  per question.
