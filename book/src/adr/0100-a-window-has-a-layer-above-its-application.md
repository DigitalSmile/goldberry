# ADR-0100: A window has a layer above its application

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7, `docs/ARCHITECTURE.md` §4 and §11,
  extends [ADR-0093](0093-an-application-is-a-root-widget.md), applies
  [ADR-0062](0062-bind-is-a-path-and-nothing-else.md) to the toolkit's own state

## Context

`docs/core-widgets.md` §7 opens by naming two places an overlay can be drawn:
"all overlays render in the **in-window overlay layer** or backend popup windows
as appropriate". The second half is M3's — a menu that escapes the window needs a
platform window, and `Backend` says outright that popups are absent "not dropped"
until something needs one ([ADR-0019](0019-the-backend-spis-first-cut.md)).

The first half had nothing behind it at all, and three of §7's five widgets want
it rather than a popup: a `toast` stacks in a corner of the window, a `dialog`'s
scrim covers the window, and `hud` — the frame-rate readout this record's
sibling ([ADR-0101](0101-a-diagnostic-must-not-be-the-thing-it-measures.md))
adds — lies in a corner of it. None of them wants a second platform window, and
on Wayland none of them could reliably have one anyway.

**Nothing in the tree can float itself.** Yoga places an absolute box against its
own parent, which means the furthest a widget can pin itself is the panel it
happens to be in. A toast raised from a form's submit handler would appear in the
corner of the form. The thing being pinned to is the *window*, and only something
that sits above the application's root can name it.

And there is exactly one place that sits there: [ADR-0093](0093-an-application-is-a-root-widget.md)'s
launcher, which owns the window, the three trees and the frame loop, and hands
an application a `Host` instead of any of them.

## Decision

**Every window's element tree is rooted at a `WindowRoot`, always, and an overlay
is one of its children.**

```java
tree = new ElementTree(new WindowRoot(application.root(), overlays));
```

`WindowRoot` renders one box: the application's root in flow with `flex-grow: 1`,
so it fills the window, and every overlay after it as an absolute box inset to a
[`Corner`](../../../core/src/main/java/io/github/digitalsmile/goldberry/widget/Corner.java).
Three consequences follow from that one shape, and each is the point of it:

- **An overlay takes no space.** An absolute box takes no part in its parent's
  flex layout, so the application's box is the same box it was. Adding a HUD
  cannot move a pixel of what is under it.
- **An overlay is painted last.** A box tree has no z-order beyond document order
  ([ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)), so "on top"
  is "listed after" and needs no new concept.
- **Two of the four insets are `UNDEFINED`, not zero.** An inset of zero on all
  four edges pins a box to all four and stretches it across the window — which is
  a scrim, and a perfectly legal box. `Corner.insets` sets the two edges its
  corner touches and leaves the others undefined, so the overlay keeps its own
  size.

### The root node is there from the first frame

Whether or not anything is floating. A layer that appeared with the first overlay
would re-parent the entire application to show a toast, and re-parenting is
precisely what throws away element state, focus and every animation in flight
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)). The
cost of the node when nothing is floating is one box in the tree and one Yoga
node; the cost of adding it later is the application's state.

### The layer arrives as a binding, not as a constructor argument

`Host.overlay(...)` is called at any time — from `Application#start`, from a
handler, from a key. An `ElementTree`'s root widget cannot be swapped, so the
list cannot be a value the root was built with.

It is a `Property<List<Overlay>>` the launcher owns and `WindowRoot` **watches**,
through the `binding()` every widget already has. The root element subscribes for
as long as it lives, and a change marks it for rebuild — the same route an
application's model takes to the screen ([ADR-0062](0062-bind-is-a-path-and-nothing-else.md)).
No `setState`, no second invalidation path, and no mutable list read behind the
framework's back.

The list is replaced rather than mutated, which is not a style preference: the
subscription is to the *value*, and a list changed in place is the same value.

### `Overlay` is a handle with identity

```java
var hud = host.overlay(new Hud(), Corner.BOTTOM_END);
hud.remove();
```

Adding is one call and removing is the same object. Two identical HUDs in two
corners are equal *values* and two different things on screen, so removal is by
identity — which is why `Overlay` is a class and not a record. `remove()` is
idempotent, because removing twice is what shutdown looks like when two things
both think they own it.

### `window-root` is selectable and not constructible

A stated exception to §11's parity invariant, on the grounds a part is one
([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)): a document cannot
write the node it is the document *of*. It is CSS-selectable because it is the
element `:root` matches and the one place a stylesheet could put the window's own
background.

Nothing visible changed by its arrival, and that is checkable rather than hoped
for: the `:root` blocks the two themes ship declare custom properties and nothing
else, so moving which element they match moves nothing that is drawn.

## Alternatives considered

- **A `stack` widget the application wraps its own root in.** `docs/core-widgets.md`
  §1 specifies `stack` and calls it "the basis for badges-over-things and custom
  overlays", so this is not a wrong tool — but it puts the layer in the
  application's tree, which means an application that forgot to add one has no
  overlay layer, and a library widget that raises a toast cannot know whether
  there is one above it. `stack` is still owed; it is a *layout* widget and this
  is a *window* facility, and building one does not build the other.
- **`Overlay.of(context)` reached through `BuildContext`,** Flutter's shape: any
  descendant finds the layer and pushes an entry into it. That is what `toast`
  and `tooltip` will want, because the thing raising them is deep in the tree.
  It is not built here, for [ADR-0019](0019-the-backend-spis-first-cut.md)'s
  reason: there is one consumer, it is the application itself, and an interface
  designed against one caller is designed twice. `Host.overlay` is the half that
  is certainly needed either way — a `BuildContext`-reachable form would be
  implemented *in terms of* it.
- **An overlay window per overlay,** the popup path, used for everything. Wrong
  for the three widgets that want this: a toast is inside the window by
  specification, and a scrim over the window is *of* the window. It is also the
  more expensive answer everywhere and the less portable one — see §4 on Wayland.
- **Placement in CSS rather than in Java.** There is no `position` in §8's
  subset, deliberately: it is the same reason `affix` is a widget rather than
  `position: sticky`. A corner and a margin are Java's, and `--gb-window-margin`
  is the name the number will take when a floating button needs it in a rule.

## Consequences

- **`Host` grows two methods** — `overlay(...)` and `frames()` — and an
  application that uses neither is unchanged. §7's toast, tooltip, dialog and
  popover all now have somewhere to be drawn that does not wait on the backend.
- **The application's root is one level deeper**, which is visible in a test that
  walks from `tree.root()`. Selectors are unaffected: nothing in the toolkit's
  stylesheets is anchored to the root, and `:root` matches custom-property blocks
  only.
- **Nothing hit-tests an overlay yet.** The pointer router tests against the
  painted frame ([ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)),
  and an overlay is in that frame, so a button in one is reachable today by
  accident of the ordering rather than by a rule anyone wrote. A modal scrim
  needs the rule written — "the topmost overlay takes the pointer first, and a
  modal one takes it exclusively" — and that belongs with `dialog`.
- **An overlay is not a focus scope.** §7 says each overlay "wraps a `focus-scope`
  and restores focus on close", which is true of the ones that take focus and is
  not true of a HUD. The wrapping belongs to those widgets rather than to the
  layer, and `focus-scope` exists already ([ADR-0078](0078-a-focus-scope-has-an-axis.md)).
- **Overlays do not animate in or out.** A toast that appears and disappears
  wants §1.7's overlay curve, and the machinery is transitions on a node that is
  *there* — so it is the widget's, not the layer's, and it is the same problem
  `collapse` has with a body that is unmounted while closed.
