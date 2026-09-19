# 406. A `uri-list` is a list of names, and only some of them are files

Date: 2026-09-19

## Status

Accepted. Closes the "No file lists" entry under "The clipboard" in
`book/src/TODO.md`.

Extends [ADR-0286](0286-a-clipboard-write-is-an-offer.md), which put bytes under
a MIME type on the clipboard and deliberately stopped there, and
[ADR-0330](0330-a-dropped-file-arrives-somewhere.md), whose rule about a name the
file system will not accept is reused here.

`SDL_EVENT_DROP_TEXT` is **not** bound here. The reason is recorded below, because
it is not the reason ADR-0330 gave — and it was paid and bound immediately
afterwards by
[ADR-0408](0408-a-dropped-line-of-text-is-a-dropped-file-in-every-way-but-one.md),
once another change in the same batch made the native half's bill payable.

## Context

The entry:

> `text/uri-list` is bytes like anything else and works today, but nothing turns
> those bytes into paths.

Both halves of that are true and the second is the whole cost. A `uri-list` is
not a list of paths; it is a list of *percent-encoded URIs*, and the twenty lines
that turn one into the other are lines every application would write for itself.
They are also lines that are easy to get wrong in a way nothing notices: the
naive reader — split on newlines, chop off `file://` — is correct for every file
in a test fixture and wrong for the first one with a space in its name, which
arrives as `/tmp/my%20file.png` and is a file that does not exist. That bug does
not fail a build. It ships, and it is reported as "dragging from Downloads does
not work" by a user whose Downloads folder has a space in one name.

The format is RFC 2483's: one URI per line, CRLF between lines, `#` starts a
comment. What actually arrives is looser than that — a bare LF is common, a
trailing NUL happens on X11 — and none of the looseness is the interesting part.
The interesting part is that **a `uri-list` is not a file list**. A drag out of a
browser is a list of `https:` URIs; a mail client offers `mailto:`. Deciding what
happens to those is the decision this ADR exists to record.

## Decision

**A record in the clipboard's own package, holding URIs, with the file half as a
conversion.**

```java
// io.github.digitalsmile.goldberry.render
public record UriList(List<URI> uris) {
    public static final String MIME = "text/uri-list";

    public static UriList parse(byte[] bytes);
    public static UriList parse(String text);
    public static UriList of(List<Path> paths);

    public List<Path> paths();
    public String text();
    public byte[] encode();

    public static boolean onClipboard(Clipboard clipboard);
    public static UriList fromClipboard(Clipboard clipboard);
    public boolean toClipboard(Clipboard clipboard);
}
```

### It lives beside `Clipboard`, unlike `Image`

ADR-0286 put the image convenience beside the decoder and argued why: a backend
implementing the SPI must not have to know what a PNG is, and putting `Image` on
`Clipboard` would have made `render` depend on `image`, which depends on `render`.

That argument does not reach this type, and it is worth saying why rather than
applying it by analogy. `UriList` has no decoder to live beside: its only
dependency is `java.nio.file`, which is the JDK. It drags nothing into the SPI,
it forces nothing onto any backend — it is a *value*, like `Cursor` and
`DamageRect` in the same package — and the alternative is not "a smaller
`render`" but "every application parsing this itself". `Clipboard`'s own javadoc
names it now, one line below where it names `Image.fromClipboard`, so a reader
who has the bytes finds the type that reads them.

### Non-`file:` entries are kept, and are not paths

`uris()` is every entry that parsed. `paths()` is the `file:` subset, converted.

Filtering the others out at parse time would be cheaper and it would throw away
the answer to the question a failing paste actually asks. An application that
asked for files and got none needs to know whether the clipboard was empty or was
offering a list of web links, and those are the same answer if the web links were
silently dropped. This is the argument `Clipboard.types()` is on the interface
for (ADR-0286), one level up.

So a paste of a browser's drag is a `UriList` with two URIs and no paths, and an
application can say so.

### A line that is not a URI is dropped, with a log

Not thrown. The list came from another application across a protocol with no
schema and no validator, and ADR-0330 already decided this for the names that
arrive by drag-and-drop from the same desktops: *a drop shortened by one bad name
is better than a drop that failed.* A `parse` that threw would turn one bad line
into a paste that does nothing, and the bad line is usually the tenth of ten.

Three kinds of entry are dropped, and each is a separate judgement:

- **Not a URI at all.** `file:///tmp/%ZZ` is a malformed escape pair and
  `file:///tmp/a b` has a raw space; `URI`'s own parser refuses both.
- **No scheme.** A bare `/tmp/x` is dropped rather than read as a path. Guessing
  here is how `C:\Users\…` written by another machine becomes a name this one
  would happily create, and a line without a scheme is, by the format's own
  definition, not an entry.
- **A name this file system refuses.** `file:///tmp/a%00b` is a *legal* URI:
  `%00` is a well-formed escape and NUL is a byte. It is not a legal path, and
  `Path.of` says so. This is exactly the case ADR-0330 handles for drops,
  arriving through the other door, and it gets the same answer.

### `file://localhost/x` is this machine

RFC 8089 blesses both `file:///x` and `file://localhost/x`, and Java's file
system accepts only the first: `Path.of(URI.create("file://localhost/tmp/a"))`
throws `IllegalArgumentException: URI has an authority component`. Dropping that
entry would be the toolkit inventing a failure the desktop did not have, so the
one authority that *is* this machine is normalised away.

Any other authority is left to fail. `file://fileserver/share/a.png` names
something on another host; resolving it to `/share/a.png` here would open the
wrong file, and opening the wrong file is worse than opening none.

### Read loosely, write strictly

Reading accepts CRLF, LF, a bare CR, blank lines, `#` comments and a trailing
NUL, and decodes as UTF-8 — which is what percent-decoding a `file:` URI
produces on every desktop this runs on. Writing emits RFC 2483's form: one entry
per line, CRLF-terminated, percent-encoded by `Path.toUri`. The half of the
protocol this toolkit controls is the half it can afford to be strict about.

### `fromClipboard` is empty rather than `Optional`

`Clipboard.text()` already argued this: "there is nothing to paste" and "what was
copied was empty" are the same paste, and a caller that had to distinguish them
would have nothing different to do. `Image.fromClipboard` returns an `Optional`
because a *decode* can fail; nothing here can. An application that does care asks
`onClipboard` first, which is the cheap question.

### `SDL_EVENT_DROP_TEXT` is still unbound, and now for a different reason

ADR-0330 left it out because nothing had asked for it — "a second event with no
caller is a second event with no test". That is still true, and it is no longer
the binding constraint. The constraint is that **the event number is a verified
constant, and the verification lives in C.**

Every value of `SdlEventType` is entered into `NativeConstants.registry()` by a
loop over `values()`, and `LayoutVerifier` reports any registered constant that
`goldberry_shim.c` does not report back:

> `SDL_EVENT_DROP_TEXT is declared in Java but not registered in
> goldberry_shim.c, so nothing verifies it`

That is the design working (ADR-0010): a hard-coded event number that nothing
checks is a binding that silently never fires. It also means adding
`DROP_TEXT(0x1001)` to the enum is not a Java-side change. It needs one more
`GB_CONSTANT` line in the shim, which is a native rebuild on four platforms and
an ABI version bump — the same bill ADR-0330 paid to take the ABI to 11 for the
other four drop events.

So the work is recorded rather than done: one line of C, one version number, and
then the `DROP_TEXT` arm in `Sdl3Backend`, a `BackendEvent` case and a
`Window.onTextDrop` shaped exactly like `onFileDrop`. Building the Java half now
and leaving the constant out would produce API that no platform can ever raise —
surface with no test, which is the thing ADR-0330 refused in the first place.

## Consequences

- **`text/uri-list` is readable in five lines of application code**:
  `UriList.fromClipboard(clipboard).paths()`. Writing one is
  `UriList.of(paths).toClipboard(clipboard)`.
- **`UriListTest` holds every decision above as a named case** — 20 of them,
  including `percentDecodes`, `aBarePathIsNotAUri`, `anUnparseableLineIsSkipped`,
  `aNulByteInTheNameIsSkipped`, `localhostIsThisMachine` and
  `anotherHostIsNotLocal`. The three that matter are the last three: they are the
  cases that came from running the conversion rather than from reading the RFC.
- **`Path.of` is stricter than RFC 8089 and than the desktops**, which was not
  obvious until it threw. That asymmetry is now in one place instead of in every
  application.
- **Nothing in `:natives` changed**, and no native rebuild is needed for this
  half. The drop-text half cannot be landed without one.
- **`FileDrop` and `UriList` stay separate types.** They are the same information
  from two different platform mechanisms — a gesture versus a clipboard offer —
  and ADR-0330's reason for `FileDrop` carrying a position is the reason they do
  not merge: a drop landed somewhere and a paste did not.

## Alternatives considered

- **`List<Path> paths()` as the only accessor, dropping non-file URIs at parse.**
  Shorter, and it destroys the evidence a failing paste needs.
- **Guess that a scheme-less line is a local path.** Tolerant of one real
  producer and wrong about Windows names that came from elsewhere, where the
  guess resolves to a path this machine would create rather than find.
- **Throw on an unparseable line.** Makes one bad entry in a list of ten into a
  paste that does nothing, and the toolkit is not the validator of another
  application's output.
- **Percent-decode by hand.** `URI` and `Path.of` already do it, including the
  UTF-8 that `%D0%BF` is, and a hand-rolled decoder is a second place for the
  same bug.
- **Put the type in `input.drop` beside `FileDrop`.** That package is input
  *events*; this is a value on a clipboard, and a paste is not an event.
- **Add `SDL_EVENT_DROP_TEXT` to `goldberry_shim.c` here.** One line, and it
  makes this a native change: a rebuild on four platforms, a new ABI version, and
  a verification matrix, for an event with no caller. It belongs in whatever
  change first needs dropped text.
