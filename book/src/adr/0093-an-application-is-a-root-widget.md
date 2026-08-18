# ADR-0093: An application is a root widget

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §11, extends [ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)

## Context

The showcase's `main` was 190 lines, and none of them were about the showcase.

It opened a window, opened a font book, built an element tree, built a render
tree, built a pointer router, held three one-element arrays to remember the
renderer and the theme and the density across frames, wrote the paint callback —
flush the tree, rebuild the renderer if the theme moved, update the render tree,
compute damage, choose between a partial and a full repaint, hand the damage
back, capture the hit-test snapshot, ask for another frame if anything is
animating — and then took it all down again in an order that matters: the tree,
then the render tree, then the icons, then the fonts, then the window.

Every line of it is the same in every application. Two of them are subtly wrong
if reordered: a render object holds a Yoga measure callback that closes over a
paragraph that closes over a font, so closing the fonts first reads unmapped
memory; and `Goldberry.shutdown()` at the end is not tidiness but the difference
between a clean Wayland disconnect and a compositor unwinding a client that never
said goodbye ([ADR-0085](0085-a-window-that-closes-beats-a-sharper-one-that-cannot.md)).

Three smaller things were wrong with the same file for related reasons:

- **The window's CSS was a Java text block.** The toolkit reads its own theme and
  control sheets from resources; an application had no supported way to do it and
  wrote CSS inside quotes, where no editor will highlight it and no designer will
  open it.
- **Nothing in the showcase exercised KDL.** §9's inflater had test coverage and
  no window coverage.
- **Building a tree read like a data structure, not a tree.** `new Row(List.of(a,
  b, c), id("bar"))` — a `List.of` between every parent and its children, and a
  static helper turning a string into an `Attributes` because a widget had no way
  to be given one after construction. Configuring a slider with tick marks meant
  an eleven-argument constructor with four nulls in it.

## Decision

**An application implements [Application] and calls `Goldberry.launch`.** One
required method, `root()`, returning the widget at the top of the window;
defaults for the title, the size, the stylesheets, and a `start`/`stop` pair for
the native objects only an application knows it owns. The launcher owns
everything else and is not public — what an application gets back is a [Host]
with `repaint`, `restyle`, `title`, `shortcut`, `fonts` and a named escape hatch
to the `Window`.

**`restyle()` is separate from `repaint()`**, and that is the one piece of state
the launcher keeps on the application's behalf. Re-reading `stylesheets()` every
frame would rebuild the renderer every frame, and the renderer is what caches the
resolved styles; re-reading it never would make a theme switch impossible. So the
application says when, which makes a theme switch two lines and costs nothing the
rest of the time.

**Every widget is chainable, through two interfaces rather than fifty pairs of
methods.** [Attributed] gives `id`, `styled` and `keyed`; [Bindable] gives
`bound`. Both are self-typed — `Attributed<Badge>` — so a chain keeps its type
and `new Badge("3").styled("danger")` is still a `Badge`. A widget supplies the
one thing only it can, `withAttributes`, which rebuilds its own record.

**Containers take children as varargs**, so a tree reads as a tree. `List.of` is
gone from the showcase entirely.

**`Stylesheet.resource` and `KdlParser.resource`** load an application's CSS and
markup from files beside its class, the way the toolkit loads its own.

## Alternatives considered

**An abstract `Application` class with the loop inside it.** An application would
extend it and override `root()`. Rejected because it spends the one superclass
slot Java gives, and because it puts the frame loop in the type an application
subclasses — where anything `protected` becomes API and any override is a way to
break the shutdown order this exists to protect.

**Pass a `Consumer<Frame>` and keep `main`.** The smallest possible change, and
it fixes nothing: the callback is not the hard part, the six objects it closes
over and the order they are released in are.

**Call `stylesheets()` every frame and compare the result.** No `restyle()` to
remember. Rejected on the arithmetic: `Controls.stylesheets(theme, density)`
builds a new list each call, so equality would be structural over every rule in
every sheet, every frame — to answer a question that is false almost always.

**Three methods per widget instead of `Attributed`.** `id`, `styled` and `keyed`
written out fifty times. Rejected for the reason [Attributes] itself exists: it
is the kind of repetition where one copy eventually forgets to preserve the key,
and the failure — a widget that silently stops matching its element across a
rebuild — costs a focus ring rather than an exception.

**Builders.** `Badge.builder().text("3").styled("danger").build()`. Rejected
because a widget is a record and records are already values: the constructor
takes what matters and the chain names what usually does not, with no second
object and no `build()`.

**A bare `opens` in the application's module.** JPMS encapsulates resources, so
the toolkit cannot read an application's `showcase.css` unless the package is
open — `exports` governs types, not bytes. The showcase opens it *to the core
module only*, because an unqualified open hands the package's private types to
everything on the path as well.

## Consequences

**The showcase's `main` is one line**, and the file is 190 lines shorter with no
behaviour lost — it opens, paints, switches themes, and shuts down exactly as it
did, which the headless three-frame run proves.

**A new application is one class and one method.** That is the claim this record
is making, and the showcase is the only evidence for it so far: nothing else has
been written against `Application` yet, so whether the defaults are the right
defaults is unproven.

**`Host` has an escape hatch and it is already used.** The showcase reaches
`host.window()` for `onResize`, `onScaleChange` and `onCloseRequest`. Each is a
candidate for a method on `Host`, and each was left off because one caller is not
a pattern. The hatch being *named* an escape hatch is the mechanism for noticing
when that changes.

**A missing resource now explains itself**, including the JPMS case: the error
checks whether the owning package is open and, when it is not, says which
`opens` line is missing. That message exists because the first headless run hit
exactly that and the original message blamed the file.

**Two more interfaces in `:core`'s widget package.** `Attributed` and `Bindable`
are contracts a widget in an application's own module should implement too, which
is why they are in `:core` beside `Attributes` and not in the catalog.

**`Slider` and `Knob` grew configuration withers** — `ticks`, `format`, `scale`,
`detents` — which are a fourth way to build a widget after the constructor, the
markup and the attribute chain. They earn it by replacing an eleven-argument call
with four nulls, and the rule they follow is the one the chain follows: the
constructor takes what matters and a named step takes what usually does not.

**The launcher reads two command-line flags**, `--frames=N` and `--size=WxH`,
from the array an application chooses to hand it. A toolkit that parsed `argv`
would be overstepping; these are read from `launch(app, args)` and an application
that calls `launch(app)` passes none. `--frames` is what lets CI prove a window
opened with no human to close it, and it was showcase-private machinery until now.
