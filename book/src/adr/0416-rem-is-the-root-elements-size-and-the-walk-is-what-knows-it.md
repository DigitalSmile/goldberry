# 416. `rem` is the root element's size, and the walk is what knows it

Date: 2026-09-19

## Status

Accepted. Closes the `TODO.md` entry left open by
[ADR-0242](0242-em-is-the-elements-own-size.md), which is the record that
created it.

## Context

ADR-0242 fixed `em` and wrote down what it had not fixed:

> `rem` continues to use `Context.rootFontSize()`. In CSS `rem` is the *root
> element's* computed font size, so these agree unless the root element itself
> declares one — and recovering that inside `ComputedStyle.of` is not possible,
> because a node is handed its parent's style and not the root's. Nothing in the
> catalog styles a root's `font-size`, so this is exact today and is written down
> rather than fixed.

And named the two shapes a fix could take:

> It needs a third thing passed down beside the parent style, or a mutable field
> on the renderer that is correct only after the root has resolved.

The diagnosis is exactly right and the pessimism is half misplaced. The problem
splits in two, and only one half is out of `ComputedStyle.of`'s reach.

## Decision

### The root's own half was already built, for `em`

ADR-0242 gave `ComputedStyle.of` two passes: `font-size` first against the
parent's size, then everything else against the size that produced. The reason
was CSS's one exception — `1.2em` on `font-size` means "a fifth larger than my
parent", because the value being computed cannot be its own input.

`rem` has the **same** exception, from the other end. CSS: when specified on the
root element's `font-size`, `rem` refers to the property's initial value. So on
the root:

- `font-size: 2rem` resolves against the configured size, because the root has
  not computed one yet;
- everything else on the root resolves against the size it just computed.

That is one line in the second pass, in a method that was already shaped for it:

```java
var rootSize = parent == null ? (float) style.typography().size() : context.rootFontSize();
var own = new CssLength.Context((float) style.typography().size(), rootSize);
```

`parent == null` *is* the root — the method's own javadoc has said so since it
was written. No plumbing, and the "correct only after the root has resolved"
caveat turns out to be the specification rather than a wart.

### The descendants' half is the renderer's, and it is the second shape

A node with a parent takes `rootFontSize` as given, because by then nothing in
the method can do better: a node is handed its parent's style and never the
root's, which is precisely ADR-0242's point. What *can* is the thing that walks
the tree, at the one moment it holds the root's resolved style and has not yet
descended.

`WidgetRenderer` keeps one field, `lengthsBelowRoot`, reset to the configured
context at the top of every frame and set once:

```java
if (element.parent() == null && self != null) {
    lengthsBelowRoot = lengths.withRootFontSize((float) self.typography().size());
}
```

It is the only mutable style state in the walk, so "is it ever stale" is the
question this shape has to answer. It is not, for two separate reasons and both
matter:

- **Within a frame**, it is set before any descendant resolves and read nowhere
  else. A frame has either not reached the root — in which case the configured
  value is the right answer, because there is no root size yet — or has.
- **Across frames**, a root whose `font-size` changed resolves a different style
  and therefore hands its children a different *instance*, and their cache is
  keyed on that by identity ([ADR-0070]). The subtree re-resolves without
  anything telling it to, which is the invalidation scheme already in place doing
  the work a "root size changed" signal would otherwise need.

**Why the field rather than the third argument.** Threading the root's size into
`ComputedStyle.of` means a fourth parameter on a method with two public overloads
and callers in the renderer, the keyframe track and every style test — to carry a
value that is constant for the whole tree, which is what `CssLength.Context`
already is. The context is the third thing, and it is already threaded; the only
change is that one of its two fields now means what it says.

### `Context`'s two fields both narrow, and that is a pattern rather than a coincidence

ADR-0242 left `Context.fontSize` meaning "what the **root's** `em` resolves
against" rather than "what every `em` resolves against". `Context.rootFontSize`
takes the same step here: it is what the root's own `font-size` declaration
resolves `rem` against, and from the root's computed size onward the renderer
replaces it. A `Context` is now, precisely, *the two numbers the root starts
from*.

## Alternatives considered

- **A fourth parameter on `ComputedStyle.of`.** Above. Every caller would carry a
  tree-wide constant through a per-node call, beside a parameter that already
  carries tree-wide constants.
- **Compute the root's size in `render(ElementTree)` before the walk.** One extra
  resolve of one node, and then the whole walk — root included — uses the final
  context. Simpler, and it gets the root's own `font-size: 2rem` wrong by making
  it its own input. It also has to mirror `Styled.restyle`, or the pre-pass and
  the walk disagree about the root's style.
- **Keep the configured value when the root declares nothing.** This is the only
  alternative with a real argument behind it: it changes no existing answer.
  Rejected because it makes `rem` mean two different things depending on whether
  some other rule exists — the configured number for a silent root, the computed
  size for a declared one — and a unit whose meaning turns on a declaration
  elsewhere is worse than one that is merely different from what you expected.
- **Leave it, since nothing in the catalog styles a root's `font-size`.** That
  was the state, and it is the same argument ADR-0242 rejected for `em`: a unit
  that silently means something else is worse than an unimplemented one, because
  it looks like it works. The typography scale is what makes it reachable, and
  §1.4's whole point is that an application moves the scale.

## Consequences

- **`rem` below a root that declares `font-size` changes**, which is the entry's
  case and the thing that was broken. `window { font-size: 20px } button {
  padding: 2rem }` is 40 and was 32.
- **`rem` below a root that declares nothing changes too**, and this one is worth
  stating plainly: a silent root computes `Typography.INITIAL`'s 13, so `2rem` is
  26 where it was 2 × the configured 16. The configured number now reaches only
  the root's own `font-size` declaration. That is the cost of the decision above,
  paid once, and nothing shipped is affected — ADR-0242 checked that not one `em`
  or `rem` appears in `nord-dark.css`, `nord-light.css`, `controls.css` or the
  showcase's sheets, and that is still true.
- **One existing test changed meaning and was rewritten**, exactly as ADR-0242's
  `em` test did. `ComputedStyleTest`'s "rem multiplies the root font size, not the
  local one" built an element with no parent, passed `Context(20, 16)`, and
  asserted 32. An element with no parent *is* a root, and this one declares no
  size, so its computed size is 13 and `2rem` is 26. On a root, `1rem` and `1em`
  coincide — which reads like the test losing its point and is CSS's rule: the
  root has nothing above it for the two to differ about. The point moves to
  `RootFontSizeTest`, where there is a descendant to tell them apart.
- **`RootFontSizeTest` needs a real renderer**, and that is the ADR in one
  sentence. The missing half was never arithmetic, it was reach, so a test built
  on `ComputedStyle.of` alone cannot fail against the old code — `computeChild`,
  the existing stand-in for the renderer, hands both contexts down unchanged and
  is itself an instance of the bug. Six tests: the entry's case, a root and a
  descendant declaring *different* sizes so `rem` and `em` cannot be satisfied by
  one number, the silent-root case above, the root agreeing with its own
  descendants, and two renders in a row.
- **`Paints.Context.length`'s `rem` is exact now.** That seam runs with
  `currentElement` set, so the walk has passed the root. Its `em` narrowing —
  against the root's size rather than the node's — stands, documented as before,
  because the element's resolved style still is not in hand there.
- **`CssLength.Context.withRootFontSize` is the only wither on the record.**
  There is deliberately none for `fontSize`: the element's own size is derived
  per node where it is used, and a second way to say it would be a worse way.
