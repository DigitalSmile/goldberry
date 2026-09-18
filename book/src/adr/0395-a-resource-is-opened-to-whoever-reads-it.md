# 395. A resource is opened to whoever reads it

Date: 2026-09-18

## Status

Accepted. Fixes the showcase's Canvas screen and gives `ImageSource.resource`
the diagnostic `Stylesheet.resource` already had.

**Amends [ADR-0093](0093-an-application-is-a-root-widget.md)**, whose "A bare
`opens` in the application's module" says the showcase opens the package "*to the
core module only*". That was right when the only resource being read was a
stylesheet and is wrong now: a package is opened to whoever reads what is in it,
and for an image that is `:widgets`.

Follows [ADR-0387](0387-a-resource-directory-is-a-package.md), which is the same
fact — a resource directory is a package — biting from the other side.

## Context

The showcase logged this, four times:

```
WARN ImageState - image resource:…example.ui.CanvasScreen:canvas-sample.jpg did not load:
  java.io.IOException: no image resource "canvas-sample.jpg" at resource:…:canvas-sample.jpg
```

The file is there. `example/src/main/resources/io/github/digitalsmile/goldberry/example/ui/canvas-sample.jpg`
has been there all along, and `DeclaredResourcesTest` asserts it is.

**JPMS encapsulates resources, and only on the module path.** A file in a package
of a named module is invisible to other modules unless the package is `opens` —
`exports` does not do it, because it governs types rather than bytes. The
showcase knew that and said so in its `module-info`, and then got the target
wrong:

```java
opens io.github.digitalsmile.goldberry.example.ui to io.github.digitalsmile.goldberry.core;
```

Its own comment explained the choice: the package is opened "to whoever loads
them, which is exactly one module", and ADR-0093 said the same. That was true
when the only thing being read was a stylesheet. It stopped being true when the Canvas screen grew an `image`
card, because `ImageSource.Resource.load` is `:widgets`' code, and a package
opened to `:core` is closed to `:widgets`.

Three things then went wrong at once, and each is worth fixing on its own.

## Decision

### The package opens to both modules that read it

One line, and a corrected comment above it. `:core` parses the stylesheet and
the markup; `:widgets` loads the picture; the package names both.

Verified on the module path in both directions, because a classpath test cannot
see this at all:

- with the open, `ImageSource.resource(CanvasScreen.class, "canvas-sample.jpg").load()`
  returns a 96 × 64 image;
- with it reverted, it fails — with the message below.

### The failure says what is wrong instead of what is missing

"No image resource" is a true sentence and a misleading one: it sends somebody
looking for a file that is sitting exactly where they put it. `Stylesheet.resource`
had already learned to tell the two cases apart, and `ImageSource.Resource` now
does the same:

```
the image resource "canvas-sample.jpg" at resource:…CanvasScreen:canvas-sample.jpg
is encapsulated: module io.github.digitalsmile.goldberry.example does not open
io.github.digitalsmile.goldberry.example.ui to io.github.digitalsmile.goldberry.widgets,
and JPMS encapsulates resources as well as classes. Add `opens
io.github.digitalsmile.goldberry.example.ui to io.github.digitalsmile.goldberry.widgets;`
to its module-info — the file itself may well be there.
```

It names the module that must be opened *to*, computed from where the reading
happens rather than written down, so it stays right if the loader ever moves.

### A broken source is reported once

One picture drawn at four `fit` values is four `ImageView`s, four loads and four
identical lines about one file. `ImageState` now keeps a bounded set of the
source keys it has complained about — `OverflowLog`'s argument and
`OverflowLog`'s answer, down to the 256-entry cap.

Keyed on the **source**, not the view: that is what failed, and two views of one
key share a cache entry, so the reason cannot differ between them.

### A test that can see what the suite cannot

This is the part worth keeping. Every test in this repository passes with the
`opens` wrong, because tests run on the class path where nothing is
encapsulated. The bug was invisible to the suite and obvious in the application.

`OpenResourcePackagesTest` therefore reads the **repository** rather than the
running JVM: it walks `src/main/resources`, derives the package each file is in,
and asserts that a package holding an image opens to `:widgets` and a package
holding a stylesheet or markup opens to `:core`. `DeclaredResourcesTest` is its
sibling and exists for the same reason — a native image's missing resource is
another failure a passing suite cannot see.

Checked by reverting the fix: the test fails, and its message names the package
and the module to add.

## Consequences

**The Canvas screen draws its sample.** On the module path, which is where it did
not.

**An application that hits this is told how to fix it.** The message is long, and
deliberately: the reader is looking at a file that exists and being told it does
not, and nothing shorter closes that gap.

**The rule is now stated where an application can copy it.** "Open the package to
whoever reads it" is a sentence somebody has to get right per package and per
module, and the showcase is the worked example. `:html` needs no open because it
takes an `ImageSource` the application supplies and reads nothing itself.

**The test is the showcase's, not the toolkit's.** It encodes which toolkit module
reads which kind of file, which is a fact about the toolkit — so it will need
amending if a third module ever reads an application's resources. That is a
cheap price for a check that catches a whole class of module-path-only failure,
and the alternative is a rule that lives only in prose.
