# 175. A banner says its kind twice

Date: 2026-08-23

## Status

Accepted. Builds `docs/core-widgets.md` §7's `message`, the first widget whose
whole job is to draw the aurora hues as a **glyph and a border on a surface** —
and the first one to measure whether that was legible.

## Context

§7 asks for an inline banner: `kind="info|success|warning|danger"`, an icon,
text, optional action links, optional dismiss. It also says, in the same
paragraph, why it is not a `toast` — a toast is transient, floats over the
window and is about something that just happened; a message is part of the
layout, persists until the condition does, and is about the thing next to it.

The specification's own sentence about the icon is the one that decided most of
this record: "the icon is **not decorative** — §1.2 forbids colour as the only
carrier of meaning, so `kind` sets an icon *and* a colour".

It was picked next because something was already waiting for it.
[ADR-0169](0169-a-field-is-silent-until-you-leave-it.md) built §4's error
summary as a register — `FormController.errors()` — and nothing drew it, because
the only thing that should is a banner with a kind, an icon and a dismiss, and
there was no such widget.

## Decision

### The kind is a value, where a badge's variant is a class

[ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md) made `badge`'s
variants **classes**, and gave a good reason: a variant that is only a skin
should not become a second vocabulary that only Java can write.

A message's kind is not a skin. It picks the **glyph**, which is §1.2's
requirement rather than a decoration, and it is what M5's semantics will read to
decide `status` from `alert` — the difference between a banner that interrupts a
screen reader and one that does not. So it is an enum the widget reads, `kind=`
is the attribute §7 names, and the class goes on the node as well so
`message.danger` still selects. `skeleton`'s `shape=` is the same arrangement.

### Four glyphs, drawn as marks and not as icons

Lucide has exactly these four — `info`, `circle-check`, `circle-alert`,
`triangle-alert` — and a banner cannot use them.
[ADR-0043](0043-icons-are-stroked-paths.md) made an `Icon` a parsed `BlendPath`
that owns native memory and must be closed exactly once, which is why markup can
only *name* one from a registry the application fills. A widget tree is
described afresh on every build, so a banner that built its own icon would leak
one per frame.

`Box.Mark` is the answer the catalog already had, and
[ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md) gave the same
argument for `tab-close`'s ×. So there are four new kinds — `CIRCLE_INFO`,
`CIRCLE_CHECK`, `CIRCLE_ALERT`, `TRIANGLE_ALERT` — named for their **shape**,
like every other mark, and drawn to Lucide's own geometry so that a banner's
glyph and an application's icon beside it are one hand rather than two.

They are the first marks made of two drawings: an outline stroked, then a dot
filled, with the path reset in between. `EnclosedMarkTest` measures where the
ink lands, because three things can go wrong there and none of them throws —
the outline can be skipped, the dot can land in the wrong half, or either can
spill outside the slot. It asserts the ring is **hollow** (a `fillPath` where a
`strokePath` was meant is a plausible-looking blob at 20px), that an `i` has its
dot above its stem and a `!` below, and that the triangle's corners are empty
where a circle's are not — which is §1.2's requirement stated as a measurement
rather than as an intention.

### The hue has a third rank, because the theme's own claim was untrue

Both theme files document `--gb-danger` as "what a label, an **icon** or a
border is drawn in", against `--gb-danger-fill` for "what you may put words on
top of". This is the first widget that actually draws a hue as a line, so it is
the first time anybody measured it:

| | info | success | warning | danger |
|---|---|---|---|---|
| dark, on `--gb-surface` | 3.74 | 4.94 | 6.44 | **2.46** |
| light, on `--gb-surface` | **2.21** | **1.67** | **1.28** | 3.36 |

Five of the eight are below §1.2's 3:1 floor for anything that is not text. The
sentence had been true-looking for months for the same reason ADR-0088's seven
button pairs were: nothing measured it.

So a hue has three ranks now — itself, `-fill` for words on top of it, and
`-line` for a stroke drawn on the page. The derivation is ADR-0087's exactly:
the palette entry moved in lightness until it clears, per theme, written beside
the value it came from. Three alias straight through on dark and one on light,
which is the two themes' opposite problems: a dark theme's trouble is the dark
end of the palette and a light theme's is the pale end.

`ContrastTest` gained a second sweep at 3:1 to hold it there, and it resolves
through the real cascade like the first one rather than reading the token.

### The banner's own colour is a token per kind, and the tint is 4%

`design-system.md` §2 gives the metrics — padding 12/16, radius 8, icon 20 with
gap 12, "1px border and a 4% tint of its `kind` colour" — and every number in
`controls.css` is one of those. The tint is a token per kind per theme, written
as an eight-digit hex, because §8's subset has no colour functions;
`--gb-selection` set that convention.

Four percent is faint, and deliberately: the banner is told apart by its glyph
and its border, which is what §1.2 asks for. The tint says "this block is one
thing", not "this block is red".

### It is stateful, and it holds nothing but a timestamp

§3 gives `message` an entrance — "in: `opacity` + 2px rise, base". A newly
mounted element deliberately starts no transition
([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)): there is no
previous style to move from. So an arrival is a function of the frame clock and
needs a beginning, which is a [`Phase`](0166-a-raised-thing-is-told-apart-by-its-edge.md)
— and a beginning has to survive the next build, which is what a `State` is for.

One difference from `collapse` and `carousel`, and it is a correction of them:
those hand their part a *function* of the clock and decide **at build time**
whether there is an animation at all, so `isAnimating` goes on saying yes until
something rebuilds them. A banner is never rebuilt by anything. So this hands
the part the `Phase` itself and asks it, and the frame loop goes quiet on the
frame after the arrival ends — which is what §1.7 promises.

### The departure runs before the application is told

§3 also asks for "out: `opacity` fast", and the first cut of this widget did not
have one on an argument that looked airtight: a banner goes away because the
application stopped describing it, so by the time anything could animate there
is nothing left to draw. Animating an exit, the argument went, needs something
that outlives the description — which is what a tab strip does for a closing tab
because a strip owns its list, and nothing owns a lone banner.

The argument is a false choice, and the way out is to **reverse the order**. The
× does not tell the application and hope. It starts a `LEAVING` phase in the
widget's own state, keeps drawing the banner for §1.7's `fast`, and calls
`onDismiss` when the fade is over. For the whole of the animation the
description is still in the tree, because nobody has asked for it to go yet — so
no owner is needed, which is precisely what a lone banner does not have.

That makes `Phase` carry a duration instead of only the `base` constant, because
§3 gives the two directions different ones: 160ms in, 100ms out. A dismissal
that took as long as an arrival reads as the control arguing.

Three consequences worth stating, and the first is the interesting one:

- **An application that wires a dismiss handler and then does not remove the
  banner keeps a banner that has gone.** The widget draws nothing once the phase
  runs out, rather than springing back — a × that faded something and then
  restored it reads as a click that failed. What it cannot do is close the gap
  its container left round it; that is the container's number.
- **Reduced motion dismisses at once**, rather than waiting out a fade that is
  not happening. The preference is a property of a frame and the code that acts
  on it runs in a pointer handler, so the value comes back out of `render` —
  `carousel`'s arrangement, for `carousel`'s reason.
- **With no window there is no timer**, so the dismissal is instant. Every widget
  test that does not ask for a host and every golden image is in that case, and a
  banner that could not animate its exit must still have one.

### The summary is a factory, not something `form` emits

§4's "failures register in the form's error summary" is now drawable:
`Message.summary(errors)` returns one `danger` banner with a line per failure,
or **empty** when nothing is wrong — a summary of no errors is not an empty
banner, it is no banner.

A factory rather than a child `form` adds, for two reasons. A form does not know
where its summary belongs: above the fields is the convention, below them is
what a long form wants, and a dialog's header is what a dialog wants. And a form
that drew one would have to rebuild whenever any field's message changed, which
is a notification from `Validated` to `FormAccess` that nothing else needs and
that `FormState.register` deliberately does not do.

### The dismiss is focusable, where `tab-close` is not

The two look alike and answer opposite questions. A `tab-close` sits inside a tab
strip, which is one Tab stop with the arrows roving inside it (§7.2), and
`Delete` on the tab is the keyboard's way to close one. A message is not a focus
scope and owns no keyboard map: an unfocusable × would mean a banner a keyboard
user cannot dismiss at all. So it is a Tab stop and takes `Space` and `Enter`
like a button — one extra stop per dismissable banner, which is the honest cost.

`dismiss=` names an action rather than being a flag, because nothing in a
*document* could take the banner away: what put it there is the application's own
state. The showcase makes that the subject of a screen rather than a footnote —
**Notifications** is Java where its neighbours are documents, and its buttons are
the demonstration. Pressing one adds a description and the banner rises into
place; pressing its × takes the description away and it is gone on the next
frame. A `message` written in a document can only *report* a dismissal, and the
four resident banners at the top of that screen do exactly that.

### A gallery image is the second frame now

The Overlays screen's first image showed four banners at zero opacity: holding
their space, drawing nothing. The gallery painted one frame, and one frame is
the frame before every arrival starts.

So `GalleryGoldenTest` renders twice against its frozen clock and asserts on the
second, 200ms in. That is half of the entry
[ADR-0171](0171-a-column-is-an-x-and-a-width-arrives-late.md) filed under
`text-area` — "the golden painting twice … would make every screen's image more
faithful, not just this one". The other half, feeding the hit-test regions back
between the two frames so a widget that measures itself sees a real width, is
still open.

## Consequences

- **Two of §7's five are built** — `tooltip`, `popover`, `tour`, `hud` and now
  `message`. What is left in the group is `dialog` and `toast`, and both inherit
  this record's arrival: a dialog's scrim and a toast's slide are the same
  clock-driven mount.
- **`toast` inherits the fade-then-tell order, and needs more than it.** A banner
  fades in place and the layout closes over the hole afterwards; a toast stack has
  to *reflow* — §3 says "siblings reflow via `translate`, like `toast`" — so the
  entries below a departing one have to travel while it fades. That is a queue's
  problem and the queue is the widget that owns one.
- **A third token rank exists and only one widget reads it.** `--gb-*-line` is
  right for any glyph or border in a semantic hue, and the widgets that already
  draw one — a `field`'s `:invalid` border, a `badge`'s edge — have not been
  looked at. `ContrastTest` will catch them when they are.
- **A message with nothing to say cannot describe itself away.** There is no
  `bind=` on this widget, deliberately: a bound banner would be *present and
  empty* when the value was blank, and §8's subset has no `display`, so no widget
  can take itself out of a layout. The thing that can is whatever describes it —
  which is why the summary is an `Optional`, and why the showcase screen that
  spawns banners had to be Java.
- **A stack of banners is the container's gap, and the first one had none.** A
  `column` has no gap of its own — the toolkit's rule everywhere, because a widget
  does not decide how far it sits from its neighbours — so the first version's
  four banners touched, and four bordered blocks sharing edges read as one control
  with rules through it. 12px on the ramp, in the showcase's stylesheet where
  every other gap on that screen is. Worth writing down because `toast` will stack
  them too and will have to decide the same number, in the widget rather than in
  a document, since nobody writes a toast stack's container.
- **Every arrival costs one wasted frame.** The renderer asks whether a node is
  animating before it draws it, so the frame that finishes an arrival still asks
  for one more. That is every clock-driven animation in the toolkit and not
  something about banners; it is written down here because the golden test had to
  work around it.
- **The `-line` derivation is four hand-computed hex values.** Each carries its
  measurement in a comment beside it, which is ADR-0087's convention, and each is
  a number that has to be recomputed if the palette moves. A theme is not a
  program and the subset has no `color-mix`; this is what that costs.
