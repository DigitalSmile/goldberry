# ADR-0539: An application reads its own resources, and a missing face is said at start

- **Status:** Accepted. Amends [ADR-0349](0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md)
  ("lazy, and forgiving at draw time").
- **Date:** 2026-10-02
- **Relates to:** [ADR-0387](0387-a-resource-directory-is-a-package.md),
  [ADR-0395](0395-a-resource-is-opened-to-whoever-reads-it.md),
  [ADR-0538](0538-a-weight-is-a-number-and-the-nearest-face-answers-it.md),
  `docs/goldberry-gaps.md` #18

## Context

`FontSource.resource(…, App.class, "fonts/Forum.ttf")` called
`anchor.getResourceAsStream(name)` from `FontSource`, which is in
`dev.goldberry.core`. On the module path that is `dev.goldberry.core` asking for
a resource in the application's module, and JPMS answers null unless the package
holding the file is opened to it. The failure was then a warning the first time
something was drawn in the face, and the text fell back to Inter. The
`Application.fonts()` example in the javadoc used exactly that form, so it failed
in any modular application. Deploy Orc read the font bytes itself, opened its
resource-only packages, and suppressed javac's `opens` lint to get there.

`Stylesheet.resource` reads the same way. It already failed loudly at the call
with an `opens` hint (ADR-0395), but the hint named the class's package, which is
not the file's when the name has a directory in it (ADR-0387).

The plan proposed `FontSource.resource(Module, name)`. That does not work, and it
was checked before being refused: `Module.getResourceAsStream`,
`Class.getResourceAsStream` and `ClassLoader.getResourceAsStream` are all
caller-sensitive, so each of them, called from Goldberry's code, answers null for
an unopened package of another module. A two-module experiment returned null for
all three, and the bytes for a supplier written in the application's module. A
`MethodHandles.Lookup` would also work, through a method handle to
`getResourceAsStream`, but that is a reflective lookup an image has to be told
about, and a lambda is not.

## Decision

**The form that works without `opens` is the application's own code: a
`Supplier<InputStream>`. The toolkit looks for every face's file when the font
book opens and says at start which ones are not there.**

- `FontSource.stream(family, weight, style, Supplier<? extends InputStream>)`.
  The lambda, `() -> App.class.getResourceAsStream("fonts/Forum.ttf")`, is
  compiled into the application, so it reads with the application's access.
  `Stylesheet.stream(layer, origin, supplier)` is the same for a sheet, read
  at once like `Stylesheet.resource`.
- `FontSource.resource(…, Class, name)` stays, documented as needing the
  file's package opened to `dev.goldberry.core`. Its failure, and
  `Stylesheet.resource`'s, now tells a missing file from an encapsulated one
  by the **file's** package, names the line to add, `opens <pkg> to
  dev.goldberry.core;`, and names the `stream` form as the alternative.
- `Application.fonts()`'s example uses `FontSource.stream`.
- **The book probes when it opens.** `Fonts.bundled(shipped)` opens and closes
  each resource or stream, unread. One that is not there is added to
  `Fonts.unreadable()`, warned about once per process at warn, naming the
  family, weight and style and why, and drawn in the UI face from then on.
  The launcher opens its book before the window shows, so the line is at
  start. Nothing is parsed until the face is drawn, so ADR-0349's laziness
  holds for the expensive part. Bytes from an arbitrary `Supplier<byte[]>`
  are taken on trust, since the only way to look is to read them.
- **No fail-fast switch.** The launcher has no strict mode to hang one on,
  and a font is not worth inventing one for. An application that would rather
  not start reads `host.fonts().unreadable()`.

## Consequences

- A modular application ships a face with no `opens`, and its javadoc
  example works as written. The test that proves it builds a real named
  module at run time, since the suite runs on the class path where nothing is
  encapsulated.
- A missing face is one warning at start rather than a fallback somebody
  notices on the screen that uses it.
- Probing costs an open and a close per shipped face at start: for a resource
  in a jar, a lookup and an inflater that reads nothing. It was not measured.
- `FontSource` equality now follows its bytes' source: two `resource` sources
  for the same class and name are equal, where two lambdas never were.
